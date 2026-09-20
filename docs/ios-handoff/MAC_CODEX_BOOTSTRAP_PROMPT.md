# Mac Codex bootstrap prompt

Copy the prompt below into the Mac architecture/implementation task. It is self-contained once the repository has been cloned. Do not replace unresolved release metadata with a guessed SHA or version tag, and do not scaffold production iOS code until repository/core-sharing architecture is explicitly decided.

---

You are implementing **Pic-Pac-Poe for iOS** as a native Swift/SwiftUI application. The Android release candidate is approved as a product and visual reference. Your task is faithful behavior and visual-system parity, not another redesign. The canonical Android repository is:

`https://github.com/saipranithb/pic-pac-poe`

## First: establish the exact reference

1. Locate or clone the Android reference repository. If it already exists, inspect branch/status/remotes first and preserve any local work. Fetch authenticated remote references without resetting, cleaning, stashing, rewriting or discarding work. Do not print credentials.
2. Read `docs/ios-handoff/release-identity.json`. Use its `git.repositoryUrl`, `git.releaseCommit`, and `git.releaseTag` fields. Read the identity status/notes and any separate handoff-documentation revision rather than assuming the latest branch tip is the release.
3. The Android app identifies as `com.thevaguebox.probabilistictictactoe`; the candidate version is 2.0.0, code 5. Verify final values from the release identity and `app/build.gradle.kts` because a store-version conflict may require a later documented adjustment.
4. If `git.releaseTag` is populated, fetch that exact tag and resolve it to its commit using `git rev-parse '<tag>^{commit}'`. Require equality with the recorded full `git.releaseCommit`. Inspect the annotated tag and remote reference. Do not assume `v2.0.0`, a tag matching the app version, or the current `main` is necessarily the reference.
5. If the release tag/commit is null or the identity says release is blocked, **stop the exact-release bootstrap and report the unresolved field**. You may read and validate the package and perform the architecture assessment, but do not silently select a different revision or claim the source is tagged. Ask the owner for the final identity or explicit authorization to use the recorded runtime candidate.
6. Open the exact commit in a separate clean reference clone/worktree if necessary; never disturb local changes in an existing checkout. Verify the reference is clean. Keep it read-only during native implementation. A tag commit and a later documentation-only identity commit may differ: follow the relationship explicitly recorded in the manifest rather than expecting a self-referential commit hash inside itself.

## Read and verify the complete handoff before implementation

Read these repository-relative files completely, not just summaries:

1. `docs/ios-handoff/PIC_PAC_POE_IOS_HANDOFF.md`
2. `docs/ios-handoff/ANDROID_TO_SWIFTUI_MAP.md`
3. `docs/ios-handoff/SCREENS_AND_ACCESSIBILITY.md`
4. `docs/ios-handoff/IOS_PARITY_CHECKLIST.md`
5. `docs/ios-handoff/design-tokens.json`
6. `docs/ios-handoff/motion-spec.json`
7. `docs/ios-handoff/state-machine.json`
8. `docs/ios-handoff/ASSET_MANIFEST.md`
9. `docs/ios-handoff/golden-fixtures.json`
10. `docs/ios-handoff/PRIVACY_AND_STORE.md`
11. `docs/ios-handoff/REPOSITORY_AND_DELIVERY.md`
12. `docs/ios-handoff/reference/screenshot-manifest.json`
13. `docs/fonts/fredoka.md` and `app/src/main/assets/licenses/fredoka-OFL.txt`
14. `docs/ios-handoff/BEHAVIOR_AND_STATE.md`
15. `docs/ios-handoff/DESIGN_AND_MOTION.md`
16. `docs/ios-handoff/state-machine.mmd` and the rendered state diagram referenced by the main handoff

Also read the source files and tests linked from those documents. If the primary handoff identifies additional authoritative AI/rules/release artifacts, read them too. Run `python3 docs/ios-handoff/verify-handoff.py --strict-release`; the script is read-only and network-free. Verify all relative links resolve from this checkout; parse the JSON documents; check every manifest-declared asset/reference SHA-256 with macOS `shasum -a 256` (or the equivalent checks in that script). A mismatch is a blocker to treating that artifact as authoritative. Do not regenerate expected hashes to hide mismatches. The verifier checks on-disk metadata; separately verify the actual Git tag/commit and do not treat it as a CI, signing or human-accessibility certification.

The existing fonts live at `app/src/main/res/font/fredoka_medium.ttf` and `app/src/main/res/font/fredoka_semibold.ttf`. Reuse those exact licensed files according to the asset manifest, retain OFL notices in the iOS distribution, and verify their hashes. Do not download a similarly named current font or substitute a system-rounded face without approval. The Q-learning policy is `app/src/main/res/raw/picpac_rl_policy_v1.bin`; verify its version and binary contract before porting the reader.

Review all curated screenshots and the final contact sheet. Consult provenance in the manifest: a held state fixture is evidence of the real composable rendering, not a live-turn timing capture. Screenshots are **comparison references only**. Do not import entire screenshots, Android status/navigation bars, pre-rendered boards, or text rasterizations as app UI assets.

## Decide architecture before creating the native implementation

First compare all four documented options: one monorepo with native apps, separate repositories, a KMP shared core, and independently implemented native cores governed by shared specs/golden fixtures. Inspect the actual Mac/Xcode/Swift/Kotlin toolchains, repository ownership, CI access, expected rule churn and release permissions. Record the decision and tradeoffs; do not infer that this handoff has already chosen for you.

Then create the native Swift/SwiftUI project in the owner-approved topology. Keep the identified Android reference immutable/read-only. Do not introduce cross-platform UI. If choosing KMP, prove the Java RNG/I/O and Swift interop boundaries before migrating production behavior; if choosing duplicated cores, make `golden-fixtures.json` executable in both implementations and define fixture ownership/versioning.

Before choosing deployment-specific APIs, inspect the available Xcode/Swift/SDK versions and confirm the intended minimum iOS version. Use native SwiftUI presentation, immutable Sendable snapshots, a main-actor observable presentation coordinator, an off-main-actor search worker, injectable clock/RNG/AI interfaces and local typed settings/restoration. The engine may be pure Swift or an explicitly justified shared core, but it must remain UI-independent and pass the same fixtures. Preserve native safe areas, navigation, scene behavior, VoiceOver and Dynamic Type where these do not alter the product contract. Do not invent a signing identity or create external services/accounts.

## Non-negotiable behavior

- Pic-Pac is not fixed-symbol tic-tac-toe: either actor can place either randomly drawn symbol; the actor completing a line wins. Shared bag starts with five Xs and five Os and removes the piece at reveal, not placement. Future odds exclude the held piece.
- Only the game session can draw. Agents see public board, held piece and bag counts, never the future draw or live random source. Easy is heuristic, Medium searches four plies, Hard is full exact search; experiments stay in AI Lab.
- Keep rules phase separate from presentation stage. Human controls are locked outside valid human placement. Computer start → reveal → optional thinking → target → place → settle → next turn/result is observable and serialized.
- Carry revision, turn-token and presentation-ID guards. A stale callback/result cannot mutate a new game. Target/symbol survive recreation and settlement. Winning computer moves settle before the result; no spurious human reveal occurs afterward.
- Use actor labels You/Computer in AI mode. Piece colors do not identify actors. Local handoff removes the board and private contents entirely until Ready.
- Reduced Motion removes decorative movement but keeps readable reveal/target/place/settle beats. Title entrance is finite and consumed at start; return Home/recreation do not replay it. The sole repeating exception is Home's central X/O explanation: retain1400 ms/symbol alternation with a 400 ms opacity-only crossfade under Reduce Motion, or instant swaps with animations disabled; no scale pulse then.
- Reuse the native production X/coral and O/pistachio pieces in Home's fixed 44 dp illustration allocation. Normal motion has one 1→1.035→1 pulse at 200/340/520 ms and a 280 ms crossfade/scale transition at the end of each 1400 ms half. Bag, arrows, board and layout stay still. Run only when visible/active after preferences load, cancel when offscreen/inactive/disposed, restart from X, and expose one stable description: “A random X or O is drawn from the bag, then placed on the board.” No repeated announcements, RNG, saved illustration state, game callbacks, sound or haptics.
- Preserve exact centered `Pic-Pac-Poe`: Fredoka SemiBold, coral Pic, warm-neutral Pac, pistachio Poe, neutral subordinate hyphens; one accessible heading and stable one-line fit. Fredoka Medium is selective emotional copy; functional text stays system sans.
- Preserve local-only/privacy intent. No new accounts, ads, analytics, networking, cloud sync, monetization, or online play in this port.

## Work through the fourteen milestones

Follow `IOS_PARITY_CHECKLIST.md` in order: architecture/bootstrap; engine/fixture conformance; presentation coordinator; tokens/type; board/pieces; Home/navigation; Classic; Local; Vs Computer/AI; supporting screens; motion/feedback; accessibility; visual parity; release readiness.

For each milestone:

1. State the scoped acceptance criteria and affected native files.
2. Implement only that bounded portion while preserving previous passing behavior.
3. Run deterministic unit/coordinator tests; then XCUITest/simulator visual checks as appropriate.
4. Compare relevant native captures to committed Android references in both appearances and important Dynamic Type/Reduce Motion states.
5. Record source revision, tests/results, screenshot paths and justified native differences in the iOS repository.
6. Commit a coherent verified change. Do not label unrun tests or physical checks as passed.

Use original Android test invariants and `golden-fixtures.json` as parity oracles, not Kotlin syntax as a translation template. Exact search values and canonical state graph counts must match. For stochastic agents, use explicit common scripted randomness or specify a portable RNG before asserting cross-language seed equivalence. Profile actual iPhone/Simulator search isolation and frame pacing; Android emulator metrics are not iOS performance certification.

## Product boundaries and stop point

No generic AI gradients, glass-card soup, decorative blobs, promotional badges, excessive pills or gratuitous 3D. The existing bounded material highlights/shadows express tactile board/piece geometry; they are not permission to redesign the whole interface. Preserve only the specifically approved, visibility-scoped Home X/O illustration loop. Do not add other looping idle movement, game timers, alternative rules or extra confirmation steps that break established flow.

Keep physical haptic quality, sound balance, VoiceOver announcement/focus behavior, large text, complete games, and install/update checks visibly pending until a human actually verifies them on the intended iPhone build. Work toward a verified native release candidate/internal distribution. **Do not initiate a public App Store rollout, purchase services, alter the Android release, or expose signing secrets.** Stop at credentials/signing/2FA or a genuine product decision requiring the owner's authority and report exactly what is needed.

---

## Bootstrap completion record (Mac agent fills in its own repository)

```text
Android canonical repository:
Android release tag:
Android release commit:
Handoff documentation commit (if different):
Manifest/hash verification result:
Chosen repository/core architecture and rationale:
Native repository/project and initial commit:
Xcode / Swift / SDK / deployment target:
Simulator and physical-device targets:
Outstanding owner/signing decisions:
```

Do not edit the approved Android reference to fill this record. Preserve the record in the chosen iOS/project workspace with a durable source link.
