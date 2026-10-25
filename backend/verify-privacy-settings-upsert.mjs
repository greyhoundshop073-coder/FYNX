import assert from "node:assert/strict";
import { test } from "node:test";
import { buildPrivacySettingsUpsertStatement } from "./privacyRoutes.js";

const RETURNING = "RETURNING profile_visibility,online_visibility,posts_visibility,status_visibility,profile_photo_visibility,messages_visibility";

test("single-field privacy update uses valid sequential SQL placeholders", () => {
  const statement = buildPrivacySettingsUpsertStatement(221, { messages_visibility: "Everyone" });
  assert.equal(statement.sql, "INSERT INTO privacy_settings(user_id,messages_visibility) VALUES($1,$2) ON CONFLICT(user_id) DO UPDATE SET messages_visibility=$2,updated_at=NOW() " + RETURNING);
  assert.deepEqual(statement.params, [221, "Everyone"]);
});

test("multiple privacy updates preserve ordered columns and placeholder values", () => {
  const statement = buildPrivacySettingsUpsertStatement("221", { profile_visibility: "My friends", messages_visibility: "Nobody", status_visibility: "Everyone" });
  assert.equal(statement.sql, "INSERT INTO privacy_settings(user_id,profile_visibility,messages_visibility,status_visibility) VALUES($1,$2,$3,$4) ON CONFLICT(user_id) DO UPDATE SET profile_visibility=$2,messages_visibility=$3,status_visibility=$4,updated_at=NOW() " + RETURNING);
  assert.deepEqual(statement.params, ["221", "My friends", "Nobody", "Everyone"]);
  const placeholders = [...statement.sql.matchAll(/\$(\d+)/g)].map(match => Number(match[1]));
  assert.ok(placeholders.length > 0);
  assert.equal(Math.max(...placeholders), statement.params.length);
});

test("privacy upsert rejects unknown columns and empty updates", () => {
  assert.throws(() => buildPrivacySettingsUpsertStatement(221, {}), /at least one privacy setting/);
  assert.throws(() => buildPrivacySettingsUpsertStatement(221, { messages_visibility: "Everyone", unknown: "Nobody" }), /unsupported privacy setting/);
});
