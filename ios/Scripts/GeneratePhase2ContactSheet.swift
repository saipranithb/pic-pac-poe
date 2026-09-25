import AppKit
import Foundation

private struct EvidenceManifest: Decodable {
    let schemaVersion: Int
    let generatedAt: String
    let source: Source
    let toolchain: Toolchain
    let simulator: Simulator
    let captures: [Capture]

    struct Source: Decodable {
        let commit: String
        let dirty: Bool
    }

    struct Toolchain: Decodable {
        let xcode: String
        let simulatorSDK: String
    }

    struct Simulator: Decodable {
        let deviceType: String
        let runtime: String
        let osVersion: String
    }

    struct Capture: Decodable {
        let scenario: String
        let theme: String
        let relativePath: String
        let capturedAt: String
        let sha256: String
        let width: Int
        let height: Int
        let settleSeconds: Double
        let canonical: Canonical
    }

    struct Canonical: Decodable {
        let relativePath: String
        let theme: String
        let kind: String
        let sha256: String
        let comparison: String
        let note: String
    }
}

private enum SheetError: Error, CustomStringConvertible {
    case usage(String)
    case invalidManifest(String)
    case missingFile(String)
    case unreadableImage(String)
    case drawing(String)

    var description: String {
        switch self {
        case .usage(let message), .invalidManifest(let message), .missingFile(let message),
             .unreadableImage(let message), .drawing(let message):
            return message
        }
    }
}

private struct Arguments {
    let manifest: URL
    let repositoryRoot: URL
    let output: URL

    init(_ values: [String]) throws {
        var parsed: [String: String] = [:]
        var index = 1
        while index < values.count {
            let key = values[index]
            guard ["--manifest", "--repository-root", "--output"].contains(key), index + 1 < values.count else {
                throw SheetError.usage("Usage: GeneratePhase2ContactSheet --manifest <json> --repository-root <path> --output <png>")
            }
            guard parsed[key] == nil else { throw SheetError.usage("Duplicate argument: \(key)") }
            parsed[key] = values[index + 1]
            index += 2
        }
        guard let manifest = parsed["--manifest"],
              let repositoryRoot = parsed["--repository-root"],
              let output = parsed["--output"] else {
            throw SheetError.usage("Usage: GeneratePhase2ContactSheet --manifest <json> --repository-root <path> --output <png>")
        }
        self.manifest = URL(fileURLWithPath: manifest).standardizedFileURL
        self.repositoryRoot = URL(fileURLWithPath: repositoryRoot).standardizedFileURL
        self.output = URL(fileURLWithPath: output).standardizedFileURL
    }
}

private let scenarioOrder = [
    "home",
    "human-placement",
    "local-handoff",
    "local-reveal",
    "computer-targeting",
    "computer-settled",
    "result",
    "settings",
    "how-to",
    "ai-lab",
]

private func fail(_ error: Error) -> Never {
    let message = String(describing: error)
    FileHandle.standardError.write(Data("contact-sheet: \(message)\n".utf8))
    exit(1)
}

private func loadImage(at url: URL) throws -> NSImage {
    guard FileManager.default.fileExists(atPath: url.path) else {
        throw SheetError.missingFile("Missing image: \(url.path)")
    }
    guard let image = NSImage(contentsOf: url), image.size.width > 0, image.size.height > 0 else {
        throw SheetError.unreadableImage("Cannot decode image: \(url.path)")
    }
    return image
}

private func rectFromTop(canvasHeight: CGFloat, x: CGFloat, top: CGFloat, width: CGFloat, height: CGFloat) -> NSRect {
    NSRect(x: x, y: canvasHeight - top - height, width: width, height: height)
}

private func fittedRect(for image: NSImage, in slot: NSRect) -> NSRect {
    let source = image.size
    let scale = min(slot.width / source.width, slot.height / source.height)
    let width = floor(source.width * scale)
    let height = floor(source.height * scale)
    return NSRect(
        x: floor(slot.midX - width / 2),
        y: floor(slot.maxY - height),
        width: width,
        height: height
    )
}

private func drawText(
    _ text: String,
    in rect: NSRect,
    font: NSFont,
    color: NSColor,
    alignment: NSTextAlignment = .left,
    lineBreak: NSLineBreakMode = .byTruncatingTail
) {
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = alignment
    paragraph.lineBreakMode = lineBreak
    paragraph.maximumLineHeight = ceil(font.pointSize * 1.3)
    (text as NSString).draw(
        in: rect,
        withAttributes: [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraph,
        ]
    )
}

private func render(arguments: Arguments) throws {
    let manifestData = try Data(contentsOf: arguments.manifest)
    let manifest = try JSONDecoder().decode(EvidenceManifest.self, from: manifestData)
    guard manifest.schemaVersion == 1 else {
        throw SheetError.invalidManifest("Unsupported evidence manifest schema \(manifest.schemaVersion)")
    }

    let manifestDirectory = arguments.manifest.deletingLastPathComponent()
    var keyed: [String: EvidenceManifest.Capture] = [:]
    for capture in manifest.captures {
        let key = "\(capture.scenario)|\(capture.theme)"
        guard keyed[key] == nil else { throw SheetError.invalidManifest("Duplicate capture: \(key)") }
        keyed[key] = capture
    }
    for scenario in scenarioOrder {
        for theme in ["dark", "light"] where keyed["\(scenario)|\(theme)"] == nil {
            throw SheetError.invalidManifest("Missing capture: \(scenario) / \(theme)")
        }
    }
    guard keyed.count == scenarioOrder.count * 2 else {
        throw SheetError.invalidManifest("Expected exactly 20 captures, found \(keyed.count)")
    }

    let margin: CGFloat = 30
    let gap: CGFloat = 18
    let columnWidth: CGFloat = 320
    let headerHeight: CGFloat = 152
    let rowHeight: CGFloat = 758
    let imageHeight: CGFloat = 646
    let canvasWidth = Int(margin * 2 + columnWidth * 4 + gap * 3)
    let canvasHeight = Int(headerHeight + CGFloat(scenarioOrder.count) * rowHeight + 34)

    guard let bitmap = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: canvasWidth,
        pixelsHigh: canvasHeight,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bitmapFormat: [],
        bytesPerRow: 0,
        bitsPerPixel: 0
    ), let context = NSGraphicsContext(bitmapImageRep: bitmap) else {
        throw SheetError.drawing("Could not create \(canvasWidth)x\(canvasHeight) bitmap")
    }

    let height = CGFloat(canvasHeight)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = context
    defer { NSGraphicsContext.restoreGraphicsState() }
    context.imageInterpolation = NSImageInterpolation.high

    NSColor(calibratedRed: 0.055, green: 0.055, blue: 0.06, alpha: 1).setFill()
    NSRect(x: 0, y: 0, width: CGFloat(canvasWidth), height: height).fill()

    drawText(
        "Pic-Pac-Poe · Build Phase 2 visual review",
        in: rectFromTop(canvasHeight: height, x: margin, top: 22, width: CGFloat(canvasWidth) - margin * 2, height: 34),
        font: .systemFont(ofSize: 25, weight: .bold),
        color: .white
    )
    let dirtyLabel = manifest.source.dirty ? "dirty worktree" : "clean worktree"
    drawText(
        "iPhone 17 Pro · iOS \(manifest.simulator.osVersion) · \(manifest.toolchain.xcode) · \(dirtyLabel) · \(manifest.source.commit.prefix(12))",
        in: rectFromTop(canvasHeight: height, x: margin, top: 60, width: CGFloat(canvasWidth) - margin * 2, height: 23),
        font: .systemFont(ofSize: 14, weight: .medium),
        color: NSColor(calibratedWhite: 0.76, alpha: 1)
    )
    drawText(
        "Left pair: dark Android reference / iOS. Right pair: light Android reference / iOS. Images are aspect-fit without cropping.",
        in: rectFromTop(canvasHeight: height, x: margin, top: 86, width: CGFloat(canvasWidth) - margin * 2, height: 22),
        font: .systemFont(ofSize: 13, weight: .regular),
        color: NSColor(calibratedWhite: 0.66, alpha: 1)
    )

    let headings = ["Dark · Android", "Dark · iOS", "Light · Android", "Light · iOS"]
    for column in 0..<4 {
        let x = margin + CGFloat(column) * (columnWidth + gap)
        drawText(
            headings[column],
            in: rectFromTop(canvasHeight: height, x: x, top: 119, width: columnWidth, height: 22),
            font: .systemFont(ofSize: 14, weight: .semibold),
            color: .white,
            alignment: .center
        )
    }

    for (row, scenario) in scenarioOrder.enumerated() {
        let rowTop = headerHeight + CGFloat(row) * rowHeight
        let rowRect = rectFromTop(
            canvasHeight: height,
            x: 14,
            top: rowTop + 5,
            width: CGFloat(canvasWidth) - 28,
            height: rowHeight - 10
        )
        let shade = row.isMultiple(of: 2) ? 0.095 : 0.075
        NSColor(calibratedWhite: shade, alpha: 1).setFill()
        NSBezierPath(roundedRect: rowRect, xRadius: 12, yRadius: 12).fill()

        drawText(
            scenario,
            in: rectFromTop(canvasHeight: height, x: margin, top: rowTop + 14, width: CGFloat(canvasWidth) - margin * 2, height: 26),
            font: .systemFont(ofSize: 18, weight: .bold),
            color: .white
        )

        let dark = keyed["\(scenario)|dark"]!
        let light = keyed["\(scenario)|light"]!
        let columns: [(EvidenceManifest.Capture, Bool)] = [(dark, true), (dark, false), (light, true), (light, false)]

        for (column, item) in columns.enumerated() {
            let (capture, isCanonical) = item
            let x = margin + CGFloat(column) * (columnWidth + gap)
            let isProxy = isCanonical && capture.canonical.comparison != "exact-theme"
            let subheading: String
            if isCanonical {
                subheading = isProxy ? "\(capture.canonical.kind) · \(capture.canonical.theme) proxy" : capture.canonical.kind
            } else {
                subheading = "simulator fixture · \(capture.width)×\(capture.height)"
            }
            drawText(
                subheading,
                in: rectFromTop(canvasHeight: height, x: x, top: rowTop + 42, width: columnWidth, height: 20),
                font: .systemFont(ofSize: 11.5, weight: .medium),
                color: isProxy
                    ? NSColor(calibratedRed: 1, green: 0.69, blue: 0.28, alpha: 1)
                    : NSColor(calibratedWhite: 0.68, alpha: 1),
                alignment: .center
            )

            let relativePath = isCanonical ? capture.canonical.relativePath : capture.relativePath
            let url = isCanonical
                ? arguments.repositoryRoot.appendingPathComponent(relativePath)
                : manifestDirectory.appendingPathComponent(relativePath)
            let image = try loadImage(at: url)
            let slot = rectFromTop(
                canvasHeight: height,
                x: x,
                top: rowTop + 68,
                width: columnWidth,
                height: imageHeight
            )
            let destination = fittedRect(for: image, in: slot)
            NSColor(calibratedWhite: 0.02, alpha: 1).setFill()
            destination.fill()
            image.draw(
                in: destination,
                from: NSRect(origin: .zero, size: image.size),
                operation: .sourceOver,
                fraction: 1,
                respectFlipped: false,
                hints: [.interpolation: NSImageInterpolation.high]
            )
            NSColor(calibratedWhite: 0.32, alpha: 1).setStroke()
            let border = NSBezierPath(rect: destination.insetBy(dx: -0.5, dy: -0.5))
            border.lineWidth = 1
            border.stroke()

            let fileLabel = URL(fileURLWithPath: relativePath).lastPathComponent
            drawText(
                fileLabel,
                in: rectFromTop(canvasHeight: height, x: x, top: rowTop + 720, width: columnWidth, height: 18),
                font: .monospacedSystemFont(ofSize: 10.5, weight: .regular),
                color: NSColor(calibratedWhite: 0.62, alpha: 1),
                alignment: .center
            )
        }
    }

    guard let png = bitmap.representation(using: NSBitmapImageRep.FileType.png, properties: [:]) else {
        throw SheetError.drawing("Could not encode contact sheet as PNG")
    }
    try FileManager.default.createDirectory(
        at: arguments.output.deletingLastPathComponent(),
        withIntermediateDirectories: true
    )
    try png.write(to: arguments.output, options: Data.WritingOptions.atomic)
}

do {
    let arguments = try Arguments(CommandLine.arguments)
    try render(arguments: arguments)
} catch {
    fail(error)
}
