import http from "node:http";
import { registerMarketplaceSettlementRoutes } from "./marketplaceSettlement.js";
import { registerMarketplaceProtectionRoutes } from "./marketplaceProtection.js";
import { registerMarketplaceAdvertisingRoutes } from "./marketplaceAdvertising.js";

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

  // Keep marketplace settlement, protection, and advertising behind the same Express app/server bootstrap.
  setImmediate(() => {
    try {
      registerMarketplaceSettlementRoutes({ app: args[0] });
      registerMarketplaceProtectionRoutes({ app: args[0] });
      registerMarketplaceAdvertisingRoutes({ app: args[0] });
      console.log("[fynx-marketplace] protected settlement, buyer/seller protection, and advertising routes enabled");
    } catch (error) {
      console.error("[fynx-marketplace] route registration failed", error);
    }
  });

  return server;
};
