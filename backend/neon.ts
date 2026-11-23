import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  functions: {
    fynxapi: {
      name: "FYNX testing backend",
      source: "./neon-function.js",
      env: {
        NODE_ENV: "production",
        DB_POOL_MAX: "5",
        JWT_SECRET: process.env.FYNX_JWT_SECRET!,
        GEMINI_API_KEY: process.env.FYNX_GEMINI_API_KEY!,
        PAYSTACK_SECRET_KEY: process.env.FYNX_PAYSTACK_SECRET_KEY!,
        FYNX_OFFICIAL_USERNAME: process.env.FYNX_OFFICIAL_USERNAME!,
        FYNX_WALLET_TEST_MODE_ENABLED: process.env.FYNX_WALLET_TEST_MODE_ENABLED!,
        FYNX_WALLET_CARD_FUNDING_ENABLED: process.env.FYNX_WALLET_CARD_FUNDING_ENABLED!,
        FYNX_WALLET_FUNDING_MIN_AMOUNT: process.env.FYNX_WALLET_FUNDING_MIN_AMOUNT!,
        FYNX_WALLET_FUNDING_MAX_AMOUNT: process.env.FYNX_WALLET_FUNDING_MAX_AMOUNT!,
        FYNX_EMAIL_HOST: { value: process.env.FYNX_EMAIL_HOST, required: false },
        FYNX_EMAIL_PORT: { value: process.env.FYNX_EMAIL_PORT, required: false },
        FYNX_EMAIL_USER: { value: process.env.FYNX_EMAIL_USER, required: false },
        FYNX_EMAIL_PASSWORD: { value: process.env.FYNX_EMAIL_PASSWORD, required: false },
        FYNX_EMAIL_FROM: { value: process.env.FYNX_EMAIL_FROM, required: false },
        FYNX_EMAIL_SECURE: { value: process.env.FYNX_EMAIL_SECURE, required: false },
        FYNX_EMAIL_REQUIRE_TLS: { value: process.env.FYNX_EMAIL_REQUIRE_TLS, required: false }
      }
    }
  },
  triggers: {
    "fynx-marketplace-maintenance": {
      type: "schedule",
      function: "fynxapi",
      cron: "* * * * *",
      functionPath: "/internal/cron",
      enabled: false
    }
  }
});
