import PicPacPresentation
import SwiftUI

@main
struct PicPacPoeApp: App {
    var body: some Scene { WindowGroup { PicPacPoeRoot() } }
}

struct PicPacPoeRoot: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.accessibilityReduceMotion) private var systemReducedMotion
    @State private var coordinator: GameCoordinator
    @State private var feedback = FeedbackService()

    @MainActor
    init(coordinator: GameCoordinator? = nil) {
        _coordinator = State(initialValue: coordinator ?? AppDependencies.makeCoordinator())
    }
    private var preferredScheme: ColorScheme? {
        switch coordinator.settings.theme { case .system: nil; case .light: .light; case .dark: .dark }
    }
    private var palette: FormPalette { FormPalette(dark: (preferredScheme ?? systemScheme) == .dark) }
    var body: some View {
        ZStack {
            palette.canvas.ignoresSafeArea()
            if coordinator.isRestorationComplete {
                content.id(coordinator.state.screen)
                    .transition(coordinator.effectiveReducedMotion ? .identity : .opacity)
            }
        }
        .foregroundStyle(palette.text)
        .environment(\.formPalette, palette)
        .environment(\.formReducedMotion, coordinator.effectiveReducedMotion)
        .preferredColorScheme(preferredScheme)
        .animation(coordinator.effectiveReducedMotion ? nil : .timingCurve(0.4, 0, 0.2, 1, duration: 0.14), value: coordinator.state.screen)
        .task(id: scenePhase) {
            coordinator.setSystemReducedMotion(systemReducedMotion)
            await coordinator.restoreAndSetSceneActive(scenePhase == .active)
            if scenePhase != .active { feedback.stop() }
        }
        .onChange(of: systemReducedMotion) { _, value in coordinator.setSystemReducedMotion(value) }
        .onChange(of: coordinator.feedbackEvent, initial: true) { _, event in
            guard let event else { return }
            coordinator.consumeFeedback(id: event.id)
            if coordinator.isSceneActive { feedback.play(event.kind, settings: coordinator.settings) }
        }
        .onChange(of: coordinator.settings.soundEnabled) { _, enabled in if !enabled { feedback.stop() } }
        #if DEBUG
        .onChange(of: coordinator.isRestorationComplete, initial: true) { _, complete in
            guard complete else { return }
            Task { @MainActor in
                await Task.yield()
                DebugLaunchConfiguration.signalScreenshotReady()
            }
        }
        #endif
    }
    @ViewBuilder private var content: some View {
        switch coordinator.state.screen {
        case .home: HomeView(coordinator: coordinator)
        case .game: GameView(coordinator: coordinator)
        case .howTo: HowToPlayView(coordinator: coordinator)
        case .settings: SettingsView(coordinator: coordinator)
        case .aiLab: AILabView(coordinator: coordinator)
        }
    }
}

@MainActor
enum AppDependencies {
    static func makeCoordinator() -> GameCoordinator {
        #if DEBUG
        if let fixture = DebugLaunchConfiguration.makeCoordinator() { return fixture }
        #endif
        let store: any LocalStateStore
        if let fileStore = try? FileLocalStateStore.appSupport() {
            store = fileStore
        } else {
            store = InMemoryLocalStateStore()
        }
        return GameCoordinator(
            aiWorker: ProductionAIWorker(),
            store: store,
            initialSceneIsActive: false,
            requiresRestorationBeforeCommands: true
        )
    }
}
