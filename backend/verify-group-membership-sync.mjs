import assert from "node:assert/strict";
import jwt from "jsonwebtoken";
import { test } from "node:test";

const SECRET = "fynx-group-membership-sync-test-secret";
process.env.JWT_SECRET = SECRET;
delete process.env.DATABASE_URL;
const { registerGroupRoutes } = await import("./groupRoutes.js");

const GROUP_ID = "group_ci_sync_001";
const users = [
  { id: "1", username: "test_owner" },
  { id: "2", username: "test_peer" },
  { id: "3", username: "old_member" },
  { id: "4", username: "regular_member" },
];

class FakeGroupPool {
  group = { id: GROUP_ID, owner_id: "1", group_photo_media_id: null, name: "CI lifecycle", description: "Test group", visibility: "PRIVATE" };
  memberships = new Map([
    ["1", "ADMIN"],
    ["3", "MEMBER"],
    ["4", "MEMBER"],
  ]);

  async query(sql, params = []) {
    const query = String(sql).replace(/\s+/g, " ").trim().toLowerCase();
    if (query.startsWith("create table if not exists fynx_groups")) return { rows: [] };
    if (query === "begin" || query === "commit" || query === "rollback") return { rows: [] };

    if (query.startsWith("select id,username from users where id=$1 limit 1")) {
      const user = users.find((candidate) => candidate.id === String(params[0]));
      return { rows: user ? [{ ...user }] : [] };
    }
    if (query.startsWith("select role from fynx_group_members where group_id=$1 and user_id=$2 limit 1")) {
      const role = String(params[0]) === GROUP_ID ? this.memberships.get(String(params[1])) : undefined;
      return { rows: role ? [{ role }] : [] };
    }
    if (query.startsWith("select id,username from users where lower(username)=any($1::text[])")) {
      const names = (params[0] || []).map((name) => String(name).toLowerCase());
      return { rows: users.filter((user) => names.includes(user.username.toLowerCase())).map((user) => ({ ...user })) };
    }
    if (query.startsWith("select id,owner_id,group_photo_media_id from fynx_groups where id=$1 for update")) {
      return { rows: String(params[0]) === GROUP_ID ? [{ ...this.group }] : [] };
    }
    if (query.startsWith("update fynx_groups set name=$1,description=$2,visibility=$3")) {
      const [name, description, visibility, photo, id] = params;
      if (String(id) !== GROUP_ID) return { rows: [] };
      this.group = { ...this.group, name, description, visibility, group_photo_media_id: photo ?? this.group.group_photo_media_id };
      return { rows: [] };
    }
    if (query.startsWith("delete from fynx_group_members where group_id=$1 and not (user_id = any($2::bigint[]))")) {
      const [groupId, keepIds] = params;
      const keep = new Set((keepIds || []).map(String));
      if (String(groupId) === GROUP_ID) {
        for (const userId of [...this.memberships.keys()]) {
          if (!keep.has(userId)) this.memberships.delete(userId);
        }
      }
      return { rows: [] };
    }
    if (query.startsWith("insert into fynx_group_members(group_id,user_id,role) values($1,$2,$3) on conflict(group_id,user_id) do update set role=excluded.role")) {
      const [groupId, userId, role] = params;
      assert.equal(String(groupId), GROUP_ID);
      this.memberships.set(String(userId), String(role));
      return { rows: [] };
    }
    if (query.startsWith("select group_photo_media_id from fynx_groups where id=$1 limit 1")) {
      return { rows: String(params[0]) === GROUP_ID ? [{ group_photo_media_id: this.group.group_photo_media_id }] : [] };
    }
    if (query.startsWith("select u.username,m.role from fynx_group_members m join users u on u.id=m.user_id where m.group_id=$1")) {
      const roster = [];
      for (const [userId, role] of this.memberships.entries()) {
        const user = users.find((candidate) => candidate.id === userId);
        if (user) roster.push({ username: user.username, role });
      }
      const rank = { ADMIN: 0, MODERATOR: 1, MEMBER: 2 };
      roster.sort((a, b) => (rank[a.role] ?? 3) - (rank[b.role] ?? 3) || a.username.localeCompare(b.username));
      return { rows: roster };
    }
    throw new Error("Unexpected SQL reached membership-sync route test: " + query.slice(0, 220));
  }

  async connect() {
    return { query: this.query.bind(this), release() {} };
  }
}

function fakeResponse() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; },
  };
}

function createHarness(pool) {
  const routes = new Map();
  const app = new Proxy({}, {
    get(_target, method) {
      return (...args) => {
        if (method === "post" && args[0] === "/api/groups/:groupId/sync") routes.set(args[0], args.slice(1));
        return app;
      };
    },
  });
  registerGroupRoutes({ app, pool, jwtSecret: SECRET });
  const handlers = routes.get("/api/groups/:groupId/sync");
  assert.ok(handlers && handlers.length === 2, "the test must exercise the registered authenticated group sync route");

  return async function sync(actorId, body) {
    const req = {
      params: { groupId: GROUP_ID },
      body,
      get(name) { return name.toLowerCase() === "authorization" ? "Bearer " + jwt.sign({ sub: String(actorId) }, SECRET) : undefined; },
    };
    const res = fakeResponse();
    let authorized = false;
    handlers[0](req, res, () => { authorized = true; });
    if (!authorized) return res;
    await handlers[1](req, res);
    return res;
  };
}

const baseGroup = (members) => ({
  ownerUsername: "test_owner",
  name: "CI lifecycle",
  description: "Test group",
  visibility: "PRIVATE",
  members,
});

test("existing group sync persists new members, removes omitted members, and rejects unknown users", async () => {
  const pool = new FakeGroupPool();
  const sync = createHarness(pool);

  const add = await sync("1", baseGroup([
    { username: "test_owner", role: "ADMIN" },
    { username: "test_peer", role: "MEMBER" },
    { username: "regular_member", role: "MEMBER" },
  ]));
  assert.equal(add.statusCode, 200);
  assert.deepEqual([...pool.memberships.entries()].sort(), [["1", "ADMIN"], ["2", "MEMBER"], ["4", "MEMBER"]]);
  assert.deepEqual(add.body.group.members.map((member) => member.username).sort(), ["regular_member", "test_owner", "test_peer"]);
  assert.equal(pool.memberships.has("3"), false, "a member omitted from the requested roster must be removed");

  const remove = await sync("1", baseGroup([{ username: "test_owner", role: "ADMIN" }]));
  assert.equal(remove.statusCode, 200);
  assert.deepEqual([...pool.memberships.entries()], [["1", "ADMIN"]]);
  assert.deepEqual(remove.body.group.members.map((member) => member.username), ["test_owner"]);

  const beforeUnknown = new Map(pool.memberships);
  const invalid = await sync("1", baseGroup([
    { username: "test_owner", role: "ADMIN" },
    { username: "unknown_ci_user", role: "MEMBER" },
  ]));
  assert.equal(invalid.statusCode, 404);
  assert.equal(invalid.body?.code, "GROUP_MEMBER_NOT_FOUND");
  assert.deepEqual(pool.memberships, beforeUnknown, "unknown username must not mutate the persisted roster");

  pool.memberships.set("4", "MEMBER");
  const beforeUnauthorized = new Map(pool.memberships);
  const unauthorized = await sync("4", baseGroup([
    { username: "test_owner", role: "ADMIN" },
    { username: "regular_member", role: "MEMBER" },
  ]));
  assert.equal(unauthorized.statusCode, 403, "ordinary members cannot use group sync to change membership");
  assert.deepEqual(pool.memberships, beforeUnauthorized, "unauthorized roster sync must be mutation-free");

  console.log("PASS existing group member addition is written to the persistent roster");
  console.log("PASS omitted members are removed while owner/admin membership is preserved");
  console.log("PASS unknown usernames return GROUP_MEMBER_NOT_FOUND without mutating membership");
  console.log("PASS non-admin/non-moderator membership sync is rejected without mutation");
});
