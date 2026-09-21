import Foundation
import PicPacCore

public struct SearchLimits: Sendable {
    public let maxDepth: Int?
    public let nodeBudget: Int?
    public let deadline: ContinuousClock.Instant?

    public init(
        maxDepth: Int? = nil,
        nodeBudget: Int? = nil,
        deadline: ContinuousClock.Instant? = nil
    ) {
        precondition(maxDepth == nil || maxDepth! > 0)
        precondition(nodeBudget == nil || nodeBudget! > 0)
        self.maxDepth = maxDepth
        self.nodeBudget = nodeBudget
        self.deadline = deadline
    }

    internal var withoutResourceBounds: SearchLimits {
        SearchLimits(maxDepth: maxDepth)
    }

    internal var isPastDeadline: Bool {
        guard let deadline else { return false }
        return ContinuousClock().now >= deadline
    }
}

public struct SearchDiagnostics: Equatable, Sendable {
    public let nodes: Int
    public let cacheHits: Int
    public let maxDepth: Int
    public let elapsedNanoseconds: UInt64
    public let value: Double
    public let simulations: Int

    public init(
        nodes: Int,
        cacheHits: Int,
        maxDepth: Int,
        elapsedNanoseconds: UInt64,
        value: Double,
        simulations: Int = 0
    ) {
        self.nodes = nodes
        self.cacheHits = cacheHits
        self.maxDepth = maxDepth
        self.elapsedNanoseconds = elapsedNanoseconds
        self.value = value
        self.simulations = simulations
    }
}

public enum AiDiagnostics: Equatable, Sendable {
    case none
    case search(SearchDiagnostics)
}

public struct AiDecision: Equatable, Sendable {
    public let cell: Cell
    public let diagnostics: AiDiagnostics

    public init(cell: Cell, diagnostics: AiDiagnostics = .none) {
        self.cell = cell
        self.diagnostics = diagnostics
    }
}

public protocol AiAgent: Sendable {
    func chooseMove(
        for observation: AiObservation,
        limits: SearchLimits
    ) async throws -> AiDecision
}

internal func elapsedNanoseconds(_ duration: Duration) -> UInt64 {
    let components = duration.components
    guard components.seconds >= 0 else { return 0 }
    let seconds = UInt64(components.seconds)
    let nanosFromSeconds = seconds.multipliedReportingOverflow(by: 1_000_000_000)
    if nanosFromSeconds.overflow { return .max }
    let attoseconds = max(components.attoseconds, 0)
    let fractional = UInt64(attoseconds / 1_000_000_000)
    return nanosFromSeconds.partialValue.addingReportingOverflow(fractional).overflow
        ? .max
        : nanosFromSeconds.partialValue + fractional
}
