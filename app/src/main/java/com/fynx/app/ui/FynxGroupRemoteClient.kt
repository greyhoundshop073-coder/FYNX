package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object FynxGroupRemoteClient {
    data class RemoteMessage(val id:String,val text:String,val senderUsername:String,val timestamp:Long,val attachmentMediaId:String?,val attachmentType:String?,val attachmentUrl:String?)
    suspend fun syncGroup(context:Context,group:FynxGroup):Result<Unit> = runCatching { val body=JSONObject().apply{put("ownerUsername",group.ownerUsername.trim().removePrefix("@"));put("name",group.name);put("description",group.description);put("visibility",group.visibility.name);put("members",JSONArray().apply{group.members.forEach{put(it.username.trim().removePrefix("@"))}})};FynxBackendClient.postJson(context,"/api/groups/${group.id}/sync",body.toString()).getOrThrow();Unit }
    suspend fun loadMessages(context:Context,groupId:String):Result<List<RemoteMessage>> = runCatching { val raw=FynxBackendClient.get(context,"/api/groups/$groupId/messages").getOrThrow();val a=JSONObject(raw).optJSONArray("messages")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteMessage(o.getString("id"),o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"}))}} }
    suspend fun sendMessage(context:Context,groupId:String,message:ChatMessage):Result<RemoteMessage> = runCatching {
        var mediaId:String?=message.attachmentUri?.substringAfterLast('/').takeIf{it?.all(Char::isDigit)==true}
        var mediaType=message.attachmentType
        if(mediaId==null && message.attachmentUri!=null){
            val uri=Uri.parse(message.attachmentUri!!);val mime=context.contentResolver.getType(uri)?.lowercase()?:when(mediaType){"video"->"video/mp4";"audio"->"audio/mp4";else->"image/jpeg"};val uploaded=FynxProductionMessaging.uploadMedia(context,uri,mime).getOrThrow();mediaId=uploaded.id;mediaType=mediaType?:mime.substringBefore('/')
        }
        val body=JSONObject().apply{put("id",message.id);put("text",message.text);if(mediaId!=null)put("attachmentMediaId",mediaId);if(mediaType!=null)put("attachmentType",mediaType)}
        val raw=FynxBackendClient.postJson(context,"/api/groups/$groupId/messages",body.toString()).getOrThrow();val o=JSONObject(raw).getJSONObject("message");RemoteMessage(o.getString("id"),o.optString("text"),o.optString("senderUsername"),o.optLong("timestamp"),o.optString("attachmentMediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentType").takeIf{it.isNotBlank()&&it!="null"},o.optString("attachmentUrl").takeIf{it.isNotBlank()&&it!="null"})
    }
    fun toChatMessage(m:RemoteMessage,currentUsername:String,baseUrl:String)=ChatMessage(m.text,m.senderUsername.equals(currentUsername.removePrefix("@"),true),m.id,m.timestamp,delivered=true,read=true,attachmentUri=m.attachmentUrl?.let{if(it.startsWith("http"))it else baseUrl.trimEnd('/')+it},attachmentType=m.attachmentType)
}
