import SwiftUI

struct AddWallboxView: View {
    @Environment(\.dismiss) private var dismiss

    // Form Fields
    @State private var name = ""
    @State private var address = ""
    @State private var city = ""
    @State private var postalCode = ""
    @State private var powerKW: Double = 11.0
    @State private var connectorType: ConnectorType = .type2
    @State private var pricePerKWhString = "0.34"
    @State private var isMetered = true
    @State private var availableMonFri = true
    @State private var fromTime = "08:00"
    @State private var toTime = "20:00"
    @State private var availableSat = false
    @State private var availableSun = false

    @State private var isLoading = false
    @State private var errorMessage: String?

    private let powerOptions: [Double] = [3.7, 7.4, 11.0, 22.0]

    var body: some View {
        NavigationStack {
            Form {
                // MARK: Standort
                Section("Standort") {
                    TextField("Name der Station", text: $name)
                        .autocorrectionDisabled()

                    TextField("Straße und Hausnummer", text: $address)
                        .textContentType(.streetAddressLine1)

                    HStack {
                        TextField("PLZ", text: $postalCode)
                            .textContentType(.postalCode)
                            .keyboardType(.numberPad)
                            .frame(width: 80)

                        TextField("Stadt", text: $city)
                            .textContentType(.addressCity)
                    }
                }

                // MARK: Technische Daten
                Section("Technische Daten") {
                    Picker("Leistung", selection: $powerKW) {
                        ForEach(powerOptions, id: \.self) { power in
                            Text("\(power, format: .number.precision(.fractionLength(1))) kW")
                                .tag(power)
                        }
                    }
                    .pickerStyle(.menu)

                    Picker("Steckertyp", selection: $connectorType) {
                        ForEach(ConnectorType.allCases, id: \.self) { type in
                            Text(type.displayName).tag(type)
                        }
                    }
                    .pickerStyle(.menu)

                    Toggle("MID-geeichter Zähler", isOn: $isMetered)
                }

                // MARK: Preise
                Section("Preise") {
                    HStack {
                        Text("Preis pro kWh")
                        Spacer()
                        TextField("0.34", text: $pricePerKWhString)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                        Text("€")
                            .foregroundStyle(.secondary)
                    }
                }

                // MARK: Verfügbarkeit
                Section("Verfügbarkeit") {
                    Toggle("Montag – Freitag", isOn: $availableMonFri)

                    if availableMonFri {
                        HStack {
                            Text("Von")
                            Spacer()
                            TextField("08:00", text: $fromTime)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 60)
                            Text("bis")
                                .foregroundStyle(.secondary)
                            TextField("20:00", text: $toTime)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 60)
                            Text("Uhr")
                                .foregroundStyle(.secondary)
                        }
                    }

                    Toggle("Samstag", isOn: $availableSat)
                    Toggle("Sonntag", isOn: $availableSun)
                }

                // MARK: Hinweis
                Section {
                    Label {
                        Text("Nach dem Hinzufügen wird deine Wallbox geprüft und innerhalb von 24 Stunden freigeschaltet.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } icon: {
                        Image(systemName: "info.circle.fill")
                            .foregroundStyle(.csGreen)
                    }
                }

                // MARK: Hinzufügen Button
                Section {
                    Button {
                        Task { await submit() }
                    } label: {
                        if isLoading {
                            HStack {
                                Spacer()
                                ProgressView().tint(.white)
                                Spacer()
                            }
                        } else {
                            Text("Wallbox hinzufügen")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(isLoading || !isFormValid)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                if let error = errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.circle.fill")
                            .foregroundStyle(.red)
                            .font(.subheadline)
                    }
                }
            }
            .navigationTitle("Wallbox hinzufügen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
        }
    }

    private var isFormValid: Bool {
        !name.isEmpty && !address.isEmpty && !city.isEmpty && !postalCode.isEmpty &&
        Decimal(string: pricePerKWhString.replacingOccurrences(of: ",", with: ".")) != nil
    }

    private func submit() async {
        guard isFormValid else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let priceString = pricePerKWhString.replacingOccurrences(of: ",", with: ".")
        guard let price = Decimal(string: priceString) else {
            errorMessage = "Ungültiger Preis."
            return
        }

        let daySchedule = DaySchedule(from: fromTime, to: toTime)
        let availability = WeeklyAvailability(
            monday: availableMonFri ? daySchedule : nil,
            tuesday: availableMonFri ? daySchedule : nil,
            wednesday: availableMonFri ? daySchedule : nil,
            thursday: availableMonFri ? daySchedule : nil,
            friday: availableMonFri ? daySchedule : nil,
            saturday: availableSat ? DaySchedule(from: "10:00", to: "18:00") : nil,
            sunday: availableSun ? DaySchedule(from: "10:00", to: "16:00") : nil
        )

        let request = CreateWallboxRequest(
            name: name,
            address: address,
            city: city,
            postalCode: postalCode,
            lat: 0, // In Produktion: CLGeocoder verwenden
            lng: 0,
            powerKW: powerKW,
            connectorType: connectorType.rawValue,
            pricePerKWh: price,
            isMetered: isMetered,
            availability: availability
        )

        do {
            _ = try await WallboxRepository().create(request)
            dismiss()
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

#Preview {
    AddWallboxView()
}
