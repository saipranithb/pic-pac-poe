import PicPacCore
import PicPacPresentation
import SwiftUI

struct PiecePath: Shape {
    let symbol: Symbol
    func path(in rect: CGRect) -> Path {
        let radius = min(rect.width, rect.height) * 0.25
        let center = CGPoint(x: rect.midX, y: rect.midY)
        return Path { path in
            if symbol == .x {
                path.move(to: CGPoint(x: center.x - radius, y: center.y - radius))
                path.addLine(to: CGPoint(x: center.x + radius, y: center.y + radius))
                path.move(to: CGPoint(x: center.x + radius, y: center.y - radius))
                path.addLine(to: CGPoint(x: center.x - radius, y: center.y + radius))
            } else {
                path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
            }
        }
    }
}

struct FormPiece: View {
    @Environment(\.formPalette) private var colors
    let symbol: Symbol
    var body: some View {
        GeometryReader { geometry in
            let side = min(geometry.size.width, geometry.size.height)
            let stroke = side * 0.19
            let thickness = min(2, side * 0.035)
            let shape = PiecePath(symbol: symbol)
            ZStack {
                shape.stroke(colors.shadow.opacity(0.12), style: StrokeStyle(lineWidth: stroke + 3, lineCap: .round)).offset(y: thickness + 2)
                shape.stroke(symbol == .x ? colors.xEdge : colors.oEdge, style: StrokeStyle(lineWidth: stroke, lineCap: .round)).offset(y: thickness)
                shape.stroke(LinearGradient(colors: [symbol == .x ? colors.xHighlight : colors.oHighlight, colors.symbol(symbol)], startPoint: UnitPoint(x: 0.5, y: 0.25), endPoint: UnitPoint(x: 0.5, y: 0.75)), style: StrokeStyle(lineWidth: stroke, lineCap: .round))
            }
        }
        .accessibilityHidden(true)
    }
}

struct RecessedPiece: View {
    @Environment(\.formPalette) private var colors
    let symbol: Symbol
    var body: some View {
        FormPiece(symbol: symbol).padding(4)
            .background(LinearGradient(colors: [colors.recess, colors.surface], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(colors.border, lineWidth: 0.75))
    }
}

enum BoardSemantics {
    static func cell(_ index: Int, symbol: Symbol?, target: Int?, moveSymbol: Symbol?, stage: TurnStage) -> String {
        let coordinate = "row \(index / 3 + 1), column \(index % 3 + 1)"
        if target == index {
            if stage == .aiTargeting { return "Computer selected \(coordinate)" }
            if [.aiPlacing, .aiSettling].contains(stage), let symbol = moveSymbol ?? symbol {
                return "Computer placed \(symbol.rawValue) in \(coordinate)"
            }
        }
        return "Row \(index / 3 + 1), column \(index % 3 + 1), \(symbol?.rawValue ?? "empty")"
    }
    static func summary(_ board: Board) -> String {
        "Final board. " + (0..<3).map { row in
            "Row \(row + 1): " + (0..<3).map { board[Cell(row * 3 + $0)]?.rawValue ?? "empty" }.joined(separator: ", ")
        }.joined(separator: ". ")
    }
}

struct PhysicalBoard: View {
    @Environment(\.formPalette) private var colors
    let board: Board
    var enabled = false
    var winningLine: WinningLine? = nil
    var target: Int? = nil
    var moveSymbol: Symbol? = nil
    var stage: TurnStage = .playing
    var miniature = false
    var onCell: (Int) -> Void = { _ in }

    @ViewBuilder var body: some View {
        if miniature {
            artwork.accessibilityRepresentation {
                Image(systemName: "square.grid.3x3")
                    .accessibilityLabel(BoardSemantics.summary(board))
                    .accessibilityIdentifier("final-board")
            }
        } else {
            artwork.accessibilityElement(children: .contain)
                .accessibilityLabel("Board").accessibilityIdentifier("game-board")
        }
    }

    private var artwork: some View {
        GeometryReader { geometry in
            let side = geometry.size.width
            let cellSide = (side - 36) / 3
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 24).fill(colors.shadow.opacity(0.24)).offset(y: 3)
                RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [colors.raised, colors.surface], startPoint: .top, endPoint: .bottom))
                RoundedRectangle(cornerRadius: 24).strokeBorder(colors.border, lineWidth: 1)
                Path { path in
                    path.move(to: CGPoint(x: 24, y: 1))
                    path.addLine(to: CGPoint(x: side - 24, y: 1))
                }.stroke(colors.highlight.opacity(0.35), style: StrokeStyle(lineWidth: 1, lineCap: .round))
                VStack(spacing: 6) {
                    ForEach(0..<3, id: \.self) { row in
                        HStack(spacing: 6) {
                            ForEach(0..<3, id: \.self) { column in
                                let index = row * 3 + column
                                let focused = target == index && [.aiTargeting, .aiPlacing, .aiSettling].contains(stage)
                                BoardCell(symbol: board[Cell(index)], target: focused,
                                          winning: winningLine?.cells.contains(Cell(index)) == true,
                                          enabled: enabled && board[Cell(index)] == nil,
                                          label: BoardSemantics.cell(index, symbol: board[Cell(index)], target: target, moveSymbol: moveSymbol, stage: stage),
                                          action: { onCell(index) })
                                    .frame(width: cellSide, height: cellSide)
                                    .accessibilityIdentifier("cell-\(index)")
                            }
                        }
                    }
                }.padding(12)
                if let winningLine {
                    Canvas { context, _ in
                        func center(_ cell: Cell) -> CGPoint {
                            CGPoint(x: 12 + CGFloat(cell.index % 3) * (cellSide + 6) + cellSide / 2,
                                    y: 12 + CGFloat(cell.index / 3) * (cellSide + 6) + cellSide / 2)
                        }
                        var line = Path()
                        line.move(to: center(winningLine.first)); line.addLine(to: center(winningLine.third))
                        context.stroke(line, with: .color(colors.recess), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                        context.stroke(line, with: .color(colors.text), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    }.allowsHitTesting(false).accessibilityHidden(true)
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

private struct BoardCell: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.formReducedMotion) private var reduced
    let symbol: Symbol?
    let target: Bool
    let winning: Bool
    let enabled: Bool
    let label: String
    let action: () -> Void
    @State private var pieceProgress: CGFloat = 1

    var body: some View {
        Button(action: action) {
            GeometryReader { proxy in
                let radius = min(18, proxy.size.width * 0.19)
                ZStack {
                    RoundedRectangle(cornerRadius: radius).fill(LinearGradient(colors: [colors.recess, colors.surface], startPoint: .top, endPoint: .bottom))
                    RoundedRectangle(cornerRadius: radius).strokeBorder(colors.border, lineWidth: 1)
                    Path { path in
                        path.move(to: CGPoint(x: radius, y: 2))
                        path.addLine(to: CGPoint(x: proxy.size.width - radius, y: 2))
                    }.stroke(colors.shadow.opacity(0.32), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    if target || winning {
                        RoundedRectangle(cornerRadius: radius).strokeBorder(colors.focus, lineWidth: 2).padding(3)
                    }
                    if target {
                        RoundedRectangle(cornerRadius: max(1, radius - 7)).strokeBorder(colors.focus, lineWidth: 1).padding(7)
                    }
                    if let symbol {
                        FormPiece(symbol: symbol).padding(4)
                            .opacity(pieceProgress).scaleEffect(0.96 + 0.04 * pieceProgress)
                            .offset(y: -3 * (1 - pieceProgress))
                    }
                }
            }
        }
        .buttonStyle(BoardCellStyle())
        .disabled(!enabled)
        .accessibilityLabel(label)
        .onChange(of: symbol) { old, new in
            guard old == nil, new != nil, !reduced else { pieceProgress = 1; return }
            pieceProgress = 0
            withAnimation(.timingCurve(0.2, 0.8, 0.2, 1, duration: 0.18)) { pieceProgress = 1 }
        }
        .onChange(of: reduced) { _, value in if value { pieceProgress = 1 } }
    }
}

private struct BoardCellStyle: ButtonStyle {
    @Environment(\.formPalette) private var colors
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.overlay {
            if configuration.isPressed { RoundedRectangle(cornerRadius: 18).fill(colors.border.opacity(0.16)).allowsHitTesting(false) }
        }.contentShape(Rectangle())
    }
}
