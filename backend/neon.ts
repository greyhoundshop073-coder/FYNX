import { defineConfig } from "@neon/config/v1";

export default defineConfig({
  functions: {
    fynxapi: {
      name: "FYNX testing backend",
      source: "./neon-function.js",
      runtime: "nodejs24"
    }
  }
});
