import Foundation
import Combine

final class MetadataService {
    private let networkManager: NetworkManager
    private var cancellables = Set<AnyCancellable>()

    // iTunes Search API
    private let itunesSearchUrl = "https://itunes.apple.com/search"

    init(networkManager: NetworkManager) {
        self.networkManager = networkManager
    }

    // MARK: - Search
    func searchTracks(query: String, limit: Int = 25) -> AnyPublisher<[Track], Error> {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let url = "\(itunesSearchUrl)?term=\(encoded)&entity=song&limit=\(limit)"

        return networkManager.getJSON(ITunesSearchResponse.self, from: url)
            .map { response in
                response.results.map { $0.toTrack() }
            }
            .eraseToAnyPublisher()
    }

    func searchAlbums(query: String, limit: Int = 25) -> AnyPublisher<[Album], Error> {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let url = "\(itunesSearchUrl)?term=\(encoded)&entity=album&limit=\(limit)"

        return networkManager.getJSON(ITunesSearchResponse.self, from: url)
            .map { response in
                response.results.map { $0.toAlbum() }
            }
            .eraseToAnyPublisher()
    }

    func lookupAlbum(albumId: Int) -> AnyPublisher<AlbumWithTracks, Error> {
        let url = "https://itunes.apple.com/lookup?id=\(albumId)&entity=song"
        return networkManager.getJSON(ITunesLookupResponse.self, from: url)
            .map { response in
                let albumResult = response.results.first { $0.wrapperType == "collection" }
                let trackResults = response.results.filter { $0.wrapperType == "track" }
                let album = albumResult?.toAlbum() ?? Album(
                    id: "\(albumId)",
                    title: "Unknown",
                    artist: "Unknown",
                    source: .appleMusic,
                    trackCount: trackResults.count,
                    duration: 0,
                    isCompilation: false,
                    dateAdded: Date()
                )
                return AlbumWithTracks(
                    album: album,
                    tracks: trackResults.map { $0.toTrack() }
                )
            }
            .eraseToAnyPublisher()
    }

    // MARK: - YouTube Music Search (using Piped or Invidious)
    func searchYouTubeMusic(query: String) -> AnyPublisher<[Track], Error> {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        let ytUrl = "https://music.youtube.com/search?q=\(encoded)"

        // Use a community instance for YT Music metadata
        // For now, fallback to iTunes as primary metadata source
        return searchTracks(query: query)
    }

    // MARK: - Apple Music API
    func searchAppleMusic(query: String) -> AnyPublisher<[Track], Error> {
        searchTracks(query: query) // Uses iTunes API as proxy for Apple Music catalog
    }
}

// MARK: - iTunes Response Models
struct ITunesSearchResponse: Codable {
    let resultCount: Int
    let results: [ITunesResult]
}

struct ITunesLookupResponse: Codable {
    let resultCount: Int
    let results: [ITunesResult]
}

struct ITunesResult: Codable {
    let wrapperType: String
    let kind: String?
    let trackId: Int?
    let trackName: String?
    let artistId: Int?
    let artistName: String?
    let collectionId: Int?
    let collectionName: String?
    let artworkUrl60: String?
    let artworkUrl100: String?
    let trackTimeMillis: Int?
    let trackNumber: Int?
    let discNumber: Int?
    let releaseDate: String?
    let primaryGenreName: String?
    let trackExplicitness: String?
    let country: String?
    let currency: String?

    func artworkUrl(size: Int = 300) -> String? {
        artworkUrl100?.replacingOccurrences(of: "100x100bb", with: "\(size)x\(size)bb")
    }

    func toTrack() -> Track {
        let year: Int?
        if let dateStr = releaseDate {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withDashSeparatorInDate]
            if let date = formatter.date(from: dateStr) {
                let cal = Calendar.current
                year = cal.component(.year, from: date)
            } else {
                year = nil
            }
        } else {
            year = nil
        }

        return Track(
            id: "itunes_\(trackId ?? 0)",
            title: trackName ?? "Unknown",
            artist: artistName ?? "Unknown",
            album: collectionName ?? "Unknown",
            albumArtist: artistName,
            albumId: collectionId.map { "\($0)" },
            genre: primaryGenreName,
            trackNumber: trackNumber ?? 0,
            discNumber: discNumber ?? 1,
            duration: trackTimeMillis.map { Double($0) / 1000.0 } ?? 0,
            year: year,
            artworkUrl: artworkUrl(size: 600),
            isrc: nil,
            source: .appleMusic,
            sourceId: trackId.map { "\($0)" },
            url: nil,
            isPlayable: true,
            explicit: trackExplicitness == "explicit",
            popularity: 0,
            dateAdded: Date(),
            lastPlayed: nil
        )
    }

    func toAlbum() -> Album {
        let year: Int?
        if let dateStr = releaseDate {
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withDashSeparatorInDate]
            if let date = formatter.date(from: dateStr) {
                year = Calendar.current.component(.year, from: date)
            } else {
                year = nil
            }
        } else {
            year = nil
        }

        return Album(
            id: "itunes_album_\(collectionId ?? 0)",
            title: collectionName ?? "Unknown",
            artist: artistName ?? "Unknown",
            artistId: artistId.map { "\($0)" },
            artworkUrl: artworkUrl(size: 600),
            year: year,
            genre: primaryGenreName,
            trackCount: 0,
            duration: 0,
            source: .appleMusic,
            isCompilation: false,
            dateAdded: Date()
        )
    }
}
