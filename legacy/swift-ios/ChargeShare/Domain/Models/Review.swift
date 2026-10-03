import Foundation

struct Review: Identifiable, Codable, Sendable, Equatable {
    let id: UUID
    var wallboxID: UUID
    var authorID: UUID
    var authorName: String
    var rating: Int  // 1-5
    var comment: String?
    var createdAt: Date

    static func == (lhs: Review, rhs: Review) -> Bool { lhs.id == rhs.id }
}
