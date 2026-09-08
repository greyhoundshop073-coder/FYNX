import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

// Bootstrap guard for the production backend. The current server defines RATE_LIMITS
// below its middleware registration; generate a startup copy with the fixed literal
// limits so the service cannot enter the TDZ before the main server is initialized.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const sourcePath = path.join(backendDir, "server.js");
const runtimePath = path.join(backendDir, ".fynx-runtime-server.js");
let source = await readFile(sourcePath, "utf8");
const replacements = [
  ['app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));', 'app.use("/api/auth", rateLimit("auth", 20));'],
  ['app.use("/api/assistant", rateLimit("assistant", RATE_LIMITS.assistant));', 'app.use("/api/assistant", rateLimit("assistant", 20));'],
  ['app.use("/api/media", rateLimit("media", RATE_LIMITS.media));', 'app.use("/api/media", rateLimit("media", 30));'],
  ['app.use("/api/messages", rateLimit("messages", RATE_LIMITS.messages));', 'app.use("/api/messages", rateLimit("messages", 120));']
];
for (const [from, to] of replacements) source = source.replace(from, to);
await writeFile(runtimePath, source, "utf8");
await import(`${pathToFileURL(runtimePath).href}?boot=${Date.now()}`);
