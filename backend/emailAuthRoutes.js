import { createHmac, randomInt, timingSafeEqual } from "node:crypto";

const EMAIL_CODE_TTL_MS = 15 * 60 * 1000;
const PASSWORD_CODE_TTL_MS = 10 * 60 * 1000;
const CODE_RESEND_COOLDOWN_MS = 60 * 1000;
const MAX_CODE_ATTEMPTS = 5;
const GENERIC_RESET_MESSAGE = "If a verified email is registered with FYNX, a password reset code will be sent.";

export function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

export function isValidEmail(value) {
  const email = normalizeEmail(value);
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function digest(code, purpose, secret) {
  return createHmac("sha256", secret).update(`fynx:${purpose}:${code}`).digest("hex");
}

function constantTimeHexEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string" ||
      !/^[a-f0-9]{64}$/i.test(left) || !/^[a-f0-9]{64}$/i.test(right)) return false;
  const a = Buffer.from(left, "hex");
  const b = Buffer.from(right, "hex");
  return a.length === b.length && timingSafeEqual(a, b);
}

function makeCode() {
  return String(randomInt(0, 1_000_000)).padStart(6, "0");
}

function sendJson(res, status, body) {
  return res.status(status).json(body);
}

export function createEmailAuthController({
  pool, emailDelivery, tokenSecret, bcrypt,
  now = () => new Date(), generateCode = makeCode
}) {
  const configured = Boolean(emailDelivery?.configured && typeof emailDelivery.send === "function");
  const canHash = typeof tokenSecret === "string" && tokenSecret.length >= 32;

  async function sendVerificationForUser(user, { respectCooldown = false } = {}) {
    if (!pool || !canHash || !configured || !user?.id || !isValidEmail(user.email)) return false;
    const code = generateCode();
    if (!/^\d{6}$/.test(code)) throw new Error("Email code generator returned an invalid code.");
    const hashed = digest(code, "email-verification", tokenSecret);
    const issuedAt = now();
    const expiresAt = new Date(issuedAt.getTime() + EMAIL_CODE_TTL_MS);
    const updated = await pool.query(
      `UPDATE users
       SET email_verification_code_hash = $2,
           email_verification_expires_at = $3,
           email_verification_attempts = 0,
           email_verification_last_sent_at = $4
       WHERE id = $1 AND email_verified_at IS NULL
         AND ($5::bigint = 0 OR email_verification_last_sent_at IS NULL
              OR email_verification_last_sent_at <= $4 - ($5::bigint * INTERVAL '1 millisecond'))
       RETURNING id`,
      [user.id, hashed, expiresAt, issuedAt, respectCooldown ? CODE_RESEND_COOLDOWN_MS : 0]
    );
    if (!updated.rows?.length) return false;
    try {
      await emailDelivery.send({
        to: user.email,
        subject: "Verify your FYNX email address",
        text: `Your FYNX verification code is ${code}. It expires in 15 minutes. If you did not request this, ignore this email.`
      });
      return true;
    } catch {
      await pool.query(
        `UPDATE users SET email_verification_code_hash = NULL,
                           email_verification_expires_at = NULL,
                           email_verification_attempts = 0
         WHERE id = $1 AND email_verification_code_hash = $2`,
        [user.id, hashed]
      ).catch(() => {});
      return false;
    }
  }

  async function verifyEmail(emailInput, codeInput) {
    if (!pool || !canHash) return false;
    const email = normalizeEmail(emailInput);
    const code = typeof codeInput === "string" ? codeInput.trim() : "";
    if (!isValidEmail(email) || !/^\d{6}$/.test(code)) return false;
    const result = await pool.query(
      `SELECT id, email_verification_code_hash, email_verification_expires_at,
              email_verification_attempts, email_verified_at
       FROM users WHERE LOWER(email) = LOWER($1) LIMIT 1`,
      [email]
    );
    const user = result.rows?.[0];
    if (!user || user.email_verified_at || !user.email_verification_code_hash) return false;
    const expiry = new Date(user.email_verification_expires_at).getTime();
    const attempts = Number(user.email_verification_attempts || 0);
    if (!Number.isFinite(expiry) || expiry <= now().getTime() || attempts >= MAX_CODE_ATTEMPTS) {
      await pool.query(
        `UPDATE users SET email_verification_code_hash = NULL,
                           email_verification_expires_at = NULL,
                           email_verification_attempts = 0
         WHERE id = $1 AND email_verification_code_hash = $2`,
        [user.id, user.email_verification_code_hash]
      );
      return false;
    }
    const submitted = digest(code, "email-verification", tokenSecret);
    if (!constantTimeHexEqual(submitted, user.email_verification_code_hash)) {
      const nextAttempts = attempts + 1;
      await pool.query(
        `UPDATE users SET email_verification_attempts = $3,
             email_verification_code_hash = CASE WHEN $3 >= $4 THEN NULL ELSE email_verification_code_hash END,
             email_verification_expires_at = CASE WHEN $3 >= $4 THEN NULL ELSE email_verification_expires_at END
         WHERE id = $1 AND email_verification_code_hash = $2`,
        [user.id, user.email_verification_code_hash, nextAttempts, MAX_CODE_ATTEMPTS]
      );
      return false;
    }
    const updated = await pool.query(
      `UPDATE users SET email_verified_at = NOW(),
           email_verification_code_hash = NULL,
           email_verification_expires_at = NULL,
           email_verification_attempts = 0
       WHERE id = $1 AND email_verification_code_hash = $2
         AND email_verification_expires_at > $3 AND email_verification_attempts < $4
       RETURNING id`,
      [user.id, user.email_verification_code_hash, now(), MAX_CODE_ATTEMPTS]
    );
    return Boolean(updated.rows?.length);
  }

  async function requestVerification(emailInput) {
    if (!pool || !configured) return false;
    const email = normalizeEmail(emailInput);
    if (!isValidEmail(email)) return false;
    const found = await pool.query(
      `SELECT id, email, email_verified_at FROM users
       WHERE LOWER(email) = LOWER($1) AND email_verified_at IS NULL LIMIT 1`,
      [email]
    );
    if (!found.rows?.[0]) return false;
    return sendVerificationForUser(found.rows[0], { respectCooldown: true });
  }

  async function requestPasswordReset(emailInput) {
    if (!pool || !canHash || !configured) return false;
    const email = normalizeEmail(emailInput);
    if (!isValidEmail(email)) return false;
    const found = await pool.query(
      `SELECT id, email, password_reset_last_sent_at FROM users
       WHERE LOWER(email) = LOWER($1) AND email_verified_at IS NOT NULL LIMIT 1`,
      [email]
    );
    const user = found.rows?.[0];
    if (!user) return false;
    const lastSent = user.password_reset_last_sent_at ? new Date(user.password_reset_last_sent_at).getTime() : 0;
    if (lastSent > 0 && now().getTime() - lastSent < CODE_RESEND_COOLDOWN_MS) return false;

    const code = generateCode();
    if (!/^\d{6}$/.test(code)) throw new Error("Email code generator returned an invalid code.");
    const hashed = digest(code, "password-reset", tokenSecret);
    const issuedAt = now();
    const expiresAt = new Date(issuedAt.getTime() + PASSWORD_CODE_TTL_MS);
    const updated = await pool.query(
      `UPDATE users
       SET password_reset_code_hash = $2, password_reset_expires_at = $3,
           password_reset_attempts = 0, password_reset_last_sent_at = $4
       WHERE id = $1 AND email_verified_at IS NOT NULL
         AND (password_reset_last_sent_at IS NULL OR password_reset_last_sent_at <= $4 - INTERVAL '60 seconds')
       RETURNING id`,
      [user.id, hashed, expiresAt, issuedAt]
    );
    if (!updated.rows?.length) return false;
    try {
      await emailDelivery.send({
        to: user.email,
        subject: "Reset your FYNX password",
        text: `Your FYNX password reset code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email; your password will not change.`
      });
      return true;
    } catch {
      await pool.query(
        `UPDATE users SET password_reset_code_hash = NULL,
                           password_reset_expires_at = NULL,
                           password_reset_attempts = 0
         WHERE id = $1 AND password_reset_code_hash = $2`,
        [user.id, hashed]
      ).catch(() => {});
      return false;
    }
  }

  async function confirmPasswordReset(emailInput, codeInput, newPassword) {
    if (!pool || !canHash || !bcrypt) return false;
    const email = normalizeEmail(emailInput);
    const code = typeof codeInput === "string" ? codeInput.trim() : "";
    if (!isValidEmail(email) || !/^\d{6}$/.test(code) ||
        typeof newPassword !== "string" || newPassword.length < 8 || newPassword.length > 128) return false;
    const result = await pool.query(
      `SELECT id, password_reset_code_hash, password_reset_expires_at, password_reset_attempts
       FROM users WHERE LOWER(email) = LOWER($1) AND email_verified_at IS NOT NULL LIMIT 1`,
      [email]
    );
    const user = result.rows?.[0];
    if (!user || !user.password_reset_code_hash) return false;
    const expiry = new Date(user.password_reset_expires_at).getTime();
    const attempts = Number(user.password_reset_attempts || 0);
    if (!Number.isFinite(expiry) || expiry <= now().getTime() || attempts >= MAX_CODE_ATTEMPTS) {
      await pool.query(
        `UPDATE users SET password_reset_code_hash = NULL,
                           password_reset_expires_at = NULL, password_reset_attempts = 0
         WHERE id = $1 AND password_reset_code_hash = $2`,
        [user.id, user.password_reset_code_hash]
      );
      return false;
    }
    const submitted = digest(code, "password-reset", tokenSecret);
    if (!constantTimeHexEqual(submitted, user.password_reset_code_hash)) {
      const nextAttempts = attempts + 1;
      await pool.query(
        `UPDATE users SET password_reset_attempts = $3,
             password_reset_code_hash = CASE WHEN $3 >= $4 THEN NULL ELSE password_reset_code_hash END,
             password_reset_expires_at = CASE WHEN $3 >= $4 THEN NULL ELSE password_reset_expires_at END
         WHERE id = $1 AND password_reset_code_hash = $2`,
        [user.id, user.password_reset_code_hash, nextAttempts, MAX_CODE_ATTEMPTS]
      );
      return false;
    }
    const passwordHash = await bcrypt.hash(newPassword, 12);
    const updated = await pool.query(
      `UPDATE users
       SET password_hash = $2,
           password_reset_code_hash = NULL,
           password_reset_expires_at = NULL,
           password_reset_attempts = 0,
           session_version = COALESCE(session_version, 0) + 1
       WHERE id = $1 AND password_reset_code_hash = $3
         AND password_reset_expires_at > $4 AND password_reset_attempts < $5
       RETURNING id`,
      [user.id, passwordHash, user.password_reset_code_hash, now(), MAX_CODE_ATTEMPTS]
    );
    return Boolean(updated.rows?.length);
  }

  async function attachEmail(userId, password, emailInput) {
    if (!pool || !canHash || !bcrypt || typeof password !== "string" || !password) return { status: 400, sent: false };
    const email = normalizeEmail(emailInput);
    if (!isValidEmail(email)) return { status: 400, sent: false };
    const found = await pool.query("SELECT id, password_hash FROM users WHERE id = $1 LIMIT 1", [userId]);
    const user = found.rows?.[0];
    if (!user || !(await bcrypt.compare(password, user.password_hash))) return { status: 401, sent: false };
    const updated = await pool.query(
      `UPDATE users SET email = $2, email_verified_at = NULL,
           email_verification_code_hash = NULL, email_verification_expires_at = NULL,
           email_verification_attempts = 0, email_verification_last_sent_at = NULL
       WHERE id = $1 RETURNING id, email`,
      [userId, email]
    );
    if (!updated.rows?.[0]) return { status: 404, sent: false };
    return { status: 200, sent: await sendVerificationForUser(updated.rows[0]) };
  }

  function registerRoutes(app, auth) {
    app.post("/api/auth/email/verify", async (req, res) => {
      try {
        if (await verifyEmail(req.body?.email, req.body?.code)) return sendJson(res, 200, { ok: true, emailVerified: true });
        return sendJson(res, 400, { error: "The verification code is invalid or expired." });
      } catch {
        return sendJson(res, 503, { error: "Email verification is temporarily unavailable." });
      }
    });

    app.post("/api/auth/email/verification/resend", async (req, res) => {
      try {
        await requestVerification(req.body?.email);
        return sendJson(res, 200, { ok: true, deliveryConfigured: configured });
      } catch {
        return sendJson(res, 503, { error: "Email verification is temporarily unavailable." });
      }
    });

    app.post("/api/auth/password-reset/request", async (req, res) => {
      try {
        await requestPasswordReset(req.body?.email);
        return sendJson(res, 200, { ok: true, deliveryConfigured: configured, message: GENERIC_RESET_MESSAGE });
      } catch {
        return sendJson(res, 200, { ok: true, deliveryConfigured: configured, message: GENERIC_RESET_MESSAGE });
      }
    });

    app.post("/api/auth/password-reset/confirm", async (req, res) => {
      try {
        if (!await confirmPasswordReset(req.body?.email, req.body?.code, req.body?.newPassword)) {
          return sendJson(res, 400, { error: "The reset code is invalid or expired, or the new password is invalid." });
        }
        return sendJson(res, 200, { ok: true, message: "Password updated. Sign in with your new password." });
      } catch {
        return sendJson(res, 503, { error: "Password recovery is temporarily unavailable." });
      }
    });

    app.post("/api/auth/email/attach", auth, async (req, res) => {
      try {
        const result = await attachEmail(req.user?.sub, req.body?.password, req.body?.email);
        if (result.status === 200) return sendJson(res, 200, { ok: true, emailVerificationSent: result.sent });
        if (result.status === 401) return sendJson(res, 401, { error: "Current password is incorrect." });
        if (result.status === 409) return sendJson(res, 409, { error: "That email address is already connected to another FYNX account." });
        return sendJson(res, result.status, { error: "A valid email address and current password are required." });
      } catch (error) {
        if (error?.code === "23505") return sendJson(res, 409, { error: "That email address is already connected to another FYNX account." });
        return sendJson(res, 503, { error: "Email settings are temporarily unavailable." });
      }
    });
  }

  return Object.freeze({
    isValidEmail, sendVerificationForUser, verifyEmail, requestVerification,
    requestPasswordReset, confirmPasswordReset, attachEmail, registerRoutes
  });
}
