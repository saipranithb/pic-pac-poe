# Build Phase 3 — Parity, Integration and Hardening

**PASS — complete unsigned release-candidate codebase.** All ordinary Phase 3 software, parity and local verification gates are complete. The explicitly unavailable physical-device, iOS 17 runtime and GitHub-hosted CI checks remain owner-controlled release gates; this is not a signed or submission-ready release.

## Recovery and provenance

Recovery on 2026-09-25 classified the surviving state as **partially complete and dirty**. Four valid checkpoints through `ad1a3b4` survived. The complete prior 24-test UI run, Android regression results and five interactive motion recordings passed their stored checksums. Dirty Release coverage exclusions, strict XCTest inventory and hosted harness fixes were inspected and preserved. No implementation work was reset, replaced or discarded. The exact read-only audit and diffs are in `recovery-20260925/`.

The sole orphaned capture belonged to the task-owned Manual simulator `2E93AE9F-D234-4B40-8F3D-254C53B9A6EC`. The shutdown request finalized its original MOV and preserved its sidecar in `/tmp/picpac-phase3-release-keyboard/`, but its device later remained Shutting Down. Exact stale task-owned shutdown/boot commands were identity-checked and terminated without resetting or deleting simulator data. Its incomplete play session is not accepted as a completed keyboard test. No build/test process survived. A further Release keyboard follow-up reached app launch on the separate existing Performance simulator, but the computer-control bridge hung twice before UI interaction (846 seconds and 282 seconds despite a requested timeout). That attempt is an environment limitation, not an accepted keyboard pass. Earlier actual Debug Full Keyboard Access evidence covers complete Classic play, result and Rematch; post-rematch Release keyboard placement is not claimed, and focus recovery plus a successful first move after rematch remain explicit hardware release checks. The canonical checkout was independently clean at `dc7cb397ad335783c13c7976d4cef816d2fc0909`.

Implementation stays in the original `/Users/bavabangaru/Developer/pic-pac-poe-ios-native-parity` worktree, branch `codex/ios-native-parity`, descended from Build Phase 2 base `9423f47b34daee71cb5ed340cf0194c941e71b92`. No push, PR, merge, tag, signing, secrets, runtime installation, global developer-directory change, upload or store-console operation occurred.

## Product and ordinary defects

The Form Playground 2.0 identity is retained: actual Fredoka wordmark, warm moulded-resin surfaces, semantic coral X/pistachio O, separate teal/lavender actors, recessed board, dimensional pieces, bounded motion, Home bag-to-board explanation and native SwiftUI interactions. All 20 accepted Phase 2 captures were opened individually at original resolution and compared directly with canonical Android references. The final 336-image matrix uses iPhone SE (3rd generation), iPhone 17 Pro and iPad Pro 11-inch (M4, 8 GB), with Large, XXXL, AX3 and Reduce Motion profiles in both themes. Separate UI tests reach AX5 and landscape. The complete comparison and before/after evidence are in `PARITY-AND-VISUAL-AUDIT.md` and `defects/`.

Corrected ordinary defects include inactive human commands consuming gameplay events; AI work starting after scene suspension; stale asynchronous navigation replacing a newer screen; replayed/duplicate restoration speech; propagated accessibility identifiers and result-board child leakage; canonical winning-line/selection geometry; Home difficulty lost through supporting navigation; missing app icon/unconfigured native launch background; Android-specific permission wording; small probability-footer legibility; scrolled text entering the status area; and singular bag grammar. Release artifacts now exclude unintended LLVM coverage instrumentation as well as Debug launch controls. Exact test-inventory gates prevent partial or skipped suites appearing green.

The expanded final visual audit rejected its first 336-frame candidate before any baseline approval. At AX3, the fixed tutorial marker column separated digits from their periods, while changing instruction wrapping, held-piece space and footer/status heights moved the board between stages. Intrinsic single-line markers and native SwiftUI measurement envelopes now retain complete text and stationary controls; hidden measurement text is excluded from accessibility and hit testing. Two focused regressions passed: all nine cell rectangles matched within 0.5 point in 24 stage/size/anchor cases, and eight AX3/AX5 tutorial row cases stayed reachable. Every tutorial original was inspected for painted punctuation. Full before/after evidence remains in `defects/large-type-layout-20260925/`; the rejected candidate was preserved.

Verification defects were also fixed and preserved honestly: the new hosted UIKit harness lacked an active SwiftUI scene environment and its speech fixture used an invalid turn token; the first recovered unsigned command used an Xcode test-only coverage option with a build action. Those failed attempts are historical evidence, not accepted passes.

## Executed gates

| Gate | Actual local result |
| --- | --- |
| Shared fixtures | 8 governed groups, 36 exact IDs, Kotlin/Swift bytes SHA-256 `c68308f7a6b3684cc413bc37f495a7dfade8e5bd7a928f4273d79cbb101021ae`; unchanged Android/shared runtime inputs |
| Android | 86 JVM test executions / 57 unique tests, zero failures/errors/skips; clean lint; Debug/test APK and unsigned AAB; 26 UI instrumentation tests in 233.485 s on Pixel_8 Android 16/API 36 arm64 emulator |
| Swift package | 63 tests, zero failures, 34.322 s; exhaustive rules/AI/fixture/presentation/restoration regressions |
| Hosted integration | 12 tests, zero failures/skips; exact inventory gate, all 19 capture fixtures and Settings states executed; actual iPhone 17 Pro / iOS 26.5 simulator |
| Unsigned builds | Debug and Release × simulator and device SDK: all 4 pass; actual Release Mach-O platform/minimum 17.0, complete native resources, no signing material, test controls or LLVM coverage sections |
| Player UI | 26 tests passed, zero failures/skips; 15 complete ordinary-timing games, 28 native audit viewports and 98 original timestamped frames; 1,036.414 s test execution / 1,063.142 s result-bundle duration |
| Governed snapshots | 336 reviewed originals and 336 independent repeats: all PASS, minimum 99.7961185681% full-frame agreement at 2/255 per-channel tolerance |
| Handoff | Ordinary portable verifier: 7 JSON documents, 243 links, 131 hashes, 0 errors; strict fails solely on intentionally null releaseCommit/releaseTag |

The final-source hosted run passed 12/12 with zero skips in 18.344 s, package 63/63 in 34.322 s, and the optimized audit 1/1 in 0.526 s. The separate complete UI inventory passed all 26 methods with zero skips; all 37 required frame families are present in 98 original timestamped frames. The final simulator was shut down successfully.

The final four-build source is `e6c0c65c9c5ad84e2f961839c5eede753c25448e`, with exact input/binary hashes in `unsigned-builds-layout-final-20260925/manifest.json`. Later evidence-only commits must not be mistaken for new product binaries. Xcode 26.6/17F113, Swift 6.3.3, macOS 26.5.2 arm64 and the already-installed iOS 26.5/23F77 runtime were used via per-command DEVELOPER_DIR. No hosted GitHub Actions run is claimed: workflow definitions were inspected and commands reproduced locally; pushing remains unauthorized.

## Measured responsiveness and Home isolation

Actual simulator hosted search, three samples per operation: cold Hard **76.871500–77.285834 ms**, warm Hard **0.020209–0.022833 ms**, MCTS exactly **2,000 simulations in 23.446875–24.048583 ms**, and cancellation **0.107125–1.241625 ms** after a request made 5 ms into a live search. Cold/warm search visited 109,706/18 nodes. The instrumented Test app and tests use `-Onone` with TESTING/DEBUG; linked production Swift packages use `-O` whole-module compilation. These are not production Release display measurements.

The separate optimized host package audit, five samples per operation, measured cold Hard 75.477959–77.157333 ms (median 75.767875), warm 0.016250–0.034833 ms, MCTS 22.168125–23.234708 ms and cancellation 0.108958–0.238084 ms. Its 2 ms main-actor heartbeat had 176 samples, p95/max 3.069916/3.089167 ms; that is responsiveness evidence, not display pacing.

Visible UIKit-hosted app views produced 186/185/184 CADisplayLink callback samples for normal Home, Reduce Motion Home and live Hard respectively. Callback p95/max was 16.824/17.189 ms, 16.791/17.133 ms and 16.697/22.113 ms. There were no callbacks at or above 25 ms and no delivered timestamp interval above 1.5× nominal 60 Hz in these samples. Raw arrival intervals and display timestamps are retained separately; neither proves GPU completion or physical-device frame pacing. The prior run’s two isolated 33 ms Home gaps remain preserved under their earlier source, not removed from history.

Active Home rendering changed X/O as expected. Inactive full-window pixels remained exactly identical in both modes. Gameplay state, draw count and decorative snapshot-write count remained unchanged; feedback was absent and the stable illustration accessibility label does not announce its phase. Actual ordinary-timing Home recordings also show unchanged persisted file bytes and modification times. The finite wordmark entrance's separately governed consumed flag is not decorative-loop persistence.

The [accessibility and lifecycle boundary record](ACCESSIBILITY-AND-LIFECYCLE.md) identifies the exact automated coverage and remaining hardware checks. [Final closure outputs](final-closure-20260925/governance-commands.json) retain ordinary, strict-release, CI-contract and whitespace verification. The root `checksums.sha256` covers every final evidence file, including historical and nested checksum inventories. The complete 267-file source inventory and [source-delivery record](source-delivery.json) establish the documentation-only native delta.

## Evidence and unavailable checks

The [post-fix visual overview](visual-overview/contact-sheet.png), [approved 336-image matrix](baselines/REVIEW.json), [full repeat comparison](snapshot-comparison.json) and [paired layout corrections](defects/large-type-layout-20260925/post-fix/before-after-contact-sheet.png) form the final visual set. Every original was opened individually. The complete repeat passed at 99.7961185681% minimum full-frame agreement with 2/255 per-channel tolerance; 131 images were pixel-identical. All observed residual differences were confined to the OS Home indicator, and those pixels remained in the gate. [The calibration analysis](snapshot-calibration/SUMMARY.json) records the maximum 84/255 outlier and confirms all app pixels outside that system indicator’s rows were identical. No mask, crop, tolerance change or automatic baseline replacement was used. Supplemental normal-speed Home and Hard recordings from `bf384da` (their exact source is retained) plus full-frame timestamped sequences are in `interactive-motion/`. The Hard recording visibly includes reveal, optional thinking, empty target, placement, settlement and subsequent result; inspection pauses are not AI latency. Controlled UI sequences explicitly record 10×/20× presentation holds and never claim normal-speed pacing. The post-fix [hosted Home sequences](lifecycle-performance/post-layout-20260925/home-frames/manifest.json) and [player-driven UI sequences](ui-flows-layout-final-20260925/manifest.json) carry current-source timestamps and hashes. Full UIWindow captures include app safe areas but no OS status bar; simulator screenshot originals retain system UI. All retained contact sheets contain whole frames; no crop or screenshot masking is used.

The final 28-viewport native accessibility audit emitted one exact offscreen dark AI Lab introductory paragraph contrast artifact at the reviewed bottom viewport, after its fully visible top audit passed; all other viewports passed without an unhandled finding. Its visible sliver is under 1 point, with actual ink outside the viewport; measured authored text contrast is 9.856:1. All findings are retained. This is zero unhandled findings, not zero native findings. Labels, headings, traits, row-major order, modal privacy, 44 pt targets, both themes, landscape and accessibility-size reachability are separately checked.

No iOS 17 runtime or compatible connected physical device is available (fresh recovery inventory: no devices). Remaining release gates are minimum-OS launch/restoration in an already available compatible environment; physical iPhone/iPad VoiceOver speech and focus, Switch Control and keyboard hardware, audio/haptics, thermal/memory/frame-pacing and install/update behavior. Simulator AX/keyboard evidence does not close physical assistive-technology gates. Signing, TestFlight and owner-reviewed privacy/store/submission work remain outside this phase; the canonical Android release identity remains deliberately unresolved.

## Owner-controlled continuation

The consolidated `APP-STORE-PHASE-1-PROMPT.md` preserves the source/evidence map and remaining gates. Proposed Git promotion is owner-authorized push of the implementation branch → one PR → actual hosted CI and artifact inspection → review → owner-approved history-preserving merge. None was executed here.

## Local checkpoints

Every Phase 3 checkpoint through the tested product revision is listed below. The final evidence/documentation delivery commit is the descendant identified in the completion response; a commit cannot embed its own resulting SHA.

- `bb7668087f3b8b1647cac4f3af5cd3aa90bfdbf8` — Harden native parity flows, lifecycle, accessibility and unsigned gates
- `1004223b1a39fcb2d0f965b1a530bbc9dfc43424` — Pin snapshot runtime identity and verify unsigned device SDK builds
- `bf384da11190cb45ff21866a6d804c85a35ce333` — Resolve visual audit findings and govern complete simulator evidence
- `ad1a3b40570406a7cf967c600a7c88516dbf2c0c` — Correct singular bag copy found in terminal visual review
- `2a04a4858d14b22f2f7dd0d4592ed55f9d3a6e68` — Preserve recovered audit evidence and close Release instrumentation gaps
- `03d44f794ef98e66ebeb63cd467313ece2331b91` — Use supported coverage settings for unsigned build gates
- `8c4cd77d4bbf9233d301f2171261968f5e085085` — Record final unsigned builds and recovered lifecycle performance gates
- `6ef2ed9033cdcb6c8b35f22a6a47c4d9078a8c4a` — Retain governed hosted result bundle with its checksum inventory
- `8eabb669b6b154e982d82d3e8de8b34b778e337b` — Preserve bounded Release interaction bridge failure without claiming a pass
- `ea2a1a2f7ffa26a165715f8af28422a99b335c22` — Record complete final UI flows and reviewed temporal evidence
- `e6c0c65c9c5ad84e2f961839c5eede753c25448e` — Stabilize large-text gameplay geometry and tutorial markers

The final delivery changes no compiled app/test/configuration/script/resource input from `e6c0c65`. The only file under `ios/` changed afterward is the UI-test README, correcting 24 to 26 and explaining the two regressions. Because the broad snapshot fingerprint deliberately includes documentation, `source-delivery.json` records that exact README-only delta and the updated broad fingerprints; it does not falsely claim whole-tree fingerprint equality.
