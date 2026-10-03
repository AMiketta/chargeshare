import Foundation

@MainActor
@Observable
final class WallboxDetailViewModel {
    let wallbox: Wallbox
    var reviews: [Review] = []
    var isLoadingReviews = false
    var errorMessage: String?

    private let repository = WallboxRepository()

    init(wallbox: Wallbox) {
        self.wallbox = wallbox
    }

    func loadReviews() async {
        isLoadingReviews = true
        defer { isLoadingReviews = false }

        do {
            reviews = try await repository.reviews(id: wallbox.id)
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
