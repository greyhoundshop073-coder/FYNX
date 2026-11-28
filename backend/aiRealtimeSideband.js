import WebSocket from "ws";
import { settleFynxAiUsage } from "./fynxAiUsageBudget.js";

const OPENAI_REALTIME_PRICING = Object.freeze({
  "gpt-realtime-2.1": {
    inputAudio: 32, cachedInputAudio: 0.4, inputText: 4, cachedInputText: 0.4,
    outputAudio: 64, outputText: 24
  },
  "gpt-realtime-2.1-mini": {
    inputAudio: 10, cachedInputAudio: 0.3, inputText: 0.6, cachedInputText: 0.06,
    outputAudio: 20, outputText: 2.4
  }
});
const TRANSCRIPTION_USD_PER_MINUTE = Object.freeze({
  "gpt-live-transcribe": 0.017,
  "gpt-realtime-whisper": 0.017
});

function count(value) {
  const result = Number(value);
  return Number.isFinite(result) && result > 0 ? Math.trunc(result) : 0;
}

function seconds(value) {
  const result = Number(value);
  return Number.isFinite(result) && result > 0 ? result : 0;
}

export function createFynxRealtimeUsageAccumulator({ includeTranscriptionUsage = false } = {}) {
  const responseIds = new Set();
  const transcriptionIds = new Set();
  const totals = {
    inputTokens: 0, outputTokens: 0,
    inputAudioTokens: 0, inputTextTokens: 0,
    cachedInputAudioTokens: 0, cachedInputTextTokens: 0,
    outputAudioTokens: 0, outputTextTokens: 0,
    inputAudioSeconds: 0, responseCount: 0, transcriptionCount: 0
  };

  function consume(event) {
    if (!event || typeof event !== "object") return false;

    if (event.type === "response.done") {
      const response = event.response || {};
      const responseId = String(response.id || event.event_id || "");
      if (responseId && responseIds.has(responseId)) return false;
      if (responseId) responseIds.add(responseId);
      const usage = response.usage;
      if (!usage || typeof usage !== "object") return false;
      const input = usage.input_token_details || {};
      const cached = input.cached_tokens_details || {};
      const output = usage.output_token_details || {};
      totals.inputTokens += count(usage.input_tokens);
      totals.outputTokens += count(usage.output_tokens);
      totals.inputAudioTokens += count(input.audio_tokens);
      totals.inputTextTokens += count(input.text_tokens);
      totals.cachedInputAudioTokens += count(cached.audio_tokens);
      totals.cachedInputTextTokens += count(cached.text_tokens);
      totals.outputAudioTokens += count(output.audio_tokens);
      totals.outputTextTokens += count(output.text_tokens);
      totals.responseCount += 1;
      return true;
    }

    // Realtime transcription-only sessions do not create assistant Responses.
    // Their completed transcript event is the authoritative usage event.
    if (includeTranscriptionUsage && event.type === "conversation.item.input_audio_transcription.completed") {
      const transcriptionId = String(event.event_id || event.item_id || "");
      if (transcriptionId && transcriptionIds.has(transcriptionId)) return false;
      if (transcriptionId) transcriptionIds.add(transcriptionId);
      const usage = event.usage;
      if (!usage || typeof usage !== "object" || usage.type !== "tokens") return false;
      const input = usage.input_token_details || {};
      totals.inputTokens += count(usage.input_tokens);
      totals.outputTokens += count(usage.output_tokens);
      totals.inputAudioTokens += count(input.audio_tokens);
      totals.inputTextTokens += count(input.text_tokens);
      totals.transcriptionCount += 1;
      return true;
    }
    return false;
  }

  return {
    consume,
    snapshot() { return { ...totals }; }
  };
}

export function estimateFynxRealtimeUsageCostUsd({
  model = "gpt-realtime-2.1",
  transcriptionModel = process.env.OPENAI_TRANSCRIPTION_MODEL || "gpt-live-transcribe",
  usage = {}
} = {}) {
  const rates = OPENAI_REALTIME_PRICING[String(model)];
  if (!rates) return null;
  const cachedAudio = count(usage.cachedInputAudioTokens);
  const cachedText = count(usage.cachedInputTextTokens);
  const inputAudio = Math.max(0, count(usage.inputAudioTokens) - cachedAudio);
  const inputText = Math.max(0, count(usage.inputTextTokens) - cachedText);
  const outputAudio = count(usage.outputAudioTokens);
  const outputText = count(usage.outputTextTokens);
  const audioSeconds = seconds(usage.inputAudioSeconds);
  const transcriptionRate = TRANSCRIPTION_USD_PER_MINUTE[String(transcriptionModel)];
  if (audioSeconds > 0 && transcriptionRate === undefined) return null;
  const realtimeCost = (
    inputAudio * rates.inputAudio +
    cachedAudio * rates.cachedInputAudio +
    inputText * rates.inputText +
    cachedText * rates.cachedInputText +
    outputAudio * rates.outputAudio +
    outputText * rates.outputText
  ) / 1_000_000;
  const transcriptionCost = audioSeconds > 0 ? (audioSeconds / 60) * transcriptionRate : 0;
  return Number((realtimeCost + transcriptionCost).toFixed(8));
}

export function connectFynxRealtimeSideband({
  callId,
  apiKey = process.env.OPENAI_API_KEY || "",
  reservation,
  model = "gpt-realtime-2.1",
  includeTranscriptionUsage = false,
  databasePool,
  WebSocketImpl = WebSocket,
  connectTimeoutMs = 8_000,
  settle = settleFynxAiUsage,
  onFinalized = () => {}
} = {}) {
  if (!/^rtc_[A-Za-z0-9_-]{4,128}$/.test(String(callId || ""))) {
    return Promise.reject(new Error("A valid OpenAI Realtime call ID is required for usage monitoring."));
  }
  if (!apiKey) return Promise.reject(new Error("OPENAI_API_KEY is not configured."));
  if (!reservation?.id) return Promise.reject(new Error("A FYNX AI usage reservation is required."));
  if (typeof WebSocketImpl !== "function") return Promise.reject(new Error("Realtime usage monitor is unavailable."));

  const accumulator = createFynxRealtimeUsageAccumulator({ includeTranscriptionUsage });
  const url = "wss://api.openai.com/v1/realtime?call_id=" + encodeURIComponent(callId);
  const socket = new WebSocketImpl(url, {
    headers: { Authorization: "Bearer " + apiKey },
    handshakeTimeout: connectTimeoutMs,
    maxPayload: 1_048_576
  });

  let opened = false;
  let finalized = false;
  let finalizationPromise = null;
  let connectTimer = null;
  let requestedFinalStatus = "failed";

  const finalize = status => {
    if (finalizationPromise) return finalizationPromise;
    if (finalized) return Promise.resolve();
    finalized = true;
    requestedFinalStatus = status === "completed" ? "completed" : "failed";
    if (connectTimer) clearTimeout(connectTimer);
    const usage = accumulator.snapshot();
    const estimatedCostUsd = estimateFynxRealtimeUsageCostUsd({ model, usage });
    finalizationPromise = Promise.resolve(settle({
      reservation,
      status: requestedFinalStatus,
      inputTokens: usage.inputTokens,
      outputTokens: usage.outputTokens,
      inputAudioTokens: usage.inputAudioTokens,
      inputTextTokens: usage.inputTextTokens,
      cachedInputAudioTokens: usage.cachedInputAudioTokens,
      cachedInputTextTokens: usage.cachedInputTextTokens,
      outputAudioTokens: usage.outputAudioTokens,
      outputTextTokens: usage.outputTextTokens,
      inputAudioSeconds: usage.inputAudioSeconds,
      estimatedCostUsd,
      databasePool
    })).then(() => {
      onFinalized({ status: requestedFinalStatus, usage, estimatedCostUsd });
    }).catch(error => {
      console.error("[fynx-ai] realtime usage settlement failed", String(error?.message || "unknown").slice(0, 160));
      onFinalized({ status: "failed", usage, estimatedCostUsd, settlementFailed: true });
    });
    return finalizationPromise;
  };

  return new Promise((resolve, reject) => {
    let settledConnection = false;
    const rejectConnection = error => {
      if (settledConnection) return;
      settledConnection = true;
      if (connectTimer) clearTimeout(connectTimer);
      void finalize("failed").finally(() => reject(error));
    };

    connectTimer = setTimeout(() => {
      if (!opened) {
        try { socket.terminate(); } catch {}
        rejectConnection(new Error("OpenAI Realtime usage sideband did not connect in time."));
      }
    }, connectTimeoutMs);

    socket.on("open", () => {
      opened = true;
      if (connectTimer) clearTimeout(connectTimer);
      if (settledConnection) return;
      settledConnection = true;
      resolve({
        callId,
        expectFinish() { requestedFinalStatus = "completed"; },
        expectFailure() { requestedFinalStatus = "failed"; },
        finish(status = "completed") {
          requestedFinalStatus = status === "completed" ? "completed" : "failed";
          const finalization = finalize(requestedFinalStatus);
          try { socket.close(1000, "FYNX Realtime call ended"); } catch {}
          return finalization;
        },
        settled() { return finalizationPromise || Promise.resolve(); }
      });
    });

    socket.on("message", raw => {
      let event;
      try {
        const text = typeof raw === "string" ? raw : raw.toString("utf8");
        event = JSON.parse(text);
      } catch {
        return;
      }
      accumulator.consume(event);
    });

    socket.on("close", () => {
      void finalize(requestedFinalStatus);
    });

    socket.on("error", error => {
      if (!opened) {
        rejectConnection(new Error("OpenAI Realtime usage sideband could not connect: " + String(error?.message || "connection error")));
      } else {
        void finalize("failed");
        console.error("[fynx-ai] realtime sideband connection error", String(error?.message || "unknown").slice(0, 160));
      }
    });
  });
}
