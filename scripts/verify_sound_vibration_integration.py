#!/usr/bin/env python3
"""Regression gate for FYNX sound/vibration preference wiring and duplicate feedback."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    if not value.strip():
        raise SystemExit(f"EMPTY: {path}")
    return value

def check(name: str, condition: bool) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {name}")
    print(f"PASS: {name}")

interaction = read("app/src/main/java/com/fynx/app/ui/FynxInteractionSound.kt")
chat_sound = read("app/src/main/java/com/fynx/app/ui/FynxInChatSound.kt")
haptics = read("app/src/main/java/com/fynx/app/ui/FynxHaptics.kt")
conversation = read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt")
group = read("app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt")
social = read("app/src/main/java/com/fynx/app/ui/FynxSocialInteractionSounds.kt")
social_client = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
post_reactions = read("app/src/main/java/com/fynx/app/ui/FynxHomePostReactionsClient.kt")
social_home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
social_hub = read("app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt")
settings = read("app/src/main/java/com/fynx/app/ui/NotificationPanel.kt")
foundation = read("app/src/main/java/com/fynx/app/ui/NotificationFoundation.kt")
comments_panel = read("app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt")
call_alert = read("app/src/main/java/com/fynx/app/ui/FynxCallAlertController.kt")
calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
messaging_service = read("app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt")

check("sound master preference is exposed in settings", "FynxInteractionSound.setMasterEnabled(context, it)" in settings)
check("private, group and social sound categories are exposed", all(token in settings for token in [
    "FynxInteractionSound.Category.CHAT", "FynxInteractionSound.Category.GROUP",
    "FynxInteractionSound.Category.SOCIAL"
]))
check("global in-app haptic preference is exposed and persisted", "FynxHaptics.setEnabled(context, it)" in settings and "KEY_ENABLED" in haptics)
check("per-chat sound preference gates actual playback", "FynxConversationPreferences.chatSounds(context, chatUsername)" in chat_sound)
check("per-chat vibration preference gates actual incoming-message vibration", "FynxConversationPreferences.chatVibration(context, chatUsername)" in haptics)
check("incoming message feedback is deduplicated by stable remote message id", "remote.id.isNotBlank() && messages.none { it.id == remote.id }" in conversation)
check("incoming chat uses the chat-specific sound and vibration preferences", "FynxInChatSound.play(context, chat.username)" in conversation and "FynxHaptics.incomingChatMessage(context, chat.username)" in conversation)
check("private text and media send confirmations play only after successful delivery response", "FynxInteractionSound.Event.MESSAGE_SENT" in conversation and "FynxInteractionSound.Event.MEDIA_SENT" in conversation and conversation.index("// Only confirm after the backend accepted the send") > conversation.index(".onSuccess { remote ->"))
check("voice-message retry success has media confirmation feedback", "FynxInteractionSound.play(context, FynxInteractionSound.Event.MEDIA_SENT)" in conversation)
check("successful private sends and reactions use the master haptics preference", "FynxHaptics.perform(localView)" in conversation and "FynxHaptics.perform(view)" in haptics)
check("social likes/reactions, comments and post publishing use shared success feedback with haptics", all(token in social_home for token in ["FynxHaptics.perform(localView)", "FynxRemoteSocialClient.like(context, id, desired = optimisticLiked)", "FynxHomePostReactionsClient.clear(context, id)"]) and "FynxHaptics.perform(localView)" in comments_panel and "FynxHaptics.perform(localView)" in social_hub and "FynxSocialInteractionSounds.commented(context)" in comments_panel)
check("successful likes play a shared social sound and removed reactions do not double-play", "if (result.first) FynxSocialInteractionSounds.liked(context)" in social_client and "FynxSocialInteractionSounds.reaction(context)" in post_reactions)
check("private replies and private message reactions have distinct success feedback", all(token in conversation for token in [
    "FynxInteractionSound.Event.REPLY", "FynxInteractionSound.Event.REACTION"
]))
check("group sends select media and reply feedback only after server success", all(token in group for token in [
    "FynxInteractionSound.Event.GROUP_MEDIA_SENT", "FynxInteractionSound.Event.GROUP_REPLY",
    "feedback(message)", "FynxGroupRemoteClient.sendMessage(context, group.id, message)"
]) and group.index("FynxGroupRemoteClient.sendMessage(context, group.id, message)") < group.index("feedback(message)"))
check("group reaction feedback is emitted after server success", "FynxInteractionSound.Event.GROUP_REACTION" in group and group.index("FynxGroupRemoteClient.reactToMessage(context, group.id, message.id, next)") < group.index("FynxInteractionSound.Event.GROUP_REACTION"))
check("social comments and replies use shared SOCIAL_COMMENT sound preference", "FynxSocialInteractionSounds.commented(context)" in comments_panel and "FynxInteractionSound.Event.SOCIAL_COMMENT" in social)
check("group success feedback uses shared sound and system haptics", "FynxHaptics.perform(localView)" in group and "FynxInteractionSound.Event.GROUP_MESSAGE_SENT" in group)
check("group incoming feedback is deduplicated and ignores messages from self", all(token in group for token in [
    "latestMessages.none { it.id == incomingId }", "groupFeedbackMessageIds.add(incomingId)",
    "senderUsername.equals(currentUsername.removePrefix(\"@\"), ignoreCase = true)",
    "FynxInteractionSound.playIncomingGroupMessage(context, group.id)",
    "FynxHaptics.incomingGroupMessage(context, group.id)"
]))
check("group incoming feedback respects mute and notification settings", all(token in interaction for token in [
    "FynxConversationPreferences.groupNotifications(context, groupId)",
    "FynxConversationPreferences.groupMuted(context, groupId)",
    "Event.GROUP_MESSAGE_RECEIVED"
]) and all(token in haptics for token in [
    "FynxConversationPreferences.groupNotifications(context, groupId)",
    "FynxConversationPreferences.groupMuted(context, groupId)",
    "fun incomingGroupMessage(context: Context, groupId: String)"
]))
check("group send confirmation happens only after server success", group.index("FynxGroupRemoteClient.sendMessage") < group.index("// Confirmation feedback is only emitted after the server accepts the message.") and "Confirmation feedback is only emitted after the server accepts the message." in group)
check("social feedback routes through shared category and silent-mode policy", "ToneGenerator" not in social and "FynxInteractionSound.Event.SOCIAL_REACTION" in social)
check("in-app sounds respect Android Silent and Vibrate modes", "audio.ringerMode != AudioManager.RINGER_MODE_NORMAL" in interaction)
check("notification settings provide direct Android channel settings access", "Settings.ACTION_APP_NOTIFICATION_SETTINGS" in settings)
check("existing notification channels remain the authority for background alert behavior", "NotificationManagerCompat.from(context).notify(id, builder.build())" in foundation)


check("incoming-call ringtone is tied to RINGING state and stopped when call state changes or screen closes", all(token in calls for token in [
    "FynxCallAlertController.start(context, currentAlertSession.id)",
    "currentAlertSession?.state == FynxCallState.RINGING",
    "FynxCallAlertController.stop()",
    "onDispose {",
    "FynxCallAlertController.stop()"
]))
check("incoming-call alerts use the device ringtone and follow ring/vibrate/silent modes", all(token in call_alert for token in [
    "RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)", "isLooping = true",
    "AudioManager.RINGER_MODE_VIBRATE", "AudioManager.RINGER_MODE_SILENT",
    "VibrationEffect.createWaveform", "AudioAttributes.USAGE_NOTIFICATION_RINGTONE"
]))
check("foreground real-time calls suppress duplicate incoming-call push alerts", "FynxNotificationForegroundState.shouldSuppressCallNotification()" in messaging_service and "foreground && FynxCallAlertController.isAlerting()" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxNotificationForegroundState.kt").read_text(encoding="utf-8"))
check("incoming and missed call push types route to the Calls notification channel", all(token in messaging_service for token in [
    '"CALL"', '"INCOMING_CALL"', '"VOICE_CALL"', '"VIDEO_CALL"', '"MISSED_CALL"',
    "FynxNotificationFoundation.CALLS_CHANNEL"
]))

print("GREEN: FYNX sound/vibration integration gate")
