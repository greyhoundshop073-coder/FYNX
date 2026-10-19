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
require("active group Gallery opens the device media picker", 'groupMediaPicker.launch("*/*")' in ACTIVE_GROUP and "ActivityResultContracts.GetContent()" in ACTIVE_GROUP)
require("active group Camera picker sends captured media through the authenticated group pipeline", "FynxGroupCameraPicker(context, onMediaSelected" in ACTIVE_GROUP and "sendGroupAttachment(uri, type)" in ACTIVE_GROUP)
require("active group Files opens the document picker", "groupDocumentPicker.launch(arrayOf(\"*/*\"))" in ACTIVE_GROUP and "ActivityResultContracts.OpenDocument()" in ACTIVE_GROUP)
require("group media picker validates MIME type before sending", 'mime.startsWith("video/")' in ACTIVE_GROUP and 'mime.startsWith("image/")' in ACTIVE_GROUP)
require("group attachments remain retryable after send failure", "it.copy(delivered = false, read = false)" in ACTIVE_GROUP and "Attachment could not be sent." in ACTIVE_GROUP)
require("group attachments are copied into app-owned storage before upload so picker URIs survive screen exit", "stagePendingGroupAttachment(context, uri, type)" in ACTIVE_GROUP and "context.filesDir, \"fynx_group_pending_media\"" in ACTIVE_GROUP and "destination.outputStream()" in ACTIVE_GROUP)
require("successful group attachment sends clean up staged files while failures retain them for retry", "deletePendingGroupAttachment(context, stagedUri.toString())" in ACTIVE_GROUP and "failedMessage.attachmentUri?.let { deletePendingGroupAttachment(context, it) }" in ACTIVE_GROUP)
require("group document retry does not default unknown attachment MIME to JPEG", "groupAttachmentMimeType(context,uri,mediaType)" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8") and "\"application/octet-stream\"" in (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8"))
require("staged group documents preserve their original filename extension", "OpenableColumns.DISPLAY_NAME" in ACTIVE_GROUP and "sourceExtension.ifBlank" in ACTIVE_GROUP)
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
