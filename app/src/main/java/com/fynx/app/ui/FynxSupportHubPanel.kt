package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

private val FYNX_SUPPORT_CATEGORIES = listOf(
    "Account & login" to "ACCOUNT_ACCESS",
    "Wallet & payments" to "WALLET_PAYMENTS",
    "Messages & calls" to "MESSAGES_CALLS",
    "Marketplace & My Shop" to "MARKETPLACE_MY_SHOP",
    "Status & Stories" to "STATUS_STORIES",
    "Notifications" to "NOTIFICATIONS",
    "App performance" to "APP_PERFORMANCE",
    "Other" to "OTHER"
)

@Composable
fun FynxSupportHubPanel(staffOnly: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<FynxSupportRemoteClient.Access?>(null) }
    var accessError by remember { mutableStateOf<String?>(null) }
    var accessLoading by remember { mutableStateOf(true) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var selectedCaseId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<FynxSupportRemoteClient.Detail?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var cases by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportCase>>(emptyList()) }
    var inboxCounts by remember { mutableStateOf<FynxSupportRemoteClient.Counts?>(null) }
    var inboxFilter by remember { mutableStateOf("") }
    var inboxQuery by remember { mutableStateOf("") }
    var inboxError by remember { mutableStateOf<String?>(null) }
    var inboxLoading by remember { mutableStateOf(false) }
    var admins by remember { mutableStateOf<List<FynxSupportRemoteClient.Admin>>(emptyList()) }
    var adminError by remember { mutableStateOf<String?>(null) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FYNX_SUPPORT_CATEGORIES.first()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var requestKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var submitting by remember { mutableStateOf(false) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    var replyRequestKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var sendingReply by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    var internalNote by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var assigneeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accessLoading = true
        FynxSupportRemoteClient.access(context)
            .onSuccess { access = it; accessError = null }
            .onFailure { accessError = it.message ?: "FYNX Help & Support is currently unavailable." }
        accessLoading = false
    }

    val staffMode = access?.let { it.role == "OWNER" || (it.role == "ADMIN" && it.supportAccess) } == true
    val ownerMode = access?.role == "OWNER"

    LaunchedEffect(access, refreshVersion, inboxFilter) {
        val currentAccess = access ?: return@LaunchedEffect
        if (currentAccess.role == "OWNER" || (currentAccess.role == "ADMIN" && currentAccess.supportAccess)) {
            inboxLoading = true
            FynxSupportRemoteClient.inbox(context, inboxFilter)
                .onSuccess { inbox -> cases = inbox.cases; inboxCounts = inbox.counts; inboxError = null }
                .onFailure { inboxError = it.message ?: "Support inbox could not be loaded." }
            if (currentAccess.role == "OWNER") {
                FynxSupportRemoteClient.admins(context)
                    .onSuccess { admins = it; adminError = null }
                    .onFailure { adminError = it.message ?: "Support team settings are unavailable." }
            }
            inboxLoading = false
        } else {
            inboxLoading = true
            FynxSupportRemoteClient.myCases(context)
                .onSuccess { cases = it; inboxError = null }
                .onFailure { inboxError = it.message ?: "Your support requests could not be loaded." }
            inboxLoading = false
        }
    }

    LaunchedEffect(selectedCaseId, access, refreshVersion) {
        val id = selectedCaseId ?: return@LaunchedEffect
        val currentAccess = access ?: return@LaunchedEffect
        detailLoading = true
        detailError = null
        val result = if (currentAccess.role == "OWNER" || (currentAccess.role == "ADMIN" && currentAccess.supportAccess)) {
            FynxSupportRemoteClient.staffCaseDetail(context, id)
        } else {
            FynxSupportRemoteClient.myCaseDetail(context, id)
        }
        result.onSuccess { detail = it; detailError = null }
            .onFailure { detail = null; detailError = it.message ?: "The support conversation could not be opened." }
        detailLoading = false
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (staffMode) "FYNX Support Centre" else "Help & Support", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (staffMode) "Private user complaints and support cases" else "Report a FYNX problem and follow the response here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { refreshVersion++ }, enabled = !accessLoading && !inboxLoading) { Text("Refresh") }
        }

        if (accessLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (access == null) {
            SupportMessageCard(accessError ?: "Support access could not be checked.", isError = true)
            OutlinedButton(onClick = {
                scope.launch {
                    accessLoading = true
                    FynxSupportRemoteClient.access(context)
                        .onSuccess { access = it; accessError = null }
                        .onFailure { accessError = it.message ?: "FYNX Help & Support is currently unavailable." }
                    accessLoading = false
                }
            }) { Text("Try again") }
        } else if (staffOnly && !staffMode) {
            SupportMessageCard("Support Centre access is not enabled for this Admin account. Ask the FYNX Owner to grant support access.", isError = false)
        } else if (staffMode && selectedCaseId != null) {
            val currentDetail = detail
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedCaseId = null; detail = null; detailError = null; actionMessage = null }) { Text("‹ Support inbox") }
                Spacer(Modifier.weight(1f))
                if (currentDetail != null) SupportStatusLabel(currentDetail.supportCase.status)
            }
            if (detailLoading && currentDetail == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            detailError?.let { SupportMessageCard(it, isError = true) }
            currentDetail?.let { value ->
                val supportCase = value.supportCase
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(supportCase.caseNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(supportCase.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("From ${supportCase.displayName.ifBlank { supportCase.username }}  @${supportCase.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${supportCategoryLabel(supportCase.category)} • ${supportDate(supportCase.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(supportCase.description, style = MaterialTheme.typography.bodyMedium)
                        if (supportCase.assignedAdminId == null) {
                            Text("Unassigned", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text("Assigned to ${supportCase.assignedAdminName?.ifBlank { supportCase.assignedAdminUsername ?: "Support Admin" } ?: "Support Admin"}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (access?.role == "ADMIN" && supportCase.assignedAdminId == null) {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    actionBusy = true; actionMessage = null
                                    scope.launch {
                                        FynxSupportRemoteClient.claim(context, supportCase.id)
                                            .onSuccess { actionMessage = "You have claimed this support request."; refreshVersion++ }
                                            .onFailure { actionMessage = it.message ?: "This case could not be claimed." }
                                        actionBusy = false
                                    }
                                }
                            ) { Text(if (actionBusy) "Claiming…" else "Claim this case") }
                        }
                        if (ownerMode) {
                            Box {
                                OutlinedButton(onClick = { assigneeMenu = true }, enabled = !actionBusy) {
                                    Text(if (supportCase.assignedAdminId == null) "Assign Admin" else "Reassign Admin")
                                }
                                DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                                    DropdownMenuItem(text = { Text("Unassigned") }, onClick = {
                                        assigneeMenu = false; actionBusy = true
                                        scope.launch {
                                            FynxSupportRemoteClient.assign(context, supportCase.id, null)
                                                .onSuccess { actionMessage = "Case returned to the unassigned queue."; refreshVersion++ }
                                                .onFailure { actionMessage = it.message ?: "Case assignment failed." }
                                            actionBusy = false
                                        }
                                    })
                                    admins.filter { it.supportAccess }.forEach { admin ->
                                        DropdownMenuItem(
                                            text = { Text(admin.displayName.ifBlank { "@${admin.username}" }) },
                                            onClick = {
                                                assigneeMenu = false; actionBusy = true
                                                scope.launch {
                                                    FynxSupportRemoteClient.assign(context, supportCase.id, admin.id)
                                                        .onSuccess { actionMessage = "Case assigned to @${admin.username}."; refreshVersion++ }
                                                        .onFailure { actionMessage = it.message ?: "Case assignment failed." }
                                                    actionBusy = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Private conversation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SupportConversationBubble(
                    title = supportCase.displayName.ifBlank { supportCase.username }.ifBlank { "FYNX user" },
                    body = supportCase.description,
                    label = "Original complaint",
                    internal = false
                )
                value.messages.forEach { message ->
                    SupportConversationBubble(
                        title = if (message.senderRole == "USER") message.displayName.ifBlank { "@${message.username}" } else "FYNX ${message.senderRole.lowercase().replaceFirstChar { it.uppercase() }}",
                        body = message.message,
                        label = if (message.visibility == "INTERNAL") "Internal note • staff only" else "${message.senderRole} • ${supportDate(message.createdAt)}",
                        internal = message.visibility == "INTERNAL"
                    )
                }
                if (value.events.isNotEmpty()) {
                    Text("Case activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    value.events.take(12).forEach { event ->
                        Text(
                            "${event.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} • ${event.actorRole} • ${supportDate(event.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Update case", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                replyError?.let { SupportMessageCard(it, isError = true) }
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it.take(4000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Write a private reply") },
                    placeholder = { Text("Explain what you checked or ask the user for more information…") },
                    minLines = 3,
                    enabled = !sendingReply
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = internalNote, onCheckedChange = { internalNote = it }, enabled = !sendingReply)
                    Column(Modifier.weight(1f)) {
                        Text("Internal note", style = MaterialTheme.typography.bodyMedium)
                        Text("Only Owner and authorised support Admins can see this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = {
                        sendingReply = true; replyError = null
                        scope.launch {
                            FynxSupportRemoteClient.sendStaffMessage(context, supportCase.id, reply, internalNote, replyRequestKey)
                                .onSuccess { reply = ""; internalNote = false; replyRequestKey = UUID.randomUUID().toString(); refreshVersion++ }
                                .onFailure { replyError = it.message ?: "The support message could not be sent." }
                            sendingReply = false
                        }
                    },
                    enabled = reply.isNotBlank() && !sendingReply
                ) { Text(if (sendingReply) "Sending…" else if (internalNote) "Add internal note" else "Send private reply") }

                Text("Case status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = !actionBusy && supportCase.status != "IN_REVIEW", onClick = { updateSupportStatus(context, scope, supportCase.id, "IN_REVIEW", { actionBusy = it }, { actionMessage = it; refreshVersion++ }) }) { Text("In review") }
                    OutlinedButton(enabled = !actionBusy && supportCase.status != "WAITING_USER", onClick = { updateSupportStatus(context, scope, supportCase.id, "WAITING_USER", { actionBusy = it }, { actionMessage = it; refreshVersion++ }) }) { Text("Waiting") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = !actionBusy && supportCase.status != "OPEN", onClick = { updateSupportStatus(context, scope, supportCase.id, "OPEN", { actionBusy = it }, { actionMessage = it; refreshVersion++ }) }) { Text("Reopen") }
                    Button(enabled = !actionBusy && supportCase.status != "RESOLVED", onClick = { updateSupportStatus(context, scope, supportCase.id, "RESOLVED", { actionBusy = it }, { actionMessage = it; refreshVersion++ }) }) { Text("Resolve") }
                }
                actionMessage?.let { SupportMessageCard(it, isError = false) }
            }
        } else if (staffMode) {
            inboxCounts?.let { counts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SupportCountCard("Open", counts.open, Modifier.weight(1f))
                    SupportCountCard("Review", counts.inReview, Modifier.weight(1f))
                    SupportCountCard("Waiting", counts.waitingUser, Modifier.weight(1f))
                    SupportCountCard("Done", counts.resolved, Modifier.weight(1f))
                }
                Text("Unassigned cases: ${counts.unassigned}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ownerMode && admins.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Support team access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Admin accounts can help users by default. Turn support access off for any Admin who should not handle private complaints.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        adminError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        admins.forEach { admin ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(admin.displayName.ifBlank { admin.username }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("@${admin.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = admin.supportAccess,
                                    onCheckedChange = { enabled ->
                                        actionBusy = true; adminError = null
                                        scope.launch {
                                            FynxSupportRemoteClient.setAdminAccess(context, admin.id, enabled)
                                                .onSuccess { actionMessage = if (enabled) "Support access enabled for @${admin.username}." else "Support access removed from @${admin.username}."; refreshVersion++ }
                                                .onFailure { adminError = it.message ?: "Support access could not be changed." }
                                            actionBusy = false
                                        }
                                    },
                                    enabled = !actionBusy
                                )
                            }
                        }
                    }
                }
            }
            Text("Support inbox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "All", "OPEN" to "Open", "IN_REVIEW" to "In review", "WAITING_USER" to "Waiting", "RESOLVED" to "Resolved").forEach { (value, label) ->
                    FilterChip(selected = inboxFilter == value, onClick = { inboxFilter = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                value = inboxQuery,
                onValueChange = { inboxQuery = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search support cases") },
                placeholder = { Text("Case number, subject or username") },
                singleLine = true
            )
            val filteredCases = cases.filter { supportCase ->
                val query = inboxQuery.trim()
                query.isBlank() ||
                    supportCase.caseNumber.contains(query, true) ||
                    supportCase.subject.contains(query, true) ||
                    supportCase.username.contains(query, true) ||
                    supportCase.displayName.contains(query, true)
            }
            if (inboxLoading && cases.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            inboxError?.let { SupportMessageCard(it, isError = true) }
            if (!inboxLoading && cases.isEmpty() && inboxError == null) {
                SupportMessageCard("No support requests in this view yet.", isError = false)
            } else if (!inboxLoading && filteredCases.isEmpty() && inboxError == null) {
                SupportMessageCard("No cases match your search.", isError = false)
            }
            filteredCases.take(50).forEach { supportCase ->
                SupportCaseRow(supportCase, staff = true, onClick = { selectedCaseId = supportCase.id; actionMessage = null; reply = ""; replyRequestKey = UUID.randomUUID().toString(); detail = null })
            }
            if (access?.role == "ADMIN") {
                Text("You can see unassigned requests and the cases assigned to you. Claim an unassigned case before replying.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (selectedCaseId != null) {
            val currentDetail = detail
            TextButton(onClick = { selectedCaseId = null; detail = null; reply = ""; replyRequestKey = UUID.randomUUID().toString(); replyError = null }) { Text("‹ My support requests") }
            if (detailLoading && currentDetail == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            detailError?.let { SupportMessageCard(it, isError = true) }
            currentDetail?.let { value ->
                Text(value.supportCase.caseNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(value.supportCase.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SupportStatusLabel(value.supportCase.status)
                SupportConversationBubble("You", value.supportCase.description, "Original complaint", false)
                value.messages.forEach { message ->
                    SupportConversationBubble(
                        if (message.senderRole == "USER") "You" else "FYNX Support",
                        message.message,
                        "${message.senderRole} • ${supportDate(message.createdAt)}",
                        false
                    )
                }
                replyError?.let { SupportMessageCard(it, isError = true) }
                OutlinedTextField(
                    value = reply, onValueChange = { reply = it.take(4000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reply to FYNX Support") },
                    minLines = 2,
                    enabled = !sendingReply
                )
                Button(
                    enabled = reply.isNotBlank() && !sendingReply,
                    onClick = {
                        sendingReply = true; replyError = null
                        scope.launch {
                            FynxSupportRemoteClient.sendUserMessage(context, value.supportCase.id, reply, replyRequestKey)
                                .onSuccess { reply = ""; replyRequestKey = UUID.randomUUID().toString(); refreshVersion++ }
                                .onFailure { replyError = it.message ?: "Your reply could not be sent." }
                            sendingReply = false
                        }
                    }
                ) { Text(if (sendingReply) "Sending…" else "Send reply") }
            }
        } else {
            Text("Report a problem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Tell us what happened. Your report will be saved as a private support case that you can follow here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            formMessage?.let { SupportMessageCard(it, isError = false) }
            formError?.let { SupportMessageCard(it, isError = true) }
            OutlinedTextField(
                value = subject, onValueChange = { subject = it.take(120) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Subject") }, singleLine = true,
                enabled = !submitting
            )
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                    Text("Category: ${category.first}", modifier = Modifier.weight(1f))
                    Text("⌄")
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    FYNX_SUPPORT_CATEGORIES.forEach { option ->
                        DropdownMenuItem(text = { Text(option.first) }, onClick = { category = option; categoryMenu = false })
                    }
                }
            }
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(5000) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Describe the problem") },
                placeholder = { Text("What were you doing, what happened, and what did you expect?") },
                minLines = 4, enabled = !submitting
            )
            Button(
                enabled = !submitting && subject.trim().length >= 3 && description.trim().length >= 10,
                onClick = {
                    submitting = true; formError = null; formMessage = null
                    scope.launch {
                        FynxSupportRemoteClient.createCase(context, subject, category.second, description, requestKey)
                            .onSuccess { created ->
                                subject = ""; description = ""; requestKey = UUID.randomUUID().toString()
                                formMessage = "Your request ${created.caseNumber} was saved. You can follow replies below."
                                refreshVersion++
                            }
                            .onFailure { formError = it.message ?: "Your support request was not saved. Please retry." }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Submitting…" else "Submit support request") }

            Text("My support requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (inboxLoading && cases.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            inboxError?.let { SupportMessageCard(it, isError = true) }
            if (!inboxLoading && cases.isEmpty() && inboxError == null) {
                Text("You have not submitted a support request yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            cases.forEach { supportCase -> SupportCaseRow(supportCase, staff = false, onClick = { selectedCaseId = supportCase.id; detail = null; reply = ""; replyRequestKey = UUID.randomUUID().toString(); replyError = null }) }
        }
    }
}

@Composable
private fun SupportCaseRow(
    supportCase: FynxSupportRemoteClient.SupportCase,
    staff: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(supportCase.caseNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SupportStatusLabel(supportCase.status)
            }
            Text(supportCase.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (staff) "@${supportCase.username} • ${supportCategoryLabel(supportCase.category)}" else supportCategoryLabel(supportCase.category),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (staff) Text("Assigned: ${supportCase.assignedAdminUsername?.let { "@$it" } ?: "Unassigned"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Updated ${supportDate(supportCase.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportConversationBubble(title: String, body: String, label: String, internal: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (internal) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (internal) MaterialTheme.colorScheme.tertiary.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportStatusLabel(status: String) {
    val label = when (status) {
        "OPEN" -> "Open"
        "IN_REVIEW" -> "In review"
        "WAITING_USER" -> "Waiting for user"
        "RESOLVED" -> "Resolved"
        else -> status
    }
    val color = when (status) {
        "RESOLVED" -> MaterialTheme.colorScheme.tertiary
        "WAITING_USER" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = RoundedCornerShape(30.dp), color = color.copy(alpha = .12f)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SupportCountCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupportMessageCard(message: String, isError: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun supportCategoryLabel(category: String): String =
    FYNX_SUPPORT_CATEGORIES.firstOrNull { it.second == category }?.first ?: category.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun supportDate(value: String): String =
    value.take(16).replace('T', ' ').ifBlank { "recently" }

private fun updateSupportStatus(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    caseId: String,
    status: String,
    setBusy: (Boolean) -> Unit,
    onFinished: (String) -> Unit
) {
    setBusy(true)
    scope.launch {
        FynxSupportRemoteClient.setStatus(context, caseId, status)
            .onSuccess { onFinished("Case status updated to ${status.replace('_', ' ').lowercase()}.") }
            .onFailure { onFinished(it.message ?: "The case status could not be updated.") }
        setBusy(false)
    }
}
