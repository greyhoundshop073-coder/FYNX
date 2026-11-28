import assert from "node:assert/strict";
import {
  buildRealtimeSessionConfig, isValidRealtimeSessionMode,
  FYNX_AI_REALTIME_VOICES, resolveFynxAiVoice,
  parseFynxRealtimeCallId, createRealtimeCall, createRealtimeAnswer,
  hangupFynxRealtimeCall
} from "./aiVoiceSession.js";

assert.equal(isValidRealtimeSessionMode("assistant"), true);
assert.equal(isValidRealtimeSessionMode("transcription"), true);
assert.equal(isValidRealtimeSessionMode("speech"), true);
assert.equal(isValidRealtimeSessionMode("unexpected"), false);

const transcription = buildRealtimeSessionConfig("transcription");
assert.equal(transcription.audio.input.transcription.model, "gpt-live-transcribe");
assert.deepEqual(transcription.output_modalities, ["text"]);
assert.deepEqual(transcription.tools, []);
assert.equal(transcription.tool_choice, "none");
assert.equal(transcription.audio.input.turn_detection.create_response, false);
assert.equal(transcription.audio.input.turn_detection.interrupt_response, false);
assert.match(transcription.instructions, /FYNX Gemini is the only assistant answer provider/);

assert.deepEqual(FYNX_AI_REALTIME_VOICES, ["marin", "cedar", "coral", "ash"]);
assert.equal(resolveFynxAiVoice("cedar"), "cedar");
assert.ok(FYNX_AI_REALTIME_VOICES.includes(resolveFynxAiVoice("not-a-voice")));
assert.ok(FYNX_AI_REALTIME_VOICES.includes(buildRealtimeSessionConfig("assistant").audio.output.voice));

const speech = buildRealtimeSessionConfig("speech", "cedar");
assert.deepEqual(speech.output_modalities, ["audio"]);
assert.equal(speech.audio.output.voice, "cedar");
assert.deepEqual(speech.tools, []);
assert.equal(speech.tool_choice, "none");
assert.equal(speech.audio.output.format.type, "audio/pcm");
assert.match(speech.instructions, /Only speak the exact text supplied/);

const legacy = buildRealtimeSessionConfig("assistant");
assert.deepEqual(legacy.output_modalities, ["audio"]);
assert.equal(legacy.audio.output.voice, "marin");
assert.ok(legacy.tools.length > 0);
assert.equal(legacy.tool_choice, "auto");
assert.throws(() => buildRealtimeSessionConfig("unexpected"), /Unsupported/);
assert.equal(parseFynxRealtimeCallId("/v1/realtime/calls/rtc_123456"), "rtc_123456");
assert.equal(parseFynxRealtimeCallId("https://api.openai.com/v1/realtime/calls/rtc_call-456_abc"), "rtc_call-456_abc");
assert.throws(() => parseFynxRealtimeCallId("https://example.com/v1/realtime/calls/rtc_123456"), /unexpected/);
assert.throws(() => parseFynxRealtimeCallId("/v1/realtime/calls/not-a-call"), /invalid/);

const fakeCreateFetch = async (url, options) => {
  assert.equal(url, "https://api.openai.com/v1/realtime/calls");
  assert.equal(options.method, "POST");
  assert.equal(options.headers.Authorization, "Bearer test-provider-key");
  assert.ok(options.body instanceof FormData);
  assert.equal(options.body.get("sdp"), "v=0 test-offer");
  return new Response("v=0 test-answer", {
    status: 201,
    headers: { Location: "/v1/realtime/calls/rtc_testcall123" }
  });
};
const created = await createRealtimeCall(
  "v=0 test-offer", "assistant", "marin",
  { apiKey: "test-provider-key", fetchImpl: fakeCreateFetch }
);
assert.deepEqual(created, { answer: "v=0 test-answer", callId: "rtc_testcall123" });
const legacyAnswer = await createRealtimeAnswer(
  "v=0 test-offer", "speech", "cedar",
  { apiKey: "test-provider-key", fetchImpl: fakeCreateFetch }
);
assert.equal(legacyAnswer, "v=0 test-answer");
let hangupRequest;
await hangupFynxRealtimeCall("rtc_testcall123", {
  apiKey: "test-provider-key",
  fetchImpl: async (url, options) => {
    hangupRequest = { url, options };
    return new Response(null, { status: 200 });
  }
});
assert.equal(hangupRequest.url, "https://api.openai.com/v1/realtime/calls/rtc_testcall123/hangup");
assert.equal(hangupRequest.options.method, "POST");
assert.equal(hangupRequest.options.headers.Authorization, "Bearer test-provider-key");
await assert.rejects(() => hangupFynxRealtimeCall("invalid", { apiKey: "test-provider-key" }), /valid FYNX Realtime call ID/);


console.log("FYNX AI voice routing tests GREEN: transcription cannot answer or call tools; speech only speaks supplied text; legacy mode remains compatible");
