import SwiftUI

struct ContentView: View {
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var selectedTab: Tab = .home

    enum Tab: String, CaseIterable {
        case home = "Home"
        case library = "Library"
        case search = "Search"
        case discover = "Discover"
        case settings = "Settings"

        var icon: String {
            switch self {
            case .home: return "house.fill"
            case .library: return "music.note.list"
            case .search: return "magnifyingglass"
            case .discover: return "sparkle.magnifyingglass"
            case .settings: return "gearshape.fill"
            }
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                // Main content
                TabView(selection: $selectedTab) {
                    HomeView()
                        .tag(Tab.home)

                    LibraryView()
                        .tag(Tab.library)

                    SearchView()
                        .tag(Tab.search)

                    DiscoverView()
                        .tag(Tab.discover)

                    SettingsView()
                        .tag(Tab.settings)
                }

                // Mini player
                if nowPlayingViewModel.currentTrack != nil {
                    MiniPlayerView()
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                // Bottom nav
                bottomNav
            }

            // Full screen player overlay
            if nowPlayingViewModel.showFullPlayer {
                NowPlayingView()
                    .transition(.move(edge: .bottom))
                    .zIndex(100)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.9), value: nowPlayingViewModel.showFullPlayer)
        .animation(.spring(response: 0.35, dampingFraction: 0.9), value: nowPlayingViewModel.currentTrack != nil)
    }

    private var bottomNav: some View {
        HStack(spacing: 0) {
            ForEach(Tab.allCases, id: \.self) { tab in
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 20, weight: .semibold))
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundColor(selectedTab == tab ? .accentColor : .secondary)
                }
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .padding(.bottom, 24)
        .background(.ultraThinMaterial)
    }
}
