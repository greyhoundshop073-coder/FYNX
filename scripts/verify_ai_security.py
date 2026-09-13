from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

registry = read("backend/fynxAiToolRegistry.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice = read("backend/aiVoiceSession.js")
android = read("app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt")

check("AI registry exposes strict function definitions", 'strict: true' in registry and 'type: "function"' in registry)
check("AI agent requires authenticated user", 'const userId = authenticate(req);' in registry and 'if (!userId) return res.status(401)' in registry)
check("AI provider key stays server-side", 'process.env.OPENAI_API_KEY' in registry and 'OPENAI_API_KEY' not in android)
check("AI tool execution requires authenticated user", 'if (!userId) throw new Error("authenticated user is required")' in registry)
check("AI tool arguments are JSON validated", 'JSON.parse(argumentsJson)' in registry)
check("Sensitive write actions remain unavailable", 'Never perform payments, refunds, purchases, transfers, deletions, settings changes, or messages' in registry)
check("Realtime tool endpoint is authenticated", 'app.post("/api/assistant/realtime-tool"' in realtime and 'const userId = authenticate(req, res);' in realtime)
check("Realtime tool execution uses approved registry", 'executeFynxAiTool' in realtime and 'getFynxAiTools' in realtime)
check("Realtime voice keeps provider credential server-side", 'process.env.OPENAI_API_KEY' in voice)
check("Android voice transport calls the FYNX session endpoint", 'FynxAiVoiceSession.requestSession' in android)
check("Android realtime tool calls use the authenticated FYNX session", 'FynxAiVoiceSession.executeTool' in android)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"AI security gate failed: {len(failed)} check(s)")

print(f"AI security gate GREEN: {len(checks)} checks passed")
