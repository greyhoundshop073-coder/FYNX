import express from "express";

const app = express();
app.use(express.json({ limit: "2mb" }));

const PORT = process.env.PORT || 10000;

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "fynx-backend" });
});

app.post("/api/assistant", (req, res) => {
  const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";

  if (!message) {
    return res.status(400).json({ error: "message is required" });
  }

  return res.json({
    reply: `FYNX received: ${message}`
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`FYNX backend listening on ${PORT}`);
});
