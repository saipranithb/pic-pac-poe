# Build Phase 3 lifecycle and AI audit

**Final recovery verification passed:** [accepted 25 September run](recovery-20260925/REPORT.md) at source `03d44f794ef98e66ebeb63cd467313ece2331b91`: **63 package tests, 12 hosted integration tests with zero skips, and the optimized performance audit**, all passing. It includes actual simulator search/callback measurements and original full-resolution Home active/inactive frame sequences. [Final checksums](recovery-20260925/checksums.sha256).

The earlier results below remain historical evidence of defect reproduction and refinement; their 62-test count is superseded by the final 63-test run.

The earlier complete package regression passed **62 tests, zero failures**. A later adversarial navigation refinement passed its two targeted regressions; the final consolidated package run is recorded separately when complete. The optimized production-agent audit also passed. These are local executions; they do not claim GitHub-hosted CI, iOS 17 runtime coverage or physical-device validation.

## Defects corrected

1. **Inactive human actions:** queued placement and Ready commands could still change the board or consume a draw after scene suspension. Both commands now require the active scene. The complete inactive/background matrix rejects every cell and Ready without state, feedback or RNG changes.
2. **Stale post-persistence AI launch:** computer reveal awaited persistence, then launched captured search even after a replacement game. Launch now rechecks scene, mode, exact domain state, valid search stage and search generation. Suspend/resume also cannot launch the same captured request again.
3. **Stale navigation continuation:** showing a supporting page from a game first awaited Home persistence. The suspended request could overwrite a newer supporting destination. It now synchronously cancels game work and publishes the requested destination once. There is no intermediate awaited Home publication or stale continuation. This also removes a transient Home presentation.

All three failed against the pre-fix coordinator in [regression-before-fix.log](regression-before-fix.log). The final passing run is [package-final.log](package-final.log). A later audit exposed the same race when the newer destination was Home, including game→Home and direct show(Home). All three paths reproduced six state/persistence assertions against the partial fix in [navigation-home-before-fix.log](navigation-home-before-fix.log); both navigation tests pass after the single-publication fix in [navigation-home-after-fix.log](navigation-home-after-fix.log).

A fourth added regression releases a cancellation-unaware pre-interruption AI result after a replacement search starts; only the new result may select the target.

## Lifecycle boundary coverage

The 21 rows cover Home, How to Play, Settings, AI Lab; Classic playing/terminal; Local handoff/revealing/playing/terminal; AI turn start, human reveal/play, computer reveal/thinking/targeting, nonterminal placing/settling and terminal placing/settling/result. Each is encoded, restored, suspended through repeated inactive/background events, subjected to stale clock and action commands, persisted, reconstructed inactive, then activated. Each resumes the complete governed delay, without a repeated draw, commit or feedback event. Selected targets commit once; committed positions remain identical. Existing suites retain revision/token guards, invalid-snapshot rejection, backup exclusion, teardown cancellation, terminal win/draw settlement and shared fixture cases.

## Production search measurements

Five samples per measurement, Apple M3 Pro arm64 macOS 26.5.2, Xcode 26.6 (17F113), Swift 6.3.3. Empty-board held-X observation uses real production agents: exact Hard must choose center at 5/21; warm results reuse that engine's cache. Cold calls visited 109,706 nodes; warm calls visited 18 nodes. Cancellation was requested 5 ms after submitting a fresh cold Hard search, and every request threw CancellationError. This is a shared host, without thermal/scheduler isolation. The final Debug regression ran amid other phase work and its slower timings are retained rather than concealed.

| Measurement | Optimized Release range | Release median | Final Debug range |
| --- | ---: | ---: | ---: |
| Cold Hard | 84.644–153.328 ms | 95.107 ms | 3392.495–6453.770 ms |
| Warm Hard | 0.017–0.033 ms | 0.020 ms | 0.267–2.648 ms |
| MCTS, exactly 2,000 simulations | 23.494–30.970 ms | 28.283 ms | 710.635–1759.656 ms |
| Hard cancellation after request | 0.208–9.371 ms | 0.518 ms | 3.575–19.738 ms |

While Release searches ran, a 2 ms main-actor heartbeat collected 190 samples (p95 8.445 ms; maximum 19.182 ms). Final Debug p95 was 14.660 ms, maximum 70.524 ms under concurrent host load. This demonstrates main-actor progress during the real isolated searches; it is **not display frame pacing**. Simulator rendering and physical-device frame pacing require their own evidence.

[measurements.json](measurements.json) retains every raw sample, initial Debug samples, exact package source hashes, environment and fixture hashes. [performance-release.log](performance-release.log) is the optimized execution record. No production timeout or altered MCTS budget was introduced.

## Home state-independence audit

`HomeIllustration` receives only an activity Boolean and viewport geometry; it cannot access a coordinator, draw source, persistence store or AI worker. Its pure `FormMotion.homeSymbol` uses deterministic elapsed-time arithmetic. Its local visible/start state is outside `RestorationSnapshot`. The Timeline is paused when inactive/offscreen, and restart begins from X. It exposes one stable accessibility label with ignored children and has no announcement, sound or haptic calls. `HomeWordmark` separately consumes the governed one-time entrance flag; this is not persistence of the repeating illustration. Native temporal/interactive evidence and hosted tests belong to the consolidated package.

## Reproduction and boundaries

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path ios/Packages/PicPacKit
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --configuration release --package-path ios/Packages/PicPacKit --filter PerformanceAuditTests
```

Shared JSON and Android runtime sources were unchanged by this audit. Swift fixture bytes remain identical to the canonical JSON. The canonical checkout was checked clean at `dc7cb397ad335783c13c7976d4cef816d2fc0909`. No signing, secret, runtime installation, global developer-directory change, push, PR or store action occurred. A sandboxed first test attempt could not access the ordinary compiler cache; the authorized test command then ran with cache access. This tooling retry is not a product failure.
