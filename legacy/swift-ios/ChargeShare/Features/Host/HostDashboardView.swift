import SwiftUI
import Charts

struct HostDashboardView: View {
    @Environment(AuthManager.self) private var authManager
    @State private var viewModel = HostDashboardViewModel()
    @State private var showAddWallbox = false

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.earnings == nil {
                    loadingView
                } else {
                    contentView
                }
            }
            .navigationTitle("Host Dashboard")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showAddWallbox = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundStyle(.csGreen)
                    }
                }
            }
            .sheet(isPresented: $showAddWallbox) {
                AddWallboxView()
            }
            .refreshable {
                await viewModel.load()
            }
            .task {
                await viewModel.load()
            }
            .errorAlert(message: Binding(
                get: { viewModel.errorMessage },
                set: { viewModel.errorMessage = $0 }
            ))
        }
    }

    // MARK: - Loading

    private var loadingView: some View {
        ProgressView("Lade Dashboard...")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Main Content

    private var contentView: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                earningsHeaderCard
                wallboxStatusCard
                weeklyChartCard
                recentSessionsSection
            }
            .padding(16)
        }
    }

    // MARK: - Earnings Header Card

    private var earningsHeaderCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Begrüßung
            Text("Guten Tag, \(viewModel.greetingName)")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.8))

            // Monatsbetrag
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                if let earnings = viewModel.earnings {
                    Text(formatDecimal(earnings.monthAmount))
                        .font(.system(size: 42, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text("€")
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.7))
                } else {
                    Text("–")
                        .font(.system(size: 42, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                }

                Spacer()

                // Delta Badge
                if let delta = viewModel.deltaPercent {
                    HStack(spacing: 4) {
                        Image(systemName: delta >= 0 ? "arrow.up.right" : "arrow.down.right")
                        Text("\(abs(Int(delta)))%")
                    }
                    .font(.caption.weight(.bold))
                    .foregroundStyle(delta >= 0 ? .white : Color.red.opacity(0.8))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.white.opacity(0.15))
                    .clipShape(Capsule())
                }
            }

            Text("Diesen Monat")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.7))

            Divider()
                .background(.white.opacity(0.3))

            // Stats Row
            if let earnings = viewModel.earnings {
                HStack {
                    statItem(value: "\(earnings.sessionCount)", label: "Sessions")
                    Spacer()
                    statItem(value: String(format: "%.1f", earnings.avgRating), label: "⭐ Bewertung")
                    Spacer()
                    statItem(value: formatDecimal(earnings.totalAmount) + " €", label: "Gesamt")
                }
            }
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [Color.csGreen, Color.csGreen.opacity(0.75)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Wallbox Status Card

    private var wallboxStatusCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Meine Wallbox", systemImage: "bolt.car.fill")
                .font(.headline)

            if viewModel.myWallboxes.isEmpty {
                Button {
                    showAddWallbox = true
                } label: {
                    Label("Erste Wallbox hinzufügen", systemImage: "plus.circle")
                }
                .buttonStyle(SecondaryButtonStyle())
            } else {
                ForEach($viewModel.myWallboxes) { $wallbox in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(wallbox.name)
                                .font(.subheadline.weight(.semibold))

                            Text(wallbox.availability.todayDisplayString)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        Toggle("", isOn: Binding(
                            get: { wallbox.isOnline },
                            set: { _ in
                                Task { await viewModel.toggleWallbox(wallbox) }
                            }
                        ))
                        .tint(.csGreen)
                        .labelsHidden()
                    }
                    .padding()
                    .background(Color(.tertiarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Weekly Chart Card

    private var weeklyChartCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Wochenübersicht", systemImage: "chart.bar.fill")
                .font(.headline)

            if let earnings = viewModel.earnings, !earnings.dailyBreakdown.isEmpty {
                Chart(earnings.dailyBreakdown) { day in
                    BarMark(
                        x: .value("Tag", day.date, unit: .day),
                        y: .value("Einnahmen", day.amountDouble)
                    )
                    .foregroundStyle(Color.csGreen)
                    .cornerRadius(4)
                }
                .frame(height: 120)
                .chartXAxis {
                    AxisMarks(values: .stride(by: .day)) { _ in
                        AxisValueLabel(format: .dateTime.weekday(.abbreviated))
                    }
                }
                .chartYAxis {
                    AxisMarks { value in
                        AxisValueLabel {
                            if let amount = value.as(Double.self) {
                                Text("\(Int(amount)) €")
                                    .font(.caption2)
                            }
                        }
                        AxisGridLine()
                    }
                }
            } else {
                Text("Keine Daten für diese Woche")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(height: 120)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Recent Sessions

    private var recentSessionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Letzte Sessions", systemImage: "list.bullet.rectangle")
                .font(.headline)

            if viewModel.recentBookings.isEmpty {
                EmptyStateView(
                    systemImage: "calendar.badge.exclamationmark",
                    title: "Noch keine Buchungen",
                    subtitle: "Buchungen erscheinen hier, sobald Fahrer deine Wallbox buchen."
                )
            } else {
                ForEach(viewModel.recentBookings) { booking in
                    BookingRowView(booking: booking)
                    if booking.id != viewModel.recentBookings.last?.id {
                        Divider().padding(.leading, 16)
                    }
                }
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Helpers

    private func statItem(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.white)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.7))
        }
    }

    private func formatDecimal(_ decimal: Decimal) -> String {
        let number = NSDecimalNumber(decimal: decimal)
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.locale = Locale(identifier: "de_DE")
        return formatter.string(from: number) ?? "0,00"
    }
}

#Preview {
    HostDashboardView()
        .environment(AuthManager.shared)
}
