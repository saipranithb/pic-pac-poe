// Native raster adaptation of the canonical Android icon source paths.
// Copyright 2026 Pic-Pac-Poe contributors. Apache-2.0; see ../../LICENSE.
// No Android adaptive mask or screenshot is imported; iOS applies its own mask.
import AppKit
import Foundation

struct IconError: Error, CustomStringConvertible { let description: String }
final class VectorPaths: NSObject, XMLParserDelegate {
    var paths: [[String: String]] = []
    var validViewport = false
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String: String]) {
        if elementName == "vector" { validViewport = attributes["android:viewportWidth"] == "108" && attributes["android:viewportHeight"] == "108" }
        if elementName == "path" { paths.append(attributes) }
    }
}
func color(_ hex: String) throws -> CGColor {
    guard hex.hasPrefix("#"), let value = UInt32(hex.dropFirst(), radix: 16), [7, 9].contains(hex.count) else { throw IconError(description: "Unsupported color \(hex)") }
    let alpha = hex.count == 9 ? CGFloat(value >> 24) / 255 : 1
    return CGColor(srgbRed: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255, blue: CGFloat(value & 255) / 255, alpha: alpha)
}
func path(_ data: String) throws -> CGPath {
    let regex = try NSRegularExpression(pattern: "[A-Za-z]|[-+]?[0-9]+(?:\\.[0-9]*)?")
    let tokens = regex.matches(in: data, range: NSRange(data.startIndex..., in: data)).map { String(data[Range($0.range, in: data)!]) }
    let result = CGMutablePath()
    var current = CGPoint.zero, start = CGPoint.zero
    var index = 0, command = ""
    func number() throws -> CGFloat {
        guard index < tokens.count, let value = Double(tokens[index]) else { throw IconError(description: "Invalid path coordinate") }
        index += 1; return CGFloat(value)
    }
    func point(_ relative: Bool) throws -> CGPoint {
        let x = try number(), y = try number()
        return CGPoint(x: x + (relative ? current.x : 0), y: y + (relative ? current.y : 0))
    }
    while index < tokens.count {
        if tokens[index].first!.isLetter { command = tokens[index]; index += 1 }
        let relative = command == command.lowercased()
        switch command.uppercased() {
        case "M": current = try point(relative); result.move(to: current); start = current; command = relative ? "l" : "L"
        case "L": current = try point(relative); result.addLine(to: current)
        case "H": current.x = try number() + (relative ? current.x : 0); result.addLine(to: current)
        case "V": current.y = try number() + (relative ? current.y : 0); result.addLine(to: current)
        case "C":
            let first = try point(relative), second = try point(relative), end = try point(relative)
            result.addCurve(to: end, control1: first, control2: second); current = end
        case "Z": result.closeSubpath(); current = start; command = ""
        default: throw IconError(description: "Unsupported icon command '\(command)'; review source changes deliberately")
        }
    }
    return result
}
guard CommandLine.arguments.count == 3 else { throw IconError(description: "Usage: GenerateNativeAppIcon <repository-root> <output.png>") }
let root = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let side = 1024
let space = CGColorSpace(name: CGColorSpace.sRGB)!
guard let context = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4, space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { throw IconError(description: "Cannot create opaque icon context") }
context.translateBy(x: 0, y: CGFloat(side)); context.scaleBy(x: CGFloat(side) / 108, y: -CGFloat(side) / 108)
for filename in ["ic_launcher_background.xml", "ic_launcher_foreground.xml"] {
    let source = root.appendingPathComponent("app/src/main/res/drawable/" + filename)
    let loader = VectorPaths(), parser = XMLParser(contentsOf: source)!
    parser.delegate = loader
    guard parser.parse(), loader.validViewport, !loader.paths.isEmpty else { throw IconError(description: "Invalid canonical vector \(filename)") }
    for attributes in loader.paths {
        let shape = try path(attributes["android:pathData"]!)
        let fill = try color(attributes["android:fillColor"] ?? "#00000000")
        if fill.alpha > 0 { context.addPath(shape); context.setFillColor(fill); context.fillPath() }
        if let stroke = attributes["android:strokeColor"] {
            context.addPath(shape); context.setStrokeColor(try color(stroke))
            context.setLineWidth(CGFloat(Double(attributes["android:strokeWidth"] ?? "0")!))
            context.setLineCap(attributes["android:strokeLineCap"] == "round" ? .round : .butt)
            context.setLineJoin(attributes["android:strokeLineJoin"] == "round" ? .round : .miter)
            context.strokePath()
        }
    }
}
let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
guard let png = bitmap.representation(using: .png, properties: [:]) else { throw IconError(description: "Cannot encode native icon") }
try png.write(to: output)
print("Rendered canonical Android icon paths to opaque 1024×1024 native iOS app icon: \(output.path)")
