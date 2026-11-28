import assert from "node:assert/strict";
import {
  createFynxRealtimeUsageAccumulator,
  estimateFynxRealtimeUsageCostUsd,
  connectFynxRealtimeSideband
} from "./aiRealtimeSideband.js";

const responseEvent = {
  type: "response.done",
  event_id: "evt_response_1",
  response: {
    id: "resp_1",
    status: "completed",
    usage: {
      input_tokens: 270,
      output_tokens: 120,
      input_token_details: {
        audio_tokens: 100,
        text_tokens: 170,
        cached_tokens: 25,
        cached_tokens_details: { audio_tokens: 10, text_tokens: 15 }
      },
      output_token_details: { audio_tokens: 60, text_tokens: 60 }
    }
  }
};

const assistantUsage = createFynxRealtimeUsageAccumulator();
assert.equal(assistantUsage.consume(responseEvent), true);
assert.equal(assistantUsage.consume(responseEvent), false, "duplicate response usage must not be counted twice");
assert.deepEqual(assistantUsage.snapshot(), {
  inputTokens: 270,
  outputTokens: 120,
  inputAudioTokens: 100,
  inputTextTokens: 170,
  cachedInputAudioTokens: 10,
  cachedInputTextTokens: 15,
  outputAudioTokens: 60,
  outputTextTokens: 60,
  inputAudioSeconds: 0,
  responseCount: 1,
  transcriptionCount: 0
});

const transcriptionUsage = createFynxRealtimeUsageAccumulator({ includeTranscriptionUsage: true });
const transcriptionEvent = {
  type: "conversation.item.input_audio_transcription.completed",
  event_id: "evt_transcription_1",
  item_id: "item_1",
  usage: {
    type: "tokens",
    total_tokens: 14,
    input_tokens: 11,
    input_token_details: { audio_tokens: 11, text_tokens: 0 },
    output_tokens: 3
  }
};
assert.equal(transcriptionUsage.consume(transcriptionEvent), true);
assert.equal(transcriptionUsage.consume(transcriptionEvent), false, "duplicate transcription usage must not be counted twice");
assert.equal(transcriptionUsage.snapshot().inputAudioTokens, 11);
assert.equal(transcriptionUsage.snapshot().outputTokens, 3);
assert.equal(createFynxRealtimeUsageAccumulator().consume(transcriptionEvent), false, "assistant sessions must not double count transcription events");

const priced = estimateFynxRealtimeUsageCostUsd({
  model: "gpt-realtime-2.1",
  usage: assistantUsage.snapshot()
});
const expected = (
  (100 - 10) * 32 + 10 * 0.4 +
  (170 - 15) * 4 + 15 * 0.4 +
  60 * 64 + 60 * 24
) / 1_000_000;
assert.equal(priced, Number(expected.toFixed(8)));
assert.equal(estimateFynxRealtimeUsageCostUsd({ model: "unknown", usage: {} }), null);

class FakeWebSocket {
  static instances = [];
  constructor(url, options) {
    this.url = url;
    this.options = options;
    this.listeners = new Map();
    this.closed = false;
    FakeWebSocket.instances.push(this);
  }
  on(event, callback) {
    const list = this.listeners.get(event) || [];
    list.push(callback);
    this.listeners.set(event, list);
    return this;
  }
  emit(event, ...args) {
    for (const callback of this.listeners.get(event) || []) callback(...args);
  }
  close() {
    if (this.closed) return;
    this.closed = true;
    this.emit("close", 1000, "closed");
  }
  terminate() {
    this.close();
  }
}

const settlements = [];
let finalized;
const connectionPromise = connectFynxRealtimeSideband({
  callId: "rtc_testcall123",
  apiKey: "test-openai-key",
  reservation: { id: "reservation-1", userId: "user-1" },
  databasePool: {},
  WebSocketImpl: FakeWebSocket,
  settle: async input => settlements.push(input),
  onFinalized: value => { finalized = value; }
});
const socket = FakeWebSocket.instances.at(-1);
assert.equal(socket.url, "wss://api.openai.com/v1/realtime?call_id=rtc_testcall123");
assert.equal(socket.options.headers.Authorization, "Bearer test-openai-key");
socket.emit("open");
const monitor = await connectionPromise;
socket.emit("message", Buffer.from(JSON.stringify(responseEvent), "utf8"));
monitor.expectFinish();
socket.close();
await monitor.settled();
await monitor.finish("completed");
assert.equal(settlements.length, 1);
assert.equal(settlements[0].status, "completed");
assert.equal(settlements[0].inputAudioTokens, 100);
assert.equal(settlements[0].cachedInputTextTokens, 15);
assert.equal(settlements[0].outputAudioTokens, 60);
assert.equal(settlements[0].estimatedCostUsd, priced);
assert.equal(finalized.status, "completed");

await assert.rejects(
  () => connectFynxRealtimeSideband({
    callId: "invalid",
    apiKey: "test-openai-key",
    reservation: { id: "reservation-2" },
    WebSocketImpl: FakeWebSocket
  }),
  /valid OpenAI Realtime call ID/
);

console.log("FYNX Realtime sideband tests GREEN: usage aggregation, cached token accounting, duplicate-event protection, pricing, authenticated call-ID validation and final settlement");
