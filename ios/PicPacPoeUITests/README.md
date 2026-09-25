# Native UI regression contract

`PicPacPoeUITests` runs in the shared `PicPacPoe` scheme. Select Debug,
an installed simulator explicitly, and disable signing:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project ios/PicPacPoe.xcodeproj -scheme PicPacPoe \
  -configuration Debug -destination 'platform=iOS Simulator,id=<available-UDID>' \
  -parallel-testing-enabled NO -only-testing:PicPacPoeUITests \
  -resultBundlePath /tmp/picpac-ui.xcresult CODE_SIGNING_ALLOWED=NO test
```

Complete Classic, Pic-Pac Local, Easy, Medium, Hard, MCTS and Q-learning tests
start from Home and use native controls, actual production AI, real game
randomness and ordinary presentation durations. They complete a game and a
rematch; Classic also exercises an explicit draw. Assertions do not depend on
a random winner. Together these paths complete 15 ordinary games. Settings and
relaunch tests use the same atomic file adapter as production in an isolated
Application Support namespace. They do not fake restoration with an in-memory
store.

Separate tests identify their controlled starting snapshots explicitly:

- Held screenshot fixtures audit transient-stage privacy, locked input and
  both themes. These tests do not claim live timing or AI execution.
- Lifecycle and terminal-settlement tests restore valid states, then run real
  coordinator transitions with 20-fold essential holds so XCTest can inspect
  boundaries reliably. Already selected targets never restart AI.
- The governed human/computer sequence uses actual Home selection, legal human
  placement and Hard search, with 10-fold essential holds. Each kept screenshot
  name includes monotonic system uptime; xcresult also records capture time.
  These frames prove order and state, not normal-speed animation pacing.

Two layout regressions retain the prior 24 methods and bring the source
inventory to 26:

- `testBoardFramesStayFixedAcrossHumanAndComputerStagesAtLargeText` compares
  every native board-cell rectangle within 0.5pt across human turn start and
  placement, plus computer thinking, target, placement and settlement. It runs
  at regular and AX3 text sizes using both top and explicit bottom fixture
  anchors, checks square targets of at least 48pt and locked-stage input, and
  requires the complete instruction rectangle inside the top viewport.
- `testTutorialStepsRemainReadableAtAccessibilityTextSizes` checks the number
  and complete body in each combined tutorial accessibility row, then uses
  real scrolling to bring all four rows fully into view at AX3 and AX5. The
  retained originals are also visually inspected for intact marker punctuation;
  semantic labels alone cannot certify painted glyph layout.

The geometry test retains 24 full frames and the tutorial test retains eight.
A bottom fixture anchor isolates layout from scrolling behavior; it is not a
production initial offset or evidence of a user gesture. Historical 24-method
results remain valid evidence of their exact source, and are not relabeled as
26-method executions.

All launch injection is in `DebugLaunchConfiguration.swift` under `#if DEBUG`.
Release app code contains neither fixture injection nor test storage routing.
The UI test target is never built for Archive, Profile or Run actions.

Automated accessibility coverage includes labels, row-major ordering, equal
stable square hitboxes, occupied/locked input, result/handoff/reveal privacy,
one final-board summary, one switch per setting, selection, scrolling and
largest Dynamic Type. Apple's contrast, hit-region, description, clipping and
trait audits run across nine screens in both themes, including the bottom
viewport of scrollable pages. One narrowly governed iOS 26.5 auditor artifact
is acknowledged: the dark AI Lab introductory paragraph at its bottom anchor
is clipped offscreen while its accessibility rectangle retains less than 1pt
of boundary intersection. Only that exact label, contrast issue and 402×874pt
viewport can be handled, after the fully visible paragraph passes in the same
build/theme. Original visible pixels measure 9.856:1. The native finding,
bounds and unmodified screen remain in evidence; this is not a zero-finding
claim or a blanket exclusion. Every other finding fails. Physical VoiceOver
speech timing, Switch Control hardware, keyboard ergonomics, audio and haptics
still require the named-device release review; a passing simulator
suite cannot certify them. The iOS 26.5 simulator also does not establish
iOS 17 minimum-runtime behavior. Locally reproduced workflow commands and
inspected workflow definitions do not establish GitHub-hosted Actions results;
pushing and hosted execution remain owner-controlled.

For an accepted full run from a clean checkpoint, use the governed runner:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  python3 ios/Scripts/ui_phase3.py --destination <available-UDID> \
  --output /tmp/picpac-ui-evidence
```

It retains test commands, logs, summary, raw attachment metadata, unmodified
frames, monotonic/capture timestamps and SHA-256 checksums. Source identity is
checked before and after execution. The source-discovered XCTest inventory must
match exactly, with all 26 methods passed and no skipped or expected failures.
All 37 required temporal/stage frame families must be present. Native audit
findings remain individually recorded in the manifest, including any precisely
acknowledged artifact. Failed or changed-source runs retain an `INCOMPLETE`
marker and cannot claim acceptance.

For ad-hoc diagnosis, export the native screenshots without cropping with:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcrun xcresulttool export attachments --path /tmp/picpac-ui.xcresult \
  --output-path /tmp/picpac-ui-attachments
```

Retain its generated attachment manifest, test summary, exact tested source
identity and toolchain/runtime beside any curated copy. Do not automatically
replace approved snapshot baselines from these UI tests.
