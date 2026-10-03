import SwiftUI

struct BookingRowView: View {
    let booking: Booking

    var body: some View {
        HStack(spacing: 12) {
            // Status-Icon
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(booking.status.color.opacity(0.12))
                    .frame(width: 40, height: 40)

                Image(systemName: booking.status.systemImage)
                    .font(.system(size: 18))
                    .foregroundStyle(booking.status.color)
            }

            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(booking.wallbox?.name ?? "Wallbox \(booking.wallboxID.uuidString.prefix(8))")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)

                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.caption)
                    Text(booking.startTime.shortDateTimeString)
                        .font(.caption)
                    Text("–")
                    Text(booking.endTime.timeString)
                        .font(.caption)
                }
                .foregroundStyle(.secondary)

                // Status Badge
                Text(booking.status.displayName)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(booking.status.color)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(booking.status.color.opacity(0.12))
                    .clipShape(Capsule())
            }

            Spacer()

            // Kosten
            if let total = booking.totalAmount {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(formatDecimal(total) + " €")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)

                    if let kwh = booking.actualKWh ?? booking.estimatedKWh {
                        Text(String(format: "%.1f kWh", kwh))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    private func formatDecimal(_ decimal: Decimal) -> String {
        let number = NSDecimalNumber(decimal: decimal)
        return String(format: "%.2f", number.doubleValue).replacingOccurrences(of: ".", with: ",")
    }
}

#Preview {
    List {
        BookingRowView(booking: Booking(
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
        ))
    }
}
