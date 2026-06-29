import SwiftUI

struct PlaylistDetailView: View {
    let playlist: Playlist
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var tracks: [Track] = []

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Playlist header
                VStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(.quaternary)
                            .frame(width: 200, height: 200)

                        if let url = playlist.artworkUrl,
                           let imageUrl = URL(string: url) {
                            AsyncImage(url: imageUrl) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().aspectRatio(contentMode: .fill)
                                }
                            }
                            .frame(width: 200, height: 200)
                        } else {
                            Image(systemName: "music.note.list")
                                .font(.system(size: 60))
                                .foregroundColor(.secondary)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .shadow(radius: 6)

                    Text(playlist.name)
                        .font(.system(size: 22, weight: .bold))

                    Text("\(playlist.trackCount) songs")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)

                    if let description = playlist.description {
                        Text(description)
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                }

                // Play button
                Button {
                    nowPlayingViewModel.playQueue(tracks)
                } label: {
                    Label("Play", systemImage: "play.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(.accentColor)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .padding(.horizontal, 40)

                // Track list
                ForEach(Array(tracks.enumerated()), id: \.element.id) { index, track in
                    Button {
                        nowPlayingViewModel.playQueue(tracks, startIndex: index)
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(track.displayTitle)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)
                                Text(track.displayArtist)
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
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
            .padding(.vertical, 24)
        }
        .navigationTitle(playlist.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
