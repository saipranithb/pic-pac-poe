# Portable assets and reference provenance

All required files are in this Git repository. This package needs no chat attachment, workstation folder, temporary renderer installation or external image host. Run [verify-handoff.py](verify-handoff.py) with Python 3.9+ from any working directory; it is read-only and uses only the standard library. It checks local links, JSON structures, exact stage timing agreement, file sizes and SHA-256 hashes. Release certification remains a separate gate.

The repository [attributes](../../.gitattributes) pin handoff text and the checksummed font license to LF, while PNGs remain binary. This prevents automatic Windows line-ending conversion from invalidating portable byte checksums. No font bytes were modified.

## Reusable native assets

The machine-readable [assets-manifest.json](assets-manifest.json) declares exact sizes/hashes; paths are relative to this directory. Assets are referenced in place, not duplicated here.

| File | Role / portability |
| --- | --- |
| [fredoka_medium.ttf](../../app/src/main/res/font/fredoka_medium.ttf) | Official static Fredoka 2.001, weight500; reveal/handoff emotional headlines |
| [fredoka_semibold.ttf](../../app/src/main/res/font/fredoka_semibold.ttf) | Official static Fredoka 2.001, weight600; Home identity/result headlines |
| [fredoka-OFL.txt](../../app/src/main/assets/licenses/fredoka-OFL.txt) | Complete SIL Open Font License1.1/copyright; distribute with fonts in iOS |
| [Font provenance](../fonts/fredoka.md) | Pinned upstream revision, internal font names, exact byte sizes/hashes, unchanged-file provenance |
| [picpac_rl_policy_v1.bin](../../app/src/main/res/raw/picpac_rl_policy_v1.bin) | Existing offline-trained Q policy; binary version/header/row layout and fallback rules in [behavior specification](BEHAVIOR_AND_STATE.md) |

The app has no downloaded fonts, bitmap board, recorded sound files or 3D models needed by the port. Board, pieces, bag illustration, highlights and shadows are native Canvas/path geometry; rebuild from [design specification](DESIGN_AND_MOTION.md), not a cropped screenshot. Android system sans is not a redistributable bundled asset; use native iOS system sans for functional text. Android launcher/adaptive icon resources remain Android-specific source references; create a proper native App Icon asset using the approved product identity rather than copying Android adaptive masks/system UI. ToneGenerator output and platform haptic constants require native equivalents and physical review.

## Curated screenshots

[reference/screenshot-manifest.json](reference/screenshot-manifest.json) enumerates every screenshot and derived artifact with filename, SHA-256, byte size, source dimensions, theme, production method and limitations. Screenshot paths are relative to [reference/](reference/). Individual PNGs are resized uniformly to540px wide with no cropping; raw full-resolution captures exist as local verification evidence but are **not required** to use this portable handoff. The curated PNGs, contact sheet, comparison and rendered diagram are Git-owned.

| Kind | Meaning |
| --- | --- |
| `live-app` | Settled MainActivity rendered by the installed final production-source debug build, with real app settings |
| `live-ui-test` | Real MainActivity/ViewModel/domain/AI, actions through Compose UI taps; clock held/advanced to inspect a beat |
| `production-composable-fixture` | Actual production composables given controlled states, theme, width/font-scale and no-op stage callbacks; not a mockup and not proof of real search latency |
| `historical-live-app` | Before-color centered title; typography commit3ace864 plus the then-uncommitted centering change; intentionally not a final parity target |
| `derived` | Labelled contact sheet, before/after comparison or Mermaid-rendered diagram assembled from declared sources |

Native baseline is Android API35 x86_64 emulator,1080×2400 physical pixels,420dpi, font_scale1.0. Synthetic320dp/900dp fixtures change Compose density within the host window; they are not claims of separate physical devices. Native large-text captures actually set wm840×1870 at420dpi (320dp width) and font_scale1.3/2.0, then restore original emulator settings. System bars in fixture hosts may differ from app-owned bars; do not import them into iOS.

Canonical quick reviews:

- [Home X/O sequence](reference/home-scene-contact-sheet.png): current illustration, X then O in both themes, normal/reduced motion and 412dp/320dp layouts. Full `home-scene-*` screenshots are individually listed in the manifest; these supersede the old static-X illustration only. The rest of the original reference set retains its separately recorded source.
- [Final contact sheet](reference/contact-sheet.png):24 labelled states across Home, game, computer sequence, support screens and adaptive examples.
- [Home title comparison](reference/home-title-before-after.png): centered neutral before and final semantic color after, both themes.
- [Home dark](reference/home-dark.png) and [Home light](reference/home-light.png): primary identity references.
- [State diagram](reference/state-machine.svg): rendered Mermaid, editable [source](state-machine.mmd).
- [Checksum file](reference/checksums.sha256): reference artifacts and screenshot manifest; the checksum file does not checksum itself.

The Home screen includes the Vs Computer setup (difficulty selection and Play); it is not a separate modal. Settled live computer captures occur during AI_SETTLING with Computer still displayed, not after input has already returned to the human. Terminal result and next-turn semantics come from separate explicit states. Still images cannot certify haptic quality, spoken announcements, animation pacing or physical-device frame times.

## Portable nonvisual contracts

[`golden-fixtures.json`](golden-fixtures.json), [`design-tokens.json`](design-tokens.json), [`motion-spec.json`](motion-spec.json) and [`state-machine.json`](state-machine.json) are source contracts, not assets to ship in the user-facing app unless the chosen test architecture deliberately embeds test resources. They remain text so Android and iOS can parse the same fixtures independently. The verifier checks their schema/source paths and cross-document invariants; native platform test runners still need to execute the behavioral expectations.

## Reproduction and integrity

```sh
python3 docs/ios-handoff/verify-handoff.py
(cd docs/ios-handoff/reference && shasum -a 256 -c checksums.sha256)
```

For capture reproduction use the committed [HomeSceneAnimationTest](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/HomeSceneAnimationTest.kt), [BrandTypographyTest](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandTypographyTest.kt), [BrandLifecycleTest](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandLifecycleTest.kt), [FormPresentationTest](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/FormPresentationTest.kt), [ProductFlowTest](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/ProductFlowTest.kt) and [VisualCapture](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/VisualCapture.kt). Set instrumentation argument `captureFormScreenshots=true`; outputs go to the app's external-files `form-verification` directory on the **selected test emulator**. Always identify the intended device explicitly; do not run against a connected owner's phone accidentally. Full commands/results are in [release verification](RELEASE_VERIFICATION.md).

The Home sequence test freezes the Compose clock before composition, captures a settled X, advances 1400ms and captures O. It verifies actual production-piece colors, stable scene/control bounds and pixel-identical surroundings outside the center slot. Its synthetic 320dp case is the narrow phone fixture, not a claim of separate hardware. Unit tests sample the real pulse/fade specifications; the stills prove both symbols/layout, not subjective smoothness or TalkBack speech on a physical phone.

Render the diagram with the official pinned Mermaid CLI11.17.0:

```sh
npx --yes --package @mermaid-js/mermaid-cli@11.17.0 mmdc \
  -i docs/ios-handoff/state-machine.mmd \
  -o docs/ios-handoff/reference/state-machine.svg -b transparent
```

Rendering needs a supported Chromium installation or the renderer's normal bundled browser; consuming the committed SVG does not. Renderer/system font differences can change SVG layout/IDs without changing transitions. Do not overwrite expected reference hashes merely to suppress a failed check: investigate provenance and intentionally update the manifest only when producing a newly reviewed reference set.

The initial reference files were compressed/composited with resvg from PNGs without changing app artwork. A Mac implementation need not reproduce the contact-sheet builder to use the checked-in references. No screenshot is a shipping iOS UI asset. Retain individual images, source manifests, font license and source contract together when moving this package.
