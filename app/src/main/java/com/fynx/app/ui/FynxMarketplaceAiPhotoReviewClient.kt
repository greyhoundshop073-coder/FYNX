package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal data class FynxMarketplaceAiPhotoReviewPhoto(val index:Int,val status:String,val issues:List<String>)
internal data class FynxMarketplaceAiPhotoReview(val overallStatus:String,val photos:List<FynxMarketplaceAiPhotoReviewPhoto>)

/** Temporary photo-quality requests. Pixels are compressed locally and sent only to the authenticated review route. */
internal object FynxMarketplaceAiPhotoReviewClient {
    private const val MAX_IMAGES=4
    private const val MAX_IMAGE_BYTES=4*1024*1024
    private const val MAX_TOTAL_BYTES=9*1024*1024
    private const val MAX_DIMENSION=1600
    private const val JPEG_QUALITY=82
    private val allowedIssues=setOf("LOW_LIGHT","BLUR","GLARE","BUSY_BACKGROUND","FRAMING","DISTANCE","ANGLE","LOW_RESOLUTION")

    suspend fun review(context:Context,photos:List<Uri>):Result<FynxMarketplaceAiPhotoReview> = withContext(Dispatchers.IO) {
        try {
            val selected=photos.distinct().take(MAX_IMAGES)
            require(selected.isNotEmpty()){"Choose at least one product photo."}
            val images=JSONArray()
            var totalBytes=0
            selected.forEach { uri ->
                val mime=context.contentResolver.getType(uri)?.trim()?.lowercase().orEmpty()
                require(mime.startsWith("image/")){"Only still photos can be checked. Remove videos and try again."}
                val bytes=prepareJpeg(context,uri)
                require(bytes.size in 1..MAX_IMAGE_BYTES){"A selected photo is too large after compression. Choose a smaller image."}
                totalBytes+=bytes.size
                require(totalBytes<=MAX_TOTAL_BYTES){"Choose fewer or smaller photos. The combined review size must be 9 MB or less."}
                images.put(JSONObject().put("mimeType","image/jpeg").put("dataBase64",Base64.encodeToString(bytes,Base64.NO_WRAP)))
            }
            val raw=FynxBackendClient.postJson(context,"/api/assistant/marketplace/photo-review",JSONObject().put("images",images).toString()).getOrThrow()
            val result=JSONObject(raw).optJSONObject("review")?:throw IllegalStateException("FYNX AI returned an incomplete photo review.")
            val items=result.optJSONArray("photos")?:throw IllegalStateException("FYNX AI returned an incomplete photo review.")
            if(items.length()!=selected.size)throw IllegalStateException("FYNX AI returned an incomplete photo review.")
            val photosResult=buildList {
                for(index in 0 until items.length()) {
                    val item=items.optJSONObject(index)?:throw IllegalStateException("FYNX AI returned an invalid photo review.")
                    val number=item.optInt("index",-1)
                    val status=item.optString("status")
                    val issuesArray=item.optJSONArray("issues")?:throw IllegalStateException("FYNX AI returned an invalid photo review.")
                    if(number!=index+1||status !in setOf("READY","IMPROVE")||issuesArray.length()>4)throw IllegalStateException("FYNX AI returned an invalid photo review.")
                    val issues=buildList {
                        for(issueIndex in 0 until issuesArray.length()) {
                            val issue=issuesArray.optString(issueIndex)
                            if(issue !in allowedIssues)throw IllegalStateException("FYNX AI returned an invalid photo review.")
                            add(issue)
                        }
                    }
                    if((status=="READY"&&issues.isNotEmpty())||(status=="IMPROVE"&&issues.isEmpty()))throw IllegalStateException("FYNX AI returned an inconsistent photo review.")
                    add(FynxMarketplaceAiPhotoReviewPhoto(number,status,issues))
                }
            }
            val overall=if(photosResult.any{it.status=="IMPROVE"})"IMPROVE" else "READY"
            Result.success(FynxMarketplaceAiPhotoReview(overall,photosResult))
        } catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Throwable){Result.failure(failure)}
    }

    private fun prepareJpeg(context:Context,uri:Uri):ByteArray {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}?:throw IllegalArgumentException("Unable to open the selected photo.")
        require(bounds.outWidth>0&&bounds.outHeight>0){"Unable to read the selected photo."}
        var sample=1
        while(bounds.outWidth/sample>MAX_DIMENSION*2||bounds.outHeight/sample>MAX_DIMENSION*2)sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.RGB_565}
        var decoded=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:throw IllegalArgumentException("Unable to decode the selected photo.")
        try {
            val scale=maxOf(decoded.width,decoded.height).toFloat()/MAX_DIMENSION
            val outputBitmap=if(scale>1f) {
                val width=(decoded.width/scale).toInt().coerceAtLeast(1)
                val height=(decoded.height/scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded,width,height,true).also{if(it!==decoded){decoded.recycle();decoded=it}}
            } else decoded
            return try {
                ByteArrayOutputStream().use{output->
                    require(outputBitmap.compress(Bitmap.CompressFormat.JPEG,JPEG_QUALITY,output)){"Unable to prepare the selected photo."}
                    output.toByteArray()
                }
            } finally {if(!outputBitmap.isRecycled)outputBitmap.recycle()}
        } catch(failure:Throwable){if(!decoded.isRecycled)decoded.recycle();throw failure}
    }

    fun issueTitle(issue:String):String=when(issue){
        "LOW_LIGHT"->"Lighting";"BLUR"->"Sharpness";"GLARE"->"Glare or reflections";"BUSY_BACKGROUND"->"Background"
        "FRAMING"->"Framing";"DISTANCE"->"Camera distance";"ANGLE"->"Viewing angle";"LOW_RESOLUTION"->"Image clarity";else->"Photo quality"
    }
    fun issueAdvice(issue:String):String=when(issue){
        "LOW_LIGHT"->"Use bright, even natural light and avoid strong shadows."
        "BLUR"->"Hold the camera steady and tap to focus before taking the photo."
        "GLARE"->"Change the angle or move the light to reduce reflections."
        "BUSY_BACKGROUND"->"Use a clean, uncluttered background so the item stands out."
        "FRAMING"->"Keep the full item inside the frame with a little space around it."
        "DISTANCE"->"Move closer while keeping the whole item visible."
        "ANGLE"->"Add a clear front or side view to show the item's shape."
        "LOW_RESOLUTION"->"Retake the photo at the camera's full quality when possible."
        else->"Retake the photo with clear, even lighting."
    }
}
