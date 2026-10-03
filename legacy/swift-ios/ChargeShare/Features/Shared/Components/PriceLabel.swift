import SwiftUI

enum PriceLabelStyle {
    case normal
    case large
    case compact
}

struct PriceLabel: View {
    var price: Decimal
    var unit: String = "kWh"
    var style: PriceLabelStyle = .normal

    private var formattedPrice: String {
        let number = NSDecimalNumber(decimal: price)
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "EUR"
        formatter.currencySymbol = ""
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.locale = Locale(identifier: "de_DE")
        return (formatter.string(from: number) ?? "0,00").trimmingCharacters(in: .whitespaces) + " €"
    }

    var body: some View {
        switch style {
        case .compact:
            compactView
        case .normal:
            normalView
        case .large:
            largeView
        }
    }

    private var compactView: some View {
        Text(formattedPrice)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.csGreen)
    }

    private var normalView: some View {
        HStack(alignment: .firstTextBaseline, spacing: 2) {
            Text(formattedPrice)
                .font(.body.weight(.semibold))
                .foregroundStyle(.csGreen)
            Text("/\(unit)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var largeView: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(formattedPrice)
                .font(.title.bold())
                .foregroundStyle(.csGreen)
            Text("/\(unit)")
                .font(.body)
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        PriceLabel(price: 0.34, style: .compact)
        PriceLabel(price: 0.34, style: .normal)
        PriceLabel(price: 0.34, style: .large)
        PriceLabel(price: 1.20, unit: "Std.", style: .normal)
    }
    .padding()
}
