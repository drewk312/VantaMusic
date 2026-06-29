import SwiftUI

struct NowPlayingView: View {
    @EnvironmentObject var viewModel: NowPlayingViewModel
    @State private var dragOffset: CGFloat = 0
    @State private var showingQueue = false

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                // Drag handle & close
                VStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 2.5)
                        .fill(.tertiary)
                        .frame(width: 36, height: 5)
                        .padding(.top, 8)

                    HStack {
                        Button {
                            viewModel.toggleFullPlayer()
                        } label: {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.primary)
                        }

                        Spacer()

                        Text("Now Playing")
                            .font(.system(size: 16, weight: .semibold))

                        Spacer()

                        Button {
                            showingQueue.toggle()
                        } label: {
                            Image(systemName: "list.bullet")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.primary)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 20)

                Spacer()

                // Artwork
                artworkView
                    .frame(width: geo.size.width * 0.85, height: geo.size.width * 0.85)

                Spacer()

                // Track info
                VStack(spacing: 4) {
                    Text(viewModel.currentTrack?.displayTitle ?? "")
                        .font(.system(size: 22, weight: .bold))
                        .lineLimit(1)

                    Text(viewModel.currentTrack?.displayArtist ?? "")
                        .font(.system(size: 16))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 20)

                // Progress bar
                VStack(spacing: 6) {
                    Slider(value: Binding(
                        get: { viewModel.progress },
                        set: { viewModel.seek(to: $0 * viewModel.duration) }
                    ))
                    .tint(.primary)

                    HStack {
                        Text(viewModel.formattedTime)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(viewModel.formattedDuration)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.horizontal, 30)

                Spacer().frame(height: 20)

                // Controls
                HStack(spacing: 40) {
                    Button { viewModel.toggleShuffle() } label: {
                        Image(systemName: "shuffle")
                            .font(.system(size: 20))
                            .foregroundColor(viewModel.isShuffled ? .accentColor : .primary)
                    }

                    Button { viewModel.playPrevious() } label: {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.primary)
                    }

                    Button {
                        viewModel.togglePlayPause()
                    } label: {
                        Image(systemName: viewModel.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 56))
                            .foregroundColor(.primary)
                    }

                    Button { viewModel.playNext() } label: {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.primary)
                    }

                    Button { viewModel.toggleRepeatMode() } label: {
                        Image(systemName: repeatIcon)
                            .font(.system(size: 20))
                            .foregroundColor(viewModel.repeatMode != .none ? .accentColor : .primary)
                    }
                }

                Spacer().frame(height: 20)

                // Volume & Immersive
                HStack {
                    Image(systemName: "speaker.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)

                    Slider(value: Binding(
                        get: { viewModel.volume },
                        set: { viewModel.setVolume($0) }
                    ))
                    .tint(.primary)

                    Image(systemName: "speaker.wave.3.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 30)

                Spacer().frame(height: 10)

                // Immersive Sound toggle
                Button {
                    viewModel.toggleImmersiveSound()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: viewModel.immersiveConfig.enabled ? "spatial.audio.fill" : "spatial.audio")
                            .font(.system(size: 14))
                        Text("Immersive Sound")
                            .font(.system(size: 13, weight: .medium))
                    }
                    .foregroundColor(viewModel.immersiveConfig.enabled ? .accentColor : .secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                }

                Spacer().frame(height: 40)
            }
            .background(.ultraThinMaterial)
            .offset(y: dragOffset)
            .gesture(
                DragGesture()
                    .onChanged { value in
                        if value.translation.height > 0 {
                            dragOffset = value.translation.height
                        }
                    }
                    .onEnded { value in
                        if value.translation.height > 100 {
                            viewModel.toggleFullPlayer()
                        }
                        dragOffset = 0
                    }
            )
        }
        .sheet(isPresented: $showingQueue) {
            QueueView()
        }
    }

    private var artworkView: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(.quaternary)
                .aspectRatio(1, contentMode: .fit)

            if let url = viewModel.currentTrack?.artworkUrl,
               let imageUrl = URL(string: url) {
                AsyncImage(url: imageUrl) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    default:
                        Image(systemName: "music.note")
                            .font(.system(size: 60))
                            .foregroundColor(.secondary)
                    }
                }
            } else {
                Image(systemName: "music.note")
                    .font(.system(size: 60))
                    .foregroundColor(.secondary)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 8)
    }

    private var repeatIcon: String {
        switch viewModel.repeatMode {
        case .none: return "repeat"
        case .one: return "repeat.1"
        case .all: return "repeat"
        }
    }
}

// MARK: - Queue Sheet
struct QueueView: View {
    @EnvironmentObject var viewModel: NowPlayingViewModel

    var body: some View {
        NavigationStack {
            List {
                if let current = viewModel.currentTrack {
                    Section("Now Playing") {
                        QueueRow(track: current, isCurrent: true)
                    }
                }

                Section("Up Next") {
                    ForEach(viewModel.queue.indices, id: \.self) { index in
                        if index > viewModel.currentIndex {
                            QueueRow(track: viewModel.queue[index], isCurrent: false)
                        }
                    }
                }
            }
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct QueueRow: View {
    let track: Track
    let isCurrent: Bool

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(track.displayTitle)
                    .font(.system(size: 15, weight: isCurrent ? .bold : .regular))
                Text(track.displayArtist)
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            }

            Spacer()

            Text(track.formattedDuration)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}
