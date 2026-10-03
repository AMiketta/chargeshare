import SwiftUI
import MapKit

struct WallboxDetailView: View {
    let wallbox: Wallbox
    var onDismiss: (() -> Void)?

    @State private var viewModel: WallboxDetailViewModel
    @State private var showBooking = false
    @Environment(\.dismiss) private var dismiss

    init(wallbox: Wallbox, onDismiss: (() -> Void)? = nil) {
        self.wallbox = wallbox
        self.onDismiss = onDismiss
        self._viewModel = State(initialValue: WallboxDetailViewModel(wallbox: wallbox))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    heroSection
                    contentSection
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                        onDismiss?()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        // Share
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                bookingButton
            }
        }
        .task {
            await viewModel.loadReviews()
        }
        .sheet(isPresented: $showBooking) {
            BookingView(wallbox: wallbox)
        }
        .errorAlert(message: Binding(
            get: { viewModel.errorMessage },
            set: { viewModel.errorMessage = $0 }
        ))
    }

    // MARK: - Hero Section

    private var heroSection: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: [Color.csGreen, Color.csGreen.opacity(0.6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .frame(height: 200)

            // Bolt Icon
            Image(systemName: "bolt.fill")
                .font(.system(size: 72, weight: .bold))
                .foregroundStyle(.white.opacity(0.15))
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.trailing, 24)

            VStack(alignment: .leading, spacing: 8) {
                // Model Badge
                if let model = wallbox.model {
                    Text(model)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.white.opacity(0.2))
                        .clipShape(Capsule())
                }

                // Online-Status
                HStack(spacing: 6) {
                    Circle()
                        .fill(wallbox.isOnline ? Color.green : Color.red)
                        .frame(width: 8, height: 8)
                    Text(wallbox.isOnline ? "Verfügbar" : "Offline")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white)
                }
            }
            .padding(20)
        }
    }

    // MARK: - Content Section

    private var contentSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Name & Adresse
            VStack(alignment: .leading, spacing: 6) {
                Text(wallbox.name)
                    .font(.title2.bold())

                Label(wallbox.fullAddress, systemImage: "mappin.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            // Rating & Verfügbarkeit
            HStack {
                RatingView(rating: wallbox.ratingAvg, count: wallbox.ratingCount, size: 16)
                Spacer()

                if wallbox.isOnline {
                    Label("Heute: \(wallbox.availability.todayDisplayString)", systemImage: "clock")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Divider()

            // Info Chips
            infoChipsSection

            Divider()

            // Preis
            VStack(alignment: .leading, spacing: 4) {
                Text("Preis")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                PriceLabel(price: wallbox.pricePerKWh, style: .large)

                if wallbox.isMetered {
                    Label("MID-geeicht", systemImage: "checkmark.seal.fill")
                        .font(.caption)
                        .foregroundStyle(.green)
                }
            }

            Divider()

            // Host Section (Placeholder — in echter App: Host-Daten laden)
            hostSection

            Divider()

            // Reviews
            reviewsSection
        }
        .padding(20)
    }

    private var infoChipsSection: some View {
        let chips: [(String, String, String)] = [
            ("bolt", "\(wallbox.powerKW.formatted(.number.precision(.fractionLength(1)))) kW AC", "Leistung"),
            ("cable.connector", wallbox.connectorType.displayName, "Stecker"),
            ("clock", wallbox.availability.todayDisplayString, "Heute"),
            (wallbox.isMetered ? "checkmark.seal" : "questionmark.circle", wallbox.isMetered ? "MID-geeicht" : "Nicht geeicht", "Messung")
        ]

        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            ForEach(chips, id: \.0) { icon, value, label in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Image(systemName: icon)
                            .foregroundStyle(.csGreen)
                        Text(label)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(value)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.csGreenLight)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var hostSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Host")
                .font(.headline)

            HStack(spacing: 12) {
                // Avatar-Initialen
                ZStack {
                    Circle()
                        .fill(Color.csGreen)
                        .frame(width: 44, height: 44)

                    Text("CW")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text("ChargeShare Host")
                            .font(.subheadline.weight(.semibold))

                        Label("Superhost", systemImage: "star.fill")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.csAmber)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.csAmber.opacity(0.15))
                            .clipShape(Capsule())
                    }

                    Text("Mitglied seit 2023")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var reviewsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Bewertungen")
                    .font(.headline)

                Spacer()

                if wallbox.ratingCount > 3 {
                    Button("Alle anzeigen") {}
                        .font(.subheadline)
                        .foregroundStyle(.csGreen)
                }
            }

            if viewModel.isLoadingReviews {
                ProgressView()
                    .frame(maxWidth: .infinity)
            } else if viewModel.reviews.isEmpty {
                Text("Noch keine Bewertungen.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.reviews.prefix(3)) { review in
                    ReviewRowView(review: review)
                    if review.id != viewModel.reviews.prefix(3).last?.id {
                        Divider()
                    }
                }
            }
        }
    }

    private var bookingButton: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                PriceLabel(price: wallbox.pricePerKWh, style: .normal)
                Spacer()

                Button("Zeitslot buchen") {
                    showBooking = true
                }
                .buttonStyle(PrimaryButtonStyle(isFullWidth: false))
                .disabled(!wallbox.isOnline)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color(.systemBackground))
        }
    }
}

// MARK: - ReviewRowView

struct ReviewRowView: View {
    let review: Review

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                RatingView(rating: Double(review.rating), size: 13)
                Spacer()
                Text(review.createdAt.relativeString)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Text(review.authorName)
                .font(.caption.weight(.semibold))

            if let comment = review.comment {
                Text(comment)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
            }
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    WallboxDetailView(wallbox: .preview)
        .environment(AuthManager.shared)
}
