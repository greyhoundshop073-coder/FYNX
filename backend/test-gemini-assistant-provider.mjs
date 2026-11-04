import assert from "node:assert/strict";
import { runGeminiAssistant, toGeminiFunctionDeclarations, toGeminiSchema } from "./geminiAssistantProvider.js";

const originalKey = process.env.GEMINI_API_KEY;
const originalModel = process.env.GEMINI_MODEL;
process.env.GEMINI_API_KEY = "unit-test-only-key";
delete process.env.GEMINI_MODEL;

function fakeResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "content-type": "application/json" } });
}

function fakeStreamResponse(payloads, status = 200) {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      for (const payload of payloads) controller.enqueue(encoder.encode("data: " + JSON.stringify(payload) + "\n\n"));
      controller.close();
    }
  });
  return new Response(stream, { status, headers: { "content-type": "text/event-stream" } });
}

async function testTextImageHistoryAndToolSchema() {
  const calls = [];
  const result = await runGeminiAssistant({
    message: "Describe this image",
    userId: "17",
    history: [{ role: "user", text: "What is in this picture?" }, { role: "assistant", text: "I can help." }],
    context: { summary: "The user is testing image understanding.", currentTask: "Describe the photo." },
    imageInputs: [{ type: "image", dataUrl: "data:image/png;base64,aGVsbG8=" }],
    instructions: "FYNX assistant test instructions",
    toolDefinitions: [{
      type: "function", name: "get_my_profile", description: "Read the signed-in profile.",
      strict: true, parameters: { type: "object", properties: {}, additionalProperties: false }
    }],
    executeTool: async () => ({ ok: true }),
    fetchImpl: async (url, options) => {
      calls.push({ url, options, body: JSON.parse(options.body) });
      return fakeResponse({ candidates: [{ content: { role: "model", parts: [{ text: "This is a test image description." }] } }] });
    }
  });
  assert.equal(result.reply, "This is a test image description.");
  assert.equal(result.pendingAction, null);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent");
  assert.equal(calls[0].options.headers["x-goog-api-key"], "unit-test-only-key");
  assert.equal(calls[0].url.includes("unit-test-only-key"), false);
  assert.equal(calls[0].body.systemInstruction.parts[0].text, "FYNX assistant test instructions");
  assert.equal(calls[0].body.contents[0].role, "user");
  assert.equal(calls[0].body.contents[1].role, "model");
  const continuityText = calls[0].body.contents[0].parts[0].text;
  assert.ok(continuityText.indexOf("Conversation continuity excerpt") < continuityText.indexOf("First recent user turn"), "older continuity must precede recent conversation history");
  assert.match(continuityText, /Conversation continuity excerpt \(untrusted historical context/);
  assert.match(continuityText, /Most recent prior user request \(historical context only/);
  assert.ok(continuityText.length <= 1200);
  const latestParts = calls[0].body.contents.at(-1).parts;
  assert.equal(latestParts[0].text, "Describe this image");
  assert.deepEqual(latestParts[1].inlineData, { mimeType: "image/png", data: "aGVsbG8=" });
  assert.equal(calls[0].body.tools[0].functionDeclarations[0].name, "get_my_profile");
  assert.equal("additionalProperties" in calls[0].body.tools[0].functionDeclarations[0].parameters, false);
  assert.equal(calls[0].body.toolConfig.functionCallingConfig.mode, "AUTO");
}

async function testStreamingTextResponse() {
  const chunks = [];
  let requestedUrl = "";
  const result = await runGeminiAssistant({
    message: "Give me a short greeting",
    userId: "19",
    instructions: "FYNX streaming test instructions",
    executeTool: async () => ({}),
    onTextChunk: chunk => chunks.push(chunk),
    fetchImpl: async (url, options) => {
      requestedUrl = url;
      assert.equal(options.headers["x-goog-api-key"], "unit-test-only-key");
      return fakeStreamResponse([
        { candidates: [{ content: { role: "model", parts: [{ text: "internal thought", thought: true }, { text: "Hello " }] } }] },
        { candidates: [{ content: { role: "model", parts: [{ text: "from FYNX." }] }, finishReason: "STOP" }] }
      ]);
    }
  });

  assert.match(requestedUrl, /:streamGenerateContent\\?alt=sse$/);
  assert.equal(chunks.join(""), "Hello from FYNX.");
  assert.equal(result.reply, "Hello from FYNX.");
  assert.equal(chunks.join("").includes("internal thought"), false, "thought-only parts must never reach the user");
  assert.equal(result.pendingAction, null);
}

async function testFunctionCallingPreservesModelTurnAndExecutesOnlyApprovedTool() {
  const calls = [];
  let executed = 0;
  const result = await runGeminiAssistant({
    message: "Show my profile",
    userId: "42",
    instructions: "FYNX instructions",
    toolDefinitions: [{ type: "function", name: "get_my_profile", description: "Read my profile.", parameters: { type: "object", properties: {} } }],
    executeTool: async ({ name, argumentsJson, userId }) => {
      executed += 1;
      assert.equal(name, "get_my_profile");
      assert.equal(argumentsJson, "{}");
      assert.equal(userId, "42");
      return { username: "test_user", postCount: 3 };
    },
    fetchImpl: async (url, options) => {
      const body = JSON.parse(options.body);
      calls.push({ url, body });
      if (calls.length === 1) {
        return fakeResponse({
          candidates: [{
            content: {
              role: "model",
              parts: [{ functionCall: { name: "get_my_profile", args: {}, id: "profile-call-1" }, thoughtSignature: "test-thought-signature" }]
            }
          }]
        });
      }
      return fakeResponse({ candidates: [{ content: { role: "model", parts: [{ text: "Your profile is ready." }] } }] });
    }
  });
  assert.equal(result.reply, "Your profile is ready.");
  assert.equal(executed, 1);
  assert.equal(calls.length, 2);
  const secondContents = calls[1].body.contents;
  assert.equal(secondContents.at(-2).parts[0].functionCall.name, "get_my_profile");
  assert.equal(secondContents.at(-2).parts[0].thoughtSignature, "test-thought-signature");
  const responsePart = secondContents.at(-1).parts[0].functionResponse;
  assert.equal(responsePart.name, "get_my_profile");
  assert.equal(responsePart.id, "profile-call-1");
  assert.deepEqual(responsePart.response.result, { username: "test_user", postCount: 3 });
}

async function testPreparedMessageConfirmationActionIsPreserved() {
  let calls = 0;
  const result = await runGeminiAssistant({
    message: "Prepare a message",
    userId: "7",
    instructions: "FYNX instructions",
    toolDefinitions: [{ type: "function", name: "prepare_send_message", parameters: { type: "object", properties: {} } }],
    executeTool: async () => ({ confirmationRequired: true, actionId: "pending-123", message: "Hello" }),
    fetchImpl: async (url, options) => {
      calls += 1;
      if (calls === 1) {
        return fakeResponse({
          candidates: [{
            content: {
              role: "model",
              parts: [{ functionCall: { name: "prepare_send_message", args: { recipientUsername: "friend", message: "Hello" }, id: "prepare-1" } }]
            }
          }]
        });
      }
      const body = JSON.parse(options.body);
      assert.equal(body.contents.at(-1).parts[0].functionResponse.response.result.actionId, "pending-123");
      return fakeResponse({ candidates: [{ content: { role: "model", parts: [{ text: "I prepared the message for your confirmation." }] } }] });
    }
  });
  assert.equal(calls, 2);
  assert.equal(result.pendingAction.actionId, "pending-123");
}

async function testMissingKeyFailsClosed() {
  const current = process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_API_KEY;
  let requested = false;
  await assert.rejects(
    runGeminiAssistant({
      message: "Hello", userId: "1", instructions: "test",
      executeTool: async () => ({}),
      fetchImpl: async () => { requested = true; return fakeResponse({}); }
    }),
    /Gemini API key is not configured/
  );
  assert.equal(requested, false);
  process.env.GEMINI_API_KEY = current;
}

async function testEmptyAndFailedProviderResponsesAreRejected() {
  await assert.rejects(
    runGeminiAssistant({
      message: "Hello", userId: "1", instructions: "test", executeTool: async () => ({}),
      fetchImpl: async () => fakeResponse({ candidates: [{ content: { role: "model", parts: [] } }] })
    }),
    /empty response/
  );
  await assert.rejects(
    runGeminiAssistant({
      message: "Hello", userId: "1", instructions: "test", executeTool: async () => ({}),
      fetchImpl: async () => fakeResponse({ error: { message: "hidden detail" } }, 401)
    }),
    /Gemini provider request failed \(401\)/
  );
}

async function testSchemaConversionDropsUnsupportedOpenApiFields() {
  const converted = toGeminiSchema({
    type: "object",
    additionalProperties: false,
    properties: { username: { type: "string", description: "Account name" }, limit: { type: "integer" } },
    required: ["username"]
  });
  assert.deepEqual(converted, {
    type: "OBJECT",
    properties: { username: { type: "STRING", description: "Account name" }, limit: { type: "INTEGER" } },
    required: ["username"]
  });
  const declarations = toGeminiFunctionDeclarations([
    { type: "function", name: "read_profile", description: "Read profile.", parameters: { type: "object", properties: {} } },
    { type: "not-a-function", name: "skip" }
  ]);
  assert.equal(declarations.length, 1);
  assert.equal(declarations[0].name, "read_profile");
}

try {
  await testTextImageHistoryAndToolSchema();
  await testStreamingTextResponse();
  await testFunctionCallingPreservesModelTurnAndExecutesOnlyApprovedTool();
  await testPreparedMessageConfirmationActionIsPreserved();
  await testMissingKeyFailsClosed();
  await testEmptyAndFailedProviderResponsesAreRejected();
  await testSchemaConversionDropsUnsupportedOpenApiFields();
  console.log("Gemini assistant provider tests GREEN: 7 suites passed");
} finally {
  if (originalKey === undefined) delete process.env.GEMINI_API_KEY;
  else process.env.GEMINI_API_KEY = originalKey;
  if (originalModel === undefined) delete process.env.GEMINI_MODEL;
  else process.env.GEMINI_MODEL = originalModel;
}
