import Foundation
import Combine
import GRDB
import Security

final class DatabaseManager {
    private var dbWriter: DatabaseWriter?
    private let dbPath: String
    private let credentialStore = SourceCredentialStore()

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        dbPath = docs.appendingPathComponent("vanta_music.db").path
    }

    func initialize() throws {
        let db = try DatabaseQueue(path: dbPath)
        try migrator.migrate(db)
        dbWriter = db
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

        migrator.registerMigration("v2_move_source_credentials_to_keychain") { [credentialStore] db in
            let rows = try Row.fetchAll(db, sql: "SELECT id, apiKey, username, password, extraConfig FROM sourceConfig")
            for row in rows {
                guard let id = row["id"] as? String else { continue }
                let credentials = SourceCredentials(
                    apiKey: row["apiKey"] as? String,
                    username: row["username"] as? String,
                    password: row["password"] as? String,
                    extraConfig: decodeExtraConfig(row["extraConfig"] as? Data)
                )
                guard credentials.hasValues else { continue }

                // Do not remove legacy values until their Keychain write succeeds.
                try credentialStore.save(credentials, for: id)
                try db.execute(
                    sql: "UPDATE sourceConfig SET apiKey = NULL, username = NULL, password = NULL, extraConfig = NULL WHERE id = ?",
                    arguments: [id]
                )
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
            let credentials = SourceCredentials(
                apiKey: config.apiKey,
                username: config.username,
                password: config.password,
                extraConfig: config.extraConfig
            )
            try credentialStore.save(credentials, for: config.id)
            try db.execute(
                sql: """
                INSERT OR REPLACE INTO sourceConfig 
                (id, name, kind, isEnabled, serverUrl, apiKey, username, password, extraConfig)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                arguments: [
                    config.id, config.name, config.kind.rawValue,
                    config.isEnabled, config.serverUrl, nil,
                    nil, nil, nil
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
                    let credentials = self?.credentialStore.load(for: id) ?? SourceCredentials()
                    return ExternalSourceConfig(
                        id: id,
                        name: name,
                        kind: kind,
                        isEnabled: (row["isEnabled"] as? Bool) ?? false,
                        serverUrl: row["serverUrl"] as? String,
                        apiKey: credentials.apiKey,
                        username: credentials.username,
                        password: credentials.password,
                        extraConfig: credentials.extraConfig
                    )
                }
                promise(.success(configs))
            }
        }
        .eraseToAnyPublisher()
    }
}

private struct SourceCredentials: Codable {
    let apiKey: String?
    let username: String?
    let password: String?
    let extraConfig: [String: String]

    init(
        apiKey: String? = nil,
        username: String? = nil,
        password: String? = nil,
        extraConfig: [String: String] = [:]
    ) {
        self.apiKey = apiKey?.nilIfBlank
        self.username = username?.nilIfBlank
        self.password = password?.nilIfBlank
        self.extraConfig = extraConfig
    }

    var hasValues: Bool {
        apiKey != nil || username != nil || password != nil || !extraConfig.isEmpty
    }
}

private final class SourceCredentialStore {
    private let service = "com.audiophile.musicplayer.source-credentials"

    func save(_ credentials: SourceCredentials, for sourceId: String) throws {
        if !credentials.hasValues {
            try delete(for: sourceId)
            return
        }

        let data = try JSONEncoder().encode(credentials)
        let query = baseQuery(for: sourceId)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData: data] as CFDictionary)
        if status == errSecItemNotFound {
            var addQuery = query
            addQuery[kSecValueData] = data
            addQuery[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
            guard addStatus == errSecSuccess else { throw KeychainError.status(addStatus) }
        } else if status != errSecSuccess {
            throw KeychainError.status(status)
        }
    }

    func load(for sourceId: String) -> SourceCredentials? {
        var query = baseQuery(for: sourceId)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(SourceCredentials.self, from: data)
    }

    private func delete(for sourceId: String) throws {
        let status = SecItemDelete(baseQuery(for: sourceId) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.status(status)
        }
    }

    private func baseQuery(for sourceId: String) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: sourceId,
        ]
    }
}

private enum KeychainError: Error {
    case status(OSStatus)
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

private func decodeExtraConfig(_ data: Data?) -> [String: String] {
    guard let data else { return [:] }
    return (try? JSONDecoder().decode([String: String].self, from: data)) ?? [:]
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
