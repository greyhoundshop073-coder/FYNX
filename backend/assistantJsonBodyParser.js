import express from "express";

// AI routes are registered before the application's global JSON middleware by
// the production startup wrapper. Attach this parser directly to each JSON
// endpoint so request bodies are available regardless of global middleware order.
export const assistantJsonBodyParser = express.json({
  limit: "18mb",
  strict: true
});
