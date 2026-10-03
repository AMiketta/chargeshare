import SwiftUI

struct BookingsListView: View {
    @State private var bookings: [Booking] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showCancelAlert = false
    @State private var bookingToCancel: Booking?

    private let repository = BookingRepository()

    // MARK: - Grouped Bookings

    private var activeBookings: [Booking] {
        bookings.filter { $0.status == .active }
    }

    private var upcomingBookings: [Booking] {
        bookings.filter { $0.status.isUpcoming }
    }

    private var pastBookings: [Booking] {
        bookings.filter { $0.status.isPast }
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && bookings.isEmpty {
                    ProgressView("Lade Buchungen...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if bookings.isEmpty {
                    EmptyStateView(
                        systemImage: "calendar.badge.exclamationmark",
                        title: "Keine Buchungen",
                        subtitle: "Buche deinen ersten Ladepunkt auf der Karte."
                    )
                } else {
                    bookingsList
                }
            }
            .navigationTitle("Buchungen")
            .navigationBarTitleDisplayMode(.large)
            .refreshable {
                await loadBookings()
            }
            .task {
                await loadBookings()
            }
            .alert("Buchung stornieren", isPresented: $showCancelAlert) {
                Button("Stornieren", role: .destructive) {
                    if let booking = bookingToCancel {
                        Task { await cancelBooking(booking) }
                    }
                }
                Button("Abbrechen", role: .cancel) {
                    bookingToCancel = nil
                }
            } message: {
                Text("Möchtest du diese Buchung wirklich stornieren?")
            }
            .errorAlert(message: Binding(
                get: { errorMessage },
                set: { errorMessage = $0 }
            ))
        }
    }

    // MARK: - Bookings List

    private var bookingsList: some View {
        List {
            // Aktive Buchungen
            if !activeBookings.isEmpty {
                Section("Aktiv") {
                    ForEach(activeBookings) { booking in
                        BookingRowView(booking: booking)
                    }
                }
            }

            // Bevorstehende Buchungen
            if !upcomingBookings.isEmpty {
                Section("Bevorstehend") {
                    ForEach(upcomingBookings) { booking in
                        BookingRowView(booking: booking)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    bookingToCancel = booking
                                    showCancelAlert = true
                                } label: {
                                    Label("Stornieren", systemImage: "xmark.circle")
                                }
                            }
                    }
                }
            }

            // Vergangene Buchungen
            if !pastBookings.isEmpty {
                Section("Vergangen") {
                    ForEach(pastBookings) { booking in
                        BookingRowView(booking: booking)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Actions

    private func loadBookings() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            bookings = try await repository.myBookings()
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func cancelBooking(_ booking: Booking) async {
        do {
            try await repository.cancel(id: booking.id)
            // Lokal updaten
            if let index = bookings.firstIndex(where: { $0.id == booking.id }) {
                bookings[index].status = .cancelled
            }
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
        bookingToCancel = nil
    }
}

#Preview {
    BookingsListView()
        .environment(AuthManager.shared)
}
