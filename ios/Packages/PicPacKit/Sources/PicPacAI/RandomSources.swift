import PicPacCore

public struct SystemAIRandomSource: BoundedRandomSource {
    private var generator = SystemRandomNumberGenerator()

    public init() {}

    public mutating func nextInt(upperBound: Int) throws -> Int {
        guard upperBound > 0 else {
            throw BoundedRandomError.invalidUpperBound(upperBound)
        }
        return Int.random(in: 0..<upperBound, using: &generator)
    }
}

/// A small portable source for repeatable AI tests and offline comparisons.
/// Production play uses `SystemAIRandomSource` and does not share the game draw source.
public struct SplitMix64RandomSource: BoundedRandomSource {
    private var state: UInt64

    public init(seed: UInt64) {
        state = seed
    }

    public mutating func nextInt(upperBound: Int) throws -> Int {
        guard upperBound > 0 else {
            throw BoundedRandomError.invalidUpperBound(upperBound)
        }
        let bound = UInt64(upperBound)
        let threshold = (UInt64.zero &- bound) % bound
        var value = nextUInt64()
        while value < threshold {
            value = nextUInt64()
        }
        return Int(value % bound)
    }

    private mutating func nextUInt64() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        return value ^ (value >> 31)
    }
}
