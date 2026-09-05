package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun FynxAdvertisingAiPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FYNX AI Ad Coach", style = MaterialTheme.typography.headlineSmall)
        Text("Get help improving your advert, audience and budget without giving AI control of your money or campaign.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = request,
            onValueChange = { request = it.take(3000); error = null },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 7,
            label = { Text("What do you want help with?") },
            placeholder = { Text("Example: Help me write an advert for my shoe business in Port Harcourt.") }
        )
        Button(
            enabled = request.isNotBlank() && !loading,
            onClick = {
                loading = true; error = null; reply = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        FynxBackendClient.postJson(context, "/api/advertising/ai-advice", JSONObject().put("request", request.trim()).toString())
                    }
                    result.onSuccess { reply = JSONObject(it).optString("reply").ifBlank { "No advice returned." } }
                        .onFailure { error = "FYNX AI is temporarily unavailable. Please try again." }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Thinking…" else "Ask FYNX AI") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        reply?.let {
            Card(Modifier.fillMaxWidth()) { LazyColumn(Modifier.padding(14.dp)) { item { Text(it) } } }
        }
    }
}
