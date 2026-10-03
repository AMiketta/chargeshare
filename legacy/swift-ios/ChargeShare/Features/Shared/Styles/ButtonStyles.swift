import SwiftUI

// MARK: - Primary Button Style (csGreen Hintergrund, weißer Text)

struct PrimaryButtonStyle: ButtonStyle {
    var isFullWidth: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: isFullWidth ? .infinity : nil)
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .background(
                Color.csGreen.opacity(configuration.isPressed ? 0.8 : 1.0)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Secondary Button Style (csGreen Rahmen und Text)

struct SecondaryButtonStyle: ButtonStyle {
    var isFullWidth: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Color.csGreen.opacity(configuration.isPressed ? 0.6 : 1.0))
            .frame(maxWidth: isFullWidth ? .infinity : nil)
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .background(Color.csGreenLight)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.csGreen.opacity(0.4), lineWidth: 1.5)
            }
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Destructive Button Style (rot)

struct DestructiveButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .background(Color.red.opacity(configuration.isPressed ? 0.7 : 1.0))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Pill Button Style (Capsule, für Filter-Chips)

struct PillButtonStyle: ButtonStyle {
    var isSelected: Bool = false
    var selectedColor: Color = .csGreen

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.medium))
            .foregroundStyle(isSelected ? .white : selectedColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                isSelected
                    ? selectedColor.opacity(configuration.isPressed ? 0.7 : 1.0)
                    : selectedColor.opacity(0.1)
            )
            .clipShape(Capsule())
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview {
    VStack(spacing: 16) {
        Button("Jetzt buchen") {}
            .buttonStyle(PrimaryButtonStyle())

        Button("Mehr erfahren") {}
            .buttonStyle(SecondaryButtonStyle())

        Button("Löschen") {}
            .buttonStyle(DestructiveButtonStyle())

        HStack {
            Button("Type 2") {}
                .buttonStyle(PillButtonStyle(isSelected: true))
            Button("CCS") {}
                .buttonStyle(PillButtonStyle())
            Button("CHAdeMO") {}
                .buttonStyle(PillButtonStyle())
        }
    }
    .padding()
}
