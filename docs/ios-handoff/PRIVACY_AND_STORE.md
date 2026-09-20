# Privacy, local-data, and store-declaration audit

This document records source facts for the Android candidate and the intended parity posture for a future iOS app. It is an engineering audit, not legal advice or a guarantee about declarations that an app-store owner has not yet submitted. Re-audit the final store artifact and every dependency before release.

## 1. Audited scope and conclusion

Audited sources are the app manifest, generated release manifest, extraction rules, Gradle module/catalog files, application source, settings storage, and the current release-candidate documentation. The Android application is designed and implemented as a completely local game. It has no app account, backend, synchronization, advertising, analytics, crash-reporting SDK, tracker, remote model, or intentional personal-data transmission.

The app-owned manifest declares no `uses-permission` entries. The generated release manifest contains no Internet, advertising ID, location, camera, microphone, contacts, storage, notification, or other data-access permission. Its only `uses-permission` is an AndroidX-generated, application-signature permission used to protect dynamically registered non-exported receivers. That permission does not grant access to user data or a remote service.

## 2. Evidence matrix

| Question | Current Android evidence | Conclusion / future iOS obligation |
| --- | --- | --- |
| Internet permission | [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) declares none. [`verifyNoInternetPermission`](../../app/build.gradle.kts) inspects every generated release manifest during `check`. The inspected merged manifest contains no `android.permission.INTERNET`. | No network transport is available to ordinary app code. Do not add an iOS networking entitlement, remote font loader, telemetry SDK, cloud model, or web-backed feature without a new product/privacy review. |
| Advertising ID / ads | No AD_ID or AdServices permission; no advertising dependency, ad view, mediation SDK, or ad source code. | Intended declarations: no ads, no tracking, no advertising identifier use. |
| Analytics / crash reporting / trackers | Version catalog and app runtime dependencies contain AndroidX, Compose, DataStore, Kotlin and coroutines only. No Firebase, Crashlytics, Sentry, Bugsnag, App Center, Segment, Amplitude, Mixpanel or equivalent SDK appears in build files/source. | Intended declarations: no analytics and no diagnostics transmitted to the developer. Native OS crash logs a user separately elects to share with a store/platform are a platform matter; do not add a first- or third-party collection channel silently. |
| Accounts / identity | No authentication UI, AccountManager/OAuth dependency, user ID, profile, subscription, server, login-gated route or account persistence. | No account-deletion feature is required while no account exists and the developer receives no account data. Adding accounts would invalidate this conclusion and require deletion/export/support flows. |
| Personal-data collection | No source path transmits settings, matches, AI observations, device identifiers or user content. The app does not accept free-form user content. | Intended Android Data Safety and iOS App Privacy posture: **no data collected** and **no data linked or used for tracking**, subject to final artifact and store-form review. |
| Local settings | [`SettingsStore.kt`](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/settings/SettingsStore.kt) stores sound, haptics, reduced motion and theme in Preferences DataStore. | Use local typed preferences on iOS. Do not enable iCloud/CloudKit synchronization by default. |
| Game restoration | [`GameViewModel.kt`](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameViewModel.kt) uses `SavedStateHandle` for the current route/game snapshot. It is restoration state, not a match-history database. | A versioned local Codable snapshot is acceptable. It must remain on-device unless a separately approved sync feature is introduced. |
| External storage | No storage permission, MediaStore API, document picker, exported file provider or external-storage source usage. Bundled fonts and the RL table are read-only app resources. | Keep generated/native data inside the app container. No Files/iCloud document exposure is part of parity. |
| Backup / device transfer | Manifest sets `allowBackup=false` and `fullBackupContent=false`. [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) excludes every listed root/file/database/shared-preference/external/device domain from cloud backup and device transfer. | Do not assume Android backup behavior transfers to iOS. Decide and document iOS backup exclusion for settings/restoration; cloud sync remains a non-goal. |
| Background execution | App source declares no service, worker, alarm, job, boot receiver or foreground service. AI runs only inside the active ViewModel scope and is canceled when the game is replaced/left. | No background processing mode is required. Cancel presentation/AI work when its owning scene/view is inactive according to the native lifecycle contract. |
| Manifest-added components | The inspected merged manifest adds `androidx.startup.InitializationProvider` (non-exported) for EmojiCompat, process lifecycle and profile installation, plus `ProfileInstallReceiver`, guarded by platform `android.permission.DUMP`. It adds no service. | These are Android library mechanics, not product features or evidence of data collection. Do not recreate them on iOS. |
| Third-party network-capable runtime | No HTTP client, WebView, socket, remote database, image loader, update SDK or general networking library is declared. AndroidX/Kotlin runtime libraries are not configured as remote services and the app lacks Internet permission. | Recheck the final Swift Package dependency graph. Prefer no third-party runtime dependency unless it has a concrete native need and a reviewed privacy manifest/license. |

## 3. Runtime dependency and license inventory

Authoritative versions are in [`libs.versions.toml`](../../gradle/libs.versions.toml), not duplicated version guesses here. Current runtime families are:

| Runtime family | Purpose | License / handoff implication |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle | Android integration, lifecycle, ViewModel | Apache License 2.0; Android-only implementation detail, not an iOS dependency. |
| Jetpack Compose UI, graphics and Material 3 | Android rendering and controls | Apache License 2.0; reproduce behavior with native SwiftUI, do not ship Compose on iOS. |
| AndroidX DataStore Preferences | Local settings | Apache License 2.0; map to a native local store. |
| Kotlin standard library and kotlinx.coroutines | Language runtime and structured concurrency | Apache License 2.0; map behavior to Swift and structured Swift concurrency unless a later architecture decision adopts KMP. |
| Bundled Fredoka Medium/SemiBold | Product display typography | SIL Open Font License 1.1; retain [`fredoka-OFL.txt`](../../app/src/main/assets/licenses/fredoka-OFL.txt) and the exact files/checksums listed in [`ASSET_MANIFEST.md`](ASSET_MANIFEST.md). |
| Bundled `picpac_rl_policy_v1.bin` | Offline AI Lab lookup table | Project-generated data governed by the repository distribution; it makes no network request. Its binary contract/checksum is in the asset manifest and behavior specification. |

JUnit, AndroidX Test/Espresso, Robolectric and coroutine-test libraries are test-only and do not ship in the release runtime. The repository source license is [`LICENSE`](../../LICENSE). A future iOS repository must generate its own complete acknowledgements from the dependencies it actually ships; this table is not a substitute for that process.

## 4. Store posture

Source evidence currently supports the following intended answers, provided the final signed artifact and store dependency report remain identical in data behavior:

- Android Data Safety: no data collected and no data shared.
- Ads: no ads.
- App access: unrestricted; no login or gated content.
- Account deletion: not applicable because users cannot create an account and no server-side account data exists.
- iOS App Privacy: no data collected and no tracking.
- User deletion: Android users can clear local app storage or uninstall; the future iOS app should similarly keep local data removable through normal app deletion and any native in-app reset that is deliberately added.

Do not make these selections automatically. The owner must compare them with the final Play/App Store forms and final dependency/privacy-manifest output.

## 5. Public legal URLs

The following are **planned, not verified live** from this audit environment:

- Privacy: `https://saipranith.dev/picpacpoe/privacy`
- Terms: `https://saipranith.dev/picpacpoe/terms`

The repository source policy is [`docs/privacy-policy.md`](../privacy-policy.md). Before either store submission, the owner must verify that each required public URL resolves over HTTPS without authentication, accurately reflects the shipped app, identifies an owner-approved contact route, and is stable. The absence of accounts, purchases, user-generated content and online services simplifies the policy, but does not eliminate store metadata review.

## 6. Re-audit triggers

Repeat this audit before release and whenever any of the following changes: manifest permissions/components, runtime dependencies, remote configuration, crash reporting, analytics, advertising, accounts, payments, cloud backup/sync, user-generated content, external file access, notifications, URL handling, or background execution. A visual-only or rules-only change does not automatically change privacy posture, but the final artifact still requires verification.
