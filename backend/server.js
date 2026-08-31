import express from "express";

const app = express();
app.use(express.json({ limit: "2mb" }));

const PORT = process.env.PORT || 10000;

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "fynx-backend" });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`FYNX backend listening on ${PORT}`);
});
