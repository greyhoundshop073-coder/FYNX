import assert from "node:assert/strict";
import bcrypt from "bcryptjs";
import { createEmailAuthController, isValidEmail, normalizeEmail } from "./emailAuthRoutes.js";

assert.equal(normalizeEmail("  Person@Example.com "), "person@example.com");
assert.equal(isValidEmail("person@example.com"), true);
assert.equal(isValidEmail("not-an-email"), false);
assert.equal(isValidEmail("person@example.com\nBcc:other@example.com"), false);

let clock = new Date("2026-11-08T12:00:00.000Z");
let nextCode = "123456";
const sent = [];
const calls = [];
const user = {
  id: "9", email: "person@example.com", email_verified_at: null,
  email_verification_code_hash: null, email_verification_expires_at: null,
  email_verification_attempts: 0, email_verification_last_sent_at: null,
  password_reset_code_hash: null, password_reset_expires_at: null,
  password_reset_attempts: 0, password_reset_last_sent_at: null,
  password_hash: await bcrypt.hash("InitialPass123", 4), session_version: 0
};
const pool = {
  async query(sql, params = []) {
    calls.push(sql);
    if (sql.includes("SET email_verification_code_hash = $2")) {
      if (user.email_verified_at) return { rows: [] };
      if (Number(params[4]) > 0 && user.email_verification_last_sent_at &&
          clock - new Date(user.email_verification_last_sent_at) < Number(params[4])) return { rows: [] };
      user.email_verification_code_hash = params[1];
      user.email_verification_expires_at = params[2];
      user.email_verification_attempts = 0;
      user.email_verification_last_sent_at = params[3];
      return { rows: [{ id: user.id, email: user.email }] };
    }
    if (sql.includes("SELECT id, email_verification_code_hash")) return { rows: [{ ...user }] };
    if (sql.includes("SET email_verified_at = NOW()")) {
      if (user.email_verification_code_hash !== params[1] || new Date(user.email_verification_expires_at) <= params[2]) return { rows: [] };
      user.email_verified_at = clock;
      user.email_verification_code_hash = null;
      user.email_verification_expires_at = null;
      user.email_verification_attempts = 0;
      return { rows: [{ id: user.id }] };
    }
    if (sql.includes("SET email_verification_attempts = $3")) {
      user.email_verification_attempts = params[2];
      if (params[2] >= params[3]) { user.email_verification_code_hash = null; user.email_verification_expires_at = null; }
      return { rows: [{ id: user.id }] };
    }
    if (sql.includes("SELECT id, email, password_reset_last_sent_at")) {
      return user.email === params[0] && user.email_verified_at ? { rows: [{ id: user.id, email: user.email, password_reset_last_sent_at: user.password_reset_last_sent_at }] } : { rows: [] };
    }
    if (sql.includes("SET password_reset_code_hash = $2")) {
      user.password_reset_code_hash = params[1];
      user.password_reset_expires_at = params[2];
      user.password_reset_attempts = 0;
      user.password_reset_last_sent_at = params[3];
      return { rows: [{ id: user.id, email: user.email }] };
    }
    if (sql.includes("SELECT id, password_reset_code_hash")) {
      return user.email === params[0] && user.email_verified_at ? { rows: [{ id: user.id, password_reset_code_hash: user.password_reset_code_hash, password_reset_expires_at: user.password_reset_expires_at, password_reset_attempts: user.password_reset_attempts }] } : { rows: [] };
    }
    if (sql.includes("SET password_reset_attempts = $3")) {
      user.password_reset_attempts = params[2];
      if (params[2] >= params[3]) { user.password_reset_code_hash = null; user.password_reset_expires_at = null; }
      return { rows: [{ id: user.id }] };
    }
    if (sql.includes("SET password_hash = $2")) {
      if (user.password_reset_code_hash !== params[2] || new Date(user.password_reset_expires_at) <= params[3]) return { rows: [] };
      user.password_hash = params[1];
      user.password_reset_code_hash = null;
      user.password_reset_expires_at = null;
      user.password_reset_attempts = 0;
      user.session_version++;
      return { rows: [{ id: user.id }] };
    }
    if (sql.includes("SET email_verification_code_hash = NULL") || sql.includes("SET password_reset_code_hash = NULL")) return { rows: [] };
    throw new Error("Unexpected email auth SQL: " + sql.slice(0, 100));
  }
};
const delivery = { configured: true, async send(message) { sent.push(message); return { messageId: "test-" + sent.length }; } };
const controller = createEmailAuthController({
  pool, emailDelivery: delivery,
  tokenSecret: "test-secret-with-at-least-thirty-two-characters",
  bcrypt, now: () => new Date(clock), generateCode: () => nextCode
});

assert.equal(await controller.sendVerificationForUser(user), true);
assert.equal(sent[0].to, "person@example.com");
assert.match(sent[0].text, /123456/);
assert.notEqual(user.email_verification_code_hash, "123456");
assert.equal(await controller.verifyEmail("person@example.com", "123456"), true);
assert.ok(user.email_verified_at);
assert.equal(user.email_verification_code_hash, null);

nextCode = "654321";
assert.equal(await controller.requestPasswordReset("person@example.com"), true);
assert.match(sent[1].text, /654321/);
assert.notEqual(user.password_reset_code_hash, "654321");
assert.equal(await controller.confirmPasswordReset("person@example.com", "654321", "ReplacementPass456"), true);
assert.equal(user.session_version, 1);
assert.equal(await bcrypt.compare("ReplacementPass456", user.password_hash), true);
assert.equal(user.password_reset_code_hash, null);

nextCode = "111222";
user.email_verified_at = null;
assert.equal(await controller.sendVerificationForUser(user), true);
assert.equal(await controller.verifyEmail("person@example.com", "000000"), false);
assert.equal(user.email_verification_attempts, 1);
user.email_verification_expires_at = new Date(clock.getTime() - 1000);
assert.equal(await controller.verifyEmail("person@example.com", "111222"), false);
assert.ok(calls.length > 0);

console.log("PASS: FYNX email verification, password recovery, HMAC code storage, attempt limits and session versioning");
