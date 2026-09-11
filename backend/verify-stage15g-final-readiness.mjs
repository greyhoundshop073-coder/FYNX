import fs from "node:fs";

const packageJson = JSON.parse(fs.readFileSync(new URL("./package.json", import.meta.url), "utf8"));
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const bootstrap = fs.readFileSync(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const isolation = fs.readFileSync(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
const security = fs.readFileSync(new URL("./securityHardening.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const workflow = fs.readFileSync(new URL("../.github/workflows/android-build.yml", import.meta.url), "utf8");

const startUsesScalability = packageJson.scripts?.start === "node --import ./scalability.js server.js"
  || (packageJson.scripts?.start === "node serverBootstrap.js" && bootstrap.includes('import("./scalability.js")'))
  || (packageJson.scripts?.start === "node realtimeIsolationBootstrap.js"
    && isolation.includes('import("./serverBootstrap.js")')
    && bootstrap.includes('import("./scalability.js")'));

const checks = [
  ["production start command loads scalability before the server", startUsesScalability],
  ["server wires the core FYNX route modules", ["registerSocialRoutes", "registerMarketplaceTransactionRoutes", "registerMarketplaceReputationRoutes", "registerMarketplaceCompletionRoutes", "registerMarketplaceAdvertisingRoutes"].every((name) => server.includes(name))],
  ["security hardening is installed through the runtime bootstrap", scalability.includes("installSecurityHardening")],
  ["security hardening detects authentication and authorization failures", security.includes("repeated_auth_failures") && security.includes("repeated_authorization_failures")],
  ["Android CI uses short-lived Google Cloud federation instead of a service-account key", workflow.includes("google-github-actions/auth@v3") && workflow.includes("workload_identity_provider") && !workflow.includes("credentials_json")],
  ["Android CI builds both the app and instrumentation APK", workflow.includes("assembleDebug") && workflow.includes("assembleDebugAndroidTest")]
];

for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15G final readiness check failed: ${name}`);
  console.log(`PASS: ${name}`);
}
console.log(`Stage 15G final readiness verification passed (${checks.length} checks)`);
