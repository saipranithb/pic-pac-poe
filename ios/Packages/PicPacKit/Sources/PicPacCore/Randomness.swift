public protocol BoundedRandomSource: Sendable {
    mutating func nextInt(upperBound: Int) throws -> Int
}

public struct BoundedRandomStep: Equatable, Codable, Sendable {
    public let upperBound: Int
    public let result: Int

    public init(upperBound: Int, result: Int) {
        self.upperBound = upperBound
        self.result = result
    }
}

public enum BoundedRandomError: Error, Equatable, Sendable {
    case invalidUpperBound(Int)
    case exhaustedScript
    case unexpectedBound(expected: Int, actual: Int)
    case resultOutOfBounds(result: Int, upperBound: Int)
}

public struct ScriptedBoundedRandomSource: BoundedRandomSource {
    public let steps: [BoundedRandomStep]
    public private(set) var consumedCount: Int

    public init(steps: [BoundedRandomStep]) {
        self.steps = steps
        consumedCount = 0
    }

    public mutating func nextInt(upperBound: Int) throws -> Int {
        guard upperBound > 0 else {
            throw BoundedRandomError.invalidUpperBound(upperBound)
        }
        guard consumedCount < steps.count else {
            throw BoundedRandomError.exhaustedScript
        }
        let step = steps[consumedCount]
        guard step.upperBound == upperBound else {
            throw BoundedRandomError.unexpectedBound(
                expected: step.upperBound,
                actual: upperBound
            )
        }
        guard (0..<upperBound).contains(step.result) else {
            throw BoundedRandomError.resultOutOfBounds(
                result: step.result,
                upperBound: upperBound
            )
        }
        consumedCount += 1
        return step.result
    }
}

extension PicPacRules {
    public static func draw<Random: BoundedRandomSource>(
        _ state: PicPacState,
        using random: inout Random
    ) throws -> TransitionResult<PicPacState> {
        switch state.phase {
        case .terminal:
            return .rejected(.terminal)
        case .awaitingPlacement:
            return .rejected(.wrongPhase)
        case .awaitingDraw:
            break
        }

        let result = try random.nextInt(upperBound: state.hiddenTotal)
        guard (0..<state.hiddenTotal).contains(result) else {
            throw BoundedRandomError.resultOutOfBounds(
                result: result,
                upperBound: state.hiddenTotal
            )
        }
        let symbol: Symbol = result < state.remainingX ? .x : .o
        return draw(state, symbol: symbol)
    }
}
