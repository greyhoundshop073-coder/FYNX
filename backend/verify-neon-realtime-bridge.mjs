import assert from "node:assert/strict";
import pg from "pg";
import { installNeonRealtimeBridge } from "./neon-realtime-bridge.js";

const { Pool } = pg;
const pool = new Pool({
  host: process.env.PGHOST || "127.0.0.1",
  port: Number(process.env.PGPORT || 5432),
  user: process.env.PGUSER || "fynx_test",
  password: process.env.PGPASSWORD || "fynx_test",
  database: process.env.PGDATABASE || "fynx_media_test",
  max: 4
});

const deliveredA = [];
const deliveredB = [];
let bridgeA;
let bridgeB;

try {
  bridgeA = await installNeonRealtimeBridge({
    pool,
    dispatchLocal: (userId, payload) => deliveredA.push({ userId, payload }),
    getLocalConnectionCount: () => 1,
    getLocalUserIds: () => ["700000001"],
    logger: { log() {}, error: (...args) => { throw new Error(args.join(" ")); } }
  });
  const publishFromA = globalThis.__fynxNeonRealtimePublish;

  bridgeB = await installNeonRealtimeBridge({
    pool,
    dispatchLocal: (userId, payload) => deliveredB.push({ userId, payload }),
    getLocalConnectionCount: () => 1,
    getLocalUserIds: () => ["700000001"],
    logger: { log() {}, error: (...args) => { throw new Error(args.join(" ")); } }
  });

  const userEvent = { type: "message_status", messageId: "neon-bridge-test", status: "delivered" };
  await publishFromA("700000001", userEvent);
  await bridgeB.pollOnce();
  assert.deepEqual(deliveredB, [{ userId: "700000001", payload: userEvent }]);

  const presenceEvent = { type: "presence", userId: "700000001", online: true };
  await publishFromA(null, presenceEvent);
  await bridgeB.pollOnce();
  assert.deepEqual(deliveredB[1], { userId: null, payload: presenceEvent });

  const stored = await pool.query(
    "SELECT COUNT(*)::int AS count FROM fynx_realtime_events WHERE source_instance_id = ANY($1::text[])",
    [[bridgeA.instanceId, bridgeB.instanceId]]
  );
  assert.ok(stored.rows[0].count >= 2, "realtime events must persist for other Neon isolates");

  console.log("Neon realtime cross-isolate user and global fanout checks passed.");
} finally {
  bridgeA?.stop();
  bridgeB?.stop();
  if (bridgeA && bridgeB) {
    await pool.query("DELETE FROM fynx_realtime_events WHERE source_instance_id = ANY($1::text[])", [[bridgeA.instanceId, bridgeB.instanceId]]).catch(() => {});
  }
  for (const key of ["__fynxNeonRealtimePublish", "__fynxNeonRegisterConnection", "__fynxNeonDisconnect", "__fynxNeonHandleCall"]) {
    delete globalThis[key];
  }
  await pool.end();
}
