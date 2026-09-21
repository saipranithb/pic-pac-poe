# Native iOS architecture

Status: approved architecture and Build Phase 2 implementation record
Reference commit: `dc7cb397ad335783c13c7976d4cef816d2fc0909`  
Android runtime candidate: `f936faf7d21e85ed71e859d94d65e125fe61b436`

This document records the implementation architecture approved after the canonical Android handoff assessment. The Android app and its four existing Gradle modules remain authoritative for shipped behavior. The portable contracts in [`docs/ios-handoff`](../ios-handoff/) govern the independent Swift implementation.

## Decision

Pic-Pac-Poe remains one monorepo with two native applications. The existing Android root stays in place. The native SwiftUI application lives under `ios/`; moving Android into an `android/` directory is not part of this effort.

The iOS game core is an independent native Swift implementation governed by shared JSON fixtures, exhaustive state-space checks and cross-platform continuous integration. Kotlin Multiplatform is not used. The rules and AI surface is small enough that Kotlin/Native export, interop, Java API replacement and an additional build toolchain would cost more than the code sharing would save. This decision can be revisited only if measured cross-platform rule churn later outweighs that cost.

The product baseline is:

- iOS and iPadOS 17.0 minimum, supporting iPhone and iPad.
- Xcode 26.6, build 17F113, with the bundled Swift 6.3.3 compiler.
- Swift 6 language mode and complete strict-concurrency checking.
- Bundle identifier `dev.saipranith.picpacpoe`, marketing version 2.0.0, build 1.
- Unsigned development until App Store Phase 1. No team, signing identity, provisioning profile, capability or store account is configured in the project.
- No third-party runtime packages, project generator, database, account, networking, analytics, advertising, cloud storage or cross-platform UI.

The bundle identifier is an approved implementation coordinate for unsigned development. Apple registration, ownership confirmation and signing remain owner-controlled App Store Phase 1 work.

## Repository boundaries

```text
app/                         existing Android application
game-core/                   existing Kotlin/JVM rules
game-ai/                     existing Kotlin/JVM AI
game-tools/                  existing offline JVM tools
docs/ios-handoff/            canonical shared behavior/design contracts
docs/ios/                    native architecture and verification records
ios/
  Config/                    deterministic build settings
  PicPacPoe.xcodeproj/       ordinary Xcode project and shared scheme
  PicPacPoe/                 SwiftUI application/platform integration
  Packages/PicPacKit/        local pure Swift package
  PicPacPoeTests/            hosted application integration tests
  Scripts/                   repeatable simulator evidence tooling
.github/workflows/           independent Android and unsigned iOS lanes
```

Android paths, Gradle commands and release history remain stable. A future directory move would cause broad workflow, path, documentation and developer-habit churn. If a concrete need appears after parity, it requires a separate mechanical change and full verification. Git can usually detect moved files, but path-based history and blame become less convenient; history must never be rewritten for cosmetic topology.

## Native module boundaries

The local `PicPacKit` package has three directional modules:

```text
PicPacPoe  ->  PicPacPresentation  ->  PicPacCore  <-  PicPacAI
```

- `PicPacCore` owns immutable domain values, validated board/state types, pure rules, AI observations and bounded-random contracts. It does not import SwiftUI.
- `PicPacAI` owns public chance/decision search state, the independent reference oracle, the production evaluator, Easy/Medium/Hard agents, the 2,000-simulation MCTS agent and the versioned tabular-policy reader. It receives lawful public observations only; offline training remains in `game-tools`.
- `PicPacPresentation` owns the main-actor coordinator, immutable view snapshots, presentation stages, injectable clock, AI and storage boundaries, settings/restoration models, the versioned persistence codec, the Foundation Application Support adapter and guarded ephemeral effects. It does not own SwiftUI views or visual, audio or haptic output.
- `PicPacPoe` owns SwiftUI views, scene-lifecycle wiring, dependency composition, design tokens, bundled assets, accessibility, sound and haptics.

Dependency direction is enforced by package targets and tests. The application root composes the production `PicPacAI` engine into `PicPacPresentation`'s worker boundary. Views render snapshots and send typed intentions. They do not mutate rules, consume the game random source or authorize stage completion. CPU-bound AI work runs outside the main actor and delivers a result through revision, turn-token, presentation-ID and task-generation guards.

## Build Phase 2 implementation boundary

Build Phase 1 established the unsigned project, fixture governance, rules/search foundations, coordinator, presentation state and restoration. Build Phase 2 completes the visible and playable product on top of those boundaries:

- Home, Classic, Pic-Pac Local, Easy/Medium/Hard computer play, Settings, How to Play and the complete AI Lab are implemented in native SwiftUI;
- the Form Playground 2.0 identity is reconstructed with native shapes and paths, bundled Fredoka resources, exact semantic colors, tactile pieces, inset board wells, governed spacing, finite motion and responsive light/dark layouts;
- Home's decorative bag-to-board explanation alternates X and O without touching domain state, persistence or game randomness, pauses while the scene is inactive and retains a non-spatial explanation with Reduce Motion;
- the application consumes production Easy, Medium and Hard agents plus AI Lab's Random, Heuristic, MCTS and Q-learning choices through the guarded worker boundary;
- local sound and haptic consumers honor their independent settings, while accessibility labels, traits, modal isolation, order and announcements derive from the presentation snapshot;
- Debug-only deterministic fixtures can render every required review state without replacing live production flows; and
- both Debug and Release simulator products build unsigned, with no signing, capability or store configuration.

The production app remains fully local. Debug fixture launches and screenshot controls are compiled only for development evidence; they do not consume live randomness, emit feedback or enter restoration. Build Phase 3 adds automated snapshot and full UI-flow coverage, calibrated parity checks, accessibility and lifecycle integration passes, and performance/device hardening. Signing and store work remain outside the unsigned build phases.

## Shared fixture governance

[`golden-fixtures.json`](../ios-handoff/golden-fixtures.json) is the single checked-in portable fixture source. Kotlin and Swift use independent test adapters that:

- reject an unknown schema version or unhandled fixture group;
- identify failures by fixture ID;
- consume the repository fixture directly on Kotlin and stage an exact byte-for-byte Swift test resource whose equality is enforced before behavioral assertions;
- compare exact discrete outcomes and use the contract's stated tolerance for floating-point values; and
- use scripted draws and scripted tie choices for stochastic cases instead of assuming that equal Swift and Kotlin seeds produce equal streams.

Generated exhaustive cases remain generated in tests. They are not expanded into a second large checked-in fixture file. A behavioral contract change must update the portable contract and both consumers in one reviewed change.

## Coordinator and restoration policy

One `@MainActor @Observable` coordinator is the only presentation mutation authority. Domain state and visible presentation stage remain separate because a move can be committed while the interface is still placing or settling it. Essential clock acknowledgements are identity-checked commands, not animation callbacks with mutation authority. App commands remain gated until the initial validated restoration completes.

The application declares single-scene support so one coordinator owns the one current-match file. When that scene becomes inactive or enters the background, the app checkpoints the current valid snapshot, stops stage acknowledgements and decorative motion, consumes pending feedback and cancels unfinished AI work. A canceled scene task cannot apply a stale phase after a slow restoration. Callbacks from an old scene/task generation are rejected, and coordinator teardown cancels its owned clock and search tasks. On activation, the app resumes the same stage with its full readable duration. An already committed move is never committed again; targeting restores its exact target; an unfinished AI search restarts only when no target has been selected.

The current restorable presentation, including a match when one exists, is stored as a versioned, validated Codable snapshot in app-private Application Support. The four typed settings use a separate Codable payload. Files are written atomically, and backup exclusion is reapplied and verified after replacement. Returning Home cancels work and removes the domain match payload while retaining the session counters, alternating next starter and title-entrance flag. Search caches, random-generator internals, elapsed animation time and consumed feedback events are not persisted. Invalid or unsupported snapshots safely return Home. No iCloud, Files exposure or match-history database is used.

## Product and platform mapping

| Concern | Owner |
| --- | --- |
| Rules, bag conservation, actor/symbol distinction | `PicPacCore` |
| Easy/Medium/Hard and AI Lab algorithms | `PicPacAI` |
| Stages, guards, restoration values and effect events | `PicPacPresentation` |
| Scene lifecycle wiring and local-storage composition | iOS application |
| Storage contract, versioned codec and backup-excluded file adapter | `PicPacPresentation` |
| Design tokens, Fredoka resources and vector assets | iOS application, sourced from the handoff manifest |
| Accessibility labels, grouping, focus and announcements | SwiftUI views driven by presentation snapshots |
| Sound and haptics | Native effect consumers honoring independent settings |
| Golden behavior | Shared JSON plus independent Kotlin and Swift consumers |

AI Lab is part of the complete iOS product. It includes the production explanations, Random and Heuristic opponents, MCTS configured for 2,000 simulations, Q-learning backed by the unchanged 891,749-byte policy artifact, and the governed fallback behavior. Training, telemetry, cloud inference and new algorithms remain out of scope.

## Visual implementation and review

The SwiftUI layer uses the handoff's semantic tokens instead of platform-default Form, List or Button styling. Coral X, pistachio O, cream typography, chocolate surfaces, restrained bevels, borders and shadows remain semantic across both themes. Pieces keep stable board hitboxes and explicit placement, target, computer-turn, reveal and result states. The title uses five semantically colored Fredoka runs and a finite entrance. Decorative Home motion is scene-gated and independent from the coordinator.

The repeatable evidence tool builds the real Debug app unsigned and launches deterministic, in-memory presentation fixtures on an ephemeral simulator. It captures Home, human placement, Local handoff and reveal, computer targeting and settlement, result, Settings, How to Play and AI Lab in both themes, verifies the canonical Android reference hashes, and generates an uncropped comparison sheet. Fixture screenshots establish rendered state and visual intent; they do not substitute for Build Phase 3's live-flow, assistive-technology or performance checks.

## Six delivery phases

The fourteen milestones in the canonical parity checklist are acceptance criteria within these six owner-facing phases. Ordinary milestone work, verification and checkpoint commits proceed autonomously inside the authorized phase.

1. **Build Phase 1 — Foundations.** Scaffold, shared fixtures, Swift core, coordinator, presentation state and restoration.
2. **Build Phase 2 — Complete Product Implementation.** Design system, every product screen and mode, production AI, AI Lab, settings, instructions, motion, sound, haptics, accessibility and responsive light/dark layouts.
3. **Build Phase 3 — Parity, Integration and Hardening.** Full fixture, snapshot, UI-flow, lifecycle, accessibility, performance and visual-parity verification; defect correction; finalized unsigned CI; complete release-candidate codebase.
4. **App Store Phase 1 — Signing and TestFlight Readiness.** Ownership and agreements, identifier registration, protected signing, archive validation, TestFlight upload and approved-device release testing.
5. **App Store Phase 2 — Final Submission Package.** Store art and metadata, legal URLs, privacy/export/age declarations, final candidate and every App Store Connect requirement.
6. **Submission Phase — App Store Review.** Select and verify the approved build, complete the version record and submit it. Public release remains owner-controlled.

Only a technical blocker, missing external authority, destructive operation, uncovered material product decision or exhaustion of the current authorized phase requires an owner pause.

## Privacy and delivery boundaries

The native app remains fully local: no account, analytics, ads, tracking, remote configuration, crash-reporting SDK, WebView, HTTP client, cloud save or online AI. A final privacy manifest will declare only APIs the release actually uses. Legal URLs remain metadata requirements for later submission:

- `https://saipranith.dev/picpacpoe/privacy`
- `https://saipranith.dev/picpacpoe/terms`

Unsigned pull-request verification contains no credentials. Protected signing, internal delivery and production submission remain distinct later trust boundaries with explicit owner approval.
