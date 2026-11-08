import assert from "node:assert/strict";
import {
  createEmailDelivery,
  EmailDeliveryNotConfiguredError
} from "./emailDelivery.js";

const missing = createEmailDelivery({ env: {}, transportFactory: () => {
  throw new Error("An unconfigured adapter must not create a transport.");
} });
assert.equal(missing.configured, false);
await assert.rejects(
  missing.send({ to: "person@example.com", subject: "Test", text: "Hello" }),
  (error) => error instanceof EmailDeliveryNotConfiguredError &&
    error.code === "FYNX_EMAIL_NOT_CONFIGURED"
);

let transportOptions;
const sent = [];
let closed = false;
const configured = createEmailDelivery({
  env: {
    FYNX_EMAIL_HOST: "smtp.example.test",
    FYNX_EMAIL_PORT: "587",
    FYNX_EMAIL_USER: "fynx@example.test",
    FYNX_EMAIL_PASSWORD: "test-only-secret",
    FYNX_EMAIL_FROM: "FYNX <no-reply@example.test>"
  },
  transportFactory: (options) => {
    transportOptions = options;
    return {
      async sendMail(message) {
        sent.push(message);
        return { accepted: [message.to], messageId: "smtp-test-message-1" };
      },
      close() { closed = true; }
    };
  }
});
assert.equal(configured.configured, true);
assert.equal(transportOptions.host, "smtp.example.test");
assert.equal(transportOptions.port, 587);
assert.equal(transportOptions.secure, false);
assert.equal(transportOptions.requireTLS, true);
assert.equal(transportOptions.tls.rejectUnauthorized, true);

const result = await configured.send({
  to: "Person@example.com",
  subject: "FYNX test",
  text: "Verification code: 123456"
});
assert.deepEqual(result, { messageId: "smtp-test-message-1" });
assert.equal(sent.length, 1);
assert.equal(sent[0].from, "FYNX <no-reply@example.test>");
assert.equal(sent[0].to, "Person@example.com");
configured.close();
assert.equal(closed, true);

await assert.rejects(
  configured.send({ to: "not-an-email", subject: "Test", text: "Hello" }),
  /valid email recipient/
);
await assert.rejects(
  configured.send({ to: "person@example.com", subject: "", text: "Hello" }),
  /valid email subject/
);

const rejected = createEmailDelivery({
  env: {
    FYNX_EMAIL_HOST: "smtp.example.test",
    FYNX_EMAIL_USER: "fynx@example.test",
    FYNX_EMAIL_PASSWORD: "test-only-secret",
    FYNX_EMAIL_FROM: "no-reply@example.test"
  },
  transportFactory: () => ({
    async sendMail() { return { accepted: [], rejected: ["person@example.com"] }; }
  })
});
await assert.rejects(
  rejected.send({ to: "person@example.com", subject: "Test", text: "Hello" }),
  /did not accept/
);

console.log("PASS: FYNX SMTP email delivery adapter configuration, TLS, validation and failure handling");
