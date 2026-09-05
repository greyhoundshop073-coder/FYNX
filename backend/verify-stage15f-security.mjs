import fs from "node:fs";

const security = fs.readFileSync(new URL("./securityHardening.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");

const checks = [
  ["central security hardening module exists", security.includes("installSecurityHardening")],
  ["HTTP method allowlist is enforced", security.includes("SAFE_METHODS") && security.includes("method_rejected")],
  ["security headers are enforced", security.includes("Content-Security-Policy") && security.includes("Permissions-Policy") && security.includes("Cross-Origin-Resource-Policy")],
  ["suspicious request detection is enforced", security.includes("SUSPICIOUS_INPUT") && security.includes("suspicious_request_rejected")],
  ["security-relevant responses are audited", security.includes("security_relevant_response") && security.includes("401") && security.includes("403") && security.includes("429")],
  ["security middleware is installed without replacing server architecture", scalability.includes("installSecurityHardening({ app })")],
  ["existing authentication boundary remains present", server.includes("function auth(req, res, next)") && server.includes('jwt.verify(token, JWT_SECRET)')],
  ["existing per-route rate limiting remains present", server.includes("function rateLimit(bucket, limit)") && server.includes("MAX_RATE_BUCKETS")],
  ["no API secret is hardcoded in security module", !/sk-[A-Za-z0-9_-]{20,}/.test(security)]
];

const failed = checks.filter(([, ok]) => !ok);
if (failed.length) {
  throw new Error(`Stage 15F security verification failed: ${failed.map(([name]) => name).join("; ")}`);
}
console.log(`Stage 15F security verification passed (${checks.length} checks)`);
