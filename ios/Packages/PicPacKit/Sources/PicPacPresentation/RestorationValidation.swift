import Foundation
import PicPacCore

public enum RestorationValidationError: Error, Equatable, Sendable, CustomStringConvertible {
    case unsupportedSchema(Int)
    case invalidEnvelope
    case invalidScreenState
    case invalidModeState
    case invalidStageState
    case invalidTurnToken
    case invalidAIFields

    public var description: String {
        switch self {
        case let .unsupportedSchema(version):
            "Unsupported restoration schema \(version)"
        case .invalidEnvelope:
            "Restoration counters do not match the contained state"
        case .invalidScreenState:
            "Screen and game payload do not form a restorable state"
        case .invalidModeState:
            "Game mode and domain payload do not match"
        case .invalidStageState:
            "Presentation stage and domain phase do not match"
        case .invalidTurnToken:
            "Turn token does not match the restorable turn"
        case .invalidAIFields:
            "Computer target fields do not match the board and stage"
        }
    }
}

extension RestorationSnapshot {
    /// Validates relationships that cannot be expressed by the domain values alone.
    /// Decoding the nested domain state has already re-run board and bag invariants.
    public func validate() throws {
        guard schemaVersion == Self.currentSchemaVersion else {
            throw RestorationValidationError.unsupportedSchema(schemaVersion)
        }
        guard revision >= 0,
              revision <= TurnToken.maximumRevision,
              presentationCounter >= state.presentationID else {
            throw RestorationValidationError.invalidEnvelope
        }

        guard state.screen == .game else {
            guard state.mode == nil,
                  state.classic == nil,
                  state.picPac == nil,
                  state.stage == .playing,
                  state.aiTargetCell == nil,
                  state.aiMoveSymbol == nil else {
                throw RestorationValidationError.invalidScreenState
            }
            return
        }

        guard let mode = state.mode else {
            throw RestorationValidationError.invalidModeState
        }

        switch mode {
        case .classicLocal:
            try validateClassic()
        case .picPacLocal:
            try validatePicPacLocal()
        case .picPacAI:
            try validatePicPacAI()
        }
    }

    private func validateClassic() throws {
        guard let classic = state.classic, state.picPac == nil,
              classic.revision == revision else {
            throw RestorationValidationError.invalidModeState
        }
        guard state.aiTargetCell == nil, state.aiMoveSymbol == nil else {
            throw RestorationValidationError.invalidAIFields
        }
        let expectedStage: TurnStage = classic.isTerminal ? .terminal : .playing
        guard state.stage == expectedStage else {
            throw RestorationValidationError.invalidStageState
        }
        let ordinal = classic.board.occupiedCount + (classic.isTerminal ? 0 : 1)
        guard let expectedToken = TurnToken.derived(revision: revision, ordinal: ordinal),
              classic.turnToken == expectedToken else {
            throw RestorationValidationError.invalidTurnToken
        }
    }

    private func validatePicPacLocal() throws {
        guard let game = state.picPac, state.classic == nil,
              game.revision == revision else {
            throw RestorationValidationError.invalidModeState
        }
        guard state.aiTargetCell == nil, state.aiMoveSymbol == nil else {
            throw RestorationValidationError.invalidAIFields
        }

        switch game.phase {
        case .awaitingDraw:
            guard state.stage == .handoff else {
                throw RestorationValidationError.invalidStageState
            }
        case let .awaitingPlacement(held: _, token: token):
            guard state.stage == .revealing || state.stage == .playing else {
                throw RestorationValidationError.invalidStageState
            }
            try validate(token: token, game: game)
        case .terminal:
            guard state.stage == .terminal else {
                throw RestorationValidationError.invalidStageState
            }
        }
    }

    private func validatePicPacAI() throws {
        guard let game = state.picPac, state.classic == nil,
              game.revision == revision else {
            throw RestorationValidationError.invalidModeState
        }

        switch game.phase {
        case .awaitingDraw:
            if state.stage == .turnStart {
                try requireNoAIFields()
            } else if state.stage == .aiPlacing || state.stage == .aiSettling {
                guard game.activePlayer == .one else {
                    throw RestorationValidationError.invalidStageState
                }
                try validateCommittedAIFields(game: game)
            } else {
                throw RestorationValidationError.invalidStageState
            }

        case let .awaitingPlacement(held: held, token: token):
            try validate(token: token, game: game)
            if game.activePlayer == .one {
                guard state.stage == .revealing || state.stage == .playing else {
                    throw RestorationValidationError.invalidStageState
                }
                try requireNoAIFields()
            } else {
                switch state.stage {
                case .revealing, .aiThinking:
                    try requireNoAIFields()
                case .aiTargeting:
                    guard let target = validTargetCell(),
                          state.aiMoveSymbol == held,
                          game.board[target] == nil else {
                        throw RestorationValidationError.invalidAIFields
                    }
                default:
                    throw RestorationValidationError.invalidStageState
                }
            }

        case .terminal:
            if state.stage == .terminal {
                if game.activePlayer == .two {
                    try validateCommittedAIFields(game: game)
                } else {
                    try requireNoAIFields()
                }
            } else if state.stage == .aiPlacing || state.stage == .aiSettling {
                guard game.activePlayer == .two else {
                    throw RestorationValidationError.invalidStageState
                }
                try validateCommittedAIFields(game: game)
            } else {
                throw RestorationValidationError.invalidStageState
            }
        }
    }

    private func validate(token: TurnToken, game: PicPacState) throws {
        guard let expected = TurnToken.derived(
            revision: game.revision,
            ordinal: game.board.occupiedCount + 1
        ), token == expected else {
            throw RestorationValidationError.invalidTurnToken
        }
    }

    private func requireNoAIFields() throws {
        guard state.aiTargetCell == nil, state.aiMoveSymbol == nil else {
            throw RestorationValidationError.invalidAIFields
        }
    }

    private func validTargetCell() -> Cell? {
        guard let index = state.aiTargetCell, (0...8).contains(index) else { return nil }
        return Cell(index)
    }

    private func validateCommittedAIFields(game: PicPacState) throws {
        guard let target = validTargetCell(),
              let symbol = state.aiMoveSymbol,
              game.board[target] == symbol else {
            throw RestorationValidationError.invalidAIFields
        }
        if case let .terminal(.win(win)) = game.phase,
           !win.lines.allSatisfy({ $0.cells.contains(target) }) {
            throw RestorationValidationError.invalidAIFields
        }
    }
}
