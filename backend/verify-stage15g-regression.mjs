import fs from "node:fs";

const requiredVerifiers = [
  "verify-stage14.mjs",
  "verify-stage15.mjs",
  "verify-stage15b.mjs",
  "verify-stage15c.mjs",
  "verify-stage15c-batch2.mjs",
  "verify-stage15c-batch3.mjs",
  "verify-stage15d-batch1.mjs",
  "verify-stage15d-batch2.mjs",
  "verify-stage15d-reliability.mjs",
  "verify-stage15d-advanced.mjs",
  "verify-stage15d-jobs.mjs",
  "verify-stage15e-performance.mjs",
  "verify-stage15f-security.mjs",
  "verify-stage15g-integration.mjs",
  "verify-stage15g-final-readiness.mjs"
];

const missing = requiredVerifiers.filter((file) => !fs.existsSync(new URL(`./${file}`, import.meta.url)));
if (missing.length) {
  throw new Error(`Stage 15G regression gate missing verifier files: ${missing.join(", ")}`);
}

const workflow = fs.readFileSync(new URL("../.github/workflows/fynx-backend-ci.yml", import.meta.url), "utf8");
const requiredWorkflowChecks = [
  "Run consolidated Stage 14 verification",
  "Run Stage 15 settlement verification",
  "Run Stage 15B buyer/seller protection verification",
  "Run Stage 15C advertising foundation verification",
  "Run Stage 15D reliability verification",
  "Run Stage 15D background jobs verification",
  "Run Stage 15E performance verification",
  "Run Stage 15F security hardening verification",
  "Run Stage 15G integration verification",
  "Run Stage 15G final readiness verification",
  "Run Stage 15G regression verification"
];
const missingWorkflowChecks = requiredWorkflowChecks.filter((name) => !workflow.includes(name));
if (missingWorkflowChecks.length) {
  throw new Error(`Stage 15G regression gate missing CI checks: ${missingWorkflowChecks.join(", ")}`);
}

for (const file of requiredVerifiers) console.log(`PASS: verifier present: ${file}`);
for (const name of requiredWorkflowChecks) console.log(`PASS: CI gate present: ${name}`);
console.log(`Stage 15G regression verification passed (${requiredVerifiers.length} verifier files, ${requiredWorkflowChecks.length} CI gates)`);
