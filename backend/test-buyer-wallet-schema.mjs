import crypto from "node:crypto";
import fs from "node:fs";
import pg from "pg";

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

  const balance = await client.query(
    "SELECT COALESCE(SUM(CASE WHEN side='CREDIT' THEN amount ELSE -amount END),0)::numeric(18,2) AS amount FROM buyer_wallet_ledger_entries WHERE wallet_id=$1 AND account_code='AVAILABLE'",
    [walletId]
  );
  if (Number(balance.rows[0].amount) !== 500) throw new Error("Balanced funding entry did not produce the expected available balance.");

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

  console.log("FYNX buyer wallet PostgreSQL schema and ledger tests GREEN (7 scenarios)");
} finally {
  if (schemaCreated) {
    await client.query("SET search_path TO public").catch(() => {});
    await client.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE").catch(() => {});
  }
  client.release();
  await pool.end();
}
