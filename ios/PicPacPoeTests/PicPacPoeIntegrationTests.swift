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
    func testDebugCaptureSnapshotsRestoreWithoutLosingStage() async throws {
        #if DEBUG
        for scenario in ["home", "classic", "human-placement", "local-handoff", "local-reveal", "computer-targeting", "computer-settled", "computer-placement", "computer-reveal", "result", "settings", "how-to", "ai-lab"] {
            let expected = try DebugLaunchConfiguration.snapshot(for: scenario)
            let coordinator = try XCTUnwrap(DebugLaunchConfiguration.makeCoordinator(arguments: ["-screenshot-scenario", scenario, "-screenshot-theme", "dark"]))
            await coordinator.restore()
            XCTAssertEqual(coordinator.state, expected.state, scenario)
            XCTAssertEqual(coordinator.settings.theme, .dark, scenario)
            XCTAssertNil(coordinator.feedbackEvent, scenario)
        }
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
