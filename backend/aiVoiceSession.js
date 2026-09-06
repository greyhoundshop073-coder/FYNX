/**
 * FYNX realtime voice session policy and OpenAI WebRTC proxy helpers.
 *
 * This module intentionally keeps the OpenAI API key server-side. The Android
 * client must never receive OPENAI_API_KEY. The exported helpers are designed
 * to be wired into the authenticated /api/assistant realtime route.
 */

export const FYNX_AI_VOICE_MODEL = process.env.OPENAI_REALTIME_MODEL || "gpt-realtime";
export const FYNX_AI_VOICE = process.env.OPENAI_REALTIME_VOICE || "marin";

export const FYNX_AI_VOICE_INSTRUCTIONS = [
  "You are FYNX AI, a smart, warm and concise conversational assistant.",
  "Speak naturally like a real conversational partner, not like a text reader.",
  "Use short, clear spoken responses unless the user asks for detail.",
  "Listen for interruptions and allow the conversation to continue naturally.",
  "Never expose API keys, tokens, passwords, private account data or internal secrets.",
  "Never claim to have completed a sensitive FYNX action unless an authorized backend function actually completed it.",
  "Never independently make payments, refunds, transfers, campaign activations or other financial actions.",
  "When current information is required, use an approved web-search capability rather than guessing.",
  "Clearly distinguish current web information from general knowledge when useful.",
].join(" ");

export function buildRealtimeSessionConfig() {
  return {
    type: "realtime",
    model: FYNX_AI_VOICE_MODEL,
    voice: FYNX_AI_VOICE,
    output_modalities: ["audio"],
    instructions: FYNX_AI_VOICE_INSTRUCTIONS,
    audio: {
      input: {
        noise_reduction: { type: "near_field" },
        turn_detection: { type: "semantic_vad", eagerness: "medium", create_response: true, interrupt_response: true }
      }
    }
  };
}

export async function createRealtimeAnswer(sdpOffer) {
  const apiKey = process.env.OPENAI_API_KEY || "";
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured.");
  if (typeof sdpOffer !== "string" || !sdpOffer.trim() || sdpOffer.length > 200_000) {
    throw new Error("A valid WebRTC SDP offer is required.");
  }

  const form = new FormData();
  form.append("sdp", sdpOffer);
  form.append("session", new Blob([JSON.stringify(buildRealtimeSessionConfig())], { type: "application/json" }));

  const response = await fetch("https://api.openai.com/v1/realtime/calls", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}` },
    body: form
  });
  const answer = await response.text();
  if (!response.ok) throw new Error(`Realtime session failed (${response.status})`);
  return answer;
}
