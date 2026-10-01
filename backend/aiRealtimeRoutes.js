import jwt from "jsonwebtoken";
import { createRealtimeAnswer } from "./aiVoiceSession.js";
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
    return payload?.sub ? String(payload.sub) : null;
  } catch {
    res.status(401).json({ error: "invalid or expired token" });
    return null;
  }
}

export function registerRealtimeAssistantRoutes({ app }) {
  app.post("/api/assistant/realtime-session", async (req, res) => {
    const userId = authenticate(req, res);
    if (!userId) return;

    const sdp = typeof req.body?.sdp === "string" ? req.body.sdp : "";
    if (!sdp.trim() || sdp.length > MAX_SDP_LENGTH) {
      return res.status(400).json({ error: "valid WebRTC SDP offer is required" });
    }

    try {
      const answer = await createRealtimeAnswer(sdp);
      res.type("application/sdp").send(answer);
    } catch (error) {
      console.error("realtime voice session", error?.message || error);
      return res.status(502).json({ error: "FYNX realtime voice session unavailable" });
    }
  });

  app.post("/api/assistant/realtime-tool", async (req, res) => {
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
