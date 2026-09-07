const SCAM_PATTERNS = [
  /\b(?:send|pay|transfer)\b.{0,40}\b(?:crypto|bitcoin|usdt|gift card|voucher)\b/i,
  /\b(?:whatsapp|telegram|signal)\b.{0,50}\b(?:pay|payment|deposit|fee)\b/i,
  /\b(?:verification|unlock|release)\b.{0,50}\b(?:fee|payment|deposit|money)\b/i,
  /\b(?:otp|one[- ]time password|verification code|2fa code)\b/i,
  /\b(?:password|passcode|seed phrase|private key)\b.{0,40}\b(?:send|share|give|tell)\b/i,
  /\b(?:pay|send|transfer)\b.{0,25}\b(?:first|upfront|advance)\b/i
];

const HARD_BLOCK_PATTERNS = [
  /\b(?:send|share|give|tell)\b.{0,35}\b(?:otp|one[- ]time password|verification code|2fa code|seed phrase|private key)\b/i,
  /\b(?:pay|send|transfer)\b.{0,35}\b(?:bitcoin|crypto|usdt|gift card|voucher)\b.{0,60}\b(?:whatsapp|telegram|signal|outside|off[- ]platform)\b/i,
  /\b(?:verification|unlock|release)\b.{0,45}\b(?:fee|payment|deposit)\b.{0,45}\b(?:whatsapp|telegram|signal|outside|off[- ]platform)\b/i
];

const SPAM_PATTERNS = [
  /(.)\1{9,}/,
  /(?:https?:\/\/\S+\s*){4,}/i,
  /\b(?:click|claim|win|free)\b.{0,20}\b(?:now|today|urgent)\b/i
];

function normalizeText(value) {
  return typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 4000) : "";
}

export function inspectTrustSafetyText(value) {
  const text = normalizeText(value);
  const scamSignals = SCAM_PATTERNS.reduce((count, pattern) => count + (pattern.test(text) ? 1 : 0), 0);
  const spamSignals = SPAM_PATTERNS.reduce((count, pattern) => count + (pattern.test(text) ? 1 : 0), 0);
  const hardBlock = HARD_BLOCK_PATTERNS.some((pattern) => pattern.test(text));
  const risk = scamSignals >= 2 || hardBlock ? "HIGH" : scamSignals === 1 || spamSignals >= 2 ? "MEDIUM" : "LOW";
  return {
    risk,
    scamSignals,
    spamSignals,
    shouldWarn: risk !== "LOW",
    shouldBlock: hardBlock || scamSignals >= 2
  };
}

export function registerTrustSafetyRoutes({ app, pool, auth }) {
  if (!pool) return;
  app.post("/api/safety/content-check", auth, async (req, res) => {
    try {
      const text = normalizeText(req.body?.text);
      if (!text) return res.status(400).json({ error: "text is required" });
      return res.json({ safety: inspectTrustSafetyText(text) });
    } catch (error) {
      console.error("safety content check", error);
      return res.status(500).json({ error: "safety check failed" });
    }
  });
}
