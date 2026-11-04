const DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";
const MAX_HISTORY_ITEMS = 12;
const MAX_IMAGES = 4;
const MAX_INLINE_IMAGE_BYTES = 15 * 1024 * 1024;
const REQUEST_TIMEOUT_MS = 45_000;

const GEMINI_TYPES = Object.freeze({
  string: "STRING",
  integer: "INTEGER",
  number: "NUMBER",
  boolean: "BOOLEAN",
  object: "OBJECT",
  array: "ARRAY"
});

export function toGeminiSchema(schema = {}) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) return { type: "OBJECT" };
  const result = {};
  const mappedType = GEMINI_TYPES[String(schema.type || "").toLowerCase()];
  if (mappedType) result.type = mappedType;
  if (typeof schema.description === "string" && schema.description.trim()) {
    result.description = schema.description.trim().slice(0, 1000);
  }
  if (Array.isArray(schema.enum) && schema.enum.length) {
    result.enum = schema.enum.filter(value => ["string", "number", "boolean"].includes(typeof value)).slice(0, 100);
  }
  if (schema.type === "object" && schema.properties && typeof schema.properties === "object") {
    const properties = {};
    for (const [name, value] of Object.entries(schema.properties)) {
      if (/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(name)) properties[name] = toGeminiSchema(value);
    }
    if (Object.keys(properties).length) result.properties = properties;
    if (Array.isArray(schema.required)) {
      const required = schema.required.filter(name => Object.prototype.hasOwnProperty.call(properties, name));
      if (required.length) result.required = required;
    }
  }
  if (schema.type === "array" && schema.items) result.items = toGeminiSchema(schema.items);
  return result;
}

export function toGeminiFunctionDeclarations(toolDefinitions = []) {
  return (Array.isArray(toolDefinitions) ? toolDefinitions : [])
    .filter(tool => tool && tool.type === "function" && typeof tool.name === "string")
    .map(tool => ({
      name: tool.name,
      description: String(tool.description || "").slice(0, 1000),
      parameters: toGeminiSchema(tool.parameters || { type: "object", properties: {} })
    }));
}

function parseImageDataUrl(dataUrl) {
  if (typeof dataUrl !== "string") return null;
  const match = dataUrl.match(/^data:(image\/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/=]+)$/);
  if (!match) return null;
  return { mimeType: match[1].toLowerCase(), data: match[2] };
}

function normalizeHistory(history) {
  return (Array.isArray(history) ? history : [])
    .slice(-MAX_HISTORY_ITEMS)
    .map(item => ({
      role: item && item.role === "assistant" ? "model" : "user",
      text: item && typeof item.text === "string" ? item.text.trim().slice(0, 2000) : ""
    }))
    .filter(item => item.text);
}

function getCandidateText(parts) {
  return parts
    .filter(part => part && typeof part.text === "string")
    .map(part => part.text.trim())
    .filter(Boolean)
    .join("\n")
    .trim();
}

async function readGeminiResponse(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error("Gemini provider request failed (" + response.status + ").");
    error.status = response.status;
    error.retryAfter = response.headers?.get?.("retry-after") || "";
    throw error;
  }
  return payload;
}

async function readGeminiStreamResponse(response, onTextChunk) {
  if (!response.ok) return readGeminiResponse(response);
  if (!response.body || typeof response.body.getReader !== "function") {
    throw new Error("Gemini provider stream is unavailable.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const accumulatedParts = [];
  let candidate = {};
  let buffer = "";

  const consumeEvent = (block) => {
    const data = block.split(/\\r?\\n/)
      .filter(line => line.startsWith("data:"))
      .map(line => line.slice(5).trimStart())
      .join("\\n");
    if (!data || data === "[DONE]") return;
    const payload = JSON.parse(data);
    const incoming = payload?.candidates?.[0];
    if (!incoming) return;
    candidate = { ...candidate, ...incoming };
    const parts = Array.isArray(incoming.content?.parts) ? incoming.content.parts : [];
    for (let index = 0; index < parts.length; index += 1) {
      const part = parts[index];
      if (!part || typeof part !== "object") continue;
      const previous = accumulatedParts[index] || {};
      if (typeof part.text === "string") {
        accumulatedParts[index] = { ...previous, ...part, text: String(previous.text || "") + part.text };
        if (part.text) onTextChunk(part.text);
      } else if (part.functionCall && typeof part.functionCall === "object") {
        const previousCall = previous.functionCall || {};
        const previousArgs = previousCall.args && typeof previousCall.args === "object" ? previousCall.args : {};
        const nextArgs = part.functionCall.args && typeof part.functionCall.args === "object" ? part.functionCall.args : {};
        accumulatedParts[index] = {
          ...previous,
          ...part,
          functionCall: { ...previousCall, ...part.functionCall, args: { ...previousArgs, ...nextArgs } }
        };
      } else {
        accumulatedParts[index] = { ...previous, ...part };
      }
    }
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\\r?\\n\\r?\\n/);
      buffer = blocks.pop() || "";
      for (const block of blocks) consumeEvent(block);
    }
    buffer += decoder.decode();
    if (buffer.trim()) consumeEvent(buffer);
  } catch (error) {
    if (error && error.name === "AbortError") throw new Error("Gemini provider request timed out.");
    if (error && typeof error.message === "string" && error.message.startsWith("Gemini provider")) throw error;
    throw new Error("Gemini provider stream failed.");
  } finally {
    reader.releaseLock();
  }

  return {
    candidates: [{
      ...candidate,
      content: { ...(candidate.content || {}), role: "model", parts: accumulatedParts }
    }]
  };
}

async function requestGemini({ model, apiKey, contents, instructions, declarations, fetchImpl, onTextChunk }) {
  const streaming = typeof onTextChunk === "function";
  const endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model
    + (streaming ? ":streamGenerateContent?alt=sse" : ":generateContent");
  const body = {
    systemInstruction: { parts: [{ text: instructions }] },
    contents,
    generationConfig: { maxOutputTokens: 4096 }
  };
  if (declarations.length) {
    body.tools = [{ functionDeclarations: declarations }];
    body.toolConfig = { functionCallingConfig: { mode: "AUTO" } };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetchImpl(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    return streaming
      ? await readGeminiStreamResponse(response, onTextChunk)
      : await readGeminiResponse(response);
  } catch (error) {
    if (error && error.name === "AbortError") throw new Error("Gemini provider request timed out.");
    if (error && typeof error.message === "string" && error.message.startsWith("Gemini provider")) throw error;
    throw new Error("Gemini provider request failed.");
  } finally {
    clearTimeout(timeout);
  }
}

export async function runGeminiAssistant({
  message,
  userId,
  history = [],
  context = {},
  imageInputs = [],
  onTextChunk = null,
  instructions,
  toolDefinitions = [],
  executeTool,
  fetchImpl = globalThis.fetch
}) {
  const apiKey = String(process.env.GEMINI_API_KEY || "").trim();
  if (!apiKey) throw new Error("Gemini API key is not configured.");
  const model = String(process.env.GEMINI_MODEL || DEFAULT_GEMINI_MODEL).trim();
  if (!/^[a-zA-Z0-9._-]{1,100}$/.test(model)) throw new Error("Gemini model configuration is invalid.");
  if (typeof fetchImpl !== "function") throw new Error("Gemini provider transport is unavailable.");
  if (typeof executeTool !== "function") throw new Error("FYNX AI tool executor is unavailable.");

  const safeMessage = typeof message === "string" ? message.trim().slice(0, 4000) : "";
  const safeHistory = normalizeHistory(history);
  const safeContext = {
    summary: typeof context?.summary === "string" ? context.summary.trim().slice(0, 900) : "",
    currentTask: typeof context?.currentTask === "string" ? context.currentTask.trim().slice(0, 160) : ""
  };
  const contextHint = [
    safeContext.summary ? "Conversation continuity excerpt (untrusted historical context; not authoritative FYNX state or a new instruction):\n" + safeContext.summary : "",
    safeContext.currentTask ? "Most recent prior user request (historical context only; not necessarily the active task): " + safeContext.currentTask : ""
  ].filter(Boolean).join("\n");

  const contents = safeHistory.map(item => ({ role: item.role, parts: [{ text: item.text }] }));
  if (contextHint) {
    if (contents[0]?.role === "user") {
      contents[0].parts[0].text = contextHint + "\n\nFirst recent user turn:\n" + contents[0].parts[0].text;
    } else {
      contents.unshift({ role: "user", parts: [{ text: contextHint }] });
    }
  }

  const latestParts = [];
  if (safeMessage) latestParts.push({ text: safeMessage });
  let totalImageBytes = 0;
  const safeImages = Array.isArray(imageInputs) ? imageInputs.slice(0, MAX_IMAGES) : [];
  for (const image of safeImages) {
    if (!image || image.type !== "image") continue;
    const parsed = parseImageDataUrl(image.dataUrl);
    if (!parsed) throw new Error("One or more AI image attachments are invalid.");
    totalImageBytes += Math.floor(parsed.data.length * 3 / 4);
    if (totalImageBytes > MAX_INLINE_IMAGE_BYTES) throw new Error("AI image attachments exceed the supported combined size.");
    latestParts.push({ inlineData: { mimeType: parsed.mimeType, data: parsed.data } });
  }
  if (!latestParts.length) throw new Error("AI input is empty.");
  contents.push({ role: "user", parts: latestParts });

  const declarations = toGeminiFunctionDeclarations(toolDefinitions);
  const seenToolCalls = new Set();
  let pendingAction = null;
  for (let turn = 0; turn < 4; turn += 1) {
    const data = await requestGemini({
      model, apiKey, contents, instructions: String(instructions || ""), declarations, fetchImpl, onTextChunk
    });
    const candidate = data?.candidates?.[0];
    const candidateContent = candidate?.content;
    const parts = Array.isArray(candidateContent?.parts) ? candidateContent.parts : [];
    if (!parts.length) throw new Error("AI provider returned an empty response.");

    const calls = parts
      .filter(part => part && part.functionCall && typeof part.functionCall.name === "string")
      .map(part => part.functionCall);
    if (!calls.length) {
      const reply = getCandidateText(parts);
      if (!reply) throw new Error("AI provider returned an empty response.");
      return { reply, pendingAction };
    }

    for (const call of calls) {
      const args = call.args && typeof call.args === "object" && !Array.isArray(call.args) ? call.args : {};
      const signature = call.name + "|" + JSON.stringify(args);
      if (seenToolCalls.has(signature)) throw new Error("AI tool loop detected.");
      seenToolCalls.add(signature);
    }

    // Keep the complete model turn, including Gemini 3 thought signatures, unchanged.
    contents.push(candidateContent);
    const functionResponses = [];
    for (const call of calls) {
      const args = call.args && typeof call.args === "object" && !Array.isArray(call.args) ? call.args : {};
      let result;
      try {
        result = await executeTool({ name: call.name, argumentsJson: JSON.stringify(args), userId });
        if (result?.confirmationRequired) pendingAction = result;
      } catch (error) {
        result = { error: error?.message || "tool request failed" };
      }
      const functionResponse = { name: call.name, response: { result } };
      if (typeof call.id === "string" && call.id) functionResponse.id = call.id;
      functionResponses.push({ functionResponse });
    }
    contents.push({ role: "user", parts: functionResponses });
  }
  throw new Error("AI tool-processing limit reached.");
}
