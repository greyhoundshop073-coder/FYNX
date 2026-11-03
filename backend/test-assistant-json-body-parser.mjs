import assert from "node:assert/strict";
import express from "express";
import { assistantJsonBodyParser } from "./assistantJsonBodyParser.js";
import { registerFynxAiConversationRoutes } from "./aiConversationRoutes.js";
import { registerRealtimeAssistantRoutes } from "./aiRealtimeRoutes.js";
import { registerFynxAiRoutes } from "./fynxAiToolRegistry.js";

const registered = [];
const captureApp = {
  get(path, ...handlers) { registered.push({ method: "GET", path, handlers }); },
  post(path, ...handlers) { registered.push({ method: "POST", path, handlers }); },
  delete(path, ...handlers) { registered.push({ method: "DELETE", path, handlers }); }
};

registerFynxAiConversationRoutes({ app: captureApp });
registerFynxAiRoutes({ app: captureApp });
registerRealtimeAssistantRoutes({ app: captureApp });

const jsonRoutes = [
  "/api/assistant/conversations",
  "/api/assistant/conversations/:id/message",
  "/api/assistant/agent",
  "/api/assistant/tools",
  "/api/assistant/realtime-session",
  "/api/assistant/realtime-tool"
];

for (const path of jsonRoutes) {
  const route = registered.find(item => item.method === "POST" && item.path === path);
  assert.ok(route, `expected assistant POST route ${path} to be registered`);
  assert.equal(route.handlers.length, 2, `${path} must have a route-local parser and one handler`);
  assert.strictEqual(route.handlers[0], assistantJsonBodyParser, `${path} must parse JSON before its handler`);
  assert.equal(typeof route.handlers[1], "function");
}

const app = express();
app.post("/parser-probe", assistantJsonBodyParser, (req, res) => {
  res.json({ body: req.body ?? null });
});
const server = app.listen(0, "127.0.0.1");
try {
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const response = await fetch(`http://127.0.0.1:${address.port}/parser-probe`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: "Hi", mediaIds: [] })
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { body: { message: "Hi", mediaIds: [] } });
} finally {
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}

console.log(`FYNX assistant JSON body parser GREEN: ${jsonRoutes.length} routes wired and request body parsed`);
