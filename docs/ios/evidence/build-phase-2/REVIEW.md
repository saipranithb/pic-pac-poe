# Build Phase 2 visual review

Accepted run: [`phase2-complete-20260921`](runs/phase2-complete-20260921/)

- Source commit: `0f499f230e8dd5e5999eb1a4e702e0b111c789c3`
- Branch: `codex/ios-native-parity`
- Simulator: iPhone 17 Pro, iOS 26.5, Large content size
- Toolchain: Xcode 26.6 (17F113), unsigned Debug build

## Accepted evidence

- [`review-contact-sheet.png`](runs/phase2-complete-20260921/review-contact-sheet.png) compares the canonical Android reference with the actual iOS simulator frame for ten required scenarios in both themes, without cropping.
- [`manifest.json`](runs/phase2-complete-20260921/manifest.json) records clean and stable source provenance, toolchain and simulator identity, launch arguments, settings, canonical reference hashes and output hashes.
- [`checksums.sha256`](runs/phase2-complete-20260921/checksums.sha256) verifies all 20 captures, the manifest, contact sheet and build log. All 23 entries passed after generation.
- Home, human placement, Local handoff and reveal, computer targeting and settlement, result, Settings, How to Play and AI Lab were inspected in dark and light appearance.
- Both Home frames show the Fredoka wordmark, bag-to-X-to-board explanation, complete game selection controls and system chrome. Settings uses the canonical control states in each theme.

## Review correction

An earlier uncommitted candidate exposed a real launch-readiness race: its dark Home screenshot contained only the background and system chrome. That run was rejected and moved out of the worktree. The capture tool now waits for a unique Debug-only marker emitted after restoration and a main-actor yield, waits for simulator appearance changes, and rejects screenshots whose sampled central content is visually blank. The accepted run was regenerated from a clean source commit after that fix.

## Scope of this evidence

These images establish the complete rendered product state and visual identity on the recorded simulator. They do not claim player-driven flow coverage, animation frame pacing, assistive-technology behavior or physical sound/haptics. Those remain explicit Build Phase 3 or device-validation gates in [`VERIFICATION.md`](../../VERIFICATION.md).
