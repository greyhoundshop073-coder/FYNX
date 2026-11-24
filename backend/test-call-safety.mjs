import assert from "node:assert/strict";
import { isFynxCallBlocked } from "./callSafety.js";

const queries = [];
const blockedPool = {
  async query(sql, params) {
    queries.push({ sql, params });
    return { rowCount: new Set(params).has("101") && new Set(params).has("202") ? 1 : 0 };
  }
};

assert.equal(await isFynxCallBlocked(blockedPool, "101", "202"), true,
  "a caller blocked by the recipient must not be allowed to call");
assert.equal(await isFynxCallBlocked(blockedPool, "202", "101"), true,
  "a block must be enforced in the reverse direction too");
assert.equal(await isFynxCallBlocked(blockedPool, "101", "303"), false,
  "an unrelated pair must remain callable");
assert.match(queries[0].sql, /blocker_id=\\$1 AND blocked_id=\\$2/);
assert.match(queries[0].sql, /blocker_id=\\$2 AND blocked_id=\\$1/);
assert.deepEqual(queries[0].params, ["101", "202"]);
await assert.rejects(() => isFynxCallBlocked(blockedPool, "101", "101"), /Invalid direct-call participants/);
await assert.rejects(() => isFynxCallBlocked(blockedPool, "not-an-id", "202"), /Invalid direct-call participants/);
await assert.rejects(() => isFynxCallBlocked(null, "101", "202"), /storage is unavailable/);
console.log("Direct-call bilateral block safety: PASS");
