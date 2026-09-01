package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FynxAuthGate(
    onAuthenticated: (String) -> Unit
) {
    FynxTheme {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = FynxDesign.SelectedContainer
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure FYNX account",
                            modifier = Modifier.padding(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "FYNX",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect. Share. Discover.",
                        color = FynxDesign.TextSecondary
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { onAuthenticated("new_user") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = FynxDesign.ControlShape
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Create account")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onAuthenticated("user") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = FynxDesign.ControlShape
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Log in")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your account keeps your FYNX profile and settings together.",
                        color = FynxDesign.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
