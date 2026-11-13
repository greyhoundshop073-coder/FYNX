export const MAX_POSTGRES_BIGINT = 9223372036854775807n;

/**
 * Validate a positive PostgreSQL BIGINT identifier without converting string
 * IDs through Number, which would round valid values above Number.MAX_SAFE_INTEGER.
 */
export function isPositivePostgresBigint(value) {
  let raw;
  if (typeof value === "string") raw = value.trim();
  else if (typeof value === "bigint") raw = value.toString();
  else if (typeof value === "number" && Number.isSafeInteger(value)) raw = String(value);
  else return false;

  if (!/^[1-9]\d*$/.test(raw)) return false;
  try {
    return BigInt(raw) <= MAX_POSTGRES_BIGINT;
  } catch {
    return false;
  }
}
