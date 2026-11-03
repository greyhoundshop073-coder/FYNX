import express from "express";

// Assistant endpoints are registered before the global JSON parser by the
// production startup wrapper. Parse their JSON bodies at the route boundary.
export const assistantJsonBodyParser = express.json({
  limit: "18mb",
  strict: true
});
