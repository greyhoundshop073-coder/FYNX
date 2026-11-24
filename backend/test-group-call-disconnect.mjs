import assert from "node:assert/strict";
import { disconnectFynxGroupCallsForUser } from "./groupCallRealtime.js";

function makePool({ creatorId = "101", userId = "101", roster = [], invitees = [] } = {}) {
  const calls = [];
  const pool = {
    calls,
    async query(sql, params = []) {
      calls.push({ sql, params });
      if (sql.includes("SELECT c.id, c.group_id")) {
        return { rows: [{ id: "call_test_01", group_id: "group_test", creator_id: creatorId, media_type: "video" }] };
      }
      if (sql.includes("UPDATE fynx_realtime_group_calls") && sql.includes("RETURNING id,group_id,creator_id")) {
        return { rowCount: 1, rows: [{ id: "call_test_01", group_id: "group_test", creator_id: creatorId, media_type: "video" }] };
      }
      if (sql.includes("UPDATE fynx_realtime_group_call_participants") && sql.includes("RETURNING user_id::text AS user_id")) {
        if (sql.includes("status IN ('JOINED','INVITED')")) {
          return { rowCount: invitees.length, rows: invitees.map(id => ({ user_id: id })) };
        }
        if (sql.includes("status='INVITED'")) {
          return { rowCount: invitees.length, rows: invitees.map(id => ({ user_id: id })) };
        }
        return { rowCount: 1, rows: [{ user_id: userId }] };
      }
      if (sql.includes("SELECT p.user_id::text AS")) return { rows: roster };
      throw new Error("Unexpected SQL in group-call disconnect test: " + sql);
    }
  };
  return pool;
}

{
  const pool = makePool({ creatorId: "101", userId: "101", invitees: ["101", "202", "303"] });
  const delivered = [];
  const count = await disconnectFynxGroupCallsForUser({
    pool, userId: "101",
    emitToUser: async (id, event) => delivered.push({ id, event }),
    logger: { error: (...args) => { throw new Error(args.join(" ")); } }
  });
  assert.equal(count, 1);
  assert.equal(pool.calls.some(x => x.sql.includes("SET status='ENDED'") && x.sql.includes("fynx_realtime_group_calls")), true);
  assert.deepEqual(delivered.map(x => x.id).sort(), ["202", "303"]);
  assert.ok(delivered.every(x => x.event.signalType === "group_ended"));
}

{
  const pool = makePool({ creatorId: "101", userId: "202", roster: [], invitees: ["303"] });
  const delivered = [];
  const count = await disconnectFynxGroupCallsForUser({
    pool, userId: "202",
    emitToUser: async (id, event) => delivered.push({ id, event }),
    logger: { error: (...args) => { throw new Error(args.join(" ")); } }
  });
  assert.equal(count, 1);
  assert.equal(pool.calls.some(x => x.sql.includes("SET status='LEFT'")), true);
  assert.equal(pool.calls.some(x => x.sql.includes("SET status='ENDED'") && x.sql.includes("fynx_realtime_group_calls")), true);
  assert.deepEqual(delivered.map(x => x.id), ["303"]);
  assert.equal(delivered[0].event.signalType, "group_ended");
}

{
  const pool = makePool();
  assert.equal(await disconnectFynxGroupCallsForUser({
    pool, userId: "not-an-id", emitToUser: async () => {}
  }), 0);
  assert.equal(pool.calls.length, 0, "invalid user IDs must not query storage");
}

console.log("Group-call disconnect lifecycle tests passed (creator, participant, empty call, invalid ID).");
