import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

export async function installSocialFeed() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  const source = await readFile(socialPath, "utf8");

  if (source.includes("/api/social/feed")) return;
  if (!source.includes("const ensureSocialSchema = async")) {
    throw new Error("FYNX social feed bootstrap could not locate social schema initializer");
  }

  const route = `

  // fynxProductionSocialFeed: authoritative paginated Home feed.
  app.get('/api/social/feed', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const requestedLimit = Number(req.query?.limit);
      const requestedOffset = Number(req.query?.offset);
      const limit = Math.min(Math.max(Number.isInteger(requestedLimit) ? requestedLimit : 50, 1), 100);
      const offset = Math.min(Math.max(Number.isInteger(requestedOffset) ? requestedOffset : 0, 0), 1000000);
      const result = await pool.query(`
        SELECT p.id, p.author_id, u.username AS author_username, u.display_name AS author_display_name,
               p.text, p.visibility, p.media_id, p.media_type,
               EXTRACT(EPOCH FROM p.created_at) * 1000 AS timestamp,
               (SELECT COUNT(*) FROM social_post_likes l WHERE l.post_id = p.id) AS like_count,
               (SELECT COUNT(*) FROM social_post_comments c WHERE c.post_id = p.id) AS comment_count,
               EXISTS(SELECT 1 FROM social_post_likes l WHERE l.post_id = p.id AND l.user_id = $1) AS liked_by_current_user,
               EXISTS(SELECT 1 FROM social_follows f WHERE f.follower_id = $1 AND f.followed_id = p.author_id) AS followed_by_current_user
          FROM social_posts p
          JOIN users u ON u.id = p.author_id
         WHERE (p.author_id = $1 OR p.visibility = 'PUBLIC' OR
           (p.visibility = 'FRIENDS_ONLY' AND EXISTS(
             SELECT 1 FROM friendships fr
              WHERE ((fr.user_id = p.author_id AND fr.friend_id = $1) OR (fr.user_id = $1 AND fr.friend_id = p.author_id))
                AND fr.status = 'accepted'
           )))
           AND NOT EXISTS(
             SELECT 1 FROM blocks b
              WHERE (b.blocker_id = $1 AND b.blocked_id = p.author_id)
                 OR (b.blocker_id = p.author_id AND b.blocked_id = $1)
           )
         ORDER BY p.created_at DESC, p.id DESC
         LIMIT $2 OFFSET $3`,
        [req.user.sub, limit + 1, offset]
      );
      const hasMore = result.rows.length > limit;
      const posts = result.rows.slice(0, limit).map(row => ({
        id: String(row.id),
        authorId: String(row.author_id),
        authorUsername: row.author_username,
        authorDisplayName: row.author_display_name,
        text: row.text,
        visibility: row.visibility,
        mediaId: row.media_id == null ? null : String(row.media_id),
        mediaType: row.media_type || null,
        mediaUrl: row.media_id == null ? null : `/api/social/media/${row.media_id}`,
        timestamp: Number(row.timestamp),
        likeCount: Number(row.like_count),
        commentCount: Number(row.comment_count),
        likedByCurrentUser: Boolean(row.liked_by_current_user),
        followedByCurrentUser: Boolean(row.followed_by_current_user)
      }));
      return res.json({ posts, hasMore });
    } catch (error) {
      console.error('social feed', error);
      return res.status(500).json({ error: 'social feed failed' });
    }
  });
`;

  const closing = "\n}\n";
  const index = source.lastIndexOf(closing);
  if (index < 0) throw new Error("FYNX social feed bootstrap could not locate social route closing marker");
  await writeFile(socialPath, source.slice(0, index) + route + source.slice(index), "utf8");
}
