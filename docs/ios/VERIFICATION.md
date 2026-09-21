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

## Build Phase 1 checks

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

| Gate | Required evidence | Build Phase 1 result |
| --- | --- | --- |
| Xcode project discovery | Shared `PicPacPoe` scheme appears in `xcodebuild -list` | Passed locally with Xcode 26.6 |
| Unsigned Debug build | Generic iOS Simulator build succeeds with signing disabled | Passed locally for iOS Simulator 26.5 |
| Xcode hosted test | Committed Test configuration builds and runs the application test target without signing | Passed on iPhone 17 Pro, iOS Simulator 26.5: 1 test, 0 failures |
| Swift package tests | Core, AI/presentation boundary and restoration test suites pass | Passed: 41 tests, 0 failures |
| Android/JVM tests | Existing modules plus Kotlin fixture consumers pass | Passed: 57 tests, 0 failures/errors/skips |
| Shared fixtures | Every recognized fixture group executes on Kotlin and Swift | Passed: 36 unique IDs across eight required groups, including two scripted multi-call random traces; all seven presentation/restoration cases execute behaviorally; unknown schema/root groups are rejected |
| Fixture identity | Swift's staged test resource exactly matches the canonical JSON | Passed: SHA-256 `c68308f7a6b3684cc413bc37f495a7dfade8e5bd7a928f4273d79cbb101021ae` |
| Canonical graph | 11,065 chance + 21,314 decision + 6,648 terminal = 39,027 states | Passed, including exact opening values within `1e-12` |
| Coordinator/restoration | Timing, worker races, lifecycle interruption and every restorable boundary pass | Passed with a virtual clock and controlled workers, including stale scene-task cancellation, teardown cancellation, exact terminal AI fields and fail-closed decoded-state validation |
| Simulator launch | Built app installs and starts without signing | Passed on iPhone 17 Pro, iOS Simulator 26.5 |
| CI definition | Read-only unsigned macOS 26 arm64/Xcode 26.6 lane has no secrets | Defined in `.github/workflows/ios-ci.yml` |

The generic Debug build and hosted Test build include the local `PicPacPresentation` package product and complete with signing disabled. The Debug product was also installed and launched on a temporary iPhone 17 Pro simulator boot; the shell rendered and the simulator was returned to its prior shutdown state. This proves launchability of the Phase 1 shell, not product-screen parity or minimum-OS coverage.

The ordinary handoff verifier passes after adding the governed fixtures. Strict release verification exits nonzero with one error solely because `releaseCommit` and `releaseTag` are intentionally null. Those values remain untouched. The local workflow definition was inspected and its commands were exercised locally; no hosted GitHub Actions run is claimed. The existing Android workflow provides JVM coverage, while `ios-ci.yml` provides the unsigned Swift package and app-build lane.

## Continuous integration contract

The Phase 1 iOS workflow:

- runs on an explicitly selected macOS 26 arm64 image;
- selects Xcode 26.6 without changing repository or account state;
- asserts macOS major version, CPU architecture, Xcode build and Swift 6 compiler before building;
- checks out with persisted credentials disabled and grants only `contents: read`;
- executes the ordinary portable handoff verifier;
- tests the local Swift package;
- builds and runs the hosted application integration test on the pinned iPhone 17 Pro/iOS 26.5 simulator; and
- builds the Debug app for the generic iOS Simulator destination with signing disabled.

It does not import certificates, provisioning profiles, Apple credentials, store API keys or repository secrets. It does not upload an app, sign an archive or contact App Store Connect.

## Full product verification matrix

Phase 1 established the core, fixture, graph, coordinator and restoration portions below. The matrix describes full product acceptance after the Build Phase 2 and 3 additions.

| Layer | Build Phase 2/3 acceptance |
| --- | --- |
| Unit | Every win line/symbol, ownership, ninth-move precedence, conservation, rejection nonmutation and all 19,683 board encodings |
| Fixture | Every shared ID on both platforms, unknown schema/group rejection, scripted random call order and exact/toleranced results |
| AI | All-state legality, deterministic choices/values, center opening for both symbols, `5/21` and `11/126` within `1e-12`, policy hash/format/fallback and cancellation |
| Coordinator | Every stage; early/late/throwing/canceled/stale workers; duplicate commands; locked cells; alternating starters; scene/restoration races; teardown cancellation; and every restoration boundary |
| Snapshot | Every screen/computer stage, both themes/symbols/motion settings, control states, narrow/regular/wide and accessibility layouts |
| Accessibility | Labels/headings/traits, row-major cells, modal isolation, private handoff, target sizes, contrast, focus/reachability, VoiceOver and keyboard/Switch Control |
| UI flow | Complete Classic, Local and Easy/Medium/Hard flows; experimental-agent smoke; terminal AI settlement; rematches; scene interruption; settings relaunch |
| Performance/device | Cold/warm Hard search, MCTS budget, frame pacing, haptics/audio and fresh-install/update behavior on approved physical devices |

Visual acceptance requires exact semantic tokens, strings, state and clock configuration; geometry within 0.5 point of specified formulas; wordmark optical centering within 1 point; and zero tolerance for clipping, actor/symbol ambiguity, privacy leaks, moving targets or premature results. Same-toolchain iOS regression captures target 99.5% pixel agreement with per-channel variance no greater than 2/255, calibrated with repeated captures and reviewed semantically. Android-to-iOS review compares normalized geometry and intent rather than blanket pixel equality.

## Deliberately unverified in Build Phase 1

- Only the Phase 1 shell received an interactive startup smoke test on iOS Simulator 26.5. Product screens, gameplay UI and end-to-end flows remain Build Phase 2/3 work.
- No iOS 17 simulator runtime is currently demonstrated on the host. Minimum-OS coverage needs a compatible runner or approved physical device.
- Snapshot, accessibility, UI-flow and performance matrices reach final acceptance in Build Phase 3 after the complete visible product exists.
- Physical VoiceOver, audio, haptics, frame pacing, install/update and scene-interruption behavior remain release gates.
- Apple team membership, bundle registration, certificates, profiles, agreements and App Store Connect roles are not verified or configured.
- Legal URL availability and final privacy/store declarations remain owner-reviewed submission work.
- Android signed release identity remains blocked by its intentionally null release commit/tag and pending release gates.
