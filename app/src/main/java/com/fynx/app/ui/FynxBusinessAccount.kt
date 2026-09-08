package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Authenticated business identity connected to the existing marketplace and advertising stack. */
@Composable
fun FynxBusinessAccountPanel(
    onBack: () -> Unit = {},
    onOpenAdvertising: () -> Unit = {},
    onOpenDashboard: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            FynxBusinessClient.load(context).onSuccess { profile ->
                if (profile != null) {
                    name = profile.businessName
                    username = profile.businessUsername
                    category = profile.category
                    description = profile.description
                    location = profile.location
                    phone = profile.phone
                    website = profile.website
                    verified = profile.verified
                }
            }.onFailure { message = it.message ?: "Unable to load business profile." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Business Account", style = MaterialTheme.typography.headlineSmall)
        Text("One professional business identity for FYNX Marketplace, social posts and advertising.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Business name") }, singleLine = true)
                OutlinedTextField(username, { username = it.take(32).removePrefix("@") }, Modifier.fillMaxWidth(), label = { Text("Business username") }, singleLine = true, prefix = { Text("@") })
                OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Business description") }, minLines = 3)
                OutlinedTextField(location, { location = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Location") }, singleLine = true)
                OutlinedTextField(phone, { phone = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Business phone") }, singleLine = true)
                OutlinedTextField(website, { website = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Website (HTTPS)") }, singleLine = true)
                if (verified) Text("✓ Verified business", color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = {
                    saving = true
                    message = null
                    scope.launch {
                        FynxBusinessClient.save(context, name.trim(), username.trim().removePrefix("@"), category.trim(), description.trim(), location.trim(), phone.trim(), website.trim())
                            .onSuccess { profile ->
                                verified = profile.verified
                                message = "Business profile saved."
                            }
                            .onFailure { message = it.message ?: "Unable to save business profile." }
                        saving = false
                    }
                },
                enabled = !loading && !saving && name.isNotBlank() && username.trim().removePrefix("@").isNotBlank() && category.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(if (saving) "Saving…" else "Save Business") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenAdvertising, modifier = Modifier.weight(1f)) { Text("Advertise") }
            Button(onClick = onOpenDashboard, modifier = Modifier.weight(1f)) { Text("Ad Dashboard") }
        }
        message?.let { Text(it, color = if (it.contains("unable", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
}
