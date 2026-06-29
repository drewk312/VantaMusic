import SwiftUI

struct HomeView: View {
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @EnvironmentObject var libraryViewModel: LibraryViewModel
    @EnvironmentObject var aiDjViewModel: AiDjViewModel
    @EnvironmentObject var discoverViewModel: DiscoverViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Good evening")
                            .font(.system(size: 28, weight: .bold))
                        Text("Welcome back to Vanta")
                            .font(.system(size: 15))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                    // Quick Play section
                    if !libraryViewModel.tracks.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Recently Played")
                                .font(.system(size: 18, weight: .bold))
                                .padding(.horizontal, 20)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(libraryViewModel.tracks.prefix(10)) { track in
                                        Button {
                                            nowPlayingViewModel.playTrack(track)
                                        } label: {
                                            VStack(alignment: .leading, spacing: 6) {
                                                ZStack {
                                                    RoundedRectangle(cornerRadius: 8)
                                                        .fill(.quaternary)
                                                        .frame(width: 140, height: 140)
                                                    if let url = track.artworkUrl,
                                                       let imageUrl = URL(string: url) {
                                                        AsyncImage(url: imageUrl) { phase in
                                                            if case .success(let image) = phase {
                                                                image.resizable().aspectRatio(contentMode: .fill)
                                                                    .frame(width: 140, height: 140)
                                                            }
                                                        }
                                                    }
                                                }
                                                .clipShape(RoundedRectangle(cornerRadius: 8))

                                                Text(track.displayTitle)
                                                    .font(.system(size: 13, weight: .semibold))
                                                    .foregroundColor(.primary)
                                                    .lineLimit(1)
                                                    .frame(width: 140, alignment: .leading)

                                                Text(track.displayArtist)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.secondary)
                                                    .lineLimit(1)
                                                    .frame(width: 140, alignment: .leading)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                    }

                    // AI DJ Quick Start
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "sparkles")
                                .font(.system(size: 18))
                                .foregroundColor(.accentColor)
                            Text("AI DJ")
                                .font(.system(size: 18, weight: .bold))
                            Spacer()
                            NavigationLink("Open", destination: AiDjView())
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.accentColor)
                        }
                        .padding(.horizontal, 20)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(aiDjViewModel.availableStations.prefix(5), id: \.self) { station in
                                    Button {
                                        aiDjViewModel.startJukebox(
                                            station: station,
                                            library: libraryViewModel.tracks
                                        )
                                    } label: {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Image(systemName: stationIcon(for: station))
                                                .font(.system(size: 20))
                                                .foregroundColor(.accentColor)
                                            Text(station.displayName)
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundColor(.primary)
                                            Text(station.description)
                                                .font(.system(size: 10))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                        }
                                        .frame(width: 120, alignment: .leading)
                                        .padding(12)
                                        .background(.quaternary.opacity(0.3))
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }

                    // Quick Stats
                    HStack(spacing: 16) {
                        StatCard(value: "\(libraryViewModel.tracks.count)", label: "Songs", icon: "music.note")
                        StatCard(value: "\(libraryViewModel.albums.count)", label: "Albums", icon: "rectangle.stack")
                        StatCard(value: "\(libraryViewModel.artists.count)", label: "Artists", icon: "person.2")
                        StatCard(value: "\(libraryViewModel.playlists.count)", label: "Playlists", icon: "list.bullet")
                    }
                    .padding(.horizontal, 20)

                    // Bottom spacer for mini player
                    Color.clear.frame(height: 60)
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear {
            libraryViewModel.loadLibrary()
            discoverViewModel.loadDiscoverContent()
        }
    }

    private func stationIcon(for station: JukeboxStation) -> String {
        switch station {
        case .goldenOldies: return "clock"
        case .yachtRock: return "sailboat"
        case .eightiesNight: return "star"
        case .classicSoul: return "heart"
        case .smoothJazz: return "wind"
        case .chillLoFi: return "moon.stars"
        case .morningAcoustic: return "sunrise"
        case .lateNightBlues: return "moon"
        case .summerParty: return "sun.max"
        case .deepFocus: return "brain"
        case .roadTrip: return "car"
        }
    }
}

// MARK: - Stat Card
struct StatCard: View {
    let value: String
    let label: String
    let icon: String

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.accentColor)
            Text(value)
                .font(.system(size: 18, weight: .bold))
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(.quaternary.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
