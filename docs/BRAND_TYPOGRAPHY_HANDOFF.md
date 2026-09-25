# Pic-Pac-Poe brand typography refinement

This is a narrow refinement of Form Playground 2.0, not another visual system. The implementation introduces a locally bundled display face and one finite Home entrance. The established colours, material treatment, board, controls, functional type, and game presentation remain the baseline.

## Typeface decision

Fredoka SemiBold (600) is the Home identity and result face. Fredoka Medium (500) is reserved for reveal and local-handoff headlines. Functional UI remains the existing system sans-serif.

The emulator comparison renders the previous 42sp system ExtraBold treatment and both Fredoka weights in the same available width, in both themes. The final Fredoka size is 40sp: its taller native metrics otherwise expand the previous Home line box. In those captures, the old treatment reads like a conventional application heading. Fredoka's rounded terminals and open counters connect more naturally to the resin pieces. Medium is clear but comparatively light for the primary identity; SemiBold provides a firmer silhouette without closing the counters or becoming inflated. Medium remains useful for longer, transient emotional headlines. Fredoka was convincing in the rendered comparison, so no additional family was introduced.

The choice is typographic, not decorative: one theme text colour, no gradient, outline, extrusion, glow, added badge, or colour-substituted letters. Spelling remains exactly `Pic-Pac-Poe`.

## Roles and tokens

| Role | Family / weight | Size / line height | Tracking | Scope |
| --- | --- | --- | --- | --- |
| Home wordmark | Fredoka 600 | 40sp / 46sp nominal | -0.35sp | `HomeWordmark` only |
| Result | Fredoka 600 | 30sp / 35sp | 0sp | The existing outcome headline |
| Emotional headline | Fredoka 500 | 30sp / 35sp | 0sp | Reveal and local handoff |
| Functional UI | Existing system sans-serif | Existing tokens | Existing tokens | All other text |

The styles live in `FormBrandTypography`, separate from the global Material typography. Brand styles disable platform font padding and use centered, untrimmed line-height alignment. Android can still reserve natural font metrics larger than the nominal line height; the wordmark's 40sp size is verified against the actual measured previous title footprint, not an assumed line-box height. Reveal/result copy, live-region semantics, actor labels, instructions, player numbers, probabilities, navigation, Settings, AI Lab, and buttons retain their previous roles; only the three selected headline locations change face.

The static Medium and SemiBold font resources are bundled in `app/src/main/res/font`. They do not require a variable-font implementation or runtime font download. The complete SIL Open Font License 1.1 notice travels in `app/src/main/assets/licenses/fredoka-OFL.txt`, including the Fredoka Project Authors attribution. Retain this notice with any redistribution or portable asset package.

## Wordmark geometry and accessibility

The visible parts are `Pic`, `-`, `Pac`, `-`, `Poe`; they occupy a single line. `HomeWordmark` measures the five actual text parts and fits the font size to the available width, retaining the original size when it fits. A bounded binary search reduces size otherwise and preserves the line-height ratio. The measurement uses the active Compose density/font-scaling implementation, including nonlinear scaling. It does not rely on a character-count estimate.

Only the identity lockup is width-fitted. Body text, actions, instructions, and emotional headlines continue to use the user's font scaling normally; large layouts remain scrollable. No extra Home hero section or decorative spacer is introduced. The wordmark uses the existing high-contrast `FormTheme.colors.text` role in dark and light themes.

The wordmark exposes one heading with the exact accessible text `Pic-Pac-Poe`, not five separately spoken fragments. Its English part order is explicitly left-to-right inside the lockup; this does not force the surrounding app out of an RTL layout. The internal progress semantics key exists for test observation, not spoken state or game logic.

## Entrance specification

The title is animated as three groups, not individual letters. Each group lasts 480ms, with starts at 0ms, 70ms, and 140ms: the complete sequence ends at 620ms.

| Property | Start | Final |
| --- | --- | --- |
| Vertical translation | 8dp below resting position | 0dp |
| Scale | 0.95 | 1.0 |
| Opacity | 0 | 1 |
| Pic rotation | -0.8 degrees | 0 degrees |
| Pac rotation | +0.6 degrees | 0 degrees |
| Poe rotation | -0.6 degrees | 0 degrees |

Each hyphen follows the following word's opacity/translation/scale and has no rotation. Per-group progress reaches 1.015 at 360ms and returns to exactly 1 at 480ms, using `FastOutSlowInEasing` then `LinearOutSlowInEasing`. This is a bounded keyframe settle, not an unbounded physics spring: positional overshoot is only 0.12dp and scale overshoot is 0.00075. Opacity is clamped. The endpoint is completely still.

Animation values affect graphics layers, not the measured layout or game clock. There is no infinite transition, looping bounce, shimmer, sound, haptic, or gameplay delay attached to the title.

### Lifetime and Reduced Motion

`PicPacApp` owns a `rememberSaveable` consumed flag outside `AnimatedContent`. It is consumed when the first Home entrance starts, not when it finishes. The component latches its entry decision so that this parent update does not interrupt its own entrance. Returning from another screen does not replay it, and recreation after an interrupted entrance restores the final state instead of restarting the sequence.

This is once per restorable app-shell session, not a permanent preference. An unrestored new session can play again; Android saved-state restoration can retain the consumed marker. It is not persisted in Settings or coupled to game state.

The title waits for the persisted motion preference before starting. Existing functional settings defaults remain unchanged. Reduced Motion renders the final transform immediately; enabling it during an entrance also resolves immediately to the final state. No reduced-motion fade is added.

## Preservation boundaries

- Do not change game rules, symbol conservation, AI, turn guards, restoration, or actor/symbol separation for typography work.
- Do not alter `PresentationClock`, presentation IDs, or the existing reveal/target/place/settle durations.
- Do not apply Fredoka to probabilities, player labels, body copy, dense instructional content, Settings, AI descriptions, navigation, or buttons.
- Keep private local handoff free of underlying board pixels and semantics, and modal result/reveal semantics isolated.
- Preserve the existing palette, 48dp control contracts, fixed board geometry, reduced-motion behavior, and responsive layouts.
- Keep font loading local and avoid bitmap wordmarks or new runtime dependencies.

## Verification and evidence

Verified on 2026-09-20 against the final production sources:

- `clean check :app:assembleDebug :app:bundleRelease :app:assembleDebugAndroidTest` passed during the pass; `check` and all three build targets were repeated successfully after the final source/test refinements.
- 58 JVM test executions passed: 20 app tests in each of debug/release, 9 core, 6 AI, and 3 tools (38 distinct test cases).
- Android lint: **No issues found.** Offline-permission and release-configuration gates passed.
- Full API 35 emulator suite: **19/19 passed** (175.542 seconds). After correcting the new emotional fixture's host Surface to match the real app, its 20-state/theme/scale matrix was rerun and passed; production colours did not change.
- Real Android settings at **320dp width / 1.3x** and **320dp / 2.0x**: three actual-app lifecycle/local-flow/Classic-result tests passed at each setting, with Home, reveal, handoff and result captures. Native settings matter because a Compose-only density override does not necessarily reach a separate Dialog window.
- The final Home title was compared to the measured previous system title at the same density/scale, and did not increase its footprint. Exact line/character bounds, not Compose's paragraph-container overflow flag, verify content fit.
- Dark/light Home and every selected emotional role were visually inspected, along with sampled motion and native large-text captures. Emulator display defaults were restored to 1080x2400, density 420, font scale 1.0, and the prior dark preference.
- APK inspection confirmed both unchanged font resources and their complete license are packaged. Font payload is 92,472 uncompressed bytes; fonts plus license occupy approximately 54 KB compressed in the debug APK.

This is emulator evidence, not an on-device frame-time benchmark or a fresh human TalkBack review. No physical phone was modified. At 320dp and 1.3x, the existing functional `Medium` difficulty label wraps onto two lines; it remains operable and is unchanged by this scope. At large text, existing screen scrolling remains necessary.

The focused instrumentation coverage comprises:

- Candidate weight comparisons in dark and light themes.
- Home at 320dp and 1.0x, 1.3x, and 2.0x font scales: one accessible heading, real child `TextLayoutResult` checks for single-line, unclipped text, and reachable controls.
- The same text-fit/order assertions inside an RTL parent.
- Preference-ready gating, staggered progress, bounded completion, no recomposition restart, initial Reduced Motion, and mid-entrance Reduced Motion.
- Actual Home navigation return, activity recreation, and interruption during the first entrance.
- Computer win, Player 1 win, Draw, computer reveal, and local handoff in both themes at 1.0x and 2.0x.
- The existing game-flow, board/input, privacy, adaptive-layout, JVM, lint, and build checks remain required.

Local evidence is outside the repository:

`C:\Users\Bharani\.codex\visualizations\2026\08\26\01a03c30-1e33-7732-83d4-263439bf5b76\brand-polish-2026-09-20`

The `before` directory retains the prior Home, result, reveal, and handoff captures. `form-verification/brand-candidates-dark.png` and `brand-candidates-light.png` contain the rendered comparison. `brand-home-*`, `brand-emotion-*`, and `brand-wordmark-rtl-*` are explicit state/layout fixtures. `brand-actual-*` captures come from the real app lifecycle tests. Controlled Compose-clock `brand-motion-*` frames are motion evidence, not a claim of physical-device frame-time measurement. `native` contains real Android font-setting captures, logs, and final real-app dark/light Home screenshots. Test-host system bars are not a product-colour reference; use the native Home captures for that.

Final labelled PNGs in that directory:

- `font-comparison.png`: previous system face, Fredoka 500 and 600, both themes.
- `before-after.png`: previous Home against the final real app, both themes.
- `brand-contact-sheet.png`: 24 labelled views spanning identity, emotional roles, themes, lifecycle and native font scaling.
- `title-motion-strip.png`: start, stagger, settling and final clock-controlled frames.

Individual full-resolution screenshots are preserved. `make-evidence.cjs` composes the unretouched captures into these sheets. The accompanying emulator-only native-scale helper restores display settings in `finally`.

### Reproduction

Run from the repository root with the configured Android SDK. The following device commands explicitly target `emulator-5554`; do not use unqualified ADB commands or include an attached physical phone in this verification run.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat check :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease --console=plain

$brandAdb = 'C:\Users\Bharani\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $brandAdb -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
& $brandAdb -s emulator-5554 install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $brandAdb -s emulator-5554 shell am instrument -w -r -e captureFormScreenshots true com.thevaguebox.probabilistictictactoe.test/androidx.test.runner.AndroidJUnitRunner
```

For a focused rerun, add `-e class com.thevaguebox.probabilistictictactoe.BrandTypographyTest,com.thevaguebox.probabilistictictactoe.BrandLifecycleTest` to the instrumentation command. For only the font comparison, use `-e class com.thevaguebox.probabilistictictactoe.BrandTypographyTest#candidateWeightsAreRenderedTogetherInBothThemes`.

Capture output is under `/sdcard/Android/data/com.thevaguebox.probabilistictictactoe/files/form-verification/` on the emulator. Pull it with the same explicit serial to an external evidence directory before uninstalling the test app. The existing screenshot helper lets Android window/system-bar rendering settle; manual Compose-clock tests hold their selected motion frame while that real-time wait occurs.

Human TalkBack review and physical-device typography/motion perception remain useful release checks; these tests do not substitute for either or establish a frame-time benchmark.
