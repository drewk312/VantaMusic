"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateStationTracks = generateStationTracks;
exports.generateDjScript = generateDjScript;
const genai_1 = require("@google/genai");
const dotenv = __importStar(require("dotenv"));
dotenv.config();
const ai = new genai_1.GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
async function generateStationTracks(req) {
    const kind = req.seedKind || 'FREE_TEXT';
    const eraInfo = req.seedEraStart ? `${req.seedEraStart}–${req.seedEraEnd || req.seedEraStart + 9}` : null;
    const recentBlock = req.recentHistory.length > 0
        ? `\nABSOLUTELY DO NOT recommend these recently played tracks:\n${req.recentHistory.map(t => `  - "${t.title}" by ${t.artist}`).join('\n')}`
        : '';
    const tasteBlock = (req.tasteProfile.likes.length > 0 || req.tasteProfile.dislikes.length > 0)
        ? `\nTaste preferences — lean INTO these likes, avoid dislikes:\n  Likes: ${req.tasteProfile.likes.join(', ') || 'none'}\n  Dislikes: ${req.tasteProfile.dislikes.join(', ') || 'none'}`
        : '';
    let seedDescription = '';
    switch (kind) {
        case 'ERA':
            seedDescription = `The user wants a radio station for the ${eraInfo} era. Recommend tracks from across the entire decade — span all genres that were popular (pop, rock, hip-hop, R&B, electronic, country, alternative, dance, etc.). Include both era-defining anthems and deeper album cuts. Every track MUST be from ${eraInfo}.`;
            break;
        case 'GENRE':
            seedDescription = `The user wants a ${req.seed} radio station. Cover the full breadth of this genre — different subgenres, influential artists from different eras within the genre, both classics and modern takes. ${eraInfo ? `Focus on tracks from ${eraInfo}.` : 'Span the genre across all eras.'}`;
            break;
        case 'ARTIST':
            seedDescription = `The user wants an artist radio station seeded by "${req.seedArtist || req.seed}". Mix the artist's own essential tracks (hits + deep cuts) with tracks from similar artists, influences, and contemporaries.`;
            break;
        case 'SONG':
        case 'SONG_SIMILAR':
            seedDescription = `The user wants a song radio station based on "${req.seedTitle}" by ${req.seedArtist}. Recommend tracks that have a similar vibe, production style, era, or audience. Mix the original artist's other work with similar artists.`;
            break;
        case 'MOOD':
            seedDescription = `The user wants a ${req.seed} mood radio station. Curate tracks that perfectly capture this mood — consider tempo, instrumentation, lyrics, and atmosphere.`;
            break;
        case 'ACTIVITY':
            seedDescription = `The user wants music for "${req.seed}". Pick tracks with appropriate energy, tempo, and mood for this activity.`;
            break;
        default:
            seedDescription = `The user wants a radio station based on: "${req.seed}". Interpret this creatively — it could be a genre, era, vibe, concept, or anything. Curate tracks that fit.`;
    }
    const hintBlock = req.hintKeywords && req.hintKeywords.length > 0
        ? `\nHint keywords to lean into: ${req.hintKeywords.join(', ')}`
        : '';
    const prompt = `You are a world-class music curator with encyclopedic knowledge of all music across every genre, era, and culture.

${seedDescription}
${hintBlock}
Generate exactly ${req.count} diverse tracks.${recentBlock}${tasteBlock}

RULES:
- Every track MUST include the album name — this is critical for downstream matching
- Mix well-known hits with deeper cuts (aim for ~60% recognizable hits, ~40% deeper cuts)
- Do not repeat the same artist more than 2–3 times across the entire list
- Cover a wide range of artists to keep the station fresh and exploratory
- For decade-specific stations, ensure ALL tracks are actually from that decade
- Be accurate — use real songs, real artists, real albums
- "reason" field is optional but helpful for explaining a pick

Return ONLY valid JSON (no markdown, no code fences):
{
  "stationName": "A catchy, descriptive station name (3–5 words)",
  "tracks": [
    { "title": "Exact Track Title", "artist": "Artist Name", "album": "Album Name", "reason": "optional why this fits" }
  ]
}`;
    try {
        const response = await ai.models.generateContent({
            model: 'gemini-2.5-pro',
            contents: prompt,
            config: {
                responseMimeType: 'application/json',
                temperature: 0.8,
            }
        });
        const text = response.text || "{}";
        const result = JSON.parse(text);
        return result;
    }
    catch (error) {
        console.error("Gemini Error generating station:", error);
        throw error;
    }
}
async function generateDjScript(req) {
    const prompt = `
You are "Nyx", a cool, knowledgeable, and slightly laid-back radio DJ for the station "${req.stationName}".
Write a VERY SHORT (1-2 sentences) spoken transition to be read aloud.
You just played: ${req.recentTracks.map(t => t.title + ' by ' + t.artist).join(', ')}.
You are about to play: ${req.nextTrack.title} by ${req.nextTrack.artist}.

Keep it natural, conversational, and avoid sounding like a robot. Don't use stage directions or markdown.
Just the exact words you will say.
`;
    try {
        const response = await ai.models.generateContent({
            model: 'gemini-2.5-flash',
            contents: prompt,
            config: {
                temperature: 0.8,
            }
        });
        return response.text?.trim() || "And now, some more music.";
    }
    catch (error) {
        console.error("Gemini Error generating DJ script:", error);
        throw error;
    }
}
//# sourceMappingURL=gemini.js.map