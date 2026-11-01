import crypto from 'node:crypto';
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

      CREATE TABLE IF NOT EXISTS buyer_wallet_payout_accounts (
        id UUID PRIMARY KEY, wallet_id UUID NOT NULL REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT, provider TEXT NOT NULL DEFAULT 'paystack',
        recipient_code TEXT NOT NULL UNIQUE, bank_code TEXT NOT NULL, bank_name TEXT NOT NULL, account_name TEXT NOT NULL,
        account_last4 TEXT NOT NULL, currency TEXT NOT NULL DEFAULT 'NGN' CHECK (currency='NGN'),
        verified BOOLEAN NOT NULL DEFAULT TRUE, active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS buyer_wallet_payout_accounts_owner_idx ON buyer_wallet_payout_accounts(user_id,active,updated_at DESC);
      CREATE TABLE IF NOT EXISTS buyer_wallet_security (
        wallet_id UUID PRIMARY KEY REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK(failed_attempts>=0),
        locked_until TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS buyer_wallet_withdrawals (
        id UUID PRIMARY KEY, transaction_id UUID NOT NULL UNIQUE REFERENCES buyer_wallet_transactions(id) ON DELETE RESTRICT,
        wallet_id UUID NOT NULL REFERENCES buyer_wallets(id) ON DELETE RESTRICT,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        payout_account_id UUID NOT NULL REFERENCES buyer_wallet_payout_accounts(id) ON DELETE RESTRICT,
        reference TEXT NOT NULL UNIQUE, idempotency_key TEXT NOT NULL UNIQUE, provider_reference TEXT UNIQUE,
        transfer_code TEXT, amount NUMERIC(18,2) NOT NULL CHECK(amount>0), fee NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(fee>=0),
        currency TEXT NOT NULL DEFAULT 'NGN' CHECK(currency='NGN'),
        status TEXT NOT NULL CHECK(status IN ('PENDING','PROCESSING','PENDING_OTP','SUCCEEDED','FAILED','REVERSED','BLOCKED')),
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS buyer_wallet_withdrawals_owner_idx ON buyer_wallet_withdrawals(user_id,created_at DESC);

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


const walletFlag = (name) => String(process.env[name] || '').trim().toLowerCase() === 'true';
const walletProviderReady = () => Boolean(process.env.PAYSTACK_SECRET_KEY)
  && walletFlag('FYNX_WALLET_PROVIDER_APPROVED')
  && walletFlag('FYNX_WALLET_CUSTODY_APPROVED')
  && walletFlag('FYNX_WALLET_WEBHOOK_CONFIGURED');
const walletFundingEnabled = () => walletProviderReady() && walletFlag('FYNX_WALLET_FUNDING_ENABLED');
const walletWithdrawalsEnabled = () => walletProviderReady()
  && walletFlag('FYNX_WALLET_WITHDRAWALS_ENABLED')
  && walletFlag('FYNX_WALLET_TRANSFERS_APPROVED')
  && walletFlag('FYNX_WALLET_KYC_APPROVED');
const walletMoneySubunit = (amount) => {
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) return null;
  const scaled = value * 100;
  const rounded = Math.round(scaled);
  if (!Number.isSafeInteger(rounded) || Math.abs(scaled - rounded) > 0.000001) return null;
  return rounded;
};
const walletMoney = (value) => Math.round(Number(value) * 100) / 100;
const walletEmailValid = (value) => typeof value === 'string' && value.length <= 254
  && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());

async function walletPaystackRequest(path, { method = 'GET', body } = {}) {
  const secret = process.env.PAYSTACK_SECRET_KEY || '';
  if (!secret) throw Object.assign(new Error('Paystack is not configured'), { code: 'PAYSTACK_NOT_CONFIGURED' });
  const response = await fetch('https://api.paystack.co' + path, {
    method, headers: { Authorization: 'Bearer ' + secret, 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20_000)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.status !== true || !payload?.data) {
    throw Object.assign(new Error(String(payload?.message || 'Paystack request failed').slice(0,240)), { code:'PAYSTACK_REQUEST_FAILED', statusCode:response.status });
  }
  return payload.data;
}
function walletWebhookSignatureValid(rawBody, signature) {
  const secret = process.env.PAYSTACK_SECRET_KEY || '';
  if (!secret || !Buffer.isBuffer(rawBody) || typeof signature !== 'string' || !/^[a-f0-9]{128}$/i.test(signature)) return false;
  const expected = crypto.createHmac('sha512',secret).update(rawBody).digest();
  const supplied = Buffer.from(signature,'hex');
  return supplied.length === expected.length && crypto.timingSafeEqual(supplied,expected);
}
async function settleBuyerWalletFunding(reference, payment, source) {
  const cleanReference = typeof reference === 'string' ? reference.trim() : '';
  if (!cleanReference || !payment || String(payment.status || '').toLowerCase() !== 'success') return { matched:false };
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const row = (await client.query("SELECT * FROM buyer_wallet_transactions WHERE reference=$1 AND transaction_type='FUNDING' FOR UPDATE",[cleanReference])).rows[0];
    if (!row) { await client.query('ROLLBACK'); return { matched:false }; }
    if (row.status === 'SUCCEEDED') { await client.query('COMMIT'); return { matched:true,idempotent:true }; }
    if (!['PENDING','PROCESSING'].includes(String(row.status))) { await client.query('COMMIT'); return { matched:true,blocked:true }; }
    const expected = walletMoneySubunit(row.amount);
    if (expected === null || Number(payment.amount) !== expected || String(payment.currency || '').toUpperCase() !== 'NGN'
      || String(row.currency).toUpperCase() !== 'NGN' || (payment.reference && String(payment.reference) !== cleanReference)) {
      await client.query("UPDATE buyer_wallet_transactions SET status='BLOCKED',description=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','PROCESSING')",
        ['Payment confirmation did not match the expected wallet top-up.',JSON.stringify({walletPaymentBlocked:true,source,providerAmount:payment.amount ?? null,providerCurrency:payment.currency ?? null}),row.id]);
      await client.query('COMMIT'); return { matched:true,blocked:true };
    }
    await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING',provider_reference=COALESCE($1,provider_reference),metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','PROCESSING')",
      [payment.id == null ? null : String(payment.id),JSON.stringify({provider:'paystack',paymentSource:source,providerFee:Number.isFinite(Number(payment.fees))?Number(payment.fees)/100:0,paidAt:payment.paid_at||null}),row.id]);
    const amount=walletMoney(row.amount);
    await client.query("INSERT INTO buyer_wallet_ledger_entries (transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'PROVIDER_CLEARING','DEBIT',$4,'NGN'),($1,$2,$5,'AVAILABLE','CREDIT',$4,'NGN') ON CONFLICT(entry_key) DO NOTHING",
      [row.id,row.wallet_id,'WALLET-FUND-CLEARING-'+row.id,amount,'WALLET-FUND-AVAILABLE-'+row.id]);
    const final=await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED',description=$1,updated_at=NOW() WHERE id=$2 AND status='PROCESSING'",
      ['Wallet funding confirmed by Paystack.',row.id]);
    if(final.rowCount!==1) throw new Error('wallet funding transaction could not be finalized');
    await client.query("UPDATE buyer_wallets SET status='ACTIVE',provider='paystack',updated_at=NOW() WHERE id=$1 AND status='PENDING_PROVIDER'",[row.wallet_id]);
    await client.query('COMMIT'); return {matched:true,idempotent:false};
  } catch(error) { try{await client.query('ROLLBACK')}catch{} throw error; }
  finally { client.release(); }
}
async function rejectBuyerWalletFunding(reference,eventName,reason='') {
  if(typeof reference!=='string'||!reference.trim())return false;
  const result=await pool.query("UPDATE buyer_wallet_transactions SET status='FAILED',description=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE reference=$3 AND transaction_type='FUNDING' AND status IN ('PENDING','PROCESSING') RETURNING id",
    ['Wallet funding was rejected by the payment provider.',JSON.stringify({providerEvent:eventName,providerFailure:String(reason||eventName).slice(0,300)}),reference.trim()]);
  return result.rowCount===1;
}
async function verifyPendingBuyerWalletFunding(transaction) {
  const reference=String(transaction?.reference||'').trim();
  if(!reference||!walletFundingEnabled())return null;
  const verified=await walletPaystackRequest('/transaction/verify/'+encodeURIComponent(reference));
  if(String(verified.reference||'')!==reference)throw new Error('Paystack returned a different funding reference');
  if(String(verified.status||'').toLowerCase()==='success')return settleBuyerWalletFunding(reference,verified,'server_verification');
  return {matched:true,pending:true,providerStatus:String(verified.status||'pending')};
}


function walletPinMatches(pin, salt, storedHash) {
  if (!/^\d{6}$/.test(String(pin || '')) || !salt || !storedHash) return false;
  const calculated = crypto.scryptSync(String(pin), String(salt), 64);
  const stored = Buffer.from(String(storedHash), 'hex');
  return stored.length === calculated.length && crypto.timingSafeEqual(stored, calculated);
}

async function settleBuyerWalletWithdrawal(reference, transfer, source) {
  if (typeof reference !== 'string' || !reference.trim() || String(transfer?.status || '').toLowerCase() !== 'success') return { matched:false };
  const client=await pool.connect();
  try {
    await client.query('BEGIN');
    const row=(await client.query(
      "SELECT w.*,t.status AS transaction_status,t.reference AS transaction_reference FROM buyer_wallet_withdrawals w JOIN buyer_wallet_transactions t ON t.id=w.transaction_id WHERE w.reference=$1 FOR UPDATE OF w,t",
      [reference.trim()]
    )).rows[0];
    if(!row){await client.query('ROLLBACK');return {matched:false};}
    if(row.status==='SUCCEEDED'){await client.query('COMMIT');return {matched:true,idempotent:true};}
    if(!['PENDING','PROCESSING','PENDING_OTP'].includes(String(row.status))){await client.query('COMMIT');return {matched:true,blocked:true};}
    const expected=walletMoneySubunit(row.amount);
    if(expected===null||Number(transfer.amount)!==expected||String(transfer.currency||'').toUpperCase()!=='NGN'
      ||String(transfer.reference||'')!==String(row.reference)){
      await client.query("UPDATE buyer_wallet_withdrawals SET status='BLOCKED',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2",
        [JSON.stringify({providerMismatch:true,source,providerAmount:transfer.amount??null,providerCurrency:transfer.currency??null}),row.id]);
      await client.query("UPDATE buyer_wallet_transactions SET status='BLOCKED',description=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','PROCESSING')",
        ['Withdrawal confirmation did not match the requested amount.',JSON.stringify({providerMismatch:true}),row.transaction_id]);
      await client.query('COMMIT');return {matched:true,blocked:true};
    }
    await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING',provider_reference=COALESCE($1,provider_reference),updated_at=NOW() WHERE id=$2 AND status IN ('PENDING','PROCESSING')",
      [transfer.id==null?null:String(transfer.id),row.transaction_id]);
    const amount=walletMoney(row.amount),fee=walletMoney(row.fee);
    const values=[row.transaction_id,row.wallet_id,'WALLET-WITHDRAW-AVAILABLE-'+row.id,amount+fee,'WALLET-WITHDRAW-BANK-'+row.id,amount];
    if(fee>0){
      values.push('WALLET-WITHDRAW-FEE-'+row.id,fee);
      await client.query("INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES($1,$2,$3,'AVAILABLE','DEBIT',$4,'NGN'),($1,$2,$5,'EXTERNAL_BANK','CREDIT',$6,'NGN'),($1,$2,$7,'FYNX_FEES','CREDIT',$8,'NGN') ON CONFLICT(entry_key) DO NOTHING",values);
    }else{
      await client.query("INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES($1,$2,$3,'AVAILABLE','DEBIT',$4,'NGN'),($1,$2,$5,'EXTERNAL_BANK','CREDIT',$6,'NGN') ON CONFLICT(entry_key) DO NOTHING",values.slice(0,6));
    }
    await client.query("UPDATE buyer_wallet_withdrawals SET status='SUCCEEDED',provider_reference=COALESCE($1,provider_reference),metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3",
      [transfer.id==null?null:String(transfer.id),JSON.stringify({confirmedBy:source,paidAt:transfer.transferred_at||transfer.updated_at||null}),row.id]);
    await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED',description=$1,updated_at=NOW() WHERE id=$2 AND status='PROCESSING'",
      ['Wallet withdrawal paid to verified bank account.',row.transaction_id]);
    await client.query('COMMIT');return {matched:true,idempotent:false};
  }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
}

async function failBuyerWalletWithdrawal(reference,eventName,reason='') {
  if(typeof reference!=='string'||!reference.trim())return false;
  const client=await pool.connect();
  try{
    await client.query('BEGIN');
    const row=(await client.query("SELECT w.id,w.transaction_id,w.status FROM buyer_wallet_withdrawals w WHERE w.reference=$1 FOR UPDATE",[reference.trim()])).rows[0];
    if(!row){await client.query('ROLLBACK');return false;}
    if(['SUCCEEDED','REVERSED','FAILED'].includes(String(row.status))){await client.query('COMMIT');return true;}
    await client.query("UPDATE buyer_wallet_withdrawals SET status='FAILED',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2",
      [JSON.stringify({providerEvent:eventName,providerFailure:String(reason||eventName).slice(0,300)}),row.id]);
    await client.query("UPDATE buyer_wallet_transactions SET status='FAILED',description=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','PROCESSING')",
      ['Bank payout was not completed. Funds remain in your FYNX Wallet.',JSON.stringify({providerEvent:eventName}),row.transaction_id]);
    await client.query('COMMIT');return true;
  }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
}

async function reverseBuyerWalletWithdrawal(reference,transfer,source) {
  const client=await pool.connect();
  try{
    await client.query('BEGIN');
    const row=(await client.query("SELECT w.*,t.status AS transaction_status FROM buyer_wallet_withdrawals w JOIN buyer_wallet_transactions t ON t.id=w.transaction_id WHERE w.reference=$1 FOR UPDATE OF w,t",[reference])).rows[0];
    if(!row){await client.query('ROLLBACK');return {matched:false};}
    if(row.status==='REVERSED'){await client.query('COMMIT');return {matched:true,idempotent:true};}
    if(row.status!=='SUCCEEDED'){await client.query('COMMIT');return {matched:true,notSettled:true};}
    const amount=walletMoney(row.amount),fee=walletMoney(row.fee),total=walletMoney(amount+fee);
    const reversalId=crypto.randomUUID();
    const reversalRef='fynxwltrev-'+crypto.randomUUID().replace(/-/g,'');
    const idem='WALLET-WITHDRAW-REVERSAL-'+row.id;
    const reversal=(await client.query("INSERT INTO buyer_wallet_transactions(id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata) VALUES($1,$2,$3,'REVERSAL','PENDING',$4,0,'NGN',$5,$6,$7,$8::jsonb) ON CONFLICT(idempotency_key) DO UPDATE SET updated_at=NOW() RETURNING id,status",
      [reversalId,row.wallet_id,row.user_id,total,reversalRef,idem,'Bank reversed a completed FYNX Wallet withdrawal.',JSON.stringify({originalWithdrawalReference:reference,source})])).rows[0];
    if(reversal.status==='PENDING'){
      await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1",[reversal.id]);
      const entries=[
        [reversal.id,row.wallet_id,'WALLET-WITHDRAW-REV-BANK-'+row.id,'EXTERNAL_BANK','DEBIT',amount],
        ...(fee>0?[[reversal.id,row.wallet_id,'WALLET-WITHDRAW-REV-FEE-'+row.id,'FYNX_FEES','DEBIT',fee]]:[]),
        [reversal.id,row.wallet_id,'WALLET-WITHDRAW-REV-AVAILABLE-'+row.id,'AVAILABLE','CREDIT',total]
      ];
      for(const entry of entries) await client.query("INSERT INTO buyer_wallet_ledger_entries(transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES($1,$2,$3,$4,$5,$6,'NGN') ON CONFLICT(entry_key) DO NOTHING",entry);
      await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED',updated_at=NOW() WHERE id=$1 AND status='PROCESSING'",[reversal.id]);
    }
    await client.query("UPDATE buyer_wallet_withdrawals SET status='REVERSED',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2",
      [JSON.stringify({providerEvent:source,providerTransferId:transfer?.id??null}),row.id]);
    await client.query('COMMIT');return {matched:true,idempotent:false};
  }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
}

export function registerBuyerWalletRoutes({ app }) {
  // Buyer wallet foundation is intentionally read-only until the production
  // wallet provider, custody terms and withdrawal flow have been approved.
  app.get('/api/wallet/summary', auth, async (req, res) => {
    try {
      await ensureBuyerWalletSchema();
      res.set('Cache-Control', 'no-store, private');
      const wallet = (await pool.query(
        'SELECT id,status,provider,currency,created_at,updated_at FROM buyer_wallets WHERE user_id=$1 AND currency=$2 LIMIT 1',
        [req.user.sub, 'NGN']
      )).rows[0];
      if (!wallet) {
        return res.json({
          walletStatus: 'NOT_SETUP',
          currency: 'NGN',
          availableBalance: null,
          reservedBalance: null,
          withdrawalPendingBalance: null,
          canFund: walletFundingEnabled(),
          canSpend: false,
          canWithdraw: walletWithdrawalsEnabled(),
          withdrawalFee: Number(process.env.FYNX_WALLET_WITHDRAWAL_FEE_NGN || 0),
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
      const pendingWithdrawalRow = await pool.query(
        "SELECT COALESCE(SUM(amount+fee),0)::numeric(18,2) AS amount FROM buyer_wallet_withdrawals WHERE wallet_id=$1 AND status IN ('PENDING','PROCESSING','PENDING_OTP')",
        [wallet.id]
      );
      const pendingWithdrawals = Number(pendingWithdrawalRow.rows[0]?.amount || 0);
      return res.json({
        walletStatus: String(wallet.status || 'PENDING_PROVIDER'),
        currency: String(wallet.currency || 'NGN'),
        availableBalance: Math.max(0, Number(balances.AVAILABLE || 0) - pendingWithdrawals),
        reservedBalance: balances.RESERVED || 0,
        withdrawalPendingBalance: Number(balances.WITHDRAWAL_PENDING || 0) + pendingWithdrawals,
        canFund: walletFundingEnabled() && !['RESTRICTED','CLOSED'].includes(String(wallet.status)),
        canSpend: false,
        canWithdraw: walletWithdrawalsEnabled() && String(wallet.status) === 'ACTIVE',
        withdrawalFee: Number(process.env.FYNX_WALLET_WITHDRAWAL_FEE_NGN || 0),
        notice: walletFundingEnabled() || walletWithdrawalsEnabled() ? 'Your Wallet is connected to its approved payment services. Only actions enabled for this account are available.' : 'Wallet money movement remains locked until provider, custody and webhook approvals are configured.',
        updatedAt: wallet.updated_at
      });
    } catch (error) {
      console.error('buyer wallet summary', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'wallet summary is temporarily unavailable' });
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
        SELECT t.id,t.transaction_type,t.status,t.amount,t.fee,t.currency,t.reference,t.description,t.created_at,
               to_char(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS created_at_iso,
               COALESCE(activity_delta.wallet_delta,0)::numeric(18,2) AS wallet_delta
          FROM buyer_wallet_transactions t
          LEFT JOIN LATERAL (
            SELECT SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END) AS wallet_delta
              FROM buyer_wallet_ledger_entries e
             WHERE e.transaction_id=t.id AND e.account_code='AVAILABLE'
          ) activity_delta ON TRUE
         WHERE t.user_id=$1 AND t.wallet_id=$2
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


  app.post('/api/wallet/funding/transfer',auth,async(req,res)=>{
    const amount=walletMoney(Number(req.body?.amount)), amountSubunit=walletMoneySubunit(amount);
    const email=typeof req.body?.email==='string'?req.body.email.trim().toLowerCase():'';
    const key=String(req.get('idempotency-key')||'').trim();
    if(!walletFundingEnabled())return res.status(503).json({error:'wallet bank-transfer funding is not enabled for this FYNX environment',code:'WALLET_PROVIDER_NOT_READY'});
    if(!amountSubunit||amount<100||amount>Number(process.env.FYNX_WALLET_MAX_FUNDING_NGN||5000000))return res.status(400).json({error:'enter a valid NGN funding amount within the wallet limits'});
    if(!walletEmailValid(email))return res.status(400).json({error:'a valid email address is required by the payment provider'});
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(key))return res.status(400).json({error:'a valid Idempotency-Key header is required'});
    let tx;
    try{
      await ensureBuyerWalletSchema();
      const client=await pool.connect();
      try{
        await client.query('BEGIN');
        const wallet=(await client.query("INSERT INTO buyer_wallets(id,user_id,currency,status,provider) VALUES($1,$2,'NGN','ACTIVE','paystack') ON CONFLICT(user_id,currency) DO UPDATE SET updated_at=NOW() RETURNING id,status",[crypto.randomUUID(),req.user.sub])).rows[0];
        if(!wallet||!['ACTIVE','PENDING_PROVIDER'].includes(String(wallet.status))){await client.query('ROLLBACK');return res.status(403).json({error:'this wallet is restricted and cannot receive funding'});}
        const existing=(await client.query("SELECT * FROM buyer_wallet_transactions WHERE user_id=$1 AND idempotency_key=$2",[req.user.sub,key])).rows[0];
        if(existing){
          if(String(existing.transaction_type)!=='FUNDING'||walletMoney(existing.amount)!==amount){await client.query('ROLLBACK');return res.status(409).json({error:'this idempotency key was already used for a different wallet operation'});}
          await client.query('COMMIT');
          return res.status(200).json({transaction:{id:String(existing.id),reference:existing.reference,status:existing.status,amount:Number(existing.amount),currency:existing.currency},bankTransfer:existing.metadata?.bankTransfer||null,idempotent:true});
        }
        const reference='fynxwlt-'+crypto.randomUUID().replace(/-/g,'');
        tx=(await client.query("INSERT INTO buyer_wallet_transactions(id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata) VALUES($1,$2,$3,'FUNDING','PENDING',$4,0,'NGN',$5,$6,'Wallet top-up by bank transfer',$7::jsonb) RETURNING *",
          [crypto.randomUUID(),wallet.id,req.user.sub,amount,reference,key,JSON.stringify({provider:'paystack',walletFlow:'FUNDING_TRANSFER',emailForProvider:email})])).rows[0];
        await client.query('COMMIT');
      }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
      const expiresAt=new Date(Date.now()+2*60*60*1000).toISOString();
      let providerData;
      try{
        providerData=await walletPaystackRequest('/charge',{method:'POST',body:{email,amount:String(amountSubunit),currency:'NGN',reference:tx.reference,bank_transfer:{account_expires_at:expiresAt},metadata:{fynxWalletFlow:'FUNDING_TRANSFER',walletTransactionId:String(tx.id),walletUserId:String(req.user.sub)}}});
      }catch(providerError){
        const ambiguous=providerError?.name==='TimeoutError'||providerError?.name==='AbortError'||providerError?.name==='TypeError';
        await pool.query("UPDATE buyer_wallet_transactions SET metadata=metadata || $1::jsonb,description=$2,updated_at=NOW() WHERE id=$3 AND status='PENDING'",
          [JSON.stringify({providerInitiationUncertain:Boolean(ambiguous),providerError:String(providerError?.message||'provider request failed').slice(0,240)}),ambiguous?'Funding request submitted; awaiting provider status confirmation.':'Funding request could not be initialized.',tx.id]);
        if(!ambiguous)await pool.query("UPDATE buyer_wallet_transactions SET status='FAILED',updated_at=NOW() WHERE id=$1 AND status='PENDING'",[tx.id]);
        return res.status(ambiguous?202:502).json({error:ambiguous?'provider response is delayed; check this transaction before trying again':'payment provider could not start this bank transfer',reference:tx.reference,status:ambiguous?'PENDING':'FAILED'});
      }
      const providerReference=String(providerData.reference||''), accountNumber=String(providerData.account_number||'');
      const bankName=String(providerData.bank?.name||providerData.bank?.bank_name||''), accountName=String(providerData.account_name||'');
      if(providerReference!==tx.reference||!/^\d{8,12}$/.test(accountNumber)||!bankName||!accountName||String(providerData.status||'').toLowerCase()!=='pending_bank_transfer'){
        await pool.query("UPDATE buyer_wallet_transactions SET status='BLOCKED',description=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'",
          ['Payment provider did not return a valid transfer account.',JSON.stringify({providerResponseStatus:providerData.status||null,providerReferenceMatched:providerReference===tx.reference}),tx.id]);
        return res.status(502).json({error:'payment provider did not return valid bank-transfer instructions',reference:tx.reference});
      }
      const bankTransfer={bankName,accountName,accountNumber,reference:tx.reference,amount,currency:'NGN',accountExpiresAt:providerData.account_expires_at||expiresAt,provider:'paystack'};
      await pool.query("UPDATE buyer_wallet_transactions SET provider_reference=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'",
        [tx.reference,JSON.stringify({bankTransfer}),tx.id]);
      return res.status(201).json({transaction:{id:String(tx.id),reference:tx.reference,status:'PENDING',amount,currency:'NGN'},bankTransfer,accountType:'TRANSACTION_SPECIFIC_TEMPORARY',message:'Transfer the exact amount to this temporary account before expiry. FYNX credits your Wallet only after verified provider confirmation.'});
    }catch(error){console.error('buyer wallet funding transfer',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'wallet funding could not be started'});}
  });

  app.post('/api/wallet/funding/checkout',auth,async(req,res)=>{
    const amount=walletMoney(Number(req.body?.amount)),amountSubunit=walletMoneySubunit(amount);
    const email=typeof req.body?.email==='string'?req.body.email.trim().toLowerCase():'';
    const method=String(req.body?.method||'').trim().toLowerCase(),key=String(req.get('idempotency-key')||'').trim();
    if(!walletFundingEnabled())return res.status(503).json({error:'wallet checkout funding is not enabled for this FYNX environment',code:'WALLET_PROVIDER_NOT_READY'});
    if(!amountSubunit||amount<100||amount>Number(process.env.FYNX_WALLET_MAX_FUNDING_NGN||5000000))return res.status(400).json({error:'enter a valid NGN funding amount within the wallet limits'});
    if(!walletEmailValid(email))return res.status(400).json({error:'a valid email address is required by the payment provider'});
    if(!['card','ussd'].includes(method))return res.status(400).json({error:'supported checkout method is card or ussd'});
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(key))return res.status(400).json({error:'a valid Idempotency-Key header is required'});
    let tx;
    try{
      await ensureBuyerWalletSchema();
      const client=await pool.connect();
      try{
        await client.query('BEGIN');
        const wallet=(await client.query("INSERT INTO buyer_wallets(id,user_id,currency,status,provider) VALUES($1,$2,'NGN','ACTIVE','paystack') ON CONFLICT(user_id,currency) DO UPDATE SET updated_at=NOW() RETURNING id,status",[crypto.randomUUID(),req.user.sub])).rows[0];
        if(!wallet||!['ACTIVE','PENDING_PROVIDER'].includes(String(wallet.status))){await client.query('ROLLBACK');return res.status(403).json({error:'this wallet is restricted and cannot receive funding'});}
        const existing=(await client.query("SELECT * FROM buyer_wallet_transactions WHERE user_id=$1 AND idempotency_key=$2",[req.user.sub,key])).rows[0];
        if(existing){
          if(String(existing.transaction_type)!=='FUNDING'||walletMoney(existing.amount)!==amount){await client.query('ROLLBACK');return res.status(409).json({error:'this idempotency key was already used for a different wallet operation'});}
          await client.query('COMMIT');
          return res.json({transaction:{id:String(existing.id),reference:existing.reference,status:existing.status,amount:Number(existing.amount),currency:existing.currency},checkoutUrl:existing.metadata?.checkoutUrl||null,idempotent:true});
        }
        const reference='fynxwlt-'+crypto.randomUUID().replace(/-/g,'');
        tx=(await client.query("INSERT INTO buyer_wallet_transactions(id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata) VALUES($1,$2,$3,'FUNDING','PENDING',$4,0,'NGN',$5,$6,$7,$8::jsonb) RETURNING *",
          [crypto.randomUUID(),wallet.id,req.user.sub,amount,reference,key,'Wallet top-up by '+method,JSON.stringify({provider:'paystack',walletFlow:'FUNDING_CHECKOUT',paymentMethod:method})])).rows[0];
        await client.query('COMMIT');
      }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
      const data=await walletPaystackRequest('/transaction/initialize',{method:'POST',body:{email,amount:String(amountSubunit),currency:'NGN',reference:tx.reference,channels:[method],metadata:{fynxWalletFlow:'FUNDING_CHECKOUT',walletTransactionId:String(tx.id),walletUserId:String(req.user.sub),paymentMethod:method}}});
      const checkoutUrl=String(data.authorization_url||'');
      let validCheckoutHost=false;
      try { const parsedCheckoutUrl=new URL(checkoutUrl); validCheckoutHost=parsedCheckoutUrl.protocol==='https:' && parsedCheckoutUrl.hostname==='checkout.paystack.com'; } catch {}
      if(String(data.reference||'')!==tx.reference||!validCheckoutHost){
        await pool.query("UPDATE buyer_wallet_transactions SET status='BLOCKED',description=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'",['Payment provider returned invalid checkout details.',tx.id]);
        return res.status(502).json({error:'payment provider returned invalid checkout details'});
      }
      await pool.query("UPDATE buyer_wallet_transactions SET provider_reference=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'",[tx.reference,JSON.stringify({checkoutUrl}),tx.id]);
      return res.status(201).json({transaction:{id:String(tx.id),reference:tx.reference,status:'PENDING',amount,currency:'NGN'},checkoutUrl,method});
    }catch(error){
      console.error('buyer wallet checkout initialization',error);
      const ambiguous=error?.name==='TimeoutError'||error?.name==='AbortError'||error?.name==='TypeError';
      if(tx?.id) {
        await pool.query("UPDATE buyer_wallet_transactions SET metadata=metadata || $1::jsonb,description=$2,updated_at=NOW() WHERE id=$3 AND status='PENDING'",
          [JSON.stringify({providerError:String(error?.message||'checkout initialization failed').slice(0,240),providerInitiationUncertain:Boolean(ambiguous)}),
           ambiguous?'Checkout initialization response is delayed; verify this reference before retrying.':'Payment checkout could not be initialized.',tx.id]).catch(()=>{});
        if(!ambiguous) await pool.query("UPDATE buyer_wallet_transactions SET status='FAILED',updated_at=NOW() WHERE id=$1 AND status='PENDING'",[tx.id]).catch(()=>{});
      }
      if(error?.code==='DATABASE_NOT_CONFIGURED')return res.status(503).json({error:'wallet database is not configured'});
      return res.status(ambiguous?202:502).json({
        error:ambiguous?'provider response is delayed; check this reference before trying again':'payment checkout could not be initialized',
        ...(tx?.reference?{reference:tx.reference,status:ambiguous?'PENDING':'FAILED',amount:Number(tx.amount),currency:'NGN'}:{})
      });
    }
  });

  app.get('/api/wallet/funding/:reference',auth,async(req,res)=>{
    const reference=typeof req.params?.reference==='string'?req.params.reference.trim():'';
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(reference))return res.status(400).json({error:'invalid wallet funding reference'});
    try{
      await ensureBuyerWalletSchema();
      const tx=(await pool.query("SELECT id,reference,status,amount,fee,currency,description,metadata,created_at,updated_at FROM buyer_wallet_transactions WHERE user_id=$1 AND reference=$2 AND transaction_type='FUNDING' LIMIT 1",[req.user.sub,reference])).rows[0];
      if(!tx)return res.status(404).json({error:'wallet funding transaction not found'});
      if(tx.status==='PENDING'&&walletFundingEnabled()){
        try{const verified=await verifyPendingBuyerWalletFunding(tx);if(verified?.matched){const fresh=(await pool.query("SELECT status,metadata,updated_at FROM buyer_wallet_transactions WHERE id=$1",[tx.id])).rows[0];tx.status=fresh?.status||tx.status;tx.metadata=fresh?.metadata||tx.metadata;tx.updated_at=fresh?.updated_at||tx.updated_at;}}catch(error){console.warn('buyer wallet funding status verification',error?.message||error);}
      }
      return res.set('Cache-Control','no-store, private').json({
        transaction:{id:String(tx.id),reference:tx.reference,status:tx.status,amount:Number(tx.amount),fee:Number(tx.fee),currency:tx.currency,description:tx.description,createdAt:tx.created_at,updatedAt:tx.updated_at},
        bankTransfer:tx.metadata?.bankTransfer||null,checkoutUrl:tx.metadata?.checkoutUrl||null
      });
    }catch(error){console.error('buyer wallet funding status',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'wallet funding status is unavailable'});}
  });


  app.get('/api/wallet/security',auth,async(req,res)=>{
    try{
      await ensureBuyerWalletSchema();
      const row=(await pool.query("SELECT 1 FROM buyer_wallet_security s JOIN buyer_wallets w ON w.id=s.wallet_id WHERE s.user_id=$1 AND w.currency='NGN' LIMIT 1",[req.user.sub])).rows[0];
      return res.set('Cache-Control','no-store, private').json({pinConfigured:Boolean(row)});
    }catch(error){return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'wallet security status is unavailable'});}
  });

  app.post('/api/wallet/security/pin',auth,async(req,res)=>{
    const pin=String(req.body?.pin||''),confirm=String(req.body?.confirmPin||''),currentPin=String(req.body?.currentPin||'');
    if(!/^\d{6}$/.test(pin)||pin!==confirm)return res.status(400).json({error:'enter and confirm the same six-digit Wallet PIN'});
    try{
      await ensureBuyerWalletSchema();
      const client=await pool.connect();
      try{
        await client.query('BEGIN');
        const wallet=(await client.query("INSERT INTO buyer_wallets(id,user_id,currency,status,provider) VALUES($1,$2,'NGN','PENDING_PROVIDER','PENDING_SELECTION') ON CONFLICT(user_id,currency) DO UPDATE SET updated_at=NOW() RETURNING id,status",[crypto.randomUUID(),req.user.sub])).rows[0];
        if(!wallet||['RESTRICTED','CLOSED'].includes(String(wallet.status))){await client.query('ROLLBACK');return res.status(403).json({error:'this wallet cannot change its security settings'});}
        const existing=(await client.query("SELECT * FROM buyer_wallet_security WHERE wallet_id=$1 AND user_id=$2 FOR UPDATE",[wallet.id,req.user.sub])).rows[0];
        if(existing){
          if(existing.locked_until&&new Date(existing.locked_until).getTime()>Date.now()){await client.query('ROLLBACK');return res.status(429).json({error:'Wallet PIN is temporarily locked. Try again later.'});}
          if(!walletPinMatches(currentPin,existing.pin_salt,existing.pin_hash)){
            const attempts=Number(existing.failed_attempts||0)+1;
            await client.query("UPDATE buyer_wallet_security SET failed_attempts=$1,locked_until=CASE WHEN $1>=5 THEN NOW()+INTERVAL '30 minutes' ELSE NULL END,updated_at=NOW() WHERE wallet_id=$2",[attempts,wallet.id]);
            await client.query('COMMIT');
            return res.status(attempts>=5?429:401).json({error:attempts>=5?'Too many incorrect PIN attempts. Try again in 30 minutes.':'Current Wallet PIN is incorrect.'});
          }
        }
        const salt=crypto.randomBytes(16).toString('hex'),hash=crypto.scryptSync(pin,salt,64).toString('hex');
        await client.query("INSERT INTO buyer_wallet_security(wallet_id,user_id,pin_salt,pin_hash,failed_attempts,locked_until,updated_at) VALUES($1,$2,$3,$4,0,NULL,NOW()) ON CONFLICT(wallet_id) DO UPDATE SET pin_salt=EXCLUDED.pin_salt,pin_hash=EXCLUDED.pin_hash,failed_attempts=0,locked_until=NULL,updated_at=NOW()",
          [wallet.id,req.user.sub,salt,hash]);
        await client.query('COMMIT');
        return res.json({success:true,pinConfigured:true});
      }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
    }catch(error){console.error('wallet PIN setup',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'Wallet PIN could not be saved'});}
  });

  app.get('/api/wallet/banks',auth,async(req,res)=>{
    if(!walletWithdrawalsEnabled())return res.status(503).json({error:'bank payouts are not enabled for this FYNX environment',code:'WALLET_PAYOUTS_NOT_READY'});
    try{
      const data=await walletPaystackRequest('/bank?country=nigeria&perPage=200');
      const banks=Array.isArray(data)?data.filter(bank=>bank&&bank.code&&bank.name).map(bank=>({code:String(bank.code),name:String(bank.name)})):[];
      return res.set('Cache-Control','no-store, private').json({banks});
    }catch(error){console.error('wallet bank list',error);return res.status(502).json({error:'supported banks could not be loaded'});}
  });

  app.post('/api/wallet/banks/resolve',auth,async(req,res)=>{
    const accountNumber=String(req.body?.accountNumber||'').trim(),bankCode=String(req.body?.bankCode||'').trim();
    if(!walletWithdrawalsEnabled())return res.status(503).json({error:'bank account verification is not enabled for this FYNX environment'});
    if(!/^\d{10}$/.test(accountNumber)||!/^[A-Za-z0-9]{2,12}$/.test(bankCode))return res.status(400).json({error:'enter a valid Nigerian 10-digit account number and bank'});
    try{
      const data=await walletPaystackRequest('/bank/resolve?account_number='+encodeURIComponent(accountNumber)+'&bank_code='+encodeURIComponent(bankCode));
      const accountName=String(data.account_name||'').trim(),resolvedNumber=String(data.account_number||'').trim();
      if(!accountName||resolvedNumber!==accountNumber)return res.status(422).json({error:'the bank account could not be verified'});
      return res.set('Cache-Control','no-store, private').json({verified:true,accountName,accountNumber:resolvedNumber,bankCode});
    }catch(error){console.warn('wallet bank account resolution',error?.message||error);return res.status(422).json({error:'the bank could not verify this account number'});}
  });

  app.post('/api/wallet/payout-accounts',auth,async(req,res)=>{
    const accountNumber=String(req.body?.accountNumber||'').trim(),bankCode=String(req.body?.bankCode||'').trim();
    if(!walletWithdrawalsEnabled())return res.status(503).json({error:'bank payouts are not enabled for this FYNX environment'});
    if(!/^\d{10}$/.test(accountNumber)||!/^[A-Za-z0-9]{2,12}$/.test(bankCode))return res.status(400).json({error:'enter a valid Nigerian bank account'});
    try{
      await ensureBuyerWalletSchema();
      const client=await pool.connect();
      let wallet;
      try{
        await client.query('BEGIN');
        wallet=(await client.query("INSERT INTO buyer_wallets(id,user_id,currency,status,provider) VALUES($1,$2,'NGN','PENDING_PROVIDER','PENDING_SELECTION') ON CONFLICT(user_id,currency) DO UPDATE SET updated_at=NOW() RETURNING id,status",[crypto.randomUUID(),req.user.sub])).rows[0];
        if(['RESTRICTED','CLOSED'].includes(String(wallet.status))){await client.query('ROLLBACK');return res.status(403).json({error:'this wallet cannot add a payout account'});}
        await client.query('COMMIT');
      }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
      const resolved=await walletPaystackRequest('/bank/resolve?account_number='+encodeURIComponent(accountNumber)+'&bank_code='+encodeURIComponent(bankCode));
      const accountName=String(resolved.account_name||'').trim();
      if(!accountName||String(resolved.account_number||'')!==accountNumber)return res.status(422).json({error:'the bank account could not be verified'});
      const recipient=await walletPaystackRequest('/transferrecipient',{method:'POST',body:{type:'nuban',name:accountName,account_number:accountNumber,bank_code:bankCode,currency:'NGN',description:'FYNX Wallet verified payout destination'}});
      const recipientCode=String(recipient.recipient_code||'');
      if(!/^RCP_[A-Za-z0-9]+$/.test(recipientCode))return res.status(502).json({error:'payment provider did not return a valid payout recipient'});
      const bankName=String(recipient.details?.bank_name||recipient.details?.bank||'').trim()||String(req.body?.bankName||'').trim();
      if(!bankName)return res.status(502).json({error:'payment provider did not confirm the payout bank name'});
      const account=(await pool.query(
        "INSERT INTO buyer_wallet_payout_accounts(id,wallet_id,user_id,provider,recipient_code,bank_code,bank_name,account_name,account_last4,currency,verified,active) VALUES($1,$2,$3,'paystack',$4,$5,$6,$7,$8,'NGN',TRUE,TRUE) ON CONFLICT(recipient_code) DO UPDATE SET bank_name=EXCLUDED.bank_name,account_name=EXCLUDED.account_name,active=TRUE,verified=TRUE,updated_at=NOW() RETURNING id,bank_name,account_name,account_last4,verified",
        [crypto.randomUUID(),wallet.id,req.user.sub,recipientCode,bankCode,bankName,accountName,accountNumber.slice(-4)]
      )).rows[0];
      return res.status(201).json({payoutAccount:{id:String(account.id),bankName:account.bank_name,accountName:account.account_name,accountLast4:account.account_last4,verified:Boolean(account.verified)}});
    }catch(error){console.error('wallet payout account setup',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:502).json({error:'payout account could not be verified and saved'});}
  });

  app.get('/api/wallet/payout-accounts',auth,async(req,res)=>{
    try{
      await ensureBuyerWalletSchema();
      const rows=await pool.query("SELECT id,bank_name,account_name,account_last4,verified,updated_at FROM buyer_wallet_payout_accounts WHERE user_id=$1 AND active=TRUE ORDER BY updated_at DESC",[req.user.sub]);
      return res.set('Cache-Control','no-store, private').json({payoutAccounts:rows.rows.map(row=>({id:String(row.id),bankName:row.bank_name,accountName:row.account_name,accountLast4:row.account_last4,verified:Boolean(row.verified),updatedAt:row.updated_at}))});
    }catch(error){return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'saved bank accounts are unavailable'});}
  });

  app.post('/api/wallet/withdrawals',auth,async(req,res)=>{
    const amount=walletMoney(Number(req.body?.amount)),amountSubunit=walletMoneySubunit(amount);
    const payoutAccountId=String(req.body?.payoutAccountId||'').trim(),pin=String(req.body?.pin||'');
    const key=String(req.get('idempotency-key')||'').trim();
    if(!walletWithdrawalsEnabled())return res.status(503).json({error:'wallet withdrawals require provider, custody, KYC and transfer approvals',code:'WALLET_PAYOUTS_NOT_READY'});
    if(!amountSubunit||amount<100||amount>Number(process.env.FYNX_WALLET_MAX_WITHDRAWAL_NGN||1000000))return res.status(400).json({error:'enter a valid withdrawal amount within the wallet limits'});
    if(!/^[0-9a-f-]{36}$/i.test(payoutAccountId))return res.status(400).json({error:'select a verified bank account'});
    if(!/^\d{6}$/.test(pin))return res.status(400).json({error:'enter your six-digit Wallet PIN'});
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(key))return res.status(400).json({error:'a valid Idempotency-Key header is required'});
    let withdrawal;
    try{
      await ensureBuyerWalletSchema();
      const client=await pool.connect();
      try{
        await client.query('BEGIN');
        const wallet=(await client.query("SELECT id,status FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' FOR UPDATE",[req.user.sub])).rows[0];
        if(!wallet||wallet.status!=='ACTIVE'){await client.query('ROLLBACK');return res.status(409).json({error:'your Wallet must be active before you can withdraw'});}
        const existing=(await client.query("SELECT w.*,t.status AS transaction_status FROM buyer_wallet_withdrawals w JOIN buyer_wallet_transactions t ON t.id=w.transaction_id WHERE w.user_id=$1 AND w.idempotency_key=$2",[req.user.sub,key])).rows[0];
        if(existing){
          if(walletMoney(existing.amount)!==amount){await client.query('ROLLBACK');return res.status(409).json({error:'this idempotency key was used for a different withdrawal'});}
          await client.query('COMMIT');
          return res.status(200).json({withdrawal:{id:String(existing.id),reference:existing.reference,status:existing.status,transactionStatus:existing.transaction_status,amount:Number(existing.amount),fee:Number(existing.fee),currency:existing.currency},idempotent:true});
        }
        const account=(await client.query("SELECT id,recipient_code,bank_name,account_name,account_last4,verified FROM buyer_wallet_payout_accounts WHERE id=$1 AND user_id=$2 AND wallet_id=$3 AND active=TRUE FOR UPDATE",[payoutAccountId,req.user.sub,wallet.id])).rows[0];
        if(!account||!account.verified){await client.query('ROLLBACK');return res.status(403).json({error:'select a provider-verified payout account'});}
        const security=(await client.query("SELECT pin_salt,pin_hash,failed_attempts,locked_until FROM buyer_wallet_security WHERE wallet_id=$1 AND user_id=$2 FOR UPDATE",[wallet.id,req.user.sub])).rows[0];
        if(!security){await client.query('ROLLBACK');return res.status(403).json({error:'set up your Wallet PIN before withdrawing'});}
        if(security.locked_until&&new Date(security.locked_until).getTime()>Date.now()){await client.query('ROLLBACK');return res.status(429).json({error:'Wallet PIN is temporarily locked. Try again later.'});}
        if(!walletPinMatches(pin,security.pin_salt,security.pin_hash)){
          const attempts=Number(security.failed_attempts||0)+1;
          await client.query("UPDATE buyer_wallet_security SET failed_attempts=$1,locked_until=CASE WHEN $1>=5 THEN NOW()+INTERVAL '30 minutes' ELSE NULL END,updated_at=NOW() WHERE wallet_id=$2",[attempts,wallet.id]);
          await client.query('COMMIT');
          return res.status(attempts>=5?429:401).json({error:attempts>=5?'Too many incorrect PIN attempts. Try again in 30 minutes.':'Wallet PIN is incorrect.'});
        }
        await client.query("UPDATE buyer_wallet_security SET failed_attempts=0,locked_until=NULL,updated_at=NOW() WHERE wallet_id=$1",[wallet.id]);
        const fee=walletMoney(Number(process.env.FYNX_WALLET_WITHDRAWAL_FEE_NGN||0));
        if(!Number.isFinite(fee)||fee<0||fee>Number(process.env.FYNX_WALLET_MAX_WITHDRAWAL_FEE_NGN||1000)){await client.query('ROLLBACK');return res.status(503).json({error:'wallet withdrawal fee configuration is invalid'});}
        const ledger=(await client.query(
          "SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS available FROM buyer_wallet_ledger_entries e JOIN buyer_wallet_transactions t ON t.id=e.transaction_id WHERE e.wallet_id=$1 AND e.account_code='AVAILABLE' AND t.status='SUCCEEDED'",
          [wallet.id]
        )).rows[0];
        const held=(await client.query("SELECT COALESCE(SUM(amount+fee),0)::numeric(18,2) AS amount FROM buyer_wallet_withdrawals WHERE wallet_id=$1 AND status IN ('PENDING','PROCESSING','PENDING_OTP')",[wallet.id])).rows[0];
        const available=walletMoney(Number(ledger.available||0)-Number(held.amount||0));
        if(available<amount+fee){await client.query('ROLLBACK');return res.status(409).json({error:'available Wallet balance is not enough for this withdrawal',availableBalance:Math.max(0,available),fee});}
        const txId=crypto.randomUUID(),withdrawalId=crypto.randomUUID(),reference='fynxwalletwd-'+crypto.randomUUID().replace(/-/g,'');
        const tx=(await client.query(
          "INSERT INTO buyer_wallet_transactions(id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata) VALUES($1,$2,$3,'WITHDRAWAL','PENDING',$4,$5,'NGN',$6,$7,'Wallet withdrawal request',$8::jsonb) RETURNING id",
          [txId,wallet.id,req.user.sub,amount,fee,reference,key,JSON.stringify({provider:'paystack',walletFlow:'WITHDRAWAL',payoutAccountId,bankName:account.bank_name,accountLast4:account.account_last4})]
        )).rows[0];
        withdrawal=(await client.query(
          "INSERT INTO buyer_wallet_withdrawals(id,transaction_id,wallet_id,user_id,payout_account_id,reference,idempotency_key,amount,fee,currency,status,metadata) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,'NGN','PENDING',$10::jsonb) RETURNING *",
          [withdrawalId,tx.id,wallet.id,req.user.sub,account.id,reference,key,amount,fee,JSON.stringify({provider:'paystack',recipientCode:account.recipient_code,bankName:account.bank_name,accountName:account.account_name,accountLast4:account.account_last4})]
        )).rows[0];
        await client.query('COMMIT');
        // Capture only the provider recipient code in this request; the full bank account number is never persisted by FYNX.
        withdrawal.recipient_code=account.recipient_code;
      }catch(error){try{await client.query('ROLLBACK')}catch{}throw error;}finally{client.release();}
      let providerTransfer;
      try{
        providerTransfer=await walletPaystackRequest('/transfer',{method:'POST',body:{source:'balance',amount:String(amountSubunit),recipient:withdrawal.recipient_code,reference:withdrawal.reference,reason:'FYNX Wallet bank withdrawal',currency:'NGN'}});
      }catch(providerError){
        const definitive=Number(providerError?.statusCode)>=400&&Number(providerError?.statusCode)<500;
        if(definitive){
          await failBuyerWalletWithdrawal(withdrawal.reference,'transfer.initiation_failed',providerError.message);
          return res.status(502).json({error:'the payment provider rejected this withdrawal request',reference:withdrawal.reference,status:'FAILED'});
        }
        await pool.query("UPDATE buyer_wallet_withdrawals SET status='PROCESSING',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2 AND status='PENDING'",[JSON.stringify({providerInitiationUncertain:true}),withdrawal.id]);
        await pool.query("UPDATE buyer_wallet_transactions SET status='PROCESSING',description=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'",['Withdrawal submitted; awaiting provider confirmation.',withdrawal.transaction_id]);
        return res.status(202).json({withdrawal:{id:String(withdrawal.id),reference:withdrawal.reference,status:'PROCESSING',amount,fee,currency:'NGN'},message:'The payout request is awaiting provider confirmation. Do not submit it again.'});
      }
      if(String(providerTransfer.reference||'')!==withdrawal.reference){
        await pool.query("UPDATE buyer_wallet_withdrawals SET status='BLOCKED',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2",[JSON.stringify({providerReferenceMismatch:true}),withdrawal.id]);
        await pool.query("UPDATE buyer_wallet_transactions SET status='BLOCKED',description=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'",['Payout provider reference did not match the wallet withdrawal.',withdrawal.transaction_id]);
        return res.status(502).json({error:'payment provider returned an unexpected withdrawal reference'});
      }
      const providerStatus=String(providerTransfer.status||'').toLowerCase();
      const nextStatus=providerStatus==='otp'?'PENDING_OTP':'PROCESSING';
      await pool.query("UPDATE buyer_wallet_withdrawals SET status=$1,provider_reference=$2,transfer_code=$3,metadata=metadata || $4::jsonb,updated_at=NOW() WHERE id=$5",
        [nextStatus,providerTransfer.transfer_code||providerTransfer.reference,providerTransfer.transfer_code||null,JSON.stringify({providerStatus}),withdrawal.id]);
      await pool.query("UPDATE buyer_wallet_transactions SET status='PROCESSING',provider_reference=$1,description=$2,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','PROCESSING')",
        [providerTransfer.transfer_code||providerTransfer.reference,nextStatus==='PENDING_OTP'?'Withdrawal requires provider OTP approval.':'Withdrawal is being processed by the payment provider.',withdrawal.transaction_id]);
      if(providerStatus==='success')await settleBuyerWalletWithdrawal(withdrawal.reference,providerTransfer,'transfer_api_response');
      if(providerStatus==='failed')await failBuyerWalletWithdrawal(withdrawal.reference,'transfer.failed',providerTransfer.message||'transfer failed');
      return res.status(201).json({withdrawal:{id:String(withdrawal.id),reference:withdrawal.reference,status:providerStatus==='success'?'SUCCEEDED':providerStatus==='failed'?'FAILED':nextStatus,amount,fee,currency:'NGN',bankName:withdrawal.metadata?.bankName,accountLast4:withdrawal.metadata?.accountLast4},otpRequired:nextStatus==='PENDING_OTP'});
    }catch(error){console.error('wallet withdrawal',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'wallet withdrawal could not be started'});}
  });

  app.post('/api/wallet/withdrawals/:reference/finalize',auth,async(req,res)=>{
    const reference=String(req.params?.reference||'').trim(),otp=String(req.body?.otp||'').trim();
    if(!walletWithdrawalsEnabled())return res.status(503).json({error:'wallet withdrawals are not enabled'});
    if(!/^[a-z0-9_-]{16,50}$/.test(reference)||!/^\d{4,8}$/.test(otp))return res.status(400).json({error:'enter the provider OTP for this withdrawal'});
    try{
      await ensureBuyerWalletSchema();
      const row=(await pool.query("SELECT w.* FROM buyer_wallet_withdrawals w WHERE w.user_id=$1 AND w.reference=$2 AND w.status='PENDING_OTP'",[req.user.sub,reference])).rows[0];
      if(!row||!row.transfer_code)return res.status(404).json({error:'withdrawal awaiting OTP was not found'});
      const data=await walletPaystackRequest('/transfer/finalize',{method:'POST',body:{transfer_code:row.transfer_code,otp}});
      const status=String(data.status||'').toLowerCase();
      if(status==='success')await settleBuyerWalletWithdrawal(reference,data,'transfer_finalize');
      else await pool.query("UPDATE buyer_wallet_withdrawals SET status='PROCESSING',metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2",[JSON.stringify({lastFinalizeStatus:status}),row.id]);
      return res.json({reference,status:status==='success'?'SUCCEEDED':'PROCESSING',message:status==='success'?'Withdrawal paid successfully.':'Provider accepted the OTP; payout is still processing.'});
    }catch(error){console.warn('wallet transfer OTP finalize',error?.message||error);return res.status(422).json({error:'the provider could not finalize this transfer with that OTP'});}
  });

  app.get('/api/wallet/withdrawals/:reference',auth,async(req,res)=>{
    const reference=String(req.params?.reference||'').trim();
    if(!/^[a-z0-9_-]{16,50}$/.test(reference))return res.status(400).json({error:'invalid withdrawal reference'});
    try{
      await ensureBuyerWalletSchema();
      let row=(await pool.query("SELECT w.*,t.status AS transaction_status FROM buyer_wallet_withdrawals w JOIN buyer_wallet_transactions t ON t.id=w.transaction_id WHERE w.user_id=$1 AND w.reference=$2",[req.user.sub,reference])).rows[0];
      if(!row)return res.status(404).json({error:'wallet withdrawal not found'});
      if(['PENDING','PROCESSING'].includes(String(row.status))&&walletWithdrawalsEnabled()){
        try{
          const data=await walletPaystackRequest('/transfer/verify/'+encodeURIComponent(reference));
          if(String(data.reference||'')===reference){
            const status=String(data.status||'').toLowerCase();
            if(status==='success')await settleBuyerWalletWithdrawal(reference,data,'transfer_verification');
            else if(['failed','reversed'].includes(status))await failBuyerWalletWithdrawal(reference,'transfer.'+status,data.message||'transfer did not complete');
          }
        }catch(error){console.warn('wallet withdrawal reconciliation',error?.message||error);}
        row=(await pool.query("SELECT w.*,t.status AS transaction_status FROM buyer_wallet_withdrawals w JOIN buyer_wallet_transactions t ON t.id=w.transaction_id WHERE w.user_id=$1 AND w.reference=$2",[req.user.sub,reference])).rows[0];
      }
      return res.set('Cache-Control','no-store, private').json({withdrawal:{id:String(row.id),reference:row.reference,status:row.status,transactionStatus:row.transaction_status,amount:Number(row.amount),fee:Number(row.fee),currency:row.currency,bankName:row.metadata?.bankName||'',accountName:row.metadata?.accountName||'',accountLast4:row.metadata?.accountLast4||'',createdAt:row.created_at,updatedAt:row.updated_at}});
    }catch(error){console.error('wallet withdrawal status',error);return res.status(error?.code==='DATABASE_NOT_CONFIGURED'?503:500).json({error:'wallet withdrawal status is unavailable'});}
  });

  app.post('/api/wallet/paystack/webhook',async(req,res)=>{
    const signature=req.get('x-paystack-signature')||'';
    if(!walletWebhookSignatureValid(req.rawBody,signature))return res.status(401).json({error:'invalid webhook signature'});
    let event;try{event=JSON.parse(req.rawBody.toString('utf8'));}catch{return res.status(400).json({error:'invalid webhook payload'});}
    const name=String(event?.event||'').trim().toLowerCase(),data=event?.data||{};
    const reference=typeof data.reference==='string'?data.reference.trim():'';
    if(name==='charge.success'){
      try{return res.status(200).json({received:true,...await settleBuyerWalletFunding(reference,data,'paystack_webhook')});}
      catch(error){console.error('buyer wallet Paystack charge webhook',error);return res.status(500).json({error:'wallet payment confirmation could not be processed'});}
    }
    if(name==='bank.transfer.rejected'){
      try{return res.status(200).json({received:true,matched:await rejectBuyerWalletFunding(reference,name,data.reason||data.message||'')});}
      catch(error){console.error('buyer wallet Paystack transfer rejection webhook',error);return res.status(500).json({error:'wallet funding rejection could not be recorded'});}
    }
    if(name==='transfer.success'){
      try{return res.status(200).json({received:true,...await settleBuyerWalletWithdrawal(reference,data,'paystack_webhook')});}
      catch(error){console.error('buyer wallet payout success webhook',error);return res.status(500).json({error:'wallet withdrawal confirmation could not be processed'});}
    }
    if(name==='transfer.failed'){
      try{return res.status(200).json({received:true,matched:await failBuyerWalletWithdrawal(reference,name,data.reason||data.message||'')});}
      catch(error){console.error('buyer wallet payout failure webhook',error);return res.status(500).json({error:'wallet withdrawal failure could not be recorded'});}
    }
    if(name==='transfer.reversed'){
      try{
        const result=await reverseBuyerWalletWithdrawal(reference,data,'paystack_webhook');
        if(result.notSettled) await failBuyerWalletWithdrawal(reference,name,data.reason||data.message||'');
        return res.status(200).json({received:true,...result});
      }catch(error){console.error('buyer wallet payout reversal webhook',error);return res.status(500).json({error:'wallet withdrawal reversal could not be recorded'});}
    }
    return res.status(200).json({received:true,ignored:true});
  });

}

export async function closeBuyerWalletPool() {
  if (pool) await pool.end();
}
