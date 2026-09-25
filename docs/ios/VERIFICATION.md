# Native iOS verification record

This record distinguishes executed local evidence, inspected workflow definitions, and external release gates. The consolidated [Build Phase 3 report](evidence/build-phase-3/REVIEW.md) and [accepted evidence manifest](evidence/build-phase-3/manifest.json) are the authoritative closure record. The [Build Phase 2 report](evidence/build-phase-2/REVIEW.md) remains historical evidence.

## Source and environment

| Item | Value |
| --- | --- |
| Canonical checkout, unchanged | `dc7cb397ad335783c13c7976d4cef816d2fc0909` |
| Implementation branch / Phase 2 base | `codex/ios-native-parity` / `9423f47b34daee71cb5ed340cf0194c941e71b92` |
| Final product/build-input revision | `e6c0c65c9c5ad84e2f961839c5eede753c25448e`; later evidence-only descendants are identified in each manifest |
| Host | macOS 26.5.2 (25F84), Apple M3 Pro, arm64 |
| Xcode / Swift | 26.6 (17F113) / 6.3.3, Swift 6 language mode |
| Deployment target | iOS/iPadOS 17.0, verified in actual simulator and device SDK Mach-O slices |
| Executed runtime | Installed iOS Simulator 26.5, build 23F77 |

Commands use `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` per process. Global developer selection and installed runtimes were not changed. No connected compatible physical device or installed iOS 17 runtime was available. Compilation with the iOS 17 deployment target is not minimum-runtime execution.

## Executed local gates

| Gate | Actual result |
| --- | --- |
| Shared fixture governance | Eight groups / 36 exact IDs consumed in Kotlin and Swift; SHA-256 `c68308f7a6b3684cc413bc37f495a7dfade8e5bd7a928f4273d79cbb101021ae`; unknown groups/schema rejected, scripted random call order and presentation/restoration behavior checked |
| Android regression | 86 JVM executions / 57 unique tests, zero failures/errors/skips; clean lint; Debug and test APKs, unsigned AAB; 26 instrumentation tests passed in 233.485 s on Pixel_8 Android 16 / API 36 arm64 |
| Swift package | 63 tests passed, zero failures, 34.322 s; exhaustive core/fixture/AI/presentation/restoration suites |
| Hosted integration | All 12 source-discovered methods passed, zero skips, 18.344 s; iPhone 17 Pro / iOS 26.5 |
| Player-driven UI | 26 tests passed, zero failures/skips; 15 complete ordinary-timing games, 28 native audit viewports and 98 original timestamped frames; 1,036.414 s test execution / 1,063.142 s result-bundle duration |
| Governed snapshot baseline and repeat | 336 reviewed originals and 336 independent repeats: all PASS, minimum 99.7961185681% full-frame agreement at 2/255 per-channel tolerance |
| Unsigned build matrix | All four Debug/Release × simulator/device SDK builds passed; no signing material, correct resources/identity/platform/minimum. Both Release artifacts exclude testing controls and LLVM coverage instrumentation |
| Performance audit | Optimized host test passed; actual simulator search and visible UIKit-hosted view measurements retained separately below |
| Handoff | Ordinary verifier: seven JSON documents, 243 relative links, 131 hashes, zero errors; strict failure solely for deliberately null `releaseCommit` and `releaseTag` |

Android runtime and shared JSON inputs are unchanged from the verified product. The retained complete regression run remains applicable; the native CI and verification tooling changes do not change Kotlin runtime behavior. Detailed commands, log hashes, counts and the unsigned AAB identity are in the [CI contract audit](evidence/build-phase-3/CI-CONTRACT-AUDIT.md).

Actual Release artifacts are checked for bundle `dev.saipranith.picpacpoe`, version 2.0.0/build 1, iPhone/iPad support, minimum iOS 17.0, native icon and theme-aware launch color, exact Fredoka/OFL and production policy bytes, absent signing material/test bundles, absent Debug launch switches/types, and absent LLVM coverage/profile sections. Device SDK compilation is not physical-device execution. See the [four-build manifest](evidence/build-phase-3/unsigned-builds-layout-final-20260925/manifest.json).

## Behavior, accessibility and visual acceptance

The application keeps Form Playground 2.0's warm moulded-resin styling, semantic coral X/pistachio O, Fredoka wordmark, dimensional pieces and tactile controls. All 20 accepted Phase 2 originals were inspected at full resolution against canonical Android references. The [parity audit](evidence/build-phase-3/PARITY-AND-VISUAL-AUDIT.md) records intentional native ergonomics and material before/after fixes. Android-to-iOS comparison uses semantic geometry and intent, not blanket pixel equality.

Final visual acceptance requires exact semantic tokens, strings, state and clock configuration; geometry within 0.5 point of specified formulas; wordmark optical centering within 1 point; and zero tolerance for clipping, actor/symbol ambiguity, privacy leaks, moving targets or premature results. Governed same-toolchain regression compares every full-frame sRGB RGBA pixel: at least 99.5% agree with at most 2/255 variance in every channel. Baselines require explicit inspection of each original capture; comparison cannot replace them. See [snapshot governance](evidence/build-phase-3/SNAPSHOT-GOVERNANCE.md).

Lifecycle tests cover 21 interruption/restoration boundary rows, pending and cancellation-unaware AI workers, selected targets, committed moves and terminal settlement. Inactive scenes cannot accept gameplay commands or start a stale worker. Restoration does not replay draws, moves, speech, sounds or haptics. Interactive recordings supplement deterministic tests with ordinary production AI/RNG and presentation timing.

Accessibility checks include labels/headings/traits, row-major board traversal, modal privacy, 44-point targets, semantic contrast, Dynamic Type, landscape reachability, locked stages and announcement deduplication. Native automation preserves every finding. Its sole permitted artifact is the exact already-reviewed dark AI Lab offscreen paragraph at the bottom viewport after its fully visible top audit passes; its visible sliver is under 1 point and authored text contrast is 9.856:1. The final run emitted exactly one such acknowledged finding and zero unhandled findings; this is not zero native findings. At source `bf384da11190cb45ff21866a6d804c85a35ce333`, simulator Full Keyboard Access was exercised interactively through a complete Classic game, result and keyboard-activated alternate-starter rematch. No subsequent keyboard move is recorded. An additional Release follow-up launched successfully but the computer-use bridge hung before interaction; it is not counted as a pass. Physical VoiceOver, Switch Control and keyboard ergonomics, including focus recovery after rematch, remain explicitly open.

## Measured responsiveness

Instrumented hosted Test configuration on the iOS 26.5 simulator measured three samples per operation: cold Hard 76.871500–77.285834 ms, warm 0.020209–0.022833 ms, exactly 2,000 MCTS simulations in 23.446875–24.048583 ms, and cancellation 0.107125–1.241625 ms. The separate optimized host package measured five samples: cold Hard 75.477959–77.157333 ms, warm 0.016250–0.034833 ms, MCTS 22.168125–23.234708 ms and cancellation 0.108958–0.238084 ms.

Visible UIKit-hosted app views measured 186/185/184 CADisplayLink callbacks for normal Home, Reduce Motion Home and live Hard. Callback p95/max was 16.824/17.189 ms, 16.791/17.133 ms and 16.697/22.113 ms respectively. No callback reached 25 ms and no delivered timestamp interval exceeded 1.5× nominal 60 Hz in these samples. These instrumented Test observations distinguish display timestamps and callback-arrival intervals; they do not measure Release GPU completion or physical-device pacing. A separate 2 ms main-actor heartbeat had 176 samples with p95/max 3.069916/3.089167 ms. Raw arrays and exact configuration are retained.

Active Home alternates X/O in both motion modes. Inactive full-window pixels remain identical. Gameplay state, RNG calls, decorative-loop persistence and feedback remain unchanged. Ordinary interactive recordings from `bf384da11190cb45ff21866a6d804c85a35ce333` also verify unchanged persisted file bytes and modification times and show the normal pulse. Home motion source is unchanged through final `e6c0c65`; older gameplay movies retain their earlier layout provenance and are complemented by current-source UI frames. See [final lifecycle/performance evidence](evidence/build-phase-3/lifecycle-performance/post-layout-20260925/REPORT.md) and [interactive motion](evidence/build-phase-3/interactive-motion/README.md).

## Unsigned CI contract and reproducible commands

Both workflow definitions use read-only permissions and SHA-pinned actions; checkout does not persist credentials. Separate visible gates cover Kotlin/shared fixtures and Android regressions/UI flows; native package/shared fixtures; hosted integration; player-driven XCUITest; approved snapshots; and unsigned Debug/Release builds for simulator and device SDK. Exact XCTest inventory guards reject missing, duplicate, failed or skipped methods. Environment assertions fail visibly if the pinned compiler/runtime is unavailable. No runtime is installed to hide that failure.

Workflow definitions were inspected and their commands reproduced locally. **GitHub-hosted Actions has not executed**, because pushing and dispatching remain unauthorized. Local Android instrumentation used the installed API 36 emulator; the workflow's API 35 emulator remains a future hosted execution environment.

From repository root, use fresh output paths and the already available simulator UDID:

```sh
python3 docs/ios-handoff/verify-handoff.py
python3 docs/ios-handoff/verify-handoff.py --strict-release
python3 ios/Scripts/verify-ci-contract.py
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path ios/Packages/PicPacKit
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -project ios/PicPacPoe.xcodeproj -scheme PicPacPoe -configuration Test -destination 'platform=iOS Simulator,id=<available-UDID>' -only-testing:PicPacPoeTests -parallel-testing-enabled NO -derivedDataPath <fresh-path> -resultBundlePath <fresh.xcresult> CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO test
python3 ios/Scripts/ui_phase3.py --destination <available-UDID> --output <fresh-evidence-path> --derived-data <fresh-derived-data-path>
python3 ios/Scripts/snapshot_phase3.py capture --output <fresh-candidate-path>
python3 ios/Scripts/snapshot_phase3.py compare --baseline docs/ios/evidence/build-phase-3/baselines --candidate <fresh-candidate-path> --output <fresh-report.json>
```

Unsigned build gates use the supported `ENABLE_CODE_COVERAGE=NO CLANG_ENABLE_CODE_COVERAGE=NO CLANG_COVERAGE_MAPPING=NO` settings. The historical rejected `-enableCodeCoverage NO` build command is preserved, not counted as a pass. Strict handoff verification is expected to fail only for the approved null Android release identity, never for an unrelated error.

## Remaining external release gates

- Already available compatible iOS 17 runtime execution: launch, play, restoration and native presentation on the declared minimum OS.
- Approved physical iPhone/iPad: VoiceOver speech/focus/modal isolation, Switch Control and external keyboard, actual sound/haptics, thermal/memory/frame pacing, fresh install and documented test-build update behavior.
- Actual hosted CI after owner-authorized Git promotion.
- Owner-supervised Apple signing/account/identifier/archive/TestFlight work; later store metadata, legal URLs, privacy declarations and submission.
- Separate Android signed-release identity remains deliberately null.

None of these is claimed by simulator evidence. No push, PR, merge, tag, signing, credentials, upload, store-console access or production submission occurred in this build phase. The [single App Store Phase 1 prompt](evidence/build-phase-3/APP-STORE-PHASE-1-PROMPT.md) includes the proposed owner-controlled Git promotion sequence.
