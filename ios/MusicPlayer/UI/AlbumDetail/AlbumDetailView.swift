import SwiftUI

struct AlbumDetailView: View {
    let album: Album
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var tracks: [Track] = []

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header artwork
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(.quaternary)
                        .aspectRatio(1, contentMode: .fit)
                        .frame(width: 220)

                    if let url = album.artworkUrl,
                       let imageUrl = URL(string: url) {
                        AsyncImage(url: imageUrl) { phase in
                            if case .success(let image) = phase {
                                image.resizable().aspectRatio(contentMode: .fill)
                            }
                        }
                        .frame(width: 220, height: 220)
                    } else {
                        Image(systemName: "music.note")
                            .font(.system(size: 60))
                            .foregroundColor(.secondary)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .shadow(radius: 6)

                // Album info
                VStack(spacing: 4) {
                    Text(album.displayTitle)
                        .font(.system(size: 22, weight: .bold))
                        .multilineTextAlignment(.center)

                    Text(album.displayArtist)
                        .font(.system(size: 16))
                        .foregroundColor(.secondary)

                    if let year = album.year {
                        Text("\(year) · \(album.genre ?? "")")
                            .font(.system(size: 13))
                            .foregroundColor(.tertiary)
                    }
                }
                .padding(.horizontal, 20)

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
                VStack(spacing: 0) {
                    ForEach(Array(tracks.enumerated()), id: \.element.id) { index, track in
                        Button {
                            nowPlayingViewModel.playQueue(tracks, startIndex: index)
                        } label: {
                            HStack(spacing: 12) {
                                Text("\(index + 1)")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.tertiary)
                                    .frame(width: 24, alignment: .leading)

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

                                if track.explicit {
                                    Image(systemName: "e.square.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                }

                                Text(track.formattedDuration)
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)

                        if index < tracks.count - 1 {
                            Divider()
                                .padding(.leading, 52)
                        }
                    }
                }
                .background(.quaternary.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 24)
        }
        .navigationTitle(album.displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            loadTracks()
        }
    }

    private func loadTracks() {
        // In a real app, load from database or API
        // For now, use placeholder data from the album
    }
}
