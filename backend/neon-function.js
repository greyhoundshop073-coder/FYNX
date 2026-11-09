import { attachDatabasePool, upgradeWebSocket } from "@neon/functions";
import serverless from "serverless-http";
import { getFynxDatabasePool } from "./databasePool.js";

process.env.FYNX_NEON_FUNCTION = "1";

const pool = getFynxDatabasePool();
if (pool) attachDatabasePool(pool);

// Apply the existing FYNX realtime socket isolation before the generated
// canonical Express server registers its WebSocket connection listener.
await import("./realtimeIsolationRuntime.js");
await import("./scalability.js");
const { app, dispatchRealtimeConnection } = await import("./.fynx-runtime-server.js");
const expressHandler = serverless(app);

function websocketAdapter(socket) {
  const adapter = {
    get readyState() { return socket.readyState; },
    send(data, options) { return socket.send(data, options); },
    close(code, reason) { return socket.close(code, reason); },
    on(event, callback) {
      if (event === "message") {
        socket.addEventListener("message", (message) => {
          const data = typeof message.data === "string" ? Buffer.from(message.data) : Buffer.from(message.data);
          callback(data, false);
        });
      } else if (event === "close") {
        socket.addEventListener("close", (closed) => callback(closed.code, closed.reason));
      } else if (event === "error") {
        socket.addEventListener("error", (error) => callback(error.error || error));
      }
      return adapter;
    }
  };
  return adapter;
}

async function toApiGatewayEvent(request) {
  const url = new URL(request.url);
  const headers = Object.fromEntries(request.headers.entries());
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const bodyBytes = hasBody ? Buffer.from(await request.arrayBuffer()) : null;
  return {
    version: "2.0",
    routeKey: `${request.method} ${url.pathname}`,
    rawPath: url.pathname,
    rawQueryString: url.search.startsWith("?") ? url.search.slice(1) : "",
    headers,
    requestContext: {
      http: {
        method: request.method,
        path: url.pathname,
        protocol: "HTTP/1.1",
        sourceIp: headers["x-forwarded-for"]?.split(",")[0]?.trim() || "",
        userAgent: headers["user-agent"] || ""
      }
    },
    body: bodyBytes ? bodyBytes.toString("base64") : undefined,
    isBase64Encoded: Boolean(bodyBytes)
  };
}

function toFetchResponse(result) {
  const headers = new Headers();
  for (const [name, value] of Object.entries(result.headers || {})) {
    if (value == null || name.toLowerCase() === "content-length") continue;
    if (Array.isArray(value)) for (const item of value) headers.append(name, String(item));
    else headers.set(name, String(value));
  }
  for (const [name, values] of Object.entries(result.multiValueHeaders || {})) {
    if (name.toLowerCase() === "content-length") continue;
    headers.delete(name);
    for (const value of values || []) headers.append(name, String(value));
  }
  const status = Number.isInteger(result.statusCode) && result.statusCode >= 200 && result.statusCode <= 599
    ? result.statusCode
    : 200;
  const body = status === 204 || status === 304 || result.body == null ? null : result.isBase64Encoded
    ? Buffer.from(result.body, "base64")
    : result.body;
  return new Response(body, { status, headers });
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/realtime") {
      if (request.headers.get("upgrade")?.toLowerCase() !== "websocket") {
        return new Response("WebSocket upgrade required", { status: 426 });
      }
      const { socket, response } = upgradeWebSocket(request);
      const compatibleSocket = websocketAdapter(socket);
      socket.addEventListener("open", () => {
        const req = {
          url: `${url.pathname}${url.search}`,
          headers: { host: request.headers.get("host") || "localhost" }
        };
        dispatchRealtimeConnection(compatibleSocket, req).catch((error) => {
          console.error("[fynx-realtime] connection setup failed", error);
          try { socket.close(1011, "realtime setup failed"); } catch {}
        });
      });
      return response;
    }

    try {
      const event = await toApiGatewayEvent(request);
      const result = await expressHandler(event, {});
      return toFetchResponse(result);
    } catch (error) {
      console.error("[fynx-function] request failed", {
        name: error?.name || "Error",
        message: error?.message || "unknown"
      });
      return Response.json({ error: "FYNX service is temporarily unavailable." }, {
        status: 503,
        headers: { "Cache-Control": "no-store" }
      });
    }
  }
};
