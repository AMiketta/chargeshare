import SwiftUI

struct BookingConfirmationView: View {
    let booking: Booking
    let onDismiss: () -> Void

    @State private var showContent = false
    @State private var showCheck = false

    var body: some View {
        VStack(spacing: 32) {
            Spacer()

            // Animierter Checkmark
            ZStack {
                Circle()
                    .fill(Color.csGreenLight)
                    .frame(width: 120, height: 120)
                    .scaleEffect(showCheck ? 1.0 : 0.5)
                    .animation(.spring(response: 0.4, dampingFraction: 0.7).delay(0.1), value: showCheck)

                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.csGreen)
                    .scaleEffect(showCheck ? 1.0 : 0.3)
                    .animation(.spring(response: 0.5, dampingFraction: 0.6).delay(0.2), value: showCheck)
            }

            // Titel
            VStack(spacing: 8) {
                Text("Buchung bestätigt!")
                    .font(.title.bold())
                    .opacity(showContent ? 1 : 0)
                    .offset(y: showContent ? 0 : 20)
                    .animation(.easeOut(duration: 0.4).delay(0.4), value: showContent)

                if let wallbox = booking.wallbox {
                    Text(wallbox.name)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .opacity(showContent ? 1 : 0)
                        .offset(y: showContent ? 0 : 20)
                        .animation(.easeOut(duration: 0.4).delay(0.5), value: showContent)
                }
            }

            // Detail-Karte
            VStack(spacing: 0) {
                detailRow(icon: "mappin.circle.fill", label: "Adresse", value: booking.wallbox?.fullAddress ?? "–")
                Divider().padding(.leading, 44)
                detailRow(
                    icon: "clock.fill",
                    label: "Von",
                    value: booking.startTime.shortDateTimeString
                )
                Divider().padding(.leading, 44)
                detailRow(
                    icon: "clock.badge.checkmark",
                    label: "Bis",
                    value: booking.endTime.timeString
                )
                Divider().padding(.leading, 44)
                detailRow(
                    icon: "eurosign.circle.fill",
                    label: "Geschätzte Kosten",
                    value: booking.totalAmount.map { formatAmount($0) } ?? "–"
                )
            }
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 2)
            .opacity(showContent ? 1 : 0)
            .offset(y: showContent ? 0 : 30)
            .animation(.easeOut(duration: 0.5).delay(0.5), value: showContent)

            Spacer()

            // Zur Karte Button
            Button("Zur Karte") {
                onDismiss()
            }
            .buttonStyle(PrimaryButtonStyle())
            .opacity(showContent ? 1 : 0)
            .animation(.easeOut(duration: 0.4).delay(0.7), value: showContent)
        }
        .padding(24)
        .onAppear {
            showCheck = true
            showContent = true
        }
    }

    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(.csGreen)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.subheadline.weight(.medium))
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func formatAmount(_ amount: Decimal) -> String {
        let number = NSDecimalNumber(decimal: amount)
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "EUR"
        formatter.locale = Locale(identifier: "de_DE")
        return formatter.string(from: number) ?? "\(amount) €"
    }
}

#Preview {
    BookingConfirmationView(
        booking: Booking(
            id: UUID(),
            wallboxID: UUID(),
            driverID: UUID(),
            wallbox: .preview,
            startTime: Date(),
            endTime: Calendar.current.date(byAdding: .hour, value: 2, to: Date())!,
            status: .confirmed,
            estimatedKWh: 22.0,
            actualKWh: nil,
            pricePerKWh: 0.34,
            totalAmount: 7.48,
            stripePaymentIntentID: nil,
            stripeClientSecret: nil,
            createdAt: Date()
        ),
        onDismiss: {}
    )
}
