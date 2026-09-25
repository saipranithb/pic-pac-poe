import Foundation
import PicPacCore

@MainActor
public protocol DrawRandomSource: AnyObject {
    func nextInt(upperBound: Int) -> Int
}

@MainActor
public final class SystemDrawRandomSource: DrawRandomSource {
    private var generator = SystemRandomNumberGenerator()

    public init() {}

    public func nextInt(upperBound: Int) -> Int {
        precondition(upperBound > 0)
        return Int.random(in: 0..<upperBound, using: &generator)
    }
}

public protocol PresentationClock: Sendable {
    func sleep(milliseconds: Int) async throws
}

public struct SystemPresentationClock: PresentationClock {
    public init() {}

    public func sleep(milliseconds: Int) async throws {
        try await Task.sleep(for: .milliseconds(milliseconds))
    }
}

public protocol AIWorker: Sendable {
    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int
}

public enum AIWorkerError: Error, Equatable, Sendable {
    case unavailable
    case illegalCell(Int)
}

public struct UnavailableAIWorker: AIWorker {
    public init() {}

    public func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        throw AIWorkerError.unavailable
    }
}

/// Runs a synchronous search closure in a detached task. Later production agents
/// can use this boundary without inheriting the main actor from the coordinator.
public struct DetachedAIWorker: AIWorker {
    private let operation: @Sendable (AiObservation, Difficulty) throws -> Int

    public init(
        operation: @escaping @Sendable (AiObservation, Difficulty) throws -> Int
    ) {
        self.operation = operation
    }

    public func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        let operation = self.operation
        let search = Task.detached(priority: .userInitiated) {
            try Task.checkCancellation()
            let cell = try operation(observation, difficulty)
            try Task.checkCancellation()
            return cell
        }
        return try await withTaskCancellationHandler {
            try await search.value
        } onCancel: {
            search.cancel()
        }
    }
}

public protocol LocalStateStore: Sendable {
    func loadSnapshotData() async throws -> Data?
    func saveSnapshotData(_ data: Data) async throws
    func clearSnapshot() async throws
    func loadSettingsData() async throws -> Data?
    func saveSettingsData(_ data: Data) async throws
}

public actor InMemoryLocalStateStore: LocalStateStore {
    public private(set) var snapshotData: Data?
    public private(set) var settingsData: Data?
    public private(set) var snapshotWriteCount = 0
    public private(set) var settingsWriteCount = 0

    public init(snapshotData: Data? = nil, settingsData: Data? = nil) {
        self.snapshotData = snapshotData
        self.settingsData = settingsData
    }

    public func loadSnapshotData() -> Data? { snapshotData }

    public func saveSnapshotData(_ data: Data) {
        snapshotData = data
        snapshotWriteCount += 1
    }

    public func clearSnapshot() {
        snapshotData = nil
    }

    public func loadSettingsData() -> Data? { settingsData }

    public func saveSettingsData(_ data: Data) {
        settingsData = data
        settingsWriteCount += 1
    }
}

public actor FileLocalStateStore: LocalStateStore {
    public enum StoreError: Error, Equatable, Sendable {
        case backupExclusionNotApplied(String)
    }

    private let directoryURL: URL
    private let snapshotURL: URL
    private let settingsURL: URL
    public init(directoryURL: URL) {
        self.directoryURL = directoryURL
        self.snapshotURL = directoryURL.appendingPathComponent("current-match.json", isDirectory: false)
        self.settingsURL = directoryURL.appendingPathComponent("settings.json", isDirectory: false)
    }

    public static func appSupport(
        directoryName: String = "PicPacPoe"
    ) throws -> FileLocalStateStore {
        let fileManager = FileManager.default
        let root = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return FileLocalStateStore(
            directoryURL: root.appendingPathComponent(directoryName, isDirectory: true)
        )
    }

    public func loadSnapshotData() throws -> Data? {
        try readIfPresent(snapshotURL)
    }

    public func saveSnapshotData(_ data: Data) throws {
        try writeAtomically(data, to: snapshotURL)
    }

    public func clearSnapshot() throws {
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: snapshotURL.path) else { return }
        try fileManager.removeItem(at: snapshotURL)
    }

    public func loadSettingsData() throws -> Data? {
        try readIfPresent(settingsURL)
    }

    public func saveSettingsData(_ data: Data) throws {
        try writeAtomically(data, to: settingsURL)
    }

    private func readIfPresent(_ url: URL) throws -> Data? {
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        return try Data(contentsOf: url)
    }

    private func writeAtomically(_ data: Data, to url: URL) throws {
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        try applyAndVerifyBackupExclusion(to: directoryURL)
        try data.write(to: url, options: .atomic)
        // Atomic replacement can produce a fresh inode, so reapply the flag.
        try applyAndVerifyBackupExclusion(to: url)
    }

    private func applyAndVerifyBackupExclusion(to inputURL: URL) throws {
        var url = inputURL
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        let actual = try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup
        guard actual == true else {
            throw StoreError.backupExclusionNotApplied(inputURL.path)
        }
    }
}

public enum PersistenceCodec {
    public static func encode<Value: Encodable>(_ value: Value) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(value)
    }

    public static func decode<Value: Decodable>(
        _ type: Value.Type,
        from data: Data
    ) throws -> Value {
        try JSONDecoder().decode(type, from: data)
    }
}
