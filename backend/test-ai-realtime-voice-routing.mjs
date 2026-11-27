import assert from "node:assert/strict";
import { buildRealtimeSessionConfig, isValidRealtimeSessionMode } from "./aiVoiceSession.js";

assert.equal(isValidRealtimeSessionMode("assistant"), true);
assert.equal(isValidRealtimeSessionMode("transcription"), true);
assert.equal(isValidRealtimeSessionMode("speech"), true);
assert.equal(isValidRealtimeSessionMode("unexpected"), false);

const transcription = buildRealtimeSessionConfig("transcription");
assert.deepEqual(transcription.output_modalities, ["text"]);
assert.deepEqual(transcription.tools, []);
assert.equal(transcription.tool_choice, "none");
assert.equal(transcription.audio.input.turn_detection.create_response, false);
assert.equal(transcription.audio.input.turn_detection.interrupt_response, false);
assert.match(transcription.instructions, /FYNX Gemini is the only assistant answer provider/);

const speech = buildRealtimeSessionConfig("speech");
assert.deepEqual(speech.output_modalities, ["audio"]);
assert.deepEqual(speech.tools, []);
assert.equal(speech.tool_choice, "none");
assert.equal(speech.audio.output.format.type, "audio/pcm");
assert.match(speech.instructions, /Only speak the exact text supplied/);

const legacy = buildRealtimeSessionConfig("assistant");
assert.deepEqual(legacy.output_modalities, ["audio"]);
assert.ok(legacy.tools.length > 0);
assert.equal(legacy.tool_choice, "auto");
assert.throws(() => buildRealtimeSessionConfig("unexpected"), /Unsupported/);

console.log("FYNX AI voice routing tests GREEN: transcription cannot answer or call tools; speech only speaks supplied text; legacy mode remains compatible");
