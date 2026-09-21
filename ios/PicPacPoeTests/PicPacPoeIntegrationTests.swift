import PicPacPresentation
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
}
