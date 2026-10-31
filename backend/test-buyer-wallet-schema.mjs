import crypto from "node:crypto";
import fs from "node:fs";
import pg from "pg";
import { closeBuyerWalletPool, parseBuyerWalletActivityCursor, parseBuyerWalletActivityDirection } from "./buyerWallet.js";

const { Pool } = pg;
const pool = new Pool({ max: 1 });
const client = await pool.connect();
const schema = "fynx_wallet_test_" + process.pid + "_" + crypto.randomUUID().replace(/-/g, "");
let schemaCreated = false;

async function insertPendingTransaction(walletId, userId, amount, label) {
  const id = crypto.randomUUID();
  const reference = "FYNX-WALLET-TEST-" + label + "-" + crypto.randomUUID();
  await client.query(
    "INSERT INTO buyer_wallet_transactions (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description) VALUES ($1,$2,$3,'FUNDING','PENDING',$4,0,'NGN',$5,$6,$7)",
    [id, walletId, userId, amount, reference, "IDEM-" + reference, label]
  );
  return id;
}

async function expectTransactionFailure(run, expectedText) {
  await client.query("BEGIN");
  let failure = null;
  try {
    await run();
    await client.query("COMMIT");
  } catch (error) {
    failure = error;
    await client.query("ROLLBACK").catch(() => {});
  }
  if (!failure) throw new Error("Expected wallet transaction failure containing: " + expectedText);
  if (!String(failure.message || "").includes(expectedText)) {
    throw new Error("Unexpected wallet failure. Expected [" + expectedText + "], received [" + failure.message + "]");
  }
}

try {
  if (parseBuyerWalletActivityDirection({}).direction !== "ALL" ||
      parseBuyerWalletActivityDirection({ direction: "in" }).direction !== "IN" ||
      parseBuyerWalletActivityDirection({ direction: "out" }).direction !== "OUT") {
    throw new Error("Wallet activity direction did not normalize correctly.");
  }
  for (const invalidDirection of ["RECENT", ["IN"], null]) {
    if (!parseBuyerWalletActivityDirection({ direction: invalidDirection }).error) {
      throw new Error("Invalid wallet activity direction was accepted.");
    }
  }

  const validCursorId = "123e4567-e89b-42d3-a456-426614174000";
  const validCursor = parseBuyerWalletActivityCursor({
    beforeCreatedAt: "2026-10-31T12:00:00.000Z",
    beforeId: validCursorId
  });
  if (validCursor.error || validCursor.cursor?.id !== validCursorId || validCursor.cursor?.createdAt !== "2026-10-31T12:00:00.000Z") {
    throw new Error("Valid buyer-wallet activity cursor was not normalized correctly.");
  }
  const preciseCursor = parseBuyerWalletActivityCursor({
    beforeCreatedAt: "2026-10-31T12:00:00.123456Z",
    beforeId: validCursorId
  });
  if (preciseCursor.error || preciseCursor.cursor?.createdAt !== "2026-10-31T12:00:00.123456Z") {
    throw new Error("Microsecond wallet activity cursor precision was not preserved.");
  }
  for (const invalidQuery of [
    { beforeCreatedAt: "2026-10-31T12:00:00.000Z" },
    { beforeId: validCursorId },
    { beforeCreatedAt: "not-a-date", beforeId: validCursorId },
    { beforeCreatedAt: "2026-10-31T12:00:00.000Z", beforeId: "not-a-uuid" },
    { beforeCreatedAt: ["2026-10-31T12:00:00.000Z"], beforeId: [validCursorId] }
  ]) {
    if (!parseBuyerWalletActivityCursor(invalidQuery).error) {
      throw new Error("Invalid buyer-wallet activity cursor was accepted.");
    }
  }

  await client.query("CREATE SCHEMA " + schema);
  schemaCreated = true;
  await client.query("SET search_path TO " + schema + ", public");
  await client.query("CREATE TABLE users (id BIGINT PRIMARY KEY)");

  const source = fs.readFileSync(new URL("./buyerWallet.js", import.meta.url), "utf8");
  const schemaMarker = "buyerWalletSchemaPromise = pool.query(" + String.fromCharCode(96);
  const schemaMarkerIndex = source.indexOf(schemaMarker);
  if (schemaMarkerIndex < 0) throw new Error("Could not locate buyer wallet DDL in marketplaceSettlement.js");
  const sqlStart = schemaMarkerIndex + schemaMarker.length;
  const sqlEnd = source.indexOf(String.fromCharCode(96) + ").catch((error)", sqlStart);
  if (sqlEnd <= sqlStart) throw new Error("Could not isolate buyer wallet DDL.");
  await client.query(source.slice(sqlStart, sqlEnd));

  const userId = 777001;
  const walletId = crypto.randomUUID();
  await client.query("INSERT INTO users(id) VALUES ($1)", [userId]);
  await client.query(
    "INSERT INTO buyer_wallets(id,user_id,currency,status,provider) VALUES ($1,$2,'NGN','PENDING_PROVIDER','TEST_PROVIDER')",
    [walletId, userId]
  );

  const cursorSeedIds = [];
  for (const [index, createdAt] of [
    [0, "2026-10-31T12:00:00.123456Z"],
    [1, "2026-10-31T12:00:00.123455Z"],
    [2, "2026-10-31T12:00:00.123454Z"]
  ]) {
    const id = crypto.randomUUID();
    const reference = "FYNX-WALLET-PAGE-" + index + "-" + id;
    await client.query(
      "INSERT INTO buyer_wallet_transactions (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,created_at) VALUES ($1,$2,$3,'FUNDING','PENDING',$4,0,'NGN',$5,$6,$7)",
      [id, walletId, userId, index + 1, reference, "IDEM-" + reference, createdAt]
    );
    cursorSeedIds.push(id);
  }
  const firstCursorPage = await client.query(
    `SELECT id,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS created_at_iso FROM buyer_wallet_transactions WHERE user_id=$1 AND wallet_id=$2 AND id=ANY($3::uuid[]) ORDER BY created_at DESC,id DESC LIMIT 2`,
    [userId, walletId, cursorSeedIds]
  );
  if (firstCursorPage.rows.length !== 2 || firstCursorPage.rows[1].created_at_iso !== "2026-10-31T12:00:00.123455Z") {
    throw new Error("First wallet activity page did not preserve microsecond timestamp order.");
  }
  const dbCursor = parseBuyerWalletActivityCursor({
    beforeCreatedAt: firstCursorPage.rows[1].created_at_iso,
    beforeId: String(firstCursorPage.rows[1].id)
  });
  if (dbCursor.error) throw new Error("Database wallet activity cursor could not be validated.");
  const secondCursorPage = await client.query(
    "SELECT id FROM buyer_wallet_transactions WHERE user_id=$1 AND wallet_id=$2 AND id=ANY($3::uuid[]) AND (created_at,id) < ($4::timestamptz,$5::uuid) ORDER BY created_at DESC,id DESC LIMIT 2",
    [userId, walletId, cursorSeedIds, dbCursor.cursor.createdAt, dbCursor.cursor.id]
  );
  if (secondCursorPage.rows.length !== 1 || String(secondCursorPage.rows[0].id) !== cursorSeedIds[2]) {
    throw new Error("Second wallet activity page skipped or duplicated a microsecond-adjacent transaction.");
  }

  const validTransactionId = await insertPendingTransaction(walletId, userId, 500, "BALANCED");
  await client.query("BEGIN");
  await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [validTransactionId]);
  await client.query(
    "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'AVAILABLE','CREDIT',500,'NGN')",
    [validTransactionId, walletId, "LEG-" + validTransactionId + "-AVAILABLE"]
  );
  await client.query(
    "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'PROVIDER_CLEARING','DEBIT',500,'NGN')",
    [validTransactionId, walletId, "LEG-" + validTransactionId + "-CLEARING"]
  );
  await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [validTransactionId]);
  await client.query("COMMIT");

  const outgoingTransactionId = await insertPendingTransaction(walletId, userId, 100, "OUTGOING");
  await client.query("BEGIN");
  await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [outgoingTransactionId]);
  await client.query(
    "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'AVAILABLE','DEBIT',100,'NGN')",
    [outgoingTransactionId, walletId, "LEG-" + outgoingTransactionId + "-AVAILABLE"]
  );
  await client.query(
    "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'MARKETPLACE_ESCROW','CREDIT',100,'NGN')",
    [outgoingTransactionId, walletId, "LEG-" + outgoingTransactionId + "-ESCROW"]
  );
  await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [outgoingTransactionId]);
  await client.query("COMMIT");

  const directionSql = `
    SELECT t.id,COALESCE(activity_delta.wallet_delta,0)::numeric(18,2) AS wallet_delta
      FROM buyer_wallet_transactions t
      LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END) AS wallet_delta
          FROM buyer_wallet_ledger_entries e
         WHERE e.transaction_id=t.id AND e.account_code='AVAILABLE'
      ) activity_delta ON TRUE
     WHERE t.user_id=$1 AND t.wallet_id=$2
       AND ($3::text='ALL'
         OR ($3::text='IN' AND COALESCE(activity_delta.wallet_delta,0) > 0)
         OR ($3::text='OUT' AND COALESCE(activity_delta.wallet_delta,0) < 0))
     ORDER BY t.created_at DESC,t.id DESC
  `;
  const incomingRows = await client.query(directionSql, [userId, walletId, "IN"]);
  const outgoingRows = await client.query(directionSql, [userId, walletId, "OUT"]);
  if (!incomingRows.rows.some((row) => String(row.id) === validTransactionId) ||
      incomingRows.rows.some((row) => String(row.id) === outgoingTransactionId)) {
    throw new Error("Wallet Money in filter did not return only positive available-ledger movements.");
  }
  if (!outgoingRows.rows.some((row) => String(row.id) === outgoingTransactionId) ||
      outgoingRows.rows.some((row) => String(row.id) === validTransactionId)) {
    throw new Error("Wallet Money out filter did not return only negative available-ledger movements.");
  }

  const balance = await client.query(
    "SELECT COALESCE(SUM(CASE WHEN side='CREDIT' THEN amount ELSE -amount END),0)::numeric(18,2) AS amount FROM buyer_wallet_ledger_entries WHERE wallet_id=$1 AND account_code='AVAILABLE'",
    [walletId]
  );
  if (Number(balance.rows[0].amount) !== 400) throw new Error("Balanced funding entry did not produce the expected available balance.");

  await expectTransactionFailure(async () => {
    const id = await insertPendingTransaction(walletId, userId, 10, "SKIP_PROCESSING");
    await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [id]);
  }, "invalid buyer wallet transaction state transition");

  await expectTransactionFailure(async () => {
    const id = await insertPendingTransaction(walletId, userId, 10, "UNBALANCED");
    await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [id]);
    await client.query(
      "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'AVAILABLE','CREDIT',10,'NGN')",
      [id, walletId, "LEG-" + id + "-ONLY"]
    );
    await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [id]);
  }, "balanced");

  await expectTransactionFailure(async () => {
    const id = await insertPendingTransaction(walletId, userId, 600, "NEGATIVE");
    await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [id]);
    await client.query(
      "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'AVAILABLE','DEBIT',600,'NGN')",
      [id, walletId, "LEG-" + id + "-AVAILABLE"]
    );
    await client.query(
      "INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'PROVIDER_CLEARING','CREDIT',600,'NGN')",
      [id, walletId, "LEG-" + id + "-CLEARING"]
    );
    await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [id]);
  }, "buyer wallet available, reserved or withdrawal balance cannot be negative");

  await expectTransactionFailure(async () => {
    await client.query("UPDATE buyer_wallet_transactions SET description='rewrite' WHERE id=$1", [validTransactionId]);
  }, "terminal buyer wallet transactions are immutable");

  await expectTransactionFailure(async () => {
    await client.query("UPDATE buyer_wallet_ledger_entries SET amount=1 WHERE transaction_id=$1", [validTransactionId]);
  }, "buyer wallet ledger entries are immutable");

  await expectTransactionFailure(async () => {
    const id = crypto.randomUUID();
    await client.query(
      "INSERT INTO buyer_wallet_transactions (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key) VALUES ($1,$2,$3,'FUNDING','PENDING',1,0,'NGN',$4,$5)",
      [id, walletId, userId + 1, "FYNX-OWNER-MISMATCH-" + id, "IDEM-OWNER-MISMATCH-" + id]
    );
  }, "buyer wallet transaction owner or currency does not match its wallet");

  console.log("FYNX buyer wallet PostgreSQL schema, ledger, cursor and direction tests GREEN (8 scenarios)");
} finally {
  if (schemaCreated) {
    await client.query("SET search_path TO public").catch(() => {});
    await client.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE").catch(() => {});
  }
  client.release();
  await pool.end();
  await closeBuyerWalletPool();
}
