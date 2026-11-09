import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  functions: {
    fynxapi: {
      name: "FYNX testing backend",
      source: "./neon-function.js",
      runtime: "nodejs24",
      env: {
        NODE_ENV: "production",
        JWT_SECRET: process.env.FYNX_JWT_SECRET!,
        GEMINI_API_KEY: process.env.FYNX_GEMINI_API_KEY!,
        PAYSTACK_SECRET_KEY: process.env.FYNX_PAYSTACK_SECRET_KEY!,
        FYNX_OFFICIAL_USERNAME: process.env.FYNX_OFFICIAL_USERNAME ?? "fynx",
        FYNX_WALLET_TEST_MODE_ENABLED: process.env.FYNX_WALLET_TEST_MODE_ENABLED ?? "",
        FYNX_WALLET_CARD_FUNDING_ENABLED: process.env.FYNX_WALLET_CARD_FUNDING_ENABLED ?? "",
        FYNX_WALLET_FUNDING_MIN_AMOUNT: process.env.FYNX_WALLET_FUNDING_MIN_AMOUNT ?? "",
        FYNX_WALLET_FUNDING_MAX_AMOUNT: process.env.FYNX_WALLET_FUNDING_MAX_AMOUNT ?? ""
      }
    }
  }
});
