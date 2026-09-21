import PicPacAI
import PicPacPresentation
import PicPacCore
import CryptoKit
import SwiftUI
import UIKit
@testable import PicPacPoe
import XCTest

final class PicPacPoeIntegrationTests: XCTestCase {
    @MainActor
    func testAppHostLoadsPresentationPackage() {
        XCTAssertEqual(Bundle.main.bundleIdentifier, "dev.saipranith.picpacpoe")
        let sceneManifest = Bundle.main.object(
            forInfoDictionaryKey: "UIApplicationSceneManifest"
        ) as? [String: Any]
        XCTAssertEqual(sceneManifest?["UIApplicationSupportsMultipleScenes"] as? Bool, false)

        let coordinator = GameCoordinator(store: InMemoryLocalStateStore())
        XCTAssertEqual(coordinator.state.screen, .home)
        XCTAssertEqual(coordinator.state.stage, .playing)
    }

    @MainActor
    func testBundledBrandFontsResolveAndMatchCanonicalFiles() throws {
        for (name, hash) in [
            ("fredoka_medium", "024bec999fd21bd237b2866ec5c9189a1522db63d88fb01aabafef8c5b4d6916"),
            ("fredoka_semibold", "95839d50cc746b491c4710673be1d2cc8179a132f9600851810863922ddc12f9")
        ] {
            let url = try XCTUnwrap(Bundle.main.url(forResource: name, withExtension: "ttf"))
            let data = try Data(contentsOf: url)
            XCTAssertEqual(SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined(), hash)
        }
        XCTAssertEqual(try XCTUnwrap(UIFont(name: "Fredoka-Medium", size: 30)).fontName, "Fredoka-Medium")
        XCTAssertEqual(try XCTUnwrap(UIFont(name: "Fredoka-SemiBold", size: 40)).fontName, "Fredoka-SemiBold")
        let license = try XCTUnwrap(Bundle.main.url(forResource: "fredoka-OFL", withExtension: "txt"))
        XCTAssertTrue(try String(contentsOf: license, encoding: .utf8).contains("SIL OPEN FONT LICENSE"))
    }

    @MainActor
    func testWordmarkFittingKeepsAllFiveRunsInsideGutters() {
        XCTAssertEqual(HomeWordmark.parts.joined(), "Pic-Pac-Poe")
        for width: CGFloat in [240, 280, 320, 353, 580] {
            for nominal: CGFloat in [40, 52, 80, 120] {
                let fitted = HomeWordmark.fittedSize(nominal: nominal, width: width)
                XCTAssertLessThanOrEqual(fitted, nominal)
                XCTAssertLessThanOrEqual(HomeWordmark.measuredWidths(size: fitted).reduce(0, +), width - 2 + 0.001)
            }
        }
    }

    func testHomeMotionIsBoundedAndReducedMotionNeverScales() {
        for milliseconds in stride(from: 0, through: 5600, by: 10) {
            for reduced in [false, true] {
                let motion = FormMotion.homeSymbol(elapsed: Double(milliseconds) / 1000, reduced: reduced)
                XCTAssertEqual(motion.xAlpha + motion.oAlpha, 1, accuracy: 1e-10)
                XCTAssertTrue((0...1).contains(motion.xAlpha))
                XCTAssertTrue((0...1).contains(motion.oAlpha))
                if reduced {
                    XCTAssertEqual(motion.xScale, 1)
                    XCTAssertEqual(motion.oScale, 1)
                } else {
                    XCTAssertTrue((0.969...1.036).contains(motion.xScale))
                    XCTAssertTrue((0.969...1.036).contains(motion.oScale))
                }
            }
        }
        XCTAssertEqual(FormMotion.homeSymbol(elapsed: 0, reduced: false).xAlpha, 1, accuracy: 1e-6)
        XCTAssertEqual(FormMotion.homeSymbol(elapsed: 1.4, reduced: false).oAlpha, 1, accuracy: 1e-6)
        XCTAssertEqual(FormMotion.homeSymbol(elapsed: 2.8, reduced: false).xAlpha, 1, accuracy: 1e-6)
        XCTAssertEqual(FormMotion.titleProgress(elapsed: 0.5, group: 0), 1)
        XCTAssertEqual(FormMotion.titleProgress(elapsed: 0.62, group: 2), 1, accuracy: 1e-6)
    }

    @MainActor
    func testBothPalettesPreserveWordmarkAndActorContrast() {
        func luminance(_ color: Color) -> CGFloat {
            var r: CGFloat = 0; var g: CGFloat = 0; var b: CGFloat = 0; var a: CGFloat = 0
            UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
            func linear(_ value: CGFloat) -> CGFloat { value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4) }
            return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        }
        func contrast(_ a: Color, _ b: Color) -> CGFloat {
            let x = luminance(a); let y = luminance(b)
            return (max(x, y) + 0.05) / (min(x, y) + 0.05)
        }
        for dark in [false, true] {
            let palette = FormPalette(dark: dark)
            for color in [palette.x, palette.text, palette.o, palette.secondary] { XCTAssertGreaterThanOrEqual(contrast(color, palette.canvas), 4.5) }
            for color in [palette.playerOne, palette.playerTwo, palette.focus] { XCTAssertGreaterThanOrEqual(contrast(color, palette.surface), 3) }
        }
    }

    func testBoardAnnouncementsDistinguishTargetFromCommittedPiece() {
        XCTAssertEqual(BoardSemantics.cell(8, symbol: nil, target: 8, moveSymbol: .o, stage: .aiTargeting), "Computer selected row 3, column 3")
        XCTAssertEqual(BoardSemantics.cell(8, symbol: .o, target: 8, moveSymbol: .o, stage: .aiSettling), "Computer placed O in row 3, column 3")
        XCTAssertEqual(BoardSemantics.cell(8, symbol: .o, target: nil, moveSymbol: nil, stage: .playing), "Row 3, column 3, O")
    }

    @MainActor
    func testTransitionSpeechNeverReplaysRestorationOrDuplicatesFocusedContent() throws {
        let token = TurnToken(22)
        let pending = try PicPacState(board: Board(symbols: [.x, nil, nil, nil, nil, nil, nil, nil, nil]),
                                      activePlayer: .two, remainingX: 4, remainingO: 4,
                                      phase: .awaitingPlacement(held: .o, token: token), starter: .one, revision: 1)
        var state = GamePresentationState(screen: .game, mode: .picPacAI, picPac: pending,
                                          stage: .aiTargeting, aiTargetCell: 8, aiMoveSymbol: .o)
        for stage in TurnStage.allCases {
            state.stage = stage
            XCTAssertNil(GameSpeech.announcement(from: stage, to: stage, state: state, sceneActive: true),
                         "Restoring \(stage) must not announce a historical transition")
            for previous in TurnStage.allCases {
                XCTAssertNil(GameSpeech.announcement(from: previous, to: stage, state: state, sceneActive: false),
                             "Inactive transition \(previous) to \(stage) must stay silent")
            }
        }
        state.stage = .aiTargeting
        XCTAssertNil(state.board[Cell(8)])
        XCTAssertEqual(GameSpeech.announcement(from: .revealing, to: .aiTargeting, state: state, sceneActive: true),
                       "Computer selected row 3, column 3")
        state.stage = .aiThinking
        XCTAssertEqual(GameSpeech.announcement(from: .revealing, to: .aiThinking, state: state, sceneActive: true),
                       "Computer is thinking. Choosing where to place O.")
        guard case let .accepted(committed, _) = PicPacRules.place(pending, at: Cell(8), token: token) else {
            return XCTFail("Valid computer move rejected")
        }
        state.picPac = committed
        state.stage = .aiPlacing
        XCTAssertEqual(state.board[Cell(8)], .o)
        XCTAssertEqual(GameSpeech.announcement(from: .aiTargeting, to: .aiPlacing, state: state, sceneActive: true),
                       "Computer placed O in row 3, column 3")
        state.stage = .aiSettling
        XCTAssertNil(GameSpeech.announcement(from: .aiPlacing, to: .aiSettling, state: state, sceneActive: true),
                     "Settlement must not repeat the placement announcement")
        state.stage = .turnStart
        XCTAssertEqual(GameSpeech.announcement(from: .aiSettling, to: .turnStart, state: state, sceneActive: true),
                       "Your turn. A piece comes from the shared bag.")
        for stage in [TurnStage.revealing, .terminal, .handoff, .playing] {
            state.stage = stage
            for previous in TurnStage.allCases {
                XCTAssertNil(GameSpeech.announcement(from: previous, to: stage, state: state, sceneActive: true),
                             "Focused content at \(stage) must not also post direct announcement speech")
            }
        }
    }

    @MainActor
    func testDebugCaptureSnapshotsRestoreWithoutLosingStage() async throws {
        #if DEBUG
        for scenario in ["home", "classic", "human-placement", "human-turn-start", "human-reveal", "local-handoff", "local-reveal", "computer-targeting", "computer-settled", "computer-placement", "computer-reveal", "computer-thinking", "result", "terminal-settling-win", "draw-result", "terminal-settling-draw", "settings", "how-to", "ai-lab"] {
            let expected = try DebugLaunchConfiguration.snapshot(for: scenario)
            let coordinator = try XCTUnwrap(DebugLaunchConfiguration.makeCoordinator(arguments: ["-screenshot-scenario", scenario, "-screenshot-theme", "dark"]))
            await coordinator.restore()
            XCTAssertEqual(coordinator.state, expected.state, scenario)
            XCTAssertEqual(coordinator.settings.theme, .dark, scenario)
            XCTAssertNil(coordinator.feedbackEvent, scenario)
        }
        #else
        throw XCTSkip("Capture fixtures are DEBUG-only; governed simulator captures exercise them in Debug.")
        #endif
    }

    @MainActor
    func testSettingsCaptureMatchesCanonicalControlStates() async throws {
        #if DEBUG
        let dark = try XCTUnwrap(DebugLaunchConfiguration.makeCoordinator(arguments: [
            "-screenshot-scenario", "settings", "-screenshot-theme", "dark"
        ]))
        await dark.restore()
        XCTAssertTrue(dark.settings.soundEnabled)
        XCTAssertTrue(dark.settings.hapticsEnabled)
        XCTAssertFalse(dark.settings.reducedMotion)
        XCTAssertEqual(dark.settings.theme, .dark)

        let light = try XCTUnwrap(DebugLaunchConfiguration.makeCoordinator(arguments: [
            "-screenshot-scenario", "settings", "-screenshot-theme", "light"
        ]))
        await light.restore()
        XCTAssertFalse(light.settings.soundEnabled)
        XCTAssertFalse(light.settings.hapticsEnabled)
        XCTAssertTrue(light.settings.reducedMotion)
        XCTAssertEqual(light.settings.theme, .light)
        #else
        throw XCTSkip("Capture fixture settings are DEBUG-only; governed captures exercise them in Debug.")
        #endif
    }

    @MainActor
    func testFeedbackCuesAreSmallLocalPCMFiles() throws {
        for kind in FeedbackKind.allCases {
            let data = FeedbackService.wave(kind)
            XCTAssertEqual(String(data: data.prefix(4), encoding: .ascii), "RIFF")
            XCTAssertEqual(String(data: data[8..<12], encoding: .ascii), "WAVE")
            XCTAssertLessThan(data.count, 9000)
            XCTAssertGreaterThan(data.count, 3000)
        }
    }
}

/// These tests run inside the real simulator application. CADisplayLink measures
/// delivered display opportunities/callbacks, not GPU completion or device feel.
@MainActor
final class PicPacPoePerformanceIntegrationTests: XCTestCase {
    func testVisibleHomeMotionAndLiveHardTurnDisplayCadence() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = try XCTUnwrap(scene.windows.first(where: \.isKeyWindow))
        let previousController = window.rootViewController
        // Replace the visible controller so an obscured second Home timeline
        // cannot contaminate the sample; restore the original test host after.
        defer {
            window.rootViewController = previousController
            window.makeKeyAndVisible()
        }
        var samples: [[String: Any]] = []
        var homeSequences: [[String: Any]] = []
        for reduced in [false, true] {
            let random = HostedDrawProbe()
            let store = InMemoryLocalStateStore()
            let coordinator = GameCoordinator(drawRandom: random, store: store)
            await coordinator.restore()
            await coordinator.updateSettings(AppSettings(soundEnabled: false, hapticsEnabled: false, reducedMotion: reduced))
            await coordinator.markTitleEntranceConsumed()
            window.rootViewController = UIHostingController(rootView: PicPacPoeRoot(coordinator: coordinator))
            window.makeKeyAndVisible()
            try await Task.sleep(for: .milliseconds(350))
            XCTAssertNotNil(window.rootViewController?.view.window)
            XCTAssertFalse(window.isHidden)
            XCTAssertTrue(coordinator.isSceneActive)
            XCTAssertEqual(coordinator.effectiveReducedMotion, reduced)
            let before = coordinator.state
            let writes = await store.snapshotWriteCount
            let probe = HostedDisplayProbe()
            probe.start()
            try await Task.sleep(for: .milliseconds(3_100))
            let sample = probe.stop(name: reduced ? "home-reduce-motion" : "home-normal-motion")
            samples.append(sample)
            XCTAssertEqual(coordinator.state, before)
            XCTAssertEqual(random.calls, 0)
            XCTAssertNil(coordinator.feedbackEvent)
            let afterWrites = await store.snapshotWriteCount
            XCTAssertEqual(afterWrites, writes, "The Home loop must never enter restoration")
            XCTAssertGreaterThan(probe.frameIntervals.count, 30)
            XCTAssertLessThan(probe.arrivalIntervals.max() ?? .infinity, 1_000, "One-second main-runloop stall")
            // Collect the real visible hierarchy separately: rasterization is
            // intentionally excluded from the display-cadence interval above.
            let mode = reduced ? "home-reduce-motion" : "home-normal-motion"
            let sequenceStart = ProcessInfo.processInfo.systemUptime
            var frames: [[String: Any]] = []
            for target in [0.0, 0.34, 1.4] {
                let wait = target - (ProcessInfo.processInfo.systemUptime - sequenceStart)
                if wait > 0 { try await Task.sleep(for: .seconds(wait)) }
                frames.append(try attachWindow(window, name: "\(mode)-active-\(target)",
                                               sequenceStart: sequenceStart, requestedOffset: target).metadata)
            }
            XCTAssertEqual(random.calls, 0)
            XCTAssertEqual(coordinator.state, before)
            let activeSequenceWrites = await store.snapshotWriteCount
            XCTAssertEqual(activeSequenceWrites, writes)
            await coordinator.setSceneActive(false)
            // Scene suspension leaves game/restoration content unchanged; its
            // single checkpoint is distinct from decorative animation writes.
            XCTAssertFalse(coordinator.isSceneActive)
            let inactiveWrites = await store.snapshotWriteCount
            try await Task.sleep(for: .milliseconds(120))
            let inactiveStart = ProcessInfo.processInfo.systemUptime
            let inactiveFirst = try attachWindow(window, name: "\(mode)-inactive-start",
                                                 sequenceStart: inactiveStart, requestedOffset: 0)
            try await Task.sleep(for: .milliseconds(1_500))
            let inactiveLast = try attachWindow(window, name: "\(mode)-inactive-plus-1.5",
                                                sequenceStart: inactiveStart, requestedOffset: 1.5)
            let finalWrites = await store.snapshotWriteCount
            XCTAssertEqual(inactiveWrites, finalWrites)
            XCTAssertEqual(coordinator.state, before)
            XCTAssertEqual(random.calls, 0)
            XCTAssertNil(coordinator.feedbackEvent)
            XCTAssertEqual(inactiveFirst.pixels, inactiveLast.pixels, "Inactive Home hierarchy must be pixel stable")
            homeSequences.append([
                "mode": mode, "activeFrames": frames,
                "inactiveFrames": [inactiveFirst.metadata, inactiveLast.metadata],
                "sequenceStartUptimeSeconds": sequenceStart,
                "inactiveSequenceStartUptimeSeconds": inactiveStart,
                "timing": "Requested offsets from sequence start after cadence sample; elapsedObservedSeconds is authoritative, not a controlled animation phase",
                "capture": "Full visible UIWindow.drawHierarchy, including safe areas; no crop or synthetic content",
                "gameStateUnchanged": coordinator.state == before,
                "drawCalls": random.calls, "decorativeSnapshotWrites": activeSequenceWrites - writes,
                "inactiveDecorativeSnapshotWrites": finalWrites - inactiveWrites,
                "inactivePixelIdentical": inactiveFirst.pixels == inactiveLast.pixels
            ])
        }

        let worker = HostedCountingProductionWorker()
        let coordinator = GameCoordinator(drawRandom: HostedDrawProbe(), aiWorker: worker, store: InMemoryLocalStateStore())
        await coordinator.restore()
        await coordinator.updateSettings(AppSettings(soundEnabled: false, hapticsEnabled: false))
        window.rootViewController = UIHostingController(rootView: PicPacPoeRoot(coordinator: coordinator))
        window.makeKeyAndVisible()
        await coordinator.startPicPacAI(difficulty: .hard)
        try await waitForPlaying(coordinator)
        let probe = HostedDisplayProbe()
        probe.start()
        await coordinator.place(at: 0)
        var stages: [String] = []
        let deadline = ContinuousClock.now.advanced(by: .seconds(20))
        while ContinuousClock.now < deadline {
            let state = coordinator.state
            if stages.last != state.stage.rawValue { stages.append(state.stage.rawValue) }
            if state.stage == .aiTargeting {
                XCTAssertEqual(state.board.occupiedCount, 1)
                if let target = state.aiTargetCell { XCTAssertNil(state.board[Cell(target)]) }
            }
            if [.aiPlacing, .aiSettling].contains(state.stage) {
                XCTAssertEqual(state.board.occupiedCount, 2)
                XCTAssertEqual(state.displayedPlayer, .two)
                XCTAssertFalse(state.inputEnabled)
            }
            if state.stage == .playing, state.board.occupiedCount == 2 { break }
            try await Task.sleep(for: .milliseconds(8))
        }
        samples.append(probe.stop(name: "live-hard-reveal-target-place-settle"))
        XCTAssertEqual(coordinator.state.stage, .playing)
        XCTAssertEqual(coordinator.state.board.occupiedCount, 2)
        XCTAssertEqual(stages.filter { $0 != TurnStage.aiThinking.rawValue }, [
            "turnStart", "revealing", "aiTargeting", "aiPlacing", "aiSettling", "turnStart", "revealing", "playing"
        ])
        XCTAssertGreaterThan(probe.frameIntervals.count, 30)
        XCTAssertLessThan(probe.arrivalIntervals.max() ?? .infinity, 1_000, "One-second main-runloop stall")
        let searches = await worker.callCount
        XCTAssertEqual(searches, 1)
        await coordinator.setSceneActive(false)
        try record("SIMULATOR_FRAME_PACING_JSON", value: [
            "schemaVersion": 1, "environment": simulatorEnvironment,
            "measurement": "CADisplayLink delivered timestamps and actual main-runloop callback intervals",
            "requestedDisplayLinkHz": 60, "gpuCompletionMeasured": false,
            "physicalDeviceEvidence": false, "samples": samples, "liveHardStages": stages,
            "homeDrawCalls": 0, "homeDecorativeSnapshotWrites": 0,
            "homeTemporalSequences": homeSequences
        ])
    }

    func testSimulatorProductionSearchBudgetLatencyAndCancellation() async throws {
        let opening = try AiObservation(board: .empty, activePlayer: .two, agentPlayer: .two,
                                        heldSymbol: .x, remainingX: 4, remainingO: 5)
        var cold: [Double] = [], warm: [Double] = [], mcts: [Double] = [], cancellation: [Double] = []
        var coldNodes: [Int] = [], warmNodes: [Int] = []
        for _ in 0..<3 {
            let engine = ProductionAIEngine(policy: .empty)
            let first = try await engine.chooseMove(for: opening, opponent: .hard)
            let second = try await engine.chooseMove(for: opening, opponent: .hard)
            XCTAssertEqual(first.cell, Cell(4))
            XCTAssertEqual(second.cell, first.cell)
            guard case let .search(a) = first.diagnostics,
                  case let .search(b) = second.diagnostics else { return XCTFail("Hard diagnostics missing") }
            XCTAssertEqual(a.value, 5.0 / 21.0, accuracy: 1e-12)
            XCTAssertGreaterThan(b.cacheHits, 0)
            cold.append(Double(a.elapsedNanoseconds) / 1_000_000)
            warm.append(Double(b.elapsedNanoseconds) / 1_000_000)
            coldNodes.append(a.nodes); warmNodes.append(b.nodes)
            let sampled = try await engine.chooseMove(for: opening, opponent: .mcts)
            guard case let .search(stats) = sampled.diagnostics else { return XCTFail("MCTS diagnostics missing") }
            XCTAssertEqual(stats.simulations, 2_000)
            mcts.append(Double(stats.elapsedNanoseconds) / 1_000_000)
            let cancellableEngine = ProductionAIEngine(policy: .empty)
            let search = Task { try await cancellableEngine.chooseMove(for: opening, opponent: .hard) }
            try await Task.sleep(for: .milliseconds(5))
            let start = ContinuousClock.now
            search.cancel()
            do {
                _ = try await search.value
                XCTFail("Cancellation did not interrupt cold Hard search")
            } catch is CancellationError {
                cancellation.append(hostedMilliseconds(start.duration(to: .now)))
            }
        }
        XCTAssertLessThan(cancellation.max() ?? .infinity, 1_000)
        try record("SIMULATOR_SEARCH_PERFORMANCE_JSON", value: [
            "schemaVersion": 1, "environment": simulatorEnvironment,
            "physicalDeviceEvidence": false, "sampleCount": 3,
            "hardColdMilliseconds": cold, "hardWarmMilliseconds": warm,
            "hardColdNodes": coldNodes, "hardWarmNodes": warmNodes,
            "mctsMilliseconds": mcts, "mctsCompletedSimulationsPerCall": 2_000,
            "hardInFlightCancellationMilliseconds": cancellation
        ])
    }

    private var simulatorEnvironment: [String: String] {
        ["kind": "actual-iOS-simulator-hosted-test", "configuration": "Test (application -Onone; production Swift package -O)",
         "systemVersion": UIDevice.current.systemVersion,
         "model": ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] ?? UIDevice.current.model,
         "deviceName": ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] ?? UIDevice.current.name]
    }

    private func attachWindow(_ window: UIWindow, name: String, sequenceStart: Double,
                              requestedOffset: Double) throws -> (metadata: [String: Any], pixels: Data) {
        let captureUptime = ProcessInfo.processInfo.systemUptime
        let format = UIGraphicsImageRendererFormat()
        format.scale = window.screen.scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds, format: format)
        let image = renderer.image { _ in
            XCTAssertTrue(window.drawHierarchy(in: window.bounds, afterScreenUpdates: true))
        }
        let png = try XCTUnwrap(image.pngData())
        let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        let cgImage = try XCTUnwrap(image.cgImage)
        let pixels = try XCTUnwrap(cgImage.dataProvider?.data) as Data
        return (["attachmentName": name, "requestedOffsetSeconds": requestedOffset,
                 "captureUptimeSeconds": captureUptime,
                 "elapsedObservedSeconds": captureUptime - sequenceStart,
                 "pixelWidth": cgImage.width, "pixelHeight": cgImage.height,
                 "pngSHA256": SHA256.hash(data: png).map { String(format: "%02x", $0) }.joined()], pixels)
    }

    private func waitForPlaying(_ coordinator: GameCoordinator) async throws {
        let deadline = ContinuousClock.now.advanced(by: .seconds(15))
        while coordinator.state.stage != .playing, ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(8))
        }
        XCTAssertEqual(coordinator.state.stage, .playing)
    }

    private func record(_ prefix: String, value: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
        print("\(prefix) \(String(decoding: data, as: UTF8.self))")
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
        attachment.name = prefix
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

@MainActor
private final class HostedDrawProbe: DrawRandomSource {
    private(set) var calls = 0
    func nextInt(upperBound: Int) -> Int { calls += 1; return 0 }
}

private actor HostedCountingProductionWorker: AIWorker {
    private let production = ProductionAIWorker()
    private(set) var callCount = 0
    func chooseMove(for observation: AiObservation, difficulty: Difficulty) async throws -> Int {
        callCount += 1
        return try await production.chooseMove(for: observation, difficulty: difficulty)
    }
}

@MainActor
private final class HostedDisplayProbe: NSObject {
    private var link: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private var previousArrival: CFTimeInterval?
    private(set) var frameIntervals: [Double] = []
    private(set) var arrivalIntervals: [Double] = []
    private var nominalIntervals: [Double] = []

    func start() {
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)
        self.link = link
        link.add(to: .main, forMode: .common)
    }

    @objc private func tick(_ link: CADisplayLink) {
        let arrival = CACurrentMediaTime()
        if let previousTimestamp { frameIntervals.append((link.timestamp - previousTimestamp) * 1_000) }
        if let previousArrival { arrivalIntervals.append((arrival - previousArrival) * 1_000) }
        nominalIntervals.append((link.targetTimestamp - link.timestamp) * 1_000)
        previousTimestamp = link.timestamp
        previousArrival = arrival
    }

    func stop(name: String) -> [String: Any] {
        link?.invalidate(); link = nil
        let nominal = percentile(nominalIntervals, 0.5)
        return ["scenario": name, "frameSamples": frameIntervals.count,
                "nominalIntervalMilliseconds": nominal,
                "timestampP50Milliseconds": percentile(frameIntervals, 0.5),
                "timestampP95Milliseconds": percentile(frameIntervals, 0.95),
                "timestampMaxMilliseconds": frameIntervals.max() ?? 0,
                "callbackP50Milliseconds": percentile(arrivalIntervals, 0.5),
                "callbackP95Milliseconds": percentile(arrivalIntervals, 0.95),
                "callbackMaxMilliseconds": arrivalIntervals.max() ?? 0,
                "intervalsOverOneAndHalfNominal": frameIntervals.filter { $0 > nominal * 1.5 }.count,
                "frameIntervalsMilliseconds": frameIntervals,
                "callbackIntervalsMilliseconds": arrivalIntervals]
    }

    private func percentile(_ values: [Double], _ fraction: Double) -> Double {
        guard !values.isEmpty else { return 0 }
        let values = values.sorted()
        return values[min(values.count - 1, Int(Double(values.count - 1) * fraction))]
    }
}

private func hostedMilliseconds(_ duration: Duration) -> Double {
    Double(duration.components.seconds) * 1_000 + Double(duration.components.attoseconds) / 1e15
}
