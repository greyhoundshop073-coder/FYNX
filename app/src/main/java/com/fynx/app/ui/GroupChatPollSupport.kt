package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reusable poll composer for Group Chat. It uses the existing group poll API;
 * it does not create a second polling backend or message model.
 */
@Composable
fun GroupChatPollDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    context: Context,
    groupId: String,
    scope: CoroutineScope,
    onError: (String) -> Unit = {}
) {
    if (!visible) return
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create poll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Question") },
                    singleLine = true
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            options = options.mapIndexed { i, old -> if (i == index) next else old }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Option ${index + 1}") },
                        singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = options.size < 5,
                        onClick = { options = options + "" }
                    ) { Text("Add option") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                onClick = {
                    val clean = options.map(String::trim).filter(String::isNotBlank).distinct().take(5)
                    scope.launch {
                        FynxGroupRemoteClient.createPoll(context, groupId, question.trim(), clean, false)
                            .onSuccess { onCreated() }
                            .onFailure { onError(it.message ?: "Poll could not be created") }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
