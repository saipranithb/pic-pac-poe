# Native iOS architecture

Status: approved Build Phase 1 decision record  
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
  PicPacPoeTests/            future application integration tests
  PicPacPoeUITests/          future UI and accessibility tests
  TestSupport/               future fixtures, clocks and controlled workers
.github/workflows/           independent Android and unsigned iOS lanes
```

Android paths, Gradle commands and release history remain stable. A future directory move would cause broad workflow, path, documentation and developer-habit churn. If a concrete need appears after parity, it requires a separate mechanical change and full verification. Git can usually detect moved files, but path-based history and blame become less convenient; history must never be rewritten for cosmetic topology.

## Native module boundaries

The local `PicPacKit` package has three directional modules:

```text
PicPacPresentation  ->  PicPacAI  ->  PicPacCore
        |                    |             |
        +--------------------+-------------+
```

- `PicPacCore` owns immutable domain values, validated board/state types, pure rules, sessions, public search state and random-source capabilities. It does not import SwiftUI.
- `PicPacAI` owns the evaluator, heuristic agent, expectiminimax, MCTS and the versioned bundled policy reader. It receives lawful public observations only. Offline training remains in `game-tools`.
- `PicPacPresentation` owns the main-actor coordinator, immutable view snapshots, presentation stages, injectable clock and AI-worker boundaries, settings/restoration models, and guarded ephemeral effects. It does not own views or platform I/O.
- `PicPacPoe` owns SwiftUI views, scene lifecycle, persistence adapters, design tokens, bundled assets, accessibility, sound and haptics.

Dependency direction is enforced by package targets and tests. Views render snapshots and send typed intentions. They do not mutate rules, consume the game random source or authorize stage completion. CPU-bound AI work runs outside the main actor and delivers a result through revision, turn-token, presentation-ID and task-generation guards.

## Build Phase 1 boundary

Build Phase 1 establishes the unsigned project, fixture governance, rules/search foundations, coordinator, presentation state and restoration. It may add test-only Kotlin fixture consumption so both implementations execute the same contracts. It does not implement production screens, board rendering, typography, assets, feedback or store integration.

Phase 1 is accepted only when:

- the app and local package build with the pinned toolchain without signing;
- all shared fixture groups are recognized and executed independently on Kotlin and Swift;
- rules, ownership, conservation, rejection behavior and canonical state counts pass;
- the coordinator passes early, late, canceled and stale worker scenarios with virtual time;
- restoration validates invariants, does not redraw or replay moves, and resumes the exact presentation stage;
- no source, dependency or capability introduces networking, accounts, analytics, ads, cloud synchronization or background execution; and
- CI runs with read-only repository permissions and no signing or store secrets.

The product shell in this phase exists only to prove project buildability. Visible product implementation belongs to Build Phase 2.

## Shared fixture governance

[`golden-fixtures.json`](../ios-handoff/golden-fixtures.json) is the single checked-in portable fixture source. Kotlin and Swift use independent test adapters that:

- reject an unknown schema version or unhandled fixture group;
- identify failures by fixture ID;
- resolve the repository fixture rather than maintaining platform copies;
- compare exact discrete outcomes and use the contract's stated tolerance for floating-point values; and
- use scripted draws and scripted tie choices for stochastic cases instead of assuming that equal Swift and Kotlin seeds produce equal streams.

Generated exhaustive cases remain generated in tests. They are not expanded into a second large checked-in fixture file. A behavioral contract change must update the portable contract and both consumers in one reviewed change.

## Coordinator and restoration policy

One `@MainActor` observable coordinator is the only presentation mutation authority. Domain state and visible presentation stage remain separate because a move can be committed while the interface is still placing or settling it. Essential clock acknowledgements are identity-checked commands, not animation callbacks with mutation authority.

When a scene becomes inactive or enters the background, the app will checkpoint the current valid snapshot, stop stage acknowledgements and decorative motion, and cancel unfinished AI work. Callbacks from the old scene/task generation are rejected. On activation, the app resumes the same stage with its full readable duration. An already committed move is never committed again; targeting restores its exact target; an unfinished AI search restarts only when no target has been selected.

The last valid current match and four typed settings are stored as versioned, validated Codable data in app-private Application Support. Files are written atomically and marked excluded from backup after replacement. Home clears the current match. Search caches, random-generator internals, elapsed animation time and consumed feedback events are not persisted. Invalid or unsupported snapshots safely return Home. No iCloud, Files exposure or match-history database is used.

## Product and platform mapping

| Concern | Owner |
| --- | --- |
| Rules, bag conservation, actor/symbol distinction | `PicPacCore` |
| Easy/Medium/Hard and AI Lab algorithms | `PicPacAI` |
| Stages, guards, restoration values and effect events | `PicPacPresentation` |
| Scene lifecycle and local storage adapter | iOS application |
| Design tokens, Fredoka resources and vector assets | iOS application, sourced from the handoff manifest |
| Accessibility labels, grouping, focus and announcements | SwiftUI views driven by presentation snapshots |
| Sound and haptics | Native effect consumers honoring independent settings |
| Golden behavior | Shared JSON plus independent Kotlin and Swift consumers |

AI Lab remains in the first complete iOS product. It includes the production explanations, MCTS configured for 2,000 simulations and the existing policy binary unchanged. Training, telemetry, cloud inference and new algorithms remain out of scope.

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
