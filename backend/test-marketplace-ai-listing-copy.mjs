import assert from "node:assert/strict";
import {
  buildMarketplaceAiListingCopyPrompt,
  generateMarketplaceAiListingCopy,
  normalizeMarketplaceAiListingCopyInput,
  parseMarketplaceAiListingCopyReply,
  MarketplaceAiListingCopyInputError
} from "./fynxAiToolRegistry.js";

const source = {
  title: "Vintage leather shoulder bag",
  category: "Fashion",
  subcategory: "Clothing",
  condition: "USED",
  description: "Genuine seller notes: brown leather shoulder bag, visible signs of use. Call +234 803 123 4567 or email seller@example.com.",
  specifications: [
    { label: "Colour", value: "Brown" },
    { label: "Material", value: "Leather" }
  ],
  // These fields must never be used as seller-copy context.
  price: 45000,
  currency: "NGN",
  quantity: 3,
  location: "Port Harcourt",
  walletBalance: 900000,
  privateProfile: "must not be read"
};

const normalized = normalizeMarketplaceAiListingCopyInput(source);
assert.equal(normalized.title, source.title);
assert.equal(normalized.category, "Fashion");
assert.equal(normalized.condition, "USED");
assert.ok(!normalized.description.includes("+234 803 123 4567"), "telephone numbers must be removed before provider context");
assert.ok(!normalized.description.includes("seller@example.com"), "email addresses must be removed before provider context");
assert.equal(Object.keys(normalized).sort().join(","), "category,condition,description,specifications,subcategory,title", "only approved seller-entered product fields may enter AI context");

const prompt = buildMarketplaceAiListingCopyPrompt(normalized);
assert.ok(prompt.includes("PRODUCT NAME: " + source.title));
assert.ok(prompt.includes("CATEGORY: Fashion"));
assert.ok(prompt.includes("CONDITION: USED"));
assert.ok(prompt.includes("Colour: Brown"));
assert.ok(!prompt.includes("45000"), "price must not be sent to Gemini");
assert.ok(!prompt.includes("NGN"), "currency must not be sent to Gemini");
assert.ok(!prompt.includes("Port Harcourt"), "seller location must not be sent to Gemini");
assert.ok(!prompt.includes("900000"), "wallet information must not be sent to Gemini");
assert.ok(!prompt.includes("must not be read"), "private account information must not be sent to Gemini");
assert.ok(prompt.includes("Never describe a used or refurbished item as new"));
assert.ok(prompt.includes("Do not invent or assume brand"));

let providerRequest = null;
const suggestion = await generateMarketplaceAiListingCopy({
  input: source,
  userId: "42",
  assistantRunner: async (request) => {
    providerRequest = request;
    return {
      reply: JSON.stringify({
        title: "Brown Leather Shoulder Bag",
        description: "A brown leather shoulder bag with a practical everyday shape. The seller notes visible signs of use, so please review the photos and condition details before buying."
      })
    };
  }
});
assert.equal(suggestion.title, "Brown Leather Shoulder Bag");
assert.ok(suggestion.description.length >= 20);
assert.equal(providerRequest.userId, "42");
assert.deepEqual(providerRequest.history, []);
assert.deepEqual(providerRequest.imageInputs, []);
assert.deepEqual(providerRequest.toolDefinitions, [], "seller copy must not have database or action tools");
assert.ok(providerRequest.instructions.includes("FYNX PRIVACY BOUNDARY"));
assert.ok(providerRequest.instructions.includes("Never publish, save or modify a listing"));
assert.ok(!providerRequest.message.includes("45000"));
assert.ok(!providerRequest.message.includes("Port Harcourt"));
assert.ok(!providerRequest.message.includes("seller@example.com"));

const fenced = parseMarketplaceAiListingCopyReply(
  'Here is the draft:\n```json\n{"title":"Desk Lamp","description":"A compact desk lamp for everyday reading and task lighting. Check the supplied photos for its exact finish."}\n```'
);
assert.equal(fenced.title, "Desk Lamp");

assert.throws(
  () => normalizeMarketplaceAiListingCopyInput({ ...source, title: " " }),
  MarketplaceAiListingCopyInputError
);
assert.throws(
  () => normalizeMarketplaceAiListingCopyInput({ ...source, category: "Private Accounts" }),
  MarketplaceAiListingCopyInputError
);
assert.throws(
  () => normalizeMarketplaceAiListingCopyInput({ ...source, condition: "FACTORY PERFECT" }),
  MarketplaceAiListingCopyInputError
);
assert.throws(
  () => normalizeMarketplaceAiListingCopyInput({ ...source, specifications: Array.from({ length: 13 }, (_, i) => ({ label: "Spec " + i, value: "Value" })) }),
  MarketplaceAiListingCopyInputError
);
assert.throws(
  () => parseMarketplaceAiListingCopyReply('{"title":"x","description":"too short"}'),
  /incomplete listing suggestion/
);
await assert.rejects(
  generateMarketplaceAiListingCopy({ input: source, userId: "", assistantRunner: async () => ({ reply: "{}" }) }),
  /authenticated user is required/
);

console.log("Marketplace AI listing-copy tests GREEN: scoped product facts, contact redaction, no private data/tools, strict preview output and validation passed");
