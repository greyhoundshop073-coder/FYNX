package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun HomePanel(currentUsername: String = "preview", onOpenChats: () -> Unit = {}, onOpenStories: () -> Unit = {}, onOpenProfile: () -> Unit = {}, onOpenMarketplace: () -> Unit = {}, onOpenNotifications: () -> Unit = {}, onOpenFindPeople: () -> Unit = {}, onOpenAi: () -> Unit = {}) {
    val context = LocalContext.current
    val displayUsername = currentUsername.trim().removePrefix("@").ifBlank { "preview" }