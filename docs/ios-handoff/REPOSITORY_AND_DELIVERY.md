# Repository, verification, delivery, and architecture decision record

This document separates observed Android facts from decisions that belong to the future Mac/iOS architecture pass. It does not authorize a merge, push, signed build, store upload, key change, or iOS implementation.

## 1. Source identity at handoff audit

| Field | Audited fact |
| --- | --- |
| Working branch | `codex/pic-pac-poe-remaster` |
| Audit-base HEAD | `7f5dba571db203365f387eb6124713f739022acc` |
| Approved Android runtime candidate | `f936faf7d21e85ed71e859d94d65e125fe61b436` (Home X/O polish included) |
| Runtime source tree at candidate | `9e49470f81d1bf23afc083e3f26308b353d24dac` for `app/src/main` |
| Production branch | `main`; not merged by this handoff task |
| Release tag / immutable release commit | Unresolved and deliberately null in [`release-identity.json`](release-identity.json) |
| Canonical package entry | [`PIC_PAC_POE_IOS_HANDOFF.md`](PIC_PAC_POE_IOS_HANDOFF.md) |

The final documentation-only handoff commit is reported by the task that creates it and is discoverable with `git log -1 -- docs/ios-handoff`. A commit cannot contain its own hash. Do not confuse the handoff documentation commit with the Android runtime candidate or an eventual tagged release commit.

## 2. Repository/module structure

```text
app/          Android application, Compose UI, settings, presentation coordinator
game-core/    Immutable domain models/rules/session and public AI boundary
game-ai/      Heuristic, exact/depth-limited search, MCTS and tabular-policy reader
game-tools/   Desktop JVM enumeration, tournaments and offline RL training
docs/         Product/design/release evidence; canonical iOS package under ios-handoff/
.github/      Android verification and optional signed-artifact workflows
gradle/       Version catalog and pinned Gradle wrapper
```

Dependency direction is `app -> game-ai -> game-core` (with `app` also depending directly on `game-core`) and `game-tools -> game-ai + game-core`. Core/AI/tools have no Compose dependency. They are JVM modules, not Kotlin Multiplatform modules.

## 3. Build identity and tools

| Item | Current value / source |
| --- | --- |
| Application ID / namespace | `com.thevaguebox.probabilistictictactoe` |
| Version | `versionCode 5`, `versionName 2.0.0` |
| Android SDK | compile 36, target 36, minimum 24 |
| Android Gradle Plugin | 8.8.0 |
| Gradle wrapper | 8.10.2 with distribution SHA-256 pinned in `gradle-wrapper.properties` |
| Kotlin | 2.3.21 |
| Java/Kotlin target | JVM 17 |
| Compose | BOM 2025.05.01 |
| Release shrinking | Disabled for the documented AGP 8.8 / Kotlin 2.3 compatibility lane |

Exact library versions are in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml). Runtime purposes/licenses and privacy implications are summarized in [`PRIVACY_AND_STORE.md`](PRIVACY_AND_STORE.md).

## 4. Test and analysis layers

| Layer | Current coverage | Latest recorded result |
| --- | --- | --- |
| `game-core` JVM | Rules, all wins, ownership, conservation, rejections, deterministic draws, exhaustive reachability | Passed in the clean runtime-candidate verification recorded in [`RELEASE_VERIFICATION.md`](RELEASE_VERIFICATION.md) |
| `game-ai` JVM | Exact opening oracle, all-state legality for fast agents, MCTS bounded reproducibility, policy format | Passed in the same run |
| `game-tools` JVM | Enumeration, deterministic tournaments/training tools | Passed in the same run |
| `app` JVM | ViewModel stage/guard/restoration tests, token/contrast/motion tests | Passed in debug and release variants in the same run |
| Android instrumentation | Production composables and live flows across themes, sizes, motion settings and all production difficulties | `OK (26 tests)` against the runtime candidate; exact caveats/duration are in release verification |
| Android lint / manifest gate | Android lint, release coordinates, merged-manifest Internet assertion | Passed; lint reported no issues in the recorded clean run |
| Portable handoff | Links, JSON, paths, manifests, SHA-256 and contract invariants | Must be rerun after every handoff edit with `verify-handoff.py`; strict release remains expected to fail until tag/commit exist |
| Human/device | TalkBack, physical haptics/audio, release-device frame profile, store-delivered install/update | Open; automation and emulator screenshots do not close these gates |

The handoff task changes documentation/fixtures only. It does not relabel the recorded Android run as a new runtime execution. The exact command previously passed from the clean runtime candidate was:

```sh
./gradlew --no-daemon clean check :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease
```

An attempted offline dependency/manifest rerun during the handoff audit failed before compilation because the sandboxed Gradle process could not resolve uncached artifacts in offline mode. It is not counted as a product failure or a successful verification. The already-generated merged release manifest was inspected read-only and matches the documented privacy result.

## 5. Static analysis and workflows

[`ci.yml`](../../.github/workflows/ci.yml) runs clean checks, debug assembly and an unsigned release bundle for pushes and pull requests targeting `dev` or `main`. It uses pinned Actions, JDK 17 and Android SDK 36. It has no connected-device job.

[`release.yml`](../../.github/workflows/release.yml) is a manually dispatched, exact-commit signed-artifact workflow. It expects four repository Actions secrets and performs no Play upload. It is **optional future automation**, not the established release path and not a blocker for 2.0.0. Empty GitHub signing configuration is expected because prior releases were signed locally on the owner's Windows machine and uploaded manually.

No workflow result, GitHub secret, Environment, branch protection state, merge, tag or remote ref is asserted by this documentation task beyond what [`release-identity.json`](release-identity.json) explicitly records.

## 6. Canonical Android release path and current gate

The canonical 2.0.0 path is local manual signing:

1. Resolve an exact reviewed release commit/tag and require a clean checkout.
2. Wait for Google Play to activate the owner-requested **upload-key reset**.
3. Compare the Play Console **Upload key certificate** SHA-256 with the new local public certificate. This is not the Google-held app-signing certificate.
4. Supply the keystore path, alias, key password and store password interactively or through ephemeral local environment variables. Never commit them, copy the keystore into the repository, or print values.
5. Run clean verification and build `:app:bundleRelease` from that exact commit.
6. Verify package ID, version code/name, JAR signature, signer certificate fingerprint, size and SHA-256; preserve the approved artifact outside source control.
7. Ask the owner for explicit upload approval. Upload manually to the approved Play track only after approval.

The new private keystore and public reset certificate are machine-local release materials and intentionally absent from this handoff. Existing historical keystores remain untouched. Play App Signing is enabled; this reset changes only the upload key, not the Google-held app-signing key.

Confirmed version facts: Production is code 4 / version 1.2.1, and the owner verified that code 4 is the highest uploaded code across “Latest releases and bundles.” Therefore code 5 / 2.0.0 remains valid. Do not change it without new Play evidence.

Current blockers before producing a final signed AAB:

- Play must confirm activation of the new upload certificate and its displayed fingerprint must match the local public certificate.
- The owner must identify/approve the exact release commit (and any tag policy).
- A signed artifact has not been built or verified yet.
- Store upload remains an explicit owner-confirmation step.

GitHub Actions secrets, a GitHub Environment and signing-workflow execution are not blockers.

## 7. Artifact-generation map

| Artifact | Generation | Release meaning |
| --- | --- | --- |
| Debug APK | `:app:assembleDebug` | Debug-signed development/smoke artifact only |
| Unsigned diagnostic AAB | `:app:bundleRelease` with no complete `ANDROID_UPLOAD_*` environment | Verification aid only; never upload |
| Locally signed release AAB | Same task with complete private local signing configuration from exact approved commit | Candidate for signature/identity/hash verification, then owner upload approval |
| GitHub signed artifact | Optional manual `release.yml` execution after future secret setup | Noncanonical alternative; private Actions artifact, still no automatic Play upload |
| Play-delivered APKs | Produced and signed by Google Play from the accepted AAB | Signed with the Google-held app-signing key, which is deliberately not being changed |

## 8. Mac architecture decision: four valid shapes

The Windows handoff does **not** select or implement a repository-sharing strategy. The Mac architecture pass must record its decision and rationale before scaffolding production iOS code.

| Option | Strengths in this project | Current constraints / questions |
| --- | --- | --- |
| One monorepo, native Android + native iOS | Contracts, fixtures, fonts and cross-platform review evolve atomically; one issue/history surface | Requires clear Gradle/Xcode boundaries, CI ownership, platform-specific ignore rules, and agreement that Android repository governance may expand |
| Separate native repositories | Clean native toolchains/release permissions and independent platform cadence | Needs a durable way to version the Android reference, fixtures, fonts/license and parity evidence without silent copying/divergence |
| Kotlin Multiplatform shared core | Could eventually share deterministic rules/search and reduce duplicate algorithm maintenance | Current modules are JVM, not KMP; production session uses Java `SecureRandom`, policy I/O uses Java streams, tools use JVM runtime/filesystem, and presentation/restoration are Android-specific. Migration and Swift interop add release risk before the first native port |
| Native duplicated cores governed by shared JSON fixtures/specs | Small pure domain is straightforward in Swift; maximum native ergonomics; [`golden-fixtures.json`](golden-fixtures.json) plus exhaustive oracles provide independent parity | Algorithm changes must be implemented twice; fixture ownership/versioning and cross-platform CI discipline become essential |

The current engineering assessment is that KMP is not required to achieve safe parity and should not be a prerequisite without a concrete maintenance benefit. That assessment is input, not a decision. The Mac pass must consider repository ownership, intended minimum iOS version, Xcode/Swift/Kotlin toolchains, team skills, CI access, future rule churn, policy-binary reuse and distribution permissions.

Whatever option is chosen:

- keep the identified Android reference immutable/read-only while implementing parity;
- use native SwiftUI for the iOS presentation;
- preserve domain and AI invariants outside presentation code;
- execute the shared golden fixtures independently on both platforms;
- do not copy Android screenshots into the shipping UI;
- do not let repository topology delay correction of a proven product defect.

## 9. Open Mac-owner decisions

1. Repository topology and how the handoff/fixtures are versioned across it.
2. Minimum iOS version, Xcode/Swift versions and Observation/SwiftData availability.
3. Bundle identifier, Apple Developer team, signing and internal distribution path.
4. Whether the RL policy binary ships in v1 iOS or AI Lab is staged later while preserving product-scope transparency.
5. Scene-inactive presentation-clock policy and local snapshot storage/backup exclusion.
6. Native audio/haptic implementation and physical-device tuning.
7. Snapshot-test tooling, simulator matrix and cross-platform visual-difference tolerances.
8. Final public legal URLs and store declarations after both pages are live and reviewed.
