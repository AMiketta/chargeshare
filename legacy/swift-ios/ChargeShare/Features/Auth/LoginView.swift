import SwiftUI

struct LoginView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel = AuthViewModel()
    @FocusState private var focusedField: Field?

    enum Field { case email, password }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("E-Mail-Adresse", text: $viewModel.email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .focused($focusedField, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }

                    SecureField("Passwort", text: $viewModel.password)
                        .textContentType(.password)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.done)
                        .onSubmit {
                            Task { await viewModel.login() }
                        }
                } header: {
                    Text("Anmeldedaten")
                }

                Section {
                    Button {
                        Task { await viewModel.login() }
                    } label: {
                        if viewModel.isLoading {
                            HStack {
                                Spacer()
                                ProgressView()
                                    .tint(.white)
                                Spacer()
                            }
                        } else {
                            Text("Anmelden")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(viewModel.isLoading || viewModel.email.isEmpty || viewModel.password.isEmpty)
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
                    Button("Passwort vergessen?") {
                        // Placeholder: Passwort-Reset wird in v1.1 implementiert
                    }
                    .font(.subheadline)
                    .foregroundStyle(.csGreen)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                }
            }
            .navigationTitle("Anmelden")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onAppear {
                focusedField = .email
            }
        }
    }
}

#Preview {
    LoginView()
        .environment(AuthManager.shared)
}
