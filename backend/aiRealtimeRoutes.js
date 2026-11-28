import jwt from "jsonwebtoken";
import { assistantJsonBodyParser } from "./assistantJsonBodyParser.js";
import {
  createRealtimeCall, hangupFynxRealtimeCall, isValidRealtimeSessionMode,
  FYNX_AI_REALTIME_VOICES, FYNX_AI_VOICE_MODEL
} from "./aiVoiceSession.js";
import { connectFynxRealtimeSideband } from "./aiRealtimeSideband.js";
import { getFynxDatabasePool } from "./databasePool.js";
import {
  reserveFynxAiUsage, settleFynxAiUsage, attachFynxAiRealtimeCall,
  getActiveFynxAiRealtimeCall, FynxAiUsageLimitError, FynxAiUsageSessionActiveError
} from "./fynxAiUsageBudget.js";
import { executeFynxAiTool, getFynxAiTools } from "./fynxAiToolRegistry.js";

const JWT_SECRET = process.env.JWT_SECRET || "";
const MAX_SDP_LENGTH = 200_000;

function authorizationHeader(req) {
  if (typeof req?.get === "function") return req.get("authorization") || "";
  return req?.headers?.authorization || req?.headers?.Authorization || "";
}

function authenticate(req, res) {
  const header = authorizationHeader(req);
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) {
    res.status(401).json({ error: "authentication required" });
    return null;
  }
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (!payload?.sub) {
      res.status(401).json({ error: "authenticated user is required" });
      return null;
    }
    return String(payload.sub);
  } catch {
    res.status(401).json({ error: "invalid or expired token" });
    return null;
  }
}

const activeRealtimeMonitors = new Map();

export function registerRealtimeAssistantRoutes({
  app,
  databasePoolProvider = getFynxDatabasePool,
  createCall = createRealtimeCall,
  hangupCall = hangupFynxRealtimeCall,
  connectSideband = connectFynxRealtimeSideband,
  reserveUsage = reserveFynxAiUsage,
  settleUsage = settleFynxAiUsage,
  attachCall = attachFynxAiRealtimeCall,
  findActiveCall = getActiveFynxAiRealtimeCall
}) {
  app.post("/api/assistant/realtime-session", assistantJsonBodyParser, async (req, res) => {
    const userId = authenticate(req, res);
    if (!userId) return;

    const sdp = typeof req.body?.sdp === "string" ? req.body.sdp : "";
    const mode = typeof req.body?.mode === "string" ? req.body.mode : "assistant";
    const requestedVoice = typeof req.body?.voice === "string" ? req.body.voice : "";
    if (!isValidRealtimeSessionMode(mode)) {
      return res.status(400).json({ error: "unsupported realtime session mode" });
    }
    if (!sdp.trim() || sdp.length > MAX_SDP_LENGTH) {
      return res.status(400).json({ error: "valid WebRTC SDP offer is required" });
    }

    const databasePool = databasePoolProvider();
    let reservation = null;
    let callId = null;
    try {
      reservation = await reserveUsage({
        userId,
        provider: "openai",
        capability: "voice_" + mode,
        model: FYNX_AI_VOICE_MODEL,
        databasePool
      });
      const voice = mode === "speech" && FYNX_AI_REALTIME_VOICES.includes(requestedVoice) ? requestedVoice : undefined;
      const realtimeCall = await createCall(sdp, mode, voice);
      callId = realtimeCall.callId;
      await attachCall({ reservation, callId, databasePool });

      let monitor;
      try {
        monitor = await connectSideband({
          callId,
          reservation,
          model: FYNX_AI_VOICE_MODEL,
          includeTranscriptionUsage: mode === "transcription",
          databasePool,
          onFinalized: () => {
            if (activeRealtimeMonitors.get(callId)?.monitor === monitor) {
              activeRealtimeMonitors.delete(callId);
            }
          }
        });
      } catch (monitorError) {
        await hangupCall(callId).catch(() => {});
        await settleUsage({ reservation, status: "failed", databasePool }).catch(() => {});
        throw monitorError;
      }

      activeRealtimeMonitors.set(callId, { monitor, reservation });
      res.type("application/sdp").send(realtimeCall.answer);
    } catch (error) {
      if (reservation) {
        if (callId) await hangupCall(callId).catch(() => {});
        await settleUsage({ reservation, status: "failed", databasePool }).catch(() => {});
      }
      if (error instanceof FynxAiUsageLimitError) {
        if (typeof res.setHeader === "function") res.setHeader("Retry-After", String(error.retryAfterSeconds));
        return res.status(429).json({ error: error.message, retryAfterSeconds: error.retryAfterSeconds });
      }
      if (error instanceof FynxAiUsageSessionActiveError) {
        return res.status(409).json({ error: error.message });
      }
      console.error("realtime voice session", error?.message || error);
      return res.status(502).json({ error: "FYNX realtime voice session unavailable" });
    }
  });

  app.post("/api/assistant/realtime-session/hangup", assistantJsonBodyParser, async (req, res) => {
    const userId = authenticate(req, res);
    if (!userId) return;
    const databasePool = databasePoolProvider();
    try {
      const activeCall = await findActiveCall(userId, databasePool);
      if (!activeCall) return res.json({ ok: true, active: false });
      const activeMonitor = activeRealtimeMonitors.get(activeCall.callId)?.monitor;
      activeMonitor?.expectFinish?.();
      let providerResult;
      try {
        providerResult = await hangupCall(activeCall.callId);
      } catch (error) {
        activeMonitor?.expectFailure?.();
        throw error;
      }
      if (activeMonitor) {
        await activeMonitor.finish("completed");
      } else {
        // A cold/serverless instance may not own the original WebSocket. Do not
        // mark usage complete without the provider usage stream that prices it.
        await settleUsage({
          reservation: activeCall,
          status: "failed",
          databasePool
        });
      }
      activeRealtimeMonitors.delete(activeCall.callId);
      return res.json({ ok: true, active: true, alreadyEnded: providerResult.alreadyEnded === true });
    } catch (error) {
      console.error("realtime voice hangup", error?.message || error);
      return res.status(502).json({ error: "FYNX realtime voice session could not be closed cleanly" });
    }
  });

  app.post("/api/assistant/realtime-tool", assistantJsonBodyParser, async (req, res) => {
    const userId = authenticate(req, res);
    if (!userId) return;

    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    const argumentsJson = typeof req.body?.arguments === "string" ? req.body.arguments : JSON.stringify(req.body?.arguments || {});
    if (!name || !getFynxAiTools().some(tool => tool.name === name)) {
      return res.status(400).json({ error: "unknown AI tool" });
    }
    if (argumentsJson.length > 8000) {
      return res.status(413).json({ error: "tool arguments too large" });
    }

    try {
      const result = await executeFynxAiTool({ name, argumentsJson, userId });
      return res.json({ result });
    } catch (error) {
      console.error("realtime AI tool", name, error?.message || error);
      return res.status(403).json({ error: "AI tool request was not permitted" });
    }
  });
}
