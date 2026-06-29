import Foundation
import SwiftUI

// MARK: - Color
extension Color {
    static let accentColor = Color(red: 0.0, green: 0.48, blue: 1.0)

    static let vantaBackground = Color.black
    static let vantaSurface = Color(white: 0.12)
    static let vantaSurfaceLight = Color(white: 0.18)

    static let vantaRed = Color(red: 1.0, green: 0.23, blue: 0.19)
    static let vantaPink = Color(red: 1.0, green: 0.18, blue: 0.33)
    static let vantaOrange = Color(red: 1.0, green: 0.58, blue: 0.0)
    static let vantaYellow = Color(red: 1.0, green: 0.8, blue: 0.0)
    static let vantaGreen = Color(red: 0.3, green: 0.85, blue: 0.39)
    static let vantaMint = Color(red: 0.0, green: 0.9, blue: 0.7)
    static let vantaTeal = Color(red: 0.35, green: 0.78, blue: 0.98)
    static let vantaBlue = Color(red: 0.0, green: 0.48, blue: 1.0)
    static let vantaIndigo = Color(red: 0.35, green: 0.34, blue: 0.84)
    static let vantaPurple = Color(red: 0.69, green: 0.32, blue: 0.87)
    static let vantaPink2 = Color(red: 1.0, green: 0.18, blue: 0.33)
}

// MARK: - View
extension View {
    func vantaCard() -> some View {
        self
            .background(Color.vantaSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    func vantaButton() -> some View {
        self
            .font(.system(size: 15, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.vantaBlue)
            .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Date Formatter
extension DateFormatter {
    static let relativeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .relative
        f.timeStyle = .none
        return f
    }()

    static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withDashSeparatorInDate]
        return f
    }()
}

// MARK: - String
extension String {
    var initials: String {
        let words = split(separator: " ")
        let initials = words.prefix(2).compactMap { $0.first }.map { String($0) }
        return initials.joined().uppercased()
    }
}

// MARK: - Bundle
extension Bundle {
    var appVersion: String {
        infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var buildNumber: String {
        infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }
}
