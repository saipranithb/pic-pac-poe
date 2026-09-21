// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "PicPacKit",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "PicPacCore", targets: ["PicPacCore"]),
        .library(name: "PicPacAI", targets: ["PicPacAI"]),
        .library(name: "PicPacPresentation", targets: ["PicPacPresentation"]),
    ],
    targets: [
        .target(name: "PicPacCore"),
        .target(
            name: "PicPacAI",
            dependencies: ["PicPacCore"],
            resources: [.copy("Resources/picpac_rl_policy_v1.bin")]
        ),
        .target(
            name: "PicPacPresentation",
            dependencies: ["PicPacCore"]
        ),
        .testTarget(
            name: "PicPacKitTests",
            dependencies: ["PicPacCore", "PicPacAI", "PicPacPresentation"],
            resources: [.copy("Resources/golden-fixtures.json")]
        ),
    ],
    swiftLanguageModes: [.v6]
)
