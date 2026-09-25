import AppKit
import AVFoundation
import Foundation

@main struct ExtractVideoFrames {
    static func main() async throws {
        let arguments = CommandLine.arguments
        guard arguments.count == 4 else { fatalError("usage: extract-video input.mov output-directory comma-separated-seconds") }
        let input = URL(fileURLWithPath: arguments[1])
        let output = URL(fileURLWithPath: arguments[2], isDirectory: true)
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        let asset = AVURLAsset(url: input)
        let duration = try await asset.load(.duration)
        let tracks = try await asset.loadTracks(withMediaType: .video)
        guard let track = tracks.first else { fatalError("No video track") }
        let size = try await track.load(.naturalSize)
        let transform = try await track.load(.preferredTransform)
        let rate = try await track.load(.nominalFrameRate)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        var frames = [[String: Any]]()
        for (index, requested) in arguments[3].split(separator: ",").compactMap({ Double($0) }).enumerated() {
            guard requested >= 0, requested < duration.seconds else { fatalError("Requested time outside video") }
            let (cgImage, actualTime) = try await generator.image(at: CMTime(seconds: requested, preferredTimescale: 60000))
            let name = String(format: "frame-%03d.png", index)
            let bitmap = NSBitmapImageRep(cgImage: cgImage)
            guard let png = bitmap.representation(using: .png, properties: [:]) else { fatalError("PNG encode failed") }
            try png.write(to: output.appendingPathComponent(name))
            frames.append(["path": name, "requestedSeconds": requested, "actualSeconds": actualTime.seconds, "width": cgImage.width, "height": cgImage.height, "noCrop": true])
        }
        let report: [String: Any] = ["input": input.lastPathComponent, "durationSeconds": duration.seconds,
            "encodedWidth": size.width, "encodedHeight": size.height, "nominalFrameRate": rate,
            "preferredTransform": [transform.a, transform.b, transform.c, transform.d, transform.tx, transform.ty],
            "extraction": "AVAssetImageGenerator, preferred track orientation applied, zero requested tolerance; returned actual timestamps recorded, no resizing or cropping", "frames": frames]
        try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: output.appendingPathComponent("frames.json"))
    }
}
