import Foundation
import PicPacCore

public enum TabularPolicyError: Error, Equatable, Sendable {
    case missingBundledArtifact
    case truncated
    case invalidMagic(UInt32)
    case unsupportedVersion(UInt32)
    case rulesMismatch(String)
    case invalidRowCount(Int)
    case duplicateKey(UInt64)
    case trailingBytes(Int)
}

public struct TabularPolicy: Sendable {
    public static let magic: UInt32 = 0x5050_5051
    public static let version: UInt32 = 1
    public static let rulesID = "picpac-5x5-draw-before-place-v1"
    public static let artifactByteCount = 891_749
    public static let artifactSHA256 = "9b05cc725ab8b52cecb940b6c823cb66e843acf462511c87d2ab3e1c834152b1"

    private let rows: [UInt64: [Float]]

    public static let empty = TabularPolicy(rows: [:])

    public init(data: Data) throws {
        var reader = PolicyReader(data: data)
        let magic = try reader.readUInt32()
        guard magic == Self.magic else { throw TabularPolicyError.invalidMagic(magic) }
        let version = try reader.readUInt32()
        guard version == Self.version else {
            throw TabularPolicyError.unsupportedVersion(version)
        }
        let rulesLength = Int(try reader.readUInt16())
        let rulesBytes = try reader.readBytes(count: rulesLength)
        let rules = String(decoding: rulesBytes, as: UTF8.self)
        guard rules == Self.rulesID else { throw TabularPolicyError.rulesMismatch(rules) }

        let signedCount = Int32(bitPattern: try reader.readUInt32())
        guard signedCount >= 0, signedCount <= 100_000 else {
            throw TabularPolicyError.invalidRowCount(Int(signedCount))
        }
        var parsed: [UInt64: [Float]] = [:]
        parsed.reserveCapacity(Int(signedCount))
        for _ in 0..<Int(signedCount) {
            let key = try reader.readUInt64()
            guard parsed[key] == nil else { throw TabularPolicyError.duplicateKey(key) }
            parsed[key] = try (0..<9).map { _ in try reader.readFloat() }
        }
        guard reader.remainingCount == 0 else {
            throw TabularPolicyError.trailingBytes(reader.remainingCount)
        }
        rows = parsed
    }

    internal init(rows: [UInt64: [Float]]) {
        precondition(rows.values.allSatisfy { $0.count == 9 })
        self.rows = rows
    }

    public var stateCount: Int { rows.count }

    public func value(_ state: PicPacDecisionState, at cell: Cell) -> Float {
        rows[Self.key(for: state)]?[cell.index] ?? 0
    }

    public func bestCell(for state: PicPacDecisionState) -> Cell {
        let ordered = PositionEvaluator.ordered(state.legalCells)
        var best = ordered[0]
        var bestValue = value(state, at: best)
        for cell in ordered.dropFirst() {
            let candidate = value(state, at: cell)
            if candidate > bestValue {
                best = cell
                bestValue = candidate
            }
        }
        return best
    }

    public func maxValue(for state: PicPacDecisionState) -> Double {
        state.legalCells.map { Double(value(state, at: $0)) }.max() ?? 0
    }

    public static func key(for state: PicPacDecisionState) -> UInt64 {
        let held: UInt64 = state.heldSymbol == .x ? 0 : 1
        return UInt64(state.board.code)
            | (held << 15)
            | (UInt64(state.remainingX) << 16)
            | (UInt64(state.remainingO) << 19)
    }

    public func encoded() -> Data {
        var writer = PolicyWriter()
        writer.append(Self.magic)
        writer.append(Self.version)
        let rules = Array(Self.rulesID.utf8)
        writer.append(UInt16(rules.count))
        writer.append(contentsOf: rules)
        let nonzero = rows.filter { $0.value.contains { $0 != 0 } }
        writer.append(UInt32(nonzero.count))
        for key in nonzero.keys.sorted() {
            writer.append(key)
            for value in nonzero[key]! {
                writer.append(value.bitPattern)
            }
        }
        return writer.data
    }

    public static func bundledArtifactData() throws -> Data {
        guard let url = Bundle.module.url(
            forResource: "picpac_rl_policy_v1",
            withExtension: "bin"
        ) else {
            throw TabularPolicyError.missingBundledArtifact
        }
        return try Data(contentsOf: url, options: [.mappedIfSafe])
    }

    /// Reads and validates the bundled table away from the caller's actor.
    public static func loadBundled() async throws -> TabularPolicy {
        guard let url = Bundle.module.url(
            forResource: "picpac_rl_policy_v1",
            withExtension: "bin"
        ) else {
            throw TabularPolicyError.missingBundledArtifact
        }
        let loader = Task.detached(priority: .utility) {
            try Task.checkCancellation()
            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            let policy = try TabularPolicy(data: data)
            try Task.checkCancellation()
            return policy
        }
        return try await withTaskCancellationHandler {
            try await loader.value
        } onCancel: {
            loader.cancel()
        }
    }
}

public struct RlPolicyAgent: AiAgent {
    private let policy: TabularPolicy
    private let fallback: any AiAgent

    public init(
        policy: TabularPolicy,
        fallback: any AiAgent = HeuristicAgent()
    ) {
        self.policy = policy
        self.fallback = fallback
    }

    public func chooseMove(
        for observation: AiObservation,
        limits: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        try Task.checkCancellation()
        guard policy.stateCount > 0 else {
            return try await fallback.chooseMove(for: observation, limits: limits)
        }
        let state = PublicPicPacSearchModel.from(observation)
        return AiDecision(cell: policy.bestCell(for: state))
    }
}

private struct PolicyReader {
    let data: Data
    var offset = 0

    var remainingCount: Int { data.count - offset }

    mutating func readUInt16() throws -> UInt16 {
        let bytes = try readBytes(count: 2)
        return bytes.reduce(UInt16.zero) { ($0 << 8) | UInt16($1) }
    }

    mutating func readUInt32() throws -> UInt32 {
        let bytes = try readBytes(count: 4)
        return bytes.reduce(UInt32.zero) { ($0 << 8) | UInt32($1) }
    }

    mutating func readUInt64() throws -> UInt64 {
        let bytes = try readBytes(count: 8)
        return bytes.reduce(UInt64.zero) { ($0 << 8) | UInt64($1) }
    }

    mutating func readFloat() throws -> Float {
        Float(bitPattern: try readUInt32())
    }

    mutating func readBytes(count: Int) throws -> Data {
        guard count >= 0, remainingCount >= count else { throw TabularPolicyError.truncated }
        defer { offset += count }
        return data.subdata(in: offset..<(offset + count))
    }
}

private struct PolicyWriter {
    var data = Data()

    mutating func append(_ value: UInt16) {
        append(contentsOf: [UInt8(value >> 8), UInt8(value & 0xff)])
    }

    mutating func append(_ value: UInt32) {
        append(contentsOf: [
            UInt8((value >> 24) & 0xff),
            UInt8((value >> 16) & 0xff),
            UInt8((value >> 8) & 0xff),
            UInt8(value & 0xff),
        ])
    }

    mutating func append(_ value: UInt64) {
        append(contentsOf: [
            UInt8((value >> 56) & 0xff),
            UInt8((value >> 48) & 0xff),
            UInt8((value >> 40) & 0xff),
            UInt8((value >> 32) & 0xff),
            UInt8((value >> 24) & 0xff),
            UInt8((value >> 16) & 0xff),
            UInt8((value >> 8) & 0xff),
            UInt8(value & 0xff),
        ])
    }

    mutating func append(contentsOf bytes: [UInt8]) {
        data.append(contentsOf: bytes)
    }
}
