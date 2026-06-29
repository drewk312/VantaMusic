import SwiftUI

@main
struct MusicPlayerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appDelegate.container.nowPlayingViewModel)
                .environmentObject(appDelegate.container.libraryViewModel)
                .environmentObject(appDelegate.container.aiDjViewModel)
                .environmentObject(appDelegate.container.searchViewModel)
                .environmentObject(appDelegate.container.settingsViewModel)
                .environmentObject(appDelegate.container.discoverViewModel)
                .preferredColorScheme(.dark)
        }
    }
}
