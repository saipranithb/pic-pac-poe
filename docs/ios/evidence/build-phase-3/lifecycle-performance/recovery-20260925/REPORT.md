# Final lifecycle, hosted integration and performance verification

**PASS.** The accepted local run used source checkpoint `03d44f794ef98e66ebeb63cd467313ece2331b91` on 25 September 2026. All tracked iOS/fixture source hashes were unchanged across execution. The canonical checkout remained clean at `dc7cb397ad335783c13c7976d4cef816d2fc0909`. Exact commands, timestamps, build identity, source fingerprints and raw samples accompany this report.

| Gate | Result | XCTest duration |
| --- | --- | ---: |
| Hosted Test configuration | 12 passed, 0 failed, 0 skipped |18.348s |
| Complete Swift package | 63 passed, 0 failed |34.346s |
| Optimized production-agent performance | 1 passed, 0 failed |0.522s |

The hosted inventory verifier compared every parsed XCTest method with the result bundle. Both DEBUG fixture tests executed in the Test configuration: all 19 capture states restored exactly, and Settings control states matched canonical expectations. The suite also verified fonts/license hashes, fit, palette contrast, motion bounds, board labels, native resources and transition-speech policy. Release hook exclusion belongs to the independent unsigned Release build gate.

The package includes 29 presentation tests and 21 restoration-boundary rows. Each boundary is restored, repeatedly suspended, sent stale clock/action commands, serialized, reconstructed inactive and resumed without redrawing, replaying feedback or recommitting a move. It covers pending AI work, selected targets, committed moves and terminal settlement. Cancellation-unaware stale AI completion and slow-persistence navigation/reveal races are explicitly rejected.

## Simulator search and cadence

Existing simulator `97FAA70C-E2D7-4893-9D76-FE172A792433`, iPhone 17 Pro (`iPhone18,1`), iOS 26.5, Xcode 26.6 (17F113). No other simulator was booted; no concurrent phase build/capture ran. The simulator was shut down before host-package measurements. This remains shared-host simulator evidence, without thermal isolation or GPU-completion measurement.

The hosted **Test** build compiled application/tests with `-Onone`, `TESTING` and `DEBUG`, and Swift package modules with `-O` plus whole-module optimization. Hosted compile commands included `-profile-generate` and `-profile-coverage-mapping`. These are measurements of the production algorithms and views inside an instrumented Test app, **not production Release app pacing**. Independent distribution Release gates verify no instrumentation or development hooks.

Three actual simulator production-search samples:

| Measurement | Range |
| --- | ---: |
| Cold Hard |77.196–79.017ms |
| Warm Hard |0.016750–0.022000ms |
| MCTS, exactly 2,000 simulations |23.434–25.013ms |
| Hard cancellation after request |0.217625–2.661875ms |

Hard visited 109,706 nodes cold and 18 warm; the opening result was center with value 5/21. Each cancellation was requested 5 ms after starting a fresh cold search and returned `CancellationError`.

| Visible scenario | Callback samples | Callback p95 / maximum | Display-timestamp p95 / maximum |
| --- | ---: | ---: | ---: |
| Home normal motion |184 |16.955 /33.004ms |16.667 /16.667ms |
| Home Reduce Motion |185 |16.809 /33.345ms |16.667 /16.667ms |
| Live Hard reveal→target→place→settle |183 |16.687 /20.288ms |16.667 /16.667ms |

The two Home maxima were isolated approximately two-interval callbacks at sample indices 1 and 13; neither repeated or occurred during live AI. The actual callback arrays were inspected beyond the one-second hang assertion. No long main-runloop stall was observed. CADisplayLink was requested at 60 Hz; delivered timestamps describe display opportunities, while callback intervals describe main-runloop delivery. These measurements do **not** prove GPU frame completion or physical-device performance.

The live Hard sequence was exactly `turnStart → revealing → aiTargeting → aiPlacing → aiSettling → turnStart → revealing → playing`. The real worker performed one search; the target remained empty until placement; placement/settlement held the computer identity and disabled input. This particular search completed during reveal, so thinking was correctly unnecessary; slow-search thinking has separate governed UI and coordinator coverage.

## Home temporal and persistence proof

[Ten original full-resolution frames](home-frames/manifest.json) and the [uncropped contact sheet](home-contact-sheet.png) show real Home X→O alternation in both modes, then identical inactive X frames over at least 1.5 seconds. Every exported image was opened at original resolution. The frame sequence uses the actual visible UIWindow at 1206×2622; the OS status bar is outside that UIKit window capture. It is not a substitute for the independent full-screen simulator recordings in `interactive-motion`.

The hosted UI uses the real application views in a visible UIKit UIHostingController with an explicitly active scene environment. SceneStorage defaults are used in this harness. Actual SwiftUI scene persistence and OS interruptions are independently covered by XCUITest and interactive recordings. The harness then deactivates the coordinator and verifies identical full pixel buffers while the Home Timeline is paused. It does not claim to simulate an OS background transition by itself.

Both sequences held the game state unchanged, consumed zero draw calls, produced no feedback, and wrote zero decorative snapshots. The one lifecycle checkpoint on suspension is separately counted. Home's 1.4 second X/O switch and bounded pulse/Reduce Motion scale invariants also passed their pure-contract test; independent simulator recordings show the continuous pulse. Observation offsets are recorded exactly and are not presented as synchronized animation phases.

## Host measurements

Apple M3 Pro arm64, macOS 26.5.2, Swift 6.3.3. Five samples using production agents compiled in Release:

| Measurement | Range | Median |
| --- | ---: | ---: |
| Cold Hard |75.243–75.522ms |75.335ms |
| Warm Hard |0.014125–0.018709ms |0.015417ms |
| MCTS, exactly 2,000 simulations |21.954–22.785ms |22.582ms |
| In-flight Hard cancellation |0.055208–0.242541ms |0.170083ms |

The 2 ms main-actor heartbeat collected 177 samples, p95 3.059ms and maximum 3.068 ms. This demonstrates main-actor progress during search and is distinct from the simulator cadence evidence above. Debug timing and all prior slower measurements remain retained. No MCTS budget, solver behavior or production timing was weakened.

## Provenance and earlier failures

- [Source before](source-before.json), [source after](source-after.json), [built Test app hashes](built-app.json), [exact commands](commands.json), [hosted inventory](hosted-inventory.json), [all raw measurements](measurements.json), [xcresult](hosted.xcresult/Info.plist).
- [Earlier failed/interrupted logs](prior-runs/DISPOSITION.md) are preserved and rejected for final acceptance. Their token and missing UIKit scene-environment harness errors were fixed; no passing final result is inferred from those logs.
- [Checksums](checksums.sha256) cover every delivered file other than the checksum file itself.

No runtime was installed; the global developer directory was not changed. No iOS 17 runtime or physical-device validation is claimed. Physical sound, haptics, VoiceOver/Switch Control and physical frame pacing remain owner/device release gates. These commands ran locally; GitHub-hosted Actions have not executed. Nothing was pushed, signed, uploaded or submitted.
