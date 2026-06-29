import Foundation
import Combine

final class LlmClient {
    private let networkManager: NetworkManager
    private var apiKey: String?
    private var baseURL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    init(networkManager: NetworkManager) {
        self.networkManager = networkManager
        loadAPIKey()
    }

    private func loadAPIKey() {
        // Load from Keychain or bundled config
        if let path = Bundle.main.path(forResource: "Config", ofType: "plist"),
           let dict = NSDictionary(contentsOfFile: path),
           let key = dict["GeminiAPIKey"] as? String {
            apiKey = key
        }
    }

    struct AIRequest: Codable {
        let contents: [Content]

        struct Content: Codable {
            let parts: [Part]
            let role: String?
        }

        struct Part: Codable {
            let text: String
        }
    }

    struct AIResponse: Codable {
        let candidates: [Candidate]?

        struct Candidate: Codable {
            let content: Content?
            let finishReason: String?

            struct Content: Codable {
                let parts: [Part]?

                struct Part: Codable {
                    let text: String?
                }
            }
        }
    }

    // MARK: - Track Suggestions
    func getTrackSuggestions(
        prompt: String,
        count: Int = 10
    ) -> AnyPublisher<[String], Error> {
        let fullPrompt = """
        You are a music recommendation engine. Suggest \(count) specific, real, 
        commercially released original songs for: \(prompt)
        
        Return ONLY a JSON array of strings with track titles.
        Format: ["Song Title 1", "Song Title 2", ...]
        Do NOT include markdown formatting or explanations.
        """

        return sendPrompt(fullPrompt)
            .tryMap { response in
                guard let text = response.candidates?.first?.content?.parts?.first?.text else {
                    throw LlmError.noResponse
                }
                // Extract JSON array from response
                let cleaned = text
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .replacingOccurrences(of: "```json", with: "")
                    .replacingOccurrences(of: "```", with: "")
                guard let data = cleaned.data(using: .utf8),
                      let titles = try? JSONDecoder().decode([String].self, from: data) else {
                    throw LlmError.parseFailed
                }
                return titles
            }
            .eraseToAnyPublisher()
    }

    // MARK: - Narration
    func generateNarration(
        context: String,
        style: String
    ) -> AnyPublisher<String, Error> {
        let prompt = """
        You are an energetic, lively DJ introducing the next set of music.
        Context: \(context)
        Style: \(style)
        
        Generate a short, punchy one-liner (max 2 sentences) to introduce the music.
        Be energetic and excited. Do NOT be romantic or sleepy.
        """

        return sendPrompt(prompt)
            .tryMap { response in
                guard let text = response.candidates?.first?.content?.parts?.first?.text else {
                    throw LlmError.noResponse
                }
                return text.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            .eraseToAnyPublisher()
    }

    // MARK: - Discovery
    func discoverByMood(mood: String) -> AnyPublisher<[Track], Error> {
        let prompt = """
        Suggest 15 specific, real, commercially released original songs that match 
        the mood "\(mood)".
        
        Return ONLY a JSON array of objects with "title" and "artist" keys.
        Format: [{"title": "Song Title", "artist": "Artist Name"}, ...]
        Do NOT include explanations or markdown.
        """

        return sendPrompt(prompt)
            .tryMap { response in
                guard let text = response.candidates?.first?.content?.parts?.first?.text else {
                    throw LlmError.noResponse
                }
                let cleaned = text
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .replacingOccurrences(of: "```json", with: "")
                    .replacingOccurrences(of: "```", with: "")
                guard let data = cleaned.data(using: .utf8),
                      let items = try? JSONDecoder().decode([MoodTrackItem].self, from: data) else {
                    throw LlmError.parseFailed
                }
                return items.enumerated().map { index, item in
                    Track(
                        id: "mood_\(mood)_\(index)",
                        title: item.title,
                        artist: item.artist,
                        album: "",
                        trackNumber: index + 1,
                        discNumber: 1,
                        duration: 0,
                        source: .unknown,
                        isPlayable: true,
                        explicit: false,
                        popularity: 0,
                        dateAdded: Date()
                    )
                }
            }
            .eraseToAnyPublisher()
    }

    // MARK: - Internal
    private func sendPrompt(_ text: String) -> AnyPublisher<AIResponse, Error> {
        guard let apiKey = apiKey else {
            return Fail(error: LlmError.noAPIKey).eraseToAnyPublisher()
        }

        let urlStr = "\(baseURL)?key=\(apiKey)"
        guard let url = URL(string: urlStr) else {
            return Fail(error: NetworkError.invalidURL).eraseToAnyPublisher()
        }

        let request = AIRequest(
            contents: [.init(parts: [.init(text: text)], role: "user")]
        )

        return networkManager.postJSON(
            AIResponse.self,
            to: urlStr,
            body: request
        )
    }

    func setAPIKey(_ key: String) {
        apiKey = key
    }
}

// MARK: - Supporting Types
enum LlmError: LocalizedError {
    case noAPIKey
    case noResponse
    case parseFailed

    var errorDescription: String? {
        switch self {
        case .noAPIKey: return "No API key configured"
        case .noResponse: return "No response from AI"
        case .parseFailed: return "Failed to parse AI response"
        }
    }
}

private struct MoodTrackItem: Codable {
    let title: String
    let artist: String
}
