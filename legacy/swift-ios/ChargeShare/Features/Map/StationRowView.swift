import SwiftUI

struct StationRowView: View {
    let wallbox: Wallbox
    var isSelected: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            // Icon Badge
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Color.csGreen : Color.csGreenLight)
                    .frame(width: 44, height: 44)

                Image(systemName: "bolt.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(isSelected ? .white : .csGreen)
            }

            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(wallbox.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)

                HStack(spacing: 4) {
                    Text("\(wallbox.powerKW, format: .number.precision(.fractionLength(1))) kW")
                    Text("·")
                    Text(wallbox.connectorType.displayName)

                    if let distance = wallbox.formattedDistance {
                        Text("·")
                        Text(distance)
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)

                // Verfügbarkeit
                HStack(spacing: 4) {
                    Circle()
                        .fill(wallbox.isOnline ? Color.green : Color.red)
                        .frame(width: 6, height: 6)
                    Text(wallbox.isOnline ? wallbox.availability.todayDisplayString : "Offline")
                        .font(.caption)
                        .foregroundStyle(wallbox.isOnline ? .secondary : .red)
                }
            }

            Spacer()

            // Preis
            VStack(alignment: .trailing, spacing: 2) {
                PriceLabel(price: wallbox.pricePerKWh, style: .compact)

                if wallbox.ratingCount > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .foregroundStyle(.csAmber)
                        Text(wallbox.ratingAvg, format: .number.precision(.fractionLength(1)))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

#Preview {
    List {
        StationRowView(wallbox: .preview, isSelected: false)
        StationRowView(wallbox: .preview, isSelected: true)
    }
}

// MARK: - Preview Helper

extension Wallbox {
    static var preview: Wallbox {
        Wallbox(
            id: UUID(),
            ownerID: UUID(),
            name: "Wallbox Berlin Mitte",
            address: "Unter den Linden 1",
            city: "Berlin",
            postalCode: "10117",
            lat: 52.5163,
            lng: 13.3777,
            powerKW: 11.0,
            connectorType: .type2,
            pricePerKWh: 0.34,
            isOnline: true,
            isMetered: true,
            ocppID: nil,
            model: "Wallbox Pulsar Plus",
            manufacturer: "Wallbox",
            photos: [],
            ratingAvg: 4.7,
            ratingCount: 42,
            availability: WeeklyAvailability(
                monday: DaySchedule(from: "08:00", to: "20:00"),
                tuesday: DaySchedule(from: "08:00", to: "20:00"),
                wednesday: DaySchedule(from: "08:00", to: "20:00"),
                thursday: DaySchedule(from: "08:00", to: "20:00"),
                friday: DaySchedule(from: "08:00", to: "20:00"),
                saturday: DaySchedule(from: "10:00", to: "18:00"),
                sunday: nil
            ),
            distanceMeters: 850
        )
    }
}
