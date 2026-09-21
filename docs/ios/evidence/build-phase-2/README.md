# Build Phase 2 simulator evidence

This directory documents the repeatable visual-evidence pass for the complete iOS product. The capture tool builds the Debug app unsigned, launches deterministic in-memory presentation fixtures, records actual simulator PNGs, and generates a no-crop Android-reference-versus-iOS review sheet.

The tool does not edit the app, project, package, CI configuration, global `xcode-select`, or any existing simulator. It creates an ephemeral simulator, deletes only that simulator on exit, and leaves an `INCOMPLETE` marker if capture or review-sheet generation fails.

## Run

From the repository root:

```bash
EVIDENCE_RUN_ID=phase2-rc1 ios/Scripts/capture-phase2-screenshots.sh
```

Prerequisites:

- macOS with Xcode 26.6 at `/Applications/Xcode.app`;
- the iOS 26.5 simulator runtime and iPhone 17 Pro device type;
- the Debug-only launch contract in `ios/PicPacPoe/DebugLaunchConfiguration.swift`;
- `/usr/bin/python3`, Git, and standard Xcode command-line tools.

The script applies `DEVELOPER_DIR` separately to each Xcode command. It never calls `xcode-select`. When Xcode lives elsewhere, pass its Developer directory:

```bash
XCODE_DEVELOPER_DIR=/path/to/Xcode.app/Contents/Developer \
EVIDENCE_RUN_ID=phase2-rc1 \
ios/Scripts/capture-phase2-screenshots.sh
```

Optional controls are `EVIDENCE_OUTPUT_ROOT`, `PIC_PAC_BUNDLE_ID`, `CAPTURE_SETTLE_SECONDS`, and `HOME_CAPTURE_SETTLE_SECONDS`. The Home default is intentionally 0.65 seconds so the capture lands in the stable X hold before the governed X/O transition. Change timing only when a measured simulator launch requires it, and retain the recorded value in `manifest.json`.

## Captures

Every run captures these ten states in dark and light appearance on an ephemeral **iPhone 17 Pro / iOS 26.5** simulator:

| Scenario | Debug launch scenario | Android comparison reference |
| --- | --- | --- |
| Home | `home` | Current `home-scene-<theme>-normal-412dp-x.png` |
| Human placement | `human-placement` | `human-placement-dark.png`; dark-only layout/state proxy for the light capture |
| Local privacy handoff | `local-handoff` | `local-handoff-dark.png`; dark-only layout/state proxy for the light capture |
| Local reveal | `local-reveal` | `local-reveal-dark.png`; dark-only layout/state proxy for the light capture |
| Computer targeting | `computer-targeting` | `computer-targeting-<theme>.png` |
| Computer settled | `computer-settled` | `computer-settled-<theme>.png` |
| Result | `result` | `result-<theme>.png` |
| Settings | `settings` | `settings-<theme>.png` |
| How to Play | `how-to` | `how-to-<theme>.png` |
| AI Lab | `ai-lab` | `ai-lab-<theme>.png` |

Light Android captures do not exist for the three marked Local/human states. The review sheet labels their dark reference as a proxy; use it for geometry, copy, and state evidence, then judge light colors against the exact semantic tokens and the theme-matched references for the other screens. The tool never silently substitutes a different game state.

Each app launch receives:

```text
-screenshot-scenario <scenario>
-screenshot-theme <dark|light>
-AppleLanguages (en)
-AppleLocale en_US
```

The simulator uses Large content size, a deterministic 9:41 status bar, and normal motion. Debug fixtures disable sound and haptics and hold timed presentation stages through the screenshot clock. They prove rendered state and semantics, not live AI duration or end-to-end player progression.

## Output and provenance

The default destination is:

```text
docs/ios/evidence/build-phase-2/runs/<run-id>/
├── captures/
│   └── ios-<scenario>-<theme>.png
├── checksums.sha256
├── manifest.json
├── review-contact-sheet.png
└── xcodebuild.log
```

`manifest.json` records the source commit, branch and dirty-state paths, verifies that source status remained stable during capture, records Xcode/SDK/macOS and simulator metadata, preserves every launch argument and settle interval, and links each PNG to the canonical manifest entry, provenance kind, production commit and SHA-256. `checksums.sha256` covers all captures, the final manifest, contact sheet and build log.

`review-contact-sheet.png` is produced by `ios/Scripts/GeneratePhase2ContactSheet.swift` using AppKit only. It aspect-fits every image without cropping or screenshot mutation. The four columns are dark Android, dark iOS, light Android, and light iOS. Orange labels identify dark-only proxy references.

A dirty worktree is allowed because visual review happens during implementation, but the sheet and manifest label it. Use a stable checkpoint run for consolidated owner review. Do not present a fixture capture as an observed live game, animation-performance result, VoiceOver result, or physical-device result. Build Phase 3 must still cover integrated flows, Dynamic Type, Reduce Motion, accessibility focus, audio/haptics, frame pacing, and physical hardware.
