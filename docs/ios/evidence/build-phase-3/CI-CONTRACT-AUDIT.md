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
| Unsigned native Release builds | Earlier `ad1a3b4` builds compiled and passed identity/resource checks, but independent inspection found LLVM coverage instrumentation. Superseded; strengthened final rebuild required |
| Unsigned native Debug device SDK build | PASS on the same source; no signing material in the app. This is compilation, not device execution |
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

The earlier unsigned builds compiled with stable source and passed their then-current identity/resource gate, but independent binary inspection found LLVM coverage sections. Their full package is preserved locally at `/tmp/picpac-phase3-unsigned-builds-ad1-before-coverage`; compact [finding, source provenance, compile flags and binary hashes](defects/release-coverage/finding.json) are retained here. The strengthened gate rejects that actual earlier binary. Final rebuilt artifacts must additionally prove no LLVM coverage/profile instrumentation from either the app or linked packages.

## Fixture governance

The contract has 36 unique fixture IDs in eight explicitly required groups. Both languages reject unsupported schema versions and unknown groups; missing ownership fails. Kotlin's core, AI and application consumers execute the shared cases. Swift executes rule/AI cases in `GoldenFixtureTests` and all seven presentation/restoration scenarios behaviorally in `PresentationCoordinatorTests`. Discrete outcomes remain exact; the full-search oracle uses the documented `1e-12` floating tolerance. Scripted bounded results test random-call order and bounds rather than comparing platform PRNG streams.

No contract value or expected outcome changed during Phase 3. A diff against the starting commit confirms Android modules and `docs/ios-handoff` remain unchanged. The new read-only `verify-ci-contract.py` gate additionally enforces exact fixture bytes, unique IDs, ordinary handoff success and the sole permitted strict-release failure. It fails on any other strict-verifier error; no broad `|| true` hides verification failures.

## Workflow definitions inspected locally

Both unsigned workflows keep `contents: read`, checkout `persist-credentials: false`, and every action pinned to a full 40-character commit SHA. Neither workflow reads repository secrets, uses a privileged pull-request event, configures signing, updates provisioning or uploads to a store. The pre-existing manually dispatched Android signing workflow is untouched and was not invoked.

The Android workflow now reports Kotlin/shared-fixture checks, Debug/test APK assembly, unsigned Release assembly/signature and an independent complete UI-regression emulator gate. Instrumentation requires a successful runner summary of at least 26 tests; `adb` process success alone cannot pass it. The emulator is explicitly selected, logs are retained and the owned hosted emulator is stopped afterward.

The iOS workflow reports independent native-package/shared-fixture, hosted application integration, player-driven XCUITest, unsigned Debug and Release for both simulator and generic iOS device SDKs, and governed snapshot gates. `xcodebuild` runs with signing disabled. The platform-aware Release artifact gate checks bundle/version identity, the actual Mach-O platform and iOS 17 deployment target in every slice, iPhone/iPad support, compiled icon/launch resources, exact canonical fonts/OFL and policy bytes, absent signing material/test bundles and absence of development-only launch switches/type names or LLVM coverage/profile sections. Unsigned build commands explicitly disable coverage with the supported ENABLE_CODE_COVERAGE, CLANG_ENABLE_CODE_COVERAGE and CLANG_COVERAGE_MAPPING build settings, backed by scheme and Release configuration exclusions. During recovery, the initial command failed before compilation because Xcode accepts -enableCodeCoverage only for testing; that failed command and its exact source are preserved in unsigned-builds, and the workflow was corrected before final rebuilding. A generic device SDK build is compile-time evidence only; it does not claim a physical-device run. The XCTest gate discovers actual direct class/extension test methods with the Swift parser, verifies every executed identifier and rejects empty, partial, duplicate, failed or unexpected-skipped suites. UI requires all 24 current methods to pass. Hosted Test requires all 12 current methods to pass with zero skips. Actual compiler flags confirm the project Test configuration defines both TESTING and DEBUG, so its two capture-fixture methods execute normally; an earlier assumption based only on the xcconfig was corrected. Neither target permits any skipped method. Snapshot comparison never updates approved baselines. Test result bundles, snapshot candidates/comparisons and failure build logs remain reviewable Actions artifacts, with explicit job summaries.

The Apple environment is asserted before work: macOS 26 arm64, Xcode 26.6 build 17F113, Swift 6.3.3 and the already installed iOS 26.5 runtime. A missing or changed environment fails visibly; no runtime download or global `xcode-select` mutation is used to suppress it. GitHub's published [runner-label inventory](https://github.com/actions/runner-images#available-images) identifies `macos-26` as arm64, and its [macOS 26 arm64 image inventory](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-arm64-Readme.md) lists Xcode 26.6/17F113. These references were inspected on 2026-09-21; hosted image inventory is mutable, so runtime assertions remain authoritative during a future job.

## Execution boundary and unavailable coverage

**Workflow definitions were inspected locally. Their build/test commands were reproduced locally where recorded above and in the consolidated report. No GitHub-hosted Actions job was executed or claimed to pass.** Push, pull request creation, workflow dispatch and merge were not performed.

Local read-only runtime discovery found only iOS 26.5; `/Applications` contains only the one Xcode installation. A read-only `xcrun devicectl list devices` completed successfully and returned an empty device inventory (`ci-logs/connected-devices.json`). The initial sandbox-limited CoreDeviceService query timed out; the authorized read-only retry succeeded. No physical app was installed, launched or signed. This session exposes no additional connected Mac or already available compatible iOS 17 simulator environment. These observations cover this local host, its current CoreDevice inventory and tools exposed to this session; they are not an exhaustive inventory of the owner’s organization or hardware. iOS 17 runtime execution therefore remains unavailable, while the deployment target and availability checking remain at 17.0. No runtime was installed and the global developer-directory selection was left unchanged; local commands use `DEVELOPER_DIR` per process.

Unsigned simulator compilation and testing do not certify physical iPhone/iPad behavior. Minimum-runtime execution, physical VoiceOver/Switch Control/keyboard usability, real haptic/audio quality, device frame pacing, and signed install/update validation remain accurately separated owner-controlled release gates. They do not excuse ordinary software defects within the unsigned product-build scope.

The exact-inventory verifier was locally checked against the canceled hosted result and the interrupted partial UI result, both correctly rejected. A parser probe excluded test-shaped strings, comments and nested local functions. Synthetic gate checks accepted a complete zero-skip inventory and rejected missing/duplicate methods, skips/failures and empty suites. The actual earlier full UI result passes 24/24 under the final no-skip policy; a focused single-method passing result correctly fails the complete-suite gate. These are verifier checks, not successful application-suite evidence; final actual result verification is recorded separately.
