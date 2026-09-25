import Foundation
import XCTest
@testable import PicPacAI
@testable import PicPacCore

/// Repeatable measurements of the actual production agents. Host scheduling
/// samples are not display frame times and are never physical-device evidence.
@MainActor
final class PerformanceAuditTests: XCTestCase {
    func testProductionSearchLatencyBudgetCancellationAndMainActorProgress() async throws {
        let opening = try AiObservation(
            board: .empty, activePlayer: .two, agentPlayer: .two,
            heldSymbol: .x, remainingX: 4, remainingO: 5
        )
        var cold: [Double] = []
        var warm: [Double] = []
        var mcts: [Double] = []
        var cancellation: [Double] = []
        var heartbeat: [Double] = []
        var coldNodes: [Int] = []
        var warmNodes: [Int] = []
        let ticker = Task { @MainActor in
            var previous = ContinuousClock.now
            while !Task.isCancelled {
                do { try await Task.sleep(for: .milliseconds(2)) } catch { break }
                let now = ContinuousClock.now
                heartbeat.append(milliseconds(previous.duration(to: now)))
                previous = now
            }
        }
        defer { ticker.cancel() }
        for _ in 0..<5 {
            let engine = ProductionAIEngine(policy: .empty)
            let first = try await engine.chooseMove(for: opening, opponent: .hard)
            let second = try await engine.chooseMove(for: opening, opponent: .hard)
            XCTAssertEqual(first.cell, Cell(4))
            XCTAssertEqual(second.cell, first.cell)
            guard case let .search(firstStats) = first.diagnostics,
                  case let .search(secondStats) = second.diagnostics else {
                return XCTFail("Hard diagnostics missing")
            }
            XCTAssertEqual(firstStats.value, 5.0 / 21.0, accuracy: 1e-12)
            XCTAssertGreaterThan(secondStats.cacheHits, 0)
            cold.append(Double(firstStats.elapsedNanoseconds) / 1_000_000)
            warm.append(Double(secondStats.elapsedNanoseconds) / 1_000_000)
            coldNodes.append(firstStats.nodes)
            warmNodes.append(secondStats.nodes)
            let sampled = try await engine.chooseMove(for: opening, opponent: .mcts)
            guard case let .search(sampledStats) = sampled.diagnostics else {
                return XCTFail("MCTS diagnostics missing")
            }
            XCTAssertEqual(sampledStats.simulations, 2_000)
            mcts.append(Double(sampledStats.elapsedNanoseconds) / 1_000_000)

            let cancellableEngine = ProductionAIEngine(policy: .empty)
            let search = Task { try await cancellableEngine.chooseMove(for: opening, opponent: .hard) }
            // Cold opening search is deliberately allowed to begin; unlike the
            // pre-cancelled contract test, cancellation is requested in flight.
            try await Task.sleep(for: .milliseconds(5))
            let requestTime = ContinuousClock.now
            search.cancel()
            do {
                _ = try await search.value
                XCTFail("Cold Hard search finished before in-flight cancellation was exercised")
            } catch is CancellationError {
                cancellation.append(milliseconds(requestTime.duration(to: .now)))
            }
        }
        ticker.cancel()
        await ticker.value
        XCTAssertGreaterThan(heartbeat.count, 5, "Main actor must make progress during search")
        // Generous hang guard, not a frame-rate promise on shared CI hardware.
        XCTAssertLessThan(cancellation.max() ?? .infinity, 1_000)
        let report: [String: Any] = [
            "schemaVersion": 1,
            "kind": "macOS-host-production-agent-measurement",
            "physicalDeviceEvidence": false,
            "framePacingEvidence": false,
            "sampleCount": 5,
            "hardColdMilliseconds": cold,
            "hardWarmMilliseconds": warm,
            "hardColdNodes": coldNodes,
            "hardWarmNodes": warmNodes,
            "mctsMilliseconds": mcts,
            "mctsCompletedSimulationsPerCall": 2_000,
            "hardInFlightCancellationMilliseconds": cancellation,
            "mainActorTwoMillisecondHeartbeat": [
                "samples": heartbeat.count,
                "p50Milliseconds": percentile(heartbeat, fraction: 0.5),
                "p95Milliseconds": percentile(heartbeat, fraction: 0.95),
                "maxMilliseconds": heartbeat.max() ?? 0
            ]
        ]
        let data = try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys])
        print("PHASE3_PERFORMANCE_JSON \(String(decoding: data, as: UTF8.self))")
    }

    private func milliseconds(_ duration: Duration) -> Double {
        Double(duration.components.seconds) * 1_000 + Double(duration.components.attoseconds) / 1e15
    }

    private func percentile(_ samples: [Double], fraction: Double) -> Double {
        guard !samples.isEmpty else { return 0 }
        let sorted = samples.sorted()
        return sorted[min(sorted.count - 1, Int(Double(sorted.count - 1) * fraction))]
    }
}
