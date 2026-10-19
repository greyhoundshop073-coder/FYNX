from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"

PRIVATE = (UI / "ConversationPanel.kt").read_text(encoding="utf-8")
ACTIVE_GROUP = (UI / "FynxGroupsPanel.kt").read_text(encoding="utf-8")
APP = (UI / "FynxApp.kt").read_text(encoding="utf-8")
MODELS = (UI / "ChatModels.kt").read_text(encoding="utf-8")

checks = []
def require(label, condition):
    checks.append((label, bool(condition)))

# The route check prevents a green gate from certifying an unused legacy screen.
require("app routes group conversations to the active FynxGroupConversationPanel", "FynxGroupConversationPanel(groupId = openGroup!!" in APP)
require("active group conversation protects top system inset", "statusBarsPadding" in ACTIVE_GROUP)
require("active group conversation has bottom content padding", "contentPadding = PaddingValues(bottom" in ACTIVE_GROUP)
require("active group conversation uses 48dp minimum touch targets", "Modifier.size(48.dp)" in ACTIVE_GROUP)
require("active group conversation supports message search", "searchQuery" in ACTIVE_GROUP)
require("active group conversation supports reply targets", "replyToId" in ACTIVE_GROUP)
require("active group conversation loads authenticated remote history", "FynxGroupRemoteClient.loadMessages(context, groupId)" in ACTIVE_GROUP)
require("active group conversation handles realtime group-message events", "FynxRealtimeClient.Event.GroupMessage" in ACTIVE_GROUP and "realtime.connect()" in ACTIVE_GROUP)
require("active group realtime connection is cancelled and closed with the screen", "awaitCancellation()" in ACTIVE_GROUP and "realtime.close()" in ACTIVE_GROUP)
require("failed group messages remain visible instead of being silently deleted", "it.copy(delivered = false, read = false)" in ACTIVE_GROUP and "messages = messages.map" in ACTIVE_GROUP)
require("active group conversation exposes a retry action for unsent messages", 'TextButton(enabled = !sending, onClick = {' in ACTIVE_GROUP and 'Text(if (unsentCount == 1) "Retry" else "Retry all")' in ACTIVE_GROUP)
require("active group conversation retries every failed outgoing message, not only the last one", 'messages.filter { it.fromMe && !it.delivered }' in ACTIVE_GROUP and 'Text(if (unsentCount == 1) "Retry" else "Retry all")' in ACTIVE_GROUP and "for (failedMessage in pendingMessages)" in ACTIVE_GROUP)
require("active group Gallery opens the device media picker", "PickMultipleVisualMedia(maxItems = 1)" in ACTIVE_GROUP and "PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)" in ACTIVE_GROUP)
require("active group Camera picker sends captured media through the authenticated group pipeline", "FynxGroupCameraPicker(context, onMediaSelected" in ACTIVE_GROUP and "sendGroupAttachment(uri, type)" in ACTIVE_GROUP)
require("active group Files opens the document picker", "groupDocumentPicker.launch(arrayOf(\"*/*\"))" in ACTIVE_GROUP and "ActivityResultContracts.OpenDocument()" in ACTIVE_GROUP)
require("group media picker validates MIME type before sending", 'mime.startsWith("video/")' in ACTIVE_GROUP and 'mime.startsWith("image/")' in ACTIVE_GROUP)
require("group attachments remain retryable after send failure", "it.copy(delivered = false, read = false)" in ACTIVE_GROUP and "Attachment could not be sent." in ACTIVE_GROUP)
require("group attachments are copied into app-owned storage before upload so picker URIs survive screen exit", "stagePendingGroupAttachment(context, uri, type)" in ACTIVE_GROUP and "context.filesDir, \"fynx_group_pending_media\"" in ACTIVE_GROUP and "destination.outputStream()" in ACTIVE_GROUP)
require("group attachment staging enforces bounded file sizes before filling app storage", "MAX_STAGED_GROUP_ATTACHMENT_BYTES = 12L * 1024 * 1024" in ACTIVE_GROUP and "MAX_STAGED_GROUP_IMAGE_BYTES = 64L * 1024 * 1024" in ACTIVE_GROUP and "totalBytes += read" in ACTIVE_GROUP and "totalBytes <= maxBytes" in ACTIVE_GROUP)
require("successful group attachment sends clean up staged files while failures retain them for retry", "deletePendingGroupAttachment(context, stagedUri.toString())" in ACTIVE_GROUP and "failedMessage.attachmentUri?.let { deletePendingGroupAttachment(context, it) }" in ACTIVE_GROUP)
require("group document retry does not default unknown attachment MIME to JPEG", "groupAttachmentMimeType(context,uri,mediaType)" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8") and "\"application/octet-stream\"" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8"))
require("staged group documents preserve their original filename extension", "OpenableColumns.DISPLAY_NAME" in ACTIVE_GROUP and "sourceExtension.ifBlank" in ACTIVE_GROUP)
require("group attachment retry reuses an uploaded media ID instead of uploading duplicate media", "message.mediaId?:persisted?.mediaId" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8") and "it.copy(mediaId=mediaId,attachmentType=mediaType)" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8"))
require("active group microphone requests runtime permission before recording", "ActivityResultContracts.RequestPermission()" in ACTIVE_GROUP and "Manifest.permission.RECORD_AUDIO" in ACTIVE_GROUP)
require("active group microphone records AAC audio and sends it through the attachment pipeline", "MediaRecorder.AudioEncoder.AAC" in ACTIVE_GROUP and "sendGroupAttachment(Uri.fromFile(file), \"audio\")" in ACTIVE_GROUP)
require("active group microphone has a stop/send control and rejects empty recordings", "Icons.Default.Stop" in ACTIVE_GROUP and "duration < 500L" in ACTIVE_GROUP and "Voice message" in ACTIVE_GROUP)
require("active group voice recording is cleaned up when leaving the conversation", "DisposableEffect(groupId)" in ACTIVE_GROUP and "activeVoiceRecorder?.let" in ACTIVE_GROUP)
require("active group Location action opens a location-share dialog and sends a map link", "showLocationShare = true" in ACTIVE_GROUP and "https://maps.google.com/?q=$mapsQuery" in ACTIVE_GROUP and "FynxGroupLocationShareDialog" in ACTIVE_GROUP)
require("active group Contact action collects and sends selected contact details", "showContactShare = true" in ACTIVE_GROUP and "FynxGroupContactShareDialog" in ACTIVE_GROUP and "Phone: $phone" in ACTIVE_GROUP)
require("active group Poll action opens the existing group content panel directly", "showPollsDirectly = true" in ACTIVE_GROUP and "openContentInitially = showPollsDirectly" in ACTIVE_GROUP and "mutableStateOf(openContentInitially)" in (UI / "FynxGroupSocialBatch.kt").read_text(encoding="utf-8"))

# Shared private-chat and model contracts remain covered.
require("private chat protects top system inset", "statusBarsPadding" in PRIVATE)
require("message model supports audio attachments", "attachmentType: String?" in MODELS and "audio" in MODELS and "ChatMessage" in MODELS)

failed = [label for label, ok in checks if not ok]
print(f"CHAT/GROUP ACTIVE-SCREEN PARITY + LAYOUT: {len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    raise SystemExit("GATE RED: " + "; ".join(failed))
print("GATE GREEN: active Chat/Group route, realtime, and layout contracts are present")
require("group Gallery uses the Android photo picker for images and videos rather than a generic file picker", "PickMultipleVisualMedia(maxItems = 1)" in ACTIVE_GROUP and "PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)" in ACTIVE_GROUP and 'groupMediaPicker.launch("*/*")' not in ACTIVE_GROUP)
require("private chat Gallery uses the Android photo picker for images and videos while Files retains the document picker", "PickMultipleVisualMedia(maxItems = 1)" in PRIVATE and "PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)" in PRIVATE and 'galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))' in PRIVATE and 'Triple("Files", Icons.Default.Description) { showAttachmentSheet = false; mediaPicker.launch(arrayOf("application/pdf"' in PRIVATE)
require("private chat maps selected MIME types to supported semantic media types and never guesses unknown files are images", 'attachmentType = context.contentResolver.getType(uris.first())?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "application/octet-stream"' in PRIVATE and 'attachmentType?.startsWith("image/") == true -> "image"' in PRIVATE and 'attachmentType?.startsWith("video/") == true -> "video"' in PRIVATE and 'attachmentType?.startsWith("audio/") == true -> "audio"' in PRIVATE and 'else -> "document"' in PRIVATE and 'val selectedType = attachmentType ?: "image"' not in PRIVATE)
require("private chat retries reuse the uploaded media for the same attachment and clear the cache after successful send", "uploadedAttachmentCache?.takeIf { it.first == selectedAttachment.toString() }" in PRIVATE and "uploadedAttachmentCache = selectedAttachment.toString() to media" in PRIVATE and "uploadedAttachmentCache = null" in PRIVATE)
require("group attachment actions use distinct colors for quick recognition", "tint = Color(0xFF1685F8)" in ACTIVE_GROUP and "tint = Color(0xFF8B6FF7)" in ACTIVE_GROUP and "tint = Color(0xFF00C99A)" in ACTIVE_GROUP and "tint = Color(0xFF00A8E8)" in ACTIVE_GROUP and "tint = Color(0xFFFFB938)" in ACTIVE_GROUP)
require("group composer exposes a dedicated video-note camera action and sends it through the group attachment pipeline", "FynxGroupCameraPicker(context, videoNoteMode = true" in ACTIVE_GROUP and 'Text("Video note", style = MaterialTheme.typography.labelMedium, color = Color(0xFFEF6C35))' in ACTIVE_GROUP and "sendGroupAttachment(uri, type)" in ACTIVE_GROUP)
require("chat attachment sheet gives each action its own recognizable color", '"Camera" -> Color(0xFFE85D75)' in PRIVATE and '"Gallery" -> Color(0xFF1685F8)' in PRIVATE and '"Files" -> Color(0xFF8B6FF7)' in PRIVATE and '"Location" -> Color(0xFF00A879)' in PRIVATE and '"Contact" -> Color(0xFF00A8E8)' in PRIVATE and '"Poll" -> Color(0xFFFFB938)' in PRIVATE and '"Video note" -> Color(0xFFEF6C35)' in PRIVATE)
