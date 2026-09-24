import express from "express";
// Profile-photo media access follows the owner's profile visibility and block state.
import http from "http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocketServer } from "ws";
import { registerSocialRoutes } from "./socialRoutes.js";
import { registerMarketplaceTransactionRoutes } from "./marketplaceTransactions.js";
import { registerMarketplaceReputationRoutes } from "./marketplaceReputation.js";
import { registerMarketplaceCompletionRoutes } from "./marketplaceCompletion.js";
import { registerMoneyPlannerRoutes } from "./moneyPlanner.js";
import { registerMarketplaceAdvertisingRoutes } from "./marketplaceAdvertising.js";
import { registerTrustSafetyRoutes } from "./trustSafety.js";

const { Pool } = pg;
const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/realtime" });

// Keep the API deliberately strict: large media is uploaded separately and authenticated.
app.disable("x-powered-by");
app.use((req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("X-Frame-Options", "DENY");
  if (process.env.NODE_ENV === "production") res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  next();
});
app.use(express.json({ limit: "18mb", strict: true }));
app.use((req, res, next) => {
  if (req.method !== "GET" && req.method !== "HEAD") res.setHeader("Cache-Control", "no-store");
  next();
});
app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));
app.use("/api/assistant", rateLimit("assistant", RATE_LIMITS.assistant));
app.use("/api/media", rateLimit("media", RATE_LIMITS.media));
app.use("/api/messages", rateLimit("messages", RATE_LIMITS.messages));

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "";