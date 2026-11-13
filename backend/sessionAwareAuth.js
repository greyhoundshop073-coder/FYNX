import { isPositivePostgresBigint } from "./postgresIds.js";

export function createSessionAwareBearerAuthenticator({ pool, jwt, jwtSecret }) {
  return async function authenticate(req, res) {
    if (!jwtSecret) { res.status(503).json({ error: "service unavailable" }); return null; }
    const header = typeof req.headers?.authorization === "string" ? req.headers.authorization : "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    if (!token) { res.status(401).json({ error: "authentication required" }); return null; }
    let claims;
    try {
      claims = jwt.verify(token, jwtSecret);
      if (!isPositivePostgresBigint(claims?.sub)) throw new Error("invalid token subject");
    } catch {
      res.status(401).json({ error: "authentication required" }); return null;
    }
    try {
      if (!pool) { res.status(503).json({ error: "authentication temporarily unavailable" }); return null; }
      const current = await pool.query("SELECT session_version FROM users WHERE id=$1", [String(claims.sub)]);
      const version = current.rows?.[0] ? Number(current.rows[0].session_version || 0) : -1;
      if (version < 0 || version !== Number(claims.sv || 0)) {
        res.status(401).json({ error: "session expired; sign in again" }); return null;
      }
      return { userId: String(claims.sub) };
    } catch {
      res.status(503).json({ error: "authentication temporarily unavailable" }); return null;
    }
  };
}
