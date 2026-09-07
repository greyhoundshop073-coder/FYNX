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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Business-account presentation layer for FYNX.
 * Uses the existing social identity and existing marketplace/advertising systems;
 * it intentionally does not create a second seller or advertising stack.
 * Persistence is enabled by the existing authenticated profile layer in the next
 * integration step; this screen keeps all fields explicit and ready for that API.
 */
@Composable
fun FynxBusinessAccountPanel(
    initialName: String = "",
    initialUsername: String = "",
    initialCategory: String = "",
    initialDescription: String = "",
    initialLocation: String = "",
    initialPhone: String = "",
    initialWebsite: String = "",
    onBack: () -> Unit = {},
    onSave: (name: String, username: String, category: String, description: String, location: String, phone: String, website: String) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    var name by remember { mutableStateOf(initialName) }
    var username by remember { mutableStateOf(initialUsername) }
    var category by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf(initialDescription) }
    var location by remember { mutableStateOf(initialLocation) }
    var phone by remember { mutableStateOf(initialPhone) }
    var website by remember { mutableStateOf(initialWebsite) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Business Account", style = MaterialTheme.typography.headlineSmall)
        Text("Create a professional FYNX business identity connected to your existing profile, marketplace and advertising tools.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Business name") }, singleLine = true)
                OutlinedTextField(username, { username = it.take(32).removePrefix("@") }, Modifier.fillMaxWidth(), label = { Text("Business username") }, singleLine = true, prefix = { Text("@") })
                OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Business description") }, minLines = 3)
                OutlinedTextField(location, { location = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Location") }, singleLine = true)
                OutlinedTextField(phone, { phone = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Business phone") }, singleLine = true)
                OutlinedTextField(website, { website = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Website") }, singleLine = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = { onSave(name.trim(), username.trim().removePrefix("@"), category.trim(), description.trim(), location.trim(), phone.trim(), website.trim()) },
                enabled = name.isNotBlank() && username.trim().removePrefix("@").isNotBlank() && category.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text("Save Business") }
        }
    }
}
