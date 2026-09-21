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
swift test --package-path ios/Packages/PicPacKit
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project ios/PicPacPoe.xcodeproj \
  -scheme PicPacPoe \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/pic-pac-poe-derived-data \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Use `-configuration Test` for the deterministic test build. Package and coordinator test reports list individual fixture IDs and exhaustive assertion counts.

| Gate | Required evidence | Scaffold result |
| --- | --- | --- |
| Xcode project discovery | Shared `PicPacPoe` scheme appears in `xcodebuild -list` | Passed locally with Xcode 26.6 |
| Unsigned Debug build | Generic iOS Simulator build succeeds with signing disabled | Passed locally for iOS Simulator 26.5 |
| Unsigned Test build | Same project builds with the committed Test configuration | Passed locally for iOS Simulator 26.5 |
| Swift package tests | Core, AI/presentation boundary and restoration test suites pass | Owned by Build Phase 1 integration |
| Shared fixtures | Every recognized fixture group executes on Kotlin and Swift | Owned by Build Phase 1 integration |
| Canonical graph | 11,065 chance + 21,314 decision + 6,648 terminal = 39,027 states | Owned by Build Phase 1 integration |
| CI definition | Read-only unsigned macOS 26 arm64/Xcode 26.6 lane has no secrets | Defined in `.github/workflows/ios-ci.yml` |

The generic builds completed without booting a simulator. CoreSimulatorService was unavailable to the sandbox and emitted diagnostic warnings, but both build commands exited zero with `BUILD SUCCEEDED`. Interactive launch remains unverified. Update the package and fixture rows with exact command results after the combined Phase 1 implementation is present. Preserve failed or unavailable evidence honestly.

## Continuous integration contract

The Phase 1 iOS workflow:

- runs on an explicitly selected macOS 26 arm64 image;
- selects Xcode 26.6 without changing repository or account state;
- asserts macOS major version, CPU architecture, Xcode build and Swift 6 compiler before building;
- checks out with persisted credentials disabled and grants only `contents: read`;
- executes the ordinary portable handoff verifier;
- tests the local Swift package; and
- builds the app for the generic iOS Simulator destination in both Debug and Test configurations with signing disabled.

It does not import certificates, provisioning profiles, Apple credentials, store API keys or repository secrets. It does not boot a simulator, upload an app, sign an archive or contact App Store Connect.

## Later verification matrix

| Layer | Build Phase 2/3 acceptance |
| --- | --- |
| Unit | Every win line/symbol, ownership, ninth-move precedence, conservation, rejection nonmutation and all 19,683 board encodings |
| Fixture | Every shared ID on both platforms, unknown schema/group rejection, scripted random call order and exact/toleranced results |
| AI | All-state legality, deterministic choices/values, center opening for both symbols, `5/21` and `11/126` within `1e-12`, policy hash/format/fallback and cancellation |
| Coordinator | Every stage; early/late/throwing/canceled/stale workers; duplicate commands; locked cells; alternating starters and every restoration boundary |
| Snapshot | Every screen/computer stage, both themes/symbols/motion settings, control states, narrow/regular/wide and accessibility layouts |
| Accessibility | Labels/headings/traits, row-major cells, modal isolation, private handoff, target sizes, contrast, focus/reachability, VoiceOver and keyboard/Switch Control |
| UI flow | Complete Classic, Local and Easy/Medium/Hard flows; experimental-agent smoke; terminal AI settlement; rematches; scene interruption; settings relaunch |
| Performance/device | Cold/warm Hard search, MCTS budget, frame pacing, haptics/audio and fresh-install/update behavior on approved physical devices |

Visual acceptance requires exact semantic tokens, strings, state and clock configuration; geometry within 0.5 point of specified formulas; wordmark optical centering within 1 point; and zero tolerance for clipping, actor/symbol ambiguity, privacy leaks, moving targets or premature results. Same-toolchain iOS regression captures target 99.5% pixel agreement with per-channel variance no greater than 2/255, calibrated with repeated captures and reviewed semantically. Android-to-iOS review compares normalized geometry and intent rather than blanket pixel equality.

## Deliberately unverified in Build Phase 1

- Simulator boot and interactive launch are deferred until the combined scaffold is ready; project buildability alone is not a launch claim.
- No iOS 17 simulator runtime is currently demonstrated on the host. Minimum-OS coverage needs a compatible runner or approved physical device.
- Physical VoiceOver, audio, haptics, frame pacing, install/update and scene-interruption behavior remain release gates.
- Apple team membership, bundle registration, certificates, profiles, agreements and App Store Connect roles are not verified or configured.
- Legal URL availability and final privacy/store declarations remain owner-reviewed submission work.
- Android signed release identity remains blocked by its intentionally null release commit/tag and pending release gates.
