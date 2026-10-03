import SwiftUI
import MapKit

struct MapView: View {
    @State private var viewModel = MapViewModel()
    @State private var selectedWallbox: Wallbox?
    @State private var showDetail = false
    @State private var sheetDetent: PresentationDetent = .medium

    var body: some View {
        ZStack(alignment: .top) {
            // Karte
            Map(position: $viewModel.cameraPosition, selection: $selectedWallbox) {
                UserAnnotation()

                ForEach(viewModel.filteredWallboxes) { wallbox in
                    Annotation(wallbox.name, coordinate: wallbox.coordinate, anchor: .bottom) {
                        WallboxPinView(
                            wallbox: wallbox,
                            isSelected: selectedWallbox?.id == wallbox.id
                        )
                        .onTapGesture {
                            withAnimation {
                                selectedWallbox = wallbox
                                viewModel.select(wallbox)
                            }
                        }
                    }
                    .tag(wallbox)
                }
            }
            .mapControls {
                MapCompass()
                MapUserLocationButton()
                MapScaleView()
            }
            .mapStyle(.standard(elevation: .realistic))
            .ignoresSafeArea(edges: .top)

            // Suchfeld
            searchBar
        }
        .safeAreaInset(edge: .bottom) {
            filterAndListSheet
        }
        .sheet(item: $selectedWallbox) { wallbox in
            WallboxDetailView(wallbox: wallbox) {
                selectedWallbox = nil
                viewModel.deselect()
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .task {
            LocationManager.shared.requestPermission()
            await viewModel.loadNearby()
        }
        .errorAlert(message: Binding(
            get: { viewModel.errorMessage },
            set: { viewModel.errorMessage = $0 }
        ))
    }

    // MARK: - Subviews

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .padding(.leading, 12)

            TextField("Wallbox suchen...", text: $viewModel.searchText)
                .autocorrectionDisabled()
                .submitLabel(.search)
        }
        .frame(height: 44)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var filterAndListSheet: some View {
        VStack(spacing: 0) {
            // Filter-Leiste
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    // Alle Stationen
                    Button {
                        viewModel.clearFilters()
                    } label: {
                        Label("Alle", systemImage: "bolt.fill")
                    }
                    .buttonStyle(PillButtonStyle(isSelected: !viewModel.hasActiveFilters))

                    // Connector-Filter
                    ForEach(ConnectorType.allCases, id: \.self) { connector in
                        Button(connector.displayName) {
                            if viewModel.connectorFilter == connector {
                                viewModel.connectorFilter = nil
                            } else {
                                viewModel.connectorFilter = connector
                            }
                        }
                        .buttonStyle(PillButtonStyle(isSelected: viewModel.connectorFilter == connector))
                    }

                    // Offline anzeigen
                    Button {
                        viewModel.showOffline.toggle()
                    } label: {
                        Label("Offline", systemImage: viewModel.showOffline ? "eye.fill" : "eye.slash")
                    }
                    .buttonStyle(PillButtonStyle(isSelected: viewModel.showOffline, selectedColor: .orange))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(.regularMaterial)

            Divider()

            // Wallbox-Liste
            if viewModel.isLoading {
                ProgressView()
                    .frame(height: 120)
                    .frame(maxWidth: .infinity)
            } else if viewModel.filteredWallboxes.isEmpty {
                EmptyStateView(
                    systemImage: "bolt.slash",
                    title: "Keine Stationen gefunden",
                    subtitle: "Versuche den Filter anzupassen.",
                    actionTitle: viewModel.hasActiveFilters ? "Filter zurücksetzen" : nil,
                    action: viewModel.hasActiveFilters ? { viewModel.clearFilters() } : nil
                )
                .frame(height: 140)
            } else {
                List(viewModel.filteredWallboxes) { wallbox in
                    Button {
                        withAnimation {
                            selectedWallbox = wallbox
                            viewModel.select(wallbox)
                        }
                    } label: {
                        StationRowView(
                            wallbox: wallbox,
                            isSelected: selectedWallbox?.id == wallbox.id
                        )
                    }
                    .listRowBackground(
                        selectedWallbox?.id == wallbox.id
                            ? Color.csGreenLight
                            : Color(.secondarySystemGroupedBackground)
                    )
                }
                .listStyle(.plain)
                .frame(height: 240)
            }
        }
        .background(.regularMaterial)
    }
}

// MARK: - WallboxPinView

struct WallboxPinView: View {
    let wallbox: Wallbox
    let isSelected: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Preis-Bubble
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? Color.csGreen : Color(.systemBackground))
                    .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                    .frame(height: 32)

                if isSelected {
                    HStack(spacing: 4) {
                        Image(systemName: "bolt.fill")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                        Text(priceText)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 8)
                } else {
                    Text(priceText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.csGreen)
                        .padding(.horizontal, 8)
                }
            }
            .fixedSize()

            // Tail
            Triangle()
                .fill(isSelected ? Color.csGreen : Color(.systemBackground))
                .frame(width: 10, height: 6)
                .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 2)
        }
        .scaleEffect(isSelected ? 1.1 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
    }

    private var priceText: String {
        let number = NSDecimalNumber(decimal: wallbox.pricePerKWh)
        return String(format: "%.2f €", number.doubleValue)
            .replacingOccurrences(of: ".", with: ",")
    }
}

// MARK: - Triangle Shape

struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

#Preview {
    MapView()
        .environment(AuthManager.shared)
        .environment(LocationManager.shared)
}
