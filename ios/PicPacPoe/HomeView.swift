import PicPacCore
import PicPacPresentation
import SwiftUI
import UIKit

extension Difficulty {
    var title: String {
        switch self { case .easy: "Easy"; case .medium: "Medium"; case .hard: "Hard"; case .mcts: "MCTS"; case .qLearning: "Q-learning" }
    }
    var detail: String {
        switch self {
        case .easy: "Makes quick, imperfect moves."
        case .medium: "Looks a few turns ahead."
        case .hard: "Plays the solved game."
        case .mcts: "Samples 2,000 games per move."
        case .qLearning: "Uses a table learned from self-play."
        }
    }
}

/// Shared deterministic easing evaluator used by the finite title and explanatory loop.
enum FormMotion {
    static func cubic(_ t: Double, _ x1: Double = 0.2, _ y1: Double = 0.8, _ x2: Double = 0.2, _ y2: Double = 1) -> Double {
        let t = min(1, max(0, t))
        if t == 0 || t == 1 { return t }
        func point(_ u: Double, _ a: Double, _ b: Double) -> Double { 3 * (1-u) * (1-u) * u * a + 3 * (1-u) * u * u * b + u * u * u }
        var low = 0.0; var high = 1.0
        for _ in 0..<20 { let mid = (low + high) / 2; if point(mid, x1, x2) < t { low = mid } else { high = mid } }
        return point((low + high) / 2, y1, y2)
    }
    static func titleProgress(elapsed: Double, group: Int) -> Double {
        let time = elapsed - Double(group) * 0.07
        if time <= 0 { return 0 }
        if time < 0.36 { return 1.015 * cubic(time / 0.36, 0.4, 0, 0.2, 1) }
        if time < 0.48 { return 1.015 - 0.015 * cubic((time - 0.36) / 0.12, 0, 0, 0.2, 1) }
        return 1
    }
    static func homeSymbol(elapsed: Double, reduced: Bool) -> (xAlpha: Double, oAlpha: Double, xScale: Double, oScale: Double) {
        let time = max(0, elapsed).truncatingRemainder(dividingBy: 2.8)
        let xIsCurrent = time < 1.4
        let half = time.truncatingRemainder(dividingBy: 1.4)
        let fadeDuration = reduced ? 0.4 : 0.28
        let fade = cubic((half - (1.4 - fadeDuration)) / fadeDuration)
        var pulse = 1.0
        if !reduced {
            if half >= 0.2 && half < 0.34 { pulse += 0.035 * cubic((half - 0.2) / 0.14) }
            if half >= 0.34 && half < 0.52 { pulse += 0.035 * (1 - cubic((half - 0.34) / 0.18)) }
        }
        let outgoingScale = reduced ? 1 : pulse - 0.03 * fade
        let incomingScale = reduced ? 1 : 0.97 + 0.03 * fade
        return xIsCurrent ? (1-fade, fade, outgoingScale, incomingScale) : (fade, 1-fade, incomingScale, outgoingScale)
    }
}

struct HomeView: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .body) private var bodyLargeSize = 16.0
    @ScaledMetric(relativeTo: .subheadline) private var modeMarkWidth = 46.0
    let coordinator: GameCoordinator
    @State private var difficulty = Difficulty.medium

    var body: some View {
        GeometryReader { viewport in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HomeWordmark(coordinator: coordinator)
                    Text("Tic-tac-toe, except you don't choose your symbol.")
                        .font(.system(size: bodyLargeSize)).foregroundStyle(colors.secondary).padding(.top, 8)
                        .fixedSize(horizontal: false, vertical: true)
                    HomeIllustration(active: coordinator.isSceneActive, viewport: viewport.frame(in: .global))
                        .frame(height: viewport.size.height < 680 || typeSize >= .xxxLarge ? 90 : 118)
                        .padding(.top, 12)
                    Text("Draw a piece. Choose a square.")
                        .font(.system(.subheadline, weight: .bold)).foregroundStyle(colors.secondary)
                        .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                        .padding(.top, 4).padding(.bottom, 20)
                    Text("Pick a game").font(.system(.body, weight: .semibold)).foregroundStyle(colors.secondary)
                        .accessibilityAddTraits(.isHeader)
                    modeRow("Classic", subtitle: "Regular tic-tac-toe.", mark: "3×3") { Task { await coordinator.startClassic() } }
                    Divider().overlay(colors.subtle)
                    modeRow("Pic-Pac Local", subtitle: "Two players. One phone.", mark: "2P") { Task { await coordinator.startPicPacLocal() } }
                    Divider().overlay(colors.subtle)
                    Text("Vs Computer").font(.system(.title2, weight: .bold)).padding(.top, 18).accessibilityAddTraits(.isHeader)
                    Text("Play Pic-Pac against the computer.").font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 3)
                    let layout = typeSize >= .xxxLarge ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(spacing: 8))
                    layout {
                        ForEach(Difficulty.allCases.filter(\.isProduction), id: \.self) { choice in
                            FormChoice(title: choice.title, selected: difficulty == choice) { difficulty = choice }
                                .accessibilityIdentifier("difficulty-\(choice.rawValue)")
                        }
                    }.padding(.top, 14)
                    Text(difficulty.detail).font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 8).padding(.bottom, 12)
                    Button("Play") { Task { await coordinator.startPicPacAI(difficulty: difficulty) } }
                        .buttonStyle(FormButtonStyle()).accessibilityIdentifier("play-computer")
                    let footer = typeSize >= .xxxLarge ? AnyLayout(VStackLayout(alignment: .leading, spacing: 0)) : AnyLayout(HStackLayout(spacing: 0))
                    footer {
                        footerButton("How to play", screen: .howTo)
                        if typeSize < .xxxLarge { Spacer(minLength: 0) }
                        footerButton("AI Lab", screen: .aiLab)
                        if typeSize < .xxxLarge { Spacer(minLength: 0) }
                        footerButton("Settings", screen: .settings)
                    }.padding(.top, 16)
                }
                .padding(.horizontal, 20).padding(.vertical, 16)
                .frame(maxWidth: 620).frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("home-screen")
        }
    }

    private func footerButton(_ title: String, screen: AppScreen) -> some View {
        Button(title) { Task { await coordinator.show(screen) } }.font(.system(.footnote, weight: .bold)).buttonStyle(FormQuietButtonStyle())
            .accessibilityIdentifier("open-\(screen.rawValue)")
    }
    private func modeRow(_ title: String, subtitle: String, mark: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 0) {
                Text(mark).font(.system(.subheadline, weight: .bold)).foregroundStyle(colors.secondary)
                    .lineLimit(1).frame(width: modeMarkWidth, alignment: .leading).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(.title2, weight: .bold)).foregroundStyle(colors.text)
                    Text(subtitle).font(.subheadline).foregroundStyle(colors.secondary)
                }.fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 12)
                Image(systemName: "chevron.forward").font(.system(size: 14, weight: .bold)).foregroundStyle(colors.secondary).accessibilityHidden(true)
            }.padding(.vertical, 12).padding(.horizontal, 4).frame(minHeight: 72).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .combine)
            .accessibilityIdentifier(title == "Classic" ? "start-classic" : "start-local")
    }
}

struct HomeWordmark: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.formReducedMotion) private var reduced
    @ScaledMetric(relativeTo: .largeTitle) private var nominalSize = 40.0
    let coordinator: GameCoordinator
    @State private var elapsed = 0.62
    @State private var requested = false
    @State private var availableWidth: CGFloat = 320
    static let parts = ["Pic", "-", "Pac", "-", "Poe"]

    init(coordinator: GameCoordinator) {
        self.coordinator = coordinator
        _elapsed = State(initialValue: coordinator.titleEntranceConsumed ? 0.62 : 0)
    }

    static func measuredWidths(size: CGFloat) -> [CGFloat] {
        let font = UIFont(name: "Fredoka-SemiBold", size: size) ?? .systemFont(ofSize: size, weight: .semibold)
        return parts.map { ($0 as NSString).size(withAttributes: [.font: font, .kern: -0.35]).width }
    }
    static func fittedSize(nominal: CGFloat, width: CGFloat) -> CGFloat {
        var low: CGFloat = 1; var high = nominal
        for _ in 0..<24 {
            let mid = (low + high) / 2
            if measuredWidths(size: mid).reduce(0, +) <= width - 2 { low = mid } else { high = mid }
        }
        return min(nominal, low)
    }
    var body: some View {
        GeometryReader { geometry in
            let size = Self.fittedSize(nominal: nominalSize, width: geometry.size.width)
            let widths = Self.measuredWidths(size: size)
            HStack(spacing: 0) {
                ForEach(0..<5) { index in
                    let group = index == 0 ? 0 : (index < 3 ? 1 : 2)
                    let progress = reduced ? 1 : FormMotion.titleProgress(elapsed: elapsed, group: group)
                    let rotation = index == 1 || index == 3 ? 0 : [-0.8, 0.6, -0.6][group]
                    Text(Self.parts[index]).font(.custom("Fredoka-SemiBold", fixedSize: size)).tracking(-0.35)
                        .foregroundStyle(index == 0 ? colors.x : index == 4 ? colors.o : index == 2 ? colors.text : colors.secondary)
                        .fixedSize().frame(width: widths[index])
                        .opacity(min(1, max(0, progress))).offset(y: 8 * (1 - progress))
                        .scaleEffect(0.95 + 0.05 * progress)
                        .rotationEffect(.degrees(rotation * (1-progress)))
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
                .environment(\.layoutDirection, .leftToRight)
                .onChange(of: geometry.size.width, initial: true) { _, width in availableWidth = width }
        }
        .frame(height: Self.fittedSize(nominal: nominalSize, width: availableWidth) * 46 / 40)
        .accessibilityElement(children: .ignore).accessibilityLabel("Pic-Pac-Poe").accessibilityAddTraits(.isHeader)
        .accessibilityIdentifier("home-wordmark")
        .task(id: reduced) {
            guard !requested else { elapsed = 0.62; return }
            requested = true
            guard !coordinator.titleEntranceConsumed else { elapsed = 0.62; return }
            await coordinator.markTitleEntranceConsumed()
            guard !reduced else { elapsed = 0.62; return }
            elapsed = 0
            let start = ContinuousClock.now
            while elapsed < 0.62 {
                do { try await Task.sleep(for: .milliseconds(16)) } catch { elapsed = 0.62; return }
                guard coordinator.isSceneActive else { elapsed = 0.62; return }
                elapsed = min(0.62, Double(start.duration(to: .now).components.attoseconds) / 1e18 + Double(start.duration(to: .now).components.seconds))
            }
        }
        .onChange(of: coordinator.isSceneActive) { _, active in if !active { elapsed = 0.62 } }
    }
}

private struct IllustrationFrameKey: PreferenceKey {
    static let defaultValue = CGRect.zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { value = nextValue() }
}

struct HomeIllustration: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.formReducedMotion) private var reduced
    let active: Bool
    let viewport: CGRect
    @State private var visible = true
    @State private var start = Date()
    private var running: Bool { active && visible }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60, paused: !running)) { context in
            let motion = FormMotion.homeSymbol(elapsed: running ? context.date.timeIntervalSince(start) : 0, reduced: reduced)
            ZStack {
                Canvas { context, size in drawScene(context: &context, size: size) }
                ZStack {
                    FormPiece(symbol: .x).opacity(motion.xAlpha).scaleEffect(motion.xScale)
                    FormPiece(symbol: .o).opacity(motion.oAlpha).scaleEffect(motion.oScale)
                }.frame(width: 44, height: 44)
            }
        }
        .frame(maxWidth: 400).frame(maxWidth: .infinity)
        .background(GeometryReader { proxy in Color.clear.preference(key: IllustrationFrameKey.self, value: proxy.frame(in: .global)) })
        .onPreferenceChange(IllustrationFrameKey.self) { visible = $0.intersects(viewport) }
        .onChange(of: running) { _, _ in start = Date() }
        .onChange(of: reduced) { _, _ in start = Date() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("A random X or O is drawn from the bag, then placed on the board.")
        .accessibilityIdentifier("home-illustration")
    }

    private func drawScene(context: inout GraphicsContext, size: CGSize) {
        let side = size.height * 0.82
        let top = (size.height - side) / 2
        let bagLeft = max(size.width * 0.2 - side / 2, 0)
        let boardLeft = size.width * 0.8 - side / 2
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: bagLeft + side*x, y: top + side*y) }
        var bag = Path()
        bag.move(to: p(0.22,0.2)); bag.addCurve(to: p(0.1,0.84), control1: p(0.14,0.39), control2: p(0,0.63))
        bag.addCurve(to: p(0.9,0.84), control1: p(0.19,1.02), control2: p(0.81,1.02))
        bag.addCurve(to: p(0.78,0.2), control1: p(1,0.63), control2: p(0.86,0.39)); bag.closeSubpath()
        var shadow = context; shadow.translateBy(x: 0,y: 2); shadow.fill(bag, with: .color(colors.shadow))
        context.fill(bag, with: .linearGradient(Gradient(colors: [colors.raised,colors.surface]), startPoint: p(0,0), endPoint: p(0,1)))
        context.stroke(bag, with: .color(colors.border), lineWidth: 1)
        let mouth = Path(ellipseIn: CGRect(x: bagLeft+side*0.19,y: top+side*0.08,width: side*0.62,height: side*0.22))
        context.fill(mouth,with:.color(colors.recess)); context.stroke(mouth,with:.color(colors.border),lineWidth:1)
        var seams = Path(); seams.move(to:p(0.31,0.51)); seams.addLine(to:p(0.26,0.79)); seams.move(to:p(0.69,0.51)); seams.addLine(to:p(0.74,0.79))
        context.stroke(seams,with:.color(colors.subtle),style:StrokeStyle(lineWidth:1,lineCap:.round))
        let boardRect = CGRect(x:boardLeft,y:top,width:side,height:side)
        let board = Path(roundedRect:boardRect,cornerRadius:side*0.12)
        shadow.fill(board,with:.color(colors.shadow)); context.fill(board,with:.color(colors.raised)); context.stroke(board,with:.color(colors.border),lineWidth:1)
        let frame = side*0.075; let gap=side*0.035; let cell=(side-frame*2-gap*2)/3
        for index in 0..<9 {
            let rect=CGRect(x:boardLeft+frame+CGFloat(index%3)*(cell+gap),y:top+frame+CGFloat(index/3)*(cell+gap),width:cell,height:cell)
            let well=Path(roundedRect:rect,cornerRadius:cell*0.19)
            context.fill(well,with:.color(colors.recess)); context.stroke(well,with:.color(index==4 ? colors.text:colors.subtle),lineWidth:index==4 ? 1.5:1)
        }
        func arrow(_ from:CGFloat,_ to:CGFloat) -> Path {
            var path=Path(); guard to-from>=4 else { return path }; let y=top+side*0.52
            path.move(to:CGPoint(x:from,y:y)); path.addLine(to:CGPoint(x:to,y:y)); path.move(to:CGPoint(x:to-4,y:y-4)); path.addLine(to:CGPoint(x:to,y:y)); path.addLine(to:CGPoint(x:to-4,y:y+4)); return path
        }
        for path in [arrow(bagLeft+side+5,size.width*0.5-26),arrow(size.width*0.5+26,boardLeft-6)] {
            context.stroke(path,with:.color(colors.secondary),style:StrokeStyle(lineWidth:1.5,lineCap:.round))
        }
    }
}
