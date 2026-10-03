import SwiftUI

struct RegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel = AuthViewModel()
    @FocusState private var focusedField: Field?

    enum Field { case name, email, password, confirmPassword }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Vollständiger Name", text: $viewModel.name)
                        .textContentType(.name)
                        .focused($focusedField, equals: .name)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .email }

                    TextField("E-Mail-Adresse", text: $viewModel.email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .focused($focusedField, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }
                } header: {
                    Text("Persönliche Daten")
                }

                Section {
                    SecureField("Passwort (min. 8 Zeichen)", text: $viewModel.password)
                        .textContentType(.newPassword)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .confirmPassword }

                    SecureField("Passwort wiederholen", text: $viewModel.confirmPassword)
                        .textContentType(.newPassword)
                        .focused($focusedField, equals: .confirmPassword)
                        .submitLabel(.done)
                        .onSubmit {
                            Task { await viewModel.register() }
                        }
                } header: {
                    Text("Sicherheit")
                } footer: {
                    if viewModel.password.count > 0 && viewModel.password.count < 8 {
                        Label("Passwort zu kurz (min. 8 Zeichen)", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                            .font(.caption)
                    } else if !viewModel.confirmPassword.isEmpty && viewModel.password != viewModel.confirmPassword {
                        Label("Passwörter stimmen nicht überein", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }

                Section {
                    Button {
                        Task { await viewModel.register() }
                    } label: {
                        if viewModel.isLoading {
                            HStack {
                                Spacer()
                                ProgressView().tint(.white)
                                Spacer()
                            }
                        } else {
                            Text("Konto erstellen")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(viewModel.isLoading || !isFormValid)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }

                if let error = viewModel.errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.circle.fill")
                            .foregroundStyle(.red)
                            .font(.subheadline)
                    }
                }

                Section {
                    Text("Mit der Registrierung stimmst du unseren [Nutzungsbedingungen](https://chargeschare.de/terms) und unserer [Datenschutzerklärung](https://chargeschare.de/privacy) zu.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Registrieren")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onAppear {
                focusedField = .name
            }
        }
    }

    private var isFormValid: Bool {
        !viewModel.name.trimmingCharacters(in: .whitespaces).isEmpty &&
        viewModel.email.contains("@") &&
        viewModel.password.count >= 8 &&
        viewModel.password == viewModel.confirmPassword
    }
}

#Preview {
    RegisterView()
        .environment(AuthManager.shared)
}
