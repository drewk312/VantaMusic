import UIKit
import AVFoundation

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    func sceneDidBecomeActive(_ scene: UIScene) {
        try? AVAudioSession.sharedInstance().setCategory(
            .playback,
            mode: .default,
            options: [.mixWithOthers]
        )
        try? AVAudioSession.sharedInstance().setActive(true)
    }
}
