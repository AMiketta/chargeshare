import SwiftUI

struct ProfileView: View {
    @Environment(AuthManager.self) private var authManager
    @State private var viewModel = ProfileViewModel()
    @State private var showEditProfile = false
    @State private var showAddWallbox = false
    @State private var showDeleteAlert = false

    var body: some View {
        NavigationStack {
            List {
                // MARK: Header Section (Avatar + Stats)
                Section {
                    avatarHeaderView
                    statsRowView
                }

                // MARK: Host-Bereich (nur für Hosts)
                if authManager.currentUser?.role.isHost == true {
                    Section("Mein Host-Bereich") {
                        NavigationLink {
                            HostDashboardView()
                                .environment(authManager)
                        } label: {
                            Label("Einnahmen & Auszahlung", systemImage: "eurosign.circle.fill")
                        }

                        Button {
                            showAddWallbox = true
                        } label: {
                            Label("Wallbox hinzufügen", systemImage: "plus.circle.fill")
                        }
                        .foregroundStyle(.primary)

                        NavigationLink {
                            Text("Kalender — Coming Soon")
                                .navigationTitle("Kalender")
                        } label: {
                            Label("Buchungskalender", systemImage: "calendar.badge.clock")
                        }
                    }
                }

                // MARK: Konto
                Section("Konto") {
                    Button {
                        showEditProfile = true
                    } label: {
                        Label("Profil bearbeiten", systemImage: "person.crop.circle")
                    }
                    .foregroundStyle(.primary)

                    NavigationLink {
                        privacyView
                    } label: {
                        Label("Datenschutz & DSGVO", systemImage: "hand.raised.fill")
                    }

                    NavigationLink {
                        Text("Sprache: Deutsch")
                            .navigationTitle("Sprache")
                    } label: {
                        Label("Sprache", systemImage: "globe")
                    }
                }

                // MARK: Session
                Section("Session") {
                    Button(role: .destructive) {
                        showDeleteAlert = true
                    } label: {
                        Label("Abmelden", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }

                // MARK: App-Version
                Section {
                    HStack {
                        Text("ChargeShare")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("Version 1.0.0")
                            .foregroundStyle(.secondary)
                            .font(.caption)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Profil")
            .navigationBarTitleDisplayMode(.large)
            .refreshable {
                await viewModel.load()
            }
            .task {
                await viewModel.load()
            }
            .sheet(isPresented: $showEditProfile) {
                EditProfileView()
            }
            .sheet(isPresented: $showAddWallbox) {
                AddWallboxView()
            }
            .alert("Abmelden", isPresented: $showDeleteAlert) {
                Button("Abmelden", role: .destructive) {
                    authManager.logout()
                }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Möchtest du dich wirklich abmelden?")
            }
            .errorAlert(message: Binding(
                get: { viewModel.errorMessage },
                set: { viewModel.errorMessage = $0 }
            ))
        }
    }

    // MARK: - Subviews

    private var avatarHeaderView: some View {
        HStack(spacing: 16) {
            // Avatar
            ZStack {
                Circle()
                    .fill(Color.csGreen)
                    .frame(width: 64, height: 64)

                Text(authManager.currentUser?.initials ?? "??")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(authManager.currentUser?.name ?? "Unbekannt")
                        .font(.headline)

                    if authManager.currentUser?.isVerified == true {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundStyle(.blue)
                            .font(.subheadline)
                    }
                }

                Text(authManager.currentUser?.email ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text(authManager.currentUser?.role.displayName ?? "")
                    .font(.caption)
                    .foregroundStyle(.csGreen)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Color.csGreenLight)
                    .clipShape(Capsule())
            }
        }
        .padding(.vertical, 8)
    }

    private var statsRowView: some View {
        HStack {
            statItem(value: "\(viewModel.totalSessions)", label: "Sessions")
            Divider().frame(height: 32)
            statItem(value: String(format: "%.1f", viewModel.totalKWh), label: "kWh")
            Divider().frame(height: 32)
            statItem(value: viewModel.avgRating > 0 ? String(format: "%.1f", viewModel.avgRating) : "–", label: "Bewertung")
        }
        .padding(.vertical, 4)
    }

    private var privacyView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Datenschutzerklärung")
                    .font(.title2.bold())

                Text("ChargeShare verarbeitet deine Daten gemäß DSGVO. Deine Daten werden ausschließlich für den Betrieb des Dienstes verwendet und nicht an Dritte weitergegeben.")
                    .font(.body)

                Text("Deine Rechte:")
                    .font(.headline)
                    .padding(.top, 8)

                Label("Auskunft über gespeicherte Daten", systemImage: "doc.text")
                Label("Löschung deines Kontos", systemImage: "trash")
                Label("Export deiner Daten", systemImage: "square.and.arrow.up")
            }
            .padding(20)
        }
        .navigationTitle("Datenschutz & DSGVO")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func statItem(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headline.bold())
                .foregroundStyle(.csGreen)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - EditProfileView

struct EditProfileView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AuthManager.self) private var authManager

    @State private var name = ""
    @State private var phone = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Persönliche Daten") {
                    TextField("Name", text: $name)
                        .textContentType(.name)

                    TextField("Telefon (optional)", text: $phone)
                        .textContentType(.telephoneNumber)
                        .keyboardType(.phonePad)
                }

                Section {
                    Button {
                        Task { await save() }
                    } label: {
                        if isLoading {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Speichern").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(isLoading || name.trimmingCharacters(in: .whitespaces).isEmpty)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                if let error = errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.circle.fill")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Profil bearbeiten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onAppear {
                name = authManager.currentUser?.name ?? ""
                phone = authManager.currentUser?.phone ?? ""
            }
        }
    }

    private func save() async {
        isLoading = true
        defer { isLoading = false }
        do {
            _ = try await AuthRepository().updateMe(name: name, phone: phone.isEmpty ? nil : phone)
            dismiss()
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

#Preview {
    ProfileView()
        .environment(AuthManager.shared)
}
