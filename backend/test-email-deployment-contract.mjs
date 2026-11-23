import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (url) => readFile(new URL(url, import.meta.url), "utf8");
const [neonConfig, deployWorkflow, deployGuide, emailAdapter] = await Promise.all([
  read("./neon.ts"),
  read("../.github/workflows/fynx-neon-function-deploy.yml"),
  read("./NEON_FUNCTIONS_DEPLOYMENT.md"),
  read("./emailDelivery.js")
]);

const optional = [
  "FYNX_EMAIL_HOST",
  "FYNX_EMAIL_PORT",
  "FYNX_EMAIL_USER",
  "FYNX_EMAIL_PASSWORD",
  "FYNX_EMAIL_FROM",
  "FYNX_EMAIL_SECURE",
  "FYNX_EMAIL_REQUIRE_TLS"
];
for (const name of optional) {
  assert.ok(
    neonConfig.includes(`${name}: { value: process.env.${name}, required: false }`),
    `${name} must be optional in the isolated Neon Function configuration`
  );
  assert.ok(deployWorkflow.includes(`${name}=$${name}`), `${name} must be written to the temporary deploy env file`);
  assert.ok(deployGuide.includes(name), `${name} must be documented for secure operator setup`);
}
for (const name of optional) {
  assert.ok(deployWorkflow.includes(`secrets.${name}`), `${name} must come from a GitHub Actions secret`);
}
for (const name of optional) {
  assert.ok(deployWorkflow.includes(`${name}: \${{ secrets.${name} }}`), `${name} must be forwarded from its secret in the deployment steps`);
}
assert.match(deployWorkflow, /trap 'rm -f \.env\.neon\.deploy' EXIT/);
assert.match(emailAdapter, /FYNX_EMAIL_PASSWORD/);
assert.match(emailAdapter, /rejectUnauthorized: true/);
console.log("PASS: optional Neon SMTP settings, GitHub Actions secret forwarding and temporary credential-file cleanup are wired");
