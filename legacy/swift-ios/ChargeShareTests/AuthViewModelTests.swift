import XCTest
@testable import ChargeShare

@MainActor
final class AuthViewModelTests: XCTestCase {

    // MARK: - Login Validation

    func testLoginValidation_EmptyEmail_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.email = ""
        viewModel.password = "password123"

        await viewModel.login()

        XCTAssertEqual(viewModel.errorMessage, "Bitte gib deine E-Mail-Adresse ein.")
        XCTAssertFalse(viewModel.isLoading)
    }

    func testLoginValidation_InvalidEmail_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.email = "not-an-email"
        viewModel.password = "password123"

        await viewModel.login()

        XCTAssertEqual(viewModel.errorMessage, "Bitte gib eine gültige E-Mail-Adresse ein.")
    }

    func testLoginValidation_EmptyPassword_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.email = "user@example.com"
        viewModel.password = ""

        await viewModel.login()

        XCTAssertEqual(viewModel.errorMessage, "Bitte gib dein Passwort ein.")
    }

    func testLoginValidation_ValidInput_NoValidationError() async {
        let viewModel = AuthViewModel()
        viewModel.email = "user@example.com"
        viewModel.password = "password123"

        // Gültige Eingaben → kein Validierungsfehler (nur Netzwerkfehler möglich)
        await viewModel.login()

        // Validierungsfehler entfernen
        XCTAssertNotEqual(viewModel.errorMessage, "Bitte gib deine E-Mail-Adresse ein.")
        XCTAssertNotEqual(viewModel.errorMessage, "Bitte gib eine gültige E-Mail-Adresse ein.")
        XCTAssertNotEqual(viewModel.errorMessage, "Bitte gib dein Passwort ein.")
    }

    // MARK: - Register Validation

    func testRegisterValidation_EmptyName_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.name = ""
        viewModel.email = "user@example.com"
        viewModel.password = "password123"
        viewModel.confirmPassword = "password123"

        await viewModel.register()

        XCTAssertEqual(viewModel.errorMessage, "Bitte gib deinen Namen ein.")
    }

    func testRegisterValidation_ShortPassword_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.name = "Max Muster"
        viewModel.email = "user@example.com"
        viewModel.password = "1234"
        viewModel.confirmPassword = "1234"

        await viewModel.register()

        XCTAssertEqual(viewModel.errorMessage, "Das Passwort muss mindestens 8 Zeichen lang sein.")
    }

    func testRegisterValidation_PasswordMismatch_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.name = "Max Muster"
        viewModel.email = "user@example.com"
        viewModel.password = "password123"
        viewModel.confirmPassword = "differentpassword"

        await viewModel.register()

        XCTAssertEqual(viewModel.errorMessage, "Die Passwörter stimmen nicht überein.")
    }

    func testRegisterValidation_MissingAt_ShowsError() async {
        let viewModel = AuthViewModel()
        viewModel.name = "Max Muster"
        viewModel.email = "notanemail"
        viewModel.password = "password123"
        viewModel.confirmPassword = "password123"

        await viewModel.register()

        XCTAssertEqual(viewModel.errorMessage, "Bitte gib eine gültige E-Mail-Adresse ein.")
    }

    // MARK: - State Management

    func testInitialState() {
        let viewModel = AuthViewModel()

        XCTAssertEqual(viewModel.email, "")
        XCTAssertEqual(viewModel.password, "")
        XCTAssertEqual(viewModel.confirmPassword, "")
        XCTAssertEqual(viewModel.name, "")
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertNil(viewModel.errorMessage)
    }

    func testErrorMessageClearedOnNewLogin() async {
        let viewModel = AuthViewModel()
        viewModel.errorMessage = "Alter Fehler"
        viewModel.email = ""
        viewModel.password = "password"

        await viewModel.login()

        // errorMessage sollte überschrieben worden sein
        XCTAssertNotEqual(viewModel.errorMessage, "Alter Fehler")
    }
}
