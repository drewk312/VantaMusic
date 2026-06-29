# VANTA Pulse Voice Relay

The Android app calls **your relay** — never the ElevenLabs API directly. Store `ELEVENLABS_API_KEY` (and optional `OPENAI_API_KEY`) on the server only.

## Endpoint

`POST /v1/pulse/speak`

Optional auth: `Authorization: Bearer <relay token>` (matches app Settings → Pulse Voice relay token).

### Request body

```json
{
  "text": "Short line Pulse will speak",
  "engine": "eleven_multilingual_v2",
  "voice_description": "…",
  "openai_instructions": "…",
  "output_format": "mp3"
}
```

`engine` values:
- `eleven_multilingual_v2` — ElevenLabs Multilingual v2 (recommended)
- `openai_gpt4o_mini_tts` — OpenAI `gpt-4o-mini-tts` with instruction-based tone

### Response

Raw `audio/mpeg` bytes.

## Minimal Node relay (example)

```javascript
import express from "express";

const app = express();
app.use(express.json({ limit: "1mb" }));

const ELEVEN_KEY = process.env.ELEVENLABS_API_KEY;
const OPENAI_KEY = process.env.OPENAI_API_KEY;
const RELAY_TOKEN = process.env.PULSE_RELAY_TOKEN;
const ELEVEN_VOICE_ID = process.env.ELEVEN_VOICE_ID; // your cloned/designed voice

app.post("/v1/pulse/speak", async (req, res) => {
  if (RELAY_TOKEN && req.headers.authorization !== `Bearer ${RELAY_TOKEN}`) {
    return res.status(401).send("Unauthorized");
  }
  const { text, engine, voice_description, openai_instructions } = req.body;
  if (!text?.trim()) return res.status(400).send("text required");

  try {
    if (engine === "openai_gpt4o_mini_tts" && OPENAI_KEY) {
      const r = await fetch("https://api.openai.com/v1/audio/speech", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENAI_KEY}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "gpt-4o-mini-tts",
          voice: "alloy",
          input: text,
          instructions: openai_instructions,
          response_format: "mp3",
        }),
      });
      const buf = Buffer.from(await r.arrayBuffer());
      if (!r.ok) return res.status(r.status).send(buf.toString());
      return res.type("audio/mpeg").send(buf);
    }

    const r = await fetch(
      `https://api.elevenlabs.io/v1/text-to-speech/${ELEVEN_VOICE_ID}`,
      {
        method: "POST",
        headers: {
          "xi-api-key": ELEVEN_KEY,
          "Content-Type": "application/json",
          Accept: "audio/mpeg",
        },
        body: JSON.stringify({
          text,
          model_id: "eleven_multilingual_v2",
          voice_settings: { stability: 0.45, similarity_boost: 0.8, style: 0.35 },
        }),
      }
    );
    const buf = Buffer.from(await r.arrayBuffer());
    if (!r.ok) return res.status(r.status).send(buf.toString());
    res.type("audio/mpeg").send(buf);
  } catch (e) {
    res.status(500).send(String(e));
  }
});

app.listen(8787, () => console.log("Pulse voice relay on :8787"));
```

Deploy behind HTTPS. In VANTA: Settings → Sources → Pulse AI → **Voice relay URL** = `https://your-host` (app appends `/v1/pulse/speak`).

## App playback flow

1. Pulse writes a short line
2. Relay returns MP3 → cached on device
3. Music ducks (~18% volume)
4. Pulse speaks
5. Volume restores; crossfade continues via playback engine

Without a relay URL, DJ voice falls back to Android system TTS.
