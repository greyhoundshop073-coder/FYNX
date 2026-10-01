import jwt from "jsonwebtoken";
import { WebSocketServer } from "ws";

// Compatibility layer for the existing Android chat transport. The Android client
// already sends authenticated typing events over /realtime; the authoritative
// server connection handler currently processes call packets there, so keep typing
// as a small isolated realtime layer instead of changing the production message API.
const originalOn = WebSocketServer.prototype.on;
const socketByUserId = new Map();
const typingRate = new Map();
const WINDOW_MS = 60_000;
const TYPING_LIMIT = 120;

function userIdFromRequest(req) {
  const secret = process.env.JWT_SECRET || "";
  if (!secret) return null;
  try {
    const url = new URL(req?.url || "/realtime", `http://${req?.headers?.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    const user = jwt.verify(token, secret);
    const id = String(user?.sub || "");
    return /^\d+$/.test(id) && id.length <= 20 ? id : null;
  } catch {
    return null;
  }
}

function allowed(userId) {
  const now = Date.now();
  const current = typingRate.get(userId);
  if (!current || now - current.startedAt >= WINDOW_MS) {
    typingRate.set(userId, { startedAt: now, count: 1 });
    return true;
  }
  if (current.count >= TYPING_LIMIT) return false;
  current.count += 1;
  return true;
}

function send(socket, payload) {
  if (socket?.readyState === 1) socket.send(JSON.stringify(payload));
}

function install() {
  if (WebSocketServer.prototype.__fynxChatRealtimeCompatibility) return;
  WebSocketServer.prototype.__fynxChatRealtimeCompatibility = true;
  WebSocketServer.prototype.on = function fynxChatRealtimeOn(event, listener) {
    if (event !== "connection") return originalOn.call(this, event, listener);
    return originalOn.call(this, event, (socket, req, ...rest) => {
      const userId = userIdFromRequest(req);
      if (userId) {
        const previous = socketByUserId.get(userId);
        if (previous && previous !== socket) {
          try { previous.close(4001, "replaced realtime session"); } catch {}
        }
        socketByUserId.set(userId, socket);

        const originalSocketOn = socket.on.bind(socket);
        socket.on = (socketEvent, callback) => {
          if (socketEvent === "message") {
            return originalSocketOn("message", (data, ...args) => {
              let body;
              try { body = JSON.parse(data.toString()); } catch { return callback(data, ...args); }
              if (body?.type === "typing") {
                if (!allowed(userId)) return;
                const recipientId = String(body?.recipientId || "");
                if (!/^\d+$/.test(recipientId) || recipientId === userId || typeof body?.isTyping !== "boolean") return;
                const recipientSocket = socketByUserId.get(recipientId);
                send(recipientSocket, { type: "typing", userId, isTyping: body.isTyping });
                return;
              }
              return callback(data, ...args);
            });
          }
          if (socketEvent === "close") {
            return originalSocketOn("close", (...args) => {
              if (socketByUserId.get(userId) === socket) socketByUserId.delete(userId);
              return callback(...args);
            });
          }
          return originalSocketOn(socketEvent, callback);
        };
      }
      return listener(socket, req, ...rest);
    });
  };
}

setInterval(() => {
  const cutoff = Date.now() - WINDOW_MS;
  for (const [key, value] of typingRate) if (value.startedAt < cutoff) typingRate.delete(key);
}, WINDOW_MS).unref();

install();
