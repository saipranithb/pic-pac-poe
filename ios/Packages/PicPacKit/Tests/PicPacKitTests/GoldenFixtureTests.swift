import Foundation
import XCTest
@testable import PicPacAI
@testable import PicPacCore

final class GoldenFixtureTests: XCTestCase {
    private typealias Object = [String: Any]

    private let expectedRootKeys: Set<String> = [
        "schemaVersion", "pathBase", "purpose", "sources", "encoding",
        "requiredFixtureGroups",
        "scriptedGames", "ruleTransitions", "bagProbabilities",
        "deterministicDraws", "scriptedRandomTraces", "aiChoices", "presentationScenarios",
        "restorationScenarios", "graphOracle",
    ]

    private let expectedFixtureIDs: Set<String> = [
        "picpac-player-one-wins-with-o",
        "picpac-player-two-wins-with-x",
        "picpac-full-board-draw",
        "classic-player-one-top-row",
        "draw-removes-piece-before-placement",
        "legal-placement-switches-actor",
        "stale-turn-rejected-without-mutation",
        "occupied-cell-rejected-without-mutation",
        "wrong-phase-rejected-without-mutation",
        "exhausted-symbol-rejected-without-mutation",
        "terminal-command-rejected-without-mutation",
        "initial-five-five",
        "held-x-is-excluded",
        "held-o-is-excluded",
        "single-o-remains",
        "initial-roll-zero-is-x",
        "initial-roll-four-is-x",
        "initial-roll-five-is-o",
        "initial-roll-nine-is-o",
        "two-one-boundary-x",
        "two-one-boundary-o",
        "bag-five-call-boundary-trace",
        "random-agent-three-call-index-trace",
        "easy-immediate-win-with-x",
        "easy-immediate-win-with-o",
        "medium-opening-held-x",
        "hard-opening-held-x",
        "hard-opening-held-o",
        "random-scripted-legal-index",
        "local-handoff-ready-reveal-place",
        "computer-winning-move-settles-before-result",
        "computer-draw-move-settles-before-result",
        "held-piece-restores-without-redraw",
        "computer-target-restores-and-commits-once",
        "stale-presentation-callback-after-rematch-is-ignored",
        "cancelled-ai-result-after-mode-replacement-is-ignored",
    ]

    func testBundledFixtureBytesExactlyMatchCanonicalFile() throws {
        let bundled = try XCTUnwrap(resourceURL())
        let canonical = try XCTUnwrap(canonicalFixtureURL())
        XCTAssertEqual(try Data(contentsOf: bundled), try Data(contentsOf: canonical))
    }

    func testSchemaAndEveryFixtureRouteAreExplicit() throws {
        let root = try fixtureRoot()
        try validateSchema(root)
        try validateEncoding(try object(root, "encoding"))

        let groupNames = [
            "scriptedGames", "ruleTransitions", "bagProbabilities",
            "deterministicDraws", "scriptedRandomTraces", "aiChoices", "presentationScenarios",
            "restorationScenarios",
        ]
        XCTAssertEqual(try strings(root, "requiredFixtureGroups"), groupNames)
        var foundIDs: [String] = []

        for group in groupNames {
            for fixture in try objects(root, group) {
                let id = try string(fixture, "id")
                foundIDs.append(id)
                do {
                    switch group {
                    case "scriptedGames":
                        try executeScriptedGame(fixture, id: id)
                    case "ruleTransitions":
                        try executeRuleTransition(fixture, id: id)
                    case "bagProbabilities":
                        try executeBagProbability(fixture, id: id)
                    case "deterministicDraws":
                        try executeDeterministicDraw(fixture, id: id)
                    case "scriptedRandomTraces":
                        try executeScriptedRandomTrace(fixture, id: id)
                    case "aiChoices":
                        try executeAIChoice(fixture, id: id)
                    case "presentationScenarios", "restorationScenarios":
                        try validatePresentationOwnedFixture(fixture, group: group, id: id)
                    default:
                        throw FixtureError("unhandled fixture group \(group)")
                    }
                } catch {
                    XCTFail("[\(id)] \(error)")
                }
            }
        }

        XCTAssertEqual(foundIDs.count, 36)
        XCTAssertEqual(Set(foundIDs).count, foundIDs.count, "Fixture IDs must be unique")
        XCTAssertEqual(Set(foundIDs), expectedFixtureIDs, "Unhandled fixture ID")

        let oracle = try object(root, "graphOracle")
        XCTAssertEqual(try int(oracle, "chanceStates"), 11_065)
        XCTAssertEqual(try int(oracle, "decisionStates"), 21_314)
        XCTAssertEqual(try int(oracle, "terminalStates"), 6_648)
        XCTAssertEqual(try int(oracle, "totalStates"), 39_027)
    }

    func testUnknownSchemaVersionAndRootGroupAreRejected() throws {
        var unknownVersion = try fixtureRoot()
        unknownVersion["schemaVersion"] = 2
        XCTAssertThrowsError(try validateSchema(unknownVersion))

        var unknownGroup = try fixtureRoot()
        unknownGroup["futureFixtureGroup"] = []
        XCTAssertThrowsError(try validateSchema(unknownGroup))
    }

    private func executeScriptedGame(_ fixture: Object, id: String) throws {
        let mode = try string(fixture, "mode")
        let starter = try player(string(fixture, "starter"))
        let moves = try objects(fixture, "moves")
        let expected = try object(fixture, "expected")

        if mode == "PIC_PAC" {
            var state = PicPacRules.newGame(starter: starter)
            for move in moves {
                XCTAssertEqual(state.activePlayer, try player(string(move, "actor")), id)
                state = try accepted(
                    PicPacRules.draw(state, symbol: try symbol(string(move, "draw")))
                )
                state = try accepted(
                    PicPacRules.place(
                        state,
                        at: Cell(try int(move, "cell")),
                        token: try requireToken(state)
                    )
                )
            }
            XCTAssertEqual(state.board, try board(expected, "board"), id)
            XCTAssertEqual(state.remainingX, try int(expected, "remainingX"), id)
            XCTAssertEqual(state.remainingO, try int(expected, "remainingO"), id)
            try assertOutcome(state.phase.outcome, expected: object(expected, "outcome"), id: id)
        } else if mode == "CLASSIC" {
            var state = ClassicRules.newGame(starter: starter)
            for move in moves {
                XCTAssertEqual(state.activePlayer, try player(string(move, "actor")), id)
                state = try accepted(
                    ClassicRules.place(
                        state,
                        at: Cell(try int(move, "cell")),
                        token: state.turnToken
                    )
                )
            }
            XCTAssertEqual(state.board, try board(expected, "board"), id)
            try assertOutcome(state.outcome, expected: object(expected, "outcome"), id: id)
        } else {
            throw FixtureError("unknown scripted-game mode \(mode)")
        }
    }

    private func executeRuleTransition(_ fixture: Object, id: String) throws {
        guard try string(fixture, "mode") == "PIC_PAC" else {
            throw FixtureError("unsupported rule transition mode")
        }
        let initial = try picPacState(object(fixture, "initial"))
        let input = try object(fixture, "input")
        let expected = try object(fixture, "expected")
        let result: TransitionResult<PicPacState>
        switch try string(input, "action") {
        case "DRAW":
            result = PicPacRules.draw(initial, symbol: try symbol(string(input, "symbol")))
        case "PLACE":
            result = PicPacRules.place(
                initial,
                at: Cell(try int(input, "cell")),
                token: TurnToken(Int64(try int(input, "turnToken")))
            )
        default:
            throw FixtureError("unknown rule action")
        }

        if try bool(expected, "accepted") {
            guard case let .accepted(state, event) = result else {
                throw FixtureError("expected accepted transition")
            }
            if expected["remainingX"] != nil {
                XCTAssertEqual(state.remainingX, try int(expected, "remainingX"), id)
                XCTAssertEqual(state.remainingO, try int(expected, "remainingO"), id)
                XCTAssertEqual(state.hiddenTotal, try int(expected, "hiddenTotal"), id)
            }
            if expected["board"] != nil {
                XCTAssertEqual(state.board, try board(expected, "board"), id)
            }
            if expected["activePlayer"] != nil {
                XCTAssertEqual(state.activePlayer, try player(string(expected, "activePlayer")), id)
            }
            XCTAssertEqual(phaseName(state.phase), try string(expected, "phase"), id)
            if expected["heldSymbol"] != nil {
                XCTAssertEqual(state.phase.heldSymbol, try symbol(string(expected, "heldSymbol")), id)
            }
            if expected["turnToken"] != nil {
                XCTAssertEqual(state.phase.turnToken, TurnToken(Int64(try int(expected, "turnToken"))), id)
            }
            try assertEvent(event, expected: object(expected, "event"), id: id)
        } else {
            guard case let .rejected(reason) = result else {
                throw FixtureError("expected rejected transition")
            }
            XCTAssertEqual(reason.rawValue, try string(expected, "rejection"), id)
            XCTAssertTrue(try bool(expected, "stateUnchanged"), id)
            XCTAssertEqual(initial, try picPacState(object(fixture, "initial")), id)
        }
    }

    private func executeBagProbability(_ fixture: Object, id: String) throws {
        let x = try int(fixture, "remainingX")
        let o = try int(fixture, "remainingO")
        let expected = try object(fixture, "expected")
        let total = x + o
        XCTAssertEqual(total, try int(expected, "hiddenTotal"), id)
        let xExpected = try object(expected, "x")
        let oExpected = try object(expected, "o")
        XCTAssertEqual(Double(x) / Double(total), try fraction(xExpected), accuracy: 1e-15, id)
        XCTAssertEqual(Double(o) / Double(total), try fraction(oExpected), accuracy: 1e-15, id)
    }

    private func executeDeterministicDraw(_ fixture: Object, id: String) throws {
        let x = try int(fixture, "remainingX")
        let o = try int(fixture, "remainingO")
        let bound = try int(fixture, "nextIntBound")
        let result = try int(fixture, "scriptedResult")
        var random = ScriptedBoundedRandomSource(
            steps: [BoundedRandomStep(upperBound: bound, result: result)]
        )
        let state = try drawState(remainingX: x, remainingO: o)
        let drawn = try accepted(try PicPacRules.draw(state, using: &random))
        XCTAssertEqual(drawn.phase.heldSymbol, try symbol(string(fixture, "expectedSymbol")), id)
        XCTAssertEqual(random.consumedCount, 1, id)
    }

    private func executeScriptedRandomTrace(_ fixture: Object, id: String) throws {
        let steps = try objects(fixture, "steps")
        var random = ScriptedBoundedRandomSource(
            steps: try steps.map {
                BoundedRandomStep(
                    upperBound: try int($0, "nextIntBound"),
                    result: try int($0, "scriptedResult")
                )
            }
        )

        switch try string(fixture, "consumer") {
        case "PIC_PAC_SESSION":
            let initial = try object(fixture, "initial")
            var state = try drawState(
                remainingX: int(initial, "remainingX"),
                remainingO: int(initial, "remainingO")
            )
            for (offset, step) in steps.enumerated() {
                XCTAssertEqual(try int(step, "call"), offset + 1, id)
                state = try accepted(try PicPacRules.draw(state, using: &random))
                XCTAssertEqual(
                    state.phase.heldSymbol,
                    try symbol(string(step, "expectedSymbol")),
                    id
                )
                state = try accepted(
                    PicPacRules.place(
                        state,
                        at: Cell(try int(step, "placeCell")),
                        token: try requireToken(state)
                    )
                )
            }
        case "RANDOM_AGENT":
            for (offset, step) in steps.enumerated() {
                XCTAssertEqual(try int(step, "call"), offset + 1, id)
                let legal = try board(step, "board").legalCells
                XCTAssertEqual(legal, try ints(step, "legalCells").map { Cell($0) }, id)
                let selectedIndex = try random.nextInt(upperBound: legal.count)
                XCTAssertEqual(legal[selectedIndex], Cell(try int(step, "expectedCell")), id)
            }
        default:
            throw FixtureError("unknown scripted random consumer")
        }
        XCTAssertEqual(random.consumedCount, steps.count, id)
    }

    private func executeAIChoice(_ fixture: Object, id: String) throws {
        let agent = try string(fixture, "agent")
        if agent == "RANDOM_BASELINE" {
            let expectedLegal = try ints(fixture, "legalCells").map { Cell($0) }
            let state = try PicPacDecisionState(
                board: board(fixture, "board"),
                heldSymbol: symbol(string(fixture, "heldSymbol")),
                remainingX: int(fixture, "remainingX"),
                remainingO: int(fixture, "remainingO")
            )
            XCTAssertEqual(state.legalCells, expectedLegal, id)
            var random = ScriptedBoundedRandomSource(
                steps: [
                    BoundedRandomStep(
                        upperBound: expectedLegal.count,
                        result: try int(fixture, "scriptedNextIntResult")
                    )
                ]
            )
            let selected = expectedLegal[try random.nextInt(upperBound: expectedLegal.count)]
            XCTAssertEqual(selected, Cell(try int(fixture, "expectedCell")), id)
            XCTAssertEqual(random.consumedCount, 1, id)
            return
        }

        let held = try symbol(string(fixture, "heldSymbol"))
        let state = try PicPacDecisionState(
            board: board(fixture, "board"),
            heldSymbol: held,
            remainingX: int(fixture, "remainingX"),
            remainingO: int(fixture, "remainingO")
        )
        let expectedCell = Cell(try int(fixture, "expectedCell"))

        if agent == "HEURISTIC" {
            let immediateWins = try state.legalCells.filter {
                try PublicPicPacSearchModel.place(state, at: $0) == .win
            }
            XCTAssertEqual(immediateWins, [expectedCell], id)
            return
        }

        guard agent == "EXPECTIMINIMAX_FULL" || agent == "EXPECTIMINIMAX_DEPTH_4" else {
            throw FixtureError("unknown agent \(agent)")
        }
        let analysis = try ReferenceExpectiminimaxSolver().analyze(state)
        XCTAssertEqual(analysis.bestCell, expectedCell, id)

        if agent == "EXPECTIMINIMAX_DEPTH_4" {
            XCTAssertEqual(
                try ints(fixture, "tieOrder"),
                ReferenceExpectiminimaxSolver.ordered(Cell.all).map(\.index),
                id
            )
        }
        if let root = fixture["expectedRootValue"] as? Object {
            let tolerance = try double(root, "tolerance")
            XCTAssertEqual(analysis.value, try fraction(root), accuracy: tolerance, id)
        }
        if let values = fixture["expectedActionValues"] as? Object {
            for (cellText, rawExpected) in values {
                guard let index = Int(cellText), let expected = rawExpected as? Object else {
                    throw FixtureError("malformed action value")
                }
                XCTAssertEqual(
                    try require(analysis.actionValues[Cell(index)]),
                    try fraction(expected),
                    accuracy: 1e-12,
                    id
                )
            }
        }
    }

    private func validatePresentationOwnedFixture(
        _ fixture: Object,
        group: String,
        id: String
    ) throws {
        // These IDs are deliberately routed rather than silently skipped. Their
        // timed-stage and restoration behavior belongs to PicPacPresentation.
        if group == "presentationScenarios" {
            _ = try string(fixture, "mode")
            _ = try object(fixture, "initial")
            XCTAssertFalse(try objects(fixture, "steps").isEmpty, id)
        } else {
            guard fixture["snapshot"] != nil || fixture["beforeReplacement"] != nil else {
                throw FixtureError("restoration fixture has no snapshot/replacement state")
            }
            guard fixture["expectedAfterRestore"] != nil || fixture["expected"] != nil else {
                throw FixtureError("restoration fixture has no expected state")
            }
        }
    }

    private func picPacState(_ value: Object) throws -> PicPacState {
        let board = try board(value, "board")
        let phase: PicPacPhase
        switch try string(value, "phase") {
        case "AWAITING_DRAW":
            phase = .awaitingDraw
        case "AWAITING_PLACEMENT":
            phase = .awaitingPlacement(
                held: try symbol(string(value, "heldSymbol")),
                token: TurnToken(Int64(try int(value, "turnToken")))
            )
        case "TERMINAL":
            phase = .terminal(outcome: try outcome(object(value, "outcome")))
        default:
            throw FixtureError("unknown domain phase")
        }
        return try PicPacState(
            board: board,
            activePlayer: player(string(value, "activePlayer")),
            remainingX: int(value, "remainingX"),
            remainingO: int(value, "remainingO"),
            phase: phase,
            starter: player(string(value, "starter")),
            revision: Int64(int(value, "revision"))
        )
    }

    private func drawState(remainingX: Int, remainingO: Int) throws -> PicPacState {
        let board: Board
        switch (remainingX, remainingO) {
        case (5, 5):
            board = .empty
        case (2, 1):
            board = try Board(symbols: [.x, .o, .x, .o, .x, .o, nil, .o, nil])
        default:
            throw FixtureError("unsupported deterministic draw counts")
        }
        return try PicPacState(
            board: board,
            activePlayer: board.occupiedCount.isMultiple(of: 2) ? .one : .two,
            remainingX: remainingX,
            remainingO: remainingO,
            phase: .awaitingDraw,
            starter: .one,
            revision: 0
        )
    }

    private func assertEvent(_ actual: GameEvent, expected: Object, id: String) throws {
        switch try string(expected, "kind") {
        case "PIECE_REVEALED":
            XCTAssertEqual(
                actual,
                .pieceRevealed(
                    player: try player(string(expected, "actor")),
                    symbol: try symbol(string(expected, "symbol"))
                ),
                id
            )
        case "PIECE_PLACED":
            XCTAssertEqual(
                actual,
                .piecePlaced(
                    player: try player(string(expected, "actor")),
                    symbol: try symbol(string(expected, "symbol")),
                    cell: Cell(try int(expected, "cell"))
                ),
                id
            )
        default:
            throw FixtureError("unknown event kind")
        }
    }

    private func assertOutcome(
        _ actual: GameOutcome?,
        expected: Object,
        id: String
    ) throws {
        XCTAssertEqual(actual, try outcome(expected), id)
    }

    private func outcome(_ value: Object) throws -> GameOutcome {
        switch try string(value, "kind") {
        case "DRAW":
            return .draw
        case "WIN":
            let lines = try arrays(value, "lines").map { raw -> WinningLine in
                let cells = try raw.map(jsonInt)
                guard cells.count == 3 else { throw FixtureError("winning line must have 3 cells") }
                return WinningLine(Cell(cells[0]), Cell(cells[1]), Cell(cells[2]))
            }
            return .win(
                GameOutcome.Win(
                    player: try player(string(value, "actor")),
                    symbol: try symbol(string(value, "symbol")),
                    lines: lines
                )
            )
        default:
            throw FixtureError("unknown outcome kind")
        }
    }

    private func phaseName(_ phase: PicPacPhase) -> String {
        switch phase {
        case .awaitingDraw: "AWAITING_DRAW"
        case .awaitingPlacement: "AWAITING_PLACEMENT"
        case .terminal: "TERMINAL"
        }
    }

    private func requireToken(_ state: PicPacState) throws -> TurnToken {
        try require(state.phase.turnToken)
    }

    private func accepted<State>(_ result: TransitionResult<State>) throws -> State {
        switch result {
        case let .accepted(state, _): state
        case let .rejected(reason): throw FixtureError("transition rejected: \(reason.rawValue)")
        }
    }

    private func fixtureRoot() throws -> Object {
        let url = try require(resourceURL())
        let value = try JSONSerialization.jsonObject(with: Data(contentsOf: url))
        return try require(value as? Object)
    }

    private func validateSchema(_ root: Object) throws {
        guard Set(root.keys) == expectedRootKeys else {
            throw FixtureError("unknown or missing root group")
        }
        guard try int(root, "schemaVersion") == 1 else {
            throw FixtureError("unsupported schema version")
        }
    }

    private func validateEncoding(_ encoding: Object) throws {
        XCTAssertEqual(try string(encoding, "cellOrder"), "row-major")
        XCTAssertEqual(try ints(encoding, "cellIndices"), Cell.all.map(\.index))
        XCTAssertEqual(
            try string(object(encoding, "coordinateFormula"), "rowOneBased"),
            "floor(cell / 3) + 1"
        )
        XCTAssertEqual(
            try string(object(encoding, "coordinateFormula"), "columnOneBased"),
            "cell % 3 + 1"
        )
        let expectedLines = try arrays(encoding, "winningLines").map { raw -> [Int] in
            try raw.map(jsonInt)
        }
        XCTAssertEqual(expectedLines, Board.winningLines.map { $0.cells.map(\.index) })
        let bag = try object(encoding, "initialBag")
        XCTAssertEqual(try int(bag, "X"), 5)
        XCTAssertEqual(try int(bag, "O"), 5)
        XCTAssertEqual(
            try string(encoding, "turnTokenFormula"),
            "revision * 16 + occupiedCount + 1"
        )
    }

    private func resourceURL() -> URL? {
        Bundle.module.url(forResource: "golden-fixtures", withExtension: "json")
            ?? Bundle.module.url(
                forResource: "golden-fixtures",
                withExtension: "json",
                subdirectory: "Resources"
            )
    }

    private func canonicalFixtureURL() -> URL? {
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while directory.path != "/" {
            let candidate = directory.appendingPathComponent(
                "docs/ios-handoff/golden-fixtures.json"
            )
            if FileManager.default.fileExists(atPath: candidate.path) { return candidate }
            directory.deleteLastPathComponent()
        }
        return nil
    }

    private func board(_ value: Object, _ key: String) throws -> Board {
        let symbols: [Symbol?] = try array(value, key).map { item in
            if item is NSNull { return nil }
            return try symbol(jsonString(item))
        }
        return try Board(symbols: symbols)
    }

    private func fraction(_ value: Object) throws -> Double {
        Double(try int(value, "numerator")) / Double(try int(value, "denominator"))
    }

    private func symbol(_ value: String) throws -> Symbol {
        guard let result = Symbol(rawValue: value) else {
            throw FixtureError("unknown symbol \(value)")
        }
        return result
    }

    private func player(_ value: String) throws -> Player {
        guard let result = Player(rawValue: value) else {
            throw FixtureError("unknown player \(value)")
        }
        return result
    }

    private func object(_ value: Object, _ key: String) throws -> Object {
        try require(value[key] as? Object)
    }

    private func objects(_ value: Object, _ key: String) throws -> [Object] {
        try require(value[key] as? [Object])
    }

    private func array(_ value: Object, _ key: String) throws -> [Any] {
        try require(value[key] as? [Any])
    }

    private func arrays(_ value: Object, _ key: String) throws -> [[Any]] {
        try require(value[key] as? [[Any]])
    }

    private func ints(_ value: Object, _ key: String) throws -> [Int] {
        try array(value, key).map(jsonInt)
    }

    private func strings(_ value: Object, _ key: String) throws -> [String] {
        try array(value, key).map(jsonString)
    }

    private func string(_ value: Object, _ key: String) throws -> String {
        try require(value[key] as? String)
    }

    private func int(_ value: Object, _ key: String) throws -> Int {
        try jsonInt(require(value[key]))
    }

    private func double(_ value: Object, _ key: String) throws -> Double {
        guard let number = value[key] as? NSNumber else {
            throw FixtureError("\(key) is not numeric")
        }
        return number.doubleValue
    }

    private func bool(_ value: Object, _ key: String) throws -> Bool {
        guard let result = value[key] as? Bool else {
            throw FixtureError("\(key) is not a boolean")
        }
        return result
    }

    private func jsonString(_ value: Any) throws -> String {
        try require(value as? String)
    }

    private func jsonInt(_ value: Any) throws -> Int {
        guard let number = value as? NSNumber else {
            throw FixtureError("value is not an integer")
        }
        return number.intValue
    }

    private func require<Value>(_ value: Value?) throws -> Value {
        guard let value else { throw FixtureError("required fixture value is missing") }
        return value
    }

    private struct FixtureError: Error, CustomStringConvertible {
        let description: String
        init(_ description: String) { self.description = description }
    }
}
