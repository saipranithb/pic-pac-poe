# Native iOS verification record

This record distinguishes executed evidence from planned acceptance. Do not treat an unchecked or future row as a pass.

## Source and environment

| Item | Recorded value |
| --- | --- |
| Canonical handoff commit | `dc7cb397ad335783c13c7976d4cef816d2fc0909` |
| Implementation branch | `codex/ios-native-parity` |
| Android runtime candidate | `f936faf7d21e85ed71e859d94d65e125fe61b436` |
| macOS host | 26.5.2 (25F84), Apple M3 Pro, arm64 |
| Local Xcode | 26.6 (17F113) at `/Applications/Xcode.app` |
| Swift | 6.3.3, Swift 6 language mode |
| Deployment target | iOS/iPadOS 17.0 |
| Installed local simulator SDK | iOS Simulator 26.5 |

The default host developer directory points to Command Line Tools. Local Xcode commands use a per-command `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` override; global developer selection is not changed.

## Handoff gate

Before implementation, the ordinary handoff verifier completed successfully with seven JSON documents, 243 relative links, 131 hashes and zero errors. Strict verification failed only because `releaseCommit` and `releaseTag` remain null. That failure is the approved Android release-identity gate; native implementation must not invent or resolve either value.

The canonical checkout remains the read-only reference. Implementation occurs in its dedicated worktree and branch.

## Executed build checks

Run local checks from repository root with the installed Xcode selected per command:

```sh
python3 docs/ios-handoff/verify-handoff.py
python3 docs/ios-handoff/verify-handoff.py --strict-release
ANDROID_HOME=/path/to/android/sdk sh gradlew --no-daemon \
  :game-core:test :game-ai:test :game-tools:test :app:testDebugUnitTest
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  swift test --package-path ios/Packages/PicPacKit
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project ios/PicPacPoe.xcodeproj \
  -scheme PicPacPoe \
  -configuration Test \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' \
  -derivedDataPath /tmp/pic-pac-poe-test \
  CODE_SIGNING_ALLOWED=NO \
  test
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project ios/PicPacPoe.xcodeproj \
  -scheme PicPacPoe \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/pic-pac-poe-derived-data \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The strict verifier's nonzero result is expected only while `releaseCommit` and `releaseTag` are null. Fixture failures are labeled by fixture ID, and named tests document the exhaustive board and state-space domains.

| Gate | Required evidence | Latest executed result |
| --- | --- | --- |
| Xcode project discovery | Shared `PicPacPoe` scheme appears in `xcodebuild -list` | Passed locally with Xcode 26.6 |
| Unsigned Debug build | Generic iOS Simulator build succeeds with signing disabled | Passed locally for the complete product on iOS Simulator 26.5 |
| Unsigned Release build | Generic iOS Simulator Release build and shared archive/profile scheme configuration use the matched Release settings | Passed locally with signing disabled |
| Xcode hosted tests | The Test configuration builds and runs application integration tests without signing | Passed on iPhone 17 Pro, iOS Simulator 26.5: 9 tests, 0 failures |
| Swift package tests | Core, fixture, production AI, presentation and restoration suites pass | Passed: 57 tests, 0 failures (13 core, 3 fixture, 24 presentation, 14 production AI, 3 search/graph) |
| Android/JVM tests | Existing modules plus Kotlin fixture consumers pass | Passed: 57 tests, 0 failures/errors/skips |
| Shared fixtures | Every recognized fixture group executes on Kotlin and Swift | Passed: 36 unique IDs across eight required groups, including two scripted multi-call random traces; all seven presentation/restoration cases execute behaviorally; unknown schema/root groups are rejected |
| Fixture identity | Swift's staged test resource exactly matches the canonical JSON | Passed: SHA-256 `c68308f7a6b3684cc413bc37f495a7dfade8e5bd7a928f4273d79cbb101021ae` |
| Canonical graph | 11,065 chance + 21,314 decision + 6,648 terminal = 39,027 states | Passed, including exact opening values within `1e-12` |
| Coordinator/restoration | Timing, worker races, lifecycle interruption and every restorable boundary pass | Passed with a virtual clock and controlled workers, including stale scene-task cancellation, teardown cancellation, exact terminal AI fields and fail-closed decoded-state validation |
| Production AI | All production routes, legal moves, cancellation, limits, artifact integrity and fallbacks are governed | Passed for Easy, depth-four Medium, exact Hard, 2,000-simulation MCTS and Q-learning; policy has 20,266 nonzero rows, is 891,749 bytes, and hashes to `9b05cc725ab8b52cecb940b6c823cb66e843acf462511c87d2ab3e1c834152b1` |
| Product integration | Fonts, palettes, wordmark fit, motion, capture restoration, accessibility announcements and local feedback resources are checked in the hosted app | Passed: 9 hosted tests, including exact canonical Fredoka resources and Settings fixture states |
| Simulator launch and capture | Complete app installs and starts without signing | Passed on an ephemeral iPhone 17 Pro, iOS Simulator 26.5 |
| Visual evidence integrity | Twenty actual simulator frames, canonical provenance, no-crop contact sheet and checksums are complete | Corrected clean-source run pending; the first candidate was rejected after review found a launch-readiness race in dark Home |
| CI definition | Read-only unsigned macOS 26 arm64/Xcode 26.6 lane has no secrets | Defined in `.github/workflows/ios-ci.yml` |

The generic Debug and Release builds and the hosted Test build include the complete SwiftUI application and local package with signing disabled. The product was installed on a temporary iPhone 17 Pro simulator. Review of the first evidence candidate found that its first dark Home frame was captured before restoration completed, so that run is not accepted. The capture tool now waits for a unique Debug-only ready marker after restoration and a main-actor yield, allows appearance to settle, and rejects visually blank sampled content.

The corrected package will record the source commit, clean status, launch arguments, exact theme/settings state, canonical reference path and hash, simulator/toolchain metadata and every output hash. It covers Home, human placement, privacy handoff, reveal, computer targeting, computer settlement, result, Settings, How to Play and AI Lab in both themes. Three light Local/human states use the available dark Android image solely as a labeled geometry/state proxy; their semantic light palette is judged against the theme-matched references for the other seven states.

The ordinary handoff verifier passes after adding the governed fixtures. Strict release verification exits nonzero with one error solely because `releaseCommit` and `releaseTag` are intentionally null. Those values remain untouched. The Android regression command completed successfully with 40 tasks up to date. The local workflow definition was inspected and its commands were exercised locally; no hosted GitHub Actions run is claimed.

## Unsigned continuous integration contract

The iOS workflow:

- runs on an explicitly selected macOS 26 arm64 image;
- selects Xcode 26.6 without changing repository or account state;
- asserts macOS major version, CPU architecture, Xcode build and Swift 6 compiler before building;
- checks out with persisted credentials disabled and grants only `contents: read`;
- executes the ordinary portable handoff verifier;
- tests the local Swift package;
- builds and runs the hosted application integration test on the pinned iPhone 17 Pro/iOS 26.5 simulator; and
- builds both Debug and Release products for the generic iOS Simulator destination with signing disabled; and
- retains bounded build/test logs on failure without exposing credentials.

It does not import certificates, provisioning profiles, Apple credentials, store API keys or repository secrets. It does not upload an app, sign an archive or contact App Store Connect.

## Product verification matrix

| Layer | Build Phase 2 evidence | Build Phase 3 closure |
| --- | --- | --- |
| Unit | Passed for every win line/symbol, ownership, ninth-move precedence, conservation, rejection nonmutation and all 19,683 board encodings | Retain as regression gates while hardening |
| Fixture | Passed for every shared ID on Kotlin and Swift, unknown schema/group rejection, scripted random call order and exact/toleranced results | Add CI reporting that makes cross-platform contract failures one visible required check |
| AI | Passed for all-state legality where applicable, deterministic choices/values, opening oracle, production routing, policy hash/format/fallback, MCTS budget and cancellation | Measure cold/warm search and cancellation budgets on supported simulator/device classes |
| Coordinator | Passed for every stage, worker timing/failure/cancellation, duplicate commands, locked cells, starters, lifecycle races, teardown and restoration boundaries | Exercise the same boundaries through complete application flows |
| Visual | Complete product screens received exploratory iPhone/iPad and accessibility-size review; corrected final simulator evidence is pending | Add stable snapshot baselines across narrow/regular/wide and accessibility layouts; calibrate repeated-capture variance and resolve every visual delta |
| Accessibility | Hosted tests cover semantic contrast, modal isolation inputs, target/committed announcements, bounded Reduce Motion and responsive reading order design | Run VoiceOver, keyboard/Switch Control, focus, reachability, Dynamic Type and announcement timing through live flows |
| UI flow | Production views and every mode are implemented; deterministic fixture restoration proves each major rendered state | Add XCUITest coverage for complete Classic, Local, Easy/Medium/Hard and AI Lab flows, rematches, relaunch, scene interruption and terminal AI settlement |
| Performance/device | AI work is detached from the main actor and cancellation guarded | Measure frame pacing, cold/warm Hard search, MCTS budget, sound and haptics; physical-device release testing remains App Store Phase 1 |

Visual acceptance requires exact semantic tokens, strings, state and clock configuration; geometry within 0.5 point of specified formulas; wordmark optical centering within 1 point; and zero tolerance for clipping, actor/symbol ambiguity, privacy leaks, moving targets or premature results. Same-toolchain iOS regression captures target 99.5% pixel agreement with per-channel variance no greater than 2/255, calibrated with repeated captures and reviewed semantically. Android-to-iOS review compares normalized geometry and intent rather than blanket pixel equality.

## Remaining after Build Phase 2

- No iOS 17 simulator runtime is installed on this host. Minimum-OS launch coverage needs a compatible CI runner/runtime or approved physical device.
- The final screenshots are deterministic state fixtures. Full player-driven XCUITest flows, automated snapshot baselines and repeated-capture pixel calibration remain Build Phase 3 work.
- Human assistive-technology review, including VoiceOver traversal/announcement timing and keyboard/Switch Control, remains Build Phase 3 and device validation.
- Physical audio, haptics, frame pacing, install/update and release-device behavior remain later gates. Simulator evidence cannot close them.
- Apple team membership, bundle registration, certificates, profiles, agreements and App Store Connect roles are not verified or configured.
- Legal URL availability and final privacy/store declarations remain owner-reviewed submission work.
- Android signed release identity remains blocked by its intentionally null release commit/tag and pending release gates.
