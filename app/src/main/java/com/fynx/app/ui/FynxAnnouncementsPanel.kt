package com.fynx.app.ui

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxAnnouncementsPanel() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<FynxAdminClient.Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { loading = true; FynxAdminClient.announcements(context).onSuccess { items = it; error = null }.onFailure { error = it.message ?: "Unable to load announcements." }; loading = false }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Official FYNX Announcements", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Important updates published by the FYNX team.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
        when { loading -> CircularProgressIndicator(); error != null -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Could not load announcements"); Spacer(Modifier.height(6.dp)); Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)); TextButton(onClick = { refresh++ }) { Text("Retry") } } }; items.isEmpty() -> Text("No official announcements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant); else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { items.forEach { announcement -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(announcement.title, style = MaterialTheme.typography.titleMedium); if (announcement.priority != "NORMAL") AssistChip(onClick = {}, label = { Text(announcement.priority) }) }; Spacer(Modifier.height(8.dp)); Text(announcement.body); if (announcement.publishedAt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(announcement.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
        }
    }
}

@Composable
fun FynxAdminControlCenterPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<FynxAdminClient.Dashboard?>(null) }
    var admins by remember { mutableStateOf<List<FynxAdminClient.Admin>>(emptyList()) }
    var protectionCases by remember { mutableStateOf<List<FynxAdminClient.ProtectionCase>>(emptyList()) }
    var appeals by remember { mutableStateOf<List<FynxAdminClient.AppealReview>>(emptyList()) }
    var appealsLoading by remember { mutableStateOf(false) }
    var appealsError by remember { mutableStateOf<String?>(null) }
    var refreshAppeals by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var protectionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var protectionError by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var targetQuery by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<FynxSocialClient.User>>(emptyList()) }
    var targetMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshAdmins by remember { mutableIntStateOf(0) }
    var refreshProtection by remember { mutableIntStateOf(0) }
    var marketplaceDrivers by remember { mutableStateOf<List<FynxAdminClient.MarketplaceDriver>>(emptyList()) }
    var driversLoading by remember { mutableStateOf(false) }
    var driversError by remember { mutableStateOf<String?>(null) }
    var refreshDrivers by remember { mutableIntStateOf(0) }
    var revenueReport by remember { mutableStateOf<FynxAdminClient.RevenueReport?>(null) }
    var revenueLoading by remember { mutableStateOf(false) }
    var revenueError by remember { mutableStateOf<String?>(null) }
    var refreshRevenue by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { FynxAdminClient.dashboard(context).onSuccess { dashboard = it; error = null }.onFailure { error = it.message ?: "Admin access required." }; loading = false }
    LaunchedEffect(refreshAdmins, dashboard?.role) { if (dashboard != null) FynxAdminClient.admins(context).onSuccess { admins = it } }
    LaunchedEffect(refreshAppeals, dashboard?.role) {
        if (dashboard != null) {
            appealsLoading = true; appealsError = null
            FynxAdminClient.appeals(context).onSuccess { appeals = it }.onFailure { appealsError = it.message ?: "Appeals unavailable." }
            appealsLoading = false
        }
    }

    LaunchedEffect(refreshProtection, dashboard?.role) {
        if (dashboard != null) {
            protectionLoading = true
            protectionError = null
            FynxAdminClient.marketplaceProtectionCases(context)
                .onSuccess { protectionCases = it }
                .onFailure { protectionError = it.message ?: "Protection cases unavailable." }
            protectionLoading = false
        }
    }

    LaunchedEffect(refreshDrivers, dashboard?.role) {
        if (dashboard != null) {
            driversLoading = true
            driversError = null
            FynxAdminClient.marketplaceDrivers(context)
                .onSuccess { marketplaceDrivers = it }
                .onFailure { driversError = it.message ?: "Approved delivery drivers unavailable." }
            driversLoading = false
        }
    }

    LaunchedEffect(refreshRevenue, dashboard?.role) {
        if (dashboard?.role == "OWNER" || dashboard?.role == "ADMIN") {
            revenueLoading = true
            revenueError = null
            FynxAdminClient.revenue(context)
                .onSuccess { revenueReport = it }
                .onFailure { revenueError = it.message ?: "FYNX revenue report unavailable." }
            revenueLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Owner / Admin Control Center", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator() else if (dashboard == null) Text(error ?: "Admin access required.", color = MaterialTheme.colorScheme.error) else {
            val d = dashboard!!
            Text("Role: ${d.role}", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Users", d.users); StatCard("Reports", d.openReports) }; Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Appeals", d.openAppeals); StatCard("Safety / 24h", d.safetyEvents24h) }

            Spacer(Modifier.height(20.dp))
            Text("FYNX Support Centre", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("Handle private user complaints and monitor your support team.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FynxSupportHubPanel(staffOnly = true)

            if (d.role == "OWNER" || d.role == "ADMIN") {
                Spacer(Modifier.height(20.dp))
                Text("FYNX Business Revenue", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    "Marketplace commission earned by FYNX is tracked separately from customer wallet balances and seller payouts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = 0.24f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Marketplace commission ledger", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            TextButton(onClick = { refreshRevenue++ }, enabled = !revenueLoading) { Text("Refresh") }
                        }
                        if (revenueLoading && revenueReport == null) {
                            CircularProgressIndicator()
                        } else if (revenueError != null && revenueReport == null) {
                            Text(revenueError!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { refreshRevenue++ }, enabled = !revenueLoading) { Text("Retry revenue report") }
                        } else {
                            val fees = revenueReport?.breakdown.orEmpty().filter { it.source == "MARKETPLACE_FEE" }
                            if (fees.isEmpty()) {
                                Text("No Marketplace commission has been recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                fees.map { it.currency }.distinct().sorted().forEach { currency ->
                                    val rows = fees.filter { it.currency == currency }
                                    fun totalFor(status: String) = rows.filter { it.status.equals(status, true) }.sumOf { it.amount }
                                    fun countFor(status: String) = rows.filter { it.status.equals(status, true) }.sumOf { it.transactions }
                                    Text(currency, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Settled commission")
                                        Text(currency + " " + String.format(java.util.Locale.US, "%,.2f", totalFor("SETTLED")), color = FynxDesign.ClassicGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Pending commission")
                                        Text(currency + " " + String.format(java.util.Locale.US, "%,.2f", totalFor("PENDING")))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Reversed / refunded")
                                        Text(currency + " " + String.format(java.util.Locale.US, "%,.2f", totalFor("REVERSED") + totalFor("REFUNDED")))
                                    }
                                    Text(
                                        "Settled transactions: ${countFor("SETTLED")}  •  Reversals: ${countFor("REVERSED")}  •  Refunds: ${countFor("REFUNDED")}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (revenueError != null) Text(revenueError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Revenue is recognised from the Marketplace fee ledger with duplicate-source protection. Pending, reversed and refunded entries are reported separately.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (d.role == "OWNER" || d.role == "ADMIN") { Spacer(Modifier.height(20.dp)); Text("Music Library", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); FynxMusicAdminPanel(); Spacer(Modifier.height(20.dp)) }; Text("Applications & Appeals", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Review account appeals and restore an account when an appeal is accepted.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); if (appealsLoading) CircularProgressIndicator() else if (appealsError != null) { Text(appealsError!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refreshAppeals++ }) { Text("Retry") } } else if (appeals.isEmpty()) { Text("No open appeals require review.", color = MaterialTheme.colorScheme.onSurfaceVariant) } else { appeals.take(20).forEach { appeal -> Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("${appeal.displayName.ifBlank { "FYNX user" }}  @${appeal.username}", style = MaterialTheme.typography.titleMedium); Text("Appeal #${appeal.id} • ${appeal.status}", color = MaterialTheme.colorScheme.primary); Text(appeal.subject); if (appeal.details.isNotBlank()) Text(appeal.details, maxLines = 5); Text("Current account status: ${appeal.accountStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = { scope.launch { FynxAdminClient.reviewAppeal(context, appeal.id, "UNDER_REVIEW", "Appeal moved to review.").onSuccess { statusMessage = "Appeal moved to review."; refreshAppeals++ }.onFailure { statusMessage = it.message ?: "Could not update appeal." } } }) { Text("Review") }; Button(onClick = { scope.launch { FynxAdminClient.reviewAppeal(context, appeal.id, "ACCEPTED", "Appeal accepted; account restored to ACTIVE.").onSuccess { statusMessage = "Appeal accepted and account restored."; refreshAppeals++ }.onFailure { statusMessage = it.message ?: "Could not restore account." } } }) { Text("Approve & restore") }; OutlinedButton(onClick = { scope.launch { FynxAdminClient.reviewAppeal(context, appeal.id, "REJECTED", "Appeal rejected after review.").onSuccess { statusMessage = "Appeal rejected."; refreshAppeals++ }.onFailure { statusMessage = it.message ?: "Could not reject appeal." } } }) { Text("Reject") } } } } } }; Spacer(Modifier.height(20.dp)); Text("Marketplace protection cases", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Review open buyer/seller disputes before money is released or refunded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (protectionLoading) CircularProgressIndicator() else if (protectionError != null) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(protectionError!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refreshProtection++ }) { Text("Retry") } } }
            } else if (protectionCases.isEmpty()) {
                Text("No protection cases currently require review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                protectionCases.take(20).forEach { case ->
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${case.caseType} • ${case.role}", style = MaterialTheme.typography.titleMedium)
                                Text(case.status, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Order: ${case.orderId}", style = MaterialTheme.typography.bodySmall)
                            Text("Amount: ${case.currency} ${case.totalAmount}", style = MaterialTheme.typography.bodySmall)
                            if (case.reason.isNotBlank()) Text(case.reason)
                            if (case.details.isNotBlank()) Text(case.details, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                            Text("Order status: ${case.orderStatus}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    statusMessage = "Resolving case for buyer..."
                                    scope.launch {
                                        FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "BUYER")
                                            .onSuccess { result -> statusMessage = "Buyer resolution: $result"; refreshProtection++ }
                                            .onFailure { statusMessage = it.message ?: "Buyer resolution failed." }
                                    }
                                }) { Text("Buyer / refund") }
                                OutlinedButton(onClick = {
                                    statusMessage = "Resolving case for seller..."
                                    scope.launch {
                                        FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "SELLER")
                                            .onSuccess { result -> statusMessage = "Seller resolution: $result"; refreshProtection++ }
                                            .onFailure { statusMessage = it.message ?: "Seller resolution failed." }
                                    }
                                }) { Text("Seller / release") }
                                OutlinedButton(onClick = {
                                    statusMessage = "Cancelling case..."
                                    scope.launch {
                                        FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "CANCEL")
                                            .onSuccess { result -> statusMessage = "Case cancelled: $result"; refreshProtection++ }
                                            .onFailure { statusMessage = it.message ?: "Case cancellation failed." }
                                    }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Approved delivery drivers", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Approve trusted delivery accounts here. Revocation is blocked while a driver holds a parcel in active custody.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (driversLoading) CircularProgressIndicator()
            driversError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refreshDrivers++ }) { Text("Retry") } }
            if (!driversLoading && driversError == null && marketplaceDrivers.isEmpty()) Text("No driver accounts have been approved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            marketplaceDrivers.forEach { driver ->
                Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(driver.displayName.ifBlank { "FYNX user" }, style = MaterialTheme.typography.titleMedium)
                            Text("@${driver.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (driver.active) "Approved" else "Approval revoked", color = if (driver.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(enabled = !driversLoading, onClick = {
                            scope.launch {
                                statusMessage = if (driver.active) "Revoking driver approval..." else "Restoring driver approval..."
                                FynxAdminClient.setMarketplaceDriverApproval(context, driver.userId, !driver.active)
                                    .onSuccess { statusMessage = if (driver.active) "Driver approval revoked." else "Driver approved."; refreshDrivers++ }
                                    .onFailure { statusMessage = it.message ?: "Driver approval could not be changed." }
                            }
                        }) { Text(if (driver.active) "Revoke" else "Approve") }
                    }
                }
            }

            if (d.role == "OWNER") { Spacer(Modifier.height(20.dp)); Text("Administration", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Owner-only administrator access.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text("Current Admins", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); if (admins.isEmpty()) Text("No additional Admins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else admins.forEach { admin -> Card(Modifier.fillMaxWidth().padding(top = 6.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(admin.displayName.ifBlank { "FYNX Admin" }, style = MaterialTheme.typography.titleMedium); Text("@${admin.username}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { statusMessage = "Removing @${admin.username}..."; scope.launch { FynxAdminClient.revokeAdmin(context, admin.id).onSuccess { statusMessage = "Admin access removed."; refreshAdmins++ }.onFailure { statusMessage = it.message ?: "Admin removal failed." } } }) { Text("Remove") } } } } }
            Spacer(Modifier.height(20.dp)); Text("Manage an account", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(targetQuery, { targetQuery = it }, Modifier.fillMaxWidth(), label = { Text("Search username") }, singleLine = true); Spacer(Modifier.height(6.dp)); Button(enabled = targetQuery.isNotBlank(), onClick = { targetMessage = "Searching..."; scope.launch { FynxSocialClient.searchUsers(context, targetQuery).onSuccess { targets = it; targetMessage = if (it.isEmpty()) "No users found." else null }.onFailure { targetMessage = it.message ?: "User search failed." } } }) { Text("Find user") }; targetMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            targets.take(5).forEach { user ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(user.displayName.ifBlank { "FYNX user" }, style = MaterialTheme.typography.titleMedium)
                        Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    FynxAdminClient.setAccountStatus(context, user.id, "LOCKED")
                                        .onSuccess { statusMessage = "@${user.username} locked." }
                                        .onFailure { statusMessage = it.message ?: "Action failed." }
                                }
                            }) { Text("Lock") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    FynxAdminClient.setAccountStatus(context, user.id, "ACTIVE")
                                        .onSuccess { statusMessage = "@${user.username} restored." }
                                        .onFailure { statusMessage = it.message ?: "Action failed." }
                                }
                            }) { Text("Activate") }
                            if (d.role == "OWNER") OutlinedButton(onClick = {
                                scope.launch {
                                    FynxAdminClient.grantAdmin(context, user.id)
                                        .onSuccess { statusMessage = "@${user.username} is now an Admin."; refreshAdmins++ }
                                        .onFailure { statusMessage = it.message ?: "Admin grant failed." }
                                }
                            }) { Text("Make Admin") }
                            OutlinedButton(onClick = {
                                val currentlyApproved = marketplaceDrivers.any { it.userId == user.id && it.active }
                                scope.launch {
                                    statusMessage = if (currentlyApproved) "Revoking driver approval..." else "Approving delivery driver..."
                                    FynxAdminClient.setMarketplaceDriverApproval(context, user.id, !currentlyApproved)
                                        .onSuccess {
                                            statusMessage = if (currentlyApproved) "@${user.username} is no longer an approved driver." else "@${user.username} is an approved driver."
                                            refreshDrivers++
                                        }
                                        .onFailure { statusMessage = it.message ?: "Driver approval could not be changed." }
                                }
                            }) { Text(if (marketplaceDrivers.any { it.userId == user.id && it.active }) "Revoke Driver" else "Approve Driver") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp)); Text("Publish official announcement", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4); Spacer(Modifier.height(8.dp)); Button(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { statusMessage = "Publishing..."; scope.launch { FynxAdminClient.publishAnnouncement(context, title, body, "NORMAL").onSuccess { title = ""; body = ""; statusMessage = "Announcement published." }.onFailure { statusMessage = it.message ?: "Publish failed." } } }) { Text("Publish") }; statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: Int) { Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value.toString(), style = MaterialTheme.typography.headlineSmall) } } }