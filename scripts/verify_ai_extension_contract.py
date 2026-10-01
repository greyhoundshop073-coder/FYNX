from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

doc = read("docs/FYNX_AI_EXTENSION_CONTRACT.md")
registry = read("backend/fynxAiToolRegistry.js")
conversation = read("backend/aiConversationRoutes.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice = read("backend/aiVoiceSession.js")
android_client = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
android_voice = read("app/src/main/java/com/fynx/app/ui/FynxAiVoiceSession.kt")
android_panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
backend_client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")

checks = [
    ("extension contract exists", "FYNX AI Extension Contract" in doc),
    ("registry remains the approved capability seam", "export function getFynxAiTools()" in registry and "executeFynxAiTool" in registry),
    ("text AI uses the existing conversation route", "/api/assistant/conversations/:id/message" in conversation and "runAssistantAgent" in conversation),
    ("realtime AI uses the existing authenticated session route", "/api/assistant/realtime-session" in realtime and "authenticate(req, res)" in realtime),
    ("realtime tools use the same approved registry", "getFynxAiTools" in realtime and "executeFynxAiTool" in realtime),
    ("voice provider credentials stay server-side", "process.env.OPENAI_API_KEY" in voice and "OPENAI_API_KEY" not in android_voice and "OPENAI_API_KEY" not in android_panel),
    ("Android text AI stays on FYNX backend transport", "FynxBackendClient.get" in android_client and "FynxBackendClient.postJson" in android_client),
    ("Android realtime session stays on FYNX backend transport", "FynxBackendClient.postJson" in android_voice),
    ("central HTTPS transport remains the Android network boundary", "PRODUCTION_BASE_URL = \"https://fynx-ai-backend.onrender.com\"" in backend_client and "startsWith(\"https://\")" in backend_client),
    ("AI panel remains presentation layer", "FynxAiConversationClient" in android_panel and "FynxAiWebRtcEngine" in android_panel),
    ("future sensitive writes remain confirmation-gated", "confirmationRequired" in registry and "NEVER sends the message" in registry),
    ("future financial/destructive writes remain blocked by policy", "Never perform payments, refunds, purchases, transfers, deletions, or settings changes" in registry),
    ("AI capability additions cannot silently bypass authentication", "authenticate(req)" in conversation and "authenticate(req, res)" in realtime and "authenticated user is required" in registry),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"AI extension contract failed: {len(failed)} check(s)")
print(f"AI extension contract GREEN: {len(checks)} checks passed")
