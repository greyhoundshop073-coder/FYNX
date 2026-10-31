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
        canFund: false,
        canSpend: false,
        canWithdraw: false,
        notice: 'Wallet money movement is not enabled until provider and regulatory checks are complete.',
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

}

export async function closeBuyerWalletPool() {
  if (pool) await pool.end();
}
