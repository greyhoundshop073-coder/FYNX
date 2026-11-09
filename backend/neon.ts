import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  functions: {
    fynxapi: {
      name: "FYNX testing backend",
      source: "./neon-function.js",
      env: {
        JWT_SECRET: process.env.JWT_SECRET!,
        OPENAI_API_KEY: process.env.OPENAI_API_KEY!,
        OPENAI_MODEL: process.env.OPENAI_MODEL!,
        PAYSTACK_SECRET_KEY: process.env.PAYSTACK_SECRET_KEY!,
        FYNX_EMAIL_HOST: process.env.FYNX_EMAIL_HOST!,
        FYNX_EMAIL_PORT: process.env.FYNX_EMAIL_PORT!,
        FYNX_EMAIL_USER: process.env.FYNX_EMAIL_USER!,
        FYNX_EMAIL_PASSWORD: process.env.FYNX_EMAIL_PASSWORD!,
        FYNX_EMAIL_FROM: process.env.FYNX_EMAIL_FROM!,
        FYNX_MEDIA_STORAGE_PROVIDER: process.env.FYNX_MEDIA_STORAGE_PROVIDER!,
        FYNX_NEON_S3_ENDPOINT: process.env.FYNX_NEON_S3_ENDPOINT!,
        FYNX_NEON_S3_REGION: process.env.FYNX_NEON_S3_REGION!,
        FYNX_NEON_S3_ACCESS_KEY_ID: process.env.FYNX_NEON_S3_ACCESS_KEY_ID!,
        FYNX_NEON_S3_SECRET_ACCESS_KEY: process.env.FYNX_NEON_S3_SECRET_ACCESS_KEY!,
        FYNX_NEON_STORAGE_BUCKET: process.env.FYNX_NEON_STORAGE_BUCKET!,
        FYNX_RECEIPT_SIGNING_SECRET: process.env.FYNX_RECEIPT_SIGNING_SECRET!,
        FYNX_MARKETPLACE_FEE_BPS: process.env.FYNX_MARKETPLACE_FEE_BPS!,
        FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS: process.env.FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS!,
        FYNX_MARKETPLACE_FEE_FIXED: process.env.FYNX_MARKETPLACE_FEE_FIXED!,
        FYNX_MARKETPLACE_FEE_MODE: process.env.FYNX_MARKETPLACE_FEE_MODE!,
        FYNX_MARKETPLACE_FEE_POLICY_VERSION: process.env.FYNX_MARKETPLACE_FEE_POLICY_VERSION!,
        FYNX_MARKETPLACE_PROVIDER_FEE_PAYER: process.env.FYNX_MARKETPLACE_PROVIDER_FEE_PAYER!,
        FYNX_AD_ADMIN_IDS: process.env.FYNX_AD_ADMIN_IDS!
      }
    }
  }
});
