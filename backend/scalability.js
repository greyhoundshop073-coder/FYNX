import http from "node:http";
import { registerMarketplaceSettlementRoutes } from "./marketplaceSettlement.js";

// Apply safe HTTP connection limits before the existing FYNX server is created.
// This improves connection reuse and protects the process under load without changing API routes.
const originalCreateServer = http.createServer;
http.createServer = function fynxCreateServer(...args) {
  const server = originalCreateServer.apply(this, args);
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;
  server.requestTimeout = 30_000;
  server.maxRequestsPerSocket = 1_000;
  server.maxConnections = 500;

  let requests = 0;
  let completed = 0;
  let totalLatencyMs = 0;
  let errors = 0;

  server.on("request", (req, res) => {
    const startedAt = process.hrtime.bigint();
    requests += 1;
    res.on("finish", () => {
      completed += 1;
      totalLatencyMs += Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      if (res.statusCode >= 500) errors += 1;
    });
  });

  const report = setInterval(() => {
    if (!completed) return;
    const averageLatencyMs = totalLatencyMs / completed;
    console.log(`[fynx-metrics] requests=${requests} completed=${completed} errors5xx=${errors} avgLatencyMs=${averageLatencyMs.toFixed(1)}`);
  }, 60_000);
  report.unref();

  server.on("error", (error) => {
    console.error("[fynx-http] server error", error);
  });

  // The existing server keeps its own auth and database helpers private. Register the
  // settlement layer against the same Express app after module initialization, using the
  // protected environment-backed database connection inside marketplaceSettlement.js.
  setImmediate(() => {
    try {
      registerMarketplaceSettlementRoutes({ app: args[0] });
      console.log("[fynx-marketplace] protected settlement routes enabled");
    } catch (error) {
      console.error("[fynx-marketplace] settlement route registration failed", error);
    }
  });

  return server;
};
