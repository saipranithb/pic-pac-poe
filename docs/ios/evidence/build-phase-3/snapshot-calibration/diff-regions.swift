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

let entries = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [[String:String]]
var results = [[String:Any]]()
for entry in entries {
 let a = try pixels(entry["left"]!), b = try pixels(entry["right"]!)
 precondition(a.0 == b.0 && a.1 == b.1)
 var minX=a.0, minY=a.1, maxX = -1, maxY = -1, differing=0, bottom=0, aboveBottom=0
 var anyMinY=a.1,anyMaxY = -1
 for i in stride(from:0,to:a.2.count,by:4) {
  let delta=(0..<4).map { abs(Int(a.2[i+$0])-Int(b.2[i+$0])) }.max()!
  let x=(i/4)%a.0,y=(i/4)/a.0
  if delta > 0 { anyMinY=min(anyMinY,y);anyMaxY=max(anyMaxY,y) }
  if delta > 2 { differing += 1;minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y);if y >= a.1-120 {bottom+=1}else{aboveBottom+=1} }
 }
 results.append(["path":entry["path"]!,"width":a.0,"height":a.1,"pixelsBeyondTolerance":differing,"boundingBox":[minX,minY,maxX,maxY],"anyDifferenceYRange":[anyMinY,anyMaxY],"last120RowsBeyondTolerance":bottom,"aboveLast120RowsBeyondTolerance":aboveBottom])
}
let data=try JSONSerialization.data(withJSONObject:results,options:[.prettyPrinted,.sortedKeys]);try data.write(to:URL(fileURLWithPath:CommandLine.arguments[2]))
