package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val supportCategories = listOf(
    "ACCOUNT_LOGIN" to "Account & login", "MESSAGES_CALLS" to "Messages & calls",
    "WALLET_PAYMENTS" to "Wallet & payments", "MARKETPLACE_SHOP" to "Marketplace & My Shop",
    "STATUS_STORIES" to "Status & Stories", "PERFORMANCE_CRASHES" to "App performance & crashes",
    "NOTIFICATIONS" to "Notifications", "OTHER" to "Other"
)

@Composable
fun FynxUserSupportPanel(initialCaseId: String? = null, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var pendingUploadedMediaId by remember { mutableStateOf<String?>(null) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        screenshotUri = it
        pendingUploadedMediaId = null
    }
    var attachments by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportAttachment>>(emptyList()) }
    var cases by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportCase>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(supportCategories.first().first) }
    var categoryMenu by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCase by remember { mutableStateOf<FynxSupportRemoteClient.SupportCase?>(null) }
    var messages by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportMessage>>(emptyList()) }
    var reply by remember { mutableStateOf("") }
    var userReplyRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        FynxSupportRemoteClient.myCases(context).onSuccess { cases = it; error = null }
            .onFailure { error = it.message ?: "Support requests could not be loaded." }
        loading = false
    }
    fun openCase(item: FynxSupportRemoteClient.SupportCase) {
        selectedCase = item
        messages = emptyList()
        reply = ""
        userReplyRequestId = UUID.randomUUID().toString()
        scope.launch { FynxSupportRemoteClient.userMessages(context, item.id).onSuccess { messages = it }.onFailure { notice = it.message ?: "Conversation unavailable." } }
    }
    LaunchedEffect(selectedCase?.id) {
        val caseId = selectedCase?.id
        if (caseId == null) attachments = emptyList()
        else FynxSupportRemoteClient.attachments(context, caseId).onSuccess { attachments = it }
    }
    var initialCaseOpened by remember { mutableStateOf(false) }
    LaunchedEffect(initialCaseId, cases) {
        if (!initialCaseOpened && !initialCaseId.isNullOrBlank()) {
            cases.firstOrNull { it.id == initialCaseId }?.let { initialCaseOpened = true; openCase(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.weight(1f)) {
                Text("Help & Support", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Get help from the FYNX support team", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Report a problem", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(supportCategories.first { it.first == category }.second, modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        supportCategories.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { category = option.first; categoryMenu = false }) }
                    }
                }
                OutlinedTextField(subject, { subject = it.take(140) }, Modifier.fillMaxWidth(), label = { Text("Subject") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(5000) }, Modifier.fillMaxWidth(), label = { Text("Describe the problem") }, minLines = 4)
                OutlinedButton(onClick = { screenshotPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (screenshotUri == null) "Attach screenshot (optional)" else "Screenshot selected • Change")
                }
                Button(enabled = !submitting && subject.trim().length >= 3 && description.trim().length >= 10, onClick = {
                    submitting = true
                    scope.launch {
                        val selectedUri = screenshotUri
                        if (selectedUri != null && pendingUploadedMediaId == null) {
                            val upload = FynxProductionMessaging.uploadMedia(context, selectedUri)
                            if (upload.isFailure) {
                                notice = upload.exceptionOrNull()?.message ?: "Screenshot upload failed."
                                submitting = false
                                return@launch
                            }
                            pendingUploadedMediaId = upload.getOrThrow().id
                        }
                        val createdResult = FynxSupportRemoteClient.createCase(context, category, subject, description, pendingRequestId)
                        val created = createdResult.getOrNull()
                        if (created == null) {
                            notice = createdResult.exceptionOrNull()?.message ?: "Your request could not be saved. Retry without changing the complaint."
                        } else {
                            val attachmentResult = pendingUploadedMediaId?.let { FynxSupportRemoteClient.attachMedia(context, created.id, it) }
                            if (attachmentResult?.isFailure == true) {
                                notice = "Request ${created.caseNumber} was saved, but its screenshot could not be attached. Tap submit again to retry the attachment."
                            } else {
                                subject = ""; description = ""; screenshotUri = null
                                pendingUploadedMediaId = null
                                pendingRequestId = UUID.randomUUID().toString()
                                notice = if (attachmentResult != null) "Request ${created.caseNumber} and screenshot were saved." else "Request ${created.caseNumber} was saved."
                            }
                            refresh++
                        }
                        submitting = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (submitting) "Submitting…" else "Submit support request") }
                Text("Never include passwords, PINs or verification codes.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("My support requests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        when {
            loading -> CircularProgressIndicator()
            error != null -> { Text(error!!, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { refresh++ }) { Text("Retry") } }
            cases.isEmpty() -> Text("You have not submitted a support request yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> cases.forEach { item ->
                Card(onClick = { openCase(item) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.caseNumber, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            SupportStatusChip(item.status)
                        }
                        Text(item.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.lastMessage.orEmpty(), maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    }

    selectedCase?.let { item ->
        AlertDialog(onDismissRequest = { selectedCase = null }, title = { Column { Text(item.caseNumber); Text(item.subject, style = MaterialTheme.typography.titleMedium); SupportStatusChip(item.status) } },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportAttachmentViewer(item.id, attachments)
                    messages.forEach { message ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(message.displayName.ifBlank { message.username.ifBlank { "FYNX Support" } }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(message.body)
                            }
                        }
                    }
                    if (item.status == "RESOLVED") {
                        Text("This request has been resolved. To report a new issue, submit a new support request. Only the Owner can reopen this case.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedTextField(reply, { reply = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Reply to support") }, minLines = 2)
                        Button(enabled = reply.isNotBlank() && !sending, onClick = {
                            sending = true
                            scope.launch {
                                FynxSupportRemoteClient.replyAsUser(context, item.id, reply, userReplyRequestId)
                                    .onSuccess { reply = ""; userReplyRequestId = UUID.randomUUID().toString(); openCase(item); refresh++ }
                                    .onFailure { notice = it.message ?: "Reply could not be sent." }
                                sending = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (sending) "Sending…" else "Send reply") }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selectedCase = null }) { Text("Close") } })
    }
}

@Composable
fun FynxAdminSupportPanel(role: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cases by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportCase>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportAttachment>>(emptyList()) }
    var admins by remember { mutableStateOf<List<FynxAdminClient.Admin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedCase by remember { mutableStateOf<FynxSupportRemoteClient.SupportCase?>(null) }
    var messages by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportMessage>>(emptyList()) }
    var events by remember { mutableStateOf<List<FynxSupportRemoteClient.SupportEvent>>(emptyList()) }
    var reply by remember { mutableStateOf("") }
    var adminReplyRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var internalNote by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var assignmentMenu by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        FynxSupportRemoteClient.adminCases(context).onSuccess { cases = it; error = null }.onFailure { error = it.message ?: "Support inbox unavailable." }
        if (role == "OWNER") FynxAdminClient.admins(context).onSuccess { admins = it }
        loading = false
    }
    fun openCase(item: FynxSupportRemoteClient.SupportCase) {
        selectedCase = item
        messages = emptyList()
        events = emptyList()
        reply = ""
        adminReplyRequestId = UUID.randomUUID().toString()
        scope.launch { FynxSupportRemoteClient.adminMessages(context, item.id).onSuccess { messages = it }.onFailure { notice = it.message ?: "Conversation unavailable." } }
    }
    LaunchedEffect(selectedCase?.id) {
        val caseId = selectedCase?.id
        if (caseId == null) {
            attachments = emptyList()
            events = emptyList()
        } else {
            FynxSupportRemoteClient.attachments(context, caseId).onSuccess { attachments = it }
            FynxSupportRemoteClient.events(context, caseId).onSuccess { events = it }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Support Centre", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (role == "OWNER") "Owner oversight • all support requests" else "Your assigned and unassigned requests", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        when {
            loading -> CircularProgressIndicator()
            error != null -> { Text(error!!, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { refresh++ }) { Text("Retry") } }
            cases.isEmpty() -> Text("No support requests need attention.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> cases.forEach { item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .3f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.caseNumber, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            SupportStatusChip(item.status)
                        }
                        Text(item.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("@${item.requesterUsername.orEmpty()} • ${item.requesterDisplayName.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Assigned: " + (item.assignedDisplayName ?: item.assignedUsername ?: "Unassigned"), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.assignedAdminId.isNullOrBlank() && item.status != "RESOLVED") OutlinedButton(onClick = {
                                scope.launch { FynxSupportRemoteClient.claimCase(context, item.id).onSuccess { notice = "Case claimed."; refresh++ }.onFailure { notice = it.message ?: "Could not claim case." } }
                            }) { Text("Take case") }
                            OutlinedButton(onClick = { openCase(item) }) { Text("Open case") }
                        }
                    }
                }
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    }

    selectedCase?.let { item ->
        AlertDialog(onDismissRequest = { selectedCase = null }, title = { Column { Text(item.caseNumber); Text(item.subject, style = MaterialTheme.typography.titleMedium); SupportStatusChip(item.status) } },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportAttachmentViewer(item.id, attachments)
                    Text("User: @${item.requesterUsername.orEmpty()}", style = MaterialTheme.typography.labelMedium)
                    Text("Category: ${item.category.replace('_', ' ')}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Problem description", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(item.description.ifBlank { "No description was supplied." }, style = MaterialTheme.typography.bodyMedium)
                    if (item.assignedAdminId.isNullOrBlank() && item.status != "RESOLVED") OutlinedButton(onClick = {
                        scope.launch { FynxSupportRemoteClient.claimCase(context, item.id).onSuccess { notice = "Case claimed."; refresh++; openCase(item) }.onFailure { notice = it.message ?: "Could not claim case." } }
                    }) { Text("Take responsibility for this case") }
                    if (role == "OWNER") {
                        Box {
                            OutlinedButton(onClick = { assignmentMenu = true }) { Text("Assign to Admin") }
                            DropdownMenu(expanded = assignmentMenu, onDismissRequest = { assignmentMenu = false }) {
                                admins.forEach { admin -> DropdownMenuItem(text = { Text("@${admin.username}") }, onClick = {
                                    assignmentMenu = false
                                    scope.launch { FynxSupportRemoteClient.assignCase(context, item.id, admin.id).onSuccess { notice = "Case assigned to @${admin.username}."; refresh++; openCase(item) }.onFailure { notice = it.message ?: "Assignment failed." } }
                                }) }
                            }
                        }
                    }
                    messages.forEach { message ->
                        Card(colors = CardDefaults.cardColors(containerColor = if (message.visibility == "INTERNAL") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(if (message.visibility == "INTERNAL") "INTERNAL NOTE • ${message.displayName}" else message.displayName.ifBlank { message.username }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(message.body)
                            }
                        }
                    }
                    if (item.status == "RESOLVED" && role != "OWNER") {
                        Text("This case is resolved. Admins may add an internal note, but only the Owner can reopen it or send another public reply.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(reply, { reply = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text(if (internalNote) "Internal note (not visible to user)" else "Reply to user") }, minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(internalNote, { internalNote = it }); Text("Internal note only") }
                    Button(enabled = reply.isNotBlank() && !sending && !(item.status == "RESOLVED" && role != "OWNER" && !internalNote), onClick = {
                        sending = true
                        scope.launch {
                            FynxSupportRemoteClient.replyAsAdmin(context, item.id, reply, internalNote, adminReplyRequestId)
                                .onSuccess { reply = ""; adminReplyRequestId = UUID.randomUUID().toString(); notice = if (internalNote) "Internal note saved." else "Reply sent to user."; openCase(item); refresh++ }
                                .onFailure { notice = it.message ?: "Message could not be sent." }
                            sending = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (sending) "Sending…" else if (internalNote) "Save internal note" else "Send reply") }
                    if (events.isNotEmpty()) {
                        Text("Case activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        events.take(20).forEach { event ->
                            Text(
                                "${event.eventType.replace('_', ' ')} • ${event.username?.let { "@$it" } ?: "FYNX"} • ${event.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (item.status != "RESOLVED" || role == "OWNER") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { scope.launch { FynxSupportRemoteClient.updateStatus(context, item.id, "IN_REVIEW").onSuccess { refresh++; notice = "Case moved to review." }.onFailure { notice = it.message ?: "Status update failed." } } }) { Text("Review") }
                            OutlinedButton(onClick = { scope.launch { FynxSupportRemoteClient.updateStatus(context, item.id, "WAITING_FOR_USER").onSuccess { refresh++; notice = "Waiting for user." }.onFailure { notice = it.message ?: "Status update failed." } } }) { Text("Waiting") }
                            Button(onClick = { scope.launch { FynxSupportRemoteClient.updateStatus(context, item.id, "RESOLVED").onSuccess { refresh++; notice = "Case resolved."; selectedCase = null }.onFailure { notice = it.message ?: "Could not resolve case." } } }) { Text("Resolve") }
                        }
                    }
                    if (role == "OWNER" && item.status == "RESOLVED") OutlinedButton(onClick = { scope.launch { FynxSupportRemoteClient.updateStatus(context, item.id, "OPEN").onSuccess { refresh++; notice = "Case reopened." }.onFailure { notice = it.message ?: "Could not reopen case." } } }) { Text("Reopen case") }
                }
            }, confirmButton = { TextButton(onClick = { selectedCase = null }) { Text("Close") } })
    }
}

@Composable
private fun SupportStatusChip(status: String) {
    AssistChip(onClick = {}, label = { Text(status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1) })
}


@Composable
private fun SupportAttachmentViewer(caseId: String, attachments: List<FynxSupportRemoteClient.SupportAttachment>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(caseId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(caseId) { mutableStateOf(false) }
    if (attachments.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Screenshots and evidence", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            attachments.forEach { attachment ->
                OutlinedButton(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        val uri = FynxProductionMessaging.cacheRemoteMedia(context, "support-${attachment.id}", FynxSupportRemoteClient.attachmentUrl(caseId, attachment.id)).getOrNull()
                        bitmap = if (uri == null) null else withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull()
                        }
                        loading = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "Loading screenshot…" else "View screenshot • ${(attachment.byteSize / 1024).coerceAtLeast(1)} KB")
                }
            }
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Support screenshot", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
        }
    }
}
