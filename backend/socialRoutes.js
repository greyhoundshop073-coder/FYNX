export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {
  app.get("/api/users/search", auth, async (req, res) => {
    try {
      const query = typeof req.query?.q === "string" ? req.query.q.trim().toLowerCase() : "";
      if (query.length < 2 || query.length > 32) return res.status(400).json({ error: "search query must be 2-32 characters" });
      const result = await pool.query(
        `SELECT id, username, display_name, phone, created_at
         FROM users
         WHERE username ILIKE $1 OR display_name ILIKE $2
         ORDER BY CASE WHEN username = $3 THEN 0 ELSE 1 END, username
         LIMIT 20`,
        [`%${query}%`, `%${query}%`, query]
      );
      return res.json({ users: result.rows });
    } catch (error) { console.error("user search", error); return res.status(500).json({ error: "user search failed" }); }
  });

  app.get("/api/friends", auth, async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT u.id, u.username, u.display_name, u.phone, f.created_at
         FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_id = $1 THEN f.friend_id ELSE f.user_id END
         WHERE (f.user_id = $1 OR f.friend_id = $1) AND f.status = 'accepted'
         ORDER BY u.username`, [req.user.sub]
      );
      return res.json({ friends: result.rows });
    } catch (error) { console.error("friends", error); return res.status(500).json({ error: "friends lookup failed" }); }
  });

  app.get("/api/friends/requests", auth, async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT f.id, f.status, f.created_at, u.id AS user_id, u.username, u.display_name
         FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_id = $1 THEN f.friend_id ELSE f.user_id END
         WHERE (f.user_id = $1 OR f.friend_id = $1) AND f.status = 'pending'
         ORDER BY f.created_at DESC`, [req.user.sub]
      );
      return res.json({ requests: result.rows });
    } catch (error) { console.error("friend requests", error); return res.status(500).json({ error: "friend requests lookup failed" }); }
  });

  app.post("/api/friends/request", auth, async (req, res) => {
    try {
      const username = typeof req.body?.username === "string" ? req.body.username.trim().toLowerCase() : "";
      const target = await findUserByUsername(username);
      if (!target) return res.status(404).json({ error: "user not found" });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error: "cannot add yourself" });
      const blocked = await pool.query(
        `SELECT 1 FROM blocks WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1) LIMIT 1`,
        [req.user.sub, target.id]
      );
      if (blocked.rowCount) return res.status(403).json({ error: "friend request unavailable" });
      const existing = await pool.query(
        `SELECT id, user_id, friend_id, status FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1) LIMIT 1`,
        [req.user.sub, target.id]
      );
      if (existing.rows[0]?.status === "accepted") return res.status(409).json({ error: "already friends" });
      if (existing.rows[0]?.status === "pending") return res.status(409).json({ error: "friend request already pending" });
      const result = await pool.query(
        `INSERT INTO friendships (user_id, friend_id, status) VALUES ($1, $2, 'pending') RETURNING id, user_id, friend_id, status, created_at`,
        [req.user.sub, target.id]
      );
      return res.status(201).json({ request: result.rows[0] });
    } catch (error) { console.error("friend request", error); return res.status(500).json({ error: "friend request failed" }); }
  });

  app.post("/api/friends/requests/:id/accept", auth, async (req, res) => {
    try {
      const result = await pool.query(
        `UPDATE friendships SET status = 'accepted' WHERE id = $1 AND friend_id = $2 AND status = 'pending' RETURNING id, user_id, friend_id, status, created_at`,
        [req.params.id, req.user.sub]
      );
      if (!result.rows[0]) return res.status(404).json({ error: "request not found" });
      return res.json({ request: result.rows[0] });
    } catch (error) { console.error("accept friend", error); return res.status(500).json({ error: "accept failed" }); }
  });

  app.post("/api/friends/requests/:id/reject", auth, async (req, res) => {
    try {
      const result = await pool.query(
        `DELETE FROM friendships WHERE id = $1 AND friend_id = $2 AND status = 'pending' RETURNING id`,
        [req.params.id, req.user.sub]
      );
      if (!result.rows[0]) return res.status(404).json({ error: "request not found" });
      return res.json({ ok: true });
    } catch (error) { console.error("reject friend", error); return res.status(500).json({ error: "reject failed" }); }
  });

  app.delete("/api/friends/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase());
      if (!target) return res.status(404).json({ error: "user not found" });
      await pool.query(
        `DELETE FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)`,
        [req.user.sub, target.id]
      );
      return res.json({ ok: true });
    } catch (error) { console.error("remove friend", error); return res.status(500).json({ error: "remove friend failed" }); }
  });

  app.get("/api/blocks", auth, async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT u.id, u.username, u.display_name, b.created_at FROM blocks b JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = $1 ORDER BY u.username`,
        [req.user.sub]
      );
      return res.json({ blocks: result.rows });
    } catch (error) { console.error("blocks", error); return res.status(500).json({ error: "blocks lookup failed" }); }
  });

  app.post("/api/blocks/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase());
      if (!target) return res.status(404).json({ error: "user not found" });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error: "cannot block yourself" });
      await pool.query("INSERT INTO blocks (blocker_id, blocked_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [req.user.sub, target.id]);
      await pool.query("DELETE FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)", [req.user.sub, target.id]);
      return res.status(201).json({ ok: true });
    } catch (error) { console.error("block", error); return res.status(500).json({ error: "block failed" }); }
  });

  app.delete("/api/blocks/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase());
      if (!target) return res.status(404).json({ error: "user not found" });
      await pool.query("DELETE FROM blocks WHERE blocker_id = $1 AND blocked_id = $2", [req.user.sub, target.id]);
      return res.json({ ok: true });
    } catch (error) { console.error("unblock", error); return res.status(500).json({ error: "unblock failed" }); }
  });
}
