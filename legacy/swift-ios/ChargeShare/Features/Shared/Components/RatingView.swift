import SwiftUI

struct RatingView: View {
    var rating: Double
    var count: Int?
    var size: CGFloat = 14

    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { star in
                starImage(for: star)
                    .font(.system(size: size))
                    .foregroundStyle(starColor(for: star))
            }

            if let count {
                Text("(\(count))")
                    .font(.system(size: size))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func starImage(for star: Int) -> Image {
        let threshold = Double(star) - 0.5
        if rating >= Double(star) {
            return Image(systemName: "star.fill")
        } else if rating >= threshold {
            return Image(systemName: "star.leadinghalf.filled")
        } else {
            return Image(systemName: "star")
        }
    }

    private func starColor(for star: Int) -> Color {
        rating >= Double(star) - 0.5 ? .csAmber : Color(.systemGray4)
    }
}

#Preview {
    VStack(spacing: 12) {
        RatingView(rating: 4.5, count: 127, size: 16)
        RatingView(rating: 3.0, count: 42)
        RatingView(rating: 5.0, size: 20)
        RatingView(rating: 1.7)
    }
    .padding()
}
