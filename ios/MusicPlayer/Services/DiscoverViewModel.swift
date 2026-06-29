import Foundation
import Combine

final class DiscoverViewModel: ObservableObject {
    @Published var featuredAlbums: [Album] = []
    @Published var newReleases: [Album] = []
    @Published var recommendedTracks: [Track] = []
    @Published var isLoading: Bool = false
    @Published var error: String?

    private let metadataService: MetadataService
    private let llmClient: LlmClient
    private var cancellables = Set<AnyCancellable>()

    init(metadataService: MetadataService, llmClient: LlmClient) {
        self.metadataService = metadataService
        self.llmClient = llmClient
    }

    func loadDiscoverContent() {
        isLoading = true
        error = nil

        // Load new releases via iTunes
        metadataService.searchAlbums(query: "new music", limit: 10)
            .sink(receiveCompletion: { [weak self] completion in
                if case .failure(let err) = completion {
                    self?.error = err.localizedDescription
                }
                self?.isLoading = false
            }, receiveValue: { [weak self] albums in
                self?.newReleases = albums
                self?.featuredAlbums = Array(albums.prefix(5))
            })
            .store(in: &cancellables)
    }
}
