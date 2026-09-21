import XCTest

/// Player-driven tests launch the shipped dependency graph with real AI and
/// clock timings. Only storage is namespaced. Explicit fixture tests below
/// remain separate from complete games and use longer holds for interruption.
@MainActor
final class PicPacPoeUITests: XCTestCase {
    private var app: XCUIApplication!
    private var session = ""

    override func setUp() async throws {
        await MainActor.run {
            continueAfterFailure = false
            session = UUID().uuidString
            app = XCUIApplication()
        }
    }

    private func launch(reset: Bool = true, extra: [String] = []) {
        app.launchArguments = ["-ui-test-session", session] + (reset ? ["-ui-test-reset"] : []) + extra
        app.launch()
    }

    private func element(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }
    private func cell(_ index: Int) -> XCUIElement { app.buttons["cell-\(index)"] }
    private var result: XCUIElement { element("result-dialog") }
    private var legalCells: XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-' AND enabled == YES"))
    }

    private func eventually(_ description: String, timeout: TimeInterval = 20, file: StaticString = #filePath, line: UInt = #line, _ condition: @escaping @MainActor () -> Bool) {
        let predicate = NSPredicate { _, _ in MainActor.assumeIsolated { condition() } }
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed, description, file: file, line: line)
    }

    private func tap(_ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let target = element(id)
        XCTAssertTrue(target.waitForExistence(timeout: 10), id, file: file, line: line)
        for _ in 0..<8 {
            if target.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(target.isHittable, "Reachable \(id)", file: file, line: line)
        target.tap()
    }

    private func attach(_ name: String) {
        let image = XCTAttachment(screenshot: app.screenshot())
        image.name = "\(name)--uptime-\(String(format: "%.3f", ProcessInfo.processInfo.systemUptime))"
        image.lifetime = .keepAlways
        add(image)
    }

    private func attachHierarchy(_ name: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name
        tree.lifetime = .keepAlways
        add(tree)
    }

    private func startComputer(_ difficulty: String) {
        tap("difficulty-\(difficulty)")
        tap("play-computer")
    }

    private func finishGame(local: Bool = false) {
        var humanMoves = 0
        for _ in 0..<10 {
            eventually("Result, Local Ready, or a legal human square") {
                self.result.exists || (local && self.app.buttons["ready"].exists) || self.legalCells.firstMatch.exists
            }
            if result.exists { break }
            if local && app.buttons["ready"].exists {
                XCTAssertFalse(element("game-board").exists, "Private handoff omits board")
                XCTAssertFalse(element("bag").exists, "Private handoff omits bag")
                tap("ready")
                eventually("Ready reveals before enabling placement") { self.result.exists || self.legalCells.firstMatch.exists }
            }
            if result.exists { break }
            let target = legalCells.firstMatch
            if !target.isHittable { app.swipeUp() }
            XCTAssertTrue(target.isHittable)
            target.tap()
            humanMoves += 1
        }
        XCTAssertGreaterThan(humanMoves, 0, "Player made real legal moves")
        XCTAssertTrue(result.waitForExistence(timeout: 25), "Complete game reaches a result")
        XCTAssertTrue(element("final-board").exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'")).count, 0, "Result isolates the underlying board")
        XCTAssertTrue(app.buttons["rematch"].isHittable)
    }

    private func computerGameAndRematch(_ difficulty: String) {
        launch()
        startComputer(difficulty)
        finishGame()
        attach("\(difficulty)-first-result")
        tap("rematch")
        // The alternate starter is Computer. The real worker must settle before
        // the first human square becomes enabled on this fresh board.
        finishGame()
        attach("\(difficulty)-rematch-result")
        app.buttons["Home"].tap()
        XCTAssertTrue(element("home-wordmark").waitForExistence(timeout: 10))
    }

    func testCompleteClassicWinRematchAndTerminalRelaunch() {
        launch()
        tap("start-classic")
        for index in [0, 3, 1, 4, 2] { cell(index).tap() }
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Player 1 wins"].exists)
        let summary = element("final-board").label
        attach("classic-player-one-win")
        app.terminate()
        launch(reset: false)
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        XCTAssertEqual(element("final-board").label, summary)
        tap("rematch")
        XCTAssertEqual(cell(0).label, "Row 1, column 1, empty")
        for index in [0, 3, 1, 4, 2] { cell(index).tap() }
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Player 2 wins"].exists, "Player 2 starts rematch with O")
        XCTAssertTrue(element("final-board").label.contains("Row 1: O, O, O"))
        attach("classic-player-two-rematch-win")
    }

    func testCompleteClassicDraw() {
        launch()
        tap("start-classic")
        for index in [0, 1, 2, 4, 3, 5, 7, 6, 8] { cell(index).tap() }
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Draw"].exists)
        attach("classic-draw")
    }

    func testCompleteLocalGameAndRematchWithPrivacy() {
        launch()
        tap("start-local")
        finishGame(local: true)
        attach("local-first-result")
        tap("rematch")
        XCTAssertTrue(app.staticTexts["Player 2, you're up."].waitForExistence(timeout: 10))
        finishGame(local: true)
        attach("local-rematch-result")
    }

    func testCompleteEasyGameAndRematch() { computerGameAndRematch("easy") }
    func testCompleteMediumGameAndRematch() { computerGameAndRematch("medium") }
    func testCompleteHardGameAndRematch() { computerGameAndRematch("hard") }

    func testCompleteMCTSGameAndRematch() {
        launch()
        tap("open-aiLab")
        tap("play-mcts")
        finishGame()
        attach("mcts-first-result")
        tap("rematch")
        finishGame()
        attach("mcts-rematch-result")
    }

    func testCompleteQLearningGameAndRematch() {
        launch()
        tap("open-aiLab")
        tap("play-qlearning")
        finishGame()
        attach("qlearning-first-result")
        tap("rematch")
        finishGame()
        attach("qlearning-rematch-result")
    }

    func testSettingsPersistAcrossNavigationBackgroundAndRelaunch() {
        launch()
        tap("open-settings")
        attachHierarchy("settings-native-controls")
        XCTAssertEqual(app.switches.matching(NSPredicate(format: "identifier BEGINSWITH 'setting-'")).count, 3, "One identified native switch per preference")
        for id in ["setting-sound", "setting-haptics"] {
            XCTAssertEqual(app.switches[id].value as? String, "1")
            app.switches[id].tap()
            eventually("\(id) changes once") { self.app.switches[id].value as? String == "0" }
        }
        app.switches["setting-motion"].tap()
        tap("theme-dark")
        XCUIDevice.shared.press(.home)
        app.activate()
        eventually("Settings remain after scene reactivation") { self.app.switches["setting-motion"].exists }
        app.terminate()
        launch(reset: false)
        XCTAssertTrue(element("settings-screen").waitForExistence(timeout: 10))
        XCTAssertEqual(app.switches["setting-sound"].value as? String, "0")
        XCTAssertEqual(app.switches["setting-haptics"].value as? String, "0")
        XCTAssertEqual(app.switches["setting-motion"].value as? String, "1")
        XCTAssertTrue(app.buttons["theme-dark"].isSelected)
        tap("back")
        tap("open-settings")
        XCTAssertTrue(app.buttons["theme-dark"].isSelected)
        attach("settings-restored")
    }

    func testClassicBoardSurvivesSceneInterruptionAndRelaunch() {
        launch()
        tap("start-classic")
        cell(4).tap()
        cell(0).tap()
        let labels = (0..<9).map { cell($0).label }
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
        XCTAssertEqual((0..<9).map { cell($0).label }, labels)
        app.terminate()
        launch(reset: false)
        XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
        XCTAssertEqual((0..<9).map { cell($0).label }, labels)
        XCTAssertFalse(cell(4).isEnabled)
        XCTAssertFalse(cell(0).isEnabled)
        cell(8).tap()
        XCTAssertEqual(cell(8).label, "Row 3, column 3, X")
    }

    func testLocalHandoffAndHeldPieceSurviveRelaunchWithoutRedraw() {
        launch()
        tap("start-local")
        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(reset: false)
        XCTAssertTrue(app.buttons["ready"].waitForExistence(timeout: 10))
        XCTAssertFalse(element("bag").exists)
        tap("ready")
        eventually("Human placement") { self.cell(0).exists && self.cell(0).isEnabled }
        let odds = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'percent next draw'")).allElementsBoundByIndex.map(\.label)
        let piece = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Piece in hand:'")).firstMatch.label
        app.terminate()
        launch(reset: false)
        eventually("Restored held piece is playable") { self.cell(0).exists && self.cell(0).isEnabled }
        XCTAssertEqual(app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Piece in hand:'")).firstMatch.label, piece)
        XCTAssertEqual(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'percent next draw'")).allElementsBoundByIndex.map(\.label), odds)
        cell(0).tap()
        XCTAssertTrue(app.buttons["ready"].waitForExistence(timeout: 10))
        XCTAssertFalse(element("game-board").exists)
    }

    func testBoardOrderTargetsAndOccupiedCellGate() {
        launch()
        tap("start-classic")
        let frames = (0..<9).map { cell($0).frame }
        let identifiers = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'")).allElementsBoundByIndex.map(\.identifier)
        XCTAssertEqual(identifiers, (0..<9).map { "cell-\($0)" }, "Row-major accessibility order")
        for (index, frame) in frames.enumerated() {
            XCTAssertGreaterThanOrEqual(frame.width, 48)
            XCTAssertGreaterThanOrEqual(frame.height, 48)
            XCTAssertEqual(frame.width, frame.height, accuracy: 0.5)
            XCTAssertEqual(frame.width, frames[0].width, accuracy: 0.5)
            XCTAssertEqual(cell(index).label, "Row \(index / 3 + 1), column \(index % 3 + 1), empty")
        }
        cell(4).doubleTap()
        XCTAssertEqual(cell(4).label, "Row 2, column 2, X")
        XCTAssertFalse(cell(4).isEnabled)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-' AND enabled == YES")).count, 8)
        XCTAssertEqual((0..<9).map { cell($0).frame }, frames, "Press and placement keep hitboxes fixed")
        attach("board-target-geometry")
    }

    func testSelectedDifficultySurvivesSupportingPageNavigation() {
        launch()
        tap("difficulty-hard")
        XCTAssertTrue(app.buttons["difficulty-hard"].isSelected)
        tap("open-settings")
        tap("back")
        XCTAssertTrue(app.buttons["difficulty-hard"].isSelected)
        tap("open-howTo")
        tap("back")
        XCTAssertTrue(app.buttons["difficulty-hard"].isSelected)
    }

    func testHomeHeadingExplanationAndTutorialReachability() {
        launch()
        XCTAssertEqual(element("home-wordmark").label, "Pic-Pac-Poe")
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "home-wordmark").count, 1)
        XCTAssertEqual(element("home-illustration").label, "A random X or O is drawn from the bag, then placed on the board.")
        tap("difficulty-medium")
        XCTAssertTrue(app.buttons["difficulty-medium"].isSelected)
        tap("open-howTo")
        XCTAssertTrue(app.staticTexts["You're not X. You're not O."].exists)
        for _ in 0..<3 { app.swipeUp() }
        XCTAssertTrue(app.staticTexts["Regular tic-tac-toe. Player 1 is X; Player 2 is O."].isHittable)
        attach("tutorial-reachable")
    }

    func testLargestDynamicTypeKeepsActionsReachable() {
        let category = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        launch(extra: category)
        XCTAssertEqual(element("home-wordmark").label, "Pic-Pac-Poe")
        tap("open-settings")
        XCTAssertEqual(app.switches.matching(NSPredicate(format: "identifier BEGINSWITH 'setting-'")).count, 3)
        tap("theme-dark")
        XCTAssertTrue(app.buttons["theme-dark"].isSelected)
        attach("accessibility-largest-settings")
        app.terminate()
        app.launchArguments = ["-screenshot-scenario", "result", "-screenshot-theme", "dark"] + category
        app.launch()
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        tap("rematch")
        XCTAssertTrue(element("game-screen").waitForExistence(timeout: 10))
        attach("accessibility-largest-rematch")
    }

    func testLandscapeAndLargestTextKeepLastSquareAndBagReachable() {
        defer { XCUIDevice.shared.orientation = .portrait }
        for largeText in [false, true] {
            XCUIDevice.shared.orientation = largeText ? .portrait : .landscapeLeft
            app.launchArguments = ["-screenshot-scenario", "human-placement", "-screenshot-theme", "dark"]
            if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
            app.launch()
            XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
            XCTAssertEqual(app.frame.width > app.frame.height, !largeText, "Actual landscape or portrait viewport")
            tap("cell-8")
            XCTAssertEqual(cell(8).label, "Row 3, column 3, X")
            let bagFooter = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'pieces remain in the shared bag'")).firstMatch
            for _ in 0..<8 {
                if bagFooter.isHittable { break }
                app.swipeUp()
            }
            XCTAssertTrue(bagFooter.isHittable)
            XCTAssertGreaterThanOrEqual(cell(8).frame.width, 48)
            attach(largeText ? "largest-type-last-square-and-bag" : "landscape-last-square-and-bag")
            app.terminate()
        }
    }

    func testAutomatedAccessibilityAuditBothThemes() throws {
        for theme in ["dark", "light"] {
            for scenario in ["home", "classic", "human-placement", "computer-targeting", "settings", "how-to", "ai-lab", "local-handoff", "result"] {
                app.launchArguments = ["-screenshot-scenario", scenario, "-screenshot-theme", theme, "-snapshot-home-time", "0"]
                app.launch()
                let screen = ["home": "home-wordmark", "settings": "settings-screen", "how-to": "how-to-screen", "ai-lab": "ai-lab-screen"][scenario] ?? "game-screen"
                XCTAssertTrue(element(screen).waitForExistence(timeout: 10))
                let scrolls = ["home", "settings", "how-to", "ai-lab", "human-placement"].contains(scenario)
                for viewport in (scrolls ? ["top", "bottom"] : ["top"]) {
                    if viewport == "bottom" { for _ in 0..<3 { app.swipeUp() } }
                    try app.performAccessibilityAudit(for: [.contrast, .hitRegion, .sufficientElementDescription, .textClipped, .trait]) { issue in
                        let detail = XCTAttachment(string: "\(scenario) / \(theme) / \(viewport): \(issue.detailedDescription)\n\(issue.element?.debugDescription ?? "No associated element")")
                        detail.name = "Accessibility audit finding"
                        detail.lifetime = .keepAlways
                        self.add(detail)
                        return false // Every finding remains a failure; no blanket exclusions.
                    }
                }
            }
        }
    }

    func testHeldFixturesKeepLockedStagesPrivateInBothThemes() {
        for theme in ["dark", "light"] {
            for scenario in ["local-handoff", "local-reveal", "computer-reveal", "computer-thinking", "computer-targeting", "computer-placement", "computer-settled", "result"] {
                app.launchArguments = ["-screenshot-scenario", scenario, "-screenshot-theme", theme]
                app.launch()
                let isHandoff = scenario == "local-handoff"
                let isModal = ["local-reveal", "computer-reveal", "result"].contains(scenario)
                XCTAssertTrue((isHandoff ? app.buttons["ready"] : element("game-screen")).waitForExistence(timeout: 10))
                let boardCells = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'"))
                if isHandoff || isModal {
                    XCTAssertEqual(boardCells.count, 0, "\(scenario) hides private background semantics")
                    XCTAssertFalse(element("bag").exists)
                } else {
                    XCTAssertEqual(boardCells.count, 9)
                    XCTAssertEqual(boardCells.matching(NSPredicate(format: "enabled == YES")).count, 0, "\(scenario) rejects every cell")
                    XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS 'Computer'")).count > 0)
                }
                attach("locked-\(scenario)-\(theme)")
                app.terminate()
            }
        }
    }

    func testGovernedLiveHumanAndComputerSequence() {
        // Tenfold essential holds make every native rendered stage inspectable
        // through XCTest. The coordinator, public worker, RNG and legal human
        // input are real; this is explicitly not a normal-speed timing benchmark.
        launch(extra: ["-ui-test-clock-multiplier", "10"])
        startComputer("hard")
        XCTAssertTrue(element("reveal-dialog").waitForExistence(timeout: 12))
        attach("sequence-clock10-human-reveal")
        eventually("Human placement after full reveal", timeout: 15) { self.legalCells.firstMatch.exists }
        attach("sequence-clock10-human-playing")
        cell(0).tap()
        attach("sequence-clock10-human-placed-computer-turn-start")
        let computerReveal = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Computer drew'")).firstMatch
        XCTAssertTrue(computerReveal.waitForExistence(timeout: 12))
        XCTAssertEqual(legalCells.count, 0)
        attach("sequence-clock10-computer-reveal")
        let target = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Computer selected'")).firstMatch
        XCTAssertTrue(target.waitForExistence(timeout: 15))
        let targetID = target.identifier
        XCTAssertFalse(target.isEnabled)
        attach("sequence-clock10-computer-target")
        eventually("Selected target receives the actual piece", timeout: 8) { self.app.buttons[targetID].label.hasPrefix("Computer placed") }
        XCTAssertFalse(result.exists)
        attach("sequence-clock10-computer-place")
        let settled = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Move placed'")).firstMatch
        XCTAssertTrue(settled.waitForExistence(timeout: 8))
        XCTAssertEqual(legalCells.count, 0)
        XCTAssertFalse(result.exists)
        attach("sequence-clock10-computer-settle")
    }

    func testHumanTurnStartAndRevealResumeWithoutRedraw() {
        for scenario in ["human-turn-start", "human-reveal"] {
            session = UUID().uuidString
            launch(extra: ["-ui-test-initial-scenario", scenario, "-ui-test-clock-multiplier", "20"])
            if scenario == "human-turn-start" {
                XCTAssertTrue(element("bag").waitForExistence(timeout: 10))
                XCTAssertTrue(element("X, 5 remaining, 50 percent next draw").exists)
            } else {
                XCTAssertTrue(element("reveal-dialog").waitForExistence(timeout: 10))
                XCTAssertTrue(app.staticTexts["You drew X"].exists)
                XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'")).count, 0)
            }
            XCUIDevice.shared.press(.home)
            app.terminate()
            launch(reset: false, extra: ["-ui-test-clock-multiplier", "20"])
            if scenario == "human-turn-start" {
                XCTAssertTrue(element("bag").waitForExistence(timeout: 10))
                XCTAssertTrue(element("X, 5 remaining, 50 percent next draw").exists)
            } else {
                XCTAssertTrue(element("reveal-dialog").waitForExistence(timeout: 10))
                XCTAssertTrue(app.staticTexts["You drew X"].exists)
            }
            attach("\(scenario)-restored")
            eventually("Restored human stage unlocks once", timeout: 30) { self.legalCells.firstMatch.exists }
            if scenario == "human-reveal" {
                XCTAssertTrue(element("X, 4 remaining, 44 percent next draw").exists)
                XCTAssertTrue(element("O, 5 remaining, 56 percent next draw").exists)
            }
            cell(0).tap()
            XCTAssertFalse(cell(0).isEnabled)
            attach("\(scenario)-single-placement")
            app.terminate()
        }
    }

    func testLeavingComputerSequenceCannotMutateNewClassicGame() {
        launch(extra: ["-ui-test-initial-scenario", "human-placement", "-ui-test-clock-multiplier", "20"])
        XCTAssertTrue(cell(0).waitForExistence(timeout: 10))
        cell(0).tap()
        tap("back")
        tap("start-classic")
        cell(4).tap()
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(cell(4).waitForExistence(timeout: 10))
        XCTAssertEqual(cell(4).label, "Row 2, column 2, X")
        XCTAssertEqual(legalCells.count, 8)
        XCTAssertFalse(element("bag").exists)
    }

    func testSelectedComputerTargetSurvivesRelaunchAndCommitsOnce() {
        launch(extra: ["-ui-test-initial-scenario", "computer-targeting", "-ui-test-clock-multiplier", "20"])
        XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
        XCTAssertEqual(cell(8).label, "Computer selected row 3, column 3")
        XCTAssertFalse(cell(8).isEnabled)
        attach("target-before-relaunch")
        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(reset: false, extra: ["-ui-test-clock-multiplier", "20"])
        XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
        XCTAssertEqual(cell(8).label, "Computer selected row 3, column 3")
        attach("target-after-relaunch")
        eventually("Exact selected target commits once", timeout: 12) { self.cell(8).label == "Computer placed O in row 3, column 3" }
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-' AND enabled == YES")).count, 0)
        attach("target-committed-once")
        // Relaunch from an already committed presentation. The target remains O;
        // reconstruction must not replay the placement or draw another symbol.
        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(reset: false, extra: ["-ui-test-clock-multiplier", "20"])
        XCTAssertTrue(cell(8).waitForExistence(timeout: 10))
        XCTAssertEqual(cell(8).label, "Computer placed O in row 3, column 3")
        attach("committed-target-after-relaunch")
    }

    func testTerminalComputerWinSettlesBeforeResultAcrossBackground() {
        terminalSettlement("terminal-settling-win", expected: "Computer wins")
    }

    func testTerminalComputerDrawSettlesBeforeResultAcrossRelaunch() {
        terminalSettlement("terminal-settling-draw", expected: "Draw", relaunch: true)
    }

    private func terminalSettlement(_ scenario: String, expected: String, relaunch: Bool = false) {
        let extra = ["-ui-test-initial-scenario", scenario, "-ui-test-clock-multiplier", "20"]
        launch(extra: extra)
        XCTAssertTrue(element("game-board").waitForExistence(timeout: 10))
        XCTAssertFalse(result.exists, "Committed terminal move remains visible through settlement")
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-' AND enabled == YES")).count, 0)
        attach("\(scenario)-before-interruption")
        XCUIDevice.shared.press(.home)
        if relaunch { app.terminate(); launch(reset: false, extra: ["-ui-test-clock-multiplier", "20"]) }
        else { app.activate() }
        XCTAssertTrue(element("game-board").waitForExistence(timeout: 10))
        XCTAssertFalse(result.exists, "Restoration resumes the entire readable settlement hold")
        attach("\(scenario)-restored-settlement")
        XCTAssertTrue(result.waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts[expected].exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'cell-'")).count, 0)
        attach("\(scenario)-result")
    }
}
