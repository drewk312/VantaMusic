import Foundation
import Combine

final class AiDjViewModel: ObservableObject {
    @Published var mode: AiDjMode = .off
    @Published var currentStation: JukeboxStation = .goldenOldies
    @Published var stationPool: [Track] = []
    @Published var currentNarration: AiDjNarration?
    @Published var narrations: [AiDjNarration] = []
    @Published var isGenerating: Bool = false
    @Published var pulseSession: PulseSession?
    @Published var discoveryResult: DiscoveryResult?
    @Published var availableStations: [JukeboxStation] = JukeboxStation.allCases

    private let audioEngine: AudioEngine
    private let aiDjService: AiDjService
    private let llmClient: LlmClient
    private let metadataService: MetadataService
    private var cancellables = Set<AnyCancellable>()
    private var stationIndex = 0

    init(
        audioEngine: AudioEngine,
        llmClient: LlmClient,
        metadataService: MetadataService
    ) {
        self.audioEngine = audioEngine
        self.llmClient = llmClient
        self.metadataService = metadataService
        self.aiDjService = AiDjService(llmClient: llmClient, metadataService: metadataService)
    }

    // MARK: - Jukebox Mode
    func startJukebox(station: JukeboxStation, library: [Track]) {
        mode = .jukebox
        currentStation = station
        isGenerating = true

        aiDjService.buildStationPool(station: station, library: library)
            .sink { [weak self] pool in
                guard let self = self else { return }
                self.stationPool = pool
                self.isGenerating = false

                // Generate intro narration
                self.aiDjService.generateIntro(for: station)
                    .sink { text in
                        let narration = AiDjNarration(
                            id: UUID().uuidString,
                            text: text,
                            style: .intro,
                            timestamp: Date(),
                            trackContext: nil
                        )
                        self.currentNarration = narration
                        self.narrations.append(narration)
                    }
                    .store(in: &self.cancellables)

                // Start playback
                if !pool.isEmpty {
                    self.audioEngine.playQueue(pool)
                }
            }
            .store(in: &cancellables)
    }

    func stopJukebox() {
        mode = .off
        audioEngine.stop()
        stationPool = []
        narrations = []
        currentNarration = nil
    }

    func nextStation() {
        stationIndex = (stationIndex + 1) % availableStations.count
        currentStation = availableStations[stationIndex]
    }

    func previousStation() {
        stationIndex = (stationIndex - 1 + availableStations.count) % availableStations.count
        currentStation = availableStations[stationIndex]
    }

    // MARK: - Pulse Mode
    func startPulseSession(style: PulseListeningStyle) {
        mode = .pulse
        pulseSession = aiDjService.startPulseSession(style: style)
    }

    func endPulseSession() {
        guard let session = pulseSession else { return }
        aiDjService.endPulseSession(session)
        pulseSession = nil
        mode = .off
    }

    // MARK: - Discovery Mode
    func discoverReleaseRadar() {
        mode = .discovery
        isGenerating = true
        aiDjService.discoverReleaseRadar()
            .sink(receiveCompletion: { [weak self] _ in
                self?.isGenerating = false
            }, receiveValue: { [weak self] result in
                self?.discoveryResult = result
            })
            .store(in: &cancellables)
    }

    func discoverForgottenFavorites() {
        mode = .discovery
        isGenerating = true
        aiDjService.discoverForgottenFavorites()
            .sink(receiveCompletion: { [weak self] _ in
                self?.isGenerating = false
            }, receiveValue: { [weak self] result in
                self?.discoveryResult = result
            })
            .store(in: &cancellables)
    }

    func playDiscoveryResult() {
        guard let result = discoveryResult, !result.tracks.isEmpty else { return }
        audioEngine.playQueue(result.tracks)
    }
}
