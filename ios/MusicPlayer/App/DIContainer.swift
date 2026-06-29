import Foundation
import Combine

final class DIContainer {
    // MARK: - Core Services
    let audioEngine: AudioEngine
    let dspProcessor: DSPProcessor
    let networkManager: NetworkManager
    let databaseManager: DatabaseManager
    let metadataService: MetadataService
    let llmClient: LlmClient

    // MARK: - ViewModels
    let nowPlayingViewModel: NowPlayingViewModel
    let libraryViewModel: LibraryViewModel
    let aiDjViewModel: AiDjViewModel
    let searchViewModel: SearchViewModel
    let settingsViewModel: SettingsViewModel
    let discoverViewModel: DiscoverViewModel

    private var cancellables = Set<AnyCancellable>()

    init() {
        // Initialize core services
        databaseManager = DatabaseManager()
        networkManager = NetworkManager()
        dspProcessor = DSPProcessor()
        audioEngine = AudioEngine(dspProcessor: dspProcessor)
        metadataService = MetadataService(networkManager: networkManager)
        llmClient = LlmClient(networkManager: networkManager)

        // Initialize ViewModels
        nowPlayingViewModel = NowPlayingViewModel(audioEngine: audioEngine)
        libraryViewModel = LibraryViewModel(databaseManager: databaseManager)
        aiDjViewModel = AiDjViewModel(
            audioEngine: audioEngine,
            llmClient: llmClient,
            metadataService: metadataService
        )
        searchViewModel = SearchViewModel(
            metadataService: metadataService,
            networkManager: networkManager
        )
        settingsViewModel = SettingsViewModel()
        discoverViewModel = DiscoverViewModel(
            metadataService: metadataService,
            llmClient: llmClient
        )

        // Wire up cross-cutting concerns
        setupBindings()
    }

    func initialize() {
        databaseManager.initialize()
        audioEngine.initialize()
        dspProcessor.initialize()
    }

    func shutdown() {
        audioEngine.shutdown()
        databaseManager.shutdown()
    }

    private func setupBindings() {
        audioEngine.$currentTrack
            .receive(on: DispatchQueue.main)
            .sink { [weak self] track in
                self?.nowPlayingViewModel.currentTrack = track
            }
            .store(in: &cancellables)

        audioEngine.$playbackState
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.nowPlayingViewModel.playbackState = state
            }
            .store(in: &cancellables)
    }
}
