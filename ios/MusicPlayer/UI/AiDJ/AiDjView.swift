import SwiftUI

struct AiDjView: View {
    @EnvironmentObject var viewModel: AiDjViewModel
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @EnvironmentObject var libraryViewModel: LibraryViewModel
    @State private var showStationPicker = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // MARK: - Current Mode Header
                    modeHeader
                        .padding(.horizontal, 16)

                    // MARK: - Jukebox Section
                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeader(title: "Jukebox Stations", icon: "music.note.list")

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(viewModel.availableStations, id: \.self) { station in
                                    StationCard(
                                        station: station,
                                        isActive: viewModel.mode == .jukebox && viewModel.currentStation == station
                                    ) {
                                        viewModel.startJukebox(
                                            station: station,
                                            library: libraryViewModel.tracks
                                        )
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }

                    // MARK: - Currently Playing Station
                    if viewModel.mode == .jukebox {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Image(systemName: "sparkles")
                                    .foregroundColor(.accentColor)
                                Text("Now Playing: \(viewModel.currentStation.displayName)")
                                    .font(.system(size: 15, weight: .semibold))
                                Spacer()
                                Button("Stop") {
                                    viewModel.stopJukebox()
                                }
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.red)
                            }
                            .padding(.horizontal, 16)

                            if let narration = viewModel.currentNarration {
                                Text(narration.text)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.secondary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(.quaternary.opacity(0.3))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .padding(.horizontal, 16)
                            }
                        }
                    }

                    // MARK: - Discovery Section
                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeader(title: "Discovery", icon: "sparkle.magnifyingglass")

                        HStack(spacing: 12) {
                            DiscoveryCard(
                                title: "Release Radar",
                                subtitle: "New releases you might have missed",
                                icon: "star.circle.fill",
                                color: .blue
                            ) {
                                viewModel.discoverReleaseRadar()
                            }

                            DiscoveryCard(
                                title: "Forgotten Favorites",
                                subtitle: "Rediscover past hits",
                                icon: "clock.arrow.circlepath",
                                color: .purple
                            ) {
                                viewModel.discoverForgottenFavorites()
                            }
                        }
                        .padding(.horizontal, 16)
                    }

                    // MARK: - Discovery Results
                    if let result = viewModel.discoveryResult {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(result.reason)
                                    .font(.system(size: 15, weight: .semibold))
                                Spacer()
                                Button("Play All") {
                                    viewModel.playDiscoveryResult()
                                }
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.accentColor)
                            }
                            .padding(.horizontal, 16)

                            ForEach(result.tracks.prefix(5)) { track in
                                TrackRow(track: track)
                                    .padding(.horizontal, 16)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        nowPlayingViewModel.playTrack(track)
                                    }
                            }
                        }
                    }

                    // MARK: - Pulse Section
                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeader(title: "Pulse", icon: "waveform")

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                PulseStyleCard(style: .releaseRadar, color: .blue) {
                                    viewModel.startPulseSession(style: .releaseRadar)
                                }
                                PulseStyleCard(style: .mood, color: .green) {
                                    viewModel.startPulseSession(style: .mood)
                                }
                                PulseStyleCard(style: .forgottenFavorites, color: .purple) {
                                    viewModel.startPulseSession(style: .forgottenFavorites)
                                }
                                PulseStyleCard(style: .genreExplorer, color: .orange) {
                                    viewModel.startPulseSession(style: .genreExplorer)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("AI DJ")
        }
    }

    // MARK: - Mode Header
    private var modeHeader: some View {
        HStack {
            Image(systemName: viewModel.mode == .off ? "sparkles" : "waveform")
                .font(.system(size: 24))
                .foregroundColor(.accentColor)
            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.mode == .off ? "AI DJ is Off" : "AI DJ is Active")
                    .font(.system(size: 18, weight: .bold))
                Text(viewModel.mode == .off
                     ? "Choose a station to start listening"
                     : "Enjoying \(viewModel.currentStation.displayName)")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding()
        .background(.quaternary.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Station Card
struct StationCard: View {
    let station: JukeboxStation
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: stationIcon)
                    .font(.system(size: 24))
                    .foregroundColor(isActive ? .white : .accentColor)

                Text(station.displayName)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(isActive ? .white : .primary)

                Text(station.description)
                    .font(.system(size: 11))
                    .foregroundColor(isActive ? .white.opacity(0.8) : .secondary)
                    .lineLimit(2)
            }
            .frame(width: 140, alignment: .leading)
            .padding(12)
            .background(isActive ? Color.accentColor : .quaternary.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private var stationIcon: String {
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

// MARK: - Discovery Card
struct DiscoveryCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 28))
                    .foregroundColor(color)
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.primary)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(.quaternary.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Pulse Style Card
struct PulseStyleCard: View {
    let style: PulseListeningStyle
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: pulseIcon)
                    .font(.system(size: 24))
                    .foregroundColor(color)
                Text(styleDisplayName)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.primary)
            }
            .frame(width: 100, height: 80)
            .background(.quaternary.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private var styleDisplayName: String {
        switch style {
        case .eclectic: return "Eclectic"
        case .deepDive: return "Deep Dive"
        case .moodMatch: return "Mood Match"
        case .eraFocus: return "Era Focus"
        case .genreExplorer: return "Genre Explorer"
        case .releaseRadar: return "Release Radar"
        case .mood: return "Mood"
        case .forgottenFavorites: return "Forgotten"
        }
    }

    private var pulseIcon: String {
        switch style {
        case .releaseRadar: return "star.circle"
        case .mood: return "face.smiling"
        case .forgottenFavorites: return "clock.arrow.circlepath"
        case .genreExplorer: return "guitars"
        default: return "waveform"
        }
    }
}

// MARK: - Section Header
struct SectionHeader: View {
    let title: String
    let icon: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(.accentColor)
            Text(title)
                .font(.system(size: 16, weight: .bold))
        }
        .padding(.horizontal, 16)
    }
}
