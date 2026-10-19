package com.fynx.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private suspend fun fetchMarketplaceReceipt(context: Context, orderId: String): Result<JSONObject> =
    FynxBackendClient.get(context, "/api/marketplace/orders/${Uri.encode(orderId)}/receipt")
        .mapCatching { JSONObject(it).getJSONObject("receipt") }

private suspend fun fetchMarketplaceCheckoutReceipt(context: Context, checkoutId: String): Result<JSONObject> =
    FynxBackendClient.get(context, "/api/marketplace/checkout/${Uri.encode(checkoutId)}/receipt")
        .mapCatching { JSONObject(it).getJSONObject("receipt") }

private suspend fun receiptAvatar(context: Context, mediaId: String?): Bitmap? = withContext(Dispatchers.IO) {
    val id = mediaId?.toLongOrNull() ?: return@withContext null
    val file = File(context.cacheDir, "receipt-avatar-$id.img")
    val downloaded = FynxBackendClient.downloadToFile(context, "/api/social/profile-photo/$id", file, 4L * 1024L * 1024L)
    if (downloaded.isFailure) return@withContext null
    BitmapFactory.decodeFile(file.absolutePath)
}

private fun qrBitmap(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 220, 220)
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
}.getOrNull()

private fun drawReceiptAvatar(canvas: Canvas, bitmap: Bitmap?, label: String, left: Float, top: Float, paint: Paint) {
    val rect = RectF(left, top, left + 58f, top + 58f)
    val save = canvas.save()
    val path = Path().apply { addOval(rect, Path.Direction.CW) }
    canvas.clipPath(path)
    if (bitmap != null) {
        canvas.drawBitmap(bitmap, null, rect, paint)
    } else {
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawOval(rect, paint)
        paint.color = Color.rgb(30, 64, 175)
        paint.textSize = 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "F", left + 21f, top + 37f, paint)
    }
    canvas.restoreToCount(save)
    paint.color = Color.rgb(148, 163, 184)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f
    canvas.drawOval(rect, paint)
    paint.style = Paint.Style.FILL
}

private suspend fun createMarketplaceCheckoutReceiptPdf(context: Context, receipt: JSONObject): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val currency = receipt.optString("currency", "NGN")
        val items = receipt.optJSONArray("items") ?: org.json.JSONArray()
        require(items.length() in 1..20) { "Checkout receipt item list is invalid." }
        val qr = qrBitmap(FynxBackendClient.baseUrl(context).trimEnd('/') + receipt.optString("verificationPath"))
        val document = PdfDocument()
        val pageCount = ((items.length() + 5) / 6).coerceAtLeast(1)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 11f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(71, 85, 105); textSize = 9f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); strokeWidth = 1f }
        fun money(value: Double) = currency + " " + String.format(Locale.US, "%,.2f", value)
        for (pageNumber in 0 until pageCount) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber + 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            ink.color = Color.rgb(30, 64, 175); ink.textSize = 23f; ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("FYNX", 36f, 48f, ink)
            ink.color = Color.rgb(15, 23, 42); ink.textSize = 16f
            canvas.drawText("MARKETPLACE CHECKOUT RECEIPT", 36f, 76f, ink)
            muted.textSize = 10f
            canvas.drawText("Digitally signed • Verify payment evidence before relying on this receipt", 36f, 96f, muted)
            canvas.drawLine(36f, 112f, 559f, 112f, line)
            ink.textSize = 11f; ink.typeface = Typeface.DEFAULT
            canvas.drawText("Receipt ID: " + receipt.optString("receiptId"), 36f, 138f, ink)
            canvas.drawText("Checkout ID: " + receipt.optString("checkoutId"), 36f, 158f, ink)
            val buyer = receipt.optJSONObject("buyer") ?: JSONObject()
            canvas.drawText("Buyer: " + buyer.optString("displayName", buyer.optString("username", "FYNX buyer")).take(56), 36f, 178f, ink)
            canvas.drawText("Payment status: " + receipt.optString("paymentStatus"), 36f, 198f, ink)
            canvas.drawText("Paystack reference: " + receipt.optString("paymentReference").take(70), 36f, 218f, ink)
            val issued = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(java.time.Instant.parse(receipt.optString("issuedAt")).toEpochMilli())) }.getOrDefault(receipt.optString("issuedAt"))
            canvas.drawText("Issued: $issued    Page ${pageNumber + 1}/$pageCount", 36f, 238f, ink)
            canvas.drawLine(36f, 252f, 559f, 252f, line)
            ink.textSize = 12f; ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("PURCHASED ITEMS", 36f, 275f, ink)
            ink.textSize = 10f; ink.typeface = Typeface.DEFAULT
            val start = pageNumber * 6
            val end = minOf(items.length(), start + 6)
            var y = 302f
            for (index in start until end) {
                val item = items.optJSONObject(index) ?: JSONObject()
                ink.color = Color.rgb(15, 23, 42); ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("${index + 1}. " + item.optString("title", "FYNX marketplace item").take(62), 36f, y, ink)
                ink.typeface = Typeface.DEFAULT; ink.color = Color.rgb(71, 85, 105)
                canvas.drawText("Seller: " + item.optString("seller", "FYNX seller").take(35) + " • Qty: " + item.optInt("quantity") + " • Fulfillment: " + item.optString("fulfillmentStatus", "UNKNOWN"), 36f, y + 16f, ink)
                canvas.drawText("Subtotal: " + money(item.optDouble("subtotal")) + "  Delivery: " + money(item.optDouble("deliveryFee")), 36f, y + 32f, ink)
                ink.textAlign = Paint.Align.RIGHT; ink.color = Color.rgb(15, 23, 42)
                canvas.drawText("Line total: " + money(item.optDouble("total")), 559f, y + 16f, ink)
                ink.textAlign = Paint.Align.LEFT
                canvas.drawLine(36f, y + 42f, 559f, y + 42f, line)
                y += 53f
            }
            if (pageNumber == pageCount - 1) {
                y += 8f
                canvas.drawLine(36f, y, 559f, y, line)
                y += 24f
                ink.textSize = 11f; ink.typeface = Typeface.DEFAULT
                canvas.drawText("Subtotal: " + money(receipt.optDouble("subtotal")), 36f, y, ink); y += 19f
                canvas.drawText("Delivery: " + money(receipt.optDouble("deliveryFee")), 36f, y, ink); y += 19f
                canvas.drawText("Buyer-paid FYNX fee: " + money(receipt.optDouble("marketplaceFee")), 36f, y, ink); y += 19f
                canvas.drawText("Payout status: " + receipt.optString("payoutStatus", "NOT_RECORDED"), 36f, y, ink); y += 23f
                ink.textSize = 15f; ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TOTAL PAID", 36f, y, ink)
                ink.textAlign = Paint.Align.RIGHT
                canvas.drawText(money(receipt.optDouble("buyerTotal")), 559f, y, ink)
                ink.textAlign = Paint.Align.LEFT
                y += 24f
                ink.textSize = 8f; ink.typeface = Typeface.DEFAULT; ink.color = Color.rgb(71, 85, 105)
                canvas.drawText("HMAC-SHA256 signature:", 36f, y, ink); y += 12f
                canvas.drawText(receipt.optString("signature").take(64), 36f, y, ink)
                if (qr != null) canvas.drawBitmap(qr, null, Rect(448, 690, 552, 794), ink)
                canvas.drawText("Scan QR to verify authenticity and current payment status.", 36f, 750f, ink)
                canvas.drawText("A PDF or screenshot alone is not proof of payment.", 36f, 767f, ink)
            } else {
                muted.textSize = 9f
                canvas.drawText("Continued on next page • FYNX Marketplace", 36f, 810f, muted)
            }
            document.finishPage(page)
        }
        val folder = File(context.cacheDir, "marketplace-receipts").apply { mkdirs() }
        val safeId = receipt.optString("receiptId").replace(Regex("[^A-Za-z0-9_-]"), "").take(80)
        val file = File(folder, "${safeId.ifBlank { "FYNX-checkout-receipt" }}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        qr?.recycle()
        file
    }
}

private suspend fun createMarketplaceReceiptPdf(context: Context, receipt: JSONObject): Result<File> {
    if (receipt.optString("receiptType") == "BUYER_CHECKOUT") return createMarketplaceCheckoutReceiptPdf(context, receipt)
    val buyer = receipt.optJSONObject("buyer") ?: JSONObject()
    val seller = receipt.optJSONObject("seller") ?: JSONObject()
    val buyerAvatar = try { receiptAvatar(context, buyer.optString("profilePhotoMediaId").takeIf { it.isNotBlank() && it != "null" }) } catch (_: Exception) { null }
    val sellerAvatar = try { receiptAvatar(context, seller.optString("profilePhotoMediaId").takeIf { it.isNotBlank() && it != "null" }) } catch (_: Exception) { null }
    return withContext(Dispatchers.IO) {
    runCatching {
        val product = receipt.optJSONObject("product") ?: JSONObject()
        val buyerName = buyer.optString("displayName").ifBlank { buyer.optString("username", "FYNX buyer") }
        val sellerName = seller.optString("displayName").ifBlank { seller.optString("username", "FYNX seller") }
        val verificationPath = receipt.optString("verificationPath")
        val verificationUrl = FynxBackendClient.baseUrl(context).trimEnd('/') + verificationPath
        val qr = qrBitmap(verificationUrl)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 12f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(71, 85, 105); textSize = 10f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); strokeWidth = 1f }
        canvas.drawColor(Color.WHITE)
        paint.color = Color.rgb(30, 64, 175); paint.textSize = 23f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FYNX", 36f, 48f, paint)
        paint.color = Color.rgb(15, 23, 42); paint.textSize = 16f
        canvas.drawText("MARKETPLACE RECEIPT", 36f, 76f, paint)
        muted.textSize = 10f
        canvas.drawText("Digitally signed • Verify before relying on this receipt", 36f, 96f, muted)
        canvas.drawLine(36f, 112f, 559f, 112f, line)
        paint.textSize = 11f; paint.typeface = Typeface.DEFAULT
        canvas.drawText("Receipt ID", 36f, 139f, muted)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(receipt.optString("receiptId"), 36f, 157f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Order ID: " + receipt.optString("orderId"), 36f, 178f, paint)
        canvas.drawText("Payment status: " + receipt.optString("paymentStatus"), 36f, 198f, paint)
        canvas.drawText("Payment reference: " + receipt.optString("paymentReference"), 36f, 218f, paint)
        val issued = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(java.time.Instant.parse(receipt.optString("issuedAt")).toEpochMilli())) }.getOrDefault(receipt.optString("issuedAt"))
        canvas.drawText("Issued: $issued", 36f, 238f, paint)
        canvas.drawLine(36f, 254f, 559f, 254f, line)
        paint.textSize = 12f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PARTICIPANTS", 36f, 278f, paint)
        drawReceiptAvatar(canvas, buyerAvatar, buyerName, 36f, 292f, paint)
        drawReceiptAvatar(canvas, sellerAvatar, sellerName, 310f, 292f, paint)
        paint.color = Color.rgb(15, 23, 42); paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(buyerName.take(32), 106f, 316f, paint)
        canvas.drawText(sellerName.take(32), 380f, 316f, paint)
        paint.color = Color.rgb(71, 85, 105); paint.textSize = 9f; paint.typeface = Typeface.DEFAULT
        canvas.drawText("Buyer", 106f, 334f, paint)
        canvas.drawText("Seller", 380f, 334f, paint)
        canvas.drawLine(36f, 365f, 559f, 365f, line)
        paint.color = Color.rgb(15, 23, 42); paint.textSize = 12f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PURCHASE DETAILS", 36f, 389f, paint)
        paint.typeface = Typeface.DEFAULT; paint.textSize = 11f
        canvas.drawText(product.optString("title", "FYNX marketplace item").take(64), 36f, 416f, paint)
        canvas.drawText("Quantity: " + product.optInt("quantity").toString(), 36f, 438f, paint)
        val currency = receipt.optString("currency", "NGN")
        fun money(key: String) = currency + " " + String.format(Locale.US, "%,.2f", receipt.optDouble(key, 0.0))
        val sellerReceipt = receipt.optString("receiptType") == "SELLER_SALE"
        val rows = if (sellerReceipt) listOf(
            "Gross merchandise" to money("subtotal"),
            "Delivery allocation" to money("deliveryFee"),
            "Seller-borne marketplace fee" to (currency + " " + String.format(Locale.US, "%,.2f", receipt.optDouble("marketplaceFeeSeller", 0.0))),
            "Discount allocated" to (currency + " " + String.format(Locale.US, "%,.2f", product.optDouble("discount", 0.0)))
        ) else listOf(
            "Subtotal" to money("subtotal"),
            "Delivery" to money("deliveryFee"),
            "Buyer-paid marketplace fee" to money("marketplaceFeeBuyer"),
            "Discount" to (currency + " " + String.format(Locale.US, "%,.2f", product.optDouble("discount", 0.0)))
        )
        var y = 470f
        for ((label, amount) in rows) {
            canvas.drawText(label, 36f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(amount, 559f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 23f
        }
        canvas.drawLine(36f, y, 559f, y, line)
        y += 29f
        paint.textSize = 15f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(if (sellerReceipt) "SELLER NET PROCEEDS" else "TOTAL PAID", 36f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(currency + " " + String.format(Locale.US, "%,.2f", if (sellerReceipt) receipt.optDouble("sellerNet", 0.0) else receipt.optDouble("buyerTotal", 0.0)), 559f, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 30f
        paint.textSize = 9f; paint.typeface = Typeface.DEFAULT; paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Cryptographic signature (HMAC-SHA256):", 36f, y, paint)
        y += 15f
        canvas.drawText(receipt.optString("signature").take(72), 36f, y, paint)
        if (qr != null) canvas.drawBitmap(qr, null, Rect(442, 690, 552, 800), paint)
        canvas.drawText("Scan to verify authenticity and current payment status.", 36f, 735f, paint)
        canvas.drawText("A PDF or screenshot alone is not proof of payment.", 36f, 754f, paint)
        canvas.drawText("FYNX verifies this receipt against its payment records.", 36f, 773f, paint)
        document.finishPage(page)
        val folder = File(context.cacheDir, "marketplace-receipts").apply { mkdirs() }
        val safeId = receipt.optString("receiptId").replace(Regex("[^A-Za-z0-9_-]"), "").take(80)
        val file = File(folder, "${safeId.ifBlank { "FYNX-receipt" }}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        buyerAvatar?.recycle()
        sellerAvatar?.recycle()
        qr?.recycle()
        file
    }
    }
}

private suspend fun saveReceiptToDownloads(context: Context, source: File): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FYNX Receipts")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create receipt in Downloads")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: error("Could not write receipt")
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
            uri
        } else {
            val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FYNX Receipts").apply { mkdirs() }
            val target = File(folder, source.name)
            source.copyTo(target, overwrite = true)
            Uri.fromFile(target)
        }
    }
}

private fun shareReceipt(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Verified FYNX Marketplace receipt")
        putExtra(Intent.EXTRA_TEXT, "Verify this receipt through the FYNX receipt verification link/QR code. Do not rely on an image or edited copy as proof of payment.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share FYNX receipt"))
}

@Composable
internal fun MarketplaceReceiptAction(context: Context, orderId: String) {
    var loading by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedButton(
        enabled = !loading,
        onClick = {
            loading = true
            error = null
            scope.launch {
                fetchMarketplaceReceipt(context, orderId)
                    .onSuccess { receipt = it }
                    .onFailure { error = it.message ?: "Verified receipt is not available yet." }
                loading = false
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) CircularProgressIndicator()
        else Text("View verified receipt")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    receipt?.let { data ->
        MarketplaceReceiptDialog(context, data, onClose = { receipt = null })
    }
}

@Composable
internal fun MarketplaceCheckoutReceiptAction(context: Context, checkoutId: String) {
    var loading by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedButton(
        enabled = !loading,
        onClick = {
            loading = true
            error = null
            scope.launch {
                fetchMarketplaceCheckoutReceipt(context, checkoutId)
                    .onSuccess { receipt = it }
                    .onFailure { error = it.message ?: "Checkout receipt is not available yet. Please retry." }
                loading = false
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) CircularProgressIndicator()
        else Text("View combined checkout receipt")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    receipt?.let { data -> MarketplaceReceiptDialog(context, data, onClose = { receipt = null }) }
}

@Composable
private fun MarketplaceReceiptDialog(context: Context, receipt: JSONObject, onClose: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val buyer = receipt.optJSONObject("buyer") ?: JSONObject()
    val seller = receipt.optJSONObject("seller") ?: JSONObject()
    val product = receipt.optJSONObject("product") ?: JSONObject()
    val currency = receipt.optString("currency", "NGN")
    val money = { key: String -> currency + " " + String.format(Locale.US, "%,.2f", receipt.optDouble(key, 0.0)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Verified FYNX receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val checkoutReceipt = receipt.optString("receiptType") == "BUYER_CHECKOUT"
                Text(receipt.optString("receiptId"), style = MaterialTheme.typography.titleSmall)
                if (checkoutReceipt) {
                    Text("Combined multi-seller checkout", style = MaterialTheme.typography.titleMedium)
                    val buyer = receipt.optJSONObject("buyer") ?: JSONObject()
                    Text("Buyer: " + buyer.optString("displayName", buyer.optString("username", "FYNX buyer")), style = MaterialTheme.typography.bodySmall)
                    val items = receipt.optJSONArray("items") ?: org.json.JSONArray()
                    for (index in 0 until minOf(items.length(), 5)) {
                        val item = items.optJSONObject(index) ?: JSONObject()
                        Text("${index + 1}. " + item.optString("title", "FYNX marketplace item"), style = MaterialTheme.typography.bodyMedium)
                        Text("Seller: " + item.optString("seller", "FYNX seller") + " • Qty " + item.optInt("quantity") + " • " + currency + " " + String.format(Locale.US, "%,.2f", item.optDouble("total")), style = MaterialTheme.typography.bodySmall)
                        Text("Fulfillment: " + item.optString("fulfillmentStatus", "UNKNOWN"), style = MaterialTheme.typography.labelSmall)
                    }
                    val remaining = (items.length() - 5).coerceAtLeast(0)
                    if (remaining > 0) Text("$remaining more item(s) included in the complete PDF receipt.", style = MaterialTheme.typography.bodySmall)
                    Text("Items: " + receipt.optInt("itemCount"))
                    Text("Subtotal: " + money("subtotal"))
                    Text("Delivery: " + money("deliveryFee"))
                    Text("Buyer-paid FYNX fee: " + money("marketplaceFee"))
                    Text("Payout status: " + receipt.optString("payoutStatus", "NOT_RECORDED"), style = MaterialTheme.typography.bodySmall)
                    Text("Total paid: " + money("buyerTotal"), style = MaterialTheme.typography.titleLarge)
                    Text("Paystack reference: " + receipt.optString("paymentReference"), style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Buyer", style = MaterialTheme.typography.labelSmall)
                            Text(buyer.optString("displayName", buyer.optString("username")), style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Seller", style = MaterialTheme.typography.labelSmall)
                            Text(seller.optString("displayName", seller.optString("username")), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(product.optString("title", "FYNX marketplace item"), style = MaterialTheme.typography.titleMedium)
                    Text("Quantity: " + product.optInt("quantity"))
                    Text("Subtotal: " + money("subtotal"))
                    Text("Delivery: " + money("deliveryFee"))
                    Text((if (receipt.optString("receiptType") == "SELLER_SALE") "Seller-borne FYNX fee: " else "Buyer-paid FYNX fee: ") + currency + " " + String.format(Locale.US, "%,.2f", receipt.optDouble(if (receipt.optString("receiptType") == "SELLER_SALE") "marketplaceFeeSeller" else "marketplaceFeeBuyer", 0.0)))
                    Text((if (receipt.optString("receiptType") == "SELLER_SALE") "Seller net proceeds: " else "Total paid: ") + currency + " " + String.format(Locale.US, "%,.2f", if (receipt.optString("receiptType") == "SELLER_SALE") receipt.optDouble("sellerNet", 0.0) else receipt.optDouble("buyerTotal", 0.0)), style = MaterialTheme.typography.titleLarge)
                }
                Text("Payment status: " + receipt.optString("paymentStatus"))
                Text("Receipt authenticity is verified by FYNX. The signature and QR link can be checked independently.", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    createMarketplaceReceiptPdf(context, receipt).onSuccess { file ->
                        shareReceipt(context, file)
                        message = "The signed PDF is ready to share."
                    }.onFailure { message = it.message ?: "Could not create receipt PDF." }
                    busy = false
                }
            }) { if (busy) CircularProgressIndicator() else Text("Share PDF") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        createMarketplaceReceiptPdf(context, receipt).onSuccess { file ->
                            saveReceiptToDownloads(context, file).onSuccess { message = "Saved to Downloads/FYNX Receipts." }.onFailure { message = it.message ?: "Could not save receipt." }
                        }.onFailure { message = it.message ?: "Could not create receipt PDF." }
                        busy = false
                    }
                }) { Text("Download PDF") }
                TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
            }
        }
    )
}
