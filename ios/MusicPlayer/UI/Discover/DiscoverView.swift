import SwiftUI

struct DiscoverView: View {
    @EnvironmentObject var viewModel: DiscoverViewModel
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Discover")
                            .font(.system(size: 28, weight: .bold))
                        Text("Find something new to love")
                            .font(.system(size: 15))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                    if viewModel.isLoading {
                        Spacer()
                        ProgressView("Loading...")
                            .frame(maxWidth: .infinity)
                        Spacer()
                    } else if let error = viewModel.error {
                        VStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.system(size: 40))
                                .foregroundColor(.secondary)
                            Text(error)
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                            Button("Retry") {
                                viewModel.loadDiscoverContent()
                            }
                            .buttonStyle(.bordered)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 40)
                    } else {
                        // New Releases
                        if !viewModel.newReleases.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("New Releases")
                                    .font(.system(size: 18, weight: .bold))
                                    .padding(.horizontal, 20)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        ForEach(viewModel.newReleases) { album in
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

                        // Featured Albums
                        if !viewModel.featuredAlbums.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Featured")
                                    .font(.system(size: 18, weight: .bold))
                                    .padding(.horizontal, 20)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        ForEach(viewModel.featuredAlbums) { album in
                                            Button {
                                                nowPlayingViewModel.playQueue(
                                                    [Track](), // Would need album tracks
                                                    startIndex: 0
                                                )
                                            } label: {
                                                ZStack(alignment: .bottomLeading) {
                                                    if let url = album.artworkUrl,
                                                       let imageUrl = URL(string: url) {
                                                        AsyncImage(url: imageUrl) { phase in
                                                            if case .success(let image) = phase {
                                                                image.resizable().aspectRatio(contentMode: .fill)
                                                            } else {
                                                                Color.quaternary
                                                            }
                                                        }
                                                    } else {
                                                        Color.quaternary
                                                    }

                                                    LinearGradient(
                                                        gradient: Gradient(colors: [.clear, .black.opacity(0.7)]),
                                                        startPoint: .center,
                                                        endPoint: .bottom
                                                    )

                                                    VStack(alignment: .leading, spacing: 2) {
                                                        Text(album.displayTitle)
                                                            .font(.system(size: 14, weight: .bold))
                                                            .foregroundColor(.white)
                                                        Text(album.displayArtist)
                                                            .font(.system(size: 12))
                                                            .foregroundColor(.white.opacity(0.8))
                                                    }
                                                    .padding(12)
                                                }
                                                .frame(width: 200, height: 200)
                                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                    .padding(.horizontal, 20)
                                }
                            }
                        }

                        // AI-powered discovery prompt
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Powered by AI")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(.secondary)

                            HStack(spacing: 12) {
                                DiscoveryPromptCard(
                                    title: "Mood Discovery",
                                    subtitle: "Find music for how you feel",
                                    icon: "face.smiling",
                                    color: .green
                                )

                                DiscoveryPromptCard(
                                    title: "Genre Explorer",
                                    subtitle: "Dive into a new genre",
                                    icon: "guitars",
                                    color: .orange
                                )
                            }
                        }
                        .padding(.horizontal, 20)
                    }

                    // Bottom spacer
                    Color.clear.frame(height: 60)
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear {
            viewModel.loadDiscoverContent()
        }
    }
}

// MARK: - Discovery Prompt Card
struct DiscoveryPromptCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color

    var body: some View {
        Button {} label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(color)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.primary)
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundColor(.tertiary)
            }
            .padding(14)
            .background(.quaternary.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}
