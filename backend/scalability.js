import http from "node:http";

// Apply safe HTTP connection limits before the existing FYNX server is created.
// This improves connection reuse under load without changing any API routes.
const originalCreateServer = http.createServer;
http.createServer = function fynxCreateServer(...args) {
  const server = originalCreateServer.apply(this, args);
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;
  server.requestTimeout = 30_000;
  server.maxRequestsPerSocket = 1_000;
  return server;
};
