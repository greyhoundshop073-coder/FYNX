import assert from "node:assert/strict";
import bcrypt from "bcryptjs";
import { createEmailAuthController, isValidEmail, normalizeEmail } from "./emailAuthRoutes.js";

assert.equal(normalizeEmail("  Person@Example.com "), "person@example.com");
assert.equal(isValidEmail("person@example.com"), true);
assert.equal(isValidEmail("not-an-email"), false);
assert.equal(isValidEmail("person@example.com\nBcc:other@example.com"), false);

let clock = new Date("2026-11-23T10:00:00.000Z");
let nextCode = "123456";
const sent = [];
const calls = [];
let failNextDelivery = false;
const user = {
  id: "9",
  email: "person@example.com",
  email_verified_at: null,
  pending_email: null,
  pending_email_verification_code_hash: null,
  pending_email_verification_expires_at: null,
  pending_email_verification_attempts: 0,
  pending_email_verification_last_sent_at: null,
  email_verification_code_hash: null,
  email_verification_expires_at: null,
  email_verification_attempts: 0,
  email_verification_last_sent_at: null,
  password_reset_code_hash: null,
  password_reset_expires_at: null,
  password_reset_attempts: 0,
  password_reset_last_sent_at: null,
  password_hash: await bcrypt.hash("InitialPass123", 4),
  session_version: 0
};

const pool = {
  async query(sql, params = []) {
    calls.push(sql);
    const normalized = sql.replace(/\s+/g, " ").trim();

    if (normalized.startsWith("ALTER TABLE users ADD COLUMN")) return { rows: [] };
    if (normalized.startsWith("SELECT id,email,email_verified_at, email_verification_code_hash")) {
      return user.email.toLowerCase() === String(params[0]).toLowerCase() || user.pending_email?.toLowerCase() === String(params[0]).toLowerCase()
        ? { rows: [{ ...user }] }
        : { rows: [] };
    }
    if (normalized.startsWith("SELECT id, email, password_reset_last_sent_at")) {
      return user.email.toLowerCase() === String(params[0]).toLowerCase() && user.email_verified_at
        ? { rows: [{ id: user.id, email: user.email, password_reset_last_sent_at: user.password_reset_last_sent_at }] }
        : { rows: [] };
    }
    if (normalized.startsWith("SELECT id, password_reset_code_hash")) {
      return user.email.toLowerCase() === String(params[0]).toLowerCase() && user.email_verified_at
        ? { rows: [{
            id: user.id,
            email: user.email,
            password_reset_code_hash: user.password_reset_code_hash,
            password_reset_expires_at: user.password_reset_expires_at,
            password_reset_attempts: user.password_reset_attempts
          }] }
        : { rows: [] };
    }
    if (normalized.startsWith("SELECT id FROM users") && normalized.includes("LOWER(pending_email)")) {
      return { rows: [] };
    }
    if (normalized.startsWith("SELECT id,password_hash,email,email_verified_at,pending_email")) {
      return user.id === String(params[0]) ? { rows: [{ ...user }] } : { rows: [] };
    }
    if (normalized.startsWith("SELECT id,email,email_verified_at,email_verification_last_sent_at")) {
      return user.id === String(params[0]) ? { rows: [{ ...user }] } : { rows: [] };
    }
    if (normalized.startsWith("SELECT email, email_verified_at, pending_email FROM users")) {
      return user.id === String(params[0]) ? { rows: [{ email: user.email, email_verified_at: user.email_verified_at, pending_email: user.pending_email }] } : { rows: [] };
    }

    if (normalized.startsWith("UPDATE users SET pending_email_verification_code_hash = $2")) {
      const cooldown = Number(params[5] || 0);
      const lastSent = user.pending_email_verification_last_sent_at ? new Date(user.pending_email_verification_last_sent_at).getTime() : 0;
      if (user.pending_email !== params[4] || (cooldown > 0 && lastSent > 0 && clock.getTime() - lastSent < cooldown)) return { rows: [] };
      user.pending_email_verification_code_hash = params[1];
      user.pending_email_verification_expires_at = params[2];
      user.pending_email_verification_attempts = 0;
      user.pending_email_verification_last_sent_at = params[3];
      return { rows: [{ id: user.id }] };
    }
    if (normalized.startsWith("UPDATE users SET pending_email=$2")) {
      const sameVerifiedState = (!user.email_verified_at && !params[6]) ||
        (user.email_verified_at && params[6] && new Date(user.email_verified_at).getTime() === new Date(params[6]).getTime());
      if (user.email !== params[5] || !sameVerifiedState) return { rows: [] };
      user.pending_email = params[1];
      user.pending_email_verification_code_hash = params[2];
      user.pending_email_verification_expires_at = params[3];
      user.pending_email_verification_attempts = 0;
      user.pending_email_verification_last_sent_at = params[4];
      return { rows: [{ id: user.id }] };
    }
    if (normalized.startsWith("UPDATE users SET email=pending_email")) {
      if (user.pending_email !== params[4] || user.pending_email_verification_code_hash !== params[1] ||
          new Date(user.pending_email_verification_expires_at) <= new Date(params[2]) ||
          user.pending_email_verification_attempts >= params[3]) return { rows: [] };
      user.email = user.pending_email;
      user.email_verified_at = clock;
      user.email_verification_code_hash = null;
      user.email_verification_expires_at = null;
      user.email_verification_attempts = 0;
      user.email_verification_last_sent_at = null;
      user.pending_email = null;
      user.pending_email_verification_code_hash = null;
      user.pending_email_verification_expires_at = null;
      user.pending_email_verification_attempts = 0;
      user.pending_email_verification_last_sent_at = null;
      return { rows: [{ id: user.id }] };
    }
    if (normalized.startsWith("UPDATE users SET pending_email=NULL")) {
      if (user.pending_email === params[1] && user.pending_email_verification_code_hash === params[2]) {
        user.pending_email = null;
        user.pending_email_verification_code_hash = null;
        user.pending_email_verification_expires_at = null;
        user.pending_email_verification_attempts = 0;
        user.pending_email_verification_last_sent_at = null;
      }
      return { rows: [] };
    }
    if (normalized.startsWith("UPDATE users SET pending_email_verification_attempts=$3")) {
      user.pending_email_verification_attempts = params[2];
      if (params[2] >= params[3]) {
        user.pending_email = null;
        user.pending_email_verification_code_hash = null;
        user.pending_email_verification_expires_at = null;
        user.pending_email_verification_last_sent_at = null;
      }
      return { rows: [{ id: user.id }] };
    }
    if (normalized.startsWith("UPDATE users SET pending_email_verification_code_hash = NULL")) {
      user.pending_email_verification_code_hash = null;
      user.pending_email_verification_expires_at = null;
      user.pending_email_verification_attempts = 0;
      return { rows: [] };
    }
    if (normalized.includes("SET email_verification_code_hash = $2")) {
      const cooldown = Number(params[4] || 0);
      if (user.email_verified_at) return { rows: [] };
      const lastSent = user.email_verification_last_sent_at
        ? new Date(user.email_verification_last_sent_at).getTime()
        : 0;
      if (cooldown > 0 && lastSent > 0 && clock.getTime() - lastSent < cooldown) return { rows: [] };
      user.email_verification_code_hash = params[1];
      user.email_verification_expires_at = params[2];
      user.email_verification_attempts = 0;
      user.email_verification_last_sent_at = params[3];
      return { rows: [{ id: user.id, email: user.email }] };
    }
    if (normalized.startsWith("UPDATE users SET email_verified_at=NOW()")) {
      if (user.email_verification_code_hash !== params[1] ||
          new Date(user.email_verification_expires_at) <= new Date(params[2]) ||
          user.email_verification_attempts >= params[3]) return { rows: [] };
      user.email_verified_at = clock;
      user.email_verification_code_hash = null;
      user.email_verification_expires_at = null;
      user.email_verification_attempts = 0;
      return { rows: [{ id: user.id }] };
    }
    if (normalized.includes("SET email_verification_attempts=$3")) {
      user.email_verification_attempts = params[2];
      if (params[2] >= params[3]) {
        user.email_verification_code_hash = null;
        user.email_verification_expires_at = null;
      }
      return { rows: [{ id: user.id }] };
    }
    if (normalized.includes("SET password_reset_code_hash = $2")) {
      const lastSent = user.password_reset_last_sent_at
        ? new Date(user.password_reset_last_sent_at).getTime()
        : 0;
      if (lastSent > 0 && clock.getTime() - lastSent < 60_000) return { rows: [] };
      user.password_reset_code_hash = params[1];
      user.password_reset_expires_at = params[2];
      user.password_reset_attempts = 0;
      user.password_reset_last_sent_at = params[3];
      return { rows: [{ id: user.id }] };
    }
    if (normalized.includes("SET password_reset_attempts = $3")) {
      user.password_reset_attempts = params[2];
      if (params[2] >= params[3]) {
        user.password_reset_code_hash = null;
        user.password_reset_expires_at = null;
      }
      return { rows: [{ id: user.id }] };
    }
    if (normalized.includes("SET password_hash = $2")) {
      if (user.password_reset_code_hash !== params[2] ||
          new Date(user.password_reset_expires_at) <= new Date(params[3]) ||
          user.password_reset_attempts >= params[4]) return { rows: [] };
      user.password_hash = params[1];
      user.password_reset_code_hash = null;
      user.password_reset_expires_at = null;
      user.password_reset_attempts = 0;
      user.session_version++;
      return { rows: [{ id: user.id }] };
    }

    if (normalized.includes("SET email_verification_code_hash = NULL") ||
        normalized.includes("SET email_verification_code_hash=NULL") ||
        normalized.includes("SET password_reset_code_hash = NULL")) {
      if (normalized.includes("email_verification_code_hash = NULL") || normalized.includes("email_verification_code_hash=NULL")) {
        user.email_verification_code_hash = null;
        user.email_verification_expires_at = null;
        user.email_verification_attempts = 0;
      } else {
        user.password_reset_code_hash = null;
        user.password_reset_expires_at = null;
        user.password_reset_attempts = 0;
      }
      return { rows: [] };
    }
    throw new Error("Unexpected email-auth SQL: " + normalized.slice(0, 160));
  }
};

const delivery = {
  configured: true,
  async send(message) {
    if (failNextDelivery) {
      failNextDelivery = false;
      throw new Error("simulated SMTP outage");
    }
    sent.push(message);
    return { messageId: "test-" + sent.length };
  }
};
const controller = createEmailAuthController({
  pool,
  emailDelivery: delivery,
  tokenSecret: "test-secret-with-at-least-thirty-two-characters",
  bcrypt,
  now: () => new Date(clock),
  generateCode: () => nextCode
});

// Verification codes are HMAC-hashed, delivered, expiring and one-use.
assert.equal(await controller.sendVerificationForUser(user), true);
assert.equal(sent[0].to, "person@example.com");
assert.match(sent[0].text, /123456/);
assert.notEqual(user.email_verification_code_hash, "123456");
assert.equal(await controller.verifyEmail("person@example.com", "123456"), true);
assert.ok(user.email_verified_at);
assert.equal(user.email_verification_code_hash, null);
assert.equal(await controller.verifyEmail("person@example.com", "123456"), false);

// Password recovery is available only to verified email addresses and has cooldown + one-use semantics.
nextCode = "654321";
assert.equal(await controller.requestPasswordReset("person@example.com"), true);
assert.match(sent[1].text, /654321/);
assert.notEqual(user.password_reset_code_hash, "654321");
assert.equal(await controller.requestPasswordReset("person@example.com"), false);
assert.equal(await controller.confirmPasswordReset("person@example.com", "654321", "ReplacementPass456"), true);
assert.equal(user.session_version, 1);
assert.equal(await bcrypt.compare("ReplacementPass456", user.password_hash), true);
assert.equal(user.password_reset_code_hash, null);
assert.equal(await controller.confirmPasswordReset("person@example.com", "654321", "AnotherPass789"), false);
assert.equal(sent[2].to, "person@example.com");
assert.equal(sent[2].subject, "Your FYNX password was changed");

// Changing an address stages verification; the previous verified recovery address remains active until the new code is confirmed.
nextCode = "777333";
const attached = await controller.attachEmail("9", "ReplacementPass456", "New.Person@example.com");
assert.equal(attached.status, 200);
assert.equal(attached.email, "new.person@example.com");
assert.equal(attached.sent, true);
assert.equal(attached.pendingVerification, true);
assert.equal(sent[3].to, "new.person@example.com");
assert.equal(user.email, "person@example.com");
assert.ok(user.email_verified_at);
assert.equal(user.pending_email, "new.person@example.com");
assert.equal(await controller.attachEmail("9", "wrong-password", "other@example.com").then(x => x.status), 401);
assert.equal((await controller.attachEmail("9", "ReplacementPass456", "not-an-email")).status, 400);

// Protected status and resend routes are registered behind the existing auth middleware.
const authMiddleware = (_req, _res, next) => next();
const routes = new Map();
const app = {
  get(path, ...handlers) { routes.set("GET " + path, handlers); },
  post(path, ...handlers) { routes.set("POST " + path, handlers); }
};
controller.registerRoutes(app, authMiddleware);
assert.equal(routes.get("GET /api/auth/email/status")[0], authMiddleware);
assert.equal(routes.get("POST /api/auth/email/resend")[0], authMiddleware);

function response() {
  return {
    statusCode: 200,
    body: null,
    status(value) { this.statusCode = value; return this; },
    json(value) { this.body = value; return this; }
  };
}
const statusResponse = response();
await routes.get("GET /api/auth/email/status").at(-1)({ user: { sub: "9" } }, statusResponse);
assert.equal(statusResponse.statusCode, 200);
assert.deepEqual(statusResponse.body, { email: "new.person@example.com", emailVerified: false, pendingEmail: true });

const cooldownResponse = response();
await routes.get("POST /api/auth/email/resend").at(-1)({ user: { sub: "9" } }, cooldownResponse);
assert.equal(cooldownResponse.statusCode, 429);
clock = new Date(clock.getTime() + 61_000);
const resendResponse = response();
await routes.get("POST /api/auth/email/resend").at(-1)({ user: { sub: "9" } }, resendResponse);
assert.equal(resendResponse.statusCode, 200);
assert.equal(resendResponse.body.emailVerificationSent, true);
assert.match(sent.at(-1).text, /777333/);
assert.equal(await controller.verifyEmail("new.person@example.com", "777333"), true);
assert.equal(user.email, "new.person@example.com");
assert.equal(user.pending_email, null);
assert.equal(user.email_verified_at, clock);
assert.equal(sent.at(-1).to, "person@example.com");
assert.equal(sent.at(-1).subject, "Your FYNX account email was changed");

// Expired verification codes are rejected and cleared.
clock = new Date(clock.getTime() + 61_000);
nextCode = "888111";
user.email_verified_at = null;
assert.equal(await controller.sendVerificationForUser(user), true);
user.email_verification_expires_at = new Date(clock.getTime() - 1_000);
assert.equal(await controller.verifyEmail("new.person@example.com", "888111"), false);
assert.equal(user.email_verification_code_hash, null);

// An email already verified on this account is not needlessly detached by saving it again.
user.email_verified_at = clock;
const unchanged = await controller.attachEmail("9", "ReplacementPass456", "new.person@example.com");
assert.equal(unchanged.unchanged, true);
assert.equal(unchanged.email, "new.person@example.com");

// Password-reset expiry is also enforced independently of verification-code expiry.
clock = new Date(clock.getTime() + 61_000);
nextCode = "555444";
assert.equal(await controller.requestPasswordReset("new.person@example.com"), true);
user.password_reset_expires_at = new Date(clock.getTime() - 1_000);
assert.equal(await controller.confirmPasswordReset("new.person@example.com", "555444", "ExpiredPass123"), false);
assert.equal(user.password_reset_code_hash, null);

// Five failed attempts invalidate a verification code.
clock = new Date(clock.getTime() + 61_000);
nextCode = "999222";
user.email_verified_at = null;
assert.equal(await controller.sendVerificationForUser(user), true);
for (let i = 0; i < 5; i++) assert.equal(await controller.verifyEmail("new.person@example.com", "000000"), false);
assert.equal(user.email_verification_attempts, 5);
assert.equal(await controller.verifyEmail("new.person@example.com", "999222"), false);

// Failed SMTP delivery does not leave an active verification code or misreport success.
clock = new Date(clock.getTime() + 61_000);
user.email_verification_code_hash = null;
user.email_verification_expires_at = null;
failNextDelivery = true;
assert.equal(await controller.sendVerificationForUser(user), false);
assert.equal(user.email_verification_code_hash, null);

// A failed provider send must leave an already verified recovery address untouched.
user.email_verified_at = new Date(clock);
const retainedEmail = user.email;
failNextDelivery = true;
const failedChange = await controller.attachEmail("9", "ReplacementPass456", "mail-outage@example.com");
assert.equal(failedChange.status, 503);
assert.equal(failedChange.retained, true);
assert.equal(user.email, retainedEmail);
assert.ok(user.email_verified_at);
assert.equal(user.pending_email, null);
assert.equal(user.email_verification_code_hash, null);

// Five failed attempts on a pending replacement address cancel only the pending change.
// The previously verified address must remain active and usable for recovery.
nextCode = "222333";
const verifiedBeforePendingAttempt = user.email;
const verifiedAtBeforePendingAttempt = user.email_verified_at;
const pendingGuard = await controller.attachEmail("9", "ReplacementPass456", "new.pending@example.com");
assert.equal(pendingGuard.status, 200);
assert.equal(user.email, verifiedBeforePendingAttempt);
assert.equal(user.email_verified_at, verifiedAtBeforePendingAttempt);
for (let i = 0; i < 5; i++) assert.equal(await controller.verifyEmail("new.pending@example.com", "000000"), false);
assert.equal(user.pending_email, null);
assert.equal(user.email, verifiedBeforePendingAttempt);
assert.equal(user.email_verified_at, verifiedAtBeforePendingAttempt);

// Email settings must not disclose or replace another account's address.
assert.equal((await controller.attachEmail("9", "ReplacementPass456", "new.person@example.com")).unchanged, true);

assert.ok(calls.some(sql => sql.includes("session_version = COALESCE(session_version, 0) + 1")));
console.log("PASS: email verification, expiry, single-use, five-attempt limit, cooldown, password recovery, session revocation, atomic email changes, SMTP-failure recovery preservation, notices and authenticated Settings routes");
