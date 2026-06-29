import Foundation
import Combine

final class NetworkManager {
    private let session: URLSession
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 120
        config.waitsForConnectivity = true
        session = URLSession(configuration: config)
    }

    // MARK: - Generic Request
    func request<T: Decodable>(
        _ type: T.Type,
        url: URL,
        method: String = "GET",
        headers: [String: String]? = nil,
        body: Data? = nil
    ) -> AnyPublisher<T, Error> {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.allHTTPHeaderFields = headers
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        return session.dataTaskPublisher(for: request)
            .tryMap { data, response in
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    throw NetworkError.invalidResponse
                }
                return data
            }
            .decode(type: T.self, decoder: decoder)
            .receive(on: DispatchQueue.main)
            .eraseToAnyPublisher()
    }

    func requestData(
        url: URL,
        method: String = "GET",
        headers: [String: String]? = nil,
        body: Data? = nil
    ) -> AnyPublisher<Data, Error> {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.allHTTPHeaderFields = headers
        request.httpBody = body

        return session.dataTaskPublisher(for: request)
            .tryMap { data, response in
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    throw NetworkError.invalidResponse
                }
                return data
            }
            .receive(on: DispatchQueue.main)
            .eraseToAnyPublisher()
    }

    // MARK: - Convenience
    func getJSON<T: Decodable>(
        _ type: T.Type,
        from urlString: String,
        headers: [String: String]? = nil
    ) -> AnyPublisher<T, Error> {
        guard let url = URL(string: urlString) else {
            return Fail(error: NetworkError.invalidURL).eraseToAnyPublisher()
        }
        return request(type, url: url, headers: headers)
    }

    func postJSON<T: Decodable, U: Encodable>(
        _ type: T.Type,
        to urlString: String,
        body: U,
        headers: [String: String]? = nil
    ) -> AnyPublisher<T, Error> {
        guard let url = URL(string: urlString) else {
            return Fail(error: NetworkError.invalidURL).eraseToAnyPublisher()
        }
        var allHeaders = headers ?? [:]
        allHeaders["Content-Type"] = "application/json"
        let data = try? encoder.encode(body)
        return request(type, url: url, method: "POST", headers: allHeaders, body: data)
    }
}

enum NetworkError: LocalizedError {
    case invalidURL
    case invalidResponse
    case decodingFailed(String)
    case notFound
    case rateLimited
    case serverError(Int)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .invalidResponse: return "Invalid server response"
        case .decodingFailed(let msg): return "Failed to decode response: \(msg)"
        case .notFound: return "Resource not found"
        case .rateLimited: return "Rate limited — try again later"
        case .serverError(let code): return "Server error (HTTP \(code))"
        }
    }
}
