export interface TrackRecommendationV1 {
    title: string;
    artist: string;
    album?: string;
    reason?: string;
}
export interface GenerateStationRequestV1 {
    seed: string;
    seedKind?: string;
    seedEraStart?: number;
    seedEraEnd?: number;
    seedArtist?: string;
    seedTitle?: string;
    hintKeywords?: string[];
    recentHistory: {
        title: string;
        artist: string;
    }[];
    tasteProfile: {
        likes: string[];
        dislikes: string[];
    };
    count: number;
}
export interface GenerateStationResponseV1 {
    tracks: TrackRecommendationV1[];
    stationName: string;
}
export interface DjSegmentRequestV1 {
    stationName: string;
    recentTracks: {
        title: string;
        artist: string;
    }[];
    nextTrack: {
        title: string;
        artist: string;
    };
    tone?: string;
}
export interface DjSegmentResponseV1 {
    audioUrl?: string;
    audioBase64?: string;
    script: string;
}
//# sourceMappingURL=dto.d.ts.map