# Vanta iOS

Full SwiftUI port of the Vanta music player for iOS 16+.

## Prerequisites

- macOS 14+ (Sonoma)
- Xcode 16+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) — `brew install xcodegen`
- [CocoaPods](https://cocoapods.org) — `sudo gem install cocoapods` (if using Pods)
- An Apple Developer account (for device builds / .ipa)

## Dependencies

The project uses Swift Package Manager for dependencies:

- **GRDB.swift** — SQLite database layer
- No additional pods required for the core build

To add GRDB:
1. Open the generated Xcode project
2. File → Add Package Dependencies → Search `https://github.com/groue/GRDB.swift`
3. Add to the MusicPlayer target

## Setup

```bash
# 1. Navigate to ios directory
cd ios

# 2. Generate Xcode project
xcodegen generate

# 3. Open in Xcode
open MusicPlayer.xcodeproj

# 4. Configure signing
#    - Select the MusicPlayer target
#    - Signing & Capabilities → Select your team
#    - Bundle Identifier: com.audiophile.vanta

# 5. Add Config.plist entries
#    Edit Resources/Config.plist with your Gemini API key (for AI DJ features)
```

## Native DSP

The iOS port includes the C++ JamesDSP engine compiled for iOS arm64:

- Source in `DSPNative/`
- Objective-C++ bridge in `DSPNative/Bridge/ImmersiveDspBridge.mm`
- Swift wrapper: `DSPNative/Bridge/DSPNativeEngine.swift`

Build settings in `project.yml` handle the C++ compilation automatically.

## Building for Device (.ipa)

### Option 1: Xcode Archive

```bash
# Build archive
xcodebuild archive \
  -project MusicPlayer.xcodeproj \
  -scheme MusicPlayer \
  -configuration Release \
  -archivePath ~/Desktop/Vanta.xcarchive

# Export .ipa
xcodebuild -exportArchive \
  -archivePath ~/Desktop/Vanta.xcarchive \
  -exportPath ~/Desktop/Vanta.ipa \
  -exportOptionsPlist ExportOptions.plist
```

### Option 2: Manual via Xcode UI

1. Select "Any iOS Device" as the build target
2. Product → Archive
3. Distribute App → App Store Connect (or Ad Hoc for side-loading)

### Required ExportOptions.plist

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>ad-hoc</string>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>signingStyle</key>
    <string>automatic</string>
</dict>
</plist>
```

## Project Structure

```
ios/
  project.yml              # XcodeGen project specification
  MusicPlayer/
    App/                   # App entry, DI container
    Models/                # Data models (Track, Album, etc.)
    Services/              # ViewModels and business logic
    UI/                    # SwiftUI screens
      NowPlaying/          # Full player + mini player
      Library/             # Library browser
      Search/              # Search interface
      Settings/            # Settings panel
      AiDJ/                # AI DJ & Jukebox
      Home/                # Home screen
      Discover/            # Discovery & browse
      Components/          # Shared UI components
    Audio/                 # Audio engine + DSP
    Network/               # API clients
    Database/              # GRDB database layer
    Extensions/            # Helpers & utilities
    Resources/             # Assets, Config.plist
  DSPNative/               # Native C++ DSP library
    Bridge/                # ObjC++ → Swift bridge
    libjamesdsp/           # JamesDSP C source
    include/               # DSP headers
  README.md
```

## Porting Status

| Feature | Status |
|---------|--------|
| Playback (AVFoundation) | ✅ |
| Immersive Sound DSP | ✅ |
| Library (local tracks) | ✅ |
| Album/Artist/Playlist detail | ✅ |
| Search (iTunes API) | ✅ |
| Settings | ✅ |
| AI DJ Jukebox | ✅ |
| Discovery (new releases) | ✅ |
| Pulse voice sessions | 🔄 Stub |
| YouTube Music source | 🔄 Stub |
| Real-Debrid / TorBox | 🔄 Stub |
| Offline download | ❌ |
| UPnP casting | ❌ |
