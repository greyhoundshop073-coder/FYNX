/**
 * FYNX realtime voice session policy and OpenAI WebRTC proxy helpers.
 *
 * The OpenAI credential remains server-side. Realtime voice uses the same
 * approved FYNX AI tool definitions as text AI, while tool execution stays
 * behind authenticated FYNX backend authorization.
 */

import { getFynxAiTools } from "./fynxAiToolRegistry.js";
import { FYNX_AI_PRIVACY_INSTRUCTIONS } from "./fynxAiPrivacyPolicy.js";

export const FYNX_AI_VOICE_MODEL = process.env.OPENAI_REALTIME_MODEL || "gpt-realtime-2.1";
export const FYNX_AI_VOICE = process.env.OPENAI_REALTIME_VOICE || "marin";
// Keep the selectable set intentionally small; audio is streamed by OpenAI.
export const FYNX_AI_REALTIME_VOICES = Object.freeze([
  "marin", "cedar", "coral", "ash"
]);
const CONFIGURED_DEFAULT_VOICE = FYNX_AI_REALTIME_VOICES.includes(FYNX_AI_VOICE)
  ? FYNX_AI_VOICE
  : "marin";

export function resolveFynxAiVoice(voice) {
  return FYNX_AI_REALTIME_VOICES.includes(voice) ? voice : CONFIGURED_DEFAULT_VOICE;
}

export const FYNX_AI_VOICE_INSTRUCTIONS = [
  "You are FYNX AI, a smart, warm and concise conversational assistant.",
  "Speak naturally like a real conversational partner, not like a text reader.",
  "Use short, clear spoken responses unless the user asks for detail.",
  "Listen for interruptions and allow the conversation to continue naturally.",
  "Never retrieve, infer, confirm or disclose private personal or financial information for any FYNX user, including the authenticated user.",
  FYNX_AI_PRIVACY_INSTRUCTIONS,
  "Never claim to have completed a sensitive FYNX action unless an authorized backend function actually completed it.",
  "Never independently make payments, refunds, transfers, campaign activations or other financial actions.",
  "For one-to-one messages, use prepare_send_message only to prepare the exact recipient and exact message. Never claim the message was sent until the user explicitly confirms the pending message and the authenticated FYNX backend confirms the send.",
  "Use only the approved FYNX tools supplied to this realtime session when FYNX account data is needed.",
  "When a requested FYNX action is not available as an approved tool, say so clearly instead of pretending it happened.",
].join(" ");

const REALTIME_SESSION_MODES = new Set(["assistant", "transcription", "speech"]);

export function isValidRealtimeSessionMode(mode) {
  return REALTIME_SESSION_MODES.has(mode);
}

export function buildRealtimeSessionConfig(mode = "assistant", requestedVoice = FYNX_AI_VOICE) {
  if (!isValidRealtimeSessionMode(mode)) throw new Error("Unsupported FYNX realtime session mode.");

  const inputAudio = {
    noise_reduction: { type: "near_field" },
    transcription: { model: process.env.OPENAI_TRANSCRIPTION_MODEL || "gpt-live-transcribe" }
  };

  if (mode === "transcription") {
    return {
      type: "realtime",
      model: FYNX_AI_VOICE_MODEL,
      output_modalities: ["text"],
      instructions: [
        "You are FYNX's speech transcription transport, not the assistant.",
        "Transcribe the user's speech accurately and emit the input transcription event.",
        "Do not answer, summarize, reason, call tools, or generate an assistant response.",
        "FYNX Gemini is the only assistant answer provider."
      ].join(" "),
      tools: [],
      tool_choice: "none",
      audio: {
        input: {
          ...inputAudio,
          turn_detection: {
            type: "semantic_vad",
            eagerness: "medium",
            create_response: false,
            interrupt_response: false
          }
        }
      }
    };
  }

  if (mode === "speech") {
    return {
      type: "realtime",
      model: FYNX_AI_VOICE_MODEL,
      output_modalities: ["audio"],
      instructions: [
        "You are FYNX's speech output service, not the assistant.",
        "Only speak the exact text supplied in a speech request.",
        "Do not answer, summarize, paraphrase, add commentary, or call tools."
      ].join(" "),
      tools: [],
      tool_choice: "none",
      audio: {
        output: { voice: resolveFynxAiVoice(requestedVoice), format: { type: "audio/pcm", rate: 24000 } }
      }
    };
  }

  return {
    type: "realtime",
    model: FYNX_AI_VOICE_MODEL,
    output_modalities: ["audio"],
    instructions: FYNX_AI_VOICE_INSTRUCTIONS,
    tools: getFynxAiTools(),
    tool_choice: "auto",
    audio: {
      input: {
        ...inputAudio,
        turn_detection: { type: "semantic_vad", eagerness: "medium", create_response: true, interrupt_response: true }
      },
      output: { voice: resolveFynxAiVoice(FYNX_AI_VOICE) }
    }
  };
}

export function parseFynxRealtimeCallId(location) {
  if (typeof location !== "string" || !location.trim()) {
    throw new Error("OpenAI did not return a Realtime call location.");
  }
  let parsed;
  try {
    parsed = new URL(location, "https://api.openai.com");
  } catch {
    throw new Error("OpenAI returned an invalid Realtime call location.");
  }
  if (parsed.origin !== "https://api.openai.com") {
    throw new Error("OpenAI returned an unexpected Realtime call host.");
  }
  const match = parsed.pathname.match(/^\/v1\/realtime\/calls\/(rtc_[A-Za-z0-9_-]{4,128})\/?$/);
  if (!match) throw new Error("OpenAI returned an invalid Realtime call ID.");
  return match[1];
}

export async function createRealtimeCall(
  sdpOffer,
  mode = "assistant",
  voice = FYNX_AI_VOICE,
  { apiKey = process.env.OPENAI_API_KEY || "", fetchImpl = globalThis.fetch } = {}
) {
  if (!isValidRealtimeSessionMode(mode)) throw new Error("Unsupported FYNX realtime session mode.");
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured.");
  if (typeof sdpOffer !== "string" || !sdpOffer.trim() || sdpOffer.length > 200_000) {
    throw new Error("A valid WebRTC SDP offer is required.");
  }
  if (typeof fetchImpl !== "function") throw new Error("Realtime provider HTTP transport is unavailable.");

  const form = new FormData();
  form.append("sdp", sdpOffer);
  form.append("session", new Blob([JSON.stringify(buildRealtimeSessionConfig(mode, voice))], { type: "application/json" }));

  const response = await fetchImpl("https://api.openai.com/v1/realtime/calls", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}` },
    body: form
  });
  const answer = await response.text();
  if (!response.ok) throw new Error(`Realtime session failed (${response.status})`);
  const callId = parseFynxRealtimeCallId(response.headers?.get?.("Location"));
  return { answer, callId };
}

export async function createRealtimeAnswer(sdpOffer, mode = "assistant", voice = FYNX_AI_VOICE, options = {}) {
  const result = await createRealtimeCall(sdpOffer, mode, voice, options);
  return result.answer;
}

export async function hangupFynxRealtimeCall(
  callId,
  { apiKey = process.env.OPENAI_API_KEY || "", fetchImpl = globalThis.fetch } = {}
) {
  if (!/^rtc_[A-Za-z0-9_-]{4,128}$/.test(String(callId || ""))) {
    throw new Error("A valid FYNX Realtime call ID is required.");
  }
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured.");
  const response = await fetchImpl(
    "https://api.openai.com/v1/realtime/calls/" + encodeURIComponent(callId) + "/hangup",
    { method: "POST", headers: { Authorization: `Bearer ${apiKey}` } }
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(`Realtime call hangup failed (${response.status})`);
  }
  return { ended: response.ok, alreadyEnded: response.status === 404 };
}
