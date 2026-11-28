import { createEmailDelivery } from "./emailDelivery.js";

const supportedEmailTypes = new Set(["MARKETPLACE_ORDER", "MARKETPLACE_PRICE", "WALLET_ACTIVITY", "SAFETY"]);
const emailDelivery = createEmailDelivery();

function cleanText(value, fallback, max = 500) {
  const text = typeof value === "string" ? value.replace(/[\r\n\t]+/g, " ").replace(/\s+/g, " ").trim() : "";
  return (text || fallback).slice(0, max);
}

/**
 * Send only transactional/important activity emails to a verified account address.
 * The existing delivery ledger gives each notification/address one idempotent send slot.
 */
export async function deliverFynxEmailNotification(pool, {
  userId, type, notificationId, title, message
}, delivery = emailDelivery) {
  if (!pool || !userId || !notificationId || !supportedEmailTypes.has(String(type || "").toUpperCase())) {
    return { sent: false, reason: "not an email notification type" };
  }
  if (!delivery?.configured || typeof delivery.send !== "function") {
    return { sent: false, reason: "email delivery is not configured" };
  }

  const kind = String(type).toUpperCase();
  const result = await pool.query(`
    SELECT u.email,u.email_verified_at,
      COALESCE(np.enabled,TRUE) AS enabled,
      COALESCE(np.email_enabled,TRUE) AS email_enabled,
      COALESCE(np.marketplace_enabled,TRUE) AS marketplace_enabled,
      COALESCE(np.marketplace_email_enabled,TRUE) AS marketplace_email_enabled,
      COALESCE(np.wallet_enabled,TRUE) AS wallet_enabled,
      COALESCE(np.wallet_email_enabled,TRUE) AS wallet_email_enabled
    FROM users u
    LEFT JOIN notification_preferences np ON np.user_id=u.id
    WHERE u.id=$1
    LIMIT 1
  `, [userId]);
  const account = result.rows?.[0];
  const address = typeof account?.email === "string" ? account.email.trim().toLowerCase() : "";
  if (!account || !account.email_verified_at || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) {
    return { sent: false, reason: "no verified email address" };
  }
  if (kind !== "SAFETY") {
    const categoryEnabled = kind === "WALLET_ACTIVITY"
      ? Boolean(account.wallet_enabled && account.wallet_email_enabled)
      : Boolean(account.marketplace_enabled && account.marketplace_email_enabled);
    if (!account.enabled || !account.email_enabled || !categoryEnabled) {
      return { sent: false, reason: "email notification preference disabled" };
    }
  }

  const id = String(notificationId).slice(0, 180);
  const claimed = await pool.query(`
    INSERT INTO fynx_notification_delivery
      (notification_id,user_id,provider,token,status,attempts,last_error,updated_at)
    VALUES ($1,$2,'email',$3,'PENDING',1,NULL,NOW())
    ON CONFLICT(notification_id,user_id,provider,token)
    DO UPDATE SET status='PENDING',
                  attempts=fynx_notification_delivery.attempts+1,
                  last_error=NULL,updated_at=NOW()
      WHERE fynx_notification_delivery.status='FAILED'
         OR (fynx_notification_delivery.status='PENDING'
             AND fynx_notification_delivery.updated_at < NOW() - INTERVAL '10 minutes')
    RETURNING status
  `, [id, userId, address]);
  if (!claimed.rows?.length) {
    return { sent: false, reason: "duplicate or already pending" };
  }

  const subject = `FYNX: ${cleanText(title, "Important account activity", 120)}`;
  const body = cleanText(message, "There is new activity on your FYNX account.", 700);
  try {
    await delivery.send({
      to: address,
      subject,
      text: `${body}\n\nOpen FYNX to review this activity. You can manage Marketplace and wallet email alerts in Settings > Notifications.\n\nThis is an automated FYNX activity email, not a marketing message.`
    });
    await pool.query(`
      UPDATE fynx_notification_delivery
      SET status='SENT',last_error=NULL,updated_at=NOW()
      WHERE notification_id=$1 AND user_id=$2 AND provider='email' AND token=$3
    `, [id, userId, address]);
    return { sent: true, reason: "sent" };
  } catch (error) {
    await pool.query(`
      UPDATE fynx_notification_delivery
      SET status='FAILED',last_error=$4,updated_at=NOW()
      WHERE notification_id=$1 AND user_id=$2 AND provider='email' AND token=$3
    `, [id, userId, address, String(error?.message || error || "email delivery failed").slice(0, 180)]).catch(() => {});
    return { sent: false, reason: "email delivery failed" };
  }
}
