# Form Playground 2.0

This is the implemented Compose visual system, built forward from `4f76dcf1c16d3eb977409e7344a1482a1358b74f` on `codex/pic-pac-poe-remaster`. It is not a replacement rules engine or a concept-image overlay.

## Product and material language

Warm cocoa surfaces, coral X, pistachio O, short contact shadows and a single moulded board with nine recessed wells. Light mode uses cream resin with darker coral/pistachio pigment to retain contrast. Actor colors are separate teal and lilac roles; neither player owns a symbol in Pic-Pac.

The Home scene describes **bag → drawn piece → board**, not symbol selection. Information takes precedence over material: current actor, held piece, required action, remaining counts and next-draw probabilities are explicit text. No glow, particles, glass cards, continuous animation or bitmap screen backgrounds.

## Source map

- `PicPacTheme.kt`: semantic palettes, type, spacing, shapes, depth, decorative motion, Material fallbacks and system-bar appearance.
- `FormControls.kt`: tactile buttons, radio choices, back/text actions and bounded material surfaces.
- `PhysicalBoard.kt`: fixed board/well geometry, native path-drawn pieces, targeting/focus rims and final winning line.
- `GamePanels.kt`: actor identities, held-piece state, instructions and bag probabilities.
- `FormHomeScene.kt`: decorative vector scene sharing the actual piece renderer.
- `GameScreen.kt`: responsive composition, private handoff, reveal/result dialogs and the unchanged presentation clock.
- `HomeScreen.kt` / `InfoScreens.kt`: all destinations and settings using the same foundation.

All paths above are under `app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/`, with foundation/components in their corresponding subdirectories.

## Portable tokens

| Role | Dark | Light |
|---|---|---|
| Canvas | `#251F1C` | `#FBF1DE` |
| Surface / raised | `#47382F` / `#503E33` | `#FFF9EC` / `#FFFCF5` |
| Recess | `#342923` | `#EADDC7` |
| Text / secondary | `#FFF2DB` / `#DDC5B5` | `#34291F` / `#695543` |
| X / O | `#FFAC8F` / `#BFD680` | `#983A24` / `#4C6819` |
| Actor one / two | `#7ADBD1` / `#BEB9EF` | `#00645D` / `#5B4B97` |
| Focus | `#FFF2DB` | `#34291F` |

- System sans-serif. Display 42/46sp; headings 30/35, 24/29, 22/27; title 20/25 and 16/22; body 16/24, 14/21, 12/18. All scale with user text size.
- Spacing: 4, 8, 12, 16, 24, 32dp; screen gutter 20dp. Board frame 12dp, gaps 6dp.
- Radii: choice 10, control 14, surface/well 18, board 24dp. Small well radii are additionally capped by their actual size.
- Material surface elevation 2dp; button base 3dp; optional press travel 2dp. No hit target moves with the face.
- Decorative motion: press 90ms, screen crossfade 140ms, piece contact 180ms; cubic Bézier `(0.2, 0.8, 0.2, 1)`. Reduced motion removes face travel, piece scale/translation, and screen transitions. Dialog window animation is disabled; the stage owns reveal visibility.
- Well geometry never animates. AI focus uses a double contour; keyboard focus, selected checkmarks, explicit text and control semantics provide non-color cues.

## Behavioral contracts — do not alter in a visual change

Production `GameViewModel`, `game-core`, `game-ai`, `game-tools`, policy data and settings persistence are unchanged by this redesign.

The environment draws and removes a piece **before** placement. No tap timing affects randomness; counts exclude the held piece. The player completing a matching-symbol line wins, regardless of which symbol they drew. Agents see public observations, not future draws or environment randomness.

The ViewModel remains the only turn-state authority. Preserve `presentationId`, game revision, turn-token guards, cancellation, target/symbol persistence and restoration. Input is enabled only for an empty well during valid human `PLAYING`. Local handoff removes the private board from both drawing and accessibility semantics. A modal removes its underlying gameplay semantics to avoid duplicate announcements.

| Stage | Normal | Reduced | Presentation |
|---|---:|---:|---|
| `TURN_START` | 300ms | 160ms | Explicit next actor; no input |
| `REVEALING` | 650ms | 500ms | Actor + actual drawn piece; no input |
| `AI_THINKING` | Worker-dependent | Worker-dependent | Waiting for the existing AI; no artificial delay |
| `AI_TARGETING` | 280ms | 160ms | Fixed selected well, before board mutation |
| `AI_PLACING` | 340ms | 180ms | Committed piece at the persisted target |
| `AI_SETTLING` | 480ms | 320ms | Same computer actor and target; still locked |
| `PLAYING` | User-controlled | User-controlled | Human held piece and empty-cell input |
| `TERMINAL` | Until action | Until action | Result only after computer settlement |

Reduced-motion stage delays remain deliberately readable. Decorative durations never decide the next turn. Rematches still alternate the starting player. Existing sound/haptic settings and effects are retained.

## Accessibility and adaptation

- Primary/secondary text and action-label palette pairs have automated 4.5:1 contrast checks; meaningful symbol/actor/focus/border pairs have 3:1 checks.
- Controls have at least 48dp targets (main buttons at least 52dp). Board tests assert equal square wells, 48dp minimum at the tested narrow layout, and input gating.
- Selected choices expose native radio semantics; each settings row exposes one switch. Decorative art is excluded from reading order. Cells identify row, column, symbol and computer action; final-board content is announced as one noninteractive description.
- Screens scroll rather than clip. Large text stacks actors, difficulty choices and settings labels. Games use two columns at 760dp+ when text size permits; otherwise a bounded single column remains scrollable. System insets remain outside content.
- Automated semantics checks are not a substitute for a human TalkBack session. Physical-device haptic quality, diverse OEM font rendering and frame-time profiling remain release follow-ups.

## Verification and screenshot provenance

The baseline build, unit checks and original two emulator flows passed before implementation. The first visual pass found and corrected: duplicate modal announcements, a split actor name at 200% text, detached large-text settings switches, inconsistent expanding row ripples, and screenshot synchronization with native windows/system bars.

`FormPresentationTest` exercises real composables using deterministic state fixtures in dark/light and normal/reduced motion. It covers every computer presentation stage, private handoff, equal-square wells, selected/toggle semantics, large-text layouts, supporting destinations and Classic. Fixture callbacks deliberately do not advance the ViewModel.

`ProductFlowTest` drives the real activity through Classic win/rematch/recreation, Local handoff/reveal/placement, and a complete computer turn. It observes the existing ViewModel read-only, advances the Compose test clock, and issues all game commands through UI actions. Captured actual-flow stages are held by the test clock for inspection; they are not wall-clock duration measurements.

Optional capture invocation after installing debug and test APKs:

```text
adb shell am instrument -w -r -e captureFormScreenshots true com.thevaguebox.probabilistictictactoe.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.thevaguebox.probabilistictictactoe/files/form-verification <outside-repository-directory>
```

Pull before running the Gradle connected-test task, which can remove the app and its external files during cleanup. Names explicitly distinguish `stage-fixture`, `screen-fixture`, `layout-fixture`, and `actual-flow`. Density-overridden fixture captures do not prove native Dialog font scaling; that requires changing the actual emulator font scale.

Authoritative Gradle gate: `clean check :app:assembleDebug :app:bundleRelease`, plus `:app:connectedDebugAndroidTest`. Without upload-signing credentials the release AAB is intentionally unsigned. No publishing is part of this work.

### Verified on 2026-09-20

- Clean gate plus test-APK assembly: **passed**, 149 Gradle tasks. Final `check :app:connectedDebugAndroidTest`: **passed**, 120 tasks.
- **58 JVM test executions**, zero failures: 20 app tests in each debug/release variant, 9 core, 6 AI, 3 tools. **11/11 Android tests**, zero failures/skips. Lint: **no issues found**. Offline-manifest and release-configuration checks passed.
- Google Pixel AVD, Android 15/API 35, default 1080×2400 at 420dpi. Real UI interactions covered Classic win/result, Local handoff/reveal and Medium/Hard computer turns; the integrated test covers Easy plus rematch/recreation. Both experimental AI Lab actions retain their existing callbacks; their algorithms were not redesigned.
- Two screenshot-led iteration passes completed, followed by final capture checks. Explicitly reviewed dark/light, reduced motion, all computer stages, held-piece/bag coherence, opaque modals and private handoff. Thinking coverage is guaranteed by a fixture and delayed-agent unit test; a fast real agent can finish during reveal without displaying `AI_THINKING`.
- Native emulator checks used 840×1600 (320dp wide) with system font scale 2.0 for Home, Classic, scrollable result actions and Local reveal; 2400×1800 (about 914dp wide) for the two-column game. Display size and font scale were restored. Settings toggles were exercised and restored to sound/haptics on, normal motion, system theme.
- The initial added wide-layout fixture exposed cached synthetic viewport composition, not a production layout defect. Final fixtures derive density from actual host constraints and recreate the synthetic viewport; strict board-versus-information geometry assertions pass. Ordinary 900dp and 200%-font fallback layouts are tested separately.
- **72 final individual instrumented captures**, plus separately labelled wall-clock/manual captures, are stored outside the repository under the local run directory `form-playground-2-2026-09-20`. The main contact sheet selects 26 captures; the computer-stage sheet compares all seven computer states in both themes. Initial-pass stale/transition frames are retained only as iteration history, not final evidence.
- The two small README images are curated copies: Home is a composable fixture; the game is the real activity's next-human state after a computer move, held by the test clock.
- Release AAB signature check confirms an **unsigned** local artifact. Debug APK SHA-256: `6C1BCEE2173842EF1BB0AC4BE660B2FBAAABFA7D8A349865ABA62793A087F992`; release AAB SHA-256: `2F82893A9F8FB05F41A9AC5DC7683D7296ECE1BF0530E4DEDEAD4F6EA49E0CC5`.

Residual release checks: human TalkBack traversal, physical-device haptics/audio experience, additional OEM/font combinations and measured frame-time profiling. The checks here do not certify those unperformed activities.

## Future SwiftUI handoff

Port the semantic roles, spacing/type hierarchy, vector piece geometry, board geometry, actor/symbol distinction, exact stage table and accessibility descriptions. Use native SwiftUI controls, safe areas, Dynamic Type, Reduce Motion and haptic APIs; do not copy Android density values as fixed pixels or imitate Android navigation bars.

The reusable assets are geometry and palette definitions, not screenshots or platform-specific raster effects. The Kotlin rules/AI boundary is already isolated, but this UI work does not introduce KMP: evaluate sharing only when an iOS project, packaging plan and cross-platform test budget exist. Keep native presentation ownership on each platform and carry the same state-machine contract tests across the boundary.
