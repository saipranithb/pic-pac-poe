import Foundation
import Observation
import PicPacCore

public enum LocalPersistenceStatus: Equatable, Sendable {
    case healthy
    case loadFailed
    case invalidSettings
    case invalidSnapshot
    case saveFailed
}

private struct AIRequest: Equatable, Sendable {
    let revision: Int64
    let token: TurnToken
}

private struct PendingAI: Equatable, Sendable {
    let request: AIRequest
    let cell: Cell
}

/// The single main-actor authority for domain state, presentation stages, local
/// restoration, and the cancellable AI boundary. Views render `state` and send
/// typed intentions; they never mutate a game value directly.
@MainActor
@Observable
public final class GameCoordinator {
    public private(set) var state: GamePresentationState
    public private(set) var settings: AppSettings
    public private(set) var feedbackEvent: FeedbackEvent?
    public private(set) var persistenceStatus: LocalPersistenceStatus
    public private(set) var isRestorationComplete: Bool

    public private(set) var titleEntranceConsumed: Bool
    public private(set) var isSceneActive: Bool
    public private(set) var systemReducedMotion: Bool

    private let drawRandom: any DrawRandomSource
    private let clock: any PresentationClock
    private let aiWorker: any AIWorker
    private let store: any LocalStateStore

    private var revision: Int64
    private var nextStarter: Player
    private var presentationCounter: UInt64
    private var feedbackCounter: UInt64
    private var didRestore = false

    private var presentationTask: Task<Void, Never>?
    private var presentationGeneration: UInt64 = 0
    private var aiTask: Task<Void, Never>?
    private var aiGeneration: UInt64 = 0
    private var pendingAI: PendingAI?
    private var revealAcknowledged = false

    public init(
        drawRandom: any DrawRandomSource = SystemDrawRandomSource(),
        clock: any PresentationClock = SystemPresentationClock(),
        aiWorker: any AIWorker = UnavailableAIWorker(),
        store: any LocalStateStore = InMemoryLocalStateStore(),
        initialSceneIsActive: Bool = true,
        requiresRestorationBeforeCommands: Bool = false
    ) {
        self.drawRandom = drawRandom
        self.clock = clock
        self.aiWorker = aiWorker
        self.store = store
        state = GamePresentationState()
        settings = AppSettings()
        feedbackEvent = nil
        persistenceStatus = .healthy
        isRestorationComplete = !requiresRestorationBeforeCommands
        titleEntranceConsumed = false
        isSceneActive = initialSceneIsActive
        systemReducedMotion = false
        revision = 0
        nextStarter = .one
        presentationCounter = 0
        feedbackCounter = 0
    }

    isolated deinit {
        presentationTask?.cancel()
        aiTask?.cancel()
    }

    /// Loads the two private local payloads at most once. Invalid restoration
    /// returns to Home and is deleted rather than partially replayed.
    public func restore() async {
        guard !didRestore else { return }
        didRestore = true
        isRestorationComplete = false
        defer { isRestorationComplete = true }

        do {
            if let data = try await store.loadSettingsData() {
                do {
                    settings = try PersistenceCodec.decode(AppSettings.self, from: data)
                } catch {
                    settings = AppSettings()
                    persistenceStatus = .invalidSettings
                }
            }
        } catch {
            persistenceStatus = .loadFailed
        }

        do {
            guard let data = try await store.loadSnapshotData() else { return }
            do {
                let snapshot = try PersistenceCodec.decode(RestorationSnapshot.self, from: data)
                try snapshot.validate()
                state = snapshot.state
                revision = snapshot.revision
                nextStarter = snapshot.nextStarter
                presentationCounter = snapshot.presentationCounter
                titleEntranceConsumed = snapshot.titleEntranceConsumed
                resumeCurrentWork()
            } catch {
                resetToFreshHome()
                persistenceStatus = .invalidSnapshot
                try? await store.clearSnapshot()
            }
        } catch {
            persistenceStatus = .loadFailed
        }
    }

    /// Connects restoration to a scene-phase observation. SwiftUI cancels the
    /// previous `.task(id:)` when the phase changes; the cancellation check
    /// prevents that older task from applying its captured phase after a slow,
    /// cancellation-unaware store load completes.
    public func restoreAndSetSceneActive(_ active: Bool) async {
        await restore()
        guard !Task.isCancelled else { return }
        await setSceneActive(active)
    }

    public func startClassic() async {
        guard isRestorationComplete else { return }
        await beginNewGame(mode: .classicLocal, difficulty: .medium)
    }

    public func startPicPacLocal() async {
        guard isRestorationComplete else { return }
        await beginNewGame(mode: .picPacLocal, difficulty: .medium)
    }

    public func startPicPacAI(difficulty: Difficulty) async {
        guard isRestorationComplete else { return }
        await beginNewGame(mode: .picPacAI, difficulty: difficulty)
    }

    public func show(_ screen: AppScreen) async {
        guard isRestorationComplete else { return }
        guard screen != .game else { return }
        if state.screen == .game {
            await goHome()
        }
        let next = GamePresentationState(screen: screen, difficulty: state.difficulty)
        await publish(next)
    }

    public func goHome() async {
        guard isRestorationComplete else { return }
        cancelAllWork()
        feedbackEvent = nil
        await publish(GamePresentationState())
    }

    public func readyForReveal() async {
        guard isRestorationComplete else { return }
        guard state.screen == .game, state.stage == .handoff else { return }
        await revealCurrentPiece()
    }

    /// Accepts either the owned clock's completion or a future SwiftUI
    /// `.task(id:)` acknowledgement. Identity makes duplicate delivery inert.
    public func presentationStepFinished(_ presentationID: UInt64) async {
        guard isRestorationComplete,
              isSceneActive,
              state.screen == .game,
              state.presentationID == presentationID else { return }

        switch state.stage {
        case .turnStart:
            await revealCurrentPiece()
        case .revealing:
            await finishReveal()
        case .aiTargeting:
            await applyAIDecision()
        case .aiPlacing:
            await publish(advance(state, to: .aiSettling))
        case .aiSettling:
            await finishAISettlement()
        case .handoff, .playing, .aiThinking, .terminal:
            break
        }
    }

    public func place(at cellIndex: Int) async {
        guard isRestorationComplete,
              state.screen == .game,
              state.stage == .playing,
              (0...8).contains(cellIndex) else { return }
        let cell = Cell(cellIndex)

        switch state.mode {
        case .classicLocal:
            await placeClassic(at: cell)
        case .picPacLocal:
            await placePicPac(at: cell)
        case .picPacAI where state.activePlayer == .one:
            await placePicPac(at: cell)
        case .picPacAI, nil:
            break
        }
    }

    public func rematch() async {
        guard isRestorationComplete, let mode = state.mode else { return }
        switch mode {
        case .classicLocal:
            nextStarter = state.classic?.starter.other ?? nextStarter.other
        case .picPacLocal, .picPacAI:
            nextStarter = state.picPac?.starter.other ?? nextStarter.other
        }
        await beginNewGame(mode: mode, difficulty: state.difficulty)
    }

    public func updateSettings(_ newSettings: AppSettings) async {
        guard isRestorationComplete else { return }
        let previousEffectiveMotion = effectiveReducedMotion
        settings = newSettings
        do {
            try await store.saveSettingsData(PersistenceCodec.encode(newSettings))
            persistenceStatus = .healthy
        } catch {
            persistenceStatus = .saveFailed
        }
        if previousEffectiveMotion != effectiveReducedMotion {
            restartPresentationClock()
        }
    }

    /// Supplies the current accessibility environment without persisting it as
    /// an in-app preference. Either source shortens presentation timing.
    public func setSystemReducedMotion(_ enabled: Bool) {
        guard systemReducedMotion != enabled else { return }
        let previousEffectiveMotion = effectiveReducedMotion
        systemReducedMotion = enabled
        if previousEffectiveMotion != effectiveReducedMotion {
            restartPresentationClock()
        }
    }

    public var effectiveReducedMotion: Bool {
        settings.reducedMotion || systemReducedMotion
    }

    public func markTitleEntranceConsumed() async {
        guard isRestorationComplete, !titleEntranceConsumed else { return }
        titleEntranceConsumed = true
        await persistCurrentSnapshot()
    }

    public func consumeFeedback(id: UInt64) {
        guard feedbackEvent?.id == id else { return }
        feedbackEvent = nil
    }

    /// Inactive scenes checkpoint, suspend every stage clock, and cancel any
    /// unfinished search. Foregrounding resumes the same complete readable beat.
    public func setSceneActive(_ active: Bool) async {
        guard active != isSceneActive else { return }
        isSceneActive = active
        guard isRestorationComplete else {
            if !active {
                feedbackEvent = nil
                cancelPresentationClock()
                cancelAIWork()
            }
            return
        }
        if active {
            resumeCurrentWork()
        } else {
            feedbackEvent = nil
            cancelPresentationClock()
            cancelAIWork()
            await persistCurrentSnapshot()
        }
    }

    private func beginNewGame(mode: GameMode, difficulty: Difficulty) async {
        cancelAllWork()
        feedbackEvent = nil
        revision = revision >= TurnToken.maximumRevision ? 1 : revision + 1
        let starter = nextStarter

        switch mode {
        case .classicLocal:
            let game = ClassicRules.newGame(starter: starter, revision: revision)
            await publish(
                GamePresentationState(
                    screen: .game,
                    mode: mode,
                    difficulty: difficulty,
                    classic: game,
                    stage: .playing
                )
            )
        case .picPacLocal, .picPacAI:
            let game = PicPacRules.newGame(starter: starter, revision: revision)
            let base = GamePresentationState(
                screen: .game,
                mode: mode,
                difficulty: difficulty,
                picPac: game
            )
            let firstStage: TurnStage = mode == .picPacLocal ? .handoff : .turnStart
            await publish(advance(base, to: firstStage))
        }
    }

    private func revealCurrentPiece() async {
        guard let game = state.picPac,
              case .awaitingDraw = game.phase,
              game.hiddenTotal > 0 else { return }
        let randomValue = drawRandom.nextInt(upperBound: game.hiddenTotal)
        guard (0..<game.hiddenTotal).contains(randomValue) else { return }
        let symbol: Symbol = randomValue < game.remainingX ? .x : .o
        guard case let .accepted(revealed, _) = PicPacRules.draw(game, symbol: symbol) else {
            return
        }

        revealAcknowledged = false
        pendingAI = nil
        var next = state
        next.picPac = revealed
        next.aiTargetCell = nil
        next.aiMoveSymbol = nil
        next = advance(next, to: .revealing)
        await publish(next, effect: .reveal)

        if next.mode == .picPacAI, revealed.activePlayer == .two {
            launchAI(for: revealed)
        }
    }

    private func placeClassic(at cell: Cell) async {
        guard let game = state.classic else { return }
        guard case let .accepted(placed, event) = ClassicRules.place(
            game,
            at: cell,
            token: game.turnToken
        ) else { return }

        var next = state
        next.classic = placed
        next.stage = placed.isTerminal ? .terminal : .playing
        await publish(next, effect: feedback(for: event))
    }

    private func placePicPac(at cell: Cell) async {
        guard let game = state.picPac,
              case let .awaitingPlacement(held: _, token: token) = game.phase else { return }
        guard case let .accepted(placed, event) = PicPacRules.place(
            game,
            at: cell,
            token: token
        ) else { return }

        var next = state
        next.picPac = placed
        if placed.isTerminal {
            next.stage = .terminal
        } else {
            let followingStage: TurnStage = state.mode == .picPacLocal ? .handoff : .turnStart
            next = advance(next, to: followingStage)
        }
        await publish(next, effect: feedback(for: event))
    }

    private func finishReveal() async {
        revealAcknowledged = true
        guard state.mode == .picPacAI, state.activePlayer == .two else {
            await publish(advance(state, to: .playing))
            return
        }
        if let pendingAI {
            await beginAITargeting(pendingAI)
        } else {
            await publish(advance(state, to: .aiThinking))
        }
    }

    private func launchAI(for game: PicPacState) {
        guard isSceneActive,
              case let .awaitingPlacement(held: _, token: token) = game.phase,
              game.activePlayer == .two,
              let observation = try? AiObservation.from(state: game, agentPlayer: .two) else {
            return
        }

        aiTask?.cancel()
        aiGeneration += 1
        let generation = aiGeneration
        let request = AIRequest(revision: game.revision, token: token)
        let worker = aiWorker
        let legalCells = observation.legalCells
        let difficulty = state.difficulty

        aiTask = Task { [weak self, worker, observation, legalCells, difficulty] in
            let cellIndex: Int
            do {
                let proposed = try await worker.chooseMove(
                    for: observation,
                    difficulty: difficulty
                )
                guard legalCells.contains(where: { $0.index == proposed }) else {
                    throw AIWorkerError.illegalCell(proposed)
                }
                cellIndex = proposed
            } catch is CancellationError {
                return
            } catch {
                guard let fallback = legalCells.first else { return }
                cellIndex = fallback.index
            }
            guard !Task.isCancelled else { return }
            await self?.receiveAIDecision(
                cellIndex: cellIndex,
                request: request,
                generation: generation
            )
        }
    }

    private func receiveAIDecision(
        cellIndex: Int,
        request: AIRequest,
        generation: UInt64
    ) async {
        guard generation == aiGeneration,
              isSceneActive,
              (0...8).contains(cellIndex),
              let game = state.picPac,
              game.revision == request.revision,
              game.activePlayer == .two,
              case let .awaitingPlacement(held: _, token: token) = game.phase,
              token == request.token else { return }
        let cell = Cell(cellIndex)
        guard game.board[cell] == nil else { return }

        let pending = PendingAI(request: request, cell: cell)
        pendingAI = pending
        if revealAcknowledged {
            await beginAITargeting(pending)
        }
    }

    private func beginAITargeting(_ pending: PendingAI) async {
        guard let game = state.picPac,
              game.revision == pending.request.revision,
              game.activePlayer == .two,
              case let .awaitingPlacement(held: held, token: token) = game.phase,
              token == pending.request.token,
              game.board[pending.cell] == nil else { return }

        var next = state
        next.aiTargetCell = pending.cell.index
        next.aiMoveSymbol = held
        await publish(advance(next, to: .aiTargeting))
    }

    private func applyAIDecision() async {
        guard let pending = pendingAI,
              let game = state.picPac,
              game.revision == pending.request.revision,
              game.activePlayer == .two,
              case let .awaitingPlacement(held: held, token: token) = game.phase,
              token == pending.request.token,
              game.board[pending.cell] == nil else { return }

        pendingAI = nil
        guard case let .accepted(placed, event) = PicPacRules.place(
            game,
            at: pending.cell,
            token: pending.request.token
        ) else { return }

        var next = state
        next.picPac = placed
        next.aiTargetCell = pending.cell.index
        next.aiMoveSymbol = held
        await publish(advance(next, to: .aiPlacing), effect: feedback(for: event))
    }

    private func finishAISettlement() async {
        guard let game = state.picPac else { return }
        if game.isTerminal {
            await publish(advance(state, to: .terminal))
        } else {
            var next = state
            next.aiTargetCell = nil
            next.aiMoveSymbol = nil
            await publish(advance(next, to: .turnStart))
        }
    }

    private func advance(
        _ input: GamePresentationState,
        to stage: TurnStage
    ) -> GamePresentationState {
        presentationCounter = presentationCounter == UInt64.max ? 1 : presentationCounter + 1
        var next = input
        next.stage = stage
        next.presentationID = presentationCounter
        return next
    }

    private func publish(
        _ newState: GamePresentationState,
        effect: FeedbackKind? = nil
    ) async {
        cancelPresentationClock()
        state = newState
        if let effect { emit(effect) }
        await persistCurrentSnapshot()
        guard state == newState else { return }
        schedulePresentationClockIfNeeded()
    }

    private func emit(_ kind: FeedbackKind) {
        feedbackCounter &+= 1
        feedbackEvent = FeedbackEvent(id: feedbackCounter, kind: kind)
    }

    private func feedback(for event: GameEvent) -> FeedbackKind {
        switch event {
        case .gameWon:
            .win
        case .gameDrawn:
            .draw
        case .pieceRevealed:
            .reveal
        case .piecePlaced:
            .place
        }
    }

    private func restartPresentationClock() {
        cancelPresentationClock()
        schedulePresentationClockIfNeeded()
    }

    private func schedulePresentationClockIfNeeded() {
        guard isSceneActive,
              state.screen == .game,
              let milliseconds = state.stage.delayMilliseconds(
                  reducedMotion: effectiveReducedMotion
              ) else { return }
        presentationGeneration += 1
        let generation = presentationGeneration
        let presentationID = state.presentationID
        let clock = clock
        presentationTask = Task { [weak self, clock] in
            do {
                try await clock.sleep(milliseconds: milliseconds)
            } catch {
                return
            }
            guard !Task.isCancelled else { return }
            await self?.presentationClockDidFire(
                presentationID: presentationID,
                generation: generation
            )
        }
    }

    private func presentationClockDidFire(
        presentationID: UInt64,
        generation: UInt64
    ) async {
        guard generation == presentationGeneration else { return }
        await presentationStepFinished(presentationID)
    }

    private func cancelPresentationClock() {
        presentationGeneration &+= 1
        presentationTask?.cancel()
        presentationTask = nil
    }

    private func cancelAIWork() {
        aiGeneration &+= 1
        aiTask?.cancel()
        aiTask = nil
        pendingAI = nil
        revealAcknowledged = false
    }

    private func cancelAllWork() {
        cancelPresentationClock()
        cancelAIWork()
    }

    private func resumeCurrentWork() {
        cancelPresentationClock()
        cancelAIWork()
        guard isSceneActive, state.screen == .game else { return }

        schedulePresentationClockIfNeeded()
        guard state.mode == .picPacAI, let game = state.picPac else { return }
        switch state.stage {
        case .revealing where game.activePlayer == .two:
            revealAcknowledged = false
            launchAI(for: game)
        case .aiThinking:
            revealAcknowledged = true
            launchAI(for: game)
        case .aiTargeting:
            revealAcknowledged = true
            guard let index = state.aiTargetCell,
                  (0...8).contains(index),
                  case let .awaitingPlacement(held: _, token: token) = game.phase else { return }
            pendingAI = PendingAI(
                request: AIRequest(revision: game.revision, token: token),
                cell: Cell(index)
            )
        default:
            break
        }
    }

    private func persistCurrentSnapshot() async {
        let snapshot = RestorationSnapshot(
            state: state,
            revision: revision,
            nextStarter: nextStarter,
            presentationCounter: presentationCounter,
            titleEntranceConsumed: titleEntranceConsumed
        )
        do {
            try snapshot.validate()
            try await store.saveSnapshotData(PersistenceCodec.encode(snapshot))
            persistenceStatus = .healthy
        } catch {
            persistenceStatus = .saveFailed
        }
    }

    private func resetToFreshHome() {
        cancelAllWork()
        state = GamePresentationState()
        revision = 0
        nextStarter = .one
        presentationCounter = 0
        titleEntranceConsumed = false
        feedbackEvent = nil
    }
}
