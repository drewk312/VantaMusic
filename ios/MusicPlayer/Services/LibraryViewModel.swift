import Foundation
import Combine

final class LibraryViewModel: ObservableObject {
    @Published var tracks: [Track] = []
    @Published var albums: [Album] = []
    @Published var playlists: [Playlist] = []
    @Published var artists: [Artist] = []
    @Published var searchQuery: String = ""
    @Published var selectedTab: LibraryTab = .tracks
    @Published var isLoading: Bool = false

    private let databaseManager: DatabaseManager
    private var cancellables = Set<AnyCancellable>()

    enum LibraryTab: String, CaseIterable {
        case tracks = "Songs"
        case albums = "Albums"
        case artists = "Artists"
        case playlists = "Playlists"
    }

    var filteredTracks: [Track] {
        guard !searchQuery.isEmpty else { return tracks }
        return tracks.filter {
            $0.displayTitle.localizedCaseInsensitiveContains(searchQuery) ||
            $0.displayArtist.localizedCaseInsensitiveContains(searchQuery) ||
            $0.displayAlbum.localizedCaseInsensitiveContains(searchQuery)
        }
    }

    var filteredAlbums: [Album] {
        guard !searchQuery.isEmpty else { return albums }
        return albums.filter {
            $0.displayTitle.localizedCaseInsensitiveContains(searchQuery) ||
            $0.displayArtist.localizedCaseInsensitiveContains(searchQuery)
        }
    }

    init(databaseManager: DatabaseManager) {
        self.databaseManager = databaseManager
    }

    func loadLibrary() {
        isLoading = true

        databaseManager.getAllTracks()
            .sink { [weak self] tracks in
                self?.tracks = tracks
            }
            .store(in: &cancellables)

        databaseManager.getAllAlbums()
            .sink { [weak self] albums in
                self?.albums = albums
            }
            .store(in: &cancellables)

        databaseManager.getAllPlaylists()
            .sink { [weak self] playlists in
                self?.playlists = playlists
            }
            .store(in: &cancellables)

        isLoading = false
    }

    func importTracks(_ newTracks: [Track]) {
        databaseManager.saveTracks(newTracks)
        tracks.append(contentsOf: newTracks)
    }

    func search(_ query: String) {
        searchQuery = query
    }
}
