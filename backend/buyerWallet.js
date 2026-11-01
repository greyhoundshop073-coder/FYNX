import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 4,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

function auth(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
  try {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('invalid token');
    const [encodedHeader, encodedPayload, encodedSignature] = parts;
    const signature = crypto.createHmac('sha256', JWT_SECRET).update(`${encodedHeader}.${encodedPayload}`).digest('base64url');
    if (signature.length !== encodedSignature.length || !crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(encodedSignature))) throw new Error('invalid token');
    const payload = JSON.parse(Buffer.from(encodedPayload, 'base64url').toString('utf8'));
    if (!payload?.sub || payload.exp && Number(payload.exp) <= Math.floor(Date.now() / 1000)) throw new Error('expired token');
    req.user = payload;
    return next();
  } catch {
    return res.status(401).json({ error: 'invalid or expired token' });
  }
}

let buyerWalletSchemaPromise;
async function ensureBuyerWalletSchema() {
  if (!pool) throw Object.assign(new Error('DATABASE_URL is not configured'), { code: 'DATABASE_NOT_CONFIGURED' });
  if (!buyerWalletSchemaPromise) {
    buyerWalletSchemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS buyer_wallets (
        id UUID PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        currency TEXT NOT NULL DEFAULT 'NGN' CHECK (currency = 'NGN'),
        status TEXT NOT NULL DEFAULT 'PENDING_PROVIDER'
          CHECK (status IN ('PENDING_PROVIDER','ACTIVE','RESTRICTED','CLOSED')),
        provider TEXT NOT NULL DEFAULT 'PENDING_SELECTION',
        provider_wallet_reference TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE (user_id, currency)
      );
      CREATE UNIQUE INDEX IF NOT EXISTS buyer_wallet_provider_wallet_reference_uidx
        ON buyer_wallets(provider, provider_wallet_reference)
        WHERE provider_wallet_reference IS NOT NULL;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS provider_customer_code TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_id TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_bank_name TEXT NOT NULL DEFAULT '';
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_name TEXT NOT NULL DEFAULT '';
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_ciphertext TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_iv TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_tag TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS funding_account_status TEXT NOT NULL DEFAULT 'NOT_CREATED'
        CHECK (funding_account_status IN ('NOT_CREATED','REQUESTING','ASSIGNING','ACTIVE','FAILED','RESTRICTED'));
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS provider_consent_at TIMESTAMPTZ;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS provider_consent_version TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS withdrawal_pin_hash TEXT;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS withdrawal_pin_set_at TIMESTAMPTZ;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS withdrawal_pin_failed_attempts INTEGER NOT NULL DEFAULT 0;
      ALTER TABLE buyer_wallets ADD COLUMN IF NOT EXISTS withdrawal_pin_locked_until TIMESTAMPTZ;
      CREATE UNIQUE INDEX IF NOT EXISTS buyer_wallet_provider_customer_code_uidx
        ON buyer_wallets(provider_customer_code) WHERE provider_customer_code IS NOT NULL;


      CREATE TABLE IF NOT EXISTS buyer_wallet_transactions (
        id UUID PRIMARY KEY,
        wallet_id UUID NOT NULL REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        transaction_type TEXT NOT NULL
          CHECK (transaction_type IN ('FUNDING','MARKETPLACE_PAYMENT','WITHDRAWAL','REFUND','REVERSAL','ADJUSTMENT')),
        status TEXT NOT NULL
          CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','REVERSED','BLOCKED')),
        amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
        fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (fee >= 0),
        currency TEXT NOT NULL CHECK (currency = 'NGN'),
        reference TEXT NOT NULL UNIQUE,
        idempotency_key TEXT NOT NULL UNIQUE,
        provider_reference TEXT,
        description TEXT NOT NULL DEFAULT '',
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE UNIQUE INDEX IF NOT EXISTS buyer_wallet_provider_reference_uidx
        ON buyer_wallet_transactions (provider_reference)
        WHERE provider_reference IS NOT NULL;
      CREATE INDEX IF NOT EXISTS buyer_wallet_transactions_owner_idx
        ON buyer_wallet_transactions (user_id, created_at DESC, id DESC);
      CREATE INDEX IF NOT EXISTS buyer_wallet_transactions_wallet_idx
        ON buyer_wallet_transactions (wallet_id, created_at DESC, id DESC);

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_require_pending_insert()
      RETURNS trigger AS $fynxwallet$
      BEGIN
        IF NEW.status <> 'PENDING' THEN
          RAISE EXCEPTION 'buyer wallet transactions must begin in pending state'
            USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_transaction_insert_guard ON buyer_wallet_transactions;
      CREATE TRIGGER buyer_wallet_transaction_insert_guard
        BEFORE INSERT ON buyer_wallet_transactions
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_require_pending_insert();

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_validate_transaction_owner()
      RETURNS trigger AS $fynxwallet$
      BEGIN
        IF NOT EXISTS (
          SELECT 1 FROM buyer_wallets
           WHERE id = NEW.wallet_id
             AND user_id = NEW.user_id
             AND currency = NEW.currency
        ) THEN
          RAISE EXCEPTION 'buyer wallet transaction owner or currency does not match its wallet'
            USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_transaction_owner_guard ON buyer_wallet_transactions;
      CREATE TRIGGER buyer_wallet_transaction_owner_guard
        BEFORE INSERT OR UPDATE ON buyer_wallet_transactions
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_validate_transaction_owner();

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_guard_transaction_update()
      RETURNS trigger AS $fynxwallet$
      BEGIN
        IF OLD.status IN ('SUCCEEDED','FAILED','REVERSED','BLOCKED') AND NEW IS DISTINCT FROM OLD THEN
          RAISE EXCEPTION 'terminal buyer wallet transactions are immutable; record a new reversal or retry'
            USING ERRCODE = '55000';
        END IF;
        IF OLD.status = 'PENDING'
           AND NEW.status NOT IN ('PENDING','PROCESSING','FAILED','BLOCKED') THEN
          RAISE EXCEPTION 'invalid buyer wallet transaction state transition'
            USING ERRCODE = '23514';
        END IF;
        IF OLD.status = 'PROCESSING'
           AND NEW.status NOT IN ('PROCESSING','SUCCEEDED','FAILED','BLOCKED') THEN
          RAISE EXCEPTION 'invalid buyer wallet transaction state transition'
            USING ERRCODE = '23514';
        END IF;
        IF OLD.status <> 'PENDING'
           AND (NEW.wallet_id IS DISTINCT FROM OLD.wallet_id
             OR NEW.user_id IS DISTINCT FROM OLD.user_id
             OR NEW.transaction_type IS DISTINCT FROM OLD.transaction_type
             OR NEW.amount IS DISTINCT FROM OLD.amount
             OR NEW.fee IS DISTINCT FROM OLD.fee
             OR NEW.currency IS DISTINCT FROM OLD.currency
             OR NEW.reference IS DISTINCT FROM OLD.reference
             OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key) THEN
          RAISE EXCEPTION 'buyer wallet transaction financial identity cannot change after processing starts'
            USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_transaction_update_guard ON buyer_wallet_transactions;
      CREATE TRIGGER buyer_wallet_transaction_update_guard
        BEFORE UPDATE ON buyer_wallet_transactions
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_guard_transaction_update();

      CREATE TABLE IF NOT EXISTS buyer_wallet_ledger_entries (
        id BIGSERIAL PRIMARY KEY,
        transaction_id UUID NOT NULL REFERENCES buyer_wallet_transactions(id) ON DELETE RESTRICT,
        wallet_id UUID NOT NULL REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        entry_key TEXT NOT NULL UNIQUE,
        account_code TEXT NOT NULL
          CHECK (account_code IN ('AVAILABLE','RESERVED','WITHDRAWAL_PENDING','PROVIDER_CLEARING','MARKETPLACE_ESCROW','EXTERNAL_BANK','FYNX_FEES')),
        side TEXT NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
        amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL CHECK (currency = 'NGN'),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS buyer_wallet_ledger_wallet_idx
        ON buyer_wallet_ledger_entries (wallet_id, account_code, created_at DESC);
      CREATE INDEX IF NOT EXISTS buyer_wallet_ledger_transaction_idx
        ON buyer_wallet_ledger_entries (transaction_id);

      CREATE TABLE IF NOT EXISTS buyer_wallet_provider_operations (
        id UUID PRIMARY KEY,
        wallet_id UUID NOT NULL REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        transaction_id UUID NOT NULL UNIQUE REFERENCES buyer_wallet_transactions(id) ON DELETE RESTRICT,
        operation_type TEXT NOT NULL CHECK (operation_type IN ('WITHDRAWAL')),
        idempotency_key TEXT NOT NULL UNIQUE,
        provider_reference TEXT NOT NULL UNIQUE,
        provider_transfer_code TEXT UNIQUE,
        recipient_code TEXT NOT NULL,
        status TEXT NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REVERSED','BLOCKED')),
        amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL CHECK (currency = 'NGN'),
        bank_name TEXT NOT NULL DEFAULT '',
        account_name TEXT NOT NULL DEFAULT '',
        account_last4 TEXT NOT NULL DEFAULT '',
        failure_reason TEXT,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS buyer_wallet_provider_operations_owner_idx
        ON buyer_wallet_provider_operations(user_id,created_at DESC);
      CREATE INDEX IF NOT EXISTS buyer_wallet_provider_operations_status_idx
        ON buyer_wallet_provider_operations(status,updated_at DESC);


      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_guard_ledger_insert()
      RETURNS trigger AS $fynxwallet$
      DECLARE
        transaction_status TEXT;
        transaction_wallet UUID;
        transaction_currency TEXT;
      BEGIN
        SELECT status,wallet_id,currency
          INTO transaction_status,transaction_wallet,transaction_currency
          FROM buyer_wallet_transactions
         WHERE id=NEW.transaction_id;
        IF transaction_status IS DISTINCT FROM 'PROCESSING' THEN
          RAISE EXCEPTION 'wallet ledger entries can only be posted while the transaction is processing'
            USING ERRCODE = '23514';
        END IF;
        IF transaction_wallet IS DISTINCT FROM NEW.wallet_id
           OR transaction_currency IS DISTINCT FROM NEW.currency THEN
          RAISE EXCEPTION 'wallet ledger entry does not match transaction wallet or currency'
            USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_ledger_insert_guard ON buyer_wallet_ledger_entries;
      CREATE TRIGGER buyer_wallet_ledger_insert_guard
        BEFORE INSERT ON buyer_wallet_ledger_entries
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_guard_ledger_insert();

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_reject_ledger_mutation()
      RETURNS trigger AS $fynxwallet$
      BEGIN
        RAISE EXCEPTION 'buyer wallet ledger entries are immutable; create a reversal transaction'
          USING ERRCODE = '55000';
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_ledger_immutable ON buyer_wallet_ledger_entries;
      CREATE TRIGGER buyer_wallet_ledger_immutable
        BEFORE UPDATE OR DELETE ON buyer_wallet_ledger_entries
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_reject_ledger_mutation();

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_assert_balanced()
      RETURNS trigger AS $fynxwallet$
      DECLARE
        debit_total NUMERIC(18,2);
        credit_total NUMERIC(18,2);
        transaction_status TEXT;
      BEGIN
        SELECT status INTO transaction_status
          FROM buyer_wallet_transactions
          WHERE id = NEW.transaction_id;
        IF transaction_status IS DISTINCT FROM 'SUCCEEDED' THEN
          RAISE EXCEPTION 'only succeeded wallet transactions may post ledger entries'
            USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
          SELECT 1
            FROM buyer_wallet_ledger_entries e
            JOIN buyer_wallet_transactions t ON t.id = e.transaction_id
           WHERE e.transaction_id = NEW.transaction_id
             AND (e.wallet_id <> t.wallet_id OR e.currency <> t.currency)
        ) THEN
          RAISE EXCEPTION 'wallet ledger entry does not match transaction wallet or currency'
            USING ERRCODE = '23514';
        END IF;
        SELECT
          COALESCE(SUM(CASE WHEN side = 'DEBIT' THEN amount ELSE 0 END), 0),
          COALESCE(SUM(CASE WHEN side = 'CREDIT' THEN amount ELSE 0 END), 0)
          INTO debit_total, credit_total
          FROM buyer_wallet_ledger_entries
         WHERE transaction_id = NEW.transaction_id;
        IF debit_total <= 0 OR debit_total <> credit_total THEN
          RAISE EXCEPTION 'buyer wallet transaction is not double-entry balanced'
            USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
          SELECT 1
            FROM buyer_wallet_ledger_entries e
            JOIN buyer_wallet_transactions t ON t.id = e.transaction_id
           WHERE e.wallet_id = NEW.wallet_id
             AND t.status = 'SUCCEEDED'
             AND e.account_code IN ('AVAILABLE','RESERVED','WITHDRAWAL_PENDING')
           GROUP BY e.account_code
          HAVING SUM(CASE WHEN e.side = 'CREDIT' THEN e.amount ELSE -e.amount END) < 0
        ) THEN
          RAISE EXCEPTION 'buyer wallet available, reserved or withdrawal balance cannot be negative'
            USING ERRCODE = '23514';
        END IF;
        RETURN NULL;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_ledger_balanced ON buyer_wallet_ledger_entries;
      CREATE CONSTRAINT TRIGGER buyer_wallet_ledger_balanced
        AFTER INSERT ON buyer_wallet_ledger_entries
        DEFERRABLE INITIALLY DEFERRED
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_assert_balanced();

      CREATE OR REPLACE FUNCTION fynx_buyer_wallet_assert_transaction_posting()
      RETURNS trigger AS $fynxwallet$
      DECLARE
        debit_total NUMERIC(18,2);
        credit_total NUMERIC(18,2);
      BEGIN
        IF NEW.status <> 'SUCCEEDED' THEN RETURN NULL; END IF;
        SELECT
          COALESCE(SUM(CASE WHEN side='DEBIT' THEN amount ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN side='CREDIT' THEN amount ELSE 0 END),0)
          INTO debit_total,credit_total
          FROM buyer_wallet_ledger_entries
         WHERE transaction_id=NEW.id;
        IF debit_total <= 0 OR debit_total <> credit_total THEN
          RAISE EXCEPTION 'successful buyer wallet transaction must have balanced ledger entries'
            USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
          SELECT 1 FROM buyer_wallet_ledger_entries
           WHERE transaction_id=NEW.id
             AND (wallet_id <> NEW.wallet_id OR currency <> NEW.currency)
        ) THEN
          RAISE EXCEPTION 'successful buyer wallet transaction has mismatched ledger entries'
            USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
          SELECT 1
            FROM buyer_wallet_ledger_entries e
            JOIN buyer_wallet_transactions t ON t.id=e.transaction_id
           WHERE e.wallet_id=NEW.wallet_id
             AND t.status='SUCCEEDED'
             AND e.account_code IN ('AVAILABLE','RESERVED','WITHDRAWAL_PENDING')
           GROUP BY e.account_code
          HAVING SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END) < 0
        ) THEN
          RAISE EXCEPTION 'buyer wallet available, reserved or withdrawal balance cannot be negative'
            USING ERRCODE = '23514';
        END IF;
        RETURN NULL;
      END;
      $fynxwallet$ LANGUAGE plpgsql;
      DROP TRIGGER IF EXISTS buyer_wallet_transaction_success_balanced ON buyer_wallet_transactions;
      CREATE CONSTRAINT TRIGGER buyer_wallet_transaction_success_balanced
        AFTER UPDATE OF status ON buyer_wallet_transactions
        DEFERRABLE INITIALLY DEFERRED
        FOR EACH ROW EXECUTE FUNCTION fynx_buyer_wallet_assert_transaction_posting();
    `).catch((error) => {
      buyerWalletSchemaPromise = undefined;
      throw error;
    });
  }
  return buyerWalletSchemaPromise;
}


function walletWithdrawalLimits() {
  const maximum = Number(process.env.FYNX_WALLET_WITHDRAWAL_MAX_AMOUNT || '');
  const daily = Number(process.env.FYNX_WALLET_WITHDRAWAL_DAILY_LIMIT || '');
  const count = Number.parseInt(process.env.FYNX_WALLET_WITHDRAWAL_DAILY_COUNT_LIMIT || '', 10);
  if (!Number.isFinite(maximum) || maximum <= 0 ||
      !Number.isFinite(daily) || daily <= 0 ||
      !Number.isInteger(count) || count <= 0 || daily < maximum) return null;
  return { maximumPerTransfer: maximum, dailyAmountLimit: daily, dailyCountLimit: count };
}

function walletProviderApproved(action) {
  const common = process.env.FYNX_WALLET_PROVIDER_APPROVED === 'true' &&
    process.env.FYNX_WALLET_CUSTODY_APPROVED === 'true' &&
    process.env.FYNX_WALLET_COMPLIANCE_APPROVED === 'true' &&
    Boolean(process.env.PAYSTACK_SECRET_KEY);
  if (!common) return false;
  if (action === 'DEPOSIT') return process.env.FYNX_WALLET_DEPOSITS_ENABLED === 'true' &&
    Boolean(process.env.FYNX_PAYSTACK_WALLET_BANK) && Boolean(walletEncryptionKey());
  if (action === 'WITHDRAWAL') return process.env.FYNX_WALLET_WITHDRAWALS_ENABLED === 'true' &&
    process.env.FYNX_WALLET_PAYOUTS_APPROVED === 'true' &&
    process.env.FYNX_WALLET_TRANSFER_OTP_DISABLED === 'true' &&
    walletWithdrawalLimits() !== null;
  return false;
}

function walletEncryptionKey() {
  const raw = String(process.env.FYNX_WALLET_ACCOUNT_ENCRYPTION_KEY || '').trim();
  if (/^[0-9a-f]{64}$/i.test(raw)) return Buffer.from(raw, 'hex');
  const decoded = Buffer.from(raw, 'base64');
  return decoded.length === 32 ? decoded : null;
}

function encryptWalletAccountNumber(accountNumber) {
  const key = walletEncryptionKey();
  if (!key) throw Object.assign(new Error('wallet account encryption is not configured'), { code: 'WALLET_ENCRYPTION_NOT_CONFIGURED' });
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(accountNumber, 'utf8'), cipher.final()]);
  return { ciphertext: ciphertext.toString('base64'), iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64') };
}

function decryptWalletAccountNumber(wallet) {
  const key = walletEncryptionKey();
  if (!key || !wallet?.funding_account_ciphertext || !wallet?.funding_account_iv || !wallet?.funding_account_tag) return null;
  try {
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(wallet.funding_account_iv, 'base64'));
    decipher.setAuthTag(Buffer.from(wallet.funding_account_tag, 'base64'));
    return Buffer.concat([
      decipher.update(Buffer.from(wallet.funding_account_ciphertext, 'base64')),
      decipher.final()
    ]).toString('utf8');
  } catch {
    return null;
  }
}

async function walletPaystackRequest(path, { method = 'GET', body } = {}) {
  const secret = String(process.env.PAYSTACK_SECRET_KEY || '').trim();
  if (!secret) throw Object.assign(new Error('wallet payment provider is not configured'), { code: 'PAYSTACK_NOT_CONFIGURED' });
  const response = await fetch('https://api.paystack.co' + path, {
    method,
    headers: {
      Authorization: 'Bearer ' + secret,
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {})
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(15_000)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.status !== true) {
    const error = new Error(String(payload?.message || 'payment provider request failed').slice(0, 240));
    error.code = 'PAYSTACK_REQUEST_FAILED';
    error.status = response.status;
    throw error;
  }
  return payload.data || {};
}

function walletCsvCell(value) {
  let text = String(value ?? '');
  if (/^[=+@\-\t\r]/.test(text)) text = "'" + text;
  return '"' + text.replace(/"/g, '""') + '"';
}

export function buildBuyerWalletStatementCsv(rows) {
  const header = ['Date','Reference','Type','Status','Description','Amount','Fee','Wallet change','Currency'];
  const output = [header.map(walletCsvCell).join(',')];
  for (const row of Array.isArray(rows) ? rows : []) {
    output.push([
      walletCsvCell(row.created_at_iso || row.created_at),
      walletCsvCell(row.reference),
      walletCsvCell(row.transaction_type),
      walletCsvCell(row.status),
      walletCsvCell(row.description),
      Number(row.amount || 0).toFixed(2),
      Number(row.fee || 0).toFixed(2),
      Number(row.wallet_delta || 0).toFixed(2),
      walletCsvCell(row.currency)
    ].join(','));
  }
  return '\uFEFF' + output.join('\r\n') + '\r\n';
}

function parseStatementDate(value, fallback) {
  if (value == null || value === '') return fallback;
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const parsed = new Date(value + 'T00:00:00.000Z');
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) return null;
  return value;
}

async function persistWalletFundingAccount(walletId, data) {
  const accountNumber = String(data?.account_number || '').replace(/\D/g, '');
  const accountName = String(data?.account_name || '').trim().slice(0, 160);
  const bankName = String(data?.bank?.name || data?.bank_name || '').trim().slice(0, 120);
  const currency = String(data?.currency || '').trim().toUpperCase();
  if (!/^\d{10}$/.test(accountNumber) || !accountName || !bankName || currency !== 'NGN' || data?.active === false || data?.assigned === false) {
    throw Object.assign(new Error('provider returned incomplete or inactive wallet account details'), { code: 'WALLET_ACCOUNT_INVALID' });
  }
  const encrypted = encryptWalletAccountNumber(accountNumber);
  await pool.query(`
    UPDATE buyer_wallets
       SET funding_account_id=$2,
           funding_bank_name=$3,
           funding_account_name=$4,
           funding_account_ciphertext=$5,
           funding_account_iv=$6,
           funding_account_tag=$7,
           funding_account_status='ACTIVE',
           status='ACTIVE',
           provider='paystack',
           updated_at=NOW()
     WHERE id=$1
  `, [walletId, String(data.id || ''), bankName, accountName, encrypted.ciphertext, encrypted.iv, encrypted.tag]);
}

async function postWalletInternalLedgerTransaction(client, {
  walletId, userId, amount, reference, description, debitAccount, creditAccount, metadata
}) {
  const transactionId = crypto.randomUUID();
  await client.query(`
    INSERT INTO buyer_wallet_transactions
      (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata)
    VALUES ($1,$2,$3,'ADJUSTMENT','PENDING',$4,0,'NGN',$5,$6,$7,$8::jsonb)
  `, [
    transactionId, walletId, userId, amount, reference,
    'INTERNAL-' + reference, description,
    JSON.stringify({ ...(metadata || {}), internal: true })
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
  await client.query(`
    INSERT INTO buyer_wallet_ledger_entries
      (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
    VALUES
      ($1,$2,$3,$4,'DEBIT',$5,'NGN'),
      ($1,$2,$6,$7,'CREDIT',$5,'NGN')
  `, [
    transactionId, walletId, reference + '-DEBIT', debitAccount,
    amount, reference + '-CREDIT', creditAccount
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
  return transactionId;
}

async function reconcileWalletTransferEvent(client, eventName, transfer) {
  const reference = typeof transfer?.reference === 'string' ? transfer.reference.trim() : '';
  if (!reference) return { matched: false };
  const operation = (await client.query(`
    SELECT * FROM buyer_wallet_provider_operations
     WHERE provider_reference=$1
     FOR UPDATE
  `, [reference])).rows[0];
  if (!operation) return { matched: false };
  const providerAmount = Number(transfer.amount);
  const expectedAmount = Math.round(Number(operation.amount) * 100);
  const providerCurrency = String(transfer.currency || '').trim().toUpperCase();
  if (!Number.isFinite(providerAmount) || providerAmount !== expectedAmount || providerCurrency !== 'NGN') {
    await client.query(`
      UPDATE buyer_wallet_provider_operations
         SET status='BLOCKED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW()
       WHERE id=$3 AND status='PENDING'
    `, [
      'Paystack transfer amount or currency did not match the withdrawal request',
      JSON.stringify({ ...(operation.metadata || {}), transferEvent: eventName, providerAmount, expectedAmount, providerCurrency }),
      operation.id
    ]);
    return { matched: true, blocked: true };
  }

  const currentStatus = String(operation.status || '').toUpperCase();
  if (eventName === 'transfer.success') {
    if (currentStatus === 'SUCCEEDED') return { matched: true, idempotent: true };
    if (currentStatus !== 'PENDING') return { matched: true, idempotent: true };
    await postWalletInternalLedgerTransaction(client, {
      walletId: operation.wallet_id,
      userId: operation.user_id,
      amount: operation.amount,
      reference: 'WALLET-WD-SETTLE-' + operation.id,
      description: 'Internal withdrawal settlement',
      debitAccount: 'WITHDRAWAL_PENDING',
      creditAccount: 'EXTERNAL_BANK',
      metadata: { provider: 'paystack', operationId: String(operation.id), providerReference: reference }
    });
    await client.query(`
      UPDATE buyer_wallet_provider_operations
         SET status='SUCCEEDED',provider_transfer_code=COALESCE($1,provider_transfer_code),
             failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW()
       WHERE id=$3 AND status='PENDING'
    `, [
      transfer.transfer_code ? String(transfer.transfer_code) : null,
      JSON.stringify({ ...(operation.metadata || {}), transferEvent: eventName, providerStatus: transfer.status || 'success' }),
      operation.id
    ]);
    return { matched: true, status: 'SUCCEEDED' };
  }

  if (eventName === 'transfer.failed') {
    if (currentStatus === 'FAILED') return { matched: true, idempotent: true };
    if (currentStatus !== 'PENDING') return { matched: true, idempotent: true };
    await postWalletInternalLedgerTransaction(client, {
      walletId: operation.wallet_id,
      userId: operation.user_id,
      amount: operation.amount,
      reference: 'WALLET-WD-RELEASE-' + operation.id,
      description: 'Failed withdrawal funds returned',
      debitAccount: 'WITHDRAWAL_PENDING',
      creditAccount: 'AVAILABLE',
      metadata: { provider: 'paystack', operationId: String(operation.id), providerReference: reference }
    });
    await client.query(`
      UPDATE buyer_wallet_provider_operations
         SET status='FAILED',provider_transfer_code=COALESCE($1,provider_transfer_code),
             failure_reason=$2,metadata=$3::jsonb,updated_at=NOW()
       WHERE id=$4 AND status='PENDING'
    `, [
      transfer.transfer_code ? String(transfer.transfer_code) : null,
      String(transfer.reason || transfer.message || 'Paystack transfer failed').slice(0, 240),
      JSON.stringify({ ...(operation.metadata || {}), transferEvent: eventName, providerStatus: transfer.status || 'failed' }),
      operation.id
    ]);
    return { matched: true, status: 'FAILED' };
  }

  if (eventName === 'transfer.reversed') {
    if (currentStatus === 'REVERSED') return { matched: true, idempotent: true };
    if (currentStatus !== 'SUCCEEDED') return { matched: true, idempotent: true };
    await postWalletInternalLedgerTransaction(client, {
      walletId: operation.wallet_id,
      userId: operation.user_id,
      amount: operation.amount,
      reference: 'WALLET-WD-REVERSE-' + operation.id,
      description: 'Reversed bank withdrawal returned to wallet',
      debitAccount: 'EXTERNAL_BANK',
      creditAccount: 'AVAILABLE',
      metadata: { provider: 'paystack', operationId: String(operation.id), providerReference: reference }
    });
    await client.query(`
      UPDATE buyer_wallet_provider_operations
         SET status='REVERSED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW()
       WHERE id=$3 AND status='SUCCEEDED'
    `, [
      String(transfer.reason || transfer.message || 'Paystack reversed the transfer').slice(0, 240),
      JSON.stringify({ ...(operation.metadata || {}), transferEvent: eventName, providerStatus: transfer.status || 'reversed' }),
      operation.id
    ]);
    return { matched: true, status: 'REVERSED' };
  }
  return { matched: true, ignored: true };
}

export function parseBuyerWalletActivityCursor(query = {}) {
  const params = query && typeof query === 'object' ? query : {};
  const hasCreatedAt = Object.prototype.hasOwnProperty.call(params, 'beforeCreatedAt');
  const hasId = Object.prototype.hasOwnProperty.call(params, 'beforeId');
  if (!hasCreatedAt && !hasId) return { cursor: null, error: null };
  if (!hasCreatedAt || !hasId) return { cursor: null, error: 'both activity cursor fields are required' };
  const createdAtValue = params.beforeCreatedAt;
  const idValue = params.beforeId;
  if (typeof createdAtValue !== 'string' || typeof idValue !== 'string') {
    return { cursor: null, error: 'activity cursor fields must be single strings' };
  }
  const createdAt = createdAtValue.trim();
  const id = idValue.trim();
  if (!createdAt || !id) return { cursor: null, error: 'both activity cursor fields are required' };
  if (createdAt.length > 64 || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/i.test(createdAt)) {
    return { cursor: null, error: 'activity cursor date is invalid' };
  }
  const parsedDate = new Date(createdAt);
  if (!Number.isFinite(parsedDate.getTime())) return { cursor: null, error: 'activity cursor date is invalid' };
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
    return { cursor: null, error: 'activity cursor id is invalid' };
  }
  return { cursor: { createdAt, id: id.toLowerCase() }, error: null };
}

export function parseBuyerWalletActivityDirection(query = {}) {
  const hasDirection = query && typeof query === 'object' &&
    Object.prototype.hasOwnProperty.call(query, 'direction');
  const value = hasDirection ? query.direction : 'ALL';
  if (typeof value !== 'string') return { direction: null, error: 'activity direction must be a single string' };
  const direction = value.trim().toUpperCase();
  if (!['ALL', 'IN', 'OUT'].includes(direction)) {
    return { direction: null, error: 'activity direction is invalid' };
  }
  return { direction, error: null };
}

export function registerBuyerWalletRoutes({ app }) {
  // Buyer wallet foundation is intentionally read-only until the production
  // wallet provider, custody terms and withdrawal flow have been approved.
  app.get('/api/wallet/summary', auth, async (req, res) => {
    try {
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const wallet = (await pool.query(
        'SELECT id,status,provider,currency,funding_account_status,created_at,updated_at FROM buyer_wallets WHERE user_id=$1 AND currency=$2 LIMIT 1',
        [req.user.sub, 'NGN']
      )).rows[0];
      if (!wallet) {
        return res.json({
          walletStatus: 'NOT_SETUP',
          currency: 'NGN',
          availableBalance: null,
          reservedBalance: null,
          withdrawalPendingBalance: null,
          canFund: false,
          canSpend: false,
          canWithdraw: false,
          notice: 'Your FYNX Wallet is being prepared. Deposits, payments and withdrawals will open after the secure wallet provider is connected and verified.'
        });
      }
      const balanceRows = await pool.query(`
        SELECT e.account_code,
               COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS balance
          FROM buyer_wallet_ledger_entries e
          JOIN buyer_wallet_transactions t ON t.id=e.transaction_id
         WHERE e.wallet_id=$1
           AND t.status='SUCCEEDED'
           AND e.account_code IN ('AVAILABLE','RESERVED','WITHDRAWAL_PENDING')
         GROUP BY e.account_code
      `, [wallet.id]);
      const balances = Object.fromEntries(balanceRows.rows.map((row) => [row.account_code, Number(row.balance || 0)]));
      return res.json({
        walletStatus: String(wallet.status || 'PENDING_PROVIDER'),
        currency: String(wallet.currency || 'NGN'),
        availableBalance: balances.AVAILABLE || 0,
        reservedBalance: balances.RESERVED || 0,
        withdrawalPendingBalance: balances.WITHDRAWAL_PENDING || 0,
        canFund: wallet.funding_account_status === 'ACTIVE' && walletProviderApproved('DEPOSIT'),
        canSpend: false,
        canWithdraw: wallet.status === 'ACTIVE' && walletProviderApproved('WITHDRAWAL'),
        notice: wallet.funding_account_status === 'ACTIVE' && walletProviderApproved('DEPOSIT')
          ? 'Your dedicated funding account is active. Wallet spending and withdrawals remain unavailable until their separate approvals are complete.'
          : 'Wallet money movement is not enabled until provider, custody and regulatory checks are complete.',
        updatedAt: wallet.updated_at
      });
    } catch (error) {
      console.error('buyer wallet summary', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet summary is temporarily unavailable' });
    }
  });


  app.get('/api/wallet/funding-account', auth, async (req, res) => {
    try {
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const wallet = (await pool.query(`
        SELECT status,funding_account_status,funding_bank_name,funding_account_name,
               funding_account_ciphertext,funding_account_iv,funding_account_tag,updated_at
          FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' LIMIT 1
      `, [req.user.sub])).rows[0];
      if (!wallet) return res.json({ status: 'NOT_SETUP', currency: 'NGN', account: null, canRequest: walletProviderApproved('DEPOSIT') });
      const accountNumber = wallet.funding_account_status === 'ACTIVE' ? decryptWalletAccountNumber(wallet) : null;
      const account = accountNumber ? {
        bankName: wallet.funding_bank_name,
        accountName: wallet.funding_account_name,
        accountNumber,
        currency: 'NGN'
      } : null;
      return res.json({
        status: String(wallet.funding_account_status || 'NOT_CREATED'),
        currency: 'NGN',
        account,
        canRequest: walletProviderApproved('DEPOSIT'),
        updatedAt: wallet.updated_at
      });
    } catch (error) {
      console.error('buyer wallet funding account lookup', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'funding account is temporarily unavailable' });
    }
  });

  app.post('/api/wallet/funding-account', auth, async (req, res) => {
    const email = typeof req.body?.email === 'string' ? req.body.email.trim().toLowerCase() : '';
    const firstName = typeof req.body?.firstName === 'string' ? req.body.firstName.trim().slice(0, 80) : '';
    const lastName = typeof req.body?.lastName === 'string' ? req.body.lastName.trim().slice(0, 80) : '';
    const phone = typeof req.body?.phone === 'string' ? req.body.phone.replace(/[\s()-]/g, '') : '';
    const preferredBank = String(process.env.FYNX_PAYSTACK_WALLET_BANK || '').trim();
    const consent = req.body?.explicitConsent === true;
    if (!consent) return res.status(400).json({ error: 'explicit consent is required before creating a dedicated account' });
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || firstName.length < 2 || lastName.length < 2 || !/^\+?[0-9]{10,15}$/.test(phone)) {
      return res.status(400).json({ error: 'valid email, full name and phone number are required' });
    }
    if (!walletProviderApproved('DEPOSIT') || !preferredBank || !walletEncryptionKey()) {
      return res.status(503).json({ error: 'FYNX dedicated wallet accounts are not enabled for this provider environment yet' });
    }

    let wallet;
    try {
      await ensureBuyerWalletSchema();
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        await client.query(`
          INSERT INTO buyer_wallets (id,user_id,currency,status,provider,funding_account_status)
          VALUES ($1,$2,'NGN','PENDING_PROVIDER','paystack','NOT_CREATED')
          ON CONFLICT (user_id,currency) DO NOTHING
        `, [crypto.randomUUID(), req.user.sub]);
        wallet = (await client.query(`
          SELECT id,status,provider_customer_code,funding_account_status,
                 funding_account_ciphertext,funding_account_iv,funding_account_tag,
                 funding_bank_name,funding_account_name,updated_at
            FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' FOR UPDATE
        `, [req.user.sub])).rows[0];
        if (wallet?.funding_account_status === 'ACTIVE') {
          await client.query('COMMIT');
          const number = decryptWalletAccountNumber(wallet);
          return res.json({
            status: 'ACTIVE',
            account: number ? { bankName: wallet.funding_bank_name, accountName: wallet.funding_account_name, accountNumber: number, currency: 'NGN' } : null,
            idempotent: true
          });
        }
        const staleRequest = wallet?.funding_account_status === 'REQUESTING' &&
          Date.now() - new Date(wallet.updated_at).getTime() > 5 * 60 * 1000;
        if (['REQUESTING','ASSIGNING'].includes(wallet?.funding_account_status) && !staleRequest) {
          await client.query('COMMIT');
          return res.status(202).json({ status: wallet.funding_account_status, message: 'Your dedicated account request is already being processed.' });
        }
        await client.query(`
          UPDATE buyer_wallets
             SET funding_account_status='REQUESTING',
                 provider='paystack',
                 provider_consent_at=NOW(),
                 provider_consent_version='DVA-CONSENT-1',
                 updated_at=NOW()
           WHERE id=$1
        `, [wallet.id]);
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
      } finally {
        client.release();
      }

      let customerCode = wallet.provider_customer_code;
      if (customerCode) {
        const existing = await walletPaystackRequest('/customer/' + encodeURIComponent(customerCode));
        if (existing?.dedicated_account?.account_number && existing?.dedicated_account?.assigned) {
          await persistWalletFundingAccount(wallet.id, existing.dedicated_account);
          const updated = (await pool.query('SELECT * FROM buyer_wallets WHERE id=$1', [wallet.id])).rows[0];
          return res.json({
            status: 'ACTIVE',
            account: {
              bankName: updated.funding_bank_name,
              accountName: updated.funding_account_name,
              accountNumber: decryptWalletAccountNumber(updated),
              currency: 'NGN'
            },
            idempotent: true
          });
        }
      } else {
        const customer = await walletPaystackRequest('/customer', {
          method: 'POST',
          body: {
            email,
            first_name: firstName,
            last_name: lastName,
            phone,
            metadata: { fynx_user_id: String(req.user.sub), wallet_currency: 'NGN' }
          }
        });
        customerCode = String(customer.customer_code || '');
        if (!/^CUS_[A-Za-z0-9]+$/.test(customerCode)) throw Object.assign(new Error('payment provider did not return a valid customer reference'), { code: 'WALLET_CUSTOMER_INVALID' });
        await pool.query('UPDATE buyer_wallets SET provider_customer_code=$2,updated_at=NOW() WHERE id=$1', [wallet.id, customerCode]);
      }

      const accountData = await walletPaystackRequest('/dedicated_account', {
        method: 'POST',
        body: { customer: customerCode, preferred_bank: preferredBank }
      });
      if (accountData?.account_number && accountData?.assigned) {
        await persistWalletFundingAccount(wallet.id, accountData);
        const updated = (await pool.query('SELECT * FROM buyer_wallets WHERE id=$1', [wallet.id])).rows[0];
        return res.json({
          status: 'ACTIVE',
          account: {
            bankName: updated.funding_bank_name,
            accountName: updated.funding_account_name,
            accountNumber: decryptWalletAccountNumber(updated),
            currency: 'NGN'
          }
        });
      }
      await pool.query(`UPDATE buyer_wallets SET funding_account_status='ASSIGNING',updated_at=NOW() WHERE id=$1`, [wallet.id]);
      return res.status(202).json({ status: 'ASSIGNING', message: 'Your bank account is being assigned. FYNX will display it after Paystack confirms the assignment.' });
    } catch (error) {
      console.error('buyer wallet dedicated account setup', error);
      await pool.query(`
        UPDATE buyer_wallets SET funding_account_status='FAILED',updated_at=NOW()
         WHERE user_id=$1 AND currency='NGN' AND funding_account_status='REQUESTING'
      `, [req.user.sub]).catch(() => {});
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 502)
        .json({ error: 'dedicated funding account setup could not be completed' });
    }
  });


  app.get('/api/wallet/security', auth, async (req, res) => {
    try {
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const wallet = (await pool.query(`
        SELECT withdrawal_pin_hash,withdrawal_pin_locked_until
          FROM buyer_wallets
         WHERE user_id=$1 AND currency='NGN'
         LIMIT 1
      `, [req.user.sub])).rows[0];
      const lockedUntil = wallet?.withdrawal_pin_locked_until || null;
      const locked = Boolean(lockedUntil && new Date(lockedUntil).getTime() > Date.now());
      return res.json({
        hasWithdrawalPin: Boolean(wallet?.withdrawal_pin_hash),
        isLocked: locked,
        lockedUntil: locked ? lockedUntil : null,
        limits: walletWithdrawalLimits()
      });
    } catch (error) {
      console.error('buyer wallet security status', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet security status is temporarily unavailable' });
    }
  });

  app.post('/api/wallet/security/pin', auth, async (req, res) => {
    const currentPassword = typeof req.body?.currentPassword === 'string' ? req.body.currentPassword : '';
    const pin = typeof req.body?.pin === 'string' ? req.body.pin.trim() : '';
    const confirmPin = typeof req.body?.confirmPin === 'string' ? req.body.confirmPin.trim() : '';
    if (!/^\d{6}$/.test(pin) || pin !== confirmPin || currentPassword.length < 8 || currentPassword.length > 128) {
      return res.status(400).json({ error: 'enter your current password and matching six-digit wallet PIN' });
    }
    try {
      await ensureBuyerWalletSchema();
      const user = (await pool.query('SELECT password_hash FROM users WHERE id=$1 LIMIT 1', [req.user.sub])).rows[0];
      if (!user?.password_hash || !(await bcrypt.compare(currentPassword, user.password_hash))) {
        return res.status(403).json({ error: 'current FYNX password could not be verified' });
      }
      const wallet = (await pool.query(
        "SELECT id,status FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' LIMIT 1",
        [req.user.sub]
      )).rows[0];
      if (!wallet || wallet.status !== 'ACTIVE') {
        return res.status(409).json({ error: 'an active FYNX Wallet is required before setting a withdrawal PIN' });
      }
      const pinHash = await bcrypt.hash(pin, 12);
      await pool.query(`
        UPDATE buyer_wallets
           SET withdrawal_pin_hash=$1,
               withdrawal_pin_set_at=NOW(),
               withdrawal_pin_failed_attempts=0,
               withdrawal_pin_locked_until=NULL,
               updated_at=NOW()
         WHERE id=$2
      `, [pinHash, wallet.id]);
      return res.json({ hasWithdrawalPin: true, updated: true });
    } catch (error) {
      console.error('buyer wallet withdrawal PIN setup', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet withdrawal PIN could not be saved' });
    }
  });

  app.get('/api/wallet/payout-banks', auth, async (req, res) => {
    if (!walletProviderApproved('WITHDRAWAL')) return res.status(503).json({ error: 'wallet bank payouts are not enabled for this provider environment yet' });
    try {
      const data = await walletPaystackRequest('/bank?country=nigeria&perPage=100');
      const banks = (Array.isArray(data) ? data : []).map((bank) => ({
        name: String(bank.name || '').trim(),
        code: String(bank.code || '').trim(),
        slug: String(bank.slug || '').trim()
      })).filter((bank) => bank.name && /^[0-9]{3,10}$/.test(bank.code));
      return res.json({ banks });
    } catch (error) {
      console.error('buyer wallet payout bank list', error);
      return res.status(502).json({ error: 'supported banks are temporarily unavailable' });
    }
  });

  app.post('/api/wallet/payout-account/verify', auth, async (req, res) => {
    const bankCode = typeof req.body?.bankCode === 'string' ? req.body.bankCode.trim() : '';
    const accountNumber = typeof req.body?.accountNumber === 'string' ? req.body.accountNumber.replace(/\D/g, '') : '';
    if (!walletProviderApproved('WITHDRAWAL')) return res.status(503).json({ error: 'wallet bank payouts are not enabled for this provider environment yet' });
    if (!/^[0-9]{3,10}$/.test(bankCode) || !/^[0-9]{10}$/.test(accountNumber)) {
      return res.status(400).json({ error: 'select a supported bank and enter a 10-digit account number' });
    }
    try {
      const resolved = await walletPaystackRequest('/bank/resolve?account_number=' + encodeURIComponent(accountNumber) + '&bank_code=' + encodeURIComponent(bankCode));
      const accountName = String(resolved.account_name || '').trim();
      if (!accountName) return res.status(422).json({ error: 'bank account could not be verified' });
      return res.json({ verified: true, accountName, accountLast4: accountNumber.slice(-4) });
    } catch (error) {
      console.error('buyer wallet payout account verification', error);
      return res.status(422).json({ error: 'bank account could not be verified' });
    }
  });

  app.post('/api/wallet/withdrawals', auth, async (req, res) => {
    const amountValue = Number(req.body?.amount);
    const amountMinor = Math.round(amountValue * 100);
    const bankCode = typeof req.body?.bankCode === 'string' ? req.body.bankCode.trim() : '';
    const accountNumber = typeof req.body?.accountNumber === 'string' ? req.body.accountNumber.replace(/\D/g, '') : '';
    const idempotencyKey = typeof req.body?.idempotencyKey === 'string' ? req.body.idempotencyKey.trim() : '';
    const pin = typeof req.body?.pin === 'string' ? req.body.pin.trim() : '';
    if (!walletProviderApproved('WITHDRAWAL')) return res.status(503).json({ error: 'wallet withdrawals are not enabled for this provider environment yet' });
    if (!Number.isFinite(amountValue) || amountValue <= 0 || !Number.isSafeInteger(amountMinor) ||
        !/^[0-9]{3,10}$/.test(bankCode) || !/^[0-9]{10}$/.test(accountNumber) ||
        !/^[A-Za-z0-9_-]{8,100}$/.test(idempotencyKey) || !/^\d{6}$/.test(pin)) {
      return res.status(400).json({ error: 'valid withdrawal amount, bank, account number, six-digit wallet PIN and idempotency key are required' });
    }
    const limits = walletWithdrawalLimits();
    if (!limits) return res.status(503).json({ error: 'wallet withdrawal limits are not configured for this provider environment yet' });
    if (amountValue > limits.maximumPerTransfer) {
      return res.status(400).json({ error: 'withdrawal amount is above the current per-transfer limit' });
    }
    const amount = (amountMinor / 100).toFixed(2);
    try {
      await ensureBuyerWalletSchema();
      const existing = (await pool.query(
        'SELECT id,user_id,amount,status,provider_reference,bank_name,account_last4 FROM buyer_wallet_provider_operations WHERE idempotency_key=$1 LIMIT 1',
        [idempotencyKey]
      )).rows[0];
      if (existing) {
        if (String(existing.user_id) !== String(req.user.sub) || Number(existing.amount) !== Number(amount)) {
          return res.status(409).json({ error: 'withdrawal idempotency key has already been used for another request' });
        }
        return res.status(202).json({
          status: existing.status,
          reference: existing.provider_reference,
          amount: Number(existing.amount),
          bankName: existing.bank_name,
          accountLast4: existing.account_last4,
          idempotent: true
        });
      }

      // Check the wallet PIN before creating provider-side recipient records.
      // The PIN is checked again under the wallet row lock before funds are reserved.
      const pinClient = await pool.connect();
      try {
        await pinClient.query('BEGIN');
        const pinWallet = (await pinClient.query(`
          SELECT id,status,withdrawal_pin_hash,withdrawal_pin_failed_attempts,withdrawal_pin_locked_until
            FROM buyer_wallets
           WHERE user_id=$1 AND currency='NGN'
           FOR UPDATE
        `, [req.user.sub])).rows[0];
        if (!pinWallet || pinWallet.status !== 'ACTIVE' || !pinWallet.withdrawal_pin_hash) {
          await pinClient.query('ROLLBACK');
          return res.status(409).json({ error: 'an active wallet with a withdrawal PIN is required' });
        }
        const lockedUntil = pinWallet.withdrawal_pin_locked_until ? new Date(pinWallet.withdrawal_pin_locked_until).getTime() : 0;
        if (lockedUntil > Date.now()) {
          await pinClient.query('ROLLBACK');
          return res.status(423).json({ error: 'wallet withdrawals are temporarily locked after repeated incorrect PIN attempts' });
        }
        if (!(await bcrypt.compare(pin, pinWallet.withdrawal_pin_hash))) {
          const previous = lockedUntil > 0 && lockedUntil <= Date.now() ? 0 : Number(pinWallet.withdrawal_pin_failed_attempts || 0);
          const attempts = previous + 1;
          await pinClient.query(`
            UPDATE buyer_wallets
               SET withdrawal_pin_failed_attempts=$1,
                   withdrawal_pin_locked_until=CASE WHEN $1 >= 5 THEN NOW() + INTERVAL '15 minutes' ELSE NULL END,
                   updated_at=NOW()
             WHERE id=$2
          `, [attempts, pinWallet.id]);
          await pinClient.query('COMMIT');
          return res.status(403).json({
            error: attempts >= 5
              ? 'wallet PIN attempts exceeded; withdrawals are locked for 15 minutes'
              : 'wallet withdrawal PIN is incorrect'
          });
        }
        await pinClient.query(
          'UPDATE buyer_wallets SET withdrawal_pin_failed_attempts=0,withdrawal_pin_locked_until=NULL WHERE id=$1',
          [pinWallet.id]
        );
        await pinClient.query('COMMIT');
      } catch (error) {
        await pinClient.query('ROLLBACK').catch(() => {});
        throw error;
      } finally {
        pinClient.release();
      }

      const resolved = await walletPaystackRequest('/bank/resolve?account_number=' + encodeURIComponent(accountNumber) + '&bank_code=' + encodeURIComponent(bankCode));
      const verifiedName = String(resolved.account_name || '').trim();
      if (!verifiedName) return res.status(422).json({ error: 'bank account could not be verified' });
      const recipient = await walletPaystackRequest('/transferrecipient', {
        method: 'POST',
        body: {
          type: 'nuban',
          name: verifiedName,
          account_number: accountNumber,
          bank_code: bankCode,
          currency: 'NGN',
          description: 'FYNX wallet payout ' + String(req.user.sub)
        }
      });
      const recipientCode = String(recipient.recipient_code || '');
      if (!/^RCP_[A-Za-z0-9]+$/.test(recipientCode)) throw Object.assign(new Error('payment provider did not return a valid transfer recipient'), { code: 'WALLET_RECIPIENT_INVALID' });

      const client = await pool.connect();
      let operation;
      try {
        await client.query('BEGIN');
        const wallet = (await client.query(`
          SELECT id,status,currency,withdrawal_pin_hash,withdrawal_pin_failed_attempts,withdrawal_pin_locked_until FROM buyer_wallets
           WHERE user_id=$1 AND currency='NGN'
           FOR UPDATE
        `, [req.user.sub])).rows[0];
        if (!wallet || wallet.status !== 'ACTIVE') {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'an active FYNX wallet is required before withdrawal' });
        }
        if (!wallet.withdrawal_pin_hash) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'set your wallet withdrawal PIN before requesting a payout' });
        }
        const pinLockedUntil = wallet.withdrawal_pin_locked_until ? new Date(wallet.withdrawal_pin_locked_until).getTime() : 0;
        if (pinLockedUntil > Date.now()) {
          await client.query('ROLLBACK');
          return res.status(423).json({ error: 'wallet withdrawals are temporarily locked after repeated incorrect PIN attempts' });
        }
        const enteredPinMatches = await bcrypt.compare(pin, wallet.withdrawal_pin_hash);
        if (!enteredPinMatches) {
          const priorFailures = pinLockedUntil > 0 && pinLockedUntil <= Date.now()
            ? 0
            : Number(wallet.withdrawal_pin_failed_attempts || 0);
          const failedAttempts = priorFailures + 1;
          await client.query(`
            UPDATE buyer_wallets
               SET withdrawal_pin_failed_attempts=$1,
                   withdrawal_pin_locked_until=CASE WHEN $1 >= 5 THEN NOW() + INTERVAL '15 minutes' ELSE NULL END,
                   updated_at=NOW()
             WHERE id=$2
          `, [failedAttempts, wallet.id]);
          await client.query('COMMIT');
          return res.status(403).json({
            error: failedAttempts >= 5
              ? 'wallet PIN attempts exceeded; withdrawals are locked for 15 minutes'
              : 'wallet withdrawal PIN is incorrect'
          });
        }
        await client.query(
          'UPDATE buyer_wallets SET withdrawal_pin_failed_attempts=0,withdrawal_pin_locked_until=NULL WHERE id=$1',
          [wallet.id]
        );
        const balance = (await client.query(`
          SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS available
            FROM buyer_wallet_ledger_entries e
            JOIN buyer_wallet_transactions t ON t.id=e.transaction_id
           WHERE e.wallet_id=$1 AND e.account_code='AVAILABLE' AND t.status='SUCCEEDED'
        `, [wallet.id])).rows[0];
        if (Number(balance?.available || 0) < Number(amount)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'withdrawal amount is above your cleared available balance' });
        }
        const dailyUsage = (await client.query(`
          SELECT COUNT(*)::int AS request_count,
                 COALESCE(SUM(amount),0)::numeric(18,2) AS requested_amount
            FROM buyer_wallet_provider_operations
           WHERE user_id=$1
             AND created_at >= date_trunc('day',NOW())
        `, [req.user.sub])).rows[0];
        if (Number(dailyUsage?.request_count || 0) >= limits.dailyCountLimit) {
          await client.query('ROLLBACK');
          return res.status(429).json({ error: 'daily wallet withdrawal request limit has been reached' });
        }
        if (Number(dailyUsage?.requested_amount || 0) + Number(amount) > limits.dailyAmountLimit) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'withdrawal amount would exceed your remaining daily limit' });
        }
        const transactionId = crypto.randomUUID();
        const reference = 'fw_' + crypto.randomUUID().replace(/-/g, '');
        const operationId = crypto.randomUUID();
        await client.query(`
          INSERT INTO buyer_wallet_transactions
            (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata)
          VALUES ($1,$2,$3,'WITHDRAWAL','PENDING',$4,0,'NGN',$5,$6,'Bank withdrawal requested',$7::jsonb)
        `, [
          transactionId, wallet.id, req.user.sub, amount, reference,
          'WALLET-WITHDRAWAL-' + idempotencyKey,
          JSON.stringify({ provider: 'paystack', bankCode, accountLast4: accountNumber.slice(-4) })
        ]);
        await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
        await client.query(`
          INSERT INTO buyer_wallet_ledger_entries
            (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
          VALUES
            ($1,$2,$3,'AVAILABLE','DEBIT',$4,'NGN'),
            ($1,$2,$5,'WITHDRAWAL_PENDING','CREDIT',$4,'NGN')
        `, [transactionId, wallet.id, 'WALLET-WD-RESERVE-' + operationId + '-DR', amount, 'WALLET-WD-RESERVE-' + operationId + '-CR']);
        await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
        operation = (await client.query(`
          INSERT INTO buyer_wallet_provider_operations
            (id,wallet_id,user_id,transaction_id,operation_type,idempotency_key,provider_reference,status,amount,currency,recipient_code,bank_name,account_name,account_last4,metadata)
          VALUES ($1,$2,$3,$4,'WITHDRAWAL',$5,$6,'PENDING',$7,'NGN',$8,$9,$10,$11,$12::jsonb)
          RETURNING *
        `, [
          operationId, wallet.id, req.user.sub, transactionId, idempotencyKey, reference,
          amount, recipientCode, String(req.body?.bankName || '').trim().slice(0, 120),
          verifiedName, accountNumber.slice(-4), JSON.stringify({ bankCode })
        ])).rows[0];
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        if (error?.code === '23505') return res.status(409).json({ error: 'a matching wallet withdrawal request already exists' });
        throw error;
      } finally {
        client.release();
      }

      try {
        const transfer = await walletPaystackRequest('/transfer', {
          method: 'POST',
          body: {
            source: 'balance',
            amount: amountMinor,
            recipient: recipientCode,
            reference: operation.provider_reference,
            reason: 'FYNX Wallet withdrawal'
          }
        });
        const providerStatus = String(transfer.status || '').toLowerCase();
        if (providerStatus === 'otp') {
          const client = await pool.connect();
          try {
            await client.query('BEGIN');
            const locked = (await client.query('SELECT * FROM buyer_wallet_provider_operations WHERE id=$1 FOR UPDATE', [operation.id])).rows[0];
            if (locked?.status === 'PENDING') {
              await postWalletInternalLedgerTransaction(client, {
                walletId: locked.wallet_id,
                userId: locked.user_id,
                amount: locked.amount,
                reference: 'WALLET-WD-OTP-RELEASE-' + locked.id,
                description: 'Withdrawal was not sent because provider approval is required',
                debitAccount: 'WITHDRAWAL_PENDING',
                creditAccount: 'AVAILABLE',
                metadata: { operationId: String(locked.id), reason: 'provider_otp_required' }
              });
              await client.query("UPDATE buyer_wallet_provider_operations SET status='BLOCKED',failure_reason='provider transfer OTP is not disabled',updated_at=NOW() WHERE id=$1 AND status='PENDING'", [locked.id]);
            }
            await client.query('COMMIT');
          } catch (error) { await client.query('ROLLBACK').catch(() => {}); throw error; }
          finally { client.release(); }
          return res.status(409).json({ error: 'provider transfer approval is not configured; reserved funds were released' });
        }
        await pool.query(`
          UPDATE buyer_wallet_provider_operations
             SET provider_transfer_code=$1,metadata=$2::jsonb,updated_at=NOW()
           WHERE id=$3 AND status='PENDING'
        `, [
          transfer.transfer_code ? String(transfer.transfer_code) : null,
          JSON.stringify({ ...(operation.metadata || {}), providerStatus, transferId: transfer.id || null }),
          operation.id
        ]);
        return res.status(202).json({
          status: 'PENDING',
          reference: operation.provider_reference,
          amount: Number(operation.amount),
          bankName: operation.bank_name,
          accountLast4: operation.account_last4,
          message: 'Withdrawal is processing. FYNX will update the wallet after Paystack confirms the bank transfer.'
        });
      } catch (error) {
        if (Number(error?.status) >= 400 && Number(error?.status) < 500) {
          const client = await pool.connect();
          try {
            await client.query('BEGIN');
            const locked = (await client.query('SELECT * FROM buyer_wallet_provider_operations WHERE id=$1 FOR UPDATE', [operation.id])).rows[0];
            if (locked?.status === 'PENDING') {
              await postWalletInternalLedgerTransaction(client, {
                walletId: locked.wallet_id,
                userId: locked.user_id,
                amount: locked.amount,
                reference: 'WALLET-WD-REJECT-RELEASE-' + locked.id,
                description: 'Rejected withdrawal funds returned',
                debitAccount: 'WITHDRAWAL_PENDING',
                creditAccount: 'AVAILABLE',
                metadata: { operationId: String(locked.id), reason: 'provider_rejected_request' }
              });
              await client.query(`
                UPDATE buyer_wallet_provider_operations
                   SET status='FAILED',failure_reason=$1,updated_at=NOW()
                 WHERE id=$2 AND status='PENDING'
              `, [String(error.message || 'provider rejected withdrawal').slice(0, 240), locked.id]);
            }
            await client.query('COMMIT');
          } catch (releaseError) { await client.query('ROLLBACK').catch(() => {}); throw releaseError; }
          finally { client.release(); }
          return res.status(409).json({ error: 'the payment provider rejected this withdrawal; reserved funds have been returned' });
        }
        // A timeout or server error may be non-conclusive. Keep the exact reference and funds reserved for reconciliation.
        console.error('buyer wallet payout request non-conclusive', error);
        return res.status(202).json({
          status: 'PENDING',
          reference: operation.provider_reference,
          amount: Number(operation.amount),
          bankName: operation.bank_name,
          accountLast4: operation.account_last4,
          message: 'The provider response is not yet conclusive. Your funds remain reserved while FYNX reconciles this reference.'
        });
      }
    } catch (error) {
      console.error('buyer wallet withdrawal request', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 502)
        .json({ error: 'withdrawal request could not be created' });
    }
  });

  app.get('/api/wallet/statement.csv', auth, async (req, res) => {
    try {
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const today = new Date().toISOString().slice(0, 10);
      const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
      const from = parseStatementDate(req.query?.from, ninetyDaysAgo);
      const to = parseStatementDate(req.query?.to, today);
      if (!from || !to || from > to) return res.status(400).json({ error: 'statement dates must be valid ISO dates with from no later than to' });
      const days = (Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86_400_000;
      if (days > 366) return res.status(400).json({ error: 'wallet statements are limited to 12 months per export' });
      const wallet = (await pool.query(
        "SELECT id FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' LIMIT 1",
        [req.user.sub]
      )).rows[0];
      if (!wallet) {
        res.type('text/csv; charset=utf-8');
        res.set('Content-Disposition', 'attachment; filename="fynx-wallet-statement.csv"');
        return res.send(buildBuyerWalletStatementCsv([]));
      }
      const rows = await pool.query(`
        SELECT t.transaction_type,
               CASE WHEN op.status='PENDING' THEN 'PROCESSING'
                    WHEN op.status IS NOT NULL THEN op.status ELSE t.status END AS status,
               t.amount,t.fee,t.currency,t.reference,t.description,
               to_char(t.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS created_at_iso,
               COALESCE(delta.wallet_delta,0)::numeric(18,2) AS wallet_delta
          FROM buyer_wallet_transactions t
          LEFT JOIN buyer_wallet_provider_operations op ON op.transaction_id=t.id
          LEFT JOIN LATERAL (
            SELECT SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END) AS wallet_delta
              FROM buyer_wallet_ledger_entries e
             WHERE e.transaction_id=t.id AND e.account_code='AVAILABLE'
          ) delta ON TRUE
         WHERE t.user_id=$1 AND t.wallet_id=$2
           AND COALESCE(t.metadata->>'internal','false') <> 'true'
           AND t.created_at >= $3::date
           AND t.created_at < ($4::date + INTERVAL '1 day')
         ORDER BY t.created_at ASC,t.id ASC
         LIMIT 5001
      `, [req.user.sub, wallet.id, from, to]);
      if (rows.rows.length > 5000) return res.status(413).json({ error: 'statement is too large; export a shorter date range' });
      res.type('text/csv; charset=utf-8');
      res.set('Content-Disposition', 'attachment; filename="fynx-wallet-statement-' + from + '-to-' + to + '.csv"');
      return res.send(buildBuyerWalletStatementCsv(rows.rows));
    } catch (error) {
      console.error('buyer wallet statement export', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet statement is temporarily unavailable' });
    }
  });


  app.post('/api/wallet/paystack/webhook', async (req, res) => {
    const secret = String(process.env.PAYSTACK_SECRET_KEY || '');
    const signature = req.get('x-paystack-signature') || '';
    const rawBody = Buffer.isBuffer(req.rawBody) ? req.rawBody : null;
    const expected = secret && rawBody
      ? crypto.createHmac('sha512', secret).update(rawBody).digest('hex')
      : '';
    const safeSignature = (left, right) => {
      if (typeof left !== 'string' || typeof right !== 'string' || left.length !== right.length) return false;
      try { return crypto.timingSafeEqual(Buffer.from(left, 'hex'), Buffer.from(right, 'hex')); } catch { return false; }
    };
    if (!secret || !rawBody || !safeSignature(expected, signature)) return res.status(401).json({ error: 'invalid wallet payment webhook signature' });
    let event;
    try { event = JSON.parse(rawBody.toString('utf8')); } catch { return res.status(400).json({ error: 'invalid webhook payload' }); }
    const eventName = String(event?.event || '').trim().toLowerCase();
    const data = event?.data || {};

    if (eventName.startsWith('transfer.')) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const outcome = await reconcileWalletTransferEvent(client, eventName, data);
        await client.query('COMMIT');
        return res.status(200).json({ received: true, ...outcome });
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        console.error('buyer wallet Paystack transfer webhook', error);
        return res.status(500).json({ error: 'wallet transfer reconciliation failed' });
      } finally {
        client.release();
      }
    }

    if (eventName === 'customeridentification.failed' || eventName === 'dedicatedaccount.assign.failed') {
      const customerCode = String(data.customer_code || data.customer?.customer_code || '').trim();
      if (customerCode) {
        await pool.query(
          "UPDATE buyer_wallets SET funding_account_status='RESTRICTED',status='RESTRICTED',updated_at=NOW() WHERE provider_customer_code=$1",
          [customerCode]
        );
      }
      return res.status(200).json({ received: true, matched: Boolean(customerCode) });
    }

    if (eventName === 'dedicatedaccount.assign.success') {
      const customerCode = String(data.customer_code || data.customer?.customer_code || '').trim();
      if (!customerCode) return res.status(200).json({ received: true, matched: false });
      const wallet = (await pool.query(
        'SELECT id FROM buyer_wallets WHERE provider_customer_code=$1 LIMIT 1',
        [customerCode]
      )).rows[0];
      if (!wallet) return res.status(200).json({ received: true, matched: false });
      try {
        let account = data.dedicated_account || data.account || data;
        if (!account?.account_number) {
          const customer = await walletPaystackRequest('/customer/' + encodeURIComponent(customerCode));
          account = customer?.dedicated_account;
        }
        await persistWalletFundingAccount(wallet.id, account);
        return res.status(200).json({ received: true, matched: true });
      } catch (error) {
        console.error('buyer wallet DVA assignment webhook', error);
        return res.status(500).json({ error: 'wallet account assignment could not be reconciled' });
      }
    }

    if (eventName !== 'charge.success') return res.status(200).json({ received: true, ignored: true });
    const reference = typeof data.reference === 'string' ? data.reference.trim() : '';
    if (!reference || !/^[A-Za-z0-9_.=-]{8,100}$/.test(reference) || String(data.status || '').toLowerCase() !== 'success') {
      return res.status(400).json({ error: 'invalid wallet funding transaction reference or status' });
    }
    if (String(data.authorization?.channel || '').toLowerCase() !== 'dedicated_nuban') {
      return res.status(200).json({ received: true, ignored: true });
    }

    const client = await pool.connect();
    try {
      const verified = await walletPaystackRequest('/transaction/verify/' + encodeURIComponent(reference));
      const providerStatus = String(verified?.status || '').toLowerCase();
      const providerAmount = Number(verified?.amount);
      const providerCurrency = String(verified?.currency || '').toUpperCase();
      const providerReference = String(verified?.reference || '');
      const customerCode = String(verified?.customer?.customer_code || data.customer?.customer_code || '');
      const receiverAccount = String(
        verified?.authorization?.receiver_bank_account_number ||
        data.authorization?.receiver_bank_account_number ||
        ''
      ).replace(/\D/g, '');
      if (providerStatus !== 'success' || !Number.isInteger(providerAmount) || providerAmount <= 0 ||
          providerCurrency !== 'NGN' || providerReference !== reference) {
        return res.status(400).json({ error: 'Paystack funding verification did not match the webhook' });
      }

      await client.query('BEGIN');
      const wallet = (await client.query(`
        SELECT id,user_id,provider_customer_code,funding_account_ciphertext,
               funding_account_iv,funding_account_tag,status,funding_account_status
          FROM buyer_wallets
         WHERE provider_customer_code=$1 AND currency='NGN'
         FOR UPDATE
      `, [customerCode])).rows[0];
      if (!wallet || wallet.funding_account_status !== 'ACTIVE' || wallet.status !== 'ACTIVE') {
        await client.query('ROLLBACK');
        return res.status(200).json({ received: true, matched: false });
      }
      const expectedAccount = decryptWalletAccountNumber(wallet);
      if (!expectedAccount || receiverAccount !== expectedAccount) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'Paystack funding account does not match this wallet' });
      }
      const existing = (await client.query(
        'SELECT id,status FROM buyer_wallet_transactions WHERE provider_reference=$1 FOR UPDATE',
        [reference]
      )).rows[0];
      if (existing) {
        await client.query('COMMIT');
        return res.status(200).json({ received: true, matched: true, idempotent: true, status: existing.status });
      }
      const amount = (providerAmount / 100).toFixed(2);
      const transactionId = crypto.randomUUID();
      await client.query(`
        INSERT INTO buyer_wallet_transactions
          (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,provider_reference,description,metadata)
        VALUES ($1,$2,$3,'FUNDING','PENDING',$4,0,'NGN',$5,$6,$5,'Dedicated bank transfer funding',$7::jsonb)
      `, [
        transactionId, wallet.id, wallet.user_id, amount, reference,
        'PAYSTACK-DVA-' + reference,
        JSON.stringify({ provider: 'paystack', channel: 'dedicated_nuban', providerFeeSubunit: Number(verified.fees || 0) })
      ]);
      await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
      await client.query(`
        INSERT INTO buyer_wallet_ledger_entries
          (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
        VALUES
          ($1,$2,$3,'PROVIDER_CLEARING','DEBIT',$4,'NGN'),
          ($1,$2,$5,'AVAILABLE','CREDIT',$4,'NGN')
      `, [transactionId, wallet.id, 'PAYSTACK-DVA-CLEARING-' + reference, amount, 'PAYSTACK-DVA-AVAILABLE-' + reference]);
      await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
      await client.query('COMMIT');
      return res.status(200).json({ received: true, matched: true, credited: true });
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      if (error?.code === '23505') return res.status(200).json({ received: true, matched: true, idempotent: true });
      console.error('buyer wallet Paystack charge webhook', error);
      return res.status(500).json({ error: 'wallet funding webhook processing failed' });
    } finally {
      client.release();
    }
  });

  app.get('/api/wallet/transactions', auth, async (req, res) => {
    try {
      const parsedCursor = parseBuyerWalletActivityCursor(req.query || {});
      if (parsedCursor.error) return res.status(400).json({ error: parsedCursor.error });
      const parsedDirection = parseBuyerWalletActivityDirection(req.query || {});
      if (parsedDirection.error) return res.status(400).json({ error: parsedDirection.error });
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const wallet = (await pool.query(
        'SELECT id FROM buyer_wallets WHERE user_id=$1 AND currency=$2 LIMIT 1',
        [req.user.sub, 'NGN']
      )).rows[0];
      if (!wallet) return res.json({ transactions: [], hasMore: false, nextCursor: null });
      const requestedLimit = Number(req.query?.limit);
      const limit = Math.min(Math.max(Number.isInteger(requestedLimit) ? requestedLimit : 20, 1), 50);
      const cursor = parsedCursor.cursor;
      const cursorClause = cursor
        ? 'AND (t.created_at,t.id) < ($4::timestamptz,$5::uuid)'
        : '';
      const params = cursor
        ? [req.user.sub, wallet.id, parsedDirection.direction, cursor.createdAt, cursor.id, limit + 1]
        : [req.user.sub, wallet.id, parsedDirection.direction, limit + 1];
      const result = await pool.query(`
        SELECT t.id,t.transaction_type,
               CASE WHEN op.status='PENDING' THEN 'PROCESSING'
                    WHEN op.status IS NOT NULL THEN op.status ELSE t.status END AS status,
               t.amount,t.fee,t.currency,t.reference,t.description,t.created_at,
               to_char(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS created_at_iso,
               COALESCE(activity_delta.wallet_delta,0)::numeric(18,2) AS wallet_delta
          FROM buyer_wallet_transactions t
          LEFT JOIN buyer_wallet_provider_operations op ON op.transaction_id=t.id
          LEFT JOIN LATERAL (
            SELECT SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END) AS wallet_delta
              FROM buyer_wallet_ledger_entries e
             WHERE e.transaction_id=t.id AND e.account_code='AVAILABLE'
          ) activity_delta ON TRUE
         WHERE t.user_id=$1 AND t.wallet_id=$2
           AND COALESCE(t.metadata->>'internal','false') <> 'true'
           AND (
             $3::text='ALL'
             OR ($3::text='IN' AND COALESCE(activity_delta.wallet_delta,0) > 0)
             OR ($3::text='OUT' AND COALESCE(activity_delta.wallet_delta,0) < 0)
           )
           ${cursorClause}
         ORDER BY t.created_at DESC,t.id DESC
         LIMIT $${params.length}
      `, params);
      const hasMore = result.rows.length > limit;
      const pageRows = result.rows.slice(0, limit);
      const lastRow = pageRows[pageRows.length - 1];
      return res.json({
        transactions: pageRows.map((row) => ({
          id: String(row.id),
          type: String(row.transaction_type),
          status: String(row.status),
          amount: Number(row.amount),
          fee: Number(row.fee || 0),
          walletDelta: Number(row.wallet_delta || 0),
          currency: String(row.currency),
          reference: String(row.reference),
          description: String(row.description || ''),
          createdAt: row.created_at_iso
        })),
        hasMore,
        nextCursor: hasMore && lastRow
          ? { createdAt: lastRow.created_at_iso, id: String(lastRow.id) }
          : null
      });
    } catch (error) {
      console.error('buyer wallet transactions', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet activity is temporarily unavailable' });
    }
  });

}

export async function closeBuyerWalletPool() {
  if (pool) await pool.end();
}
