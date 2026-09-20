# Screen, interaction and accessibility specification

Read with [architecture/lifecycle](ANDROID_TO_SWIFTUI_MAP.md), [tokens](design-tokens.json), [motion](motion-spec.json), and [parity tests](IOS_PARITY_CHECKLIST.md). The [reference directory](reference/) contains the curated Android evidence. Reference names below identify states; the screenshot manifest is authoritative about the actual file, captured revision, theme, dimensions, font scale and **live flow versus held fixture** provenance. A fixture demonstrates rendering, not successful AI computation or real-time progression.

## 1. Common hierarchy and layout

The product is a single game application, not a dashboard. Home has one wordmark, explanatory subtitle, decorative tabletop scene, two direct local-mode entries, inline computer setup, and three supporting actions. Gameplay has one board, explicit actors, one clear stage instruction, and (Pic-Pac only) bag information. Reveal, handoff and result are meaningful interruptions, not decorative cards competing with the board.

| Region | Exact Android behavior | Native parity acceptance |
|---|---|---|
| Safe areas | Root uses status/navigation-bar padding and a solid semantic canvas | Respect iOS safe areas; never render a screenshot's Android system bars |
| Home | 20dp horizontal spacing token; 16dp vertical padding; content max 620dp; vertically scrollable | Center the content column on large displays; every action remains scroll-reachable |
| Home compact choice | Scene is 90dp high if available height <680dp or font scale >1.35; otherwise 118dp | Preserve useful controls and hierarchy on short/large-text devices; artwork may compact before body text |
| Home large text | Difficulty options and footer navigation become vertical above font scale 1.35 | Use content-driven/Dynamic Type adaptation; do not require a numeric 1.35 iOS scale mapping |
| Game narrow | 20dp horizontal /12dp vertical padding; max content width 500dp; header → actors → instruction → square board → bag | Same reading hierarchy, single column, natural scrolling |
| Game wide | Width ≥760dp and font scale <1.6: 32dp horizontal padding, max 1000dp, board and instruction/bag columns with 28dp gap | Show two columns when content genuinely fits; revert to one at accessibility text sizes |
| Player indicators | Stack if font scale >1.5, or available indicator width <320dp with font scale >1.2 | Actor names remain whole; never clip Computer or split it into an unusable narrow column |
| Supporting pages | Scrollable; 20dp horizontal /16dp vertical padding; max 660dp | Native safe-area scrolling and legible line lengths; footer text need not fit above fold |
| Reveal/result modal | Outer24dp padding; max400dp; inner24dp; contents16dp spacing; scrollable; no outside-tap dismissal; platform window animation disabled | Preserve content/focus isolation and stage-owned timing. Native modal must not add invisible blocking time or obscure essential buttons |
| Local handoff | Full screen; inner max400dp,20dp padding/spacing; board absent | Remove private game from render and accessibility trees, not merely blur/dim it |

Logical Android dp is a starting point for SwiftUI points, not a demand to match physical pixel dimensions across screens. Font scale is an Android test input; test corresponding readable Dynamic Type sizes rather than synthesizing an arbitrary multiplier for all iOS labels.

## 2. Complete screen/state inventory

All game rows use [GameScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameScreen.kt), [GamePanels.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/GamePanels.kt), and [PhysicalBoard.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/PhysicalBoard.kt) unless specified. `dark` reference IDs have `light` counterparts where the manifest includes them; absence of a captured counterpart is a coverage gap, not permission to omit its iOS implementation.

| Screen / state and reference ID | User intent and required information | Hierarchy, interaction and iOS acceptance |
|---|---|---|
| Home dark/light: `home-dark.png`, `home-light.png` | Understand the twist; start a game or find help/settings | Centered exact `Pic-Pac-Poe`; subtitle; decorative pieces; “Draw a piece. Choose a square.”; Pick a game; Classic; Pic-Pac Local; Vs Computer selection/Play; How to play/AI Lab/Settings. No extra badges. Wordmark is one heading; illustration is silent. [HomeScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/HomeScreen.kt) |
| Vs Computer setup: Home reference, optional `computer-setup-dark.png` | Select Easy/Medium/Hard, understand selected opponent | **This is a section of Home, not a separate Android screen.** Medium initial selection, choice check/fill/selected semantics; description updates; Play starts chosen difficulty. Selection survives view recreation via saveable state. Easy/Medium/Hard are production choices; MCTS/RL belong in Lab |
| Classic empty/start: `classic-dark.png`, `classic-light.png` | Know current actor/fixed piece and choose an empty square | Back; Classic heading; Player1/2 indicators; Place X or O; square board; no bag. Nine empty targets enabled; Player1 always X and Player2 always O even when rematch starter alternates |
| Classic midgame: `classic-game-dark.png` if provided | Continue without overwriting a square | Same board geometry; occupied squares readable but disabled; active actor and held fixed symbol update after each valid move. No reveal/handoff stages |
| Classic win/draw: result reference | Read result and choose rematch/Home | Stage becomes terminal immediately after human/classic final placement; final position + static win line as applicable; Rematch and Home. A draw is not styled/spoken as a win |
| Classic rematch: reuse Classic start reference | Play same mode with alternate starter | Empty board; same mode; opposite previous starter. Verify Player2-start shows Place O, not X based on turn count |
| Local handoff: `local-handoff-dark.png` | Pass device privately to next player | Pic-Pac Local label; numbered player marker; “Player 1, you're up.”/Player2; instruction; Ready; Home. Ready is the only reveal trigger. Board, held piece and bag are absent, including semantics; no background modal content leaks |
| Local reveal: `local-reveal-dark.png` | See exactly the piece drawn after Ready | Player label; “You drew X/O”; large piece on132dp recessed surface; explanatory line. Bag already decremented, board unchanged, all placement locked. Modal timed, no dismiss-by-outside-tap; Back/Home navigation cancels game, not secretly skips a reveal |
| Local placement: `local-placement-dark.png` | Place the drawn symbol strategically | Current numbered actor, Place X/O, piece-in-hand, board, Bag/Next draw/counts/rounded odds. Only empty cells enabled. A valid nonterminal place immediately leads to next private handoff |
| Human turn start: `human-turn-start-dark.png` if provided | Notice turn ownership before draw | “Your turn”; next-draw explanation; bag still before this draw; locked board. 300ms normal/160ms reduced foreground beat |
| Human reveal: `human-reveal-dark.png` | Learn own random piece | Actor You; “You drew X/O”; piece; “One piece. Your choice of square.” No touch shortcut before reveal clock. Keep exact friendly second-person copy, not “Player1 drew” |
| Human placement: `human-placement-dark.png` | Choose any legal empty square | You active; Place X/O; piece in hand; current board; future bag odds exclude held piece. Accessible enabled cells only. No apparent computer activity before the accepted human placement |
| Computer turn start: `computer-turn-start-dark.png` | Understand control has passed to AI | Computer indicator and “Computer's turn”; no new human reveal; board locked. Preserve existing board; draw begins only after turn-start beat |
| Computer reveal: `computer-reveal-dark.png` | See what AI actually drew | Computer label; “Computer drew X/O”; piece; “Computer will choose a square.” AI calculation begins in parallel, but a quick result cannot cut reveal short |
| Computer thinking: `computer-thinking-dark.png` | Understand a late computation is still in progress | “Computer is thinking”; “Choosing where to place X/O.”; held piece; board locked. Only needed when reveal ends before computation. Do not insert artificial thinking if result already ready, and do not use indefinite looping decoration |
| AI selected-cell focus / targeting: `computer-targeting-dark.png` | See where the AI intends to place | “Square selected”; exact row/column explanation; target gets two inset focus outlines; **target remains empty** until completion. Computer remains displayed actor; all cells disabled. Announce “Computer selected row r, column c” |
| AI placement: `computer-placement-dark.png` | Perceive actual move entering the selected well | Board now contains the AI symbol; “Placing X/O”; persistent selected-cell outline; “Computer placed X/O in row r, column c.” No hitbox movement. Domain may already have next actor or terminal outcome; display stays Computer |
| AI settled placement: `computer-settled-dark.png` | Read the committed board before next interruption | “Move placed”; “Computer's move is on the board.”; target/piece persist. No next human draw until settlement finishes. Reduced Motion keeps a320ms readable hold |
| Terminal computer result: `result-dark.png`, `result-light.png` | Understand final result after settlement | “Computer wins”, “You win”, or “Draw”; summary;188dp final-board evidence; Rematch/Home. Winner is actor who completed line, not owner of X/O. AI terminal move must pass placement and settlement before this overlay |
| How to play: `how-to-dark.png`, `how-to-light.png` | Learn shared-symbol rules and probability | Back/title; five X/five O statement; four steps; “You're not X. You're not O.”; worked next-draw example after X (4X/5O,44%/56%); speed does not change draw; Classic explanation. Headings and rule steps are meaningful semantic groups. [InfoScreens.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/InfoScreens.kt) |
| Settings: `settings-dark.png`, `settings-light.png` | Control feedback, motion and appearance | Back/title; Feedback section; Sound, Haptics, Reduced motion; Theme System/Light/Dark; local-only privacy statement. Exactly one semantic switch per setting, whole row toggles, description included; inner visual switch is not a duplicate target. Large text reflows descriptions/options |
| AI Lab: `ai-lab-dark.png`, `ai-lab-light.png` | Understand algorithms and try experiments | Back/title/subtitle; public-observation rule; Random/Heuristic/Expectiminimax explanation; Play MCTS with2,000 simulations; Play Q-learning with fixed-sample92.3% agreement statement; exact Hard explanation. Do not present92.3% as win rate or universal strength |

## 3. Control-state specification

| Component/state | Current visual and semantic behavior | Native acceptance |
|---|---|---|
| Primary/secondary button normal | At least52dp high; inset/base/rim and semantic face/content colors; native button role | Real Button with clear action and sufficiently large hit area |
| Button pressed | Finite 90ms contact interpolation; face travels at most 2dp when motion allowed; hitbox fixed | Tactile but no scale/translation of entire target; release returns to original; no repeated bounce |
| Button disabled | Recess face, secondary text, subtle border, no enabled shadow/base; disabled click handling | No action through pointer, keyboard, switch control or accessibility activation; retain legible label |
| Button focused |2dp rim; primary uses on-action contrast, secondary focus color | Keyboard/switch-control focus visible independently of color fill |
| Choice normal/selected | Minimum48dp; radio semantics inside group; selected check plus inverse fill, unselected recess | Exactly one selected state, not color-only; accessible selected trait; allow vertical layout |
| Choice pressed/focused/disabled | Pressed unselected surface raised; focused2dp outline; disabled noninteractive | Native state feedback without losing selected check or adding redundant focus stops |
| Back |48dp square with Back label; arrow flips with RTL | Use native navigation semantics; never flip English wordmark order with app direction |
| Home mode row |≥72dp, entire row button; decorative prefix/chevron silent; recess on press and2dp focus outline | Mode title and subtitle belong to one discoverable action |
| Settings row |≥64dp; switch role; whole row toggles; visual inner switch silent | One toggle target with on/off value and description; toggling row and thumb must not double-toggle |
| Empty enabled board well | Button role and “Row r, column c, empty”; coordinate bounds match drawn well | Row-major order; stable square target; actual cell target—not one giant canvas gesture overlay |
| Occupied/locked well | Symbol/coordinate description still available; disabled semantics | No input in any computer/reveal/handoff/terminal stage; occupied squares never overwrite |
| Selected AI well | Double inset line plus stage text/coordinate speech | Distinguish AI selection from keyboard focus; selection alone is not placement |
| Focused board well |3dp outer focus stroke | Visible focus without moving board, cells or neighboring pieces |
| Winning well/line | Focus outline; static completed line uses5dp recess under-stroke and2dp text stroke | Preserve line evidence and shape recognition without animated board rotation |
| Reduced motion | No title entrance, screen crossfade, piece interpolation or press travel; sequential timed stage holds remain | No disappearance of essential sequence. A shorter clock is not permission to skip reveal/target/place/settle |

## 4. Accessibility contract

### Spoken labels and grouping

- Wordmark is exactly **Pic-Pac-Poe**, one heading. Five drawn text parts are not five accessibility nodes. Its group colors and animation progress must not be spoken. Internal English ordering stays left-to-right even under RTL layout.
- Home decorative tabletop shapes, row prefixes/chevrons, and piece paths are silent unless their parent supplies game meaning.
- Actor indicators group actor name, turn/waiting label and underline into one comprehensible item. Player colors do not imply X/O ownership; copy supplies ownership explicitly in Classic and disclaims it in Pic-Pac.
- Stage instruction is a polite live region. Handoff, reveal and result headlines are assertive live regions on Android. VoiceOver equivalents must be tested for intelligibility; avoid emitting two overlapping identical announcements when an actor label and modal appear together.
- Human piece tray: `Piece in hand: X` or O. Computer placing/settling: `Placed X` or O. These are independent of piece color.
- Bag symbol groups: `X, 2 remaining, 40 percent next draw` and corresponding O example. Visible next-draw odds exclude the held symbol; do not read them as chance of the already-known held piece.
- Cell default: `Row 1, column 1, empty` or X/O. AI target: `Computer selected row 3, column 3`. AI placing/settling: `Computer placed O in row 3, column 3`.
- Result miniature is one noninteractive description: `Final board.` then row-major descriptions of all nine squares. Do not expose nine actionable buttons in that evidence miniature.
- Choice groups expose selected state. Settings expose exactly three switch targets. Typography/font family never substitutes for heading traits or action roles.

### Focus and privacy

Default visual order is also intended reading order: navigation/title → actors → stage instruction/piece → board in row-major order → bag. Supporting screens use heading structure and scroll-reachable controls. Handoff must have no board node anywhere behind it. Reveal/result clear underlying game semantics and are modal; do not allow VoiceOver to activate a background empty square. Back currently goes Home and discards the game; it is not “dismiss reveal and resume placement.” A native navigation adaptation must preserve this distinction.

On iOS explicitly test modal entry focus, result announcement, return focus after rematch, Home return, external keyboard focus, and screen-reader activation of disabled controls. Automated existence of a label is not proof its spoken order/timing is pleasant.

### Targets, text, contrast and adaptation

- Android controls target≥48dp where applicable; primary/secondary52dp; settings rows64dp; mode rows72dp. Preserve these generous logical sizes on iOS rather than shrinking to a platform minimum.
- At320dp logical width, board cell targets remain≥48dp in the existing fixture. Wells remain equal squares, including large text. The board is fixed geometry, not a typography container.
- Functional primary/secondary text and action labels are checked at≥4.5:1; meaningful symbol/actor/focus roles at≥3:1 over tested surfaces. See actual palette test and exported tokens for final measured values, including wordmark additions. Color alone is not a state indicator.
- Home wordmark is an identity fitting one line: its nominal display size may shrink only enough to fit, including large-text conditions. **This exception does not permit shrinking body copy or controls.** Emotional headlines wrap and modal contents scroll.
- Test at ordinary, approximately130%, and200% Android text inputs plus real native modal font scaling; iOS requires ordinary through accessibility Dynamic Type categories. Text clipping checks must inspect actual glyph layout, not just a large enclosing box.
- Dark/light use distinct semantic palettes. System theme follows platform selection. In iOS light/dark testing, include modal surfaces, disabled controls, selection marks, title groups and piece edges—not just background swap.

## 5. Evidence already available versus human sign-off

Repository test sources establish **automatable assertions**: grouped title semantics; glyph/line bounds; centered title bounds; font-scale/RTL layouts; selection/disabled roles; equal board wells; private handoff omission; locked computer stages; semantic cell announcements; recreation and reduced-motion behavior. Their execution results belong in the release verification report, not inferences from this document.

The following remain **unchecked human tasks** until a named tester records a device, OS, build revision and result:

- [ ] TalkBack complete navigation, reveal/result announcement timing, focus restoration and nonduplicated settings switches.
- [ ] Physical title entrance quality in dark/light and with Reduced Motion, including a fresh launch and return Home.
- [ ] Physical haptic quality and event intensity; no duplicate vibration after rotation or settings changes.
- [ ] Speaker/headphone sound balance, interruption/silent-mode expectations, and feedback opt-out.
- [ ] Large text and display zoom on a real phone, including native reveal/result windows.
- [ ] One complete Classic, Pic-Pac Local, Medium and Hard game; results/rematches, alternating starter and no premature human prompt.
- [ ] Fresh install and update from prior installed release without losing preferences or breaking restoration.
- [ ] Future iOS: VoiceOver, Switch Control/keyboard, Dynamic Type, system Reduce Motion, native scene interruption and physical feedback.

No Android emulator screenshot, semantics assertion or preview should be relabeled as human TalkBack/VoiceOver certification.
