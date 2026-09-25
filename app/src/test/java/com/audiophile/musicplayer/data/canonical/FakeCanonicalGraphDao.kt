package com.audiophile.musicplayer.data.canonical

/**
 * In-memory CanonicalGraphDao for resolver unit tests (no Room runtime required).
 */
class FakeCanonicalGraphDao : CanonicalGraphDao {
    private var nextArtistId = 1L
    private var nextAlbumId = 1L
    private var nextTrackId = 1L
    private var nextArtistExtId = 1L
    private var nextAlbumExtId = 1L
    private var nextTrackExtId = 1L

    private val artists = linkedMapOf<Long, CanonicalArtistEntity>()
    private val albums = linkedMapOf<Long, CanonicalAlbumEntity>()
    private val tracks = linkedMapOf<Long, CanonicalTrackEntity>()
    private val artistExternals = mutableListOf<ArtistExternalIdentityEntity>()
    private val albumExternals = mutableListOf<AlbumExternalIdentityEntity>()
    private val trackExternals = mutableListOf<TrackExternalIdentityEntity>()

    override suspend fun insertArtist(artist: CanonicalArtistEntity): Long {
        val id = nextArtistId++
        artists[id] = artist.copy(artistId = id)
        return id
    }

    override suspend fun updateArtist(artist: CanonicalArtistEntity) {
        artists[artist.artistId] = artist
    }

    override suspend fun artistById(artistId: Long): CanonicalArtistEntity? = artists[artistId]

    override suspend fun artistByNormalizedName(normalizedName: String): CanonicalArtistEntity? =
        artists.values.firstOrNull { it.normalizedName == normalizedName }

    override suspend fun artistsMatchingPrefix(prefix: String, limit: Int): List<CanonicalArtistEntity> =
        artists.values
            .filter {
                it.normalizedName.startsWith(prefix) ||
                    it.canonicalName.lowercase().startsWith(prefix)
            }
            .sortedWith(compareBy({ it.canonicalName.length }, { it.canonicalName }))
            .take(limit)

    override suspend fun albumsMatchingPrefix(prefix: String, limit: Int): List<CanonicalAlbumEntity> =
        albums.values
            .filter {
                it.normalizedTitle.startsWith(prefix) ||
                    it.title.lowercase().startsWith(prefix)
            }
            .sortedWith(compareBy({ it.title.length }, { it.title }))
            .take(limit)

    override suspend fun insertArtistExternal(identity: ArtistExternalIdentityEntity): Long {
        if (artistExternal(identity.providerId, identity.externalArtistId) != null) return -1L
        val id = nextArtistExtId++
        artistExternals += identity.copy(id = id)
        return id
    }

    override suspend fun artistExternal(providerId: String, externalArtistId: String): ArtistExternalIdentityEntity? =
        artistExternals.firstOrNull { it.providerId == providerId && it.externalArtistId == externalArtistId }

    override suspend fun artistExternals(artistId: Long): List<ArtistExternalIdentityEntity> =
        artistExternals.filter { it.canonicalArtistId == artistId }

    override suspend fun insertAlbum(album: CanonicalAlbumEntity): Long {
        val id = nextAlbumId++
        albums[id] = album.copy(albumId = id)
        return id
    }

    override suspend fun updateAlbum(album: CanonicalAlbumEntity) {
        albums[album.albumId] = album
    }

    override suspend fun albumById(albumId: Long): CanonicalAlbumEntity? = albums[albumId]

    override suspend fun albumByNormalizedTitleArtist(normalizedTitle: String, artistId: Long?): CanonicalAlbumEntity? =
        albums.values.firstOrNull {
            it.normalizedTitle == normalizedTitle &&
                ((artistId != null && it.canonicalArtistId == artistId) ||
                    (artistId == null && it.canonicalArtistId == null))
        }

    override suspend fun albumByUpc(upc: String): CanonicalAlbumEntity? =
        albums.values.firstOrNull { it.upc == upc }

    override suspend fun albumsForArtist(artistId: Long): List<CanonicalAlbumEntity> =
        albums.values.filter { it.canonicalArtistId == artistId }

    override suspend fun insertAlbumExternal(identity: AlbumExternalIdentityEntity): Long {
        if (albumExternal(identity.providerId, identity.externalAlbumId) != null) return -1L
        val id = nextAlbumExtId++
        albumExternals += identity.copy(id = id)
        return id
    }

    override suspend fun albumExternal(providerId: String, externalAlbumId: String): AlbumExternalIdentityEntity? =
        albumExternals.firstOrNull { it.providerId == providerId && it.externalAlbumId == externalAlbumId }

    override suspend fun albumExternals(albumId: Long): List<AlbumExternalIdentityEntity> =
        albumExternals.filter { it.canonicalAlbumId == albumId }

    override suspend fun insertTrack(track: CanonicalTrackEntity): Long {
        val id = nextTrackId++
        tracks[id] = track.copy(trackId = id)
        return id
    }

    override suspend fun updateTrack(track: CanonicalTrackEntity) {
        tracks[track.trackId] = track
    }

    override suspend fun trackById(trackId: Long): CanonicalTrackEntity? = tracks[trackId]

    override suspend fun trackByIsrc(isrc: String): CanonicalTrackEntity? =
        tracks.values.firstOrNull { it.isrc.equals(isrc, ignoreCase = true) }

    override suspend fun trackByNormalizedTitleArtist(normalizedTitle: String, artistId: Long): CanonicalTrackEntity? =
        tracks.values.firstOrNull {
            it.normalizedTitle == normalizedTitle && it.canonicalArtistId == artistId
        }

    override suspend fun tracksForArtist(artistId: Long): List<CanonicalTrackEntity> =
        tracks.values.filter { it.canonicalArtistId == artistId }

    override suspend fun tracksForAlbum(albumId: Long): List<CanonicalTrackEntity> =
        tracks.values.filter { it.canonicalAlbumId == albumId }

    override suspend fun trackByUnifiedId(unifiedTrackId: Long): CanonicalTrackEntity? =
        tracks.values.firstOrNull { it.unifiedTrackId == unifiedTrackId }

    override suspend fun insertTrackExternal(identity: TrackExternalIdentityEntity): Long {
        if (trackExternal(identity.providerId, identity.externalTrackId) != null) return -1L
        val id = nextTrackExtId++
        trackExternals += identity.copy(id = id)
        return id
    }

    override suspend fun trackExternal(providerId: String, externalTrackId: String): TrackExternalIdentityEntity? =
        trackExternals.firstOrNull { it.providerId == providerId && it.externalTrackId == externalTrackId }

    override suspend fun trackExternals(trackId: Long): List<TrackExternalIdentityEntity> =
        trackExternals.filter { it.canonicalTrackId == trackId }

    override suspend fun artistCount(): Int = artists.size

    override suspend fun albumCount(): Int = albums.size

    override suspend fun trackCount(): Int = tracks.size
}
