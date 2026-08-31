import express from "express";

const app = express();
app.use(express.json({ limit: "2mb" }));

const PORT = process.env.PORT || 10000;
const RUNWAY_API_KEY = process.env.RUNWAY_API_KEY;
const RUNWAY_VERSION = "2024-11-06";
const RUNWAY_BASE = "https://api.dev.runwayml.com/v1";

function runwayHeaders() {
  if (!RUNWAY_API_KEY) throw new Error("RUNWAY_API_KEY is not configured");
  return {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${RUNWAY_API_KEY}`,
    "X-Runway-Version": RUNWAY_VERSION,
  };
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "fynx-ai-backend" });
});

app.post("/api/generate", async (req, res) => {
  try {
    const { mode = "video", prompt, imageUrl } = req.body || {};
    if (!prompt || typeof prompt !== "string" || !prompt.trim()) {
      return res.status(400).json({ error: "Prompt is required" });
    }

    let endpoint;
    let body;

    if (mode === "image") {
      endpoint = `${RUNWAY_BASE}/text_to_image`;
      body = {
        model: "gen4_image",
        promptText: prompt.trim(),
        ratio: "1024:1024",
      };
    } else {
      endpoint = `${RUNWAY_BASE}/image_to_video`;
      body = {
        model: "gen4.5",
        promptText: prompt.trim(),
        ratio: "1280:720",
        duration: 5,
      };
      if (imageUrl) {
        body.promptImage = imageUrl;
      }
    }

    const response = await fetch(endpoint, {
      method: "POST",
      headers: runwayHeaders(),
      body: JSON.stringify(body),
    });

    const data = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({ error: data?.error || "Runway request failed", details: data });
    }

    return res.status(202).json({ taskId: data.id, mode });
  } catch (error) {
    console.error(error);
    return res.status(500).json({ error: error.message || "Generation request failed" });
  }
});

app.get("/api/tasks/:taskId", async (req, res) => {
  try {
    const response = await fetch(`${RUNWAY_BASE}/tasks/${encodeURIComponent(req.params.taskId)}`, {
      headers: runwayHeaders(),
    });
    const data = await response.json();
    return res.status(response.ok ? 200 : response.status).json(data);
  } catch (error) {
    console.error(error);
    return res.status(500).json({ error: error.message || "Task lookup failed" });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`FYNX AI backend listening on ${PORT}`);
});
