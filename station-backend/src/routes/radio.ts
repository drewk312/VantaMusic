import { Router, Request, Response } from 'express';
import { GenerateStationRequestV1, DjSegmentRequestV1 } from '../models/dto';
import { generateStationTracks, generateDjScript } from '../services/gemini';
import { generateSpeechBase64 } from '../services/elevenlabs';

const router = Router();

router.post('/generate', async (req: Request, res: Response) => {
    try {
        const payload = req.body as GenerateStationRequestV1;
        
        if (!payload.seed) {
            return res.status(400).json({ error: "Missing seed in request" });
        }
        
        const count = payload.count || 15;
        const result = await generateStationTracks({ ...payload, count });
        
        return res.json(result);
    } catch (error) {
        console.error("Error in /generate:", error);
        return res.status(500).json({ error: "Internal server error during station generation" });
    }
});

router.post('/dj', async (req: Request, res: Response) => {
    try {
        const payload = req.body as DjSegmentRequestV1;
        
        if (!payload.nextTrack || !payload.stationName) {
            return res.status(400).json({ error: "Missing nextTrack or stationName in request" });
        }
        
        // 1. Generate the script
        const script = await generateDjScript(payload);
        
        // 2. Convert to speech
        let audioBase64 = undefined;
        try {
            audioBase64 = await generateSpeechBase64(script);
        } catch (ttsError) {
            console.error("TTS generation failed, falling back to script only", ttsError);
        }
        
        return res.json({
            script,
            audioBase64
        });
    } catch (error) {
        console.error("Error in /dj:", error);
        return res.status(500).json({ error: "Internal server error during DJ segment generation" });
    }
});

export default router;
