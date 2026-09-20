# Android → native SwiftUI architecture and lifecycle map

This document describes the shipped Android implementation, then proposes an iOS architecture. **Proposals are not claims that an iOS implementation exists.** Read the [authoritative handoff](PIC_PAC_POE_IOS_HANDOFF.md), [state contract](state-machine.json), [motion contract](motion-spec.json), and [parity checklist](IOS_PARITY_CHECKLIST.md) together. All source links resolve from a clean checkout.

## 1. Dependency boundaries

```text
Android application / Compose
  ├── GameViewModel: navigation, sessions, presentation, cancellation, saved state
  ├── SettingsStore: on-device preferences
  ├── game-ai: public-observation decision algorithms
  └── game-core: immutable models, rules, session/environment randomness, AI API

game-tools (desktop JVM only)
  └── game-ai + game-core: enumeration, tournaments, offline training
```

`game-core`, `game-ai`, and `game-tools` do not depend on Compose or the Android application. UI state is not the game engine. A rules transition can have completed while the presentation intentionally still shows the computer settling its move. Neither drawing code nor an animation completion may directly mutate the board.

Recommended iOS separation:

```text
PicPacPoeApp / scene
  └── @MainActor observable AppCoordinator (single authoritative snapshot)
       ├── pure Swift GameCore values and rules
       ├── private GameSession random-draw capability
       ├── non-main-actor AI worker using Sendable public snapshots
       ├── injectable PresentationClock
       ├── local SettingsStore + versioned restoration snapshot
       └── effect consumer (sound / haptics, once per event)

SwiftUI views: observe snapshots → render → send typed intentions
Tests: scripted random source + fake clock + controllable AI worker
```

Use Swift value types for board, phase, observation, decision, and persisted snapshots. Keep mutations serialized by the main-actor coordinator; put CPU-bound search outside that actor. Select Observation or `ObservableObject` only after confirming the iOS deployment target. SwiftUI's model-data facilities support observable state; they do not replace explicit ownership of a game or an async task. [Apple model-data documentation](https://developer.apple.com/documentation/swiftui/model-data)

## 2. Exact source-to-counterpart map

Paths below are authoritative Android sources; Swift names are suggestions, not required spelling.

| Android file / types | Responsibility and portable contract | Proposed Swift / SwiftUI counterpart |
|---|---|---|
| [Model.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/Model.kt): `Symbol`, `Player`, `Cell`, `Board`, `WinningLine`, `TurnToken` | Immutable identities; cells 0–8 in row-major order; base-3 board encoding; all eight winning lines; actor is distinct from symbol | `enum Symbol`, `enum Player`, validated `Cell`, `struct Board`, `WinningLine`, `TurnToken`; `Equatable`, `Codable`, `Sendable` as appropriate |
| [State.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/State.kt): `PicPacState`, `ClassicState`, `PicPacPhase`, `GameOutcome` | State conservation, phase-associated held symbol/token, starter and revision, terminal validation, bag probabilities | Immutable Swift structs and enums with associated values; validating decoding initializer rather than unconstrained synthesized restoration |
| [State.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/State.kt): `TransitionResult`, `RejectionReason`, `GameEvent` | Accepted transition with event or explicit rejection; effects follow accepted events only | `enum TransitionResult<State>`, `GameEvent`; reject stale/occupied/wrong-phase/terminal input without mutation |
| [Rules.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/Rules.kt): `PicPacRules`, `ClassicRules` | Pure creation/draw/place functions; win before full-board draw; winning actor is placer in Pic-Pac | Pure namespace/struct functions in a local Swift package, no SwiftUI imports |
| [Session.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/Session.kt): `PicPacGameSession`, `ClassicGameSession` | Own current state; Pic-Pac owns private `EnvironmentRandomSource`; production uses `SecureRandom`; test-only scripted draws | Coordinator-owned session; production system random source, test-only injected scripted source; never expose it to an agent |
| [AiApi.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/ai/AiApi.kt): `AiObservation`, `AiAgent`, `SearchLimits`, `AiDecision`, `AiDiagnostics` | Public information only: board, held symbol, bag counts, actors; no session or future draw access; suspending decision API | `Sendable` observations/limits/results, async agent protocol and diagnostics values |
| [SearchModel.kt](../../game-core/src/main/kotlin/com/thevaguebox/picpac/core/ai/SearchModel.kt): `PublicPicPacSearchModel`, chance/decision states and transitions | Public-information search simulator distinct from live session; chance branching uses bag probabilities | Pure search-state structs/enums and simulator; never call live random draw during speculative search |
| [RandomAgent.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/RandomAgent.kt): `RandomAgent` | Uniform random legal move baseline | Seedable test agent; production randomness independent of game draw RNG |
| [HeuristicAgent.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/HeuristicAgent.kt): `HeuristicAgent` | Easy opponent: immediate wins, probabilistic threats/position evaluation | Swift heuristic agent using the same evaluator and tie behavior |
| [PositionEvaluator.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/PositionEvaluator.kt) | Shared heuristic evaluation | Pure Swift evaluator; fixture expected values before optimizing |
| [ExpectiminimaxAgent.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/ExpectiminimaxAgent.kt): `ExpectiminimaxAgent` | Medium configured depth 4; Hard full exact search; cancellation-aware search, diagnostics and cached evaluations | Non-main-actor exact/limited search implementation; parity fixtures for scores, chosen cells and tie order |
| [StochasticMctsAgent.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/StochasticMctsAgent.kt): `StochasticMctsAgent` | Experimental MCTS, production UI budget 2,000 simulations | Separate worker state per search/agent; seeded reproducibility tests; no work on rendering actor |
| [TabularPolicy.kt](../../game-ai/src/main/kotlin/com/thevaguebox/picpac/ai/TabularPolicy.kt): `TabularPolicy`, `RlPolicyAgent` | Versioned binary table reader; legal-action policy; fallback when state/artifact unavailable | Swift binary reader with fixture round trip; reproduce format/ordering, not Java stream API |
| [Main.kt](../../game-tools/src/main/kotlin/com/thevaguebox/picpac/tools/Main.kt), [Reachability.kt](../../game-tools/src/main/kotlin/com/thevaguebox/picpac/tools/Reachability.kt), [MatchRunner.kt](../../game-tools/src/main/kotlin/com/thevaguebox/picpac/tools/MatchRunner.kt), [RlTrainer.kt](../../game-tools/src/main/kotlin/com/thevaguebox/picpac/tools/RlTrainer.kt) | JVM developer CLI: state enumeration, paired tournaments, seeded offline Q-learning | Optional Swift test/CLI tooling later; not required in the iOS application, no on-device training requirement |
| [GameViewModel.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameViewModel.kt): `GameViewModel`, `GameUiState`, `TurnStage`, `AppScreen`, `GameMode`, `Difficulty`, `UiEffect` | Authoritative UI snapshot; intentions, session transitions, stage advancement, AI jobs, restoration, actor-facing labels | `@MainActor AppCoordinator` plus `GamePresentationState`; one source of truth, typed intentions, explicit job/clock handles |
| [GameScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameScreen.kt): `PresentationClock`, `presentationDelayMillis` | View-owned delay sends completion with `presentationId`; coordinator alone advances stage | Injectable UI-presentation scheduler; a cancellation-aware `.task(id:)` or owned clock task keyed by stage/ID/motion; no animation-duration inference |
| [PicPacApp.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/PicPacApp.kt): `PicPacApp`, `FeedbackEffects` | Lifecycle-aware UI collection; root navigation; preferences; saveable title-consumption flag; effect-driven audio/haptics | App/scene root owns coordinator and title-consumption state; native navigation; once-per-event effect service |
| [SettingsStore.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/settings/SettingsStore.kt): `AppSettings`, `ThemePreference`, `SettingsStore` | Preferences DataStore, four local settings; no account/network synchronization | Typed `UserDefaults`-backed settings or equivalent local store, observed on main actor; no CloudKit requirement |
| [HomeScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/HomeScreen.kt) | Product identity, Classic/Local entry, inline Vs Computer selection, supporting navigation; selected difficulty is saveable view state | `HomeView`, adaptive mode buttons/selection, no extra setup screen unless behavior remains equivalent |
| [GameScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameScreen.kt) | Narrow/wide game layout; local privacy handoff; reveal/result modal; input gating | `GameView`, `HandoffView`, `RevealPresentation`, `ResultPresentation`; modal focus isolation, explicit stage-driven visibility |
| [InfoScreens.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/InfoScreens.kt) | How to play, Settings, AI Lab; scroll/large-text layouts | Native `HowToPlayView`, `SettingsView`, `AILabView`; native switch behavior with single accessible row target |
| [PhysicalBoard.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/PhysicalBoard.kt): `PhysicalBoard`, `RecessedWell`, `FormPiece` | Vector board/pieces, fixed hit geometry, selected/placed/win indicators and semantic cell descriptions | SwiftUI `Shape`/`Canvas` for artwork plus nine real semantic `Button`s; static coordinate system, draw order preserved |
| [GamePanels.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/GamePanels.kt): `PlayerTurnHeader`, `TurnInstructions`, `ProbabilityTray` | Actor identity, stage explanation, held/placed symbol, public next-draw bag information | SwiftUI accessible view groups; computed presentation labels, not inference from last animation |
| [FormControls.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/FormControls.kt) | Material planes, tactile buttons, radio choices, back/text controls, section headings | Reusable native `ButtonStyle`, selection controls, surfaces and heading traits; preserve focus and disabled behavior |
| [FormHomeScene.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/FormHomeScene.kt) | Code-drawn, decorative tabletop pieces | Native vector composition; accessibility hidden; not a screenshot texture |
| [HomeWordmark.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/HomeWordmark.kt) | Five text parts / three motion groups, measured width fit, centered identity, once-only finite entrance | Native grouped text layout with full single accessibility label; group transforms, no per-letter rainbow or looping animation |
| [FormBrandTypography.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/theme/FormBrandTypography.kt) and [PicPacTheme.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/theme/PicPacTheme.kt) | Bundled Fredoka for emotional display only; system functional type; semantic palette, shapes, spacing, motion and depth | Swift tokens and asset colors; bundled licensed TTF; system UI font for controls/body; no global display-font override |
| [MainActivity.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/MainActivity.kt) | Android activity entry and edge-to-edge hosting | SwiftUI `App`/`WindowGroup`; use native safe areas, not emulated Android status/navigation bars |

## 3. Data and event ownership

1. The view emits an intention (`start`, `ready`, `place`, `rematch`, `home`, `presentationFinished(id)`).
2. The coordinator validates screen/stage/actor before calling a session.
3. The session invokes pure rules, returning an accepted value/event or rejection.
4. The coordinator publishes one new snapshot and persists it. It assigns a fresh monotonically increasing presentation ID on stage advancement.
5. Rendering is a projection of that snapshot. Audio/haptics consume an event ID; they must never drive game progression.
6. A timed presentation sends its captured ID back. A completion for an obsolete ID or non-game screen is ignored.
7. AI starts after the computer's symbol is drawn, in parallel with reveal presentation. An early result waits for reveal acknowledgement; a late result leaves the visible stage at thinking. Targeting precedes model placement; placement and settlement precede the next turn or terminal modal.

Do not combine `PicPacPhase` and `TurnStage` into a single enum. The former is rules state; the latter is presentation state. During `AI_PLACING`/`AI_SETTLING`, a nonterminal rules state already has the next active player, but `displayedPlayer` remains Computer. During a winning AI placement the rules state is already terminal, but the result overlay must wait for settlement.

## 4. Concurrency, cancellation and stale work

Android stores `aiJob`, `pendingAi`, and `revealAcknowledged` outside `GameUiState`. `launchAi` captures an immutable `AiObservation` and `AiRequest(revision, turnToken)`, runs `chooseMove` through `withContext(Dispatchers.Default)`, and resumes on the ViewModel scope. Cancellation is rethrown, not converted into fallback. Other search errors fall back to the observation's first legal cell. The move-application gate checks revision, token, active player and emptiness.

Native Swift requirements:

- Keep the worker's observation/result `Sendable`; do not pass a live mutable session, view, RNG capability, or coordinator into search.
- Do not assume wrapping a synchronous search inside `Task` makes it leave the main actor. Use an explicitly non-main-actor search worker and verify thread/actor behavior with Instruments.
- Search loops must cooperatively check cancellation. `cancel()` alone does not guarantee a long-running search exits. [Swift task cancellation](https://developer.apple.com/documentation/swift/task/cancel%28%29) and [Swift concurrency guide](https://docs.swift.org/swift-book/LanguageGuide/Concurrency.html)
- Retain a task handle. Cancel on Home, replacement game/rematch, coordinator teardown, and whichever scene policy is implemented. Clear pending decision and reveal-acknowledged state at cancellation boundaries.
- Revalidate revision/token/current phase/current actor/legal cell at delivery and again at placement. Cancellation is a resource-management mechanism, **not** the sole correctness guard.
- Inject a controllable worker and clock in tests. Test early, late, canceled, throwing, and stale results. UI timing must not depend on how fast the simulator happens to search.
- Keep cache lifetime and random tie behavior explicit. Actor-isolation convenience must not accidentally serialize all rendering behind an exact search.

## 5. Persistence: exact Android fields

Android has two separate stores. Preferences DataStore is durable local settings; `SavedStateHandle` is Android-managed game/navigation restoration. It is **not** a promised user-visible save-file/history system and should not be described as one.

### Settings (`pic_pac_settings`)

| Key | Default | Meaning / Swift equivalent |
|---|---|---|
| `sound` | `true` | Feedback sound enabled; typed local Boolean |
| `haptics` | `true` | Haptic feedback enabled; typed local Boolean |
| `reduced_motion` | `false` | In-app reduced-motion preference |
| `theme` | `SYSTEM` | `SYSTEM`, `LIGHT`, `DARK`; unknown stored value falls back to system |

The UI initially uses `AppSettings()` while asynchronous preference reading completes, but title animation waits for `storedSettings != null`. This prevents a normal-motion title entrance before a persisted reduced-motion preference arrives. iOS should initialize that preference before consuming its decorative entrance. Recommended native effective reduced motion is the user's app preference **or** the OS accessibility preference; document this as a native accessibility adaptation, since Android currently passes the in-app setting explicitly rather than reading an OS flag in `SettingsStore`.

### Game/navigation (`SavedStateHandle`)

| Stored keys | Content |
|---|---|
| `screen`, `mode`, `difficulty`, `stage` | Enum names; route, gameplay kind, chosen opponent, presentation stage |
| `presentation_id` | Current stage ID; restored counter prevents old completions from applying |
| `ai_target`, `ai_symbol` | Selected cell and committed/selected symbol, retained through targeting/placing/settling |
| `revision` | New-game revision, incremented at each `beginNewGame` |
| `next_starter` | Alternating rematch starter; survives returning Home |
| `kind`, `board`, `active`, `starter`, `token` | Classic/Pic-Pac discriminator, base-3 code, actor IDs, starting actor and applicable turn token |
| `remaining_x`, `remaining_o`, `phase`, `held` | Pic-Pac hidden bag counts, `draw`/`place`/`terminal`, held piece when awaiting placement |
| `outcome`, `winner`, `win_symbol` | Optional `draw`/`win`; winning actor and symbol. Winning lines reconstructed from board |

Not persisted: AI task, running search stack/cache, elapsed presentation time, pending decision before targeting, reveal-acknowledgement Boolean, sound/haptic effect ID/effect. On **serialized process-state reconstruction**, effects reset rather than replaying. This does not prove exactly-once feedback across every lifecycle: an Activity recreation retaining its ViewModel creates a new `FeedbackEffects` composition and can replay the retained last effect through its newly started `LaunchedEffect(effectId)`. Preserve event meanings but use a consumable ephemeral-event policy on iOS; do not copy this Android limitation intentionally. Root `titleEntranceConsumed` and Home difficulty selection use Compose saveable state separately; they are not DataStore settings.

### Restoration behavior to reproduce

| Restored point | Android action / parity obligation |
|---|---|
| Local handoff | Wait for Ready; no draw during reconstruction |
| Awaiting draw / turn start | Preserve bag/board; presentation clock eventually performs exactly one draw |
| Human or local revealing/playing | Restore the same held symbol and reduced bag counts; never redraw |
| Computer revealing | Recompute AI from the existing held piece; wait for reveal completion |
| Computer thinking | Set reveal acknowledged, restart search from the existing observation |
| Computer targeting with target | Reconstruct pending move from persisted target and current token; no search or redraw needed |
| Computer targeting without target | Incomplete/corrupt snapshot caveat: Android launches search, but does not establish the ordinary reveal-acknowledged path; recovery may stall. This is not a proven recovery contract. Native validation should reject or explicitly repair this combination without redrawing |
| Computer placing/settling | Board already contains move; preserve target/symbol; restart current presentation delay, do not place again |
| Terminal | Show same outcome and final board, no new reveal. Serialized snapshot reconstruction has no effect to replay; retained-ViewModel Activity recreation has the feedback caveat above |
| Invalid game reconstruction | Caught board/phase construction failures clear saved game and return Home. Some enum parsing occurs before that catch, so malformed snapshots are not comprehensively handled. Native decoder should validate enum/schema/domain invariants before use |

`PresentationClock` stores no remaining duration: entering/restoring a timed stage starts its whole duration. Toggling Reduced Motion changes the effect key and starts the new duration for that same stage. The title consumes at entrance start, so a recreation in flight returns to its fully settled form instead of replaying the remaining animation.

`goHome()` cancels AI, drops sessions and game keys, and publishes Home. It does not reset the private starter/revision/presentation counters. Starting another mode after a rematch uses the current `nextStarter`, not a hardcoded Player 1 every time. Fresh state defaults to Player 1. Classic player-symbol ownership remains Player 1=X and Player 2=O even when Player 2 starts.

## 6. Scene phase and background behavior: distinguish fact from recommendation

**Android fact:** `collectAsStateWithLifecycle()` governs UI collection; `PresentationClock` is composition-scoped; `viewModelScope` governs AI. There is no explicit `onPause`, `scenePhase`, background checkpoint policy, exact remaining-time snapshot, or resume-specific transition in the checked-in code. Do not claim Android fully pauses every presentation or AI search when backgrounded. A pending delay/search can complete according to the lifecycle and coroutine state, while UI collection may be stopped.

**Recommended native policy, to record and test explicitly:** use scene activity changes to checkpoint a valid snapshot and suspend/cancel presentation scheduling while inactive. On foreground, resume the saved stage with one complete readable beat, restarting a canceled AI search only when its phase needs one. Preserve held symbol, target, committed board, revision and presentation ID; never replay a random draw or placement. This provides native lifecycle safety without changing foreground turn semantics. [Apple Scene documentation](https://developer.apple.com/documentation/swiftui/scene)

Store a small versioned Codable restoration payload separately from settings. A scene-level store is sufficient; no database, cloud synchronization, account, analytics, or network dependency is warranted. Validate schema version and game invariants on decode. Do not store a closure, running task, random-generator internals, or absolute timestamps as the only source of truth. Decide and document force-quit/new-session behavior on Mac; Android saved-instance restoration is not a guarantee that explicit force-stop preserves a playable game.

## 7. Platform boundaries and deliberate compromises

| Preserve exactly | Adapt natively |
|---|---|
| Rules, bag conservation, actor/symbol distinction, AI public-information boundary, legal moves, deterministic oracle values | Swift syntax, concurrency isolation, persistence serialization and project organization |
| Foreground presentation order, IDs/tokens/revision guards, target/symbol persistence, readable reduced-motion beats | Scene lifetime, safe areas, native navigation gestures/back affordance and modal mechanics |
| Semantic token roles, brand font files/license, color grouping, physical board geometry, hierarchy | Rasterization/subpixel metrics, native system font metrics, SF Symbols for standard navigation where appropriate |
| Single accessible wordmark, correct cell coordinates, privacy handoff, disabled cells, selected controls | VoiceOver APIs/focus mechanics, Dynamic Type categories, iOS accessibility-reduced-motion integration |
| Feedback events and user opt-out | Actual haptic actuators, native sound assets/API, interruption and silent-mode behavior; verify on device |

Do not port Android `ToneGenerator`, `SavedStateHandle`, DataStore file formats, Compose test tags as user speech, or a fake Android status bar. Screenshots are references, not UI assets. Gradients in the current board/piece material are bounded lighting within physical forms, not permission for page-level AI gradients or glass cards.

### Why not KMP now?

The core is already cleanly separated, but it is JVM-oriented: session randomness uses Java security, policy I/O uses Java streams, the Gradle modules are JVM modules, and no multiplatform target or Swift interop layer is established. Sharing it would require build/interop/serialization/RNG work before the native product exists. For this small finite game, a tested native Swift engine with common golden fixtures is a proportionate first step. Keep algorithms and observations portable; reconsider KMP only if maintaining two verified engines produces meaningful ongoing cost. Do not restructure Android or introduce cross-platform UI during the initial iOS port.

### Known limitations to carry honestly

- Android automation is not a human TalkBack, acoustic, haptic, or physical-device certification.
- Screenshot fixtures can hold an otherwise transient stage; only captures explicitly marked live demonstrate integrated timing.
- Swift and Kotlin seeded RNG implementations need not generate the same sequence from the same integer seed. Golden parity should use scripted draws/tie choices or a specified common RNG; do not claim seed-number equivalence alone.
- Fredoka glyph shaping, Dynamic Type, modal widths and baseline rounding require visual comparison on real iOS sizes. Functional text must not be shrunk merely to match a screenshot.
- AI worker latency and frame pacing must be profiled on representative iPhones. Emulator/Android timing is not an iOS performance promise.
- The Android hierarchy currently hides the underlying board during reveal/result and omits it during local handoff; preserve isolation even when selecting a different native modal presentation.
- First iOS release non-goals: online multiplayer, accounts, ads, analytics, monetization, cloud saves, new difficulty modes, altered rules, broad redesign, live learning, and public release without human accessibility checks.
