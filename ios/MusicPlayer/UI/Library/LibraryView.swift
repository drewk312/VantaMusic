import SwiftUI

struct LibraryView: View {
    @EnvironmentObject var viewModel: LibraryViewModel
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab picker
                Picker("Library", selection: $viewModel.selectedTab) {
                    ForEach(LibraryViewModel.LibraryTab.allCases, id: \.self) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Content
                switch viewModel.selectedTab {
                case .tracks:
                    trackList
                case .albums:
                    albumGrid
                case .artists:
                    artistList
                case .playlists:
                    playlistList
                }
            }
            .searchable(text: $searchText, prompt: "Search library")
            .onChange(of: searchText) { _, newValue in
                viewModel.search(newValue)
            }
            .navigationTitle("Library")
            .onAppear {
                viewModel.loadLibrary()
            }
        }
    }

    // MARK: - Tracks
    private var trackList: some View {
        List {
            ForEach(viewModel.filteredTracks) { track in
                TrackRow(track: track)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        nowPlayingViewModel.playTrack(track)
                    }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Albums
    private var albumGrid: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160))], spacing: 16) {
                ForEach(viewModel.filteredAlbums) { album in
                    NavigationLink(destination: AlbumDetailView(album: album)) {
                        AlbumCard(album: album)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
    }

    // MARK: - Artists
    private var artistList: some View {
        List {
            ForEach(viewModel.artists) { artist in
                NavigationLink(destination: ArtistDetailView(artist: artist)) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(.quaternary)
                                .frame(width: 44, height: 44)
                            Image(systemName: "person.fill")
                                .foregroundColor(.secondary)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(artist.displayName)
                                .font(.system(size: 15, weight: .medium))
                            Text("\(artist.albumCount) albums")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Playlists
    private var playlistList: some View {
        List {
            ForEach(viewModel.playlists) { playlist in
                NavigationLink(destination: PlaylistDetailView(playlist: playlist)) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(.quaternary)
                                .frame(width: 44, height: 44)
                            Image(systemName: "music.note.list")
                                .foregroundColor(.secondary)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(playlist.name)
                                .font(.system(size: 15, weight: .medium))
                            Text("\(playlist.trackCount) songs")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .listStyle(.plain)
    }
}

// MARK: - Track Row
struct TrackRow: View {
    let track: Track

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(.quaternary)
                    .frame(width: 40, height: 40)
                Image(systemName: "music.note")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(track.displayTitle)
                    .font(.system(size: 15, weight: .medium))
                    .lineLimit(1)
                Text("\(track.displayArtist) · \(track.displayAlbum)")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            Text(track.formattedDuration)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Album Card
struct AlbumCard: View {
    let album: Album

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(.quaternary)
                    .aspectRatio(1, contentMode: .fit)

                if let url = album.artworkUrl,
                   let imageUrl = URL(string: url) {
                    AsyncImage(url: imageUrl) { phase in
                        if case .success(let image) = phase {
                            image.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Image(systemName: "music.note")
                                .font(.system(size: 30))
                                .foregroundColor(.secondary)
                        }
                    }
                } else {
                    Image(systemName: "music.note")
                        .font(.system(size: 30))
                        .foregroundColor(.secondary)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))

            Text(album.displayTitle)
                .font(.system(size: 13, weight: .semibold))
                .lineLimit(1)

            Text(album.displayArtist)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
    }
}
