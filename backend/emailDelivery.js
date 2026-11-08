import nodemailer from "nodemailer";

export class EmailDeliveryNotConfiguredError extends Error {
  constructor() {
    super("FYNX email delivery is not configured. Set the SMTP environment variables before enabling verification or password recovery.");
    this.name = "EmailDeliveryNotConfiguredError";
    this.code = "FYNX_EMAIL_NOT_CONFIGURED";
  }
}

function asBoolean(value, fallback) {
  if (value == null || String(value).trim() === "") return fallback;
  return ["1", "true", "yes", "on"].includes(String(value).trim().toLowerCase());
}

function isEmailAddress(value) {
  return typeof value === "string" &&
    value.length <= 254 &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

/**
 * Creates a fail-closed SMTP adapter.
 *
 * Required deployment variables:
 * FYNX_EMAIL_HOST, FYNX_EMAIL_USER, FYNX_EMAIL_PASSWORD, FYNX_EMAIL_FROM.
 * FYNX_EMAIL_PORT defaults to 587; FYNX_EMAIL_SECURE defaults to true only
 * for port 465; FYNX_EMAIL_REQUIRE_TLS defaults to true for non-secure SMTP.
 *
 * A transport factory is injected by tests; production uses Nodemailer.
 */
export function createEmailDelivery({
  env = process.env,
  transportFactory = (options) => nodemailer.createTransport(options)
} = {}) {
  const host = String(env.FYNX_EMAIL_HOST || "").trim();
  const port = Number(env.FYNX_EMAIL_PORT || 587);
  const username = String(env.FYNX_EMAIL_USER || "").trim();
  const password = String(env.FYNX_EMAIL_PASSWORD || "");
  const from = String(env.FYNX_EMAIL_FROM || "").trim();
  const secure = asBoolean(env.FYNX_EMAIL_SECURE, port === 465);
  const requireTLS = asBoolean(env.FYNX_EMAIL_REQUIRE_TLS, !secure);

  const configured = Boolean(
    host &&
    Number.isInteger(port) &&
    port >= 1 &&
    port <= 65535 &&
    username &&
    password &&
    isEmailAddress(from)
  );

  const transport = configured
    ? transportFactory({
        host,
        port,
        secure,
        requireTLS,
        auth: { user: username, pass: password },
        connectionTimeout: 10_000,
        greetingTimeout: 10_000,
        socketTimeout: 20_000,
        tls: { rejectUnauthorized: true }
      })
    : null;

  return Object.freeze({
    configured,
    async send({ to, subject, text, html }) {
      if (!configured || !transport) throw new EmailDeliveryNotConfiguredError();
      const recipient = typeof to === "string" ? to.trim() : "";
      const title = typeof subject === "string" ? subject.trim() : "";
      if (!isEmailAddress(recipient)) throw new TypeError("A valid email recipient is required.");
      if (!title || title.length > 180) throw new TypeError("A valid email subject is required.");
      if ((!text || !String(text).trim()) && (!html || !String(html).trim())) {
        throw new TypeError("Email content is required.");
      }

      const info = await transport.sendMail({
        from,
        to: recipient,
        subject: title,
        text: text == null ? undefined : String(text),
        html: html == null ? undefined : String(html)
      });
      const accepted = Array.isArray(info?.accepted) ? info.accepted : [];
      if (!accepted.some((address) => String(address).trim().toLowerCase() === recipient.toLowerCase())) {
        throw new Error("The SMTP provider did not accept the requested recipient.");
      }
      return { messageId: String(info.messageId || "") };
    },
    close() {
      transport?.close?.();
    }
  });
}
