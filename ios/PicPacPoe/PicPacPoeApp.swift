import PicPacPresentation
import SwiftUI

@main
struct PicPacPoeApp: App {
    var body: some Scene {
        WindowGroup {
            PicPacPoeShell()
        }
    }
}

private struct PicPacPoeShell: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var coordinator: GameCoordinator

    @MainActor
    init() {
        _coordinator = State(initialValue: AppDependencies.makeCoordinator())
    }

    var body: some View {
        Text("Pic-Pac-Poe")
            .font(.title)
            .accessibilityAddTraits(.isHeader)
            .task(id: scenePhase) {
                await coordinator.restoreAndSetSceneActive(scenePhase == .active)
            }
    }
}

@MainActor
private enum AppDependencies {
    static func makeCoordinator() -> GameCoordinator {
        let store: any LocalStateStore
        if let fileStore = try? FileLocalStateStore.appSupport() {
            store = fileStore
        } else {
            store = InMemoryLocalStateStore()
        }
        return GameCoordinator(
            store: store,
            initialSceneIsActive: false,
            requiresRestorationBeforeCommands: true
        )
    }
}
