import assert from "node:assert/strict";
import { deliverFynxEmailNotification } from "./notificationEmail.js";
import { queueFynxNotification } from "./notificationPush.js";

const account = {
  email: "verified@example.com",
  email_verified_at: new Date("2026-11-20T10:00:00Z"),
  enabled: true,
  email_enabled: true,
  marketplace_enabled: true,
  marketplace_email_enabled: true,
  wallet_enabled: true,
  wallet_email_enabled: true
};
let status = null;
const sent = [];
const sqlCalls = [];
let failOnce = false;
const pool = {
  async query(sql, params = []) {
    sqlCalls.push(sql);
    if (sql.includes("SELECT u.email")) return { rows: [{ ...account }] };
    if (sql.includes("INSERT INTO fynx_notification_delivery")) {
      if (status === null || status === "FAILED") {
        status = "PENDING";
        return { rows: [{ status }] };
      }
      return { rows: [] };
    }
    if (sql.includes("UPDATE fynx_notification_delivery")) {
      status = sql.includes("status='SENT'") ? "SENT" : "FAILED";
      return { rows: [{ status }] };
    }
    throw new Error("Unexpected notification email SQL: " + sql.slice(0, 100));
  }
};
const delivery = {
  configured: true,
  async send(message) {
    if (failOnce) {
      failOnce = false;
      throw new Error("simulated SMTP outage");
    }
    sent.push(message);
  }
};
const event = {
  userId: "25",
  type: "MARKETPLACE_ORDER",
  notificationId: "marketplace-order-25-paid",
  title: "Marketplace payment confirmed",
  message: "Payment has been confirmed for your order."
};

assert.deepEqual(
  await deliverFynxEmailNotification(pool, event, { configured: false }),
  { sent: false, reason: "email delivery is not configured" }
);
assert.equal(sent.length, 0);

account.email_verified_at = null;
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).reason, "no verified email address");
assert.equal(sent.length, 0);
account.email_verified_at = new Date("2026-11-20T10:00:00Z");

account.marketplace_email_enabled = false;
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).reason, "email notification preference disabled");
assert.equal(sent.length, 0);
account.marketplace_email_enabled = true;

failOnce = true;
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).sent, false);
assert.equal(status, "FAILED");
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).sent, true);
assert.equal(status, "SENT");
assert.equal(sent.length, 1);
assert.equal(sent[0].to, "verified@example.com");
assert.match(sent[0].subject, /FYNX: Marketplace payment confirmed/);

// A repeated event with the same deterministic ID cannot send a second email.
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).reason, "duplicate or already pending");
assert.equal(sent.length, 1);

// Existing Marketplace price-watch events use the same verified-email channel.
status = null;
const priceAlert = await deliverFynxEmailNotification(pool, {
  ...event, type: "MARKETPLACE_PRICE", notificationId: "marketplace-price-watch-25",
  title: "Price dropped",
  message: "A watched Marketplace listing has dropped in price."
}, delivery);
assert.equal(priceAlert.sent, true);
assert.equal(sent.length, 2);
assert.equal(sent[1].to, "verified@example.com");
assert.match(sent[1].subject, /Price dropped/);

// Wallet email delivery uses its own opt-in category and respects the master setting.
status = null;
account.wallet_email_enabled = false;
assert.equal((await deliverFynxEmailNotification(pool, {
  ...event, type: "WALLET_ACTIVITY", notificationId: "wallet-funding-25"
}, delivery)).reason, "email notification preference disabled");
account.wallet_email_enabled = true;
account.email_enabled = false;
assert.equal((await deliverFynxEmailNotification(pool, event, delivery)).reason, "email notification preference disabled");

// Essential account protection notices bypass optional activity-email preferences.
status = null;
account.email_enabled = false;
account.enabled = false;
const safetyNotice = await deliverFynxEmailNotification(pool, {
  ...event, type: "SAFETY", notificationId: "safety-account-restricted",
  title: "FYNX Safety — Account restricted",
  message: "Your FYNX account has a new safety status. Review the Safety Center."
}, delivery);
assert.equal(safetyNotice.sent, true);
assert.equal(safetyNotice.reason, "sent");
assert.equal(sent.length, 3);
assert.equal(sent[2].to, "verified@example.com");
assert.match(sent[2].subject, /Account restricted/);

console.log("PASS: verified recipients, optional Marketplace and wallet email controls, essential safety notices, SMTP failure retry, and idempotent delivery");

// Exercise the real notification-to-email bridge used by Marketplace and Wallet event hooks.
// Push is deliberately disabled in this mock: verified-email delivery must still happen.
account.enabled = true;
account.email_enabled = true;
account.marketplace_enabled = true;
account.marketplace_email_enabled = true;
account.wallet_enabled = true;
account.wallet_email_enabled = true;
const integratedSent = [];
const integrationPool = {
  async query(sql) {
    if (sql.includes("CREATE TABLE IF NOT EXISTS notification_preferences")) return { rows: [] };
    if (sql.includes("INSERT INTO fynx_notifications")) return { rows: [] };
    if (sql.includes("SELECT u.email")) return { rows: [{ ...account }] };
    if (sql.includes("INSERT INTO fynx_notification_delivery")) return { rows: [{ status: "PENDING" }] };
    if (sql.includes("UPDATE fynx_notification_delivery")) return { rows: [] };
    if (sql.includes("SELECT enabled,push_enabled")) {
      return { rows: [{ enabled: true, push_enabled: false, category_enabled: true, quiet_mode: false }] };
    }
    throw new Error("Unexpected notification pipeline SQL: " + sql.slice(0, 120));
  }
};
const integrationTransport = {
  configured: true,
  async send(message) { integratedSent.push(message); }
};
for (const integratedEvent of [
  { userId: "25", type: "MARKETPLACE_ORDER", notificationId: "marketplace-paid-buyer-25", title: "Marketplace payment confirmed", message: "Your Marketplace order has been paid." },
  { userId: "25", type: "WALLET_ACTIVITY", notificationId: "wallet-topup-25", title: "Wallet top-up received", message: "Your FYNX Wallet top-up is complete." }
]) {
  const queued = await queueFynxNotification(integrationPool, integratedEvent, integrationTransport);
  assert.equal(queued.stored, true);
  assert.equal(queued.delivered, false);
  assert.equal(queued.emailDelivered, true);
}
assert.equal(integratedSent.length, 2);
assert.ok(integratedSent.every(message => message.to === "verified@example.com"));
assert.match(integratedSent[0].subject, /Marketplace payment confirmed/);
assert.match(integratedSent[1].subject, /Wallet top-up received/);

console.log("PASS: Marketplace and Wallet notification events reach verified email even when push alerts are disabled");

