/**
 * FYNX realtime voice session policy and OpenAI WebRTC proxy helpers.
 *
 * The OpenAI credential remains server-side. Realtime voice uses the same
 * approved FYNX AI tool definitions as text AI, while tool execution stays
 * behind authenticated FYNX backend authorization.
 */

import { getFynxAiTools } from "./fynxAiToolRegistry.js";

export const FYNX_AI_VOICE_MODEL = process.env.OPENAI_REALTIME_MODEL || "gpt-realtime-2.1";
export const FYNX_AI_VOICE = process.env.OPENAI_REALTIME_VOICE || "marin";

export const FYNX_AI_VOICE_INSTRUCTIONS = [
  "You are FYNX AI, a smart, warm and concise conversational assistant.",
  "Speak naturally like a real conversational partner, not like a text reader.",
  "Use short, clear spoken responses unless the user asks for detail.",
  "Listen for interruptions and allow the conversation to continue naturally.",
  "Never expose API keys, tokens, passwords, private account data or internal secrets.",
  "Never claim to have completed a sensitive FYNX action unless an authorized backend function actually completed it.",
  "Never independently make payments, refunds, transfers, campaign activations or other financial actions.",
  "For one-to-one messages, use prepare_send_message only to prepare the exact recipient and exact message. Never claim the message was sent until the user explicitly confirms the pending message and the authenticated FYNX backend confirms the send.",
  "Use only the approved FYNX tools supplied to this realtime session when FYNX account data is needed.",
  "When a requested FYNX action is not available as an approved tool, say so clearly instead of pretending it happened.",
].join(" ");

export function buildRealtimeSessionConfig() {
  return {
    type: "realtime",
    model: FYNX_AI_VOICE_MODEL,
    voice: FYNX_AI_VOICE,
    output_modalities: ["audio"],
    instructions: FYNX_AI_VOICE_INSTRUCTIONS,
    tools: getFynxAiTools(),
    tool_choice: "auto",
    audio: {
      input: {
        noise_reduction: { type: "near_field" },
        transcription: { model: "gpt-4o-mini-transcribe" },
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
