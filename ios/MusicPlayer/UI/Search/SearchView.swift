import SwiftUI

struct SearchView: View {
    @EnvironmentObject var viewModel: SearchViewModel
    @EnvironmentObject var nowPlayingViewModel: NowPlayingViewModel
    @State private var searchText = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Filter chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SearchViewModel.SearchFilter.allCases, id: \.self) { filter in
                            Button {
                                viewModel.selectedFilter = filter
                                if !searchText.isEmpty {
                                    viewModel.performSearch(searchText)
                                }
                            } label: {
                                Text(filter.rawValue)
                                    .font(.system(size: 13, weight: .medium))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(viewModel.selectedFilter == filter ? .accentColor : .quaternary)
                                    .foregroundColor(viewModel.selectedFilter == filter ? .white : .primary)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }

                if viewModel.isSearching {
                    Spacer()
                    ProgressView("Searching...")
                    Spacer()
                } else if viewModel.results.isEmpty && !searchText.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 40))
                            .foregroundColor(.quaternary)
                        Text("No results found")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                } else {
                    List {
                        ForEach(viewModel.results) { track in
                            TrackRow(track: track)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    nowPlayingViewModel.playTrack(track)
                                }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .searchable(text: $searchText, prompt: "Search songs, albums, artists...")
            .onChange(of: searchText) { _, newValue in
                viewModel.query = newValue
            }
            .navigationTitle("Search")
        }
    }
}
