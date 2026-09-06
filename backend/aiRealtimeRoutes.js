import jwt from "jsonwebtoken";
import { createRealtimeAnswer } from "./aiVoiceSession.js";

const JWT_SECRET = process.env.JWT_SECRET || "";
const MAX_SDP_LENGTH = 200_000;

function authenticate(req, res) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) {
    res.status(401).json({ error: "authentication required" });
    return false;
  }
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    return true;
  } catch {
    res.status(401).json({ error: "invalid or expired token" });
    return false;
  }
}

export function registerRealtimeAssistantRoutes({ app }) {
  app.post("/api/assistant/realtime-session", async (req, res) => {
    if (!authenticate(req, res)) return;

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
}
