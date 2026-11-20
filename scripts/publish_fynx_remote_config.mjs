import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const API_ROOT = "https://firebaseremoteconfig.googleapis.com/v1";
const REQUIRED_TEMPLATE_PATH = new URL("../firebase/remoteconfig.template.json", import.meta.url);

export function mergeFynxRemoteConfigTemplate(currentTemplate, desiredTemplate) {
  const merged = structuredClone(currentTemplate ?? {});
  delete merged.version;

  if (!Array.isArray(merged.conditions)) merged.conditions = [];
  if (!merged.parameters || typeof merged.parameters !== "object" || Array.isArray(merged.parameters)) {
    merged.parameters = {};
  }

  let changed = false;

  for (const [key, desired] of Object.entries(desiredTemplate.parameters ?? {})) {
    const existing = merged.parameters[key];

    if (!existing) {
      merged.parameters[key] = structuredClone(desired);
      changed = true;
      continue;
    }

    if (existing.valueType !== "BOOLEAN") {
      throw new Error(`Refusing to change existing Remote Config parameter "${key}": expected BOOLEAN, found ${existing.valueType ?? "unspecified"}.`);
    }

    if (!existing.defaultValue) {
      existing.defaultValue = { value: "false" };
      changed = true;
    } else if (existing.defaultValue.useInAppDefault !== true && existing.defaultValue.value !== "false") {
      throw new Error(`Refusing to publish: existing parameter "${key}" has a non-false default. Review it manually first.`);
    }

    for (const [conditionName, conditionalValue] of Object.entries(existing.conditionalValues ?? {})) {
      if (conditionalValue.useInAppDefault !== true && conditionalValue.value !== "false") {
        throw new Error(`Refusing to publish: "${key}" is enabled for condition "${conditionName}". Review it manually first.`);
      }
    }
  }

  return { template: merged, changed };
}

function accessToken() {
  return execFileSync("gcloud", ["auth", "print-access-token"], { encoding: "utf8" }).trim();
}

async function requestTemplate(projectId, token) {
  const response = await fetch(`${API_ROOT}/projects/${encodeURIComponent(projectId)}/remoteConfig`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
      "Accept-Encoding": "gzip",
    },
  });

  if (!response.ok) {
    const detail = (await response.text()).slice(0, 1200);
    throw new Error(`Firebase Remote Config GET failed (${response.status}): ${detail}`);
  }

  return {
    template: await response.json(),
    etag: response.headers.get("etag"),
  };
}

async function putTemplate(projectId, token, etag, template, validateOnly) {
  if (!etag) throw new Error("Firebase Remote Config GET did not return an ETag; refusing an unsafe template overwrite.");

  const url = new URL(`${API_ROOT}/projects/${encodeURIComponent(projectId)}/remoteConfig`);
  if (validateOnly) url.searchParams.set("validateOnly", "true");

  const response = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
      "Accept-Encoding": "gzip",
      "Content-Type": "application/json",
      "If-Match": etag,
    },
    body: JSON.stringify(template),
  });

  if (!response.ok) {
    const detail = (await response.text()).slice(0, 1600);
    throw new Error(`Firebase Remote Config ${validateOnly ? "validation" : "publish"} failed (${response.status}): ${detail}`);
  }

  return response.json();
}

async function main() {
  const projectId = process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) throw new Error("GOOGLE_CLOUD_PROJECT is required.");

  const desired = JSON.parse(readFileSync(REQUIRED_TEMPLATE_PATH, "utf8"));
  const token = accessToken();
  const current = await requestTemplate(projectId, token);
  const merged = mergeFynxRemoteConfigTemplate(current.template, desired);

  if (!merged.changed) {
    console.log(`REMOTE_CONFIG_TEMPLATE=already_ready project=${projectId} version=${current.template.version?.versionNumber ?? "unknown"}`);
    return;
  }

  await putTemplate(projectId, token, current.etag, merged.template, true);
  const published = await putTemplate(projectId, token, current.etag, merged.template, false);
  const verified = await requestTemplate(projectId, token);

  for (const key of Object.keys(desired.parameters ?? {})) {
    const parameter = verified.template.parameters?.[key];
    if (!parameter || parameter.valueType !== "BOOLEAN") {
      throw new Error(`Post-publish verification failed: parameter "${key}" is missing or not BOOLEAN.`);
    }
    if (parameter.defaultValue?.useInAppDefault !== true && parameter.defaultValue?.value !== "false") {
      throw new Error(`Post-publish verification failed: parameter "${key}" does not default to false.`);
    }
    for (const [conditionName, value] of Object.entries(parameter.conditionalValues ?? {})) {
      if (value.useInAppDefault !== true && value.value !== "false") {
        throw new Error(`Post-publish verification failed: "${key}" is enabled for condition "${conditionName}".`);
      }
    }
  }

  console.log(`REMOTE_CONFIG_TEMPLATE=published project=${projectId} version=${verified.template.version?.versionNumber ?? published.version?.versionNumber ?? "unknown"} parameters=${Object.keys(desired.parameters ?? {}).length}`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
