import SwiftUI

struct BookingView: View {
    let wallbox: Wallbox

    @State private var viewModel: BookingViewModel
    @State private var showConfirmation = false
    @Environment(\.dismiss) private var dismiss

    init(wallbox: Wallbox) {
        self.wallbox = wallbox
        self._viewModel = State(initialValue: BookingViewModel(wallbox: wallbox))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // 1. Datum
                    datumSection

                    // 2. Startzeit
                    startzeitSection

                    // 3. Dauer
                    dauerSection

                    // 4. Kostenübersicht
                    kostenSection

                    // 5. Zahlung
                    zahlungSection
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("Zeitslot buchen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Buchen") {
                        Task { await viewModel.book() }
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(viewModel.canBook ? .csGreen : .secondary)
                    .disabled(!viewModel.canBook || viewModel.isBooking)
                }
            }
            .loadingOverlay(viewModel.isBooking)
            .onChange(of: viewModel.createdBooking) { _, booking in
                if booking != nil {
                    showConfirmation = true
                }
            }
            .sheet(isPresented: $showConfirmation) {
                if let booking = viewModel.createdBooking {
                    BookingConfirmationView(booking: booking) {
                        showConfirmation = false
                        dismiss()
                    }
                    .presentationDetents([.large])
                }
            }
            .errorAlert(message: Binding(
                get: { viewModel.errorMessage },
                set: { viewModel.errorMessage = $0 }
            ))
        }
        .task {
            await viewModel.loadSlots()
        }
    }

    // MARK: - Sections

    private var datumSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Datum", icon: "calendar")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.availableDates, id: \.self) { date in
                        DatePillView(
                            date: date,
                            isSelected: Calendar.current.isDate(date, inSameDayAs: viewModel.selectedDate)
                        ) {
                            viewModel.selectedDate = date
                            Task { await viewModel.loadSlots() }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private var startzeitSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Startzeit", icon: "clock")

            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if viewModel.availableSlots.isEmpty {
                Text("Keine Slots verfügbar")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 16)
            } else {
                LazyVGrid(
                    columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())],
                    spacing: 8
                ) {
                    ForEach(viewModel.availableSlots) { slot in
                        TimeSlotView(
                            slot: slot,
                            isSelected: viewModel.selectedStartTime == slot.startTime
                        ) {
                            if slot.available {
                                viewModel.selectedStartTime = slot.startTime
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private var dauerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Dauer", icon: "hourglass")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.durationOptions, id: \.self) { hours in
                        DurationPillView(
                            hours: hours,
                            isSelected: viewModel.selectedDuration == hours
                        ) {
                            viewModel.selectedDuration = hours
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private var kostenSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Kostenübersicht", icon: "eurosign.circle")

            VStack(spacing: 0) {
                if let kwh = viewModel.estimatedKWh {
                    kostenRow("Geschätzter Verbrauch", value: "\(kwh.formatted(.number.precision(.fractionLength(1)))) kWh")
                    Divider().padding(.leading, 16)
                }
                kostenRow("Preis/kWh", value: "\(wallbox.pricePerKWh) €")
                if let cost = viewModel.estimatedCost {
                    Divider().padding(.leading, 16)
                    kostenRow("Geschätzte Gesamtkosten", value: formatDecimal(cost) + " €", isBold: true)
                }
            }
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 16)
        }
    }

    private var zahlungSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader("Zahlung", icon: "creditcard")

            VStack(spacing: 0) {
                ForEach(BookingViewModel.PaymentMethod.allCases) { method in
                    Button {
                        viewModel.selectedPayment = method
                    } label: {
                        HStack {
                            Image(systemName: method.icon)
                                .frame(width: 24)
                                .foregroundStyle(.csGreen)
                            Text(method.label)
                                .font(.body)
                                .foregroundStyle(.primary)
                            Spacer()
                            if viewModel.selectedPayment == method {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.csGreen)
                            }
                        }
                        .padding()
                    }
                    if method != BookingViewModel.PaymentMethod.allCases.last {
                        Divider().padding(.leading, 16 + 24 + 12)
                    }
                }
            }
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 16)
        }
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(.headline)
            .padding(.horizontal, 16)
    }

    private func kostenRow(_ label: String, value: String, isBold: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(isBold ? .subheadline.weight(.semibold) : .subheadline)
                .foregroundStyle(isBold ? .primary : .secondary)
            Spacer()
            Text(value)
                .font(isBold ? .subheadline.weight(.bold) : .subheadline)
                .foregroundStyle(isBold ? .csGreen : .primary)
        }
        .padding()
    }

    private func formatDecimal(_ decimal: Decimal) -> String {
        let number = NSDecimalNumber(decimal: decimal)
        return String(format: "%.2f", number.doubleValue).replacingOccurrences(of: ".", with: ",")
    }
}

// MARK: - Sub-Components

struct DatePillView: View {
    let date: Date
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(date.weekdayShort)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(isSelected ? .white : .secondary)

                Text(date, format: .dateTime.day())
                    .font(.body.weight(.semibold))
                    .foregroundStyle(isSelected ? .white : .primary)
            }
            .frame(width: 52, height: 60)
            .background(isSelected ? Color.csGreen : Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

struct TimeSlotView: View {
    let slot: TimeSlot
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(slot.startTime.timeString)
                .font(.subheadline.weight(.medium))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(backgroundColor)
                .foregroundStyle(foregroundColor)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay {
                    if isSelected {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.csGreen, lineWidth: 2)
                    }
                }
        }
        .disabled(!slot.available)
        .buttonStyle(.plain)
    }

    private var backgroundColor: Color {
        if !slot.available { return Color(.systemGray5) }
        if isSelected { return Color.csGreenLight }
        return Color(.secondarySystemGroupedBackground)
    }

    private var foregroundColor: Color {
        if !slot.available { return Color(.systemGray3) }
        if isSelected { return .csGreen }
        return .primary
    }
}

struct DurationPillView: View {
    let hours: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text("\(hours)")
                    .font(.title3.weight(.bold))
                Text(hours == 1 ? "Std." : "Std.")
                    .font(.caption2)
            }
            .frame(width: 56, height: 50)
            .background(isSelected ? Color.csAmber : Color(.secondarySystemGroupedBackground))
            .foregroundStyle(isSelected ? .white : .primary)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    BookingView(wallbox: .preview)
        .environment(AuthManager.shared)
}
