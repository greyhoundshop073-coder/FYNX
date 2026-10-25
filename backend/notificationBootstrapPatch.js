// Pure patch application shared by the notification bootstrap and its regression tests.
export function applyNotificationBootstrapPatch(source, patch, fileName = "backend file") {
  // Replacements intentionally retain their marker; test the full replacement first
  // so restarts/retries do not inject the same import or route code a second time.
  if (source.includes(patch.replacement) || (patch.already && source.includes(patch.already))) return source;

  if (source.includes(patch.marker)) {
    return source.replace(patch.marker, patch.replacement);
  }

  if (patch.required && !source.includes(patch.already)) {
    throw new Error(`Notification bootstrap could not locate ${fileName} marker: ${patch.already}`);
  }

  return source;
}
