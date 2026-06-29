import { GoogleGenAI } from '@google/genai';
import { GenerateStationRequestV1, GenerateStationResponseV1, DjSegmentRequestV1 } from '../models/dto';
import * as dotenv from 'dotenv';
dotenv.config();

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

export async function generateStationTracks(req: GenerateStationRequestV1): Promise<GenerateStationResponseV1> {
    const kind = req.seedKind || 'FREE_TEXT';
    const start = req.seedEraStart ?? 0;
    const eraInfo = start > 0 ? `${start}–${req.seedEraEnd || start + 9}` : null;
    const recentBlock = req.recentHistory.length > 0
        ? `\nPRIORITY: Avoid these recently played tracks:\n${req.recentHistory.map(t => `  - "${t.title}" by ${t.artist}`).join('\n')}`
        : '';

    const tasteBlock = (req.tasteProfile.likes.length > 0 || req.tasteProfile.dislikes.length > 0)
        ? `\nTASTE SIGNALS:\n  Likes (Lean into): ${req.tasteProfile.likes.join(', ') || 'none'}\n  Dislikes (Avoid strictly): ${req.tasteProfile.dislikes.join(', ') || 'none'}`
        : '';

    let seedDescription = '';
    switch (kind) {
        case 'ERA':
            seedDescription = `Curation Goal: A definitive ${eraInfo} experience. Span pop, rock, hip-hop, R&B, and alternative. Include 60% massive chart-toppers and 40% respected deep cuts that define the era's subcultures. ALL tracks MUST be released between ${start} and ${req.seedEraEnd || start + 9}.`;
            break;
        case 'GENRE':
            seedDescription = `Curation Goal: Deep dive into ${req.seed}. Bridge the gap between the genre's pioneers and modern innovators. Focus on high-energy, high-impact tracks. ${eraInfo ? `Restriction: Only tracks from ${eraInfo}.` : 'Span the genre\'s entire history.'}`;
            break;
        case 'ARTIST':
            seedDescription = `Curation Goal: The world of ${req.seedArtist || req.seed}. Feature their core hits, 2-3 essential deep cuts, and tracks from direct contemporaries, collaborators, and artists they influenced. Maintain the artist's signature energy level.`;
            break;
        case 'SONG':
        case 'SONG_SIMILAR':
            seedDescription = `Curation Goal: Sonic expansion of "${req.seedTitle}" by ${req.seedArtist}. Match the tempo, production texture, and emotional weight. Do not just pick the same genre; pick songs that *feel* like this one.`;
            break;
        case 'MOOD':
            seedDescription = `Curation Goal: Capture the "${req.seed}" vibe. Prioritize tracks where the instrumentation, rhythm, and lyrical themes translate this mood. Avoid tracks that just have the mood word in the title; find songs that *feel* like the mood.`;
            break;
        case 'ACTIVITY':
            seedDescription = `Curation Goal: The ultimate soundtrack for "${req.seed}". Focus on appropriate BPM, energy levels, and momentum for this specific activity. Do not just look for the activity name in track titles; find the music that fuels the experience.`;
            break;
        default:
            seedDescription = `Curation Goal: Deep creative interpretation of "${req.seed}". Build a cohesive, high-quality set that explores the musical essence, eras, and subcultures associated with this prompt. Do not be literal; if they ask for a "vibe", give them the music that defines it.`;
    }

    const hintBlock = req.hintKeywords && req.hintKeywords.length > 0
        ? `\nSTYLISTIC HINTS: ${req.hintKeywords.join(', ')}`
        : '';

    const prompt = `You are Pulse — a high-energy, world-class music curator inside VANTA Music. You have encyclopedic knowledge of music history, subcultures, and sonic textures.

${seedDescription}
${hintBlock}
Generate exactly ${req.count} tracks that form a perfect, high-momentum set.${recentBlock}${tasteBlock}

CONSTRAINTS:
- Use real songs, real artists, and real albums. Accuracy is paramount.
- Every track MUST include the "album" field.
- Limit any single artist to maximum 2 appearances unless they are the primary seed artist.
- For decade/era stations, strict adherence to the date range is required.
- Do not repeat tracks from the recent history provided.
- NO LITERALISM: Avoid tracks that just have the seed words in the title unless they are absolutely essential classics. Focus on the *sound* and *genre* implied by the seed.
- "reason" field: Write a sharp, 1-sentence DJ-style callout (e.g., "A synth-pop masterpiece from the peak of the 80s.")

RESPONSE FORMAT (Strict JSON, no markdown):
{
  "stationName": "A punchy, 2-4 word name for this set",
  "tracks": [
    { "title": "Track Name", "artist": "Artist", "album": "Album", "reason": "Why this fits the set." }
  ]
}`;

    try {
        const response = await ai.models.generateContent({
            model: 'gemini-2.5-pro',
            contents: prompt,
            config: {
                responseMimeType: 'application/json',
                temperature: 0.75,
            }
        });

        const text = response.text || "{}";
        const result = JSON.parse(text) as GenerateStationResponseV1;
        return result;
    } catch (error) {
        console.error("Pulse Error generating station:", error);
        throw error;
    }
}

export async function generateDjScript(req: DjSegmentRequestV1): Promise<string> {
    const prompt = `
You are Pulse — the high-energy, confident personal DJ for VANTA Music. 
You are hosting the station "${req.stationName}".

Tone: Sharp, direct, and enthusiastic. One or two short, punchy sentences. No empty filler. No "immaculate vibes."
Goal: Bridge the gap between what just played and what's coming next.

Recent tracks played: ${req.recentTracks.map(t => t.title + ' by ' + t.artist).join(', ')}.
Next track up: ${req.nextTrack.title} by ${req.nextTrack.artist}.

Guidelines:
- Reference the next artist or track with confidence.
- Keep it under 25 words.
- Never use markdown, emojis, or stage directions.
- If the station has a specific theme (e.g., an era or genre), lean into that slightly.
- Just output the exact words to be spoken.
`;

    try {
        const response = await ai.models.generateContent({
            model: 'gemini-2.5-flash',
            contents: prompt,
            config: {
                temperature: 0.85,
            }
        });
        return response.text?.trim() || "Let's keep the momentum going.";
    } catch (error) {
        console.error("Pulse Error generating DJ script:", error);
        throw error;
    }
}
