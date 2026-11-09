import { attachDatabasePool, upgradeWebSocket } from "@neon/functions";
import { parseTriggerDelivery } from "@neon/functions/triggers";
import serverless from "serverless-http";
import http from "node:http";
import { getFynxDatabasePool } from "./databasePool.js";
import { createBackgroundJobQueue } from "./backgroundJobs.js";
import { registerMarketplaceSettlementWorker } from "./marketplaceSettlementWorker.js";
import { expireUnpaidMarketplaceReservations, expireUnpaidMarketplaceCheckoutGroups, recoverInterruptedPaymentInitializations, reconcileTerminalFinancialOperations } from "./marketplacePaymentExpiry.js";
import { autoCompleteExpiredMarketplaceInspections } from "./marketplaceInspectionExpiry.js";
import { runMarketplaceBatch2RecoveryOnce } from "./marketplaceBatch2FinalHardening.js";

process.env.FYNX_NEON_FUNCTION = "1";
const originalHttpCreateServer = http.createServer;

const pool = getFynxDatabasePool();
if (pool) attachDatabasePool(pool);

// Apply the existing FYNX realtime socket isolation before the generated
// canonical Express server registers its WebSocket connection listener.
await import("./realtimeIsolationRuntime.js");
await import("./scalability.js");
const { app, dispatchRealtimeConnection } = await import("./.fynx-runtime-server.js");
// Restore Node HTTP after the canonical route-registration hook has built the app.
// serverless-http creates its own adapter server and must not trigger FYNX route wiring twice.
http.createServer = originalHttpCreateServer;
const scheduledJobs = createBackgroundJobQueue({ logger: console, leaseMs: 90_000 });
const settlementScan = registerMarketplaceSettlementWorker({ jobs: scheduledJobs, logger: console, startTimer: false });

async function runFynxScheduledTasks() {
  const result = {};
  const failures = [];
  const jobFailuresBefore = Number(scheduledJobs.snapshot().failures || 0);
  const run = async (name, task) => {
    try { result[name] = await task(); }
    catch (error) {
      failures.push(name);
      console.error("[fynx-scheduled] task failed", name, error?.message || error);
    }
  };

  await run("job_queue_schema", () => scheduledJobs.runOnce());
  await run("expired_payment_reservations", () => expireUnpaidMarketplaceReservations({ logger: console }));
  await run("expired_checkout_groups", () => expireUnpaidMarketplaceCheckoutGroups({ logger: console }));
  await run("interrupted_payment_recovery", () => recoverInterruptedPaymentInitializations({ logger: console }));
  await run("terminal_financial_reconciliation", () => reconcileTerminalFinancialOperations({ logger: console }));
  await run("expired_inspection_completion", () => autoCompleteExpiredMarketplaceInspections({ logger: console }));
  const batch2 = await runMarketplaceBatch2RecoveryOnce({ logger: console });
  result.marketplace_batch2_recovery = batch2;
  if (batch2?.failures?.length) failures.push("marketplace_batch2_recovery");
  if (settlementScan) await run("payout_settlement_scan", () => settlementScan());

  let jobsProcessed = 0;
  for (let index = 0; index < 20; index += 1) {
    const before = Number(scheduledJobs.snapshot().claimed || 0);
    await scheduledJobs.runOnce();
    const after = Number(scheduledJobs.snapshot().claimed || 0);
    if (after === before) break;
    jobsProcessed += 1;
  }
  result.jobsProcessed = jobsProcessed;
  result.jobQueue = scheduledJobs.snapshot();
  if (Number(result.jobQueue.failures || 0) > jobFailuresBefore) failures.push("background_job_queue");
  return { ok: failures.length === 0, failures, tasks: result };
}

const expressHandler = serverless(app, { binary: ["application/octet-stream", "image/*", "video/*", "audio/*"] });

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
    if (url.pathname === "/internal/cron") {
      if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
      const parsed = await parseTriggerDelivery(request);
      if (!parsed.ok) return new Response(parsed.error, { status: parsed.error === "invalid_body" ? 400 : 401 });
      if (parsed.invocation.type !== "schedule") return new Response("Unsupported trigger type", { status: 400 });
      const result = await runFynxScheduledTasks();
      return Response.json({ ...result, invocationId: parsed.invocation.invocationId });
    }
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
