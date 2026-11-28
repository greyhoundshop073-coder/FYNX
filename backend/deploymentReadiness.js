const VALID_DATABASE_STATES = new Set(["ready", "unavailable", "not-configured"]);
const VALID_MEDIA_PROVIDERS = new Set(["postgres", "neon", "r2"]);

/**
 * Build a secret-safe readiness snapshot for the core FYNX API.
 *
 * This validates database reachability and required configuration shape.
 * It deliberately does not call optional AI, email, payment, or call providers:
 * those integrations must fail closed independently without taking down login,
 * social, messaging, or Marketplace read paths.
 */
export function assessFynxBackendReadiness({
  databaseStatus = "not-configured",
  jwtSecretConfigured = false,
  mediaStorageConfigured = false,
  mediaStorageProvider = "unknown"
} = {}) {
  const database = VALID_DATABASE_STATES.has(databaseStatus)
    ? databaseStatus
    : "unavailable";
  const provider = VALID_MEDIA_PROVIDERS.has(mediaStorageProvider)
    ? mediaStorageProvider
    : "unknown";
  const checks = {
    database,
    authentication: jwtSecretConfigured === true ? "ready" : "not-configured",
    mediaStorage: mediaStorageConfigured === true ? "ready" : "not-configured",
    mediaStorageProvider: provider
  };
  const ready = checks.database === "ready" &&
    checks.authentication === "ready" &&
    checks.mediaStorage === "ready";
  return Object.freeze({ ready, checks: Object.freeze(checks) });
}
