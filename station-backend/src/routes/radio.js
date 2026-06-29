"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = require("express");
const gemini_1 = require("../services/gemini");
const elevenlabs_1 = require("../services/elevenlabs");
const router = (0, express_1.Router)();
router.post('/generate', async (req, res) => {
    try {
        const payload = req.body;
        if (!payload.seed) {
            return res.status(400).json({ error: "Missing seed in request" });
        }
        const count = payload.count || 15;
        const result = await (0, gemini_1.generateStationTracks)({ ...payload, count });
        return res.json(result);
    }
    catch (error) {
        console.error("Error in /generate:", error);
        return res.status(500).json({ error: "Internal server error during station generation" });
    }
});
router.post('/dj', async (req, res) => {
    try {
        const payload = req.body;
        if (!payload.nextTrack || !payload.stationName) {
            return res.status(400).json({ error: "Missing nextTrack or stationName in request" });
        }
        // 1. Generate the script
        const script = await (0, gemini_1.generateDjScript)(payload);
        // 2. Convert to speech
        let audioBase64 = undefined;
        try {
            audioBase64 = await (0, elevenlabs_1.generateSpeechBase64)(script);
        }
        catch (ttsError) {
            console.error("TTS generation failed, falling back to script only", ttsError);
        }
        return res.json({
            script,
            audioBase64
        });
    }
    catch (error) {
        console.error("Error in /dj:", error);
        return res.status(500).json({ error: "Internal server error during DJ segment generation" });
    }
});
exports.default = router;
//# sourceMappingURL=radio.js.map