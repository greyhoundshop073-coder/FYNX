import http from "node:http";
import { installFailureRecovery, createIdempotencyStore } from "./reliability.js";
import { registerMarketplaceSettlementRoutes } from "./marketplaceSettlement.js";
import { registerMarketplaceProtectionRoutes } from "./marketplaceProtection.js";

// Apply safe HTTP connection limits before the existing FYNX server is created.
// Marketplace settlement and protection routes are registered here because server.js is loaded
// after this preload in the backend start command. Keep each route family registered exactly once.
const originalCreateServer = http.createServer;
http.createServer = function fynxCreateServer(...args) {
  const server = originalCreateServer.apply(this, args);
  const app = args[0];
  if (app && typeof app.use === "function") {
    setImmediate(() => {
      registerMarketplaceSettlementRoutes({ app });
      registerMarketplaceProtectionRoutes({ app });
    });
  }
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;
  server.requestTimeout = 30_000;
  server.maxRequestsPerSocket = 1_000;
  server.maxConnections = 500;

  let requests = 0;
  let completed = 0;
  let totalLatencyMs = 0;
  let errors = 0;

  server.on("request", (_req, res) => {
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

  const recovery = installFailureRecovery({ server, pool: null, logger: console });
  globalThis.__fynxRecovery = recovery;
  globalThis.__fynxIdempotency = createIdempotencyStore({ maxEntries: 10_000, ttlMs: 24 * 60 * 60 * 1000 });

  return server;
};