package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Displays the user's selected FYNX customization image as a subtle app-wide backdrop. */
@Composable
fun FynxCustomizationBackground(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val asset = FynxPreferencesStore.loadAsset(context)
    var bitmap by remember(asset) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(asset) {
        bitmap = withContext(Dispatchers.IO) {
            asset?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it)).use { input -> BitmapFactory.decodeStream(input) } }.getOrNull() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.055f),
                contentScale = ContentScale.Crop
            )
        }
        content()
    }
}
