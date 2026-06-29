import SwiftUI

struct MiniPlayerView: View {
    @EnvironmentObject var viewModel: NowPlayingViewModel

    var body: some View {
        HStack(spacing: 12) {
            // Artwork thumbnail
            artworkThumb
                .frame(width: 44, height: 44)

            // Track info
            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.currentTrack?.displayTitle ?? "")
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
                Text(viewModel.currentTrack?.displayArtist ?? "")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            // Controls
            Button {
                viewModel.togglePlayPause()
            } label: {
                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 8)

            Button {
                viewModel.playNext()
            } label: {
                Image(systemName: "forward.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
        .contentShape(Rectangle())
        .onTapGesture {
            viewModel.toggleFullPlayer()
        }
    }

    private var artworkThumb: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 6)
                .fill(.quaternary)

            if let url = viewModel.currentTrack?.artworkUrl,
               let imageUrl = URL(string: url) {
                AsyncImage(url: imageUrl) { phase in
                    if case .success(let image) = phase {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Image(systemName: "music.note").foregroundColor(.secondary)
                    }
                }
            } else {
                Image(systemName: "music.note").foregroundColor(.secondary)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
