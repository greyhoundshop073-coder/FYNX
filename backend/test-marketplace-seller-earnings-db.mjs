import assert from "node:assert/strict";
import crypto from "node:crypto";
import { readFile } from "node:fs/promises";
import pg from "pg";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");
const source = await readFile(new URL("./marketplaceSettlement.js", import.meta.url), "utf8");
const marker = source.indexOf("const totalsResult = await pool.query(");
const tick = String.fromCharCode(96);
const sqlStart = source.indexOf(tick, marker) + 1;
const sqlEnd = source.indexOf(tick + ", [req.user.sub]);", sqlStart);
assert.ok(marker >= 0 && sqlStart > marker && sqlEnd > sqlStart, "seller settlement totals SQL extraction failed");
const sql = source.slice(sqlStart, sqlEnd);
assert.ok(sql.includes("failed_unreconciled"), "seller totals SQL must classify failed payout records");
assert.ok(sql.includes("WHERE o.seller_id = $1"), "seller totals SQL must isolate the requested seller");

const db = new Client();
const schema = "fynx_seller_earnings_" + process.pid + "_" + Date.now();
await db.connect();
try {
  await db.query("CREATE SCHEMA " + schema);
  await db.query("SET search_path TO " + schema);
  await db.query("CREATE TABLE marketplace_orders (id UUID PRIMARY KEY, seller_id BIGINT NOT NULL, product_subtotal NUMERIC(14,2) NOT NULL, delivery_fee NUMERIC(14,2) NOT NULL, seller_net_amount NUMERIC(14,2) NOT NULL, total_amount NUMERIC(14,2) NOT NULL, marketplace_fee_seller NUMERIC(14,2) NOT NULL, currency TEXT NOT NULL, status TEXT NOT NULL, updated_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL)");
  await db.query("CREATE TABLE marketplace_escrows (id UUID PRIMARY KEY, order_id UUID NOT NULL, status TEXT NOT NULL, updated_at TIMESTAMPTZ NOT NULL)");
  await db.query("CREATE TABLE marketplace_financial_operations (id UUID PRIMARY KEY, order_id UUID NOT NULL, operation_type TEXT NOT NULL, status TEXT NOT NULL, updated_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL)");

  const sellerId = 1101;
  const otherSellerId = 2202;
  const insertOrder = async (owner, amounts) => {
    const id = crypto.randomUUID();
    const now = new Date();
    await db.query(
      "INSERT INTO marketplace_orders (id,seller_id,product_subtotal,delivery_fee,seller_net_amount,total_amount,marketplace_fee_seller,currency,status,updated_at,created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$10)",
      [id, owner, amounts.productSubtotal, amounts.deliveryFee, amounts.sellerNet, amounts.sellerNet, amounts.fee, amounts.currency, amounts.status || "COMPLETED", now]
    );
    return id;
  };
  const insertEscrow = async (orderId, status) => {
    await db.query("INSERT INTO marketplace_escrows (id,order_id,status,updated_at) VALUES ($1,$2,$3,NOW())", [crypto.randomUUID(), orderId, status]);
  };
  const insertPayout = async (orderId, status, updatedAt = new Date()) => {
    await db.query(
      "INSERT INTO marketplace_financial_operations (id,order_id,operation_type,status,updated_at,created_at) VALUES ($1,$2,'PAYOUT_RELEASE',$3,$4,$4)",
      [crypto.randomUUID(), orderId, status, updatedAt]
    );
  };

  const legacy = await insertOrder(sellerId, { productSubtotal:100, deliveryFee:10, sellerNet:105, fee:5, currency:"NGN" });
  const failed = await insertOrder(sellerId, { productSubtotal:200, deliveryFee:20, sellerNet:210, fee:10, currency:"NGN" });
  const blocked = await insertOrder(sellerId, { productSubtotal:300, deliveryFee:30, sellerNet:315, fee:15, currency:"NGN" });
  const pending = await insertOrder(sellerId, { productSubtotal:400, deliveryFee:40, sellerNet:420, fee:20, currency:"NGN" });
  const succeeded = await insertOrder(sellerId, { productSubtotal:500, deliveryFee:50, sellerNet:525, fee:25, currency:"NGN" });
  const available = await insertOrder(sellerId, { productSubtotal:600, deliveryFee:60, sellerNet:630, fee:30, currency:"NGN" });
  const protectedOrder = await insertOrder(sellerId, { productSubtotal:700, deliveryFee:70, sellerNet:735, fee:35, currency:"NGN", status:"PAID" });
  const released = await insertOrder(sellerId, { productSubtotal:800, deliveryFee:80, sellerNet:840, fee:40, currency:"NGN" });
  await insertOrder(sellerId, { productSubtotal:25, deliveryFee:5, sellerNet:23, fee:2, currency:"USD" });
  await insertOrder(otherSellerId, { productSubtotal:9000, deliveryFee:900, sellerNet:9000, fee:900, currency:"NGN" });

  await insertPayout(failed, "SUCCEEDED", new Date(Date.now() - 60_000));
  await insertPayout(failed, "FAILED", new Date(Date.now() + 60_000));
  await insertPayout(blocked, "BLOCKED");
  await insertPayout(pending, "PENDING");
  await insertPayout(succeeded, "SUCCEEDED");
  await insertEscrow(available, "RELEASE_ELIGIBLE");
  await insertEscrow(protectedOrder, "HELD");
  await insertEscrow(released, "RELEASED");

  const result = await db.query(sql, [sellerId]);
  const totals = new Map(result.rows.map(row => [String(row.currency).toUpperCase(), row]));
  assert.deepEqual([...totals.keys()].sort(), ["NGN", "USD"], "seller totals must remain separated by currency");

  const ngn = totals.get("NGN");
  assert.equal(Number(ngn.gross_sales), 3600, "all reconciled and unresolved completed NGN sales must be included");
  assert.equal(Number(ngn.delivery_fees), 360, "delivery fees must include unresolved completed orders");
  assert.equal(Number(ngn.marketplace_fees), 180, "seller commission must include unresolved completed orders");
  assert.equal(Number(ngn.net_earnings), 3780, "net earnings must include unresolved completed orders");
  assert.equal(Number(ngn.protected_funds), 735, "only held escrow is protected");
  assert.equal(Number(ngn.available_for_payout), 630, "only release-eligible escrow is available");
  assert.equal(Number(ngn.payout_pending), 420, "pending payout operation is pending");
  assert.equal(Number(ngn.paid_out), 1365, "only successful/released payouts are paid");
  assert.equal(Number(ngn.unreconciled_legacy), 105, "legacy completed order without any payout record is separately unresolved");
  assert.equal(Number(ngn.unreconciled_failed_payouts), 525, "failed and blocked payout amounts are separately unresolved");

  const usd = totals.get("USD");
  assert.equal(Number(usd.gross_sales), 25, "USD earnings must not be mixed into NGN totals");
  assert.equal(Number(usd.net_earnings), 23, "USD seller net must remain separately reported");
  assert.equal(Number(usd.unreconciled_legacy), 23, "USD legacy amount must remain in its own currency bucket");

  assert.equal(Number(ngn.protected_funds) + Number(ngn.available_for_payout) + Number(ngn.payout_pending) + Number(ngn.paid_out), 3150,
    "unreconciled legacy and failed payout amounts must not be represented as protected, available, pending or paid");
  assert.equal(Number(ngn.unreconciled_legacy) + Number(ngn.unreconciled_failed_payouts), 630,
    "unresolved amounts must remain visible as separate reconciliation buckets");

  console.log("PostgreSQL seller earnings integration tests GREEN (legacy, failed/blocked, pending, succeeded, escrow states, currency separation and seller isolation).");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE").catch(() => {});
  await db.end();
}
