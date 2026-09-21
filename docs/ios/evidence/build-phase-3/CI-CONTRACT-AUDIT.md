# Build Phase 3 CI and shared-contract audit

This audit concerns the authorized implementation worktree on `codex/ios-native-parity`. Android source, its portable handoff and the canonical checkout were not modified. The Android regression source remains implementation-start commit `9423f47b34daee71cb5ed340cf0194c941e71b92`; its app runtime tree is the canonical `9e49470f81d1bf23afc083e3f26308b353d24dac`. Concurrent Phase 3 changes are confined to native iOS, CI and native evidence.

## Local execution

| Gate | Actual result |
| --- | --- |
| Ordinary handoff verifier | PASS: 7 JSON documents, 243 relative Markdown links, 131 hashes, zero errors |
| Strict-release verifier | Expected failure: exactly one error, caused solely by null `git.releaseCommit` and `git.releaseTag`; neither value was changed |
| Shared JSON identity | PASS: Kotlin reads the canonical fixture directly; Swift staged resource matches its exact bytes, SHA-256 `c68308f7a6b3684cc413bc37f495a7dfade8e5bd7a928f4273d79cbb101021ae` |
| Kotlin/Android regression, lint and artifacts | PASS: clean `check`, Debug APK, Android test APK and unsigned Release AAB; 151 executed Gradle tasks in 2m 3s |
| JVM tests | PASS: 86 executions, 57 unique tests; zero failures, errors or skips. App runs 29 tests in each of Debug and Release; core 17, AI 8, tools 3 |
| Android lint | PASS: `No issues found.` |
| Android Release signature | PASS: `jarsigner` reports `jar is unsigned.` The diagnostic artifact is not an upload artifact |
| Existing Android instrumentation | PASS: `OK (26 tests)`, 233.485 seconds, on the installed API 36 arm64 emulator; raw runner summary retained |
| Apple environment preflight | PASS locally: arm64, Xcode 26.6 (17F113), Swift 6.3.3, installed iOS Simulator 26.5 (23F77) |
| Workflow syntax and shell bodies | YAML files parsed locally; every embedded run block passed `bash -n`; Python gate scripts compiled. Synthetic Release-verifier checks reject development hooks and an incorrect deployment target |
| Native app/package/UI/snapshot execution | Recorded by the consolidated Phase 3 report and native result bundles; this audit does not duplicate or infer their results |

The complete Android command was:

```sh
ANDROID_HOME=/Users/bavabangaru/Library/Android/sdk \
JAVA_HOME=/Users/bavabangaru/Library/Java/JavaVirtualMachines/temurin-17.0.20/Contents/Home \
sh gradlew --no-daemon clean check :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease
```

The unchanged regression suite was run explicitly on the already installed `Pixel_8` emulator at `emulator-5554` (Android 16 / API 36, arm64-v8a). No connected physical device was targeted and no Android or Apple runtime was installed locally. The source-level canonical Android UI reference used API 35; the current local API 36 rerun is additional compatibility evidence, not a relabeling of that prior run. The new hosted Android lane specifies API 35 and will produce its own independent result when the owner authorizes Git promotion.

The local diagnostic AAB is 7,719,334 bytes with SHA-256 `755e94d976541620010f06b1b1faabcba1cd45173c72942b563eb6bcedaffdec`; `ci-logs/android-results.json` records the measured artifact metadata. It is ignored build output and deliberately absent from the reviewable evidence package.

## Fixture governance

The contract has 36 unique fixture IDs in eight explicitly required groups. Both languages reject unsupported schema versions and unknown groups; missing ownership fails. Kotlin's core, AI and application consumers execute the shared cases. Swift executes rule/AI cases in `GoldenFixtureTests` and all seven presentation/restoration scenarios behaviorally in `PresentationCoordinatorTests`. Discrete outcomes remain exact; the full-search oracle uses the documented `1e-12` floating tolerance. Scripted bounded results test random-call order and bounds rather than comparing platform PRNG streams.

No contract value or expected outcome changed during Phase 3. A diff against the starting commit confirms Android modules and `docs/ios-handoff` remain unchanged. The new read-only `verify-ci-contract.py` gate additionally enforces exact fixture bytes, unique IDs, ordinary handoff success and the sole permitted strict-release failure. It fails on any other strict-verifier error; no broad `|| true` hides verification failures.

## Workflow definitions inspected locally

Both unsigned workflows keep `contents: read`, checkout `persist-credentials: false`, and every action pinned to a full 40-character commit SHA. Neither workflow reads repository secrets, uses a privileged pull-request event, configures signing, updates provisioning or uploads to a store. The pre-existing manually dispatched Android signing workflow is untouched and was not invoked.

The Android workflow now reports Kotlin/shared-fixture checks, Debug/test APK assembly, unsigned Release assembly/signature and an independent complete UI-regression emulator gate. Instrumentation requires a successful runner summary of at least 26 tests; `adb` process success alone cannot pass it. The emulator is explicitly selected, logs are retained and the owned hosted emulator is stopped afterward.

The iOS workflow reports independent native-package/shared-fixture, hosted application integration, player-driven XCUITest, unsigned Debug, unsigned Release and governed snapshot gates. `xcodebuild` runs with signing disabled. The Release artifact gate checks bundle/version identity, the iOS 17 deployment target, iPhone/iPad support, absent signing material/test bundles and absence of development-only launch switches/type names. Snapshot comparison never updates approved baselines. Test result bundles, snapshot candidates/comparisons and failure build logs remain reviewable Actions artifacts, with explicit job summaries.

The Apple environment is asserted before work: macOS 26 arm64, Xcode 26.6 build 17F113, Swift 6.3.3 and the already installed iOS 26.5 runtime. A missing or changed environment fails visibly; no runtime download or global `xcode-select` mutation is used to suppress it. GitHub's published [runner-label inventory](https://github.com/actions/runner-images#available-images) identifies `macos-26` as arm64, and its [macOS 26 arm64 image inventory](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-arm64-Readme.md) lists Xcode 26.6/17F113. These references were inspected on 2026-09-21; hosted image inventory is mutable, so runtime assertions remain authoritative during a future job.

## Execution boundary and unavailable coverage

**Workflow definitions were inspected locally. Their build/test commands were reproduced locally where recorded above and in the consolidated report. No GitHub-hosted Actions job was executed or claimed to pass.** Push, pull request creation, workflow dispatch and merge were not performed.

Local read-only runtime discovery found only iOS 26.5; `/Applications` contains only the one Xcode installation. This session exposes no additional connected Mac or already available compatible iOS 17 simulator environment. iOS 17 runtime execution therefore remains unavailable, while the deployment target and availability checking remain at 17.0. No runtime was installed and the global developer-directory selection was left unchanged; local commands use `DEVELOPER_DIR` per process.

Unsigned simulator compilation and testing do not certify physical iPhone/iPad behavior. Minimum-runtime execution, physical VoiceOver/Switch Control/keyboard usability, real haptic/audio quality, device frame pacing, and signed install/update validation remain accurately separated owner-controlled release gates. They do not excuse ordinary software defects within the unsigned product-build scope.
