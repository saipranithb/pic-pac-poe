import AppKit
import Foundation

func pixels(_ path: String) throws -> (Int, Int, [UInt8]) {
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil), let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { throw NSError(domain: "PNG", code: 1) }
    let w = image.width, h = image.height
    var bytes = [UInt8](repeating: 0, count: w*h*4)
    let ok = bytes.withUnsafeMutableBytes { raw -> Bool in
        guard let context = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w*4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
        context.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h)); return true
    }
    guard ok else { throw NSError(domain: "Pixels", code: 1) }
    return (w,h,bytes)
}
let args = CommandLine.arguments
guard args.count >= 3 else { fatalError("Usage: snapshot-pixels inspect image | compare left right | sheet plan output") }
if args[1] == "inspect" {
    let value = try pixels(args[2])
    var colors = Set<UInt32>()
    for y in stride(from: value.1 / 4, to: value.1 * 3 / 4, by: 4) {
        for x in stride(from: value.0 / 8, to: value.0 * 7 / 8, by: 4) {
            let i = (y * value.0 + x) * 4
            colors.insert(UInt32(value.2[i]) << 16 | UInt32(value.2[i+1]) << 8 | UInt32(value.2[i+2]))
        }
    }
    let opaque = stride(from: 3, to: value.2.count, by: 4).allSatisfy { value.2[$0] == 255 }
    print("{\"width\":\(value.0),\"height\":\(value.1),\"centralDistinctColors\":\(colors.count),\"nonblank\":\(colors.count >= 20),\"opaque\":\(opaque)}")
} else if args[1] == "compare" {
    guard args.count == 4 else { fatalError("compare requires two image paths") }
    let a = try pixels(args[2]), b = try pixels(args[3])
    guard a.0 == b.0 && a.1 == b.1 else { print("{\"pass\":false,\"agreement\":0,\"error\":\"dimension mismatch\"}"); exit(0) }
    var agreed = 0, maxDelta = 0, sum = 0
    for i in stride(from: 0, to: a.2.count, by: 4) {
        var pixelMax = 0
        for channel in 0..<4 { let d = abs(Int(a.2[i+channel])-Int(b.2[i+channel])); pixelMax = max(pixelMax,d); sum += d }
        if pixelMax <= 2 { agreed += 1 }; maxDelta = max(maxDelta,pixelMax)
    }
    let agreement = Double(agreed)/Double(a.0*a.1)
    print("{\"pass\":\(agreement >= 0.995),\"agreement\":\(agreement),\"maxChannelDelta\":\(maxDelta),\"meanChannelDelta\":\(Double(sum)/Double(a.0*a.1*4)),\"width\":\(a.0),\"height\":\(a.1)}")
} else if args[1] == "sheet" {
    guard args.count == 4 else { fatalError("sheet requires a JSON plan and output path") }
    let entries = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: args[2]))) as! [[String:String]]
    let columns = 6, tileW = 240, tileH = 570, rows = (entries.count+columns-1)/columns
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: columns*tileW, pixelsHigh: rows*tileH, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSColor(calibratedWhite: 0.94, alpha: 1).setFill(); NSRect(x:0,y:0,width:columns*tileW,height:rows*tileH).fill()
    for (index, entry) in entries.enumerated() {
        guard let image = NSImage(contentsOfFile: entry["path"]!) else { fatalError("missing image") }
        let x = CGFloat(index%columns*tileW), y = CGFloat((rows-1-index/columns)*tileH)
        let scale = min(CGFloat(tileW-16)/image.size.width, CGFloat(tileH-56)/image.size.height)
        let size = NSSize(width:image.size.width*scale,height:image.size.height*scale)
        image.draw(in:NSRect(x:x+(CGFloat(tileW)-size.width)/2,y:y+8,width:size.width,height:size.height))
        let labelRect = NSRect(x: x + 8, y: y + CGFloat(tileH - 46), width: CGFloat(tileW - 16), height: 42)
        let attributes: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 11), .foregroundColor: NSColor.black]
        (entry["label"]! as NSString).draw(in: labelRect, withAttributes: attributes)
    }
    NSGraphicsContext.restoreGraphicsState()
    try rep.representation(using:.png,properties:[:])!.write(to:URL(fileURLWithPath:args[3]))
} else { fatalError("unknown command") }
