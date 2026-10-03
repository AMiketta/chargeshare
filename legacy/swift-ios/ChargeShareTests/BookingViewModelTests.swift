import XCTest
@testable import ChargeShare

@MainActor
final class BookingViewModelTests: XCTestCase {

    private var wallbox: Wallbox!

    override func setUp() {
        super.setUp()
        wallbox = Wallbox(
            id: UUID(),
            ownerID: UUID(),
            name: "Test Wallbox",
            address: "Teststraße 1",
            city: "Berlin",
            postalCode: "10115",
            lat: 52.5200,
            lng: 13.4050,
            powerKW: 11.0,
            connectorType: .type2,
            pricePerKWh: Decimal(string: "0.34")!,
            isOnline: true,
            isMetered: true,
            ocppID: nil,
            model: "Test Model",
            manufacturer: "Test",
            photos: [],
            ratingAvg: 4.5,
            ratingCount: 10,
            availability: WeeklyAvailability(
                monday: DaySchedule(from: "08:00", to: "20:00"),
                tuesday: nil,
                wednesday: nil,
                thursday: nil,
                friday: nil,
                saturday: nil,
                sunday: nil
            ),
            distanceMeters: 500
        )
    }

    // MARK: - Initialization

    func testInitialState() {
        let viewModel = BookingViewModel(wallbox: wallbox)

        XCTAssertNil(viewModel.selectedStartTime)
        XCTAssertEqual(viewModel.selectedDuration, 2)
        XCTAssertEqual(viewModel.selectedPayment, .applePay)
        XCTAssertFalse(viewModel.isLoading)
        XCTAssertFalse(viewModel.isBooking)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertNil(viewModel.createdBooking)
    }

    // MARK: - Computed Properties

    func testEndTime_WhenStartTimeSet_CalculatedCorrectly() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        let startTime = Date(timeIntervalSinceNow: 3600) // 1 Stunde ab jetzt
        viewModel.selectedStartTime = startTime
        viewModel.selectedDuration = 2

        let expectedEndTime = Calendar.current.date(byAdding: .hour, value: 2, to: startTime)!
        XCTAssertEqual(viewModel.endTime?.timeIntervalSince1970 ?? 0,
                       expectedEndTime.timeIntervalSince1970,
                       accuracy: 1.0)
    }

    func testEndTime_WhenNoStartTime_IsNil() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = nil

        XCTAssertNil(viewModel.endTime)
    }

    func testEstimatedKWh_WhenStartTimeSet() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = Date()
        viewModel.selectedDuration = 2

        // 11 kW × 2 Stunden = 22 kWh
        XCTAssertEqual(viewModel.estimatedKWh, 22.0, accuracy: 0.001)
    }

    func testEstimatedKWh_WhenNoStartTime_IsNil() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = nil

        XCTAssertNil(viewModel.estimatedKWh)
    }

    func testEstimatedCost_WhenKWhKnown() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = Date()
        viewModel.selectedDuration = 2

        // 11 kW × 2 Std × 0.34 €/kWh = 7.48 €
        let expectedCost = Decimal(string: "7.48")!
        XCTAssertEqual(viewModel.estimatedCost, expectedCost)
    }

    func testEstimatedCost_WhenNoStartTime_IsNil() {
        let viewModel = BookingViewModel(wallbox: wallbox)

        XCTAssertNil(viewModel.estimatedCost)
    }

    // MARK: - canBook

    func testCanBook_WhenStartTimeSet_IsTrue() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = Date()

        XCTAssertTrue(viewModel.canBook)
    }

    func testCanBook_WhenNoStartTime_IsFalse() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = nil

        XCTAssertFalse(viewModel.canBook)
    }

    func testCanBook_WhenBooking_IsFalse() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        viewModel.selectedStartTime = Date()
        viewModel.isBooking = true

        XCTAssertFalse(viewModel.canBook)
    }

    // MARK: - Available Dates

    func testAvailableDates_Returns7Days() {
        let viewModel = BookingViewModel(wallbox: wallbox)

        XCTAssertEqual(viewModel.availableDates.count, 7)
    }

    func testAvailableDates_FirstDateIsToday() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        let today = Calendar.current.startOfDay(for: .now)

        XCTAssertEqual(viewModel.availableDates.first, today)
    }

    func testAvailableDates_SeventhDateIs6DaysFromNow() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        let sixDaysFromNow = Calendar.current.date(
            byAdding: .day, value: 6,
            to: Calendar.current.startOfDay(for: .now)
        )!

        XCTAssertEqual(viewModel.availableDates.last, sixDaysFromNow)
    }

    // MARK: - Duration Options

    func testDurationOptions_ContainsExpectedValues() {
        let viewModel = BookingViewModel(wallbox: wallbox)
        let expected = [1, 2, 3, 4, 6, 8]

        XCTAssertEqual(viewModel.durationOptions, expected)
    }

    // MARK: - Payment Methods

    func testPaymentMethodIcons_NotEmpty() {
        for method in BookingViewModel.PaymentMethod.allCases {
            XCTAssertFalse(method.icon.isEmpty)
            XCTAssertFalse(method.label.isEmpty)
        }
    }

    func testPaymentMethodId_IsUniquePerCase() {
        let ids = BookingViewModel.PaymentMethod.allCases.map { $0.id }
        let uniqueIds = Set(ids)
        XCTAssertEqual(ids.count, uniqueIds.count)
    }
}
