import SwiftUI

struct ArtistDetailView: View {
    let artist: Artist
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var albums: [Album] = []
    @State private var topTracks: [Track] = []

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Artist header
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(.quaternary)
                            .frame(width: 160, height: 160)

                        if let url = artist.artworkUrl,
                           let imageUrl = URL(string: url) {
                            AsyncImage(url: imageUrl) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().aspectRatio(contentMode: .fill)
                                }
                            }
                            .frame(width: 160, height: 160)
                            .clipShape(Circle())
                        } else {
                            Image(systemName: "person.fill")
                                .font(.system(size: 60))
                                .foregroundColor(.secondary)
                        }
                    }

                    Text(artist.displayName)
                        .font(.system(size: 26, weight: .bold))

                    if let genre = artist.genre {
                        Text(genre)
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }

                    HStack(spacing: 20) {
                        StatBadge(value: "\(artist.albumCount)", label: "Albums")
                        StatBadge(value: "\(artist.trackCount)", label: "Songs")
                    }
                }
                .padding(.horizontal, 20)

                // Top tracks section
                if !topTracks.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Top Songs")
                            .font(.system(size: 18, weight: .bold))
                            .padding(.horizontal, 20)

                        ForEach(Array(topTracks.prefix(5).enumerated()), id: \.element.id) { index, track in
                            Button {
                                nowPlayingViewModel.playTrack(track)
                            } label: {
                                HStack(spacing: 12) {
                                    Text("\(index + 1)")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.tertiary)
                                        .frame(width: 24)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(track.displayTitle)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundColor(.primary)
                                        Text(track.displayAlbum)
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    Text(track.formattedDuration)
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.secondary)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 6)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                // Albums section
                if !albums.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Albums")
                            .font(.system(size: 18, weight: .bold))
                            .padding(.horizontal, 20)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(albums) { album in
                                    NavigationLink(destination: AlbumDetailView(album: album)) {
                                        AlbumCard(album: album)
                                            .frame(width: 150)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                }
            }
            .padding(.vertical, 24)
        }
        .navigationTitle(artist.displayName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct StatBadge: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 18, weight: .bold))
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
    }
}
