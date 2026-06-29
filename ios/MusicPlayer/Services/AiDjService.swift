import Foundation
import Combine

final class AiDjService {
    private let llmClient: LlmClient
    private let metadataService: MetadataService
    private var cancellables = Set<AnyCancellable>()

    // Session state
    private(set) var currentStation: JukeboxStation?
    private(set) var currentNarrations: [AiDjNarration] = []
    private(set) var sessionHistory: [Track] = []

    init(llmClient: LlmClient, metadataService: MetadataService) {
        self.llmClient = llmClient
        self.metadataService = metadataService
    }

    // MARK: - Jukebox Mode
    func buildStationPool(
        station: JukeboxStation,
        library: [Track]
    ) -> AnyPublisher<[Track], Never> {
        let eraRange = station.eraRange
        let keywords = station.genreKeywords

        // Strict pool: era + genre match
        var strictPool = library.filter { track in
            var matches = true
            if let era = eraRange, let year = track.year {
                matches = matches && era.contains(year)
            }
            if !keywords.isEmpty {
                let genreMatch = track.genre.map { genre in
                    keywords.contains { genre.localizedCaseInsensitiveContains($0) }
                } ?? false
                let titleMatch = keywords.contains {
                    track.displayTitle.localizedCaseInsensitiveContains($0) ||
                    track.displayArtist.localizedCaseInsensitiveContains($0)
                }
                matches = matches && (genreMatch || titleMatch)
            }
            return matches
        }

        // If strict pool is empty, use OPEN fallback: any track
        if strictPool.isEmpty {
            strictPool = library
        }

        return Just(strictPool.shuffled())
            .eraseToAnyPublisher()
    }

    func generateIntro(for station: JukeboxStation) -> AnyPublisher<String, Never> {
        let context = "Starting a \(station.displayName) set — \(station.description)"
        let style = "energetic intro"
        return llmClient.generateNarration(context: context, style: style)
            .replaceError(with: "Let's kick off some \(station.displayName)! 🎵")
            .eraseToAnyPublisher()
    }

    func generateTransition(
        currentTrack: Track,
        nextTrack: Track
    ) -> AnyPublisher<String, Never> {
        let context = "Just played '\(currentTrack.displayTitle)' by \(currentTrack.displayArtist). " +
                      "Next up: '\(nextTrack.displayTitle)' by \(nextTrack.displayArtist)."
        let style = "smooth transition"
        return llmClient.generateNarration(context: context, style: style)
            .replaceError(with: "Up next: \(nextTrack.displayTitle) by \(nextTrack.displayArtist)")
            .eraseToAnyPublisher()
    }

    // MARK: - Discovery Mode
    func discoverReleaseRadar() -> AnyPublisher<DiscoveryResult, Error> {
        let prompt = "Suggest 10 recently released notable songs (last 6 months) across pop, rock, hip-hop, and electronic."
        return llmClient.getTrackSuggestions(prompt: prompt, count: 10)
            .map { titles in
                let tracks = titles.enumerated().map { index, title in
                    Track(
                        id: "release_radar_\(index)",
                        title: title,
                        artist: "",
                        album: "",
                        trackNumber: index + 1,
                        discNumber: 1,
                        duration: 0,
                        source: .unknown,
                        isPlayable: true,
                        explicit: false,
                        popularity: 0,
                        dateAdded: Date()
                    )
                }
                return DiscoveryResult(
                    id: "release_radar_\(Date().timeIntervalSince1970)",
                    tracks: tracks,
                    reason: "Fresh releases you might have missed"
                )
            }
            .eraseToAnyPublisher()
    }

    func discoverForgottenFavorites() -> AnyPublisher<DiscoveryResult, Error> {
        let prompt = "Suggest 10 songs that were hit singles 5-15 years ago but are rarely played now."
        return llmClient.getTrackSuggestions(prompt: prompt, count: 10)
            .map { titles in
                let tracks = titles.enumerated().map { index, title in
                    Track(
                        id: "forgotten_\(index)",
                        title: title,
                        artist: "",
                        album: "",
                        trackNumber: index + 1,
                        discNumber: 1,
                        duration: 0,
                        source: .unknown,
                        isPlayable: true,
                        explicit: false,
                        popularity: 0,
                        dateAdded: Date()
                    )
                }
                return DiscoveryResult(
                    id: "forgotten_\(Date().timeIntervalSince1970)",
                    tracks: tracks,
                    reason: "Forgotten favorites worth another listen"
                )
            }
            .eraseToAnyPublisher()
    }

    // MARK: - Pulse Mode
    func startPulseSession(style: PulseListeningStyle) -> PulseSession {
        let session = PulseSession(
            id: UUID().uuidString,
            style: style,
            startedAt: Date(),
            trackHistory: [],
            currentEnergy: 0.5,
            userMood: nil,
            isActive: true
        )
        return session
    }

    func endPulseSession(_ session: PulseSession) {
        // Cleanup session state
    }
}
