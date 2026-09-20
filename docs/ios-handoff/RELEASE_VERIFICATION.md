# Android release-candidate verification and owner gates

Status is finalized in [release-identity.json](release-identity.json). This report distinguishes executed automated checks, emulator evidence, unavailable signing/Play gates and **unchecked human work**. It does not authorize public rollout.

## Home illustration follow-up (current pre-release polish)

The later Home-only change replaces the fixed central X with production X/O pieces alternating every 1400ms. One bounded 3.5% pulse and 280ms crossfade/scale transition are used normally; Reduced Motion retains a 400ms crossfade with unit scale. System animations-off retains instantaneous swaps. The scene waits for preferences, stops offscreen/backgrounded/removed and restarts at X. A single stable accessibility description replaces the formerly silent illustration. Bag, arrows, board, layout, game rules, navigation, AI, saved state and turn presentation are unchanged. The exact contract is in [motion-spec.json](motion-spec.json).

New evidence: six production-spec JVM tests (debug/release), four focused rendering/lifecycle tests, and [16 sequential Home screenshots plus a labelled contact sheet](reference/home-scene-contact-sheet.png) spanning dark/light, normal/reduced motion and 412dp/320dp logical widths. Screenshot provenance is separate from the previous baseline in [the manifest](reference/screenshot-manifest.json). The replacement artifact identity is finalized after the isolated polish commit; the older bundle below is superseded and must not be uploaded.

| Current polish check | Result |
| --- | --- |
| Production-spec unit tests | PASS: all six in both debug and release; timing, complementary opacity, one bounded pulse, reduced-motion scale, fade lengths and repeat boundaries |
| Build/check/lint | PASS: debug APK, test APK and unsigned release AAB; lint `No issues found.` |
| Full emulator regression suite | PASS: `OK (26 tests)`,258.258 seconds; includes all four new Home tests and all22 existing brand/layout/game-flow tests |
| Home visual matrix | PASS: both themes, normal/reduced motion, 412dp/320dp; real piece colors, equal bounds, unchanged neighboring controls and pixel-identical surroundings outside the center slot |
| Native narrow-phone smoke | PASS: real MainActivity at840x1870/420dpi (320dp), both X/O observed in sequential screenshots; original1080x2400 restored. An8-second raw emulator recording is also retained externally; cold-start/transitional frames are not curated parity references or a smoothness benchmark |
| Visibility/settings/system motion | PASS: readiness, STARTED/RESUMED lifecycle, removal, fully offscreen scrolling; zero-scale swaps and mounted0→1→0 system-scale changes |
| Portable handoff | PASS:6 JSON documents,203 relative links,131 hashes at this checkpoint; release certification intentionally remains gated |

An initial focused test run was intentionally stopped after its offscreen-scroll test stalled: Compose's animated `performScrollTo` semantics cannot finish with the test clock frozen. The harness now launches an instantaneous `ScrollState.scrollTo` asynchronously and advances the clock. No production fix was needed. The corrected scroll and mounted system-scale switching tests passed together (`OK (2 tests)`,19.298 seconds). The interrupted run is not counted as a successful suite. Physical TalkBack speech and frame pacing remain owner checks; semantic assertions/stills do not replace them.

Publishing remains explicitly on hold. All four local upload-signing variables were rechecked and are absent; Play's highest uploaded version code remains unverified. No key substitution, version bump, tag, push, CI dispatch or upload is authorized by a successful local build.

## Previous wordmark-candidate baseline (historical)

The following original results and artifact hash describe the earlier candidate, not the new Home illustration source. Preserve this history; use the current [release identity](release-identity.json) for the replacement candidate/artifact record.

### Scope and source

Only production change in this final pass: center Home's existing wordmark and map Pic/Pac/Poe to existing x/text/o semantic colors, with subordinate textSecondary hyphens. Typography, optical footprint, palette values, all gameplay/AI/state-machine code and timings remain unchanged. The focused UI commit is `feefdfd37932a061d0be5775cb294b2b38e8dcb2`. Supporting tests add exact run-color/contrast/center assertions and live Medium/Hard computer-turn coverage.

Local checks ran with Android Studio's bundled OpenJDK21.0.4, the project's Java17 target/toolchain, Gradle wrapper, installed SDK36/build-tools35 and API35 emulator. GitHub CI separately uses Temurin17. Only emulator-5554 was targeted; the connected physical phone was not used. Final merged-source identity, CI and artifact metadata are recorded after they exist, not guessed here.

## Automated result ledger

| Check | Result |
| --- | --- |
| JVM suites, `check` | PASS:60 executions /39 unique tests: app21 debug +21 release; core9; AI6; tools3; zero failures/errors |
| Android lint | PASS: `No issues found.` |
| Debug APK and Android test APK | PASS |
| Release AAB | PASS unsigned verification build; **not upload-ready** without existing upload-key configuration |
| Full22-test emulator suite | PASS: `OK (22 tests)`,366.858 seconds; zero failures |
| Native320dp / font1.3 and2.0 | PASS:3 tests at each scale (6 additional executions),34.128s and40.979s; original display/font settings restored |
| Emulator frame-time sample | WARNING:17 rendered frames,17 janky (100%); p50=250ms,p90=900ms,p95/p99=1000ms; not a physical-device benchmark |
| Handoff links/JSON/hashes, clean-checkout perspective | PASS:6 JSON documents,191 relative links,97 hash checks,zero errors in working tree and an independently extracted Git archive of3a5514e |
| Main CI | NOT RUN:remote push/merge blocked by execution approval gate; no main integration occurred |

Instrumentation suite composition:7 BrandTypography,2 BrandLifecycle,7 FormPresentation,6 ProductFlow. It includes exact grouped heading/colors/center/line fit;320dp1.0/1.3/2.0 in both themes; RTL identity; settings-gated stagger/finite completion/reduced-motion interruption; Home return/activity recreation including mid-entrance; all computer-stage locked semantics; local handoff privacy; equal stable board cells;900dp layout; support screens; Classic win/rematch/recreation; Local Ready/reveal/placement; Easy/Medium/Hard live computer turns and input only after settlement; How-to explanation.

The first full instrumentation attempt aborted with Android `INSTRUMENTATION_ABORTED: System has crashed` and a SystemUI DeadSystemException, not a test assertion failure. Host available physical memory at diagnosis was approximately622MiB. The completed Gradle daemon was stopped to free build memory, Android recovered, and the entire suite was restarted. That interrupted run is **not counted as passed**. Do not erase this distinction if the repeat succeeds.

### Frame-time sample: warning, not a pass

Android `dumpsys gfxinfo ... reset` followed by native Home→Classic navigation, five UI placements and a visible Player1-win result produced17 rendered frames, all17 classified janky;13 slow-UI-thread events and11 missed-vsync events. Histogram percentiles were250/900/1000/1000ms (50/90/95/99). This was the debug APK on the API35 emulator with Skia/OpenGL on a heavily loaded Windows host, after an Android-system crash earlier in testing. GPU histogram buckets saturated at4950ms for12 frames, further limiting usefulness as a GPU timing measure. No controlled baseline, release-build/physical-device trace or calibrated benchmark was collected.

This is evidence that the **observed emulator session is not smooth**; it neither establishes an app-only cause nor proves the production app is smooth. Do not dismiss it or market a frame-rate claim. Source inspection still confirms search is off the UI dispatcher and the title uses finite graphics transforms; that architectural evidence cannot replace a physical profile. Retest an isolated release/Internal-testing build on the owner's phone before public rollout. Screenshot review also rejected transient blank/gray window frames and replaced them with settled native/controlled captures; the final manifest identifies the actual sources.

Useful commands (select an emulator explicitly):

```sh
./gradlew --no-daemon clean check :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease
adb -s <test-emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <test-emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <test-emulator> shell am instrument -w -r \
  -e captureFormScreenshots true \
  com.thevaguebox.probabilistictictactoe.test/androidx.test.runner.AndroidJUnitRunner
```

Check the actual runner summary for `OK (22 tests)`; adb process exit status alone is insufficient. Connected Gradle tasks may otherwise target every attached compatible device. Capture fixtures use controllable clocks and are not suitable latency benchmarks. Final dark/light screenshots and their provenance are in [the manifest](reference/screenshot-manifest.json).

### Final clean build and diagnostic artifact

After the complete package commit, the exact clean candidate `3a5514eea7108d274416d1b30fc1bfe53c6a4c45` ran `--no-daemon clean check :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease`: **BUILD SUCCESSFUL in4m7s**,149 actionable tasks (147 executed,2 up-to-date). All60 JVM executions passed again and lint reported `No issues found.` The earlier22-test full emulator suite and6 native-scale reruns exercised identical production/test source; no app source changed afterward. The verification build emitted the existing nonfatal warning that two prebuilt AndroidX native libraries could not be stripped; they were packaged unchanged.

| Unsigned diagnostic AAB field | Value |
| --- | --- |
| Repository-relative output | `app/build/outputs/bundle/release/app-release.aab` (ignored generated output, not a Git asset) |
| Source | Clean candidate3a5514eea7108d274416d1b30fc1bfe53c6a4c45, before this metadata-only follow-up |
| Version / size | 2.0.0 /5 /7,716,132 bytes |
| SHA-256 | `4b054aa5605a76e4529f14e816ee635eadde802714f01e4cf0b5ea57b9f795d1` |
| Signature / certificate | `jarsigner` explicitly reports `jar is unsigned.` No signing certificate. **Do not upload this file.** |

The file is a local build diagnostic, not the signed artifact the release brief ultimately requires. A separately preserved local copy has the same hash; the handoff itself needs neither copy. A future signed bundle must be rebuilt from the authorized clean merged/tagged release source and gets its own signature, size and checksum record. The final documentation-only status update changes no Android runtime/test source.

## Git integration checkpoint

Local branch remains `codex/pic-pac-poe-remaster`. New implementation/test/package commits are `feefdfd37932a061d0be5775cb294b2b38e8dcb2`, `728142335e3fc94a3467366b3357cd07e871039b`, and `3a5514eea7108d274416d1b30fc1bfe53c6a4c45`; the final metadata-only commit is discoverable with `git log -- docs/ios-handoff/release-identity.json`. That avoids pretending a commit can embed its own SHA.

Read-only GitHub inspection found no open/historical PRs, no repository rulesets, and main metadata marked unprotected (the detailed administration-only protection endpoint was unavailable). Therefore a normal non-rewriting merge was the selected user-requested integration path, not a PR bypass. Immediately before integration, local main and remote main agreed and the candidate had no behind commits. The combined feature-push/main-merge/main-push command was rejected **before execution** by the safety approval gate. A subsequent read confirmed both remote refs unchanged:

- `main`: `63b6749fd26c5777d0cd0d2fec565deac77c246a`
- `codex/pic-pac-poe-remaster`: `4f76dcf1c16d3eb977409e7344a1482a1358b74f`

No push, merge, PR, tag, CI run or Play upload occurred in this pass. Publishing requires direct owner confirmation to push the feature branch and perform a normal merge/push to main. After confirmation, inspect state/fetch again; preserve all commits, require clean tree, integrate without force/history rewriting, then verify main's push CI. Do not repeat completed implementation or discard the package. Git release tags remain deferred until Play version history is verified. `verify-handoff.py --strict-release` intentionally fails while release SHA/tag are null; ordinary portability validation passes.

## Signing, version and Play boundary

Application ID is `com.thevaguebox.probabilistictictactoe`, candidate2.0.0/code5/target36. Repository tags were empty when inspected. Play's authenticated app list showed the existing Pic-Pac-Poe production listing; browser control repeatedly timed out before its version history/track details could be inspected. **The highest uploaded version code is not yet verified.** A release tag is therefore not assumed available/appropriate solely because no Git tag exists.

Local `ANDROID_UPLOAD_KEYSTORE_PATH`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`, `ANDROID_UPLOAD_STORE_PASSWORD` are not configured. No existing upload key was located/used/copied; no debug key was substituted; no new signing identity was created. The existing [release workflow](../../.github/workflows/release.yml) can use the GitHub `production` environment's existing secrets, but provisioning/approval has not been verified. Do not treat the unsigned bundle's successful build task as proof of signing.

When the owner makes the existing signing configuration available and confirms a valid version code:

1. Verify the exact clean merged release SHA/tag and successful CI; ensure code5 is greater than every already uploaded code, including drafts/testing tracks. If not, make a focused version/gate/docs adjustment and rerun verification before tagging/building.
2. Configure the four existing upload variables securely outside source control, or use the existing production-environment workflow. Never paste passwords into chat/logs or commit a keystore. No key reset/replacement is part of this task.
3. Build `:app:bundleRelease` from that exact clean source (or dispatch the pinned-commit release workflow). Record SHA, version, file size and SHA-256. Verify JAR signature is actually present and valid, not merely that `jarsigner` returned a zero exit code for an unsigned archive. Compare the public certificate fingerprint with the registered **upload** certificate; it may differ from Play's app-signing certificate.
4. In this app's Play Console, open **Test and release → Testing → Internal testing → Create new release**. Upload that exact verified signed AAB, wait for processing, confirm package/version/code, paste [release notes](../play/release-notes-2.0.0.txt), review legitimate errors and warnings, and publish **only to Internal testing** for the existing authorized tester group. Do not create new account access or change security settings.
5. If login/passkey/OTP/account approval or a legal agreement appears, pause for the owner. If Create release is disabled, inspect outstanding legitimate setup requirements; do not bypass them. Record resulting release ID/track/status and tester install link after success.
6. Leave Production untouched. Internal app sharing is a different mechanism and is not a substitute for the requested Internal testing track.

Current task has not uploaded an AAB or started a public rollout. Signed artifact path/hash remain unavailable until the authorized signing requirement is resolved. An unsigned diagnostic artifact is never the manual upload artifact.

Official references: [prepare/review a release](https://support.google.com/googleplay/android-developer/answer/9859348), [Internal testing](https://support.google.com/googleplay/android-developer/answer/9845334), [Play App Signing/upload key](https://support.google.com/googleplay/android-developer/answer/9842756).

## Privacy and listing review

The final wordmark does not introduce permissions, SDKs or data flows. Current app has no INTERNET permission, accounts, ads or analytics, uses bundled fonts/policy and local settings/game state, and has Android backup disabled. The existing privacy documents/checklist remain relevant. Actual Play Data safety answers, public policy URL/contact and current screenshots must be checked by the owner because the Console details were not successfully read in this pass. Do not assert the declarations are verified merely because source is offline. Old listing screenshots should be compared against the committed Form Playground references and refreshed if they show the previous UI; the exact listing set has not been inspected.

## Manual owner release checklist — all still open

- [ ] Install the Internal testing build on the intended physical phone; verify update from the current public version and fresh install behavior without losing valid settings unexpectedly.
- [ ] Judge physical haptics: piece reveal, normal placement, win/draw, intensity and repeated-event behavior; verify the off switch.
- [ ] Judge sound balance/appropriateness and mute/off behavior, including device/system conditions.
- [ ] Watch the centered colored title once on a fresh session; verify calm finite entrance, no replay after navigation/recreation and immediate stillness with Reduced Motion.
- [ ] Use TalkBack from Home through setup/game/reveal/target/result/rematch/Settings; verify reading order, actor/mark separation, modal announcements, disabled cells, selected choices, single switch targets and focus return.
- [ ] Use largest intended text/display settings in both themes; verify all controls and critical copy remain reachable and legible.
- [ ] Complete one Classic game/rematch, one Pic-Pac Local game with private handoffs, and one Vs Computer game (Medium and Hard if possible), including a terminal computer move.
- [ ] Check actual-device frame pacing, response latency, background/foreground and rotation during reveal/target/settlement.
- [ ] Verify Play signing/version/track/privacy/listing details, then explicitly approve any later public production rollout in a separate step.

Automation and screenshot review do not mark these human checks complete.
