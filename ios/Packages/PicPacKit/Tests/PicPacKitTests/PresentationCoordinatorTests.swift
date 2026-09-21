import Foundation
import XCTest
@testable import PicPacCore
@testable import PicPacPresentation

private typealias GoldenObject = [String: Any]

@MainActor
final class PresentationCoordinatorTests: XCTestCase {
    func testPresentationTimingContractKeepsEveryRequiredBeat() {
        let expected: [TurnStage: (normal: Int, reduced: Int)] = [
            .turnStart: (300, 160),
            .revealing: (650, 500),
            .aiTargeting: (280, 160),
            .aiPlacing: (340, 180),
            .aiSettling: (480, 320),
        ]

        for stage in TurnStage.allCases {
            XCTAssertEqual(stage.delayMilliseconds(reducedMotion: false), expected[stage]?.normal)
            XCTAssertEqual(stage.delayMilliseconds(reducedMotion: true), expected[stage]?.reduced)
        }
    }

    func testAllSevenPresentationOwnedGoldenFixturesExecuteBehaviorally() async throws {
        let root = try goldenRoot()
        let presentation = try objects(root, key: "presentationScenarios")
        let restoration = try objects(root, key: "restorationScenarios")
        var executed: Set<String> = []

        for fixture in presentation {
            let id = try string(fixture, key: "id")
            executed.insert(id)
            switch id {
            case "local-handoff-ready-reveal-place":
                try await executeGoldenLocalHandoff(fixture, id: id)
            case "computer-winning-move-settles-before-result",
                 "computer-draw-move-settles-before-result":
                try await executeGoldenComputerSettlement(fixture, id: id)
            default:
                XCTFail("Unhandled presentation fixture \(id)")
            }
        }

        for fixture in restoration {
            let id = try string(fixture, key: "id")
            executed.insert(id)
            switch id {
            case "held-piece-restores-without-redraw":
                try await executeGoldenHeldPieceRestore(fixture, id: id)
            case "computer-target-restores-and-commits-once":
                try await executeGoldenTargetRestore(fixture, id: id)
            case "stale-presentation-callback-after-rematch-is-ignored":
                try await executeGoldenStalePresentation(fixture, id: id)
            case "cancelled-ai-result-after-mode-replacement-is-ignored":
                try await executeGoldenCancelledAI(fixture, id: id)
            default:
                XCTFail("Unhandled restoration fixture \(id)")
            }
        }

        XCTAssertEqual(
            executed,
            [
                "local-handoff-ready-reveal-place",
                "computer-winning-move-settles-before-result",
                "computer-draw-move-settles-before-result",
                "held-piece-restores-without-redraw",
                "computer-target-restores-and-commits-once",
                "stale-presentation-callback-after-rematch-is-ignored",
                "cancelled-ai-result-after-mode-replacement-is-ignored",
            ]
        )
    }

    func testLocalHandoffRevealsOnceAndRejectsRapidDuplicatePlacement() async {
        let random = ScriptedDrawRandom([0])
        let coordinator = makeCoordinator(random: random)

        await coordinator.startPicPacLocal()
        XCTAssertEqual(coordinator.state.stage, .handoff)
        XCTAssertEqual(coordinator.state.picPac?.hiddenTotal, 10)

        await coordinator.readyForReveal()
        let revealID = coordinator.state.presentationID
        XCTAssertEqual(coordinator.state.stage, .revealing)
        XCTAssertEqual(coordinator.state.heldSymbol, .x)
        XCTAssertEqual(coordinator.state.picPac?.hiddenTotal, 9)
        XCTAssertEqual(random.callCount, 1)

        await coordinator.presentationStepFinished(revealID)
        XCTAssertEqual(coordinator.state.stage, .playing)
        await coordinator.place(at: 4)
        let afterFirst = coordinator.state
        await coordinator.place(at: 5)

        XCTAssertEqual(coordinator.state, afterFirst)
        XCTAssertEqual(coordinator.state.board.occupiedCount, 1)
        XCTAssertEqual(coordinator.state.stage, .handoff)
        XCTAssertEqual(random.callCount, 1)
    }

    func testRematchAlternatesStarterAndHomeRetainsSessionCounters() async {
        let coordinator = makeCoordinator()
        await coordinator.startClassic()
        XCTAssertEqual(coordinator.state.activePlayer, .one)

        await coordinator.rematch()
        XCTAssertEqual(coordinator.state.activePlayer, .two)
        await coordinator.goHome()
        XCTAssertEqual(coordinator.state.screen, .home)

        await coordinator.startPicPacLocal()
        XCTAssertEqual(coordinator.state.picPac?.starter, .two)
        await coordinator.rematch()
        XCTAssertEqual(coordinator.state.picPac?.starter, .one)
    }

    func testEarlyAIResultWaitsForRevealThenTargetsBeforeCommit() async {
        let random = ScriptedDrawRandom([0, 8])
        let coordinator = makeCoordinator(
            random: random,
            worker: FixedAIWorker(cell: 4)
        )
        await advanceToComputerReveal(coordinator)
        await drainTasks()

        XCTAssertEqual(coordinator.state.stage, .revealing)
        XCTAssertEqual(coordinator.state.board.occupiedCount, 1)
        let revealID = coordinator.state.presentationID
        await coordinator.presentationStepFinished(revealID)
        await drainTasks()

        XCTAssertEqual(coordinator.state.stage, .aiTargeting)
        XCTAssertEqual(coordinator.state.aiTargetCell, 4)
        XCTAssertEqual(coordinator.state.board[Cell(4)], nil)

        let targetID = coordinator.state.presentationID
        await coordinator.presentationStepFinished(targetID)
        XCTAssertEqual(coordinator.state.stage, .aiPlacing)
        XCTAssertEqual(coordinator.state.board[Cell(4)], .o)
        XCTAssertEqual(coordinator.state.displayedPlayer, .two)

        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .aiSettling)
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .turnStart)
        XCTAssertNil(coordinator.state.aiTargetCell)
        XCTAssertNil(coordinator.state.aiMoveSymbol)
        XCTAssertEqual(coordinator.state.displayedPlayer, .one)
    }

    func testLateAIResultUsesTruthfulThinkingStage() async {
        let worker = GateAIWorker()
        let coordinator = makeCoordinator(
            random: ScriptedDrawRandom([0, 8]),
            worker: worker
        )
        await advanceToComputerReveal(coordinator)
        await waitForWorkerCalls(worker, count: 1)

        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .aiThinking)
        await worker.succeedNext(with: 4)
        await drainTasks()

        XCTAssertEqual(coordinator.state.stage, .aiTargeting)
        XCTAssertEqual(coordinator.state.aiTargetCell, 4)
        XCTAssertEqual(coordinator.state.board.occupiedCount, 1)
    }

    func testThrowingAndIllegalWorkersUseFirstRowMajorLegalFallback() async {
        for worker in [AnyAIWorker(ThrowingAIWorker()), AnyAIWorker(FixedAIWorker(cell: 99))] {
            let coordinator = makeCoordinator(
                random: ScriptedDrawRandom([0, 8]),
                worker: worker
            )
            await advanceToComputerReveal(coordinator)
            await drainTasks()
            await coordinator.presentationStepFinished(coordinator.state.presentationID)
            await drainTasks()

            XCTAssertEqual(coordinator.state.stage, .aiTargeting)
            XCTAssertEqual(coordinator.state.aiTargetCell, 1, "cell 0 is occupied by the human")
        }
    }

    func testCancelledLateAIResultCannotMutateReplacementGame() async {
        let worker = GateAIWorker()
        let coordinator = makeCoordinator(
            random: ScriptedDrawRandom([0, 8]),
            worker: worker
        )
        await advanceToComputerReveal(coordinator)
        await waitForWorkerCalls(worker, count: 1)
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .aiThinking)

        await coordinator.startClassic()
        let replacement = coordinator.state
        await worker.succeedNext(with: 4)
        await drainTasks()
        XCTAssertEqual(coordinator.state, replacement)
    }

    func testTargetingRestoresWithoutDrawOrSearchAndCommitsExactlyOnce() async throws {
        let snapshot = try targetingSnapshot()
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let random = ScriptedDrawRandom([])
        let worker = GateAIWorker()
        let coordinator = makeCoordinator(random: random, worker: worker, store: store)

        await coordinator.restore()
        XCTAssertEqual(coordinator.state.stage, .aiTargeting)
        XCTAssertEqual(coordinator.state.presentationID, 23)
        XCTAssertEqual(coordinator.state.aiTargetCell, 4)
        XCTAssertEqual(coordinator.state.board.occupiedCount, 1)
        XCTAssertEqual(random.callCount, 0)
        let searchCount = await worker.numberOfCalls()
        XCTAssertEqual(searchCount, 0)

        await coordinator.presentationStepFinished(23)
        let committed = coordinator.state
        XCTAssertEqual(committed.stage, .aiPlacing)
        XCTAssertEqual(committed.board[Cell(4)], .x)
        XCTAssertEqual(committed.board.occupiedCount, 2)

        await coordinator.presentationStepFinished(23)
        XCTAssertEqual(coordinator.state, committed)
    }

    func testHeldPieceRestoresAndSceneResumeRestartsFullBeatWithoutRedraw() async throws {
        let snapshot = try localRevealSnapshot()
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let random = ScriptedDrawRandom([])
        let clock = RecordingClock()
        let coordinator = makeCoordinator(random: random, clock: clock, store: store)

        await coordinator.restore()
        await drainTasks()
        XCTAssertEqual(coordinator.state.stage, .revealing)
        XCTAssertEqual(coordinator.state.heldSymbol, .x)
        XCTAssertEqual(random.callCount, 0)
        let initialClockRequests = await clock.recordedMilliseconds()
        XCTAssertEqual(initialClockRequests, [650])

        let beforeInactive = coordinator.state
        await coordinator.setSceneActive(false)
        await coordinator.presentationStepFinished(beforeInactive.presentationID)
        XCTAssertEqual(coordinator.state, beforeInactive)

        await coordinator.setSceneActive(true)
        await drainTasks()
        XCTAssertEqual(coordinator.state, beforeInactive)
        XCTAssertEqual(random.callCount, 0)
        let resumedClockRequests = await clock.recordedMilliseconds()
        XCTAssertEqual(resumedClockRequests, [650, 650])
        await coordinator.setSceneActive(false)
    }

    func testSceneInactivityCancelsAndRelaunchesComputerSearchWithoutRedraw() async {
        let random = ScriptedDrawRandom([0, 8])
        let worker = CancellableCountingAIWorker()
        let coordinator = makeCoordinator(random: random, worker: worker)
        await advanceToComputerReveal(coordinator)
        await waitForWorkerCalls(worker, count: 1)
        let before = coordinator.state

        await coordinator.setSceneActive(false)
        await drainTasks()
        XCTAssertEqual(coordinator.state, before)
        XCTAssertEqual(random.callCount, 2)

        await coordinator.setSceneActive(true)
        await waitForWorkerCalls(worker, count: 2)
        XCTAssertEqual(coordinator.state, before)
        XCTAssertEqual(random.callCount, 2)
        await coordinator.setSceneActive(false)
    }

    func testSceneInactivityConsumesPendingFeedbackWithoutReplay() async {
        let coordinator = makeCoordinator(random: ScriptedDrawRandom([0]))
        await coordinator.startPicPacLocal()
        await coordinator.readyForReveal()
        XCTAssertEqual(coordinator.feedbackEvent?.kind, .reveal)

        await coordinator.setSceneActive(false)
        XCTAssertNil(coordinator.feedbackEvent)
        await coordinator.setSceneActive(true)
        XCTAssertNil(coordinator.feedbackEvent)
        await coordinator.setSceneActive(false)
    }

    func testSystemReducedMotionCombinesWithSavedPreferenceWithoutPersistingIt() async {
        let clock = RecordingClock()
        let store = InMemoryLocalStateStore()
        let coordinator = makeCoordinator(clock: clock, store: store)

        await coordinator.startPicPacAI(difficulty: .medium)
        await drainTasks()
        var recorded = await clock.recordedMilliseconds()
        XCTAssertEqual(recorded, [300])

        coordinator.setSystemReducedMotion(true)
        await drainTasks()
        XCTAssertTrue(coordinator.effectiveReducedMotion)
        recorded = await clock.recordedMilliseconds()
        let initialSettingsData = await store.settingsData
        XCTAssertEqual(recorded, [300, 160])
        XCTAssertNil(initialSettingsData)

        coordinator.setSystemReducedMotion(false)
        await drainTasks()
        XCTAssertFalse(coordinator.effectiveReducedMotion)
        recorded = await clock.recordedMilliseconds()
        XCTAssertEqual(recorded, [300, 160, 300])

        var settings = coordinator.settings
        settings.reducedMotion = true
        await coordinator.updateSettings(settings)
        await drainTasks()
        coordinator.setSystemReducedMotion(true)
        coordinator.setSystemReducedMotion(false)
        await drainTasks()
        XCTAssertTrue(coordinator.effectiveReducedMotion)
        recorded = await clock.recordedMilliseconds()
        let savedSettingsData = await store.settingsData
        XCTAssertEqual(recorded, [300, 160, 300, 160])
        XCTAssertNotNil(savedSettingsData)
        await coordinator.setSceneActive(false)
    }

    func testCoordinatorForwardsSelectedDifficultyToWorker() async {
        let worker = DifficultyRecordingAIWorker()
        let coordinator = makeCoordinator(
            random: ScriptedDrawRandom([0, 8]),
            worker: worker
        )

        await advanceToComputerReveal(coordinator, difficulty: .hard)
        for _ in 0..<100 {
            if !(await worker.recordedDifficulties()).isEmpty { break }
            await Task.yield()
        }

        let difficulties = await worker.recordedDifficulties()
        XCTAssertEqual(difficulties, [.hard])
        await coordinator.setSceneActive(false)
    }

    func testRestorationReadinessPreventsCommandsAndTracksLatestScenePhase() async throws {
        let snapshot = try localRevealSnapshot()
        let store = GatedSnapshotStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let clock = RecordingClock()
        let coordinator = GameCoordinator(
            drawRandom: ScriptedDrawRandom([]),
            clock: clock,
            aiWorker: FixedAIWorker(cell: 0),
            store: store,
            initialSceneIsActive: false,
            requiresRestorationBeforeCommands: true
        )

        let staleSceneTask = Task {
            await coordinator.restoreAndSetSceneActive(true)
        }
        await waitForSnapshotLoad(store)
        XCTAssertFalse(coordinator.isRestorationComplete)
        await coordinator.startClassic()
        XCTAssertEqual(coordinator.state.screen, .home)

        staleSceneTask.cancel()
        let currentSceneTask = Task {
            await coordinator.restoreAndSetSceneActive(false)
        }
        await currentSceneTask.value
        await store.releaseSnapshot()
        await staleSceneTask.value

        XCTAssertTrue(coordinator.isRestorationComplete)
        XCTAssertFalse(coordinator.isSceneActive)
        XCTAssertEqual(coordinator.state, snapshot.state)
        let writesBeforeActivation = await store.snapshotWriteCount()
        let sleepsBeforeActivation = await clock.recordedMilliseconds()
        XCTAssertEqual(writesBeforeActivation, 0)
        XCTAssertEqual(sleepsBeforeActivation, [])

        await coordinator.restoreAndSetSceneActive(true)
        await drainTasks()
        let sleepsAfterActivation = await clock.recordedMilliseconds()
        XCTAssertEqual(sleepsAfterActivation, [650])
        await coordinator.setSceneActive(false)
    }

    func testEveryRestorationBoundaryRoundTripsAndLockedStagesRejectInput() async throws {
        for (name, snapshot) in try restorationBoundarySnapshots() {
            try snapshot.validate()
            let store = InMemoryLocalStateStore(
                snapshotData: try PersistenceCodec.encode(snapshot)
            )
            let random = ScriptedDrawRandom([])
            let worker = CancellableCountingAIWorker()
            let coordinator = makeCoordinator(random: random, worker: worker, store: store)
            await coordinator.restore()
            await drainTasks()

            XCTAssertEqual(coordinator.state, snapshot.state, name)
            XCTAssertEqual(random.callCount, 0, name)
            let expectsSearch = snapshot.state.mode == .picPacAI
                && (snapshot.state.stage == .aiThinking
                    || (snapshot.state.stage == .revealing
                        && snapshot.state.activePlayer == .two))
            let searches = await worker.numberOfCalls()
            XCTAssertEqual(searches, expectsSearch ? 1 : 0, name)

            if snapshot.state.stage != .playing {
                let locked = coordinator.state
                for cell in 0...8 { await coordinator.place(at: cell) }
                XCTAssertEqual(coordinator.state, locked, "\(name) accepted locked input")
            }
            await coordinator.setSceneActive(false)
        }
    }

    func testCorruptSnapshotFailsClosedToHomeAndIsRemoved() async {
        let store = InMemoryLocalStateStore(snapshotData: Data("not json".utf8))
        let coordinator = makeCoordinator(store: store)

        await coordinator.restore()

        XCTAssertEqual(coordinator.state, GamePresentationState())
        XCTAssertEqual(coordinator.persistenceStatus, .invalidSnapshot)
        let snapshotData = await store.snapshotData
        XCTAssertNil(snapshotData)
    }

    func testCounterBoundariesRejectOverflowingRevisionAndWrapPresentationIdentity() async throws {
        let invalid = RestorationSnapshot(revision: Int64.max)
        XCTAssertThrowsError(try invalid.validate()) { error in
            XCTAssertEqual(error as? RestorationValidationError, .invalidEnvelope)
        }

        var stageSnapshot = try localRevealSnapshot()
        stageSnapshot.state.presentationID = UInt64.max
        stageSnapshot.presentationCounter = UInt64.max
        try stageSnapshot.validate()
        let stageStore = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(stageSnapshot)
        )
        let stageCoordinator = makeCoordinator(store: stageStore)
        await stageCoordinator.restore()
        await stageCoordinator.presentationStepFinished(UInt64.max)
        XCTAssertEqual(stageCoordinator.state.stage, .playing)
        XCTAssertEqual(stageCoordinator.state.presentationID, 1)

        let revisionSnapshot = RestorationSnapshot(revision: TurnToken.maximumRevision)
        let revisionStore = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(revisionSnapshot)
        )
        let revisionCoordinator = makeCoordinator(store: revisionStore)
        await revisionCoordinator.restore()
        await revisionCoordinator.startClassic()
        XCTAssertEqual(revisionCoordinator.state.classic?.revision, 1)
    }

    func testAITerminalRestorationRequiresExactCommittedMoveFields() throws {
        let computerBoard = try Board(symbols: [
            .x, .x, .x,
            nil, nil, nil,
            nil, nil, nil,
        ])
        let computerWin = GameOutcome.Win(
            player: .two,
            symbol: .x,
            lines: computerBoard.winningLines(for: .x)
        )
        let computerGame = try PicPacState(
            board: computerBoard,
            activePlayer: .two,
            remainingX: 2,
            remainingO: 5,
            phase: .terminal(outcome: .win(computerWin)),
            starter: .two,
            revision: 1
        )
        let missingComputerFields = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                picPac: computerGame,
                stage: .terminal,
                presentationID: 9
            ),
            revision: 1,
            presentationCounter: 9
        )
        XCTAssertThrowsError(try missingComputerFields.validate()) { error in
            XCTAssertEqual(error as? RestorationValidationError, .invalidAIFields)
        }

        let causalityBoard = try Board(symbols: [
            .x, .x, .x,
            .x, .o, nil,
            nil, nil, nil,
        ])
        let causalityWin = GameOutcome.Win(
            player: .two,
            symbol: .x,
            lines: causalityBoard.winningLines(for: .x)
        )
        let causalityGame = try PicPacState(
            board: causalityBoard,
            activePlayer: .two,
            remainingX: 1,
            remainingO: 4,
            phase: .terminal(outcome: .win(causalityWin)),
            starter: .two,
            revision: 1
        )
        let falseComputerTarget = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                picPac: causalityGame,
                stage: .terminal,
                presentationID: 10,
                aiTargetCell: 3,
                aiMoveSymbol: .x
            ),
            revision: 1,
            presentationCounter: 10
        )
        XCTAssertThrowsError(try falseComputerTarget.validate()) { error in
            XCTAssertEqual(error as? RestorationValidationError, .invalidAIFields)
        }

        let humanWin = GameOutcome.Win(
            player: .one,
            symbol: .x,
            lines: computerBoard.winningLines(for: .x)
        )
        let humanGame = try PicPacState(
            board: computerBoard,
            activePlayer: .one,
            remainingX: 2,
            remainingO: 5,
            phase: .terminal(outcome: .win(humanWin)),
            starter: .one,
            revision: 2
        )
        let extraneousHumanFields = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                picPac: humanGame,
                stage: .terminal,
                presentationID: 11,
                aiTargetCell: 0,
                aiMoveSymbol: .x
            ),
            revision: 2,
            presentationCounter: 11
        )
        XCTAssertThrowsError(try extraneousHumanFields.validate()) { error in
            XCTAssertEqual(error as? RestorationValidationError, .invalidAIFields)
        }
    }

    func testSettingsAreTypedAndPersistSeparatelyFromMatchSnapshot() async throws {
        let store = InMemoryLocalStateStore()
        let coordinator = makeCoordinator(store: store)
        let settings = AppSettings(
            soundEnabled: false,
            hapticsEnabled: false,
            reducedMotion: true,
            theme: .dark
        )
        await coordinator.updateSettings(settings)

        let restored = makeCoordinator(store: store)
        await restored.restore()
        XCTAssertEqual(restored.settings, settings)
        let snapshotData = await store.snapshotData
        let settingsData = await store.settingsData
        XCTAssertNil(snapshotData)
        XCTAssertNotNil(settingsData)
    }

    func testPrivateFilesAreAtomicReadableAndExcludedFromBackup() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("PicPacPoe-PresentationTests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = FileLocalStateStore(directoryURL: directory)
        let snapshotBytes = Data("snapshot".utf8)
        let settingsBytes = Data("settings".utf8)

        try await store.saveSnapshotData(snapshotBytes)
        try await store.saveSettingsData(settingsBytes)

        let loadedSnapshot = try await store.loadSnapshotData()
        let loadedSettings = try await store.loadSettingsData()
        XCTAssertEqual(loadedSnapshot, snapshotBytes)
        XCTAssertEqual(loadedSettings, settingsBytes)
        for name in ["current-match.json", "settings.json"] {
            let url = directory.appendingPathComponent(name)
            XCTAssertEqual(
                try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup,
                true,
                name
            )
        }
        XCTAssertEqual(
            try directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup,
            true
        )
    }

    func testDetachedWorkerBoundaryDoesNotRunSynchronousSearchOnMainThread() async throws {
        let worker = DetachedAIWorker { observation, _ in
            Thread.isMainThread ? -1 : observation.legalCells[0].index
        }
        let game = try accepted(PicPacRules.draw(PicPacRules.newGame(), symbol: .x))
        let observation = try AiObservation.from(state: game, agentPlayer: .two)

        let chosenCell = try await worker.chooseMove(for: observation, difficulty: .medium)
        XCTAssertEqual(chosenCell, 0)
    }

    func testDetachedWorkerPropagatesCancellationToCooperativeSearch() async throws {
        let probe = CancellationProbe()
        let worker = DetachedAIWorker { observation, _ in
            probe.markStarted()
            while !Task.isCancelled {
                Thread.sleep(forTimeInterval: 0.001)
            }
            probe.markCancelled()
            throw CancellationError()
        }
        let game = try accepted(PicPacRules.draw(PicPacRules.newGame(), symbol: .x))
        let observation = try AiObservation.from(state: game, agentPlayer: .two)
        let search = Task {
            try await worker.chooseMove(for: observation, difficulty: .medium)
        }

        for _ in 0..<1_000 where !probe.didStart {
            await Task.yield()
        }
        XCTAssertTrue(probe.didStart)
        search.cancel()
        do {
            _ = try await search.value
            XCTFail("Cancelled detached search returned a move")
        } catch is CancellationError {
            // Expected.
        }
        XCTAssertTrue(probe.didObserveCancellation)
    }

    func testCoordinatorTeardownCancelsOwnedClockAndSearchTasks() async {
        let clock = CancellableCountingClock()
        let worker = CancellableCountingAIWorker()
        var coordinator: GameCoordinator? = makeCoordinator(
            random: ScriptedDrawRandom([0, 8]),
            clock: clock,
            worker: worker
        )
        await advanceToComputerReveal(coordinator!)
        await waitForWorkerCalls(worker, count: 1)
        await waitForClockCalls(clock, minimum: 3)
        let clockCancellationsBeforeTeardown = await clock.numberOfCancellations()

        coordinator = nil
        await waitForWorkerCancellations(worker, count: 1)
        await waitForClockCancellations(
            clock,
            minimum: clockCancellationsBeforeTeardown + 1
        )

    }

    private func makeCoordinator(
        random: ScriptedDrawRandom = ScriptedDrawRandom([]),
        clock: any PresentationClock = RecordingClock(),
        worker: any AIWorker = FixedAIWorker(cell: 0),
        store: any LocalStateStore = InMemoryLocalStateStore()
    ) -> GameCoordinator {
        GameCoordinator(
            drawRandom: random,
            clock: clock,
            aiWorker: worker,
            store: store
        )
    }

    private func advanceToComputerReveal(
        _ coordinator: GameCoordinator,
        difficulty: Difficulty = .easy
    ) async {
        await coordinator.startPicPacAI(difficulty: difficulty)
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .revealing)
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .playing)
        await coordinator.place(at: 0)
        XCTAssertEqual(coordinator.state.stage, .turnStart)
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(coordinator.state.stage, .revealing)
        XCTAssertEqual(coordinator.state.activePlayer, .two)
    }

    private func waitForWorkerCalls(_ worker: GateAIWorker, count: Int) async {
        for _ in 0..<100 {
            if await worker.numberOfCalls() >= count { break }
            await Task.yield()
        }
        let actual = await worker.numberOfCalls()
        XCTAssertEqual(actual, count)
    }

    private func waitForWorkerCalls(
        _ worker: CancellableCountingAIWorker,
        count: Int
    ) async {
        for _ in 0..<100 {
            if await worker.numberOfCalls() >= count { break }
            await Task.yield()
        }
        let actual = await worker.numberOfCalls()
        XCTAssertEqual(actual, count)
    }

    private func waitForWorkerCancellations(
        _ worker: CancellableCountingAIWorker,
        count: Int
    ) async {
        for _ in 0..<100 {
            if await worker.numberOfCancellations() >= count { break }
            await Task.yield()
        }
        let actual = await worker.numberOfCancellations()
        XCTAssertEqual(actual, count)
    }

    private func waitForClockCalls(
        _ clock: CancellableCountingClock,
        minimum: Int
    ) async {
        for _ in 0..<100 {
            if await clock.numberOfCalls() >= minimum { break }
            await Task.yield()
        }
        let actual = await clock.numberOfCalls()
        XCTAssertGreaterThanOrEqual(actual, minimum)
    }

    private func waitForClockCancellations(
        _ clock: CancellableCountingClock,
        minimum: Int
    ) async {
        for _ in 0..<100 {
            if await clock.numberOfCancellations() >= minimum { break }
            await Task.yield()
        }
        let actual = await clock.numberOfCancellations()
        XCTAssertGreaterThanOrEqual(actual, minimum)
    }

    private func waitForSnapshotLoad(_ store: GatedSnapshotStore) async {
        for _ in 0..<100 {
            if await store.didBeginSnapshotLoad() { break }
            await Task.yield()
        }
        let didBegin = await store.didBeginSnapshotLoad()
        XCTAssertTrue(didBegin)
    }

    private func drainTasks() async {
        for _ in 0..<30 { await Task.yield() }
    }

    private func targetingSnapshot() throws -> RestorationSnapshot {
        let board = try Board.empty.placing(.o, at: Cell(0))
        let game = try PicPacState(
            board: board,
            activePlayer: .two,
            remainingX: 4,
            remainingO: 4,
            phase: .awaitingPlacement(held: .x, token: TurnToken(50)),
            starter: .one,
            revision: 3
        )
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                difficulty: .medium,
                picPac: game,
                stage: .aiTargeting,
                presentationID: 23,
                aiTargetCell: 4,
                aiMoveSymbol: .x
            ),
            revision: 3,
            nextStarter: .one,
            presentationCounter: 23
        )
        try snapshot.validate()
        return snapshot
    }

    private func localRevealSnapshot() throws -> RestorationSnapshot {
        let game = try accepted(
            PicPacRules.draw(
                PicPacRules.newGame(starter: .one, revision: 1),
                symbol: .x
            )
        )
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacLocal,
                picPac: game,
                stage: .revealing,
                presentationID: 7
            ),
            revision: 1,
            nextStarter: .one,
            presentationCounter: 7
        )
        try snapshot.validate()
        return snapshot
    }

    private func restorationBoundarySnapshots() throws -> [(String, RestorationSnapshot)] {
        var snapshots: [(String, RestorationSnapshot)] = []

        for screen in [AppScreen.home, .howTo, .settings, .aiLab] {
            snapshots.append((
                "outside-\(screen.rawValue)",
                RestorationSnapshot(
                    state: GamePresentationState(screen: screen),
                    revision: 0,
                    presentationCounter: 0
                )
            ))
        }

        let classicRevision: Int64 = 20
        let classicPlaying = ClassicRules.newGame(starter: .two, revision: classicRevision)
        snapshots.append((
            "classic-playing",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .classicLocal,
                    classic: classicPlaying,
                    stage: .playing,
                    presentationID: 70
                ),
                revision: classicRevision,
                nextStarter: .two,
                presentationCounter: 70
            )
        ))
        var classicTerminal = ClassicRules.newGame(revision: classicRevision)
        for cell in [0, 3, 1, 4, 2] {
            classicTerminal = try accepted(
                ClassicRules.place(
                    classicTerminal,
                    at: Cell(cell),
                    token: classicTerminal.turnToken
                )
            )
        }
        snapshots.append((
            "classic-terminal",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .classicLocal,
                    classic: classicTerminal,
                    stage: .terminal,
                    presentationID: 71
                ),
                revision: classicRevision,
                presentationCounter: 71
            )
        ))

        let localRevision: Int64 = 21
        let localDraw = PicPacRules.newGame(revision: localRevision)
        snapshots.append((
            "local-handoff",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .picPacLocal,
                    picPac: localDraw,
                    stage: .handoff,
                    presentationID: 72
                ),
                revision: localRevision,
                presentationCounter: 72
            )
        ))
        let localHeld = try accepted(PicPacRules.draw(localDraw, symbol: .x))
        for (name, stage, identifier) in [
            ("local-revealing", TurnStage.revealing, UInt64(73)),
            ("local-playing", TurnStage.playing, UInt64(74)),
        ] {
            snapshots.append((
                name,
                RestorationSnapshot(
                    state: GamePresentationState(
                        screen: .game,
                        mode: .picPacLocal,
                        picPac: localHeld,
                        stage: stage,
                        presentationID: identifier
                    ),
                    revision: localRevision,
                    presentationCounter: identifier
                )
            ))
        }
        let localTerminalBoard = try Board(symbols: [
            .x, .x, .x, .o, .o, nil, nil, nil, nil,
        ])
        let localWin = GameOutcome.Win(
            player: .one,
            symbol: .x,
            lines: localTerminalBoard.winningLines(for: .x)
        )
        let localTerminal = try PicPacState(
            board: localTerminalBoard,
            activePlayer: .one,
            remainingX: 2,
            remainingO: 3,
            phase: .terminal(outcome: .win(localWin)),
            starter: .one,
            revision: localRevision
        )
        snapshots.append((
            "local-terminal",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .picPacLocal,
                    picPac: localTerminal,
                    stage: .terminal,
                    presentationID: 75
                ),
                revision: localRevision,
                presentationCounter: 75
            )
        ))

        let aiRevision: Int64 = 22
        let aiDraw = PicPacRules.newGame(revision: aiRevision)
        snapshots.append((
            "ai-turn-start",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .picPacAI,
                    difficulty: .hard,
                    picPac: aiDraw,
                    stage: .turnStart,
                    presentationID: 76
                ),
                revision: aiRevision,
                presentationCounter: 76
            )
        ))
        let humanHeld = try accepted(PicPacRules.draw(aiDraw, symbol: .x))
        for (name, stage, identifier) in [
            ("ai-human-revealing", TurnStage.revealing, UInt64(77)),
            ("ai-human-playing", TurnStage.playing, UInt64(78)),
        ] {
            snapshots.append((
                name,
                RestorationSnapshot(
                    state: GamePresentationState(
                        screen: .game,
                        mode: .picPacAI,
                        difficulty: .hard,
                        picPac: humanHeld,
                        stage: stage,
                        presentationID: identifier
                    ),
                    revision: aiRevision,
                    presentationCounter: identifier
                )
            ))
        }

        let computerBoard = try Board.empty.placing(.o, at: Cell(0))
        let computerHeld = try PicPacState(
            board: computerBoard,
            activePlayer: .two,
            remainingX: 4,
            remainingO: 4,
            phase: .awaitingPlacement(
                held: .x,
                token: TurnToken(aiRevision * 16 + 2)
            ),
            starter: .one,
            revision: aiRevision
        )
        for (name, stage, identifier) in [
            ("ai-computer-revealing", TurnStage.revealing, UInt64(79)),
            ("ai-thinking", TurnStage.aiThinking, UInt64(80)),
        ] {
            snapshots.append((
                name,
                RestorationSnapshot(
                    state: GamePresentationState(
                        screen: .game,
                        mode: .picPacAI,
                        difficulty: .hard,
                        picPac: computerHeld,
                        stage: stage,
                        presentationID: identifier
                    ),
                    revision: aiRevision,
                    presentationCounter: identifier
                )
            ))
        }
        snapshots.append((
            "ai-targeting",
            RestorationSnapshot(
                state: GamePresentationState(
                    screen: .game,
                    mode: .picPacAI,
                    difficulty: .hard,
                    picPac: computerHeld,
                    stage: .aiTargeting,
                    presentationID: 81,
                    aiTargetCell: 4,
                    aiMoveSymbol: .x
                ),
                revision: aiRevision,
                presentationCounter: 81
            )
        ))
        let computerPlaced = try accepted(
            PicPacRules.place(
                computerHeld,
                at: Cell(4),
                token: try XCTUnwrap(computerHeld.phase.turnToken)
            )
        )
        for (name, stage, identifier) in [
            ("ai-placing", TurnStage.aiPlacing, UInt64(82)),
            ("ai-settling", TurnStage.aiSettling, UInt64(83)),
        ] {
            snapshots.append((
                name,
                RestorationSnapshot(
                    state: GamePresentationState(
                        screen: .game,
                        mode: .picPacAI,
                        difficulty: .hard,
                        picPac: computerPlaced,
                        stage: stage,
                        presentationID: identifier,
                        aiTargetCell: 4,
                        aiMoveSymbol: .x
                    ),
                    revision: aiRevision,
                    presentationCounter: identifier
                )
            ))
        }

        let aiTerminalBoard = try Board(symbols: [
            .x, .x, .x, nil, nil, nil, nil, nil, nil,
        ])
        let aiWin = GameOutcome.Win(
            player: .two,
            symbol: .x,
            lines: aiTerminalBoard.winningLines(for: .x)
        )
        let aiTerminal = try PicPacState(
            board: aiTerminalBoard,
            activePlayer: .two,
            remainingX: 2,
            remainingO: 5,
            phase: .terminal(outcome: .win(aiWin)),
            starter: .two,
            revision: aiRevision
        )
        for (name, stage, identifier) in [
            ("ai-terminal-placing", TurnStage.aiPlacing, UInt64(84)),
            ("ai-terminal-settling", TurnStage.aiSettling, UInt64(85)),
            ("ai-terminal", TurnStage.terminal, UInt64(86)),
        ] {
            snapshots.append((
                name,
                RestorationSnapshot(
                    state: GamePresentationState(
                        screen: .game,
                        mode: .picPacAI,
                        difficulty: .hard,
                        picPac: aiTerminal,
                        stage: stage,
                        presentationID: identifier,
                        aiTargetCell: 2,
                        aiMoveSymbol: .x
                    ),
                    revision: aiRevision,
                    presentationCounter: identifier
                )
            ))
        }

        return snapshots
    }

    private func executeGoldenLocalHandoff(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let initial = try object(fixture, key: "initial")
        let steps = try objects(fixture, key: "steps")
        let ready = steps[0]
        let random = ScriptedDrawRandom([try int(ready, key: "scriptedDrawResult")])
        let coordinator = makeCoordinator(random: random)

        await coordinator.startPicPacLocal()
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(initial, key: "stage")), id)
        XCTAssertEqual(coordinator.state.activePlayer, try player(string(initial, key: "activePlayer")), id)
        XCTAssertEqual(random.callCount, try int(initial, key: "randomCalls"), id)

        await coordinator.readyForReveal()
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(ready, key: "expectedStage")), id)
        XCTAssertEqual(coordinator.state.heldSymbol, try symbol(string(ready, key: "expectedHeldSymbol")), id)
        XCTAssertEqual(coordinator.state.picPac?.remainingX, try int(ready, key: "expectedRemainingX"), id)
        XCTAssertEqual(coordinator.state.picPac?.remainingO, try int(ready, key: "expectedRemainingO"), id)
        XCTAssertEqual(random.callCount, try int(ready, key: "expectedRandomCalls"), id)

        let revealFinished = steps[1]
        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        XCTAssertEqual(
            coordinator.state.stage,
            try turnStage(string(revealFinished, key: "expectedStage")),
            id
        )
        XCTAssertEqual(
            coordinator.state.board.legalCells.map(\.index),
            try ints(revealFinished, key: "expectedInputEnabledCells"),
            id
        )

        let placement = steps[2]
        await coordinator.place(at: try int(placement, key: "cell"))
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(placement, key: "expectedStage")), id)
        XCTAssertEqual(
            coordinator.state.activePlayer,
            try player(string(placement, key: "expectedActivePlayer")),
            id
        )
        XCTAssertEqual(coordinator.state.board, try board(placement, key: "expectedBoard"), id)
        XCTAssertEqual(random.callCount, try int(placement, key: "expectedRandomCalls"), id)
    }

    private func executeGoldenComputerSettlement(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let initial = try object(fixture, key: "initial")
        let snapshot = try targetingSnapshot(from: initial)
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let coordinator = makeCoordinator(store: store)
        await coordinator.restore()

        XCTAssertEqual(coordinator.state.stage, try turnStage(string(initial, key: "stage")), id)
        XCTAssertEqual(coordinator.state.displayedPlayer, try player(string(initial, key: "displayedPlayer")), id)

        for step in try objects(fixture, key: "steps") {
            let callbackID = step["presentationId"] == nil
                ? coordinator.state.presentationID
                : UInt64(try int(step, key: "presentationId"))
            await coordinator.presentationStepFinished(callbackID)
            XCTAssertEqual(coordinator.state.stage, try turnStage(string(step, key: "expectedStage")), id)
            if step["expectedBoard"] != nil {
                XCTAssertEqual(coordinator.state.board, try board(step, key: "expectedBoard"), id)
            }
            if let expectedPlayer = step["expectedDisplayedPlayer"] as? String {
                XCTAssertEqual(coordinator.state.displayedPlayer, try player(expectedPlayer), id)
            }
            if let expectedOutcome = step["expectedOutcome"] as? GoldenObject {
                try assertOutcome(coordinator.state.outcome, expected: expectedOutcome, id: id)
            }
            XCTAssertEqual(
                coordinator.state.stage == .terminal,
                try bool(step, key: "resultVisible"),
                id
            )
            if (step["mustNotStartAnotherDraw"] as? Bool) == true {
                let terminal = coordinator.state
                await coordinator.presentationStepFinished(terminal.presentationID)
                XCTAssertEqual(coordinator.state, terminal, id)
            }
        }
    }

    private func executeGoldenHeldPieceRestore(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let rawSnapshot = try object(fixture, key: "snapshot")
        let expected = try object(fixture, key: "expectedAfterRestore")
        let snapshot = try restorationSnapshot(from: rawSnapshot)
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let random = ScriptedDrawRandom([])
        let coordinator = makeCoordinator(random: random, store: store)
        await coordinator.restore()

        XCTAssertEqual(coordinator.state.stage, try turnStage(string(expected, key: "stage")), id)
        XCTAssertEqual(coordinator.state.presentationID, UInt64(try int(expected, key: "presentationId")), id)
        XCTAssertEqual(coordinator.state.heldSymbol, try symbol(string(expected, key: "heldSymbol")), id)
        XCTAssertEqual(coordinator.state.picPac?.remainingX, try int(expected, key: "remainingX"), id)
        XCTAssertEqual(coordinator.state.picPac?.remainingO, try int(expected, key: "remainingO"), id)
        XCTAssertEqual(random.callCount, try int(expected, key: "randomCalls"), id)
    }

    private func executeGoldenTargetRestore(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let rawSnapshot = try object(fixture, key: "snapshot")
        let expected = try object(fixture, key: "expectedAfterRestore")
        let snapshot = try restorationSnapshot(from: rawSnapshot)
        let worker = GateAIWorker()
        let random = ScriptedDrawRandom([])
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let coordinator = makeCoordinator(random: random, worker: worker, store: store)
        await coordinator.restore()

        XCTAssertEqual(coordinator.state.stage, try turnStage(string(expected, key: "stage")), id)
        XCTAssertEqual(coordinator.state.presentationID, UInt64(try int(expected, key: "presentationId")), id)
        XCTAssertEqual(coordinator.state.aiTargetCell, try int(expected, key: "aiTargetCell"), id)
        XCTAssertEqual(coordinator.state.aiMoveSymbol, try symbol(string(expected, key: "aiMoveSymbol")), id)
        XCTAssertEqual(coordinator.state.board.occupiedCount, try int(expected, key: "boardOccupiedCount"), id)
        XCTAssertEqual(random.callCount, try int(expected, key: "randomCalls"), id)
        let searchCount = await worker.numberOfCalls()
        XCTAssertEqual(searchCount, try int(expected, key: "newSearches"), id)

        await coordinator.presentationStepFinished(coordinator.state.presentationID)
        let after = try object(fixture, key: "expectedAfterCurrentCallback")
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(after, key: "stage")), id)
        XCTAssertEqual(coordinator.state.board, try board(after, key: "board"), id)
        XCTAssertEqual(coordinator.state.board.occupiedCount, try int(after, key: "boardOccupiedCount"), id)
    }

    private func executeGoldenStalePresentation(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let before = try object(fixture, key: "beforeReplacement")
        let presentationID = UInt64(try int(before, key: "presentationId"))
        let board = try Board.empty.placing(.o, at: Cell(0))
        let game = try PicPacState(
            board: board,
            activePlayer: .two,
            remainingX: 4,
            remainingO: 4,
            phase: .awaitingPlacement(held: .x, token: TurnToken(18)),
            starter: .one,
            revision: 1
        )
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                difficulty: .easy,
                picPac: game,
                stage: try turnStage(string(before, key: "stage")),
                presentationID: presentationID,
                aiTargetCell: 4,
                aiMoveSymbol: .x
            ),
            revision: 1,
            nextStarter: .one,
            presentationCounter: presentationID
        )
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let coordinator = makeCoordinator(store: store)
        await coordinator.restore()
        await coordinator.rematch()
        let replacement = try object(fixture, key: "replacement")
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(replacement, key: "expectedStage")), id)
        XCTAssertEqual(
            coordinator.state.board.occupiedCount,
            try int(replacement, key: "expectedBoardOccupiedCount"),
            id
        )
        XCTAssertGreaterThan(
            coordinator.state.presentationID,
            UInt64(try int(replacement, key: "expectedPresentationIdGreaterThan")),
            id
        )
        let replaced = coordinator.state
        let late = try object(fixture, key: "lateEvent")
        await coordinator.presentationStepFinished(UInt64(try int(late, key: "presentationId")))
        XCTAssertEqual(coordinator.state, replaced, id)
    }

    private func executeGoldenCancelledAI(
        _ fixture: GoldenObject,
        id: String
    ) async throws {
        let before = try object(fixture, key: "beforeReplacement")
        let revision = Int64(try int(before, key: "capturedRevision"))
        let token = Int64(try int(before, key: "capturedTurnToken"))
        let board = try Board.empty.placing(.o, at: Cell(0))
        let game = try PicPacState(
            board: board,
            activePlayer: .two,
            remainingX: 4,
            remainingO: 4,
            phase: .awaitingPlacement(held: .x, token: TurnToken(token)),
            starter: .one,
            revision: revision
        )
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                difficulty: .easy,
                picPac: game,
                stage: try turnStage(string(before, key: "stage")),
                presentationID: 30
            ),
            revision: revision,
            nextStarter: .one,
            presentationCounter: 30
        )
        let worker = GateAIWorker()
        let store = InMemoryLocalStateStore(
            snapshotData: try PersistenceCodec.encode(snapshot)
        )
        let coordinator = makeCoordinator(worker: worker, store: store)
        await coordinator.restore()
        await waitForWorkerCalls(worker, count: 1)
        await coordinator.startClassic()
        let replacement = try object(fixture, key: "replacement")
        XCTAssertEqual(coordinator.state.mode, try gameMode(string(replacement, key: "mode")), id)
        XCTAssertEqual(coordinator.state.stage, try turnStage(string(replacement, key: "stage")), id)
        XCTAssertEqual(
            coordinator.state.board.occupiedCount,
            try int(replacement, key: "boardOccupiedCount"),
            id
        )
        let replaced = coordinator.state
        let late = try object(fixture, key: "lateEvent")
        await worker.succeedNext(with: try int(late, key: "cell"))
        await drainTasks()
        XCTAssertEqual(coordinator.state, replaced, id)
    }

    private func goldenRoot() throws -> GoldenObject {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "golden-fixtures", withExtension: "json")
        )
        return try XCTUnwrap(
            JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? GoldenObject
        )
    }

    private func targetingSnapshot(from value: GoldenObject) throws -> RestorationSnapshot {
        let board = try board(value, key: "board")
        let revision = (Int64(try int(value, key: "turnToken"))
            - Int64(board.occupiedCount) - 1) / 16
        let activePlayer = try player(string(value, key: "activePlayer"))
        let starter = board.occupiedCount.isMultiple(of: 2)
            ? activePlayer
            : activePlayer.other
        let game = try PicPacState(
            board: board,
            activePlayer: activePlayer,
            remainingX: try int(value, key: "remainingX"),
            remainingO: try int(value, key: "remainingO"),
            phase: .awaitingPlacement(
                held: try symbol(string(value, key: "heldSymbol")),
                token: TurnToken(Int64(try int(value, key: "turnToken")))
            ),
            starter: starter,
            revision: revision
        )
        let presentationID = UInt64(try int(value, key: "presentationId"))
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: .picPacAI,
                difficulty: .medium,
                picPac: game,
                stage: try turnStage(string(value, key: "stage")),
                presentationID: presentationID,
                aiTargetCell: try int(value, key: "aiTargetCell"),
                aiMoveSymbol: try symbol(string(value, key: "aiMoveSymbol"))
            ),
            revision: revision,
            nextStarter: .one,
            presentationCounter: presentationID
        )
        try snapshot.validate()
        return snapshot
    }

    private func restorationSnapshot(from value: GoldenObject) throws -> RestorationSnapshot {
        let board = try board(value, key: "board")
        let revision = Int64(try int(value, key: "revision"))
        let game = try PicPacState(
            board: board,
            activePlayer: try player(string(value, key: "activePlayer")),
            remainingX: try int(value, key: "remainingX"),
            remainingO: try int(value, key: "remainingO"),
            phase: .awaitingPlacement(
                held: try symbol(string(value, key: "heldSymbol")),
                token: TurnToken(Int64(try int(value, key: "turnToken")))
            ),
            starter: try player(string(value, key: "starter")),
            revision: revision
        )
        let presentationID = UInt64(try int(value, key: "presentationId"))
        let mode = try gameMode(string(value, key: "mode"))
        let difficulty = try difficulty(value["difficulty"] as? String ?? "MEDIUM")
        let snapshot = RestorationSnapshot(
            state: GamePresentationState(
                screen: .game,
                mode: mode,
                difficulty: difficulty,
                picPac: game,
                stage: try turnStage(string(value, key: "stage")),
                presentationID: presentationID,
                aiTargetCell: value["aiTargetCell"] as? Int,
                aiMoveSymbol: try (value["aiMoveSymbol"] as? String).map(symbol)
            ),
            revision: revision,
            nextStarter: game.starter,
            presentationCounter: presentationID
        )
        try snapshot.validate()
        return snapshot
    }

    private func assertOutcome(
        _ actual: GameOutcome?,
        expected: GoldenObject,
        id: String
    ) throws {
        switch try string(expected, key: "kind") {
        case "DRAW":
            XCTAssertEqual(actual, .draw, id)
        case "WIN":
            guard case let .win(win) = actual else {
                return XCTFail("\(id): expected win")
            }
            XCTAssertEqual(win.player, try player(string(expected, key: "actor")), id)
            XCTAssertEqual(win.symbol, try symbol(string(expected, key: "symbol")), id)
        default:
            throw GoldenFixtureFailure.invalidValue("outcome kind")
        }
    }

    private func object(_ value: GoldenObject, key: String) throws -> GoldenObject {
        guard let result = value[key] as? GoldenObject else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result
    }

    private func objects(_ value: GoldenObject, key: String) throws -> [GoldenObject] {
        guard let result = value[key] as? [GoldenObject] else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result
    }

    private func string(_ value: GoldenObject, key: String) throws -> String {
        guard let result = value[key] as? String else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result
    }

    private func int(_ value: GoldenObject, key: String) throws -> Int {
        guard let result = value[key] as? NSNumber else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result.intValue
    }

    private func bool(_ value: GoldenObject, key: String) throws -> Bool {
        guard let result = value[key] as? Bool else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result
    }

    private func ints(_ value: GoldenObject, key: String) throws -> [Int] {
        guard let result = value[key] as? [NSNumber] else {
            throw GoldenFixtureFailure.missing(key)
        }
        return result.map(\.intValue)
    }

    private func board(_ value: GoldenObject, key: String) throws -> Board {
        guard let cells = value[key] as? [Any], cells.count == 9 else {
            throw GoldenFixtureFailure.missing(key)
        }
        return try Board(symbols: cells.map { raw in
            if raw is NSNull { return nil }
            guard let raw = raw as? String else { return nil }
            return try? symbol(raw)
        })
    }

    private func symbol(_ raw: String) throws -> Symbol {
        guard let value = Symbol(rawValue: raw) else {
            throw GoldenFixtureFailure.invalidValue("symbol \(raw)")
        }
        return value
    }

    private func player(_ raw: String) throws -> Player {
        switch raw {
        case "ONE": .one
        case "TWO": .two
        default: throw GoldenFixtureFailure.invalidValue("player \(raw)")
        }
    }

    private func gameMode(_ raw: String) throws -> GameMode {
        switch raw {
        case "CLASSIC_LOCAL": .classicLocal
        case "PIC_PAC_LOCAL": .picPacLocal
        case "PIC_PAC_AI": .picPacAI
        default: throw GoldenFixtureFailure.invalidValue("mode \(raw)")
        }
    }

    private func difficulty(_ raw: String) throws -> Difficulty {
        switch raw {
        case "EASY": .easy
        case "MEDIUM": .medium
        case "HARD": .hard
        case "MCTS": .mcts
        case "RL": .qLearning
        default: throw GoldenFixtureFailure.invalidValue("difficulty \(raw)")
        }
    }

    private func turnStage(_ raw: String) throws -> TurnStage {
        switch raw {
        case "HANDOFF": .handoff
        case "TURN_START": .turnStart
        case "REVEALING": .revealing
        case "PLAYING": .playing
        case "AI_THINKING": .aiThinking
        case "AI_TARGETING": .aiTargeting
        case "AI_PLACING": .aiPlacing
        case "AI_SETTLING": .aiSettling
        case "TERMINAL": .terminal
        default: throw GoldenFixtureFailure.invalidValue("stage \(raw)")
        }
    }

    private func accepted<State>(_ result: TransitionResult<State>) throws -> State {
        switch result {
        case let .accepted(state, _): state
        case let .rejected(reason): throw TestFailure.rejected(reason)
        }
    }
}

@MainActor
private final class ScriptedDrawRandom: DrawRandomSource {
    private var values: [Int]
    private(set) var callCount = 0

    init(_ values: [Int]) {
        self.values = values
    }

    func nextInt(upperBound: Int) -> Int {
        callCount += 1
        guard !values.isEmpty else { return -1 }
        return values.removeFirst()
    }
}

private actor RecordingClock: PresentationClock {
    private var milliseconds: [Int] = []

    func sleep(milliseconds: Int) async throws {
        self.milliseconds.append(milliseconds)
        try await Task.sleep(for: .seconds(600))
    }

    func recordedMilliseconds() -> [Int] { milliseconds }
}

private struct FixedAIWorker: AIWorker {
    let cell: Int
    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int { cell }
}

private struct ThrowingAIWorker: AIWorker {
    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        throw TestFailure.workerFailed
    }
}

private struct AnyAIWorker: AIWorker {
    private let choose: @Sendable (AiObservation, Difficulty) async throws -> Int

    init<Worker: AIWorker>(_ worker: Worker) {
        choose = { observation, difficulty in
            try await worker.chooseMove(for: observation, difficulty: difficulty)
        }
    }

    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        try await choose(observation, difficulty)
    }
}

private actor GateAIWorker: AIWorker {
    private var callCount = 0
    private var waiters: [CheckedContinuation<Int, any Error>] = []

    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        callCount += 1
        return try await withCheckedThrowingContinuation { continuation in
            waiters.append(continuation)
        }
    }

    func numberOfCalls() -> Int { callCount }

    func succeedNext(with cell: Int) {
        guard !waiters.isEmpty else { return }
        waiters.removeFirst().resume(returning: cell)
    }
}

private actor CancellableCountingAIWorker: AIWorker {
    private var callCount = 0
    private var cancellationCount = 0

    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        callCount += 1
        do {
            try await Task.sleep(for: .seconds(600))
            return observation.legalCells[0].index
        } catch is CancellationError {
            cancellationCount += 1
            throw CancellationError()
        }
    }

    func numberOfCalls() -> Int { callCount }
    func numberOfCancellations() -> Int { cancellationCount }
}

private actor DifficultyRecordingAIWorker: AIWorker {
    private var difficulties: [Difficulty] = []

    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        difficulties.append(difficulty)
        return observation.legalCells[0].index
    }

    func recordedDifficulties() -> [Difficulty] { difficulties }
}

private actor CancellableCountingClock: PresentationClock {
    private var callCount = 0
    private var cancellationCount = 0

    func sleep(milliseconds: Int) async throws {
        callCount += 1
        do {
            try await Task.sleep(for: .seconds(600))
        } catch is CancellationError {
            cancellationCount += 1
            throw CancellationError()
        }
    }

    func numberOfCalls() -> Int { callCount }
    func numberOfCancellations() -> Int { cancellationCount }
}

private actor GatedSnapshotStore: LocalStateStore {
    private let snapshotData: Data?
    private var loadBegan = false
    private var snapshotContinuation: CheckedContinuation<Void, Never>?
    private var writes = 0

    init(snapshotData: Data?) {
        self.snapshotData = snapshotData
    }

    func loadSnapshotData() async -> Data? {
        loadBegan = true
        await withCheckedContinuation { continuation in
            snapshotContinuation = continuation
        }
        return snapshotData
    }

    func saveSnapshotData(_ data: Data) {
        writes += 1
    }

    func clearSnapshot() {}
    func loadSettingsData() -> Data? { nil }
    func saveSettingsData(_ data: Data) {}

    func didBeginSnapshotLoad() -> Bool { loadBegan }
    func snapshotWriteCount() -> Int { writes }

    func releaseSnapshot() {
        snapshotContinuation?.resume()
        snapshotContinuation = nil
    }
}

private final class CancellationProbe: @unchecked Sendable {
    private let lock = NSLock()
    private var started = false
    private var cancelled = false

    var didStart: Bool {
        lock.withLock { started }
    }

    var didObserveCancellation: Bool {
        lock.withLock { cancelled }
    }

    func markStarted() {
        lock.withLock { started = true }
    }

    func markCancelled() {
        lock.withLock { cancelled = true }
    }
}

private enum TestFailure: Error {
    case rejected(RejectionReason)
    case workerFailed
}

private enum GoldenFixtureFailure: Error {
    case missing(String)
    case invalidValue(String)
}
