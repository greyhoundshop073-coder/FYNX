// Apply a runtime source patch safely across repeated starts and partially-updated files.
export function applyNotificationBootstrapPatch(source, patch, fileName = "backend file") {
  const alreadyMarkerPresent =
    typeof patch.already === "string" &&
    patch.already.length > 0 &&
    source.includes(patch.already);

  // Prefer a no-op when the replacement or its equivalent import is already present.
  if (source.includes(patch.replacement) || alreadyMarkerPresent) return source;

  if (source.includes(patch.marker)) {
    return source.replace(patch.marker, patch.replacement);
  }

  if (patch.required) {
    throw new Error(
      `Notification bootstrap could not locate ${fileName} marker: ${patch.already || patch.marker}`
    );
  }

  return source;
}
