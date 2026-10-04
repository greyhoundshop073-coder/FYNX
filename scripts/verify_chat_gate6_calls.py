#!/usr/bin/env python3
from pathlib import Path
import sys

def read(path):
    return Path(path).read_text(encoding="utf-8")

checks = []
active = read("app/src/main/java/com/fynx/app/ui/FynxActiveCallPanel.kt")
calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
foundation = read("app/src/main/java/com/fynx/app/ui/FynxCallsFoundation.kt")
webrtc = read("app/src/main/java/com/fynx/app/ui/FynxWebRtcCallEngine.kt")
notify = read("app/src/main/java/com/fynx/app/ui/NotificationFoundation.kt")
realtime = read("app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt")
manifest = read("app/src/main/AndroidManifest.xml")
backend = read("backend/server.js")

def check(name, ok):
    checks.append((name, bool(ok)))

check("voice call exposes mute speaker and end controls", all(x in active for x in ["Mute", "Speaker", "End"]))
check("video call exposes camera flip and end controls", all(x in active for x in ["Camera", "Flip", "End", "Cameraswitch"]))
check("incoming call exposes answer and decline", all(x in active for x in ["Answer", "Decline", "onAnswer", "onEnd"]))
check("active call shows live duration", "durationSeconds" in active and "formatDuration(durationSeconds)" in active and "callDurationSeconds" in calls)
check("call screen has video remote and local surfaces", "remoteVideoTrack" in active and "localVideoTrack" in active and "FynxCallVideoSurface" in active)
check("call media controls are wired to WebRTC engine", all(x in calls for x in ["setMicrophoneEnabled", "setCameraEnabled", "switchCamera", "setSpeakerEnabled"]))
check("WebRTC supports voice and video media", all(x in webrtc for x in ["createLocalAudio", "createLocalVideo", "addTrack"]))
check("WebRTC has ICE recovery and cleanup", all(x in webrtc for x in ["scheduleIceRecovery", "IceRestart", "disconnect()", "peerConnection?.dispose()"]))
check("incoming call can be restored from notification intent", all(x in calls for x in ["initialIncomingCall", "FynxCallState.RINGING", "sendCallAccept"]))
check("incoming notification uses Android CallStyle and answer/decline actions", all(x in notify for x in ["NotificationCompat.CallStyle.forIncomingCall", "answerIntent", "declineIntent"]))
check("incoming notification can launch full-screen call UI", "setFullScreenIntent" in notify and "USE_FULL_SCREEN_INTENT" in manifest)
check("realtime call notifications carry call identity", "incomingCall = FynxIncomingCall" in realtime)
check("backend relays call signaling and validates SDP/ICE", all(x in backend for x in ["relayCallSignal", "validateSignalPayload", "offer", "answer", "ice"]))
check("backend bounds active calls and handles unavailable/busy", all(x in backend for x in ["activeCalls", '"busy"', '"unavailable"']))
check("call transport tests cover voice and video types", all(x in read("app/src/androidTest/java/com/fynx/app/FynxCallTransportHardeningTest.kt") for x in ['"voice"', '"video"']))

failed = [name for name, ok in checks if not ok]
for i, (name, ok) in enumerate(checks, 1):
    print(f"{i:02d}. {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)
print("\nPASS: Gate 6 call contract is internally consistent.")
