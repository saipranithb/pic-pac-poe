# iOS parity matrix and milestone acceptance gates

This is a forward implementation checklist, not a claim that iOS tests have run. Every checkbox starts unchecked. Android source tests are executable specifications; the final Android verification report records which ran on the release candidate. Both platforms should execute the shared [`golden-fixtures.json`](golden-fixtures.json) independently. See [architecture](ANDROID_TO_SWIFTUI_MAP.md), [repository decision record](REPOSITORY_AND_DELIVERY.md), [screen/accessibility inventory](SCREENS_AND_ACCESSIBILITY.md), [state machine](state-machine.json), and [main handoff](PIC_PAC_POE_IOS_HANDOFF.md).

## 1. Test harness policy

- Put pure Swift rules/search in a local testable module. Test it without booting SwiftUI or a simulator.
- Inject a scripted environment draw source, fake presentation clock, and controllable async agent into the coordinator. Do not use sleeps to prove ordering or production randomness to choose fixtures.
- Use an immutable launch fixture in UI-test builds to hold each transient presentation state. Keep fixture screenshots clearly distinct from integrated live-flow captures.
- Give native controls stable accessibility identifiers corresponding to intent (`home-wordmark`, `game-board`, `board-cell-0`…`8`, `turn-status`), but do not add those identifiers to spoken labels.
- Record test environment, source revision, build configuration, runtime OS/device, appearance, Dynamic Type and Reduce Motion setting with screenshot results.
- Geometry/contrast/behavior are strict assertions. Pixel comparisons need a documented tolerance for system text rasterization, native safe areas and differing renderers; do not hide a meaningful state mismatch inside a generous threshold.
- Search comparison uses exact rational/Double expectations where applicable, with explicit numerical tolerance. Identical integer seeds in Kotlin and Swift do not alone guarantee identical pseudorandom streams.
- Parse `golden-fixtures.json` as versioned test data. Reject unknown schema versions and name the fixture ID on failure; do not fork/edit expected values only on one platform.

## 2. Android test → XCTest/XCUITest parity matrix

### Domain and probability

Source: [RulesTest.kt](../../game-core/src/test/kotlin/com/thevaguebox/picpac/core/RulesTest.kt), [ReachabilityTest.kt](../../game-core/src/test/kotlin/com/thevaguebox/picpac/core/ReachabilityTest.kt).

| Invariant / Android test | Recommended native test | Acceptance condition |
|---|---|---|
| `all eight winning lines are recognized` | `BoardTests.testAllWinningLines` | Every row/column/diagonal recognized with exact cell order; no false positive for partial line |
| `player one can win with O` | `PicPacRulesTests.testActorOneWinsWithO` | Outcome actor is placer1, symbol O; never infer winner from symbol |
| `player two can win with X` | `PicPacRulesTests.testActorTwoWinsWithX` | Outcome actor2, symbol X; symmetric ownership independence |
| `draw removes held piece before placement and conserves bag` | `BagTests.testDrawDecrementsBeforePlacement` | From5/5, draw X→4/5 with held X, board unchanged, next odds4/9 and5/9; conservation board+held+bag=5 of each |
| `exhausted symbol cannot be drawn` | `BagTests.testExhaustion` | Zero-count symbol has probability0 and explicit draw rejection; other has probability1; no negative counts |
| `stale occupied and terminal moves are rejected` | `RulesTests.testRejectionsAreNonMutating` | Stale token, occupied target, wrong phase and terminal moves leave state identical and return expected rejection |
| `classic fixes symbols to players` | `ClassicRulesTests.testFixedOwnership` | Player1=X, Player2=O including Player2-start games |
| `scripted environment samples both sides of initial five-five split` | `SessionTests.testScriptedDrawBoundary` | Draw samples on either side of count threshold yield correct symbol with no hidden future-draw access |
| `canonical graph matches independently audited counts` | `ReachabilityTests.testCanonicalGraph` |11,065 chance +21,314 decision +6,648 terminal =39,027 states, using same deduplication keys and no symmetry reduction |
| Board encoding and rounding (cross-platform fixtures) | `BoardTests.testEncodingRoundTrip`, `ProbabilityPresentationTests` | All19,683 base-3 codes round-trip; cells row-major; public display matches nearest-integer percentages (4/9→44%,5/9→56%); no locale-induced semantic ambiguity |

Additional iOS decoder tests should reject invalid schema, out-of-range cell/board codes, conservation violations, and impossible nonterminal winners. Distinguish new robustness tests from Android tests already present.

### AI and offline tools

Source: [AgentTest.kt](../../game-ai/src/test/kotlin/com/thevaguebox/picpac/ai/AgentTest.kt), [ToolsTest.kt](../../game-tools/src/test/kotlin/com/thevaguebox/picpac/tools/ToolsTest.kt).

| Invariant / Android test | Recommended native test | Acceptance condition |
|---|---|---|
| `exact opening values match independent oracle` | `ExpectiminimaxTests.testOpeningOracle` | Both initial held symbols choose center; root value5/21; each candidate value matches Android oracle within1e-12 |
| `heuristic always takes an immediate win with either symbol` | `HeuristicTests.testImmediateWin` | Always choose forced immediate winning target for X and O |
| `random and heuristic are legal across every reachable decision state` | `AgentLegalityTests.testAllReachableStates` | Random and heuristic return empty legal cells for all21,314 decision states; preserve observation invariants |
| `agent constructors cannot receive production session capabilities` | Architecture/API compile-time review + `AgentBoundaryTests` | Agent API exposes immutable public observation only; no live session/environment RNG/draw-sequence access |
| `stochastic MCTS is seeded bounded and legal` | `MCTSTests.testBoundedReproducibility` | With specified test RNG, same seed/configuration→same legal move;400-simulation fixture reports400 simulations and >1 node; UI production budget2,000 |
| `tabular policy artifact is versioned and round trips` | `PolicyTests.testBinaryFormat` | Known one-state table round-trips, value0.75 within1e-6 and expected center decision; reject/handle incompatible version safely |
| `enumerator reproduces canonical state graph` | Shared engine enumerator test | Same39,027-state graph and21,314 decision list; separate CLI optional |
| `paired tournament is reproducible and swaps starters` | `TournamentTests.testPairedSeeds` | Five pairs→10 games, wins+losses+draws=10, zero invalids; repeated run equal except measured latency |
| `negamax learner builds a local policy` | Optional offline `TrainingTests` |2,000 episodes, >100 policy states, nonnegative mean absolute TD error; no requirement to train inside iOS app |
| Production difficulty configuration | `OpponentFactoryTests` | Easy=heuristic, Medium=depth4 expectiminimax, Hard=full exact search, MCTS=2,000 simulations, Q-learning=bundled table with safe fallback |
| Cooperative cancellation / main-thread responsiveness | Worker tests + Instruments | Canceled search stops at defined checkpoints; no stale result mutates replacement game; UI remains responsive during Hard search |

Do not label Easy as purely random: Random is an algorithm baseline/explanation, while the production Easy option instantiates `HeuristicAgent`.

### Coordinator / full presentation state machine

Source: [GameViewModelTest.kt](../../app/src/test/java/com/thevaguebox/probabilistictictactoe/ui/GameViewModelTest.kt). Port all named cases as main-actor coordinator tests with virtual time, including these exact assertions.

| Android case | XCTest equivalent and acceptance |
|---|---|
| `local turn hands off then reveals before placement` | Start→handoff; Ready draws exactly once→revealing with9 hidden pieces; callback→playing; placement→next handoff |
| `rapid duplicate placement is accepted once` | Two immediate taps commit one move only; board count stays1 |
| `rematch alternates starting player` | Fresh1→rematch2→rematch1, resetting board and preserving mode/difficulty |
| `saved held piece survives recreation` | Serialize/restore same phase, held symbol, bag, board and presentation stage; no RNG call |
| `computer turn is serialized between human reveals and gates input` | Human start/reveal/play/place→computer start/reveal/target/place/settle→human start/reveal/play; exact board mutation point and disabled input |
| `restart during computer targeting rejects stale presentation callback` | Capture ID, rematch, deliver old ID; replacement state unchanged |
| `computer targeting survives recreation without replaying the draw` | Target, symbol, bag and ID restored; completing target places exactly one move |
| `vs computer actor labels never expose domain player names` | You/Computer and Your turn/Computer's turn; no Player1/2 leaks in AI mode |
| `reduced motion keeps every presentation beat readable` | Timed stages remain positive; reveal≥500ms; no skip from human move directly to next human prompt |
| `computer cell announcements name the action symbol row and column` | Target announcement contains selected coordinate; placement contains placed symbol and coordinate; ordinary cells do not retain stale AI announcement |
| `computer winning move settles before result and never starts human draw` | Terminal domain outcome exists in placing; stage still places→settles→terminal; draw count unchanged thereafter |
| `computer draw move settles before draw result` | Full board without win follows same deferred-result ordering |
| `human win is addressed directly in vs computer mode` | Exact result label You win; rematch empty board and same mode |
| `presentation clocks retain the committed normal and reduced timing contracts` | Normal/reduced milliseconds: start300/160; reveal650/500; target280/160; place340/180; settle480/320; all other stages untimed |
| `all locked presentation stages reject human placement without any state change` | Test every cell in handoff/start/reveal/thinking/target/place/settle/terminal; whole snapshot unchanged |
| `target and symbol survive every committed computer placement beat and recreation` | Targeting/placing/settling retain ID, target, symbol and snapshot; Computer remains displayed actor; target clears only on next turn |
| `completed presentation callbacks cannot be replayed in later stages or after going home` | Old callbacks ignored in later stage, after Home and after new game; presentation counter remains monotonic |
| `cancelled late AI decision cannot mutate a replacement game` | Gate async result, replace AI game with Classic, release result; Classic snapshot identical |

Add native tests for the chosen scene inactive/active policy: interrupted reveal never redraws; interrupted target never changes target; committed placement never duplicates; event feedback does not replay. These are native lifecycle acceptance tests, not evidence that Android has an explicit scene policy.

### Visual semantics, typography and lifecycle

| Android source / protected areas | Native equivalent | Acceptance condition |
|---|---|---|
| [FormPaletteTest.kt](../../app/src/test/java/com/thevaguebox/probabilistictictactoe/ui/FormPaletteTest.kt): three tests for wordmark, text/action and symbol/actor/focus contrast | `DesignTokenTests` | Every wordmark part including hyphens≥4.5:1 on Home canvas; normal text≥4.5:1 on content surfaces; meaningful nontext roles≥3:1; actors distinct from X/O |
| [FormPresentationTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/FormPresentationTest.kt): every computer stage input/actor | State-fixture `GameAccessibilityUITests` | No enabled board target; Computer named at each nonmodal AI stage; target/placement coordinate label correct; both themes and motion settings |
| Same: nine equal square human targets | `BoardGeometryUITests` | Nine equal square frames; occupied cells disabled; empty cells enabled; tapping expected cell emits exactly its index |
| Same: private local handoff | `HandoffPrivacyUITests` | Ready/Home accessible; no board/cell/held-piece nodes behind handoff |
| Same: difficulty/theme selections and switches | `SettingsUITests` | Exactly one selected option; exactly three switch elements; each row toggles once; labels and values available |
| Same: narrow/wide large fonts and wide ordinary layout | `AdaptiveLayoutUITests` | Small width/accessibility text: Back, last cell, bag, Play, settings and result actions scroll-reachable; normal wide: board left, instructions/bag right; cells≥48 logical points target policy |
| Same: supporting screens/Classic dark/light | `NavigationUITests` | Tutorial ownership statement; MCTS/RL launch actions; Classic center cell available; no dark-only styling |
| [BrandTypographyTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandTypographyTest.kt): candidate fixture | `BrandSnapshotTests` | Final selection uses SemiBold wordmark/result, Medium emotional copy; bundled font resolves; functional text remains system family |
| Same: `wordmarkUsesCoralNeutralPistachioGroupsAndSubordinateNeutralHyphens` | `WordmarkColorTests` | Five parts are Pic / hyphen / Pac / hyphen / Poe; colors use x / textSecondary / text / textSecondary / o semantic roles in dark/light; no per-letter coloring or raw UI literals |
| Same:320dp and font scales1/1.3/2 | `WordmarkLayoutTests` plus UI snapshots | Exact one accessible heading; five real parts, one line, no dropped/ellipsized glyphs; centered union of parts; within gutters; no extra Home footprint; Play/Settings reachable |
| Same: RTL | `WordmarkLayoutTests.testRTL` | Pic-Pac-Poe stays left-to-right and untruncated; surrounding native navigation may mirror |
| Same: settings-ready gate/stagger/recomposition | Fake-clock title tests | No entrance before preferences read; three staggered groups; completes finite620ms contract; no theme/recomposition replay |
| Same: Reduced Motion initially or mid-flight | `WordmarkMotionTests` | Final state immediately; switching on stops in-flight motion; switching off does not replay consumed entrance |
| Same: emotional headline fixtures | Native modal snapshot/UI tests | Computer win/local win/Draw/computer reveal/handoff readable in both themes and large text; actions reachable; handoff privacy preserved |
| [BrandLifecycleTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandLifecycleTest.kt): navigation and recreation | `BrandSceneLifecycleUITests` | Settled title never replays on Settings→Home or scene reconstruction; recreation during entrance returns final immediately |
| [ProductFlowTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/ProductFlowTest.kt): six integrated tests for Local/tutorial/Classic and Easy/Medium/Hard AI | `ProductFlowUITests` | Ready gates Local; tutorial accurate; scripted Classic win/rematch/restoration; each production difficulty's actual coordinator turn targets before mutation and returns human control only after settlement |
| [HomeSceneMotionTest.kt](../../app/src/test/java/com/thevaguebox/probabilistictictactoe/ui/HomeSceneMotionTest.kt): production animation-spec samples | `HomeIllustrationMotionTests` | 1400ms halves / 2800ms cycle; complementary bounded alpha; 280/400ms fades; one bounded pulse per normal symbol; unit scale throughout Reduce Motion; seamless repeat boundaries |
| [HomeSceneAnimationTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/HomeSceneAnimationTest.kt): four clock-controlled UI tests | `HomeIllustrationUITests` | X→O→X, dark/light, 412/320dp, normal/reduced; scene and controls fixed; outer artwork unchanged; stable single description; readiness/lifecycle/removal/offscreen cancellation and restart; animations-off instant alternation |
| [VisualCapture.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/VisualCapture.kt) | XCUITest attachments/snapshot harness | Capture actual app render; wait for draw; record scenario metadata. Never use Android screenshot as SwiftUI background |

The current instrumentation inventory is 26 test methods: seven `BrandTypographyTest`, two `BrandLifecycleTest`, seven `FormPresentationTest`, six `ProductFlowTest`, and four `HomeSceneAnimationTest`. This is a source inventory, **not a pass claim**; consult final execution evidence. The Home illustration also has production-spec JVM coverage in `HomeSceneMotionTest`. Automated contrast/layout checks supplement—not replace—the human acceptance tasks below.

## 3. Screenshot parity matrix

Create stable iOS fixture references corresponding to the committed [Android reference](reference/) manifest. Minimum matrix:

- [ ] Home dark/light; title centered with coral Pic, warm neutral Pac, pistachio Poe, subordinate neutral hyphens; normal/smallest supported width; ordinary and largest accessibility text.
- [ ] Classic empty/midgame/win/draw/rematch; correct fixed symbols and alternating starter.
- [ ] Local handoff/reveal/placement; both starting actors and either held symbol.
- [ ] AI human reveal/placement; computer start/reveal/thinking/target/place/settle/result; early and deliberately delayed decisions.
- [ ] Targeting frame empty at target; placement frame contains same symbol at same target; actor remains Computer through settlement.
- [ ] How to play, Settings and AI Lab in dark/light; selected/pressed/focused/disabled controls and off/on settings.
- [ ] Wide layout and accessibility-text single-column fallback; result modal with long heading and scroll-reachable actions.
- [ ] Reduced Motion counterparts preserve each readable game stage with static decorative artwork; Home's central explanatory X/O retains its slow opacity-only alternation, with no scale pulse.
- [ ] Title animation sampled at start, stagger, settle and final; navigation/recreation return final still image.
- [ ] Home X/O sequence at normal and 320 dp width, dark/light and normal/reduced motion: both symbols sampled; same scene/control bounds; bag/arrows/board unchanged. System animations-off still alternates instantaneously.

Strict visual failures: clipped wordmark, actor/symbol ambiguity, colored functional text below contrast threshold, invisible selected state, moving hitboxes, a board behind the private handoff, missing bag exclusion information, disappearing/overlapping modal actions, or result displayed before AI settlement. Renderer differences in antialiasing alone are not product failures.

## 4. Ordered native implementation plan

Do not begin a later milestone to conceal an unfinished earlier behavioral gate. Each milestone ends with tests, a concise change summary and a reproducible verification instruction.

### 1. Repository/project bootstrap

- [ ] Resolve [release identity](release-identity.json), clone/fetch the exact approved Android reference, verify checksums and licenses; if tag/commit is still unresolved, stop and ask the owner rather than guessing.
- [ ] Evaluate and record one-monorepo, separate-repository, KMP-core and duplicated-native-core options using [the decision record](REPOSITORY_AND_DELIVERY.md); keep the identified Android reference read-only under any option.
- [ ] Create the native SwiftUI project in the owner-approved topology only after that decision.
- [ ] Record minimum iOS version, current Xcode/Swift toolchain, bundle ID/signing ownership and simulator targets; do not invent signing credentials.
- [ ] Acceptance: empty native app builds/tests/launches, source-control tree clean, no Android dependencies or copied screenshots in app assets.

### 2. Pure Swift game engine and tests

- [ ] Implement or expose the chosen board, actor, phase, rule and session-capability boundary to Swift without UI dependencies.
- [ ] Execute the shared golden fixtures, scripted draws, full rule/ownership/conservation and reachability tests.
- [ ] Acceptance: every fixture ID passes, canonical graph counts match, no SwiftUI import reaches the engine, and all rejection paths are nonmutating.

### 3. State/presentation coordinator

- [ ] Main-actor observable snapshot, injected clock/worker, revision/token/presentation guards and snapshot restoration.
- [ ] Implement complete stage enum including handoff and terminal; simulate early/late/canceled AI.
- [ ] Acceptance: all coordinator parity cases pass with virtual time before visual work; no live RNG draw in view rendering.

### 4. Design tokens and typography

- [ ] Import exact palette/geometry/motion values; bundle existing Fredoka fonts and OFL license via manifest.
- [ ] Map functional roles to native system typography with Dynamic Type; display roles retain approved font/weight.
- [ ] Acceptance: token contrast tests pass; font names/weights resolve; no network font loader; no global Fredoka substitution.

### 5. Physical board and pieces

- [ ] Native vector body/wells/pieces with prescribed draw order and fixed nine-cell geometry.
- [ ] Add disabled/pressed/focus/target/win states with real accessible cell controls.
- [ ] Acceptance: equal square hitboxes, coordinate descriptions and focus states pass; rendering stable with zero idle animation work.

### 6. Home and navigation

- [ ] Centered grouped wordmark, compact scene, exact modes/computer setup/help/Lab/settings hierarchy.
- [ ] Save once-per-session entrance consumption at start; wait for settings; preserve RTL identity and narrow fit.
- [ ] Implement only the approved visibility-scoped central X/O illustration:1400 ms per symbol, production piece paths/colors, fixed 44 dp allocation, one normal pulse, reduced-motion opacity-only alternation and stable grouped speech. Cancel inactive/offscreen/disposed and restart from X; never call game RNG/state or emit feedback.
- [ ] Acceptance: normal/dark/light/large-text snapshots approved; return Home/reconstruction never replays consumed entrance; subtitle/controls retain hierarchy.

### 7. Classic mode

- [ ] Human two-player legal placement, win/draw, final board, rematch, back-to-Home cancellation.
- [ ] Acceptance: complete game and rematch UI tests pass; Player2 starts with O on alternate rematch; rotation/scene restoration preserves position.

### 8. Local mode

- [ ] Full private handoff, Ready-gated reveal, held-symbol placement and changing bag display.
- [ ] Acceptance: no board in handoff accessibility tree; no draw replay on restoration; either actor can win with either symbol; duplicate taps harmless.

### 9. Vs Computer and AI

- [ ] Implement or bridge heuristic, depth4/full exact search and the public-observation boundary; preserve worker cancellation and off-main execution.
- [ ] Build computer sequence with selected target before mutation, persistent symbol/target through settlement and delayed terminal modal.
- [ ] Acceptance: oracle/legality tests and complete Easy/Medium/Hard games pass; canceled search cannot mutate replacement; Instruments shows search outside main actor.

### 10. Settings / How to Play / AI Lab

- [ ] Durable four settings, precise probability example and ownership explanation; experimental MCTS/Q-learning with policy reader.
- [ ] Acceptance: settings survive relaunch; three single switch targets; both experimental agents make legal moves; benchmark text not misrepresented as win rate.

### 11. Motion, haptics and sound

- [ ] Reproduce bounded title/press/piece/screen motion and essential presentation clock; native feedback service responds once per event.
- [ ] Acceptance: normal/reduced contract tests pass, only the approved visible Home X/O illustration repeats, no feedback replay on scene restore; physical feedback remains pending until human checked.

### 12. Accessibility

- [ ] VoiceOver grouping/announcements/modal focus, large targets, selected/disabled states, Dynamic Type and system Reduce Motion integration.
- [ ] Acceptance: automated accessibility/geometry tests and actual human VoiceOver flow succeed; defects fixed rather than waived by screenshots.

### 13. Visual parity

- [ ] Capture all state/theme/size variants against tagged Android reference; compare materials, spacing, typography and semantics.
- [ ] Acceptance: contact sheet reviewed; no unexplained design drift, no clipped functional text; justified native differences documented; screenshots remain reference evidence only.

### 14. Release readiness

- [ ] Clean build/test/archive, physical-device performance/feedback/accessibility/install/update tests, privacy declarations and store assets.
- [ ] Acceptance: source/tag/artifact identities agree; distribution signing managed securely; internal/TestFlight delivery only with authorization; no public rollout before owner approval.

## 5. Human release gates — not completed by automation

- [ ] Physical haptics: reveal/place/win/draw distinct enough, gentle, no double pulses, toggle respected.
- [ ] Sound: balanced speaker/headphone volume, no harsh/overlapping tone, toggle respected, native interruption policy verified.
- [ ] Title animation: restrained620ms entrance, centered final state, no clipping, no replay after navigation/recreation; Reduced Motion still.
- [ ] TalkBack on Android release candidate: correct order, no background board behind modals/handoff, intelligible stage announcements and single switches.
- [ ] Large text/display scale on physical phone: Home controls, actor names, reveal/result copy and actions reachable.
- [ ] One complete Classic, Local, Medium and Hard game including results/rematches; no player/symbol confusion.
- [ ] Install/update behavior: fresh install and update path, correct version, preferences retained where expected, no debug-only behavior.
- [ ] iOS equivalent sign-off later: physical VoiceOver, Dynamic Type, scene interruption, native haptics/audio, install/update and store archive.

## 6. Current Android automation boundary

[Android CI](../../.github/workflows/ci.yml) runs `clean check :app:assembleDebug :app:bundleRelease` for pushes and pull requests targeting `dev`/`main`, with JDK17 and the configured SDK. It does **not** contain a connected-emulator job. Local connected/emulator results therefore need their own evidence; do not infer them from a green CI badge. The [signed release workflow](../../.github/workflows/release.yml) is an optional, manually dispatched exact-commit artifact workflow requiring repository secrets; it performs no Play upload. Historical releases and the canonical 2.0.0 plan use local manual signing, so unconfigured GitHub signing is not a release blocker.

Keep release tests distinct from future iOS milestones. An Android release can be automated-test green while physical-device/TalkBack checks remain legitimately open. An iOS release cannot inherit Android's human accessibility sign-off.
