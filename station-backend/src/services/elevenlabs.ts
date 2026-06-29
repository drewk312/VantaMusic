import axios from 'axios';
import * as dotenv from 'dotenv';
dotenv.config();

const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY;
// Using a default voice ID if not specified (e.g. Rachel or a cool DJ voice)
const DEFAULT_VOICE_ID = process.env.ELEVENLABS_VOICE_ID || '21m00Tcm4TlvDq8ikWAM'; 

export async function generateSpeechBase64(text: string, voiceId: string = DEFAULT_VOICE_ID): Promise<string> {
    if (!ELEVENLABS_API_KEY) {
        throw new Error("ELEVENLABS_API_KEY is missing");
    }

    try {
        const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`;
        const response = await axios.post(
            url,
            {
                text,
                model_id: "eleven_monolingual_v1",
                voice_settings: {
                    stability: 0.5,
                    similarity_boost: 0.75
                }
            },
            {
                headers: {
                    'Accept': 'audio/mpeg',
                    'xi-api-key': ELEVENLABS_API_KEY,
                    'Content-Type': 'application/json'
                },
                responseType: 'arraybuffer'
            }
        );

        const buffer = Buffer.from(response.data, 'binary');
        return buffer.toString('base64');
    } catch (error) {
        console.error("ElevenLabs API Error:", error);
        throw error;
    }
}
