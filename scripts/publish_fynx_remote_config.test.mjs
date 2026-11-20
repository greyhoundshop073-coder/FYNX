import test from "node:test";
import assert from "node:assert/strict";
import { mergeFynxRemoteConfigTemplate } from "./publish_fynx_remote_config.mjs";

const desired = {
  conditions: [],
  parameters: {
    home_feed_experiment_enabled: {
      defaultValue: { value: "false" },
      description: "Home Feed experiment; disabled by default.",
      valueType: "BOOLEAN",
    },
    marketplace_discovery_experiment_enabled: {
      defaultValue: { value: "false" },
      description: "Marketplace discovery experiment; disabled by default.",
      valueType: "BOOLEAN",
    },
    ai_voice_experiment_enabled: {
      defaultValue: { value: "false" },
      description: "AI voice experiment; disabled by default.",
      valueType: "BOOLEAN",
    },
  },
};

test("adds only missing FYNX parameters and preserves unrelated remote settings", () => {
  const current = {
    version: { versionNumber: "12" },
    conditions: [{ name: "existing-condition" }],
    parameters: {
      unrelated_banner: {
        defaultValue: { value: "Welcome" },
        valueType: "STRING",
      },
    },
  };

  const result = mergeFynxRemoteConfigTemplate(current, desired);

  assert.equal(result.changed, true);
  assert.deepEqual(result.template.conditions, current.conditions);
  assert.deepEqual(result.template.parameters.unrelated_banner, current.parameters.unrelated_banner);
  assert.deepEqual(Object.keys(result.template.parameters).sort(), [
    "ai_voice_experiment_enabled",
    "home_feed_experiment_enabled",
    "marketplace_discovery_experiment_enabled",
    "unrelated_banner",
  ]);
  assert.equal("version" in result.template, false);
  for (const key of Object.keys(desired.parameters)) {
    assert.equal(result.template.parameters[key].defaultValue.value, "false");
  }
});

test("does not rewrite existing safe boolean parameters", () => {
  const current = {
    conditions: [],
    parameters: {
      home_feed_experiment_enabled: {
        defaultValue: { value: "false" },
        description: "Existing reviewed description.",
        valueType: "BOOLEAN",
      },
      marketplace_discovery_experiment_enabled: structuredClone(desired.parameters.marketplace_discovery_experiment_enabled),
      ai_voice_experiment_enabled: structuredClone(desired.parameters.ai_voice_experiment_enabled),
    },
  };

  const result = mergeFynxRemoteConfigTemplate(current, desired);
  assert.equal(result.changed, false);
  assert.equal(result.template.parameters.home_feed_experiment_enabled.description, "Existing reviewed description.");
});

test("refuses to overwrite an existing parameter with the wrong type", () => {
  const current = {
    parameters: {
      home_feed_experiment_enabled: {
        defaultValue: { value: "false" },
        valueType: "STRING",
      },
    },
  };

  assert.throws(() => mergeFynxRemoteConfigTemplate(current, desired), /expected BOOLEAN/);
});

test("refuses to publish if an existing default enables a flag", () => {
  const current = {
    parameters: {
      home_feed_experiment_enabled: {
        defaultValue: { value: "true" },
        valueType: "BOOLEAN",
      },
    },
  };

  assert.throws(() => mergeFynxRemoteConfigTemplate(current, desired), /non-false default/);
});

test("refuses to publish if an existing condition enables a flag", () => {
  const current = {
    parameters: {
      home_feed_experiment_enabled: {
        defaultValue: { value: "false" },
        conditionalValues: {
          internal_testers: { value: "true" },
        },
        valueType: "BOOLEAN",
      },
    },
  };

  assert.throws(() => mergeFynxRemoteConfigTemplate(current, desired), /enabled for condition/);
});
