import AVFoundation
import PicPacPresentation
import UIKit

/// Ephemeral native feedback. The coordinator consumes each event before playback;
/// neither audio completion nor haptics can advance a game.
@MainActor
final class FeedbackService {
    private let selection = UISelectionFeedbackGenerator()
    private let contact = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()
    private var player: AVAudioPlayer?

    func play(_ kind: FeedbackKind, settings: AppSettings) {
        if settings.hapticsEnabled {
            switch kind {
            case .reveal: selection.selectionChanged()
            case .place, .draw: contact.impactOccurred(intensity: kind == .draw ? 0.45 : 0.6)
            case .win: success.notificationOccurred(.success)
            }
        }
        guard settings.soundEnabled else { return }
        do {
            // Ambient follows the silent switch and mixes unobtrusively with other audio.
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
            player?.stop()
            let next = try AVAudioPlayer(data: Self.wave(kind))
            next.volume = 0.22
            next.prepareToPlay()
            next.play()
            player = next
        } catch {
            // Feedback failure never affects play or changes the saved preference.
            player = nil
        }
    }
    func stop() {
        player?.stop(); player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }
    static func wave(_ kind: FeedbackKind) -> Data {
        let sampleRate = 22_050
        let duration = kind == .win ? 0.18 : 0.07
        let count = Int(Double(sampleRate) * duration)
        let frequency: Double = switch kind { case .reveal: 660; case .place: 520; case .win: 780; case .draw: 440 }
        var samples = Data(capacity: count * 2)
        for index in 0..<count {
            let t = Double(index) / Double(sampleRate)
            let envelope = min(1, t / 0.008) * max(0, 1 - t / duration) * max(0, 1 - t / duration)
            let note = kind == .win && t >= 0.09 ? frequency * 1.25 : frequency
            let value = Int16(sin(2 * .pi * note * t) * envelope * 16_000)
            let raw = UInt16(bitPattern: value)
            samples.append(UInt8(raw & 255)); samples.append(UInt8(raw >> 8))
        }
        var data = Data()
        func ascii(_ value: String) { data.append(contentsOf: value.utf8) }
        func int16(_ value: UInt16) { data.append(UInt8(value & 255)); data.append(UInt8(value >> 8)) }
        func int32(_ value: UInt32) { for shift in stride(from: 0, through: 24, by: 8) { data.append(UInt8((value >> shift) & 255)) } }
        ascii("RIFF"); int32(UInt32(36 + samples.count)); ascii("WAVEfmt "); int32(16)
        int16(1); int16(1); int32(UInt32(sampleRate)); int32(UInt32(sampleRate * 2)); int16(2); int16(16)
        ascii("data"); int32(UInt32(samples.count)); data.append(samples)
        return data
    }
}
