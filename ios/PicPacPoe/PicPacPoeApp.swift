import SwiftUI

@main
struct PicPacPoeApp: App {
    var body: some Scene {
        WindowGroup {
            PicPacPoeShell()
        }
    }
}

private struct PicPacPoeShell: View {
    var body: some View {
        Text("Pic-Pac-Poe")
            .font(.title)
            .accessibilityAddTraits(.isHeader)
    }
}
