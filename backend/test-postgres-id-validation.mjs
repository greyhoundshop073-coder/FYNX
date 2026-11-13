import assert from "node:assert/strict";
import { MAX_POSTGRES_BIGINT, isPositivePostgresBigint } from "./postgresIds.js";

const validStrings = ["1", "42", "9007199254740991", "9007199254740993", "9223372036854775807"];
for (const value of validStrings) {
  assert.equal(isPositivePostgresBigint(value), true, `valid PostgreSQL BIGINT string: ${value}`);
}
assert.equal(MAX_POSTGRES_BIGINT.toString(), "9223372036854775807");
assert.equal(isPositivePostgresBigint(MAX_POSTGRES_BIGINT), true);
assert.equal(isPositivePostgresBigint(42n), true);
assert.equal(isPositivePostgresBigint(42), true);
assert.equal(isPositivePostgresBigint(Number.MAX_SAFE_INTEGER), true);

const invalid = [
  "0", "00", "01", "-1", "+1", "1.0", "1e3",
  "9223372036854775808", "99999999999999999999", "", "  ",
  "not-an-id", "9007199254740993 OR 1=1",
  null, undefined, 0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY,
  Number.MAX_SAFE_INTEGER + 1, 9007199254740992
];
for (const value of invalid) {
  assert.equal(isPositivePostgresBigint(value), false, `invalid PostgreSQL BIGINT: ${String(value)}`);
}
console.log(`POSTGRES_BIGINT_VALIDATION=GREEN (${validStrings.length + invalid.length + 4} cases)`);
