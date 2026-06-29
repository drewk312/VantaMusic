import Foundation
import Combine
import GRDB

final class DatabaseManager {
    private var dbWriter: DatabaseWriter?
    private let dbPath: String

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        dbPath = docs.appendingPathComponent("vanta_music.db").path
    }

    func initialize() {
        do {
            let db = try DatabaseQueue(path: dbPath)
            dbWriter = db
            try migrator.migrate(db)
        } catch {
            print("Database initialization error: \(error)")
        }
    }

    func shutdown() {
        dbWriter = nil
    }

    // MARK: - Schema Migrations
    private var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()

        migrator.registerMigration("v1_initial") { db in
            try db.create(table: "track") { t in
                t.column("id", .text).primaryKey()
                t.column("title", .text).notNull()
                t.column("artist", .text).notNull()
                t.column("album", .text).notNull()
                t.column("albumArtist", .text)
                t.column("albumId", .text)
                t.column("genre", .text)
                t.column("trackNumber", .integer).notNull()
                t.column("discNumber", .integer).notNull()
                t.column("duration", .double).notNull()
                t.column("year", .integer)
                t.column("artworkUrl", .text)
                t.column("isrc", .text)
                t.column("source", .text).notNull()
                t.column("sourceId", .text)
                t.column("url", .text)
                t.column("isPlayable", .boolean).notNull()
                t.column("explicit", .boolean).notNull()
                t.column("popularity", .float).notNull()
                t.column("dateAdded", .datetime).notNull()
                t.column("lastPlayed", .datetime)
            }

            try db.create(table: "album") { t in
                t.column("id", .text).primaryKey()
                t.column("title", .text).notNull()
                t.column("artist", .text).notNull()
                t.column("artistId", .text)
                t.column("artworkUrl", .text)
                t.column("year", .integer)
                t.column("genre", .text)
                t.column("trackCount", .integer).notNull()
                t.column("duration", .double).notNull()
                t.column("source", .text).notNull()
                t.column("isCompilation", .boolean).notNull()
                t.column("dateAdded", .datetime).notNull()
            }

            try db.create(table: "playlist") { t in
                t.column("id", .text).primaryKey()
                t.column("name", .text).notNull()
                t.column("description", .text)
                t.column("artworkUrl", .text)
                t.column("trackCount", .integer).notNull()
                t.column("duration", .double).notNull()
                t.column("source", .text).notNull()
                t.column("isEditable", .boolean).notNull()
                t.column("dateCreated", .datetime).notNull()
                t.column("dateModified", .datetime).notNull()
                t.column("color", .text)
            }

            try db.create(table: "playlistTrack") { t in
                t.column("playlistId", .text).notNull()
                t.column("trackId", .text).notNull()
                t.column("position", .integer).notNull()
                t.primaryKey(["playlistId", "trackId"])
                t.foreignKey(["playlistId"], references: "playlist", onDelete: .cascade)
                t.foreignKey(["trackId"], references: "track", onDelete: .cascade)
            }

            try db.create(table: "artist") { t in
                t.column("id", .text).primaryKey()
                t.column("name", .text).notNull()
                t.column("artworkUrl", .text)
                t.column("genre", .text)
                t.column("albumCount", .integer).notNull()
                t.column("trackCount", .integer).notNull()
                t.column("biography", .text)
                t.column("source", .text).notNull()
                t.column("color", .text)
            }

            try db.create(table: "sourceConfig") { t in
                t.column("id", .text).primaryKey()
                t.column("name", .text).notNull()
                t.column("kind", .text).notNull()
                t.column("isEnabled", .boolean).notNull()
                t.column("serverUrl", .text)
                t.column("apiKey", .text)
                t.column("username", .text)
                t.column("password", .text)
                t.column("extraConfig", .blob)
            }

            try db.create(table: "resolutionCache") { t in
                t.column("id", .text).primaryKey()
                t.column("trackId", .text).notNull()
                t.column("resolvedUrl", .text).notNull()
                t.column("source", .text).notNull()
                t.column("quality", .text).notNull()
                t.column("expiresAt", .datetime).notNull()
                t.column("createdAt", .datetime).notNull()
            }
        }

        return migrator
    }

    // MARK: - Track Operations
    func saveTrack(_ track: Track) {
        try? dbWriter?.write { db in
            try track.insert(db, onConflict: .replace)
        }
    }

    func saveTracks(_ tracks: [Track]) {
        try? dbWriter?.write { db in
            for track in tracks {
                try track.insert(db, onConflict: .replace)
            }
        }
    }

    func getAllTracks() -> AnyPublisher<[Track], Never> {
        Future<[Track], Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success([]))
                return
            }
            try? db.read { db in
                let tracks = try Track.fetchAll(db)
                promise(.success(tracks))
            }
        }
        .eraseToAnyPublisher()
    }

    func searchTracks(query: String) -> AnyPublisher<[Track], Never> {
        Future<[Track], Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success([]))
                return
            }
            let pattern = "%\(query)%"
            try? db.read { database in
                let tracks = try Track
                    .filter(Column("title").like(pattern) || Column("artist").like(pattern) || Column("album").like(pattern))
                    .fetchAll(database)
                promise(.success(tracks))
            }
        }
        .eraseToAnyPublisher()
    }

    // MARK: - Album Operations
    func saveAlbum(_ album: Album) {
        try? dbWriter?.write { db in
            try album.insert(db, onConflict: .replace)
        }
    }

    func getAllAlbums() -> AnyPublisher<[Album], Never> {
        Future<[Album], Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success([]))
                return
            }
            try? db.read { database in
                let albums = try Album.fetchAll(database)
                promise(.success(albums))
            }
        }
        .eraseToAnyPublisher()
    }

    func getAlbumWithTracks(albumId: String) -> AnyPublisher<AlbumWithTracks?, Never> {
        Future<AlbumWithTracks?, Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success(nil))
                return
            }
            try? db.read { database in
                guard let album = try Album.fetchOne(database, id: albumId) else {
                    promise(.success(nil))
                    return
                }
                let tracks = try Track
                    .filter(Column("albumId") == albumId)
                    .order(Column("discNumber"), Column("trackNumber"))
                    .fetchAll(database)
                promise(.success(AlbumWithTracks(album: album, tracks: tracks)))
            }
        }
        .eraseToAnyPublisher()
    }

    // MARK: - Playlist Operations
    func savePlaylist(_ playlist: Playlist) {
        try? dbWriter?.write { db in
            try playlist.insert(db, onConflict: .replace)
        }
    }

    func getAllPlaylists() -> AnyPublisher<[Playlist], Never> {
        Future<[Playlist], Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success([]))
                return
            }
            try? db.read { database in
                let playlists = try Playlist.fetchAll(database)
                promise(.success(playlists))
            }
        }
        .eraseToAnyPublisher()
    }

    // MARK: - Source Config
    func saveSourceConfig(_ config: ExternalSourceConfig) {
        try? dbWriter?.write { db in
            let data = try? JSONEncoder().encode(config.extraConfig)
            try db.execute(
                sql: """
                INSERT OR REPLACE INTO sourceConfig 
                (id, name, kind, isEnabled, serverUrl, apiKey, username, password, extraConfig)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                arguments: [
                    config.id, config.name, config.kind.rawValue,
                    config.isEnabled, config.serverUrl, config.apiKey,
                    config.username, config.password, data
                ]
            )
        }
    }

    func getAllSourceConfigs() -> AnyPublisher<[ExternalSourceConfig], Never> {
        Future<[ExternalSourceConfig], Never> { [weak self] promise in
            guard let db = self?.dbWriter else {
                promise(.success([]))
                return
            }
            try? db.read { database in
                let rows = try Row.fetchAll(database, sql: "SELECT * FROM sourceConfig")
                let configs = rows.compactMap { row -> ExternalSourceConfig? in
                    guard let id = row["id"] as? String,
                          let name = row["name"] as? String,
                          let kindRaw = row["kind"] as? String,
                          let kind = PlaybackProviderKind(rawValue: kindRaw) else {
                        return nil
                    }
                    let extra: [String: String]
                    if let blob = row["extraConfig"] as? Data,
                       let decoded = try? JSONDecoder().decode([String: String].self, from: blob) {
                        extra = decoded
                    } else {
                        extra = [:]
                    }
                    return ExternalSourceConfig(
                        id: id,
                        name: name,
                        kind: kind,
                        isEnabled: (row["isEnabled"] as? Bool) ?? false,
                        serverUrl: row["serverUrl"] as? String,
                        apiKey: row["apiKey"] as? String,
                        username: row["username"] as? String,
                        password: row["password"] as? String,
                        extraConfig: extra
                    )
                }
                promise(.success(configs))
            }
        }
        .eraseToAnyPublisher()
    }
}

// MARK: - GRDB Record Conformance
extension Track: Codable, FetchableRecord, MutablePersistableRecord {
    enum CodingKeys: String, CodingKey {
        case id, title, artist, album, albumArtist, albumId, genre
        case trackNumber, discNumber, duration, year, artworkUrl, isrc
        case source, sourceId, url, isPlayable, explicit, popularity
        case dateAdded, lastPlayed
    }
}

extension Album: Codable, FetchableRecord, MutablePersistableRecord {
    enum CodingKeys: String, CodingKey {
        case id, title, artist, artistId, artworkUrl, year, genre
        case trackCount, duration, source, isCompilation, dateAdded
    }
}

extension Playlist: Codable, FetchableRecord, MutablePersistableRecord {
    enum CodingKeys: String, CodingKey {
        case id, name, description, artworkUrl, trackCount, duration
        case source, isEditable, dateCreated, dateModified, color
    }
}
