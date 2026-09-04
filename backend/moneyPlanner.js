const CURRENCY_RE = /^[A-Z]{3}$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const FREQUENCIES = new Set(["WEEKLY", "MONTHLY", "YEARLY"]);

export function registerMoneyPlannerRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS money_transactions (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          title TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
          category TEXT NOT NULL DEFAULT 'General' CHECK (length(category) BETWEEN 1 AND 80),
          type TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
          amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
          currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
          note TEXT NOT NULL DEFAULT '' CHECK (length(note) <= 500),
          occurred_on DATE NOT NULL DEFAULT CURRENT_DATE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS money_transactions_user_date_idx ON money_transactions(user_id, occurred_on DESC, created_at DESC);
        CREATE TABLE IF NOT EXISTS money_budgets (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          category TEXT NOT NULL CHECK (length(category) BETWEEN 1 AND 80),
          amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
          currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
          period TEXT NOT NULL CHECK (period IN ('WEEKLY','MONTHLY','YEARLY')),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          UNIQUE(user_id, category, period, currency)
        );
        CREATE TABLE IF NOT EXISTS money_savings_goals (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
          target_amount NUMERIC(14,2) NOT NULL CHECK (target_amount > 0),
          saved_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (saved_amount >= 0 AND saved_amount <= target_amount),
          currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
          target_date DATE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS money_goals_user_idx ON money_savings_goals(user_id, created_at DESC);
        CREATE TABLE IF NOT EXISTS money_recurring_expenses (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
          category TEXT NOT NULL DEFAULT 'Bills' CHECK (length(category) BETWEEN 1 AND 80),
          amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
          currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
          frequency TEXT NOT NULL CHECK (frequency IN ('WEEKLY','MONTHLY','YEARLY')),
          next_date DATE NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS money_recurring_user_idx ON money_recurring_expenses(user_id, next_date ASC);
      `).catch((error) => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };

  const text = (value, max, fallback = '') => typeof value === 'string' ? value.trim().slice(0, max) : fallback;
  const currency = (value) => { const v = text(value, 3).toUpperCase(); return CURRENCY_RE.test(v) ? v : null; };
  const amount = (value) => { const n = typeof value === 'number' ? value : Number(value); return Number.isFinite(n) && n > 0 && n <= 999999999999.99 ? Math.round(n * 100) / 100 : null; };
  const date = (value) => { const v = text(value, 10); return DATE_RE.test(v) && !Number.isNaN(Date.parse(`${v}T00:00:00Z`)) ? v : null; };
  const id = (value) => { const n = Number(value); return Number.isSafeInteger(n) && n > 0 ? n : null; };

  app.get('/api/money-planner', auth, async (req, res) => {
    try {
      await ensureSchema();
      const uid = req.user.sub;
      const [transactions, budgets, goals, recurring] = await Promise.all([
        pool.query(`SELECT id,title,category,type,amount,currency,note,occurred_on,created_at FROM money_transactions WHERE user_id=$1 ORDER BY occurred_on DESC,created_at DESC LIMIT 200`, [uid]),
        pool.query(`SELECT id,category,amount,currency,period,created_at FROM money_budgets WHERE user_id=$1 ORDER BY category`, [uid]),
        pool.query(`SELECT id,name,target_amount,saved_amount,currency,target_date,created_at FROM money_savings_goals WHERE user_id=$1 ORDER BY created_at DESC`, [uid]),
        pool.query(`SELECT id,name,category,amount,currency,frequency,next_date,created_at FROM money_recurring_expenses WHERE user_id=$1 ORDER BY next_date ASC`, [uid])
      ]);
      const income = transactions.rows.filter(r => r.type === 'INCOME').reduce((s, r) => s + Number(r.amount), 0);
      const expenses = transactions.rows.filter(r => r.type === 'EXPENSE').reduce((s, r) => s + Number(r.amount), 0);
      const budgetTotal = budgets.rows.reduce((s, r) => s + Number(r.amount), 0);
      const recurringTotal = recurring.rows.reduce((s, r) => s + Number(r.amount), 0);
      return res.json({
        summary: { income, expenses, net: income - expenses, budgetTotal, recurringTotal, transactionCount: transactions.rows.length },
        transactions: transactions.rows.map(r => ({ id:String(r.id), title:r.title, category:r.category, type:r.type, amount:Number(r.amount), currency:r.currency, note:r.note, occurredOn:r.occurred_on, createdAt:r.created_at })),
        budgets: budgets.rows.map(r => ({ id:String(r.id), category:r.category, amount:Number(r.amount), currency:r.currency, period:r.period, createdAt:r.created_at })),
        goals: goals.rows.map(r => ({ id:String(r.id), name:r.name, targetAmount:Number(r.target_amount), savedAmount:Number(r.saved_amount), currency:r.currency, targetDate:r.target_date, createdAt:r.created_at })),
        recurring: recurring.rows.map(r => ({ id:String(r.id), name:r.name, category:r.category, amount:Number(r.amount), currency:r.currency, frequency:r.frequency, nextDate:r.next_date, createdAt:r.created_at }))
      });
    } catch (error) { console.error('money planner get', error); return res.status(500).json({ error: 'money planner lookup failed' }); }
  });

  app.post('/api/money-planner/transactions', auth, async (req, res) => {
    try {
      await ensureSchema();
      const title=text(req.body?.title,120), category=text(req.body?.category,80,'General'), note=text(req.body?.note,500), type=text(req.body?.type,10).toUpperCase();
      const value=amount(req.body?.amount), curr=currency(req.body?.currency || 'NGN'), occurred=date(req.body?.occurredOn) || new Date().toISOString().slice(0,10);
      if (!title || !value || !curr || !['INCOME','EXPENSE'].includes(type)) return res.status(400).json({ error:'valid title, amount, currency and transaction type are required' });
      const r=await pool.query(`INSERT INTO money_transactions(user_id,title,category,type,amount,currency,note,occurred_on) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,[req.user.sub,title,category,type,value,curr,note,occurred]);
      const row=r.rows[0]; return res.status(201).json({ transaction:{ id:String(row.id),title:row.title,category:row.category,type:row.type,amount:Number(row.amount),currency:row.currency,note:row.note,occurredOn:row.occurred_on,createdAt:row.created_at } });
    } catch(error){ console.error('money transaction create',error); return res.status(500).json({error:'transaction creation failed'}); }
  });

  app.delete('/api/money-planner/transactions/:id', auth, async (req,res)=>{
    try { await ensureSchema(); const transactionId=id(req.params.id); if(!transactionId) return res.status(400).json({error:'invalid transaction id'}); const r=await pool.query('DELETE FROM money_transactions WHERE id=$1 AND user_id=$2 RETURNING id',[transactionId,req.user.sub]); if(!r.rows[0]) return res.status(404).json({error:'transaction not found'}); return res.json({ok:true}); }
    catch(error){ console.error('money transaction delete',error); return res.status(500).json({error:'transaction deletion failed'}); }
  });

  app.post('/api/money-planner/budgets', auth, async (req,res)=>{
    try { await ensureSchema(); const category=text(req.body?.category,80), value=amount(req.body?.amount), curr=currency(req.body?.currency || 'NGN'), period=text(req.body?.period,10).toUpperCase(); if(!category||!value||!curr||!['WEEKLY','MONTHLY','YEARLY'].includes(period)) return res.status(400).json({error:'valid category, amount, currency and period are required'}); const r=await pool.query(`INSERT INTO money_budgets(user_id,category,amount,currency,period) VALUES($1,$2,$3,$4,$5) ON CONFLICT(user_id,category,period,currency) DO UPDATE SET amount=EXCLUDED.amount RETURNING *`,[req.user.sub,category,value,curr,period]); const row=r.rows[0]; return res.status(201).json({budget:{id:String(row.id),category:row.category,amount:Number(row.amount),currency:row.currency,period:row.period,createdAt:row.created_at}}); }
    catch(error){ console.error('money budget create',error); return res.status(500).json({error:'budget creation failed'}); }
  });

  app.delete('/api/money-planner/budgets/:id', auth, async (req,res)=>{ try{ await ensureSchema(); const budgetId=id(req.params.id); if(!budgetId)return res.status(400).json({error:'invalid budget id'}); const r=await pool.query('DELETE FROM money_budgets WHERE id=$1 AND user_id=$2 RETURNING id',[budgetId,req.user.sub]); if(!r.rows[0])return res.status(404).json({error:'budget not found'}); return res.json({ok:true}); }catch(error){console.error('money budget delete',error);return res.status(500).json({error:'budget deletion failed'});} });

  app.post('/api/money-planner/goals', auth, async (req,res)=>{ try{ await ensureSchema(); const name=text(req.body?.name,120), target=amount(req.body?.targetAmount), saved=typeof req.body?.savedAmount==='undefined'?0:amount(req.body?.savedAmount), curr=currency(req.body?.currency||'NGN'), targetDate=req.body?.targetDate==null?null:date(req.body.targetDate); if(!name||!target||saved===null||saved>target||!curr||(req.body?.targetDate!=null&&!targetDate))return res.status(400).json({error:'valid savings goal values are required'}); const r=await pool.query(`INSERT INTO money_savings_goals(user_id,name,target_amount,saved_amount,currency,target_date) VALUES($1,$2,$3,$4,$5,$6) RETURNING *`,[req.user.sub,name,target,saved,curr,targetDate]); const row=r.rows[0]; return res.status(201).json({goal:{id:String(row.id),name:row.name,targetAmount:Number(row.target_amount),savedAmount:Number(row.saved_amount),currency:row.currency,targetDate:row.target_date,createdAt:row.created_at}}); }catch(error){console.error('money goal create',error);return res.status(500).json({error:'savings goal creation failed'});} });

  app.post('/api/money-planner/goals/:id/contributions', auth, async (req,res)=>{ try{ await ensureSchema(); const goalId=id(req.params.id), add=amount(req.body?.amount); if(!goalId||!add)return res.status(400).json({error:'valid goal id and contribution are required'}); const r=await pool.query(`UPDATE money_savings_goals SET saved_amount=LEAST(target_amount,saved_amount+$1) WHERE id=$2 AND user_id=$3 RETURNING *`,[add,goalId,req.user.sub]); if(!r.rows[0])return res.status(404).json({error:'savings goal not found'}); const row=r.rows[0]; return res.json({goal:{id:String(row.id),name:row.name,targetAmount:Number(row.target_amount),savedAmount:Number(row.saved_amount),currency:row.currency,targetDate:row.target_date}}); }catch(error){console.error('money goal contribution',error);return res.status(500).json({error:'savings contribution failed'});} });

  app.delete('/api/money-planner/goals/:id', auth, async (req,res)=>{ try{ await ensureSchema(); const goalId=id(req.params.id); if(!goalId)return res.status(400).json({error:'invalid goal id'}); const r=await pool.query('DELETE FROM money_savings_goals WHERE id=$1 AND user_id=$2 RETURNING id',[goalId,req.user.sub]); if(!r.rows[0])return res.status(404).json({error:'savings goal not found'}); return res.json({ok:true}); }catch(error){console.error('money goal delete',error);return res.status(500).json({error:'savings goal deletion failed'});} });

  app.post('/api/money-planner/recurring', auth, async (req,res)=>{ try{ await ensureSchema(); const name=text(req.body?.name,120), category=text(req.body?.category,80,'Bills'), value=amount(req.body?.amount), curr=currency(req.body?.currency||'NGN'), frequency=text(req.body?.frequency,10).toUpperCase(), nextDate=date(req.body?.nextDate); if(!name||!value||!curr||!FREQUENCIES.has(frequency)||!nextDate)return res.status(400).json({error:'valid recurring expense values are required'}); const r=await pool.query(`INSERT INTO money_recurring_expenses(user_id,name,category,amount,currency,frequency,next_date) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING *`,[req.user.sub,name,category,value,curr,frequency,nextDate]); const row=r.rows[0]; return res.status(201).json({recurring:{id:String(row.id),name:row.name,category:row.category,amount:Number(row.amount),currency:row.currency,frequency:row.frequency,nextDate:row.next_date,createdAt:row.created_at}}); }catch(error){console.error('money recurring create',error);return res.status(500).json({error:'recurring expense creation failed'});} });

  app.delete('/api/money-planner/recurring/:id', auth, async (req,res)=>{ try{ await ensureSchema(); const recurringId=id(req.params.id); if(!recurringId)return res.status(400).json({error:'invalid recurring expense id'}); const r=await pool.query('DELETE FROM money_recurring_expenses WHERE id=$1 AND user_id=$2 RETURNING id',[recurringId,req.user.sub]); if(!r.rows[0])return res.status(404).json({error:'recurring expense not found'}); return res.json({ok:true}); }catch(error){console.error('money recurring delete',error);return res.status(500).json({error:'recurring expense deletion failed'});} });
}
