package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxVisibleUpdatesPanel(currentUsername: String, onOpenStories: () -> Unit, onOpenAi: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(onClick = onOpenAi, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(52.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Icon(Icons.Default.AutoAwesome, "FYNX AI Assistant", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp).padding(14.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FYNX AI Assistant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Ask, plan, learn and get help inside FYNX", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, "Open FYNX AI", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Card(onClick = onOpenStories, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("See all", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FynxStatusPreviewCircle(currentUsername, "Your status", true, onOpenStories)
                    FynxStatusPreviewCircle("FYNX", "Create status", false, onOpenStories)
                    Column(Modifier.weight(1f)) {
                        Text("Your status stays here", fontWeight = FontWeight.SemiBold)
                        Text("Text, photo, video and voice", color = FynxDesign.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxStatusPreviewCircle(name: String, label: String, active: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(3.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (active) FynxProfileImage(name, FynxPreferencesStore.loadProfilePhoto(context), Modifier.size(54.dp))
                else Icon(Icons.Default.CameraAlt, "Create status", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
