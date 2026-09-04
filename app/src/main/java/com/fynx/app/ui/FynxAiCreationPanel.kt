package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FYNX AI Creation Layer. Text creation uses the existing authenticated FYNX AI
 * backend; no provider key or private FYNX data is exposed to the Android app.
 */
@Composable
fun FynxAiCreationPanel(
    onUseCaptionForPost: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("caption") }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val modes = listOf(
        "caption" to "Create caption",
        "rewrite" to "Improve caption",
        "ideas" to "Post ideas",
        "creative" to "Creative brief",
        "product" to "Product description"
    )

    fun requestCreation() {
        val promptInput = input.trim()
        if (promptInput.isEmpty() || loading) return
        val instruction = when (mode) {
            "rewrite" -> "Improve this FYNX social-media caption. Keep the original meaning, make it natural, engaging and concise. Return only the finished caption.\n\nCaption:\n$promptInput"
            "ideas" -> "Give 5 safe, original FYNX post ideas based on this topic. Keep each idea short and practical.\n\nTopic:\n$promptInput"
            "creative" -> "Create a practical creative brief for a FYNX social post from this idea. Include: a short hook, visual concept, caption direction, suggested call-to-action, and 3 safe content variations. Do not invent personal facts. Keep it concise and ready to use.\n\nIdea:\n$promptInput"
            "product" -> "Write a clear, persuasive marketplace product description from these seller notes. Do not invent specifications, guarantees, prices or facts. Return only the finished description.\n\nSeller notes:\n$promptInput"
            else -> "Create a natural, engaging FYNX social-media caption from this idea. Do not invent personal facts. Return only the finished caption.\n\nIdea:\n$promptInput"
        }
        val capability = if (mode == "product") {
            FynxAiCapability.MARKETPLACE_ASSIST
        } else {
            FynxAiCapability.MEDIA_ASSIST
        }
        val decision = FynxFutureIntelligencePolicy.authorize(
            permissions = listOf(
                FynxAiPermission(
                    capability = capability,
                    allowedScopes = setOf(FynxAiDataScope.NONE),
                    enabled = true
                )
            ),
            request = FynxAiRequest(
                capability = capability,
                prompt = instruction,
                requestedScopes = setOf(FynxAiDataScope.NONE)
            )
        )
        if (!decision.allowed) {
            error = "I couldn't process that creation request safely."
            return
        }
        loading = true
        error = null
        scope.launch {
            val response = withContext(Dispatchers.IO) {
                AiAssistantClient.sendMessage(context, instruction)
            }
            response.onSuccess { result = it.trim() }
                .onFailure { error = "FYNX AI is temporarily unavailable. Please try again." }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("AI Creation", style = MaterialTheme.typography.headlineSmall)
                Text("Create with FYNX AI without leaving FYNX.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { (key, label) ->
                        FilterChip(
                            selected = mode == key,
                            onClick = { mode = key; result = ""; error = null },
                            label = { Text(label) },
                            enabled = !loading
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(FynxSecurityFoundation.MAX_AI_PROMPT_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            enabled = !loading,
            shape = FynxDesign.ControlShape,
            placeholder = { Text(when (mode) {
                "rewrite" -> "Paste the caption you want improved…"
                "ideas" -> "What do you want to post about?"
                "creative" -> "Describe the content idea you want to develop…"
                "product" -> "Enter your real product details…"
                else -> "Describe the post you want to create…"
            }) }
        )

        Button(
            onClick = ::requestCreation,
            enabled = !loading && input.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (loading) "Creating…" else "Create with AI")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (result.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.CardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(result)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        if (mode == "caption" || mode == "rewrite") {
                            Button(onClick = { onUseCaptionForPost(result) }) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Use in post")
                            }
                        }
                    }
                }
            }
        }
    }
}
