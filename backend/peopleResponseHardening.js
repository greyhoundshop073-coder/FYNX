import pg from "pg";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 2,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

export function installPeopleResponseHardening(app) {
  if (!app?._router?.stack || !pool) return;
  if (app._router.stack.some((layer) => layer.fynxPeopleResponseHardening)) return;

  const paths = new Set(["/api/users/search", "/api/friends", "/api/friends/requests"]);
  const middleware = (req, res, next) => {
    if (!paths.has(req.path)) return next();
    const originalJson = res.json.bind(res);
    res.json = async (payload) => {
      try {
        const users = Array.isArray(payload?.users) ? payload.users : [];
        const friends = Array.isArray(payload?.friends) ? payload.friends : [];
        const requests = Array.isArray(payload?.requests) ? payload.requests : [];
        const rows = [...users, ...friends, ...requests];
        const ids = [...new Set(rows.map((row) => String(row?.id ?? row?.user_id ?? "")).filter((id) => /^\d+$/.test(id)))];
        if (ids.length) {
          const result = await pool.query(
            "SELECT id, profile_photo_media_id FROM users WHERE id = ANY($1::bigint[])",
            [ids]
          );
          const photos = new Map(result.rows.map((row) => [String(row.id), row.profile_photo_media_id == null ? null : String(row.profile_photo_media_id)]));
          const enrich = (row) => ({
            ...row,
            profile_photo_media_id: photos.has(String(row?.id ?? row?.user_id))
              ? photos.get(String(row?.id ?? row?.user_id))
              : row?.profile_photo_media_id ?? null
          });
          if (users.length) payload.users = users.map(enrich);
          if (friends.length) payload.friends = friends.map(enrich);
          if (requests.length) payload.requests = requests.map(enrich);
        }
      } catch (error) {
        console.error("people response hardening", error);
      }
      return originalJson(payload);
    };
    return next();
  };

  app.use(middleware);
  const addedIndex = app._router.stack.length - 1;
  const added = app._router.stack[addedIndex];
  if (!added) return;
  added.fynxPeopleResponseHardening = true;
  const targetIndexes = app._router.stack.reduce((indexes, layer, index) => {
    if (paths.has(layer.route?.path)) indexes.push(index);
    return indexes;
  }, []);
  if (!targetIndexes.length) return;
  app._router.stack.splice(addedIndex, 1);
  app._router.stack.splice(Math.min(...targetIndexes), 0, added);
}
