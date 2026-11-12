package com.fynx.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FynxAnnouncementsPanel(ownerAccess: Boolean = false) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var items by remember { mutableStateOf<List<FynxAdminClient.Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        FynxAdminClient.announcements(context)
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message ?: "Unable to load announcements." }
        loading = false
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .18f)) {
                        Icon(androidx.compose.material.icons.Icons.Default.Campaign, contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(10.dp).size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Official FYNX Announcements", style = MaterialTheme.typography.headlineSmall,
                            color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Official news from the FYNX team", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .88f))
                    }
                }
                Text("Product news, service notices and community updates—all in one trusted place.",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .94f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (ownerAccess) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue.copy(alpha = 0.06f)),
                border = BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = 0.22f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Owner music tools", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Publish and manage the official FYNX music catalogue. Only the FYNX OWNER can change official tracks.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    FynxMusicAdminPanel()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Latest announcements", style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${items.size} published updates", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { refresh++ }, enabled = !loading) {
                Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp)); Text("Refresh")
            }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Updates are temporarily unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { refresh++ }) { Text("Try again") }
                }
            }
            items.isEmpty() -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue.copy(alpha = .06f))) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Default.Campaign, contentDescription = null,
                        tint = FynxDesign.AzureBlue, modifier = Modifier.size(36.dp))
                    Text("No announcements yet", style = MaterialTheme.typography.titleMedium)
                    Text("Official FYNX updates will appear here when published.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> items.forEach { announcement ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = .14f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("FYNX OFFICIAL", style = MaterialTheme.typography.labelSmall,
                                color = FynxDesign.AzureBlue, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            if (announcement.priority != "NORMAL") {
                                AssistChip(onClick = {}, label = { Text(announcement.priority.replace('_', ' ')) })
                            }
                        }
                        Text(announcement.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(announcement.body, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        announcement.mediaId?.takeIf { it.isNotBlank() }?.let { mediaId ->
                            FynxRemoteMedia(
                                mediaUrl = "/api/media/$mediaId",
                                type = "image",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                autoPlay = false
                            )
                        }
                        announcement.linkUrl.takeIf { it.isNotBlank() }?.let { link ->
                            TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                                Icon(androidx.compose.material.icons.Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open link")
                            }
                        }
                        if (announcement.publishedAt.isNotBlank()) {
                            Text(announcement.publishedAt, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
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
    var activity by remember { mutableStateOf<List<FynxAdminClient.AdminActivity>>(emptyList()) }
    var activityQuery by remember { mutableStateOf("") }
    var activityOffset by remember { mutableIntStateOf(0) }
    var activityHasMore by remember { mutableStateOf(false) }
    var activityLoading by remember { mutableStateOf(false) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var refreshActivity by remember { mutableIntStateOf(0) }
    var reports by remember { mutableStateOf<List<FynxAdminClient.AdminReport>>(emptyList()) }
    var reportsOffset by remember { mutableIntStateOf(0) }
    var reportsHasMore by remember { mutableStateOf(false) }
    var reportsLoading by remember { mutableStateOf(false) }
    var reportsError by remember { mutableStateOf<String?>(null) }
    var refreshReports by remember { mutableIntStateOf(0) }
    var appeals by remember { mutableStateOf<List<FynxAdminClient.AppealReview>>(emptyList()) }
    var appealsOffset by remember { mutableIntStateOf(0) }
    var appealsHasMore by remember { mutableStateOf(false) }
    var appealsLoading by remember { mutableStateOf(false) }
    var appealsError by remember { mutableStateOf<String?>(null) }
    var refreshAppeals by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var protectionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var protectionError by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var announcementMediaId by remember { mutableStateOf<String?>(null) }
    var announcementLinkUrl by remember { mutableStateOf("") }
    var announcementScheduleAt by remember { mutableStateOf("") }
    var announcementUploading by remember { mutableStateOf(false) }
    var adminAnnouncementItems by remember { mutableStateOf<List<FynxAdminClient.Announcement>>(emptyList()) }
    var adminAnnouncementsLoading by remember { mutableStateOf(false) }
    var adminAnnouncementsError by remember { mutableStateOf<String?>(null) }
    var refreshAdminAnnouncements by remember { mutableIntStateOf(0) }
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
    var refreshDashboard by remember { mutableIntStateOf(0) }
    var activeSection by remember { mutableStateOf("Overview") }
    var confirmationTitle by remember { mutableStateOf("") }
    var confirmationMessage by remember { mutableStateOf("") }
    var confirmationLabel by remember { mutableStateOf("Confirm") }
    var confirmationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestAdminConfirmation(title: String, message: String, label: String, action: () -> Unit) {
        confirmationTitle = title
        confirmationMessage = message
        confirmationLabel = label
        confirmationAction = action
    }

    val announcementImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (!mimeType.startsWith("image/")) {
                statusMessage = "Choose an image file for the announcement."
            } else {
                announcementUploading = true
                statusMessage = "Uploading announcement image..."
                scope.launch {
                    FynxProductionMessaging.uploadMedia(context, uri, mimeType)
                        .onSuccess { uploaded ->
                            announcementMediaId = uploaded.id
                            statusMessage = "Announcement image attached."
                        }
                        .onFailure { statusMessage = it.message ?: "Image upload failed." }
                    announcementUploading = false
                }
            }
        }
    }

    LaunchedEffect(refreshDashboard) {
        loading = true
        FynxAdminClient.dashboard(context)
            .onSuccess { dashboard = it; error = null }
            .onFailure { error = it.message ?: "Admin access required." }
        loading = false
    }
    LaunchedEffect(refreshAdmins, dashboard?.role) { if (dashboard != null) FynxAdminClient.admins(context).onSuccess { admins = it } }
    LaunchedEffect(refreshActivity, dashboard?.role, activeSection, activityOffset) {
        if (dashboard != null && activeSection == "Activity") {
            activityLoading = true
            activityError = null
            FynxAdminClient.activity(context, activityQuery, activityOffset)
                .onSuccess {
                    activity = it.items
                    activityHasMore = it.hasMore
                }
                .onFailure { activityError = it.message ?: "Activity history unavailable." }
            activityLoading = false
        }
    }

    LaunchedEffect(refreshAdminAnnouncements, dashboard?.role, activeSection) {
        if (dashboard != null && activeSection == "Announcements") {
            adminAnnouncementsLoading = true
            adminAnnouncementsError = null
            FynxAdminClient.adminAnnouncements(context)
                .onSuccess { adminAnnouncementItems = it }
                .onFailure { adminAnnouncementsError = it.message ?: "Announcement management is unavailable." }
            adminAnnouncementsLoading = false
        }
    }

    LaunchedEffect(refreshReports, dashboard?.role, activeSection, reportsOffset) {
        if (dashboard != null && activeSection == "Safety") {
            reportsLoading = true
            reportsError = null
            FynxAdminClient.reports(context, reportsOffset)
                .onSuccess { reports = it.items; reportsHasMore = it.hasMore }
                .onFailure { reportsError = it.message ?: "Reports unavailable." }
            reportsLoading = false
        }
    }
    LaunchedEffect(refreshAppeals, dashboard?.role, activeSection, appealsOffset) {
        if (dashboard != null && activeSection == "Safety") {
            appealsLoading = true
            appealsError = null
            FynxAdminClient.appeals(context, appealsOffset)
                .onSuccess { appeals = it.items; appealsHasMore = it.hasMore }
                .onFailure { appealsError = it.message ?: "Appeals unavailable." }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Card(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("FYNX CONTROL CENTER", style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Admin workspace", style = MaterialTheme.typography.headlineSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .18f)) {
                        Icon(androidx.compose.material.icons.Icons.Default.AdminPanelSettings, contentDescription = "Admin",
                            tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                }
                Text("Manage platform safety, Marketplace operations, people and official updates.",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .92f))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) CircularProgressIndicator()
        else if (dashboard == null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Admin access could not be verified", style = MaterialTheme.typography.titleMedium)
                    Text(error ?: "Sign in with an authorised owner or admin account.")
                    OutlinedButton(onClick = { refreshDashboard++ }) { Text("Retry access check") }
                }
            }
        } else {
            val d = dashboard!!
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(if (d.role == "OWNER") "OWNER ACCOUNT" else "ADMIN ACCOUNT") })
                TextButton(onClick = { refreshDashboard++ }, enabled = !loading) { Text("Refresh") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Users", d.users); StatCard("Open reports", d.openReports) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("Open appeals", d.openAppeals); StatCard("Safety actions · 24h", d.safetyActions24h) }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Safety", "Marketplace", "People", "Announcements", "Activity").forEach { section ->
                    FilterChip(selected = activeSection == section, onClick = { activeSection = section }, label = { Text(section) })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (activeSection == "Overview" && (d.role == "OWNER" || d.role == "ADMIN")) {
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

            if (activeSection == "Overview" && d.role == "OWNER") {
                Spacer(Modifier.height(20.dp))
                Text("Music Library", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FynxMusicAdminPanel()
            }
            if (activeSection == "Overview" && (d.role == "OWNER" || d.role == "ADMIN")) {
                Spacer(Modifier.height(20.dp))
                FynxMarketplaceSeasonalPromotionAdminPanel()
                Spacer(Modifier.height(20.dp))
            }
            if (activeSection == "Safety") {
                Text("Reported accounts", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Review account reports submitted by FYNX users. This queue currently covers account reports; content-report types are not presented as account reports.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                when {
                    reportsLoading -> CircularProgressIndicator()
                    reportsError != null -> {
                        Text(reportsError!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refreshReports++ }) { Text("Retry reports") }
                    }
                    reports.isEmpty() -> Text("No open account reports require review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        reports.forEach { report ->
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Reported account: @${report.targetUsername}", style = MaterialTheme.typography.titleMedium)
                                    Text("Report #${report.id} • ${report.status}", color = MaterialTheme.colorScheme.primary)
                                    Text("Submitted by: ${report.reporterDisplayName.ifBlank { "@${report.reporterUsername}" }}")
                                    Text("Reason: ${report.reason}")
                                    if (report.details.isNotBlank()) Text(report.details, maxLines = 5)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = {
                                            requestAdminConfirmation("Move report to review?", "Report #${report.id} will remain open while it is investigated.", "Start review") {
                                                scope.launch {
                                                    FynxAdminClient.reviewReport(context, report.id, "UNDER_REVIEW")
                                                        .onSuccess { statusMessage = "Report moved to review."; refreshReports++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not update report." }
                                                }
                                            }
                                        }) { Text("Review") }
                                        Button(onClick = {
                                            requestAdminConfirmation("Resolve this report?", "Mark report #${report.id} as resolved after completing the safety review.", "Resolve report") {
                                                scope.launch {
                                                    FynxAdminClient.reviewReport(context, report.id, "RESOLVED")
                                                        .onSuccess { statusMessage = "Report resolved."; reportsOffset = 0; refreshReports++; refreshDashboard++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not resolve report." }
                                                }
                                            }
                                        }) { Text("Resolve") }
                                        OutlinedButton(onClick = {
                                            requestAdminConfirmation("Dismiss this report?", "Dismiss report #${report.id} only if it does not require action.", "Dismiss report") {
                                                scope.launch {
                                                    FynxAdminClient.reviewReport(context, report.id, "DISMISSED")
                                                        .onSuccess { statusMessage = "Report dismissed."; reportsOffset = 0; refreshReports++; refreshDashboard++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not dismiss report." }
                                                }
                                            }
                                        }) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Showing ${reportsOffset + 1}–${reportsOffset + reports.size}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(enabled = reportsOffset > 0, onClick = { reportsOffset = (reportsOffset - 50).coerceAtLeast(0) }) { Text("Previous") }
                                TextButton(enabled = reportsHasMore && reportsOffset < 10000, onClick = { reportsOffset = (reportsOffset + 50).coerceAtMost(10000) }) { Text("Next") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Applications & Appeals", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Review account appeals and restore an account when an appeal is accepted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when {
                    appealsLoading -> CircularProgressIndicator()
                    appealsError != null -> {
                        Text(appealsError!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refreshAppeals++ }) { Text("Retry appeals") }
                    }
                    appeals.isEmpty() -> Text("No open appeals require review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        appeals.forEach { appeal ->
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${appeal.displayName.ifBlank { "FYNX user" }}  @${appeal.username}", style = MaterialTheme.typography.titleMedium)
                                    Text("Appeal #${appeal.id} • ${appeal.status}", color = MaterialTheme.colorScheme.primary)
                                    Text(appeal.subject)
                                    if (appeal.details.isNotBlank()) Text(appeal.details, maxLines = 5)
                                    Text("Current account status: ${appeal.accountStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = {
                                            requestAdminConfirmation("Move appeal to review?", "This marks appeal #${appeal.id} as under review. No account status is changed by this action.", "Confirm review") {
                                                scope.launch {
                                                    FynxAdminClient.reviewAppeal(context, appeal.id, "UNDER_REVIEW", "Appeal moved to review.")
                                                        .onSuccess { statusMessage = "Appeal moved to review."; refreshAppeals++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not update appeal." }
                                                }
                                            }
                                        }) { Text("Review") }
                                        Button(onClick = {
                                            requestAdminConfirmation("Accept appeal and restore account?", "This accepts appeal #${appeal.id} and asks the server to restore @${appeal.username} to ACTIVE. Confirm only after reviewing the appeal details.", "Approve & restore") {
                                                scope.launch {
                                                    FynxAdminClient.reviewAppeal(context, appeal.id, "ACCEPTED", "Appeal accepted; account restored to ACTIVE.")
                                                        .onSuccess { statusMessage = "Appeal accepted and account restored."; appealsOffset = 0; refreshAppeals++; refreshDashboard++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not restore account." }
                                                }
                                            }
                                        }) { Text("Approve & restore") }
                                        OutlinedButton(onClick = {
                                            requestAdminConfirmation("Reject this appeal?", "This rejects appeal #${appeal.id} for @${appeal.username}. The existing account status will remain subject to the server's decision.", "Reject appeal") {
                                                scope.launch {
                                                    FynxAdminClient.reviewAppeal(context, appeal.id, "REJECTED", "Appeal rejected after review.")
                                                        .onSuccess { statusMessage = "Appeal rejected."; appealsOffset = 0; refreshAppeals++; refreshDashboard++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not reject appeal." }
                                                }
                                            }
                                        }) { Text("Reject") }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Showing ${appealsOffset + 1}–${appealsOffset + appeals.size}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(enabled = appealsOffset > 0, onClick = { appealsOffset = (appealsOffset - 50).coerceAtLeast(0) }) { Text("Previous") }
                                TextButton(enabled = appealsHasMore && appealsOffset < 10000, onClick = { appealsOffset = (appealsOffset + 50).coerceAtMost(10000) }) { Text("Next") }
                            }
                        }
                    }
                }
            }
            if (activeSection == "Marketplace") {
            Spacer(Modifier.height(20.dp)); Text("Marketplace protection cases", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Review open buyer/seller disputes before money is released or refunded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    requestAdminConfirmation(
                                        "Resolve in the buyer's favour?",
                                        "This submits the buyer/refund resolution for order ${case.orderId}. The backend remains responsible for validating eligibility and recording the decision.",
                                        "Confirm buyer resolution"
                                    ) {
                                        statusMessage = "Resolving case for buyer..."
                                        scope.launch {
                                            FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "BUYER")
                                                .onSuccess { result -> statusMessage = "Buyer resolution: $result"; refreshProtection++ }
                                                .onFailure { statusMessage = it.message ?: "Buyer resolution failed." }
                                        }
                                    }
                                }) { Text("Buyer / refund") }
                                OutlinedButton(onClick = {
                                    requestAdminConfirmation(
                                        "Resolve in the seller's favour?",
                                        "This submits the seller/release resolution for order ${case.orderId}. The backend remains responsible for validating eligibility and recording the decision.",
                                        "Confirm seller resolution"
                                    ) {
                                        statusMessage = "Resolving case for seller..."
                                        scope.launch {
                                            FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "SELLER")
                                                .onSuccess { result -> statusMessage = "Seller resolution: $result"; refreshProtection++ }
                                                .onFailure { statusMessage = it.message ?: "Seller resolution failed." }
                                        }
                                    }
                                }) { Text("Seller / release") }
                                OutlinedButton(onClick = {
                                    requestAdminConfirmation(
                                        "Cancel this protection case?",
                                        "This submits a cancellation for the case on order ${case.orderId}. Confirm only if cancellation is the correct administrative outcome.",
                                        "Cancel case"
                                    ) {
                                        statusMessage = "Cancelling case..."
                                        scope.launch {
                                            FynxAdminClient.resolveMarketplaceProtectionCase(context, case.id, "CANCEL")
                                                .onSuccess { result -> statusMessage = "Case cancelled: $result"; refreshProtection++ }
                                                .onFailure { statusMessage = it.message ?: "Case cancellation failed." }
                                        }
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
                            val action = if (driver.active) "Revoke delivery-driver approval?" else "Approve this delivery driver?"
                            val detail = if (driver.active) "This asks the server to revoke @${driver.username}'s approval. Revocation may be blocked while the driver has a parcel in active custody." else "This asks the server to approve @${driver.username} as a Marketplace delivery driver."
                            requestAdminConfirmation(action, detail, if (driver.active) "Revoke driver" else "Approve driver") {
                                scope.launch {
                                    statusMessage = if (driver.active) "Revoking driver approval..." else "Restoring driver approval..."
                                    FynxAdminClient.setMarketplaceDriverApproval(context, driver.userId, !driver.active)
                                        .onSuccess { statusMessage = if (driver.active) "Driver approval revoked." else "Driver approved."; refreshDrivers++ }
                                        .onFailure { statusMessage = it.message ?: "Driver approval could not be changed." }
                                }
                            }
                        }) { Text(if (driver.active) "Revoke" else "Approve") }
                    }
                }
            }

            }
            if (activeSection == "People") {
            if (d.role == "OWNER") { Spacer(Modifier.height(20.dp)); Text("Administration", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Owner-only administrator access.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text("Current Admins", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); if (admins.isEmpty()) Text("No additional Admins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) else admins.forEach { admin -> Card(Modifier.fillMaxWidth().padding(top = 6.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(admin.displayName.ifBlank { "FYNX Admin" }, style = MaterialTheme.typography.titleMedium); Text("@${admin.username}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = {
                requestAdminConfirmation(
                    "Remove administrator access?",
                    "This will revoke @${admin.username}'s administrator privileges. The server will verify your owner permission.",
                    "Remove access"
                ) {
                    statusMessage = "Removing @${admin.username}..."
                    scope.launch {
                        FynxAdminClient.revokeAdmin(context, admin.id)
                            .onSuccess { statusMessage = "Admin access removed."; refreshAdmins++ }
                            .onFailure { statusMessage = it.message ?: "Admin removal failed." }
                    }
                }
            }) { Text("Remove") } } } } }
            Spacer(Modifier.height(20.dp)); Text("Manage an account", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); OutlinedTextField(targetQuery, { targetQuery = it }, Modifier.fillMaxWidth(), label = { Text("Search username") }, singleLine = true); Spacer(Modifier.height(6.dp)); Button(enabled = targetQuery.isNotBlank(), onClick = { targetMessage = "Searching..."; scope.launch { FynxSocialClient.searchUsers(context, targetQuery).onSuccess { targets = it; targetMessage = if (it.isEmpty()) "No users found." else null }.onFailure { targetMessage = it.message ?: "User search failed." } } }) { Text("Find user") }; targetMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            targets.take(5).forEach { user ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(user.displayName.ifBlank { "FYNX user" }, style = MaterialTheme.typography.titleMedium)
                        Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                requestAdminConfirmation(
                                    "Block Marketplace and group messaging?",
                                    "@${user.username} will be blocked from Marketplace purchases and sending group messages. This does not sign the user out or disable other FYNX features. Continue only after reviewing the account.",
                                    "Block Marketplace + messaging"
                                ) {
                                    scope.launch {
                                        FynxAdminClient.setAccountStatus(context, user.id, "LOCKED")
                                            .onSuccess { statusMessage = "@${user.username} blocked from Marketplace purchases and group messaging." }
                                            .onFailure { statusMessage = it.message ?: "Action failed." }
                                    }
                                }
                            }) { Text("Lock") }
                            OutlinedButton(onClick = {
                                requestAdminConfirmation(
                                    "Restore this account?",
                                    "@${user.username} will regain access to FYNX if the server accepts the change.",
                                    "Restore account"
                                ) {
                                    scope.launch {
                                        FynxAdminClient.setAccountStatus(context, user.id, "ACTIVE")
                                            .onSuccess { statusMessage = "@${user.username} restored." }
                                            .onFailure { statusMessage = it.message ?: "Action failed." }
                                    }
                                }
                            }) { Text("Activate") }
                            if (d.role == "OWNER") OutlinedButton(onClick = {
                                requestAdminConfirmation(
                                    "Grant administrator access?",
                                    "@${user.username} will receive FYNX administrator privileges. Only continue if this person is trusted to manage the platform.",
                                    "Grant access"
                                ) {
                                    scope.launch {
                                        FynxAdminClient.grantAdmin(context, user.id)
                                            .onSuccess { statusMessage = "@${user.username} is now an Admin."; refreshAdmins++ }
                                            .onFailure { statusMessage = it.message ?: "Admin grant failed." }
                                    }
                                }
                            }) { Text("Make Admin") }
                            OutlinedButton(onClick = {
                                val currentlyApproved = marketplaceDrivers.any { it.userId == user.id && it.active }
                                requestAdminConfirmation(
                                    if (currentlyApproved) "Revoke delivery-driver approval?" else "Approve this delivery driver?",
                                    if (currentlyApproved) "This asks the server to revoke @${user.username}'s approval. Revocation may be blocked while the driver has a parcel in active custody." else "This asks the server to approve @${user.username} as a Marketplace delivery driver.",
                                    if (currentlyApproved) "Revoke driver" else "Approve driver"
                                ) {
                                    scope.launch {
                                        statusMessage = if (currentlyApproved) "Revoking driver approval..." else "Approving delivery driver..."
                                        FynxAdminClient.setMarketplaceDriverApproval(context, user.id, !currentlyApproved)
                                            .onSuccess {
                                                statusMessage = if (currentlyApproved) "@${user.username} is no longer an approved driver." else "@${user.username} is an approved driver."
                                                refreshDrivers++
                                            }
                                            .onFailure { statusMessage = it.message ?: "Driver approval could not be changed." }
                                    }
                                }
                            }) { Text(if (marketplaceDrivers.any { it.userId == user.id && it.active }) "Revoke Driver" else "Approve Driver") }
                        }
                        Text(
                            "Limited status blocks Marketplace purchases and sending group messages without fully locking the account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                requestAdminConfirmation(
                                    "Temporarily limit this account?",
                                    "@${user.username} will be blocked from Marketplace purchases and sending group messages. Other FYNX access remains available where supported.",
                                    "Limit account"
                                ) {
                                    scope.launch {
                                        FynxAdminClient.setAccountStatus(context, user.id, "LIMITED")
                                            .onSuccess { statusMessage = "@${user.username} is now limited." }
                                            .onFailure { statusMessage = it.message ?: "Account restriction failed." }
                                    }
                                }
                            }
                        ) { Text("Limit Marketplace + group messaging") }
                    }
                }
            }
            }
            if (activeSection == "Activity") {
                Text("Administrative activity", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Search a traceable history of administrative changes and decisions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = activityQuery,
                    onValueChange = { activityQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search actions, users or record IDs") },
                    singleLine = true,
                    trailingIcon = { IconButton(onClick = { activityOffset = 0; refreshActivity++ }) { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = "Search activity") } }
                )
                Spacer(Modifier.height(8.dp))
                if (activityLoading) CircularProgressIndicator()
                else if (activityError != null) {
                    Text(activityError!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refreshActivity++ }) { Text("Retry") }
                } else if (activity.isEmpty()) {
                    Text("No matching administrative activity has been recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else activity.forEach { entry ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.action.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(entry.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${entry.actorDisplayName.ifBlank { entry.actorUsername.ifBlank { entry.actorRole } }} • ${entry.actorRole} • ${entry.createdAt}", style = MaterialTheme.typography.labelSmall)
                            if (entry.targetId.isNotBlank()) Text("${entry.targetType} #${entry.targetId}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.AzureBlue)
                        }
                    }
                }
                if (!activityLoading && activityError == null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (activity.isEmpty()) "No records" else "Showing ${activityOffset + 1}–${activityOffset + activity.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                enabled = activityOffset > 0,
                                onClick = { activityOffset = (activityOffset - 50).coerceAtLeast(0) }
                            ) { Text("Previous") }
                            TextButton(
                                enabled = activityHasMore && activityOffset < 10000,
                                onClick = { activityOffset = (activityOffset + 50).coerceAtMost(10000) }
                            ) { Text("Next") }
                        }
                    }
                }
            }
            if (activeSection == "Announcements") {
                Spacer(Modifier.height(16.dp))
                Text("Create an official update", style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Attach an image, add a safe external link, publish immediately or schedule for a future time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = .18f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(),
                            label = { Text("Announcement title") }, singleLine = true)
                        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(),
                            label = { Text("Message to users") }, minLines = 4,
                            supportingText = { Text(body.length.toString() + "/4000 characters") })
                        OutlinedTextField(
                            value = announcementLinkUrl,
                            onValueChange = { announcementLinkUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("External link (optional)") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = announcementScheduleAt,
                            onValueChange = { announcementScheduleAt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Schedule time (optional)") },
                            placeholder = { Text("YYYY-MM-DDTHH:mm:ss+01:00") },
                            supportingText = { Text("Leave blank to publish now. For scheduling, use ISO 8601 with a timezone offset.") },
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                enabled = !announcementUploading,
                                onClick = { announcementImagePicker.launch("image/*") }
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (announcementUploading) "Uploading..." else if (announcementMediaId == null) "Add image" else "Change image")
                            }
                            if (announcementMediaId != null) {
                                TextButton(onClick = { announcementMediaId = null }) { Text("Remove image") }
                            }
                        }
                        announcementMediaId?.let { mediaId ->
                            FynxRemoteMedia(
                                mediaUrl = "/api/media/$mediaId",
                                type = "image",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                                autoPlay = false
                            )
                        }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue.copy(alpha = .06f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("PREVIEW", style = MaterialTheme.typography.labelSmall,
                                    color = FynxDesign.AzureBlue, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(title.ifBlank { "Your announcement title" }, style = MaterialTheme.typography.titleMedium)
                                Text(body.ifBlank { "Your message will appear here as users read the official FYNX update." },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (announcementLinkUrl.isNotBlank()) Text(announcementLinkUrl, color = FynxDesign.AzureBlue)
                            }
                        }
                        val isScheduled = announcementScheduleAt.isNotBlank()
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = title.isNotBlank() && body.isNotBlank() && body.length <= 4000 && !announcementUploading,
                            onClick = {
                                requestAdminConfirmation(
                                    if (isScheduled) "Schedule this official announcement?" else "Publish this official announcement?",
                                    "This will " + (if (isScheduled) "schedule" else "publish") + " " + title.trim() + " in the FYNX official feed. The server validates its time, image ownership and link before saving.",
                                    if (isScheduled) "Schedule announcement" else "Publish announcement"
                                ) {
                                    statusMessage = if (isScheduled) "Scheduling..." else "Publishing..."
                                    scope.launch {
                                        FynxAdminClient.publishAnnouncement(
                                            context, title.trim(), body.trim(), "NORMAL",
                                            announcementMediaId, announcementLinkUrl.trim(),
                                            announcementScheduleAt.trim().takeIf { it.isNotBlank() }
                                        )
                                            .onSuccess {
                                                title = ""
                                                body = ""
                                                announcementMediaId = null
                                                announcementLinkUrl = ""
                                                announcementScheduleAt = ""
                                                statusMessage = if (isScheduled) "Announcement scheduled." else "Announcement published."
                                                refreshAdminAnnouncements++
                                            }
                                            .onFailure { statusMessage = it.message ?: "Announcement could not be saved." }
                                    }
                                }
                            }
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Campaign, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isScheduled) "Schedule announcement" else "Publish announcement")
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Manage announcements", style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Cancel scheduled items or publish an unpublished announcement immediately.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                when {
                    adminAnnouncementsLoading -> CircularProgressIndicator()
                    adminAnnouncementsError != null -> {
                        Text(adminAnnouncementsError!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refreshAdminAnnouncements++ }) { Text("Retry") }
                    }
                    adminAnnouncementItems.isEmpty() -> Text("No announcements have been created yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> adminAnnouncementItems.forEach { item ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (!item.published) "Unpublished"
                                    else if (item.scheduledAt != null) "Scheduled: " + item.scheduledAt
                                    else "Published",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.published) {
                                        OutlinedButton(onClick = {
                                            requestAdminConfirmation(
                                                "Unpublish this announcement?",
                                                "This removes " + item.title + " from the public announcements feed. You can publish it again later.",
                                                "Unpublish"
                                            ) {
                                                scope.launch {
                                                    FynxAdminClient.setAnnouncementPublishing(context, item.id, false, null)
                                                        .onSuccess { statusMessage = "Announcement unpublished."; refreshAdminAnnouncements++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not unpublish announcement." }
                                                }
                                            }
                                        }) { Text(if (item.scheduledAt != null) "Cancel schedule" else "Unpublish") }
                                    } else {
                                        Button(onClick = {
                                            requestAdminConfirmation(
                                                "Publish this announcement now?",
                                                "This makes " + item.title + " visible in the public announcements feed immediately.",
                                                "Publish now"
                                            ) {
                                                scope.launch {
                                                    FynxAdminClient.setAnnouncementPublishing(context, item.id, true, null)
                                                        .onSuccess { statusMessage = "Announcement published."; refreshAdminAnnouncements++ }
                                                        .onFailure { statusMessage = it.message ?: "Could not publish announcement." }
                                                }
                                            }
                                        }) { Text("Publish now") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (confirmationAction != null) {
                AlertDialog(
                    onDismissRequest = { confirmationAction = null },
                    title = { Text(confirmationTitle) },
                    text = { Text(confirmationMessage) },
                    confirmButton = {
                        TextButton(onClick = {
                            val action = confirmationAction
                            confirmationAction = null
                            action?.invoke()
                        }) { Text(confirmationLabel) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmationAction = null }) { Text("Go back") }
                    }
                )
            }
            statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: Int) {
    Card(
        Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FynxDesign.AzureBlue.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = 0.20f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FynxDesign.AzureBlue)
        }
    }
}
