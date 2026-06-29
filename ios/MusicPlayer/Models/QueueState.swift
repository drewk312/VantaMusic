import Foundation
import Combine

struct QueueState: Equatable {
    var tracks: [Track] = []
    var currentIndex: Int = 0
    var history: [Track] = []

    var currentTrack: Track? {
        guard tracks.indices.contains(currentIndex) else { return nil }
        return tracks[currentIndex]
    }

    var upcomingTracks: [Track] {
        guard currentIndex + 1 < tracks.count else { return [] }
        return Array(tracks[(currentIndex + 1)...])
    }

    func track(at index: Int) -> Track? {
        guard tracks.indices.contains(index) else { return nil }
        return tracks[index]
    }

    mutating func enqueue(_ track: Track, at index: Int? = nil) {
        if let index = index {
            tracks.insert(track, at: min(index, tracks.count))
        } else {
            tracks.append(track)
        }
    }

    mutating func enqueue(_ tracks: [Track], at index: Int? = nil) {
        if let index = index {
            self.tracks.insert(contentsOf: tracks, at: min(index, self.tracks.count))
        } else {
            self.tracks.append(contentsOf: tracks)
        }
    }

    mutating func remove(at index: Int) {
        guard tracks.indices.contains(index) else { return }
        tracks.remove(at: index)
        if currentIndex >= index && currentIndex > 0 {
            currentIndex -= 1
        }
    }

    mutating func playNext() -> Bool {
        guard currentIndex + 1 < tracks.count else { return false }
        if let track = currentTrack {
            history.append(track)
        }
        currentIndex += 1
        return true
    }

    mutating func playPrevious() -> Bool {
        guard let track = history.popLast() else { return false }
        if currentIndex > 0 {
            currentIndex -= 1
        }
        tracks.insert(track, at: currentIndex)
        return true
    }

    mutating func shuffle() {
        guard tracks.count > 1 else { return }
        let current = currentTrack
        var remaining = Array(tracks.dropFirst(currentIndex))
        remaining.shuffle()
        tracks = Array(tracks.prefix(currentIndex)) + remaining
        if let current = current, let newIndex = tracks.firstIndex(of: current) {
            currentIndex = newIndex
        }
    }

    mutating func clear() {
        tracks = []
        history = []
        currentIndex = 0
    }

    static func == (lhs: QueueState, rhs: QueueState) -> Bool {
        lhs.tracks == rhs.tracks &&
        lhs.currentIndex == rhs.currentIndex &&
        lhs.history == rhs.history
    }
}
