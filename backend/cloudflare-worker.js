import { handleAsNodeRequest } from "cloudflare:node";

const SERVER_PORT = 10000;
let serverPromise;

async function getServer(env) {
  if (!serverPromise) {
    serverPromise = (async () => {
      if (!env.HYPERDRIVE?.connectionString) {
        throw new Error("FYNX Hyperdrive binding is not configured.");
      }

      // Workers exposes configured secrets and bindings through env. Populate
      // only scalar values before importing the Node application so modules
      // that read process.env at module load see the correct deployment config.
      for (const [key, value] of Object.entries(env)) {
        if (typeof value === "string") process.env[key] = value;
      }
      process.env.DATABASE_URL = env.HYPERDRIVE.connectionString;
      process.env.PORT = String(SERVER_PORT);
      process.env.FYNX_CLOUDFLARE_WORKER = "1";
      process.env.NODE_ENV = "production";

      const { server } = await import("./.fynx-runtime-server.js");
      if (!server?.listening) throw new Error("FYNX HTTP server did not start.");
      return server;
    })().catch((error) => {
      serverPromise = undefined;
      throw error;
    });
  }
  return serverPromise;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/realtime" || url.pathname.startsWith("/realtime/")) {
      return Response.json(
        { error: "FYNX realtime transport is not enabled on this Worker deployment yet." },
        { status: 503, headers: { "Cache-Control": "no-store" } }
      );
    }

    try {
      await getServer(env);
      return await handleAsNodeRequest(SERVER_PORT, request);
    } catch (error) {
      console.error("[fynx-worker] request bootstrap failed", {
        name: error?.name || "Error",
        message: error?.message || "unknown"
      });
      return Response.json(
        { error: "FYNX service is temporarily unavailable." },
        { status: 503, headers: { "Cache-Control": "no-store" } }
      );
    }
  }
};
