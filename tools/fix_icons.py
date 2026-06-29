import re, os

# Mappings from deprecated Filled to AutoMirrored where applicable
MIRROR_MAP = {
    "ArrowBack": "AutoMirrored.Filled.ArrowBack",
    "ArrowForward": "AutoMirrored.Filled.ArrowForward",
    "OpenInNew": "AutoMirrored.Filled.OpenInNew",
    "QueueMusic": "AutoMirrored.Filled.QueueMusic",
    "PlaylistAdd": "AutoMirrored.Filled.PlaylistAdd",
    "Article": "AutoMirrored.Filled.Article",
    "Send": "AutoMirrored.Filled.Send",
    "Reply": "AutoMirrored.Filled.Reply",
    "ExitToApp": "AutoMirrored.Filled.ExitToApp",
}

def transform_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    original = text
    # Add import for AutoMirrored if we will use it
    if 'import androidx.compose.material.icons.automirrored.filled' not in text:
        # Insert after the last filled icon import
        lines = text.split('\n')
        last_filled = -1
        for i, line in enumerate(lines):
            if line.startswith('import androidx.compose.material.icons.filled.'):
                last_filled = i
        if last_filled != -1:
            lines.insert(last_filled + 1, 'import androidx.compose.material.icons.automirrored.filled.*')
            text = '\n'.join(lines)
    # Replace usages
    for old, new in MIRROR_MAP.items():
        # Replace Icons.Filled.Old with Icons.AutoMirrored.Filled.Old
        text = re.sub(rf'Icons\.Filled\.{old}\b', f'Icons.{new}', text)
    if text != original:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        return True
    return False

if __name__ == '__main__':
    files = [
        'app/src/main/java/com/audiophile/musicplayer/ui/AccountScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/AddToPlaylistSheet.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/AiDjScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/AlbumDetailScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/ArtistDetailScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/LibraryScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/MixDetailScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/NowPlayingScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/SearchScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/SettingsScreen.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/TrackDetailSheet.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/VantaActionSheet.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/nowplaying/ActionDock.kt',
        'app/src/main/java/com/audiophile/musicplayer/ui/nowplaying/QueueView.kt',
    ]
    fixed = 0
    for path in files:
        if os.path.exists(path):
            if transform_file(path):
                print(f'FIXED: {path}')
                fixed += 1
        else:
            print(f'MISSING: {path}')
    print(f'Fixed {fixed} files')
