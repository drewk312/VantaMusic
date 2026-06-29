import Foundation
import Combine

final class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var results: [Track] = []
    @Published var albumResults: [Album] = []
    @Published var isSearching: Bool = false
    @Published var selectedFilter: SearchFilter = .all
    @Published var error: String?

    private let metadataService: MetadataService
    private let networkManager: NetworkManager
    private var searchCancellable: AnyCancellable?
    private var cancellables = Set<AnyCancellable>()

    enum SearchFilter: String, CaseIterable {
        case all = "All"
        case songs = "Songs"
        case albums = "Albums"
        case artists = "Artists"
    }

    init(metadataService: MetadataService, networkManager: NetworkManager) {
        self.metadataService = metadataService
        self.networkManager = networkManager

        setupDebouncedSearch()
    }

    private func setupDebouncedSearch() {
        $query
            .debounce(for: .milliseconds(400), scheduler: RunLoop.main)
            .removeDuplicates()
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .sink { [weak self] query in
                self?.performSearch(query)
            }
            .store(in: &cancellables)
    }

    func performSearch(_ query: String) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            results = []
            albumResults = []
            return
        }

        isSearching = true
        error = nil

        searchCancellable?.cancel()

        switch selectedFilter {
        case .all, .songs:
            searchCancellable = metadataService.searchTracks(query: query)
                .sink(receiveCompletion: { [weak self] completion in
                    self?.isSearching = false
                    if case .failure(let err) = completion {
                        self?.error = err.localizedDescription
                    }
                }, receiveValue: { [weak self] tracks in
                    self?.results = tracks
                })
        case .albums:
            searchCancellable = metadataService.searchAlbums(query: query)
                .sink(receiveCompletion: { [weak self] completion in
                    self?.isSearching = false
                    if case .failure(let err) = completion {
                        self?.error = err.localizedDescription
                    }
                }, receiveValue: { [weak self] albums in
                    self?.albumResults = albums
                })
        case .artists:
            // Artist search would need a different endpoint
            isSearching = false
            break
        }
    }

    func clearSearch() {
        query = ""
        results = []
        albumResults = []
        error = nil
    }
}
