import pg from 'pg';
import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 4,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

export function registerR6GIntegrationRoutes({ app }) {
  if (!pool) return;
  const auth = (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
    try { req.user = jwt.verify(token, JWT_SECRET); return next(); } catch { return res.status(401).json({ error: 'invalid or expired token' }); }
  };
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS business_id BIGINT REFERENCES business_profiles(id) ON DELETE SET NULL;
      CREATE INDEX IF NOT EXISTS marketplace_listings_business_idx ON marketplace_listings(business_id, active, created_at DESC);
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS listing_id BIGINT REFERENCES marketplace_listings(id) ON DELETE SET NULL;
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS business_id BIGINT REFERENCES business_profiles(id) ON DELETE SET NULL;
      CREATE INDEX IF NOT EXISTS social_posts_listing_idx ON social_posts(listing_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS social_posts_business_idx ON social_posts(business_id, created_at DESC);
      ALTER TABLE messages ADD COLUMN IF NOT EXISTS listing_id BIGINT REFERENCES marketplace_listings(id) ON DELETE SET NULL;
      ALTER TABLE messages ADD COLUMN IF NOT EXISTS business_id BIGINT REFERENCES business_profiles(id) ON DELETE SET NULL;
      CREATE INDEX IF NOT EXISTS messages_listing_idx ON messages(listing_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS messages_business_idx ON messages(business_id, created_at DESC);
      ALTER TABLE fynx_group_posts ADD COLUMN IF NOT EXISTS listing_id BIGINT REFERENCES marketplace_listings(id) ON DELETE SET NULL;
      ALTER TABLE fynx_group_posts ADD COLUMN IF NOT EXISTS business_id BIGINT REFERENCES business_profiles(id) ON DELETE SET NULL;
      CREATE INDEX IF NOT EXISTS fynx_group_posts_listing_idx ON fynx_group_posts(listing_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_group_posts_business_idx ON fynx_group_posts(business_id, created_at DESC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const positiveId = value => { const n = Number(value); return Number.isInteger(n) && n > 0 ? n : null; };
  const listingContext = async listingId => (await pool.query(`
      SELECT l.id,l.seller_id,l.business_id,l.active,l.quantity,l.store_name,l.title,l.description,l.price,l.currency,l.category,
             u.username AS seller_username,u.display_name AS seller_display_name,
             b.business_name,b.business_username,b.category AS business_category,b.verified AS business_verified,b.active AS business_active
      FROM marketplace_listings l JOIN users u ON u.id=l.seller_id
      LEFT JOIN business_profiles b ON b.id=l.business_id WHERE l.id=$1 LIMIT 1`, [listingId])).rows[0] || null;

  app.get('/api/r6g/listings/:id/context', auth, async (req, res) => {
    try {
      await ensureSchema(); const listingId = positiveId(req.params?.id);
      if (!listingId) return res.status(400).json({ error: 'invalid listing id' });
      const row = await listingContext(listingId);
      if (!row || (!row.active && String(row.seller_id) !== String(req.user.sub))) return res.status(404).json({ error: 'listing unavailable' });
      const blocked = await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`, [req.user.sub, row.seller_id]);
      if (blocked.rowCount && String(row.seller_id) !== String(req.user.sub)) return res.status(404).json({ error: 'listing unavailable' });
      return res.json({ context: { listingId:String(row.id), sellerId:String(row.seller_id), sellerUsername:row.seller_username, sellerDisplayName:row.seller_display_name, businessId:row.business_id==null?null:String(row.business_id), businessName:row.business_name||null, businessUsername:row.business_username||null, businessCategory:row.business_category||null, businessVerified:Boolean(row.business_verified), businessActive:row.business_active==null?null:Boolean(row.business_active), active:Boolean(row.active), quantity:Number(row.quantity), storeName:row.store_name, title:row.title, description:row.description, price:Number(row.price), currency:row.currency, category:row.category } });
    } catch (error) { console.error('r6g listing context', error); return res.status(500).json({ error: 'integration context unavailable' }); }
  });

  app.patch('/api/r6g/listings/:id/business', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSchema(); const listingId=positiveId(req.params?.id); if(!listingId)return res.status(400).json({error:'invalid listing id'});
      const listing=(await client.query('SELECT id,seller_id FROM marketplace_listings WHERE id=$1 FOR UPDATE',[listingId])).rows[0];
      if(!listing)return res.status(404).json({error:'listing not found'}); if(String(listing.seller_id)!==String(req.user.sub))return res.status(403).json({error:'only the listing seller can change business ownership'});
      const businessId=req.body?.businessId==null||String(req.body.businessId).trim()===''?null:positiveId(req.body.businessId);
      if(req.body?.businessId!=null&&businessId==null)return res.status(400).json({error:'invalid business id'});
      if(businessId!=null){const business=(await client.query('SELECT id FROM business_profiles WHERE id=$1 AND owner_id=$2 AND COALESCE(active,TRUE)=TRUE LIMIT 1',[businessId,req.user.sub])).rows[0];if(!business)return res.status(403).json({error:'business is not owned by this account'});}
      const result=await client.query('UPDATE marketplace_listings SET business_id=$1,updated_at=NOW() WHERE id=$2 RETURNING id,business_id',[businessId,listingId]);
      return res.json({listingId:String(result.rows[0].id),businessId:result.rows[0].business_id==null?null:String(result.rows[0].business_id)});
    } catch(error){console.error('r6g listing business link',error);return res.status(500).json({error:'business linkage failed'});} finally{client.release();}
  });

  app.post('/api/r6g/listings/:id/share', auth, async (req,res)=>{
    const client=await pool.connect();
    try{
      await ensureSchema(); const listingId=positiveId(req.params?.id); if(!listingId)return res.status(400).json({error:'invalid listing id'});
      const listing=(await client.query(`SELECT l.*,b.id AS linked_business_id FROM marketplace_listings l LEFT JOIN business_profiles b ON b.id=l.business_id WHERE l.id=$1 AND l.active=TRUE AND l.quantity>0 LIMIT 1`,[listingId])).rows[0];
      if(!listing)return res.status(404).json({error:'listing unavailable'}); if(String(listing.seller_id)!==String(req.user.sub))return res.status(403).json({error:'only the listing seller can share this product from its canonical record'});
      const visibility=req.body?.visibility==='FRIENDS_ONLY'?'FRIENDS_ONLY':'PUBLIC'; const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,4000):'';
      const post=(await client.query('INSERT INTO social_posts(author_id,text,visibility,listing_id,business_id) VALUES($1,$2,$3,$4,$5) RETURNING id',[req.user.sub,text,visibility,listingId,listing.linked_business_id||null])).rows[0];
      return res.status(201).json({postId:String(post.id),listingId:String(listingId),businessId:listing.linked_business_id==null?null:String(listing.linked_business_id)});
    }catch(error){console.error('r6g listing share',error);return res.status(500).json({error:'product share failed'});}finally{client.release();}
  });

  app.post('/api/r6g/messages/:id/context',auth,async(req,res)=>{
    try{
      await ensureSchema(); const messageId=positiveId(req.params?.id); const listingId=req.body?.listingId==null?null:positiveId(req.body.listingId);
      if(!messageId||!listingId)return res.status(400).json({error:'valid message and listing ids are required'});
      const message=(await pool.query('SELECT id,sender_id,recipient_id FROM messages WHERE id=$1 LIMIT 1',[messageId])).rows[0]; if(!message)return res.status(404).json({error:'message not found'});
      if(![String(message.sender_id),String(message.recipient_id)].includes(String(req.user.sub)))return res.status(403).json({error:'message access denied'});
      const listing=await listingContext(listingId); if(!listing)return res.status(404).json({error:'listing not found'});
      const otherUserId=String(message.sender_id)===String(req.user.sub)?String(message.recipient_id):String(message.sender_id);
      if(![String(listing.seller_id),String(req.user.sub)].includes(otherUserId)&&String(listing.seller_id)!==String(req.user.sub))return res.status(403).json({error:'message is not connected to this seller'});
      if(listing.business_id!=null&&String(req.body?.businessId||listing.business_id)!==String(listing.business_id))return res.status(400).json({error:'business context does not match listing'});
      await pool.query('UPDATE messages SET listing_id=$1,business_id=$2 WHERE id=$3',[listingId,listing.business_id||null,messageId]);
      return res.json({messageId:String(messageId),listingId:String(listingId),businessId:listing.business_id==null?null:String(listing.business_id)});
    }catch(error){console.error('r6g message context',error);return res.status(500).json({error:'message context failed'});}
  });

  app.post('/api/r6g/groups/:groupId/marketplace-posts',auth,async(req,res)=>{
    try{
      await ensureSchema(); const groupId=String(req.params?.groupId||'').trim(); const listingId=positiveId(req.body?.listingId); const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,4000):'';
      if(!groupId||!listingId)return res.status(400).json({error:'group and listing are required'});
      const member=(await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1',[groupId,req.user.sub])).rows[0]; if(!member)return res.status(403).json({error:'group membership required'});
      const listing=await listingContext(listingId); if(!listing||!listing.active||Number(listing.quantity)<=0)return res.status(404).json({error:'listing unavailable'});
      const blocked=await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`,[req.user.sub,listing.seller_id]); if(blocked.rowCount&&String(listing.seller_id)!==String(req.user.sub))return res.status(403).json({error:'listing unavailable'});
      const post=(await pool.query('INSERT INTO fynx_group_posts(id,group_id,author_id,text,listing_id,business_id) VALUES($1,$2,$3,$4,$5,$6) RETURNING id,created_at',[randomUUID(),groupId,req.user.sub,text,listingId,listing.business_id||null])).rows[0];
      return res.status(201).json({postId:String(post.id),listingId:String(listingId),businessId:listing.business_id==null?null:String(listing.business_id),createdAt:new Date(post.created_at).getTime()});
    }catch(error){console.error('r6g group marketplace post',error);return res.status(500).json({error:'group product share failed'});}
  });

  app.get('/api/r6g/health',auth,async(_req,res)=>{try{await ensureSchema();const checks=await Promise.all([pool.query("SELECT 1 FROM information_schema.columns WHERE table_name='marketplace_listings' AND column_name='business_id'"),pool.query("SELECT 1 FROM information_schema.columns WHERE table_name='social_posts' AND column_name='listing_id'"),pool.query("SELECT 1 FROM information_schema.columns WHERE table_name='messages' AND column_name='listing_id'"),pool.query("SELECT 1 FROM information_schema.columns WHERE table_name='fynx_group_posts' AND column_name='listing_id'")]);return res.json({ok:checks.every(r=>r.rowCount>0),canonicalListingLinks:true,businessLinks:true,homeLinks:true,messageLinks:true,groupLinks:true});}catch(error){return res.status(500).json({ok:false,error:'integration schema unavailable'});}});
}
