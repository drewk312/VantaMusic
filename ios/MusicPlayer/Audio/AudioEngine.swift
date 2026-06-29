import Foundation
import AVFoundation
import Combine
import MediaPlayer

final class AudioEngine: NSObject, ObservableObject {
    // MARK: - Published State
    @Published var currentTrack: Track?
    @Published var playbackState: PlaybackState = .stopped
    @Published var currentTime: TimeInterval = 0
    @Published var duration: TimeInterval = 0
    @Published var volume: Float = 1.0
    @Published var isShuffled: Bool = false
    @Published var repeatMode: RepeatMode = .none
    @Published var immersiveConfig = ImmersiveAudioConfig()

    // MARK: - Queue
    @Published private(set) var queue = QueueState()

    // MARK: - Private
    private var player: AVPlayer?
    private var playerItem: AVPlayerItem?
    private var timeObserver: Any?
    private let dspProcessor: DSPProcessor
    private var cancellables = Set<AnyCancellable>()
    private var audioEngine: AVAudioEngine
    private var playerNode: AVAudioPlayerNode
    private let mixerNode: AVAudioMixerNode
    private let audioFormat: AVAudioFormat

    // MARK: - Now Playing
    private var nowPlayingInfo = MPNowPlayingInfoCenter.default()
    private var remoteCommandCenter = MPRemoteCommandCenter.shared()

    enum PlaybackState: Equatable {
        case stopped, playing, paused, loading, error(String)
    }

    enum RepeatMode: Equatable {
        case none, one, all
    }

    override init() {
        audioEngine = AVAudioEngine()
        playerNode = AVAudioPlayerNode()
        mixerNode = AVAudioMixerNode()
        audioFormat = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 2)!
        self.dspProcessor = DSPProcessor()
        super.init()
        setupAudioSession()
        setupAudioEngine()
        setupRemoteCommands()
        setupNowPlaying()
    }

    convenience init(dspProcessor: DSPProcessor) {
        self.init()
    }

    func initialize() {
        setupAudioEngine()
    }

    func shutdown() {
        stop()
        audioEngine.stop()
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
        }
    }

    // MARK: - Playback Control
    func play(track: Track) {
        guard let url = URL(string: track.url ?? "") else {
            playbackState = .error("Invalid URL")
            return
        }
        let item = AVPlayerItem(url: url)
        playerItem = item
        player = AVPlayer(playerItem: item)

        currentTrack = track
        duration = track.duration

        setupTimeObserver()
        setupItemObservers()

        player?.play()
        playbackState = .playing
        updateNowPlaying()

        // Apply DSP
        applyImmersiveDSP()
    }

    func play() {
        player?.play()
        playbackState = .playing
        updateNowPlaying()
    }

    func pause() {
        player?.pause()
        playbackState = .paused
        updateNowPlaying()
    }

    func stop() {
        player?.pause()
        player = nil
        playerItem = nil
        currentTrack = nil
        currentTime = 0
        duration = 0
        playbackState = .stopped
        updateNowPlaying()
    }

    func seek(to time: TimeInterval) {
        let cmTime = CMTime(seconds: time, preferredTimescale: 1000)
        player?.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
        currentTime = time
    }

    func setVolume(_ volume: Float) {
        self.volume = volume
        player?.volume = volume
    }

    // MARK: - Queue Management
    func playQueue(_ tracks: [Track], startIndex: Int = 0) {
        queue = QueueState()
        queue.enqueue(tracks)
        queue.currentIndex = startIndex
        if let track = queue.currentTrack {
            play(track: track)
        }
    }

    func playNext() {
        if queue.playNext() {
            if let track = queue.currentTrack {
                play(track: track)
            }
        } else if repeatMode == .all {
            queue.currentIndex = 0
            if let track = queue.currentTrack {
                play(track: track)
            }
        } else {
            playbackState = .stopped
        }
    }

    func playPrevious() {
        if currentTime > 3 {
            seek(to: 0)
        } else if queue.playPrevious() {
            if let track = queue.currentTrack {
                play(track: track)
            }
        }
    }

    func toggleShuffle() {
        isShuffled.toggle()
        if isShuffled {
            queue.shuffle()
        }
    }

    func toggleRepeatMode() {
        switch repeatMode {
        case .none: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .none
        }
    }

    // MARK: - Immersive DSP
    func setImmersiveConfig(_ config: ImmersiveAudioConfig) {
        immersiveConfig = config
        applyImmersiveDSP()
    }

    private func applyImmersiveDSP() {
        guard immersiveConfig.enabled else {
            dspProcessor.disable()
            return
        }
        let params = immersiveConfig.resolvedParams()
        dspProcessor.apply(params: params)
    }

    // MARK: - Setup
    private func setupAudioSession() {
        try? AVAudioSession.sharedInstance().setCategory(
            .playback,
            mode: .default,
            options: [.allowAirPlay, .allowBluetoothA2DP]
        )
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func setupAudioEngine() {
        audioEngine.attach(playerNode)
        audioEngine.connect(playerNode, to: audioEngine.mainMixerNode, format: audioFormat)
        audioEngine.prepare()
        try? audioEngine.start()
    }

    private func setupTimeObserver() {
        if let existing = timeObserver {
            player?.removeTimeObserver(existing)
        }
        timeObserver = player?.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 1000),
            queue: .main
        ) { [weak self] time in
            self?.currentTime = time.seconds
        }
    }

    private func setupItemObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(itemDidPlayToEnd),
            name: .AVPlayerItemDidPlayToEndTime,
            object: playerItem
        )
    }

    @objc private func itemDidPlayToEnd() {
        playNext()
    }

    // MARK: - Remote Control
    private func setupRemoteCommands() {
        remoteCommandCenter.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        remoteCommandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        remoteCommandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.playNext()
            return .success
        }
        remoteCommandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.playPrevious()
            return .success
        }
        remoteCommandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: event.positionTime)
            return .success
        }
    }

    private func setupNowPlaying() {
        UIApplication.shared.beginReceivingRemoteControlEvents()
    }

    private func updateNowPlaying() {
        guard let track = currentTrack else {
            nowPlayingInfo.nowPlayingInfo = nil
            return
        }

        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = track.displayTitle
        info[MPMediaItemPropertyArtist] = track.displayArtist
        info[MPMediaItemPropertyAlbumTitle] = track.displayAlbum
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        info[MPNowPlayingInfoPropertyPlaybackRate] = playbackState == .playing ? 1.0 : 0.0

        nowPlayingInfo.nowPlayingInfo = info
    }
}
