# Final post-layout lifecycle, hosted integration and performance verification

**PASS.** The local run on 25 September 2026 used clean source `e6c0c65c9c5ad84e2f961839c5eede753c25448e`. Every tracked iOS/fixture hash was identical before and after execution. The canonical checkout remained clean at `dc7cb397ad335783c13c7976d4cef816d2fc0909`. This package records fresh results after the last product-layout fixes; earlier accepted and failed evidence remains preserved separately.

| Gate | Actual result | XCTest duration |
| --- | --- | ---: |
| Hosted Test configuration | 12 passed, 0 failed, 0 skipped | 18.344s |
| Complete Swift package | 63 passed, 0 failed, 0 skipped | 34.322s |
| Optimized production-agent audit | 1 passed, 0 failed, 0 skipped | 0.526s |

The source-discovered hosted inventory exactly matches the result bundle. All 19 capture-state restoration checks and both DEBUG fixture methods executed. The hosted suite also checks fonts/license hashes, wordmark fit, palette contrast, motion bounds, board labels, local feedback resources and transition-speech policy. Package coverage comprises 13 core, 3 fixture, 1 performance, 29 presentation, 14 production-AI and 3 search/graph tests.

The 21 presentation restoration-boundary rows cover supporting pages, Classic and Local play/terminal states, handoff, human/computer reveal, pending search, selected targets, committed placement, settlement and terminal results. Repeated inactive/background transitions, stale commands, serialization and inactive reconstruction must not redraw, replay feedback or recommit. Cancellation-unaware stale AI completion and slow-persistence navigation/reveal races have explicit regressions. Actual OS background/relaunch and native SwiftUI scene persistence remain separately verified by XCUITest and interactive recordings.

## Simulator search and cadence

Existing task-owned simulator `97FAA70C-E2D7-4893-9D76-FE172A792433`, iPhone 17 Pro (`iPhone18,1`), iOS 26.5 build 23F77; Xcode 26.6 (17F113). No other simulator was booted at the measurement start, and no concurrent phase compiler/capture ran. The simulator shut down successfully at 10:59:12 UTC before the host-package runs. This is a shared host without thermal isolation.

The actual [compiler context](compilation-context.json) is **Test**, with application/tests `-Onone`, `TESTING` and `DEBUG`; package modules use `-O` and whole-module optimization. All hosted modules retain `-profile-generate` and `-profile-coverage-mapping`. These are production algorithms/views inside an instrumented Test app, **not production Release app pacing**. Independent unsigned distribution Release builds cover exclusion of instrumentation and development hooks.

Three production-search samples on the simulator:

| Measurement | Range | Median |
| --- | ---: | ---: |
| Cold Hard | 76.871500–77.285834ms | 77.230875ms |
| Warm Hard | 0.020209–0.022833ms | 0.022459ms |
| MCTS, exactly 2,000 simulations | 23.446875–24.048583ms | 23.898125ms |
| In-flight Hard cancellation after request | 0.107125–1.241625ms | 0.228167ms |

Cold Hard visited 109,706 nodes; warm Hard visited 18. The opening chooses center with value 5/21. Cancellation was requested 5ms after starting a fresh cold search; every request returned `CancellationError`.

| Visible scenario | Callback samples | Callback p95 / maximum | Display-timestamp p95 / maximum |
| --- | ---: | ---: | ---: |
| Home normal motion | 186 | 16.824 / 17.189ms | 16.667 / 16.667ms |
| Home Reduce Motion | 185 | 16.791 / 17.133ms | 16.667 / 16.667ms |
| Live Hard reveal→target→place→settle | 184 | 16.697 / 22.113ms | 16.667 / 16.667ms |

All actual callback arrays and their five largest samples were inspected. No callback exceeded 25ms; no delivered display timestamp interval exceeded 1.5 nominal intervals. The few Hard callback variations (largest 22.113ms, final sample) are below that threshold and do not show a long main-runloop stall. The live stage sequence was `turnStart → revealing → aiTargeting → aiPlacing → aiSettling → turnStart → revealing → playing`, with one actual search. This search finished during reveal, so a thinking stage was unnecessary; delayed-search thinking has separate controlled coverage. Target remained empty until placement; computer identity and input lock persisted through settlement.

CADisplayLink requested 60Hz. Timestamps measure display opportunities; callback intervals measure main-runloop delivery. Neither is GPU completion or physical-device frame pacing. The broad one-second hang assertion was not used as the sole acceptance basis.

## Home temporal and state-independence evidence

[Ten original 1206×2622 frames](home-frames/manifest.json), the [uncropped contact sheet](home-contact-sheet.png), and raw attachment metadata preserve exact observed monotonic times and PNG hashes. All ten originals were opened at original resolution and the contact sheet was inspected. Both normal and Reduced Motion sequences visibly switch X→O. Inactive pairs retain identical full pixel buffers for 1.625535s and 1.628424s, respectively.

The 0.0/0.34 samples are identical and do not independently demonstrate continuous pulse amplitude; the separate full-screen normal-motion recording supplies that evidence. Requested offsets are not synchronized animation phases. These real UIWindow hierarchy captures include its safe areas but exclude the OS status bar, and complement the full-screen simulator recordings rather than replacing them.

Both sequences retain unchanged game state, zero draw-source calls, no feedback and zero decorative snapshot writes. Suspension's lifecycle checkpoint is counted separately. The repeating illustration is deterministic elapsed-time state, absent from the persisted game, with a stable accessibility description and no speech/audio/haptic route. The hosted UIHostingController explicitly installs active scene state, then deactivates the coordinator and pauses the Timeline. It uses SceneStorage defaults and does not itself simulate an OS background event; actual app scene persistence and OS interruptions belong to the UI/interactive evidence.

## Optimized host search measurements

Apple M3 Pro arm64, macOS 26.5.2, Swift 6.3.3. Five samples of the production agents compiled in Release:

| Measurement | Range | Median |
| --- | ---: | ---: |
| Cold Hard | 75.477959–77.157333ms | 75.767875ms |
| Warm Hard | 0.016250–0.034833ms | 0.017500ms |
| MCTS, exactly 2,000 simulations | 22.168125–23.234708ms | 22.847500ms |
| In-flight Hard cancellation | 0.108958–0.238084ms | 0.231542ms |

The 2ms main-actor heartbeat collected 176 samples, p95 3.069916ms and maximum 3.089167ms. This demonstrates main-actor progress during search, not display frame pacing. All Debug samples remain retained: cold Hard 1,822.860–1,837.740ms and MCTS 462.992–490.712ms under unoptimized host-package compilation. No MCTS budget, solver behavior or production timing was weakened.

## Provenance, warnings and release boundaries

[Source before](source-before.json), [source after](source-after.json), [built app hashes](built-app.json), [exact commands](commands.json), [hosted inventory](hosted-inventory.json), [measurements and all raw callback samples](measurements.json), [xcresult](hosted.xcresult/Info.plist), and [checksums](checksums.sha256) accompany the complete logs. The export contains 12 attachments: ten PNG frames and two measurement JSON records.

The log retains the known UIKit-host SceneStorage warning. After all 12 tests passed, Xcode's optional simulator-diagnostic collection also reported `xcrun` could not locate `simctl` (error 72). The outer `xcodebuild` command returned zero, the complete exact test inventory passed, xcresult and every attachment exported, and per-process `DEVELOPER_DIR` simulator commands succeeded. This is an auxiliary diagnostics warning, not omitted test execution or a gameplay failure; the global developer directory was not altered to suppress it.

No physical-device, iOS 17 runtime or GitHub-hosted Actions execution is claimed. Physical VoiceOver spoken traversal/focus/timing, Switch Control hardware, external-keyboard focus recovery and successful placement after rematch, real sound/haptic quality, device frame pacing and minimum-runtime execution remain owner-controlled release checks. A generic device-SDK build is compilation only. Nothing was pushed, signed, uploaded or submitted. This report accepts its lifecycle/integration/performance gates; the consolidated unsigned-RC verdict also requires the independently recorded final UI, snapshot, fixture, Android, build and handoff gates.
