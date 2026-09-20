# Pic-Pac-Poe 2.0.0 Play release checklist

This checklist separates facts verified in the repository from actions that require the Play Console, GitHub repository administration, or the owner's private upload key. A checked repository item is not evidence that its corresponding Play Console task is complete.

## Repository-verified release facts

- [x] Production identity is unchanged: `com.thevaguebox.probabilistictictactoe` is both the application ID and namespace.
- [x] The remaster is `versionCode 5` / `versionName 2.0.0`. The owner confirmed the current Production release is code 4 / name 1.2.1. Code 5 remains provisional until every testing track, draft release and uploaded bundle is confirmed below 5.
- [x] `compileSdk` and `targetSdk` are 36; `minSdk` remains 24.
- [x] Java and Kotlin target JVM 17. The Gradle wrapper is 8.10.2 and its binary distribution is pinned to Gradle's published SHA-256 checksum.
- [x] Dependencies use fixed versions or the fixed Compose BOM. There are no dynamic versions or snapshots.
- [x] The app-owned manifest requests no permissions and contains only the exported launcher activity. It declares no app-owned services, providers, receivers, deep links, cleartext configuration, or package visibility queries.
- [x] The merged manifest adds only AndroidX Startup/ProfileInstaller components: a non-exported startup provider, a profile receiver guarded by the platform signature-level `android.permission.DUMP`, and AndroidX's generated signature-level non-exported-receiver permission.
- [x] The release runtime has no Firebase, analytics, advertising, networking, remote model, or cloud AI dependency.
- [x] Android backup is disabled. API 31+ cloud-backup and device-transfer rules explicitly exclude all supported app-data domains; the pre-31 manifest path is disabled with `fullBackupContent="false"`.
- [x] Settings and transient game restoration state stay on the device. The privacy policy describes storage, backup behavior, retention, and user deletion accurately.
- [x] The merged release manifest is checked for `android.permission.INTERNET` during every `check` run.
- [x] The release variant is non-debuggable, not `testOnly`, and never falls back to debug signing. It is unsigned unless the complete upload-key environment is present.
- [x] The launcher has no orientation lock, uses edge-to-edge content, enables predictive-back handling, and uses adaptive Compose layouts for phone, tablet, and resizable windows.
- [x] Classic local, Pic-Pac local, Easy/Medium/Hard computer modes, AI Lab, saved-state restoration, illegal-input gating, and the approved You/Computer presentation sequence are covered by automated tests.
- [x] Local release builds are unsigned when signing environment variables are absent. The repository contains no keystore or credentials.
- [x] CI and release action dependencies are pinned to immutable commits and use read-only repository permissions.

## Release-shrinking decision

R8/resource shrinking remains disabled for 2.0.0. The supported Android Studio lane caps this project at AGP 8.8, while Kotlin 2.3 metadata requires R8 8.13.19 (bundled by AGP 8.13.2) for shrinking. The stable release choice is therefore an unshrunk AAB rather than an IDE-incompatible AGP upgrade or an ad-hoc R8 override.

Revisit shrinking only as a separate toolchain change with a compatible Android Studio/AGP/R8 combination and a complete release regression run. References: [Kotlin compatibility](https://developer.android.com/build/kotlin-support) and [AGP 8.8 compatibility](https://developer.android.com/build/releases/agp-8-8-0-release-notes).

## GitHub owner setup

- [ ] Protect `main` and `dev`; require the Android CI job before merge.
- [x] Confirm current environment topology: this repository has no GitHub Environments. The manually dispatched workflow therefore reads repository-level Actions secrets and still requires an exact 40-character commit SHA; it is not environment-approval-gated.
- [ ] After the original upload key is positively identified, add or verify these repository-level Actions secrets, never repository files:
  - `ANDROID_UPLOAD_KEYSTORE_BASE64`: base64 encoding of the existing Play upload-keystore file.
  - `ANDROID_UPLOAD_KEY_ALIAS`: upload-key alias.
  - `ANDROID_UPLOAD_KEY_PASSWORD`: upload-key password.
  - `ANDROID_UPLOAD_STORE_PASSWORD`: keystore password.
- [ ] Confirm Actions retention and access settings are appropriate for a signed release artifact.
- [ ] Run **Build signed Play release** with the full 40-character SHA of the reviewed release commit.
- [ ] Review the workflow's clean test/build result, signature verification, and SHA-256 output. Download the manually dispatched `pic-pac-poe-2.0.0-signed-<sha>` artifact.

The release workflow decodes the keystore into the runner's temporary directory, passes only the temporary path and secret values to Gradle, verifies the resulting AAB with `jarsigner`, and uploads it as a private Actions artifact. It deliberately does not publish to Play. Do not dispatch it until the upload-key fingerprint and version code are confirmed, and do not upload the local unsigned `app-release.aab`.

If GitHub Actions is unavailable, the owner may use Android Studio's **Build > Generate Signed Bundle / APK > Android App Bundle** flow with the existing Play upload keystore and the `release` variant. Do not save passwords in project files, do not use the debug key, and do not generate a replacement upload key. Run the repository's clean verification command first, then verify the generated AAB signature and package/version before upload.

## Play Console owner tasks

- [ ] Confirm the highest version code already uploaded to every Play track is lower than 5. If it is 5 or higher, increment the repository version code before building.
- [ ] Confirm this is the existing app with package name `com.thevaguebox.probabilistictictactoe`; never create a replacement listing for the remaster.
- [x] Confirm Play App Signing enrollment and Play Console access. The existing upload key is still being identified; do not request a reset or create replacement credentials yet.
- [ ] Host `docs/privacy-policy.md` at a stable, public, non-editing URL and enter that URL in Play Console. Confirm the public page identifies the app and provides an owner-approved contact route.
- [ ] Complete Data safety from the shipped app, not from assumptions: no data collected, no data shared, no security practices involving transmitted data, and no account deletion mechanism because the app has no accounts.
- [ ] If Play asks about AI-generated content, classify the shipped heuristic, Expectiminimax, MCTS, and tabular Q-learning systems as game-action decision algorithms, not generative AI. They do not generate user-prompted text, images, audio, video, or conversation.
- [ ] Declare that the app contains no ads. Complete target-audience, content-rating, app-content, government-app, financial-features, and any other declarations shown for the owner account.
- [ ] Mark app access unrestricted; the app has no login or gated content.
- [ ] Review store title, short/full descriptions, category, contact details, icon, feature graphic, phone/tablet screenshots, and localized assets. Repository screenshots are reference evidence, not automatically uploaded assets.
- [ ] Paste the reviewed 2.0.0 notes from `docs/play/release-notes-2.0.0.txt`.
- [ ] Upload the signed workflow artifact to an internal test track first. Confirm Play accepts package identity, version code, target API, signing certificate, and manifest declarations.
- [ ] Review the App Bundle Explorer, automated pre-launch report, device catalog, policy status, and Android vitals signals available from the test release.
- [ ] Smoke-test install/update behavior from the Play-delivered internal build on at least one supported phone and one larger or resizable device. Cover Classic, local reveal/handoff, all three computer difficulties, rotation/recreation, rematch, settings, sound/haptics, reduced motion, and offline launch.
- [ ] Confirm countries/regions, pricing/free status, release availability, and managed-publishing choice.
- [ ] Promote with the owner's chosen staged rollout only after all blocking warnings and test feedback are resolved.

## Artifact map

- Local debug APK: `app/build/outputs/apk/debug/app-debug.apk` (debug signed; install/smoke testing only).
- Local release bundle: `app/build/outputs/bundle/release/app-release.aab` (unsigned unless all `ANDROID_UPLOAD_*` path/password variables are deliberately supplied).
- Production candidate: signed AAB downloaded from the manually dispatched release workflow for the exact reviewed commit, after signing/version gates are resolved.

## Go/no-go rule

Repository readiness requires a clean `clean check :app:assembleDebug :app:bundleRelease` run and a reviewed clean Git state. Play readiness additionally requires every unchecked owner item relevant to the release to be completed. Missing Play Console state, upload-key access, public policy hosting, production secrets, or store declarations blocks upload/promotion even when the repository is ready.
