# Android release-candidate verification and owner gates

Status is finalized in [release-identity.json](release-identity.json). This report distinguishes executed automated checks, emulator evidence, unavailable signing/Play gates and **unchecked human work**. It does not authorize public rollout.

## Scope and source

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
| Handoff links/JSON/hashes, clean-checkout perspective | Working-tree validator PASS; clean Git archive validation follows final package commit |
| Main CI | Pending safe integration and push |

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
