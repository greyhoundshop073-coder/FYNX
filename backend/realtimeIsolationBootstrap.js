import jwt from "jsonwebtoken";
import { WebSocketServer } from "ws";

// R3 connection isolation layer. server.js is intentionally kept intact; this
// wrapper makes the existing WebSocket connection handler reject stale/replaced
// sockets before its message listeners can process or deliver realtime traffic.
const currentSocketByUserId = new Map();
const originalServerOn = WebSocketServer.prototype.on;

function authenticatedUserId(req) {
  const secret = process.env.JWT_SECRET || "";
  if (!secret) return null;
  try {
    const url = new URL(req?.url || "/realtime", `http://${req?.headers?.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    if (!token) return null;
    const user = jwt.verify(token, secret);
    const userId = String(user?.sub || "");
    return /^\d+$/.test(userId) && userId.length <= 20 ? userId : null;
  } catch {
    return null;
  }
}

WebSocketServer.prototype.on = function on(event, listener) {
  if (event !== "connection") return originalServerOn.call(this, event, listener);

  return originalServerOn.call(this, event, (socket, req, ...rest) => {
    const userId = authenticatedUserId(req);
    if (!userId) return listener(socket, req, ...rest);

    const previous = currentSocketByUserId.get(userId);
    if (previous && previous !== socket) {
      previous.__fynxStale = true;
      try { previous.close(4001, "replaced realtime session"); } catch {}
    }
    currentSocketByUserId.set(userId, socket);
    socket.__fynxStale = false;

    const originalSend = socket.send.bind(socket);
    socket.send = (...args) => {
      if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale) return;
      return originalSend(...args);
    };

    const originalSocketOn = socket.on.bind(socket);
    socket.on = (socketEvent, callback) => {
      if (socketEvent === "message") {
        return originalSocketOn("message", (data, ...args) => {
          if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale || socket.readyState !== 1) return;
          return callback(data, ...args);
        });
      }
      if (socketEvent === "close") {
        return originalSocketOn("close", (...args) => {
          if (currentSocketByUserId.get(userId) === socket) currentSocketByUserId.delete(userId);
          return callback(...args);
        });
      }
      return originalSocketOn(socketEvent, callback);
    };

    return listener(socket, req, ...rest);
  });
};

await import("./serverBootstrap.js");
