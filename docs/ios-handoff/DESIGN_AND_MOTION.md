# Pic-Pac-Poe: portable visual, accessibility and motion contract

This is the implementation-level companion to [the iOS handoff](PIC_PAC_POE_IOS_HANDOFF.md). It describes the approved Android release candidate, not a new design direction. [design-tokens.json](design-tokens.json) and [motion-spec.json](motion-spec.json) are the machine-readable equivalents. Paths in JSON are relative to the repository root; Markdown links are relative to this document. For screenshots and provenance use [the asset manifest](ASSET_MANIFEST.md) and [reference directory](reference/).

## Authority and units

Source precedence is the tagged Android Kotlin implementation, then these extracted specifications, then screenshots as visual evidence. A screenshot is not an image to put behind SwiftUI controls. Reconstruct native layout, paths, typography and semantics. If a numerical specification differs from the tagged source, report it before changing product behavior.

| Source | Responsibility |
| --- | --- |
| [PicPacTheme.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/theme/PicPacTheme.kt) | Semantic palettes, functional typography, spacing, shapes, depth, common motion constants |
| [FormBrandTypography.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/theme/FormBrandTypography.kt) | Bundled Fredoka styles |
| [HomeWordmark.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/HomeWordmark.kt) | Width fitting, centered five-part/three-group identity, entrance and semantics |
| [PhysicalBoard.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/PhysicalBoard.kt) | Fixed board/wells, pieces, focus and winning line |
| [FormControls.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/FormControls.kt) | Surface, buttons, choices, back/text actions |
| [FormHomeScene.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/FormHomeScene.kt) | Native bag-to-piece-to-board drawing |
| [GamePanels.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/components/GamePanels.kt) | Actors, instructions, held piece, bag/probability tray |
| [HomeScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/HomeScreen.kt) | Home order, setup and compact layout |
| [GameScreen.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/GameScreen.kt) | Game layout, handoff/reveal/result, presentation clock |
| [InfoScreens.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/InfoScreens.kt) | How to Play, Settings, AI Lab |
| [PicPacApp.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/ui/PicPacApp.kt) | Screen fade, title consumption, sound/haptics |
| [SettingsStore.kt](../../app/src/main/java/com/thevaguebox/probabilistictictactoe/settings/SettingsStore.kt) | Persistent preferences |

Geometry below is in logical Android dp; type is nominal Android sp before user scaling. Start with equivalent iOS logical points, not emulator pixels. Font-scale/Dynamic Type mapping and safe areas are platform adaptations. Preserve relationships, optical footprint and semantics rather than copying a physical screenshot's resolution. Coordinates are top-left origin, positive y downward. Colors are opaque sRGB unless a layer explicitly supplies alpha. `lerp(a,b,t)` is the actual Compose color interpolation operation; do not mistake it for a separately named palette color.

## Identity and non-negotiable visual hierarchy

Form Playground 2.0 is a warm, restrained tabletop game: cocoa/cream environment, a substantial board, fixed recessed squares, coral X pieces and pistachio O pieces. Material character comes from shallow sidewalls, modest top light, a little contact shadow and immediate pressed states. It does not use scene lighting, camera perspective or moving board coordinates.

The displayed actor, held symbol, legal input, selected computer square and bag odds must be understood before decorative depth. Players are **not** assigned X/O in Pic-Pac. Player 1 uses teal and Player 2/Computer lavender; piece color never substitutes for actor identity. Explicit words, numbers, checks, borders and shapes carry every meaningful state.

Brand-defining: exact `Pic-Pac-Poe` spelling; centered Fredoka wordmark; restrained coral/neutral/pistachio word groups; system sans functional text; tactile front-facing pieces; nine equal fixed wells; real bag counts; calm finite motion. Anti-goals: generic AI gradients, glass-card soup, decorative blobs, promotional badges, excessive pills, neon, shimmer, glow, rainbow letters, fake title extrusion, gratuitous 3D or continuous idle motion. Existing small material-face gradients are intentional directional shading, not permission to introduce gradient branding.

## 1. Color system

The source fields are `x` and `o`, not separate player-ownership fields. `symbolX`/`symbolO` in the JSON are explanatory aliases. Every row below has alpha 255/1.0. The JSON includes both `[r,g,b,a]` integer RGBA and normalized float RGBA; use hex or integer values to avoid six-decimal rounding when generating native colors.

| Role | Dark hex / RGB | Light hex / RGB |
| --- | --- | --- |
| canvas | `#251F1C` / 37,31,28 | `#FBF1DE` / 251,241,222 |
| surface | `#47382F` / 71,56,47 | `#FFF9EC` / 255,249,236 |
| surfaceRaised | `#503E33` / 80,62,51 | `#FFFCF5` / 255,252,245 |
| recess | `#342923` / 52,41,35 | `#EADDC7` / 234,221,199 |
| text | `#FFF2DB` / 255,242,219 | `#34291F` / 52,41,31 |
| textSecondary | `#DDC5B5` / 221,197,181 | `#695543` / 105,85,67 |
| border | `#B59B88` / 181,155,136 | `#8D755F` / 141,117,95 |
| borderSubtle | `#796455` / 121,100,85 | `#C2AF95` / 194,175,149 |
| highlight | `#FFE5C6` / 255,229,198 | `#FFFDF7` / 255,253,247 |
| shadow | `#17120F` / 23,18,15 | `#9B8872` / 155,136,114 |
| x | `#FFAC8F` / 255,172,143 | `#983A24` / 152,58,36 |
| xHighlight | `#FFD5BC` / 255,213,188 | `#B44C32` / 180,76,50 |
| xEdge | `#BB775F` / 187,119,95 | `#6F2B1C` / 111,43,28 |
| o | `#BFD680` / 191,214,128 | `#4C6819` / 76,104,25 |
| oHighlight | `#DDEBB1` / 221,235,177 | `#638033` / 99,128,51 |
| oEdge | `#819748` / 129,151,72 | `#344C0C` / 52,76,12 |
| playerOne | `#7ADBD1` / 122,219,209 | `#00645D` / 0,100,93 |
| playerTwo | `#BEB9EF` / 190,185,239 | `#5B4B97` / 91,75,151 |
| focus | `#FFF2DB` / 255,242,219 | `#34291F` / 52,41,31 |
| action | `#FFAC8F` / 255,172,143 | `#983A24` / 152,58,36 |
| onAction | `#382218` / 56,34,24 | `#FFF9EC` / 255,249,236 |

Disabled buttons use `recess` face, `textSecondary` text, `borderSubtle` rim and zero elevation; they are not faded wholesale. Secondary buttons use `surface/text`. Selected choices invert to `text/canvas` and show a check, not simply an accent tint. System theme selects one complete palette; status/navigation-bar foreground appearance follows the selected dark/light theme. Dynamic wallpaper colors are not used. Material surface tint is transparent. The JSON records every explicit Material color-scheme mapping; unspecified Material roles remain library defaults and are not approved new brand accents.

### Measured contrast

Computed from exact opaque sRGB tokens: convert channel `c` to linear `c/12.92` when `c <= 0.04045`, otherwise `((c+0.055)/1.055)^2.4`; `L = 0.2126R + 0.7152G + 0.0722B`; ratio `(max(L1,L2)+0.05)/(min(L1,L2)+0.05)`. These are mathematical token-pair measurements, not a claim that an entire screen or every anti-aliased pixel has been audited.

| Foreground / background | Dark | Light |
| --- | ---: | ---: |
| Pic (`x`) / canvas | 8.943:1 | 6.318:1 |
| Pac (`text`) / canvas | 14.694:1 | 12.636:1 |
| Poe (`o`) / canvas | 10.179:1 | 5.676:1 |
| Hyphens (`textSecondary`) / canvas | 9.856:1 | 6.286:1 |
| text / surface | 10.122:1 | 13.496:1 |
| textSecondary / surface | 6.790:1 | 6.713:1 |
| textSecondary / recess (disabled labels) | 8.557:1 | 5.255:1 |
| x / surface (odds) | 6.161:1 | 6.748:1 |
| o / surface (odds) | 7.012:1 | 6.062:1 |
| playerOne / surface | 6.871:1 | 6.714:1 |
| playerTwo / surface | 6.068:1 | 6.906:1 |
| onAction / action | 8.190:1 | 6.748:1 |
| border / recess | 5.384:1 | 3.235:1 |
| focus / recess | 12.757:1 | 10.564:1 |
| borderSubtle / canvas (decorative only) | 2.917:1 | 1.901:1 |

All four wordmark roles exceed 4.5:1 in both themes; no palette change is needed for the requested title. Keep normal informative text >=4.5:1, large text >=3:1 and meaningful non-text controls/state outlines >=3:1. `borderSubtle`, piece bevels and shadows are decoration and must not become the sole state signal. For example dark `xEdge` against `surfaceRaised` is only 2.847:1, while the X face itself is 5.558:1. Light O highlight against `surfaceRaised` is 4.392:1 and is a drawn piece face, not small text. Do not reuse bevel/highlight tokens for functional labels.

## 2. Typography

“Playful but mature” means one display accent, not an all-Fredoka interface. The locally bundled unchanged static [Fredoka Medium](../../app/src/main/res/font/fredoka_medium.ttf) (500) and [SemiBold](../../app/src/main/res/font/fredoka_semibold.ttf) (600), version 2.001, retain the full [SIL OFL 1.1 license](../../app/src/main/assets/licenses/fredoka-OFL.txt). [Font provenance](../fonts/fredoka.md) has upstream revision, authors and SHA-256. No runtime download, synthetic bold, variable-font conversion, modification or duplicate font copy is needed for the handoff. The iOS app must bundle the same TTFs and attribution when created.

| Role | Family/weight | Size / line height | Tracking | Usage |
| --- | --- | --- | --- | --- |
| wordmark | Fredoka 600 | 40 / 46 | -0.35 | Home identity only |
| emotionalHeadline | Fredoka 500 | 30 / 35 | 0 | Reveal and local handoff |
| resultHeadline | Fredoka 600 | 30 / 35 | 0 | Win/draw result title |

All brand styles disable extra Android font padding, center the line-height alignment and trim neither edge. The wordmark's 40sp optical size deliberately maintains the former 42sp system-title footprint despite Fredoka's different metrics. Do not revert it to 42. Functional roles use Android `FontFamily.SansSerif`, not a bundled Roboto file; iOS should use native system sans with matching hierarchy and weights rather than embedding an Android system font.

| Functional role | Size | Line height | Weight | Tracking |
| --- | ---: | ---: | ---: | ---: |
| displayLarge | 42 | 46 | 800 | -0.8 |
| displayMedium | 36 | 41 | 800 | -0.6 |
| displaySmall | 32 | 37 | 700 | -0.4 |
| headlineLarge | 30 | 35 | 800 | -0.4 |
| headlineMedium | 24 | 29 | 700 | -0.2 |
| headlineSmall | 22 | 27 | 700 | 0 |
| titleLarge | 20 | 25 | 700 | 0 |
| titleMedium | 16 | 22 | 600 | 0 |
| titleSmall | 14 | 20 | 600 | 0 |
| bodyLarge | 16 | 24 | 400 | 0 |
| bodyMedium | 14 | 21 | 400 | 0 |
| bodySmall | 12 | 18 | 400 | 0 |
| labelLarge | 14 | 19 | 700 | 0.15 |
| labelMedium | 12 | 17 | 700 | 0.4 |
| labelSmall | 11 | 16 | 600 | 0.4 |

Percentages in the bag tray use headlineMedium, explicit weight 700 and OpenType `tnum`; probability numbers remain functional sans. Navigation, instructions, actor names, button labels, counts, settings, rules and AI explanations never switch to Fredoka. iOS line metrics will not be byte-identical; use actual font measurements and screenshots, not an arbitrary top-offset patch. Avoid cropping ascenders/descenders to mimic Android.

## 3. Final wordmark

Render a wrap-content horizontal sequence inside a full-width top-centered container:

| Visual run | Token | Animation group | Initial rotation |
| --- | --- | ---: | ---: |
| Pic | x | 0 | -0.8 degrees |
| - | textSecondary | 1 | 0 |
| Pac | text | 1 | +0.6 degrees |
| - | textSecondary | 2 | 0 |
| Poe | o | 2 | -0.6 degrees |

Hyphens have the same font/weight/style, but neutral secondary color and no rotation. There is no inter-run spacer, per-letter coloring, outline, extrusion or title gradient. The existing 1dp horizontal padding on the row gives 2dp total safety allowance. All five runs share one measured line. Force only this English identity's internal order to LTR; do not force the whole app into LTR.

Measure each run using the actual font at the actual accessibility scale, explicit LTR and no wrap. Sum the five widths. Available width is container width minus twice the pixel-rounded 1dp allowance. Use 40sp/46sp when it fits; otherwise binary-search nominal font size in `[1,40]` for twelve iterations and keep line height `size * 46/40`. This width fitting applies **only to the product identity**, not body text or controls. At a 320dp screen with Home's 20dp gutters, the parent width is 280dp and nominal fit allowance approximately 278dp. Do not use ellipsis or wrap `Poe` to another line.

Expose one accessibility heading with exact text `Pic-Pac-Poe`; hide the five visual children. The internal test progress property is not a spoken state. Center the visual run bounds, not merely an invisible full-width wrapper. Subtitle below remains left aligned with Home content, starts 8dp lower, and retains system bodyLarge. Everything below the title has the existing footprint.

### Entrance, including lifecycle

Each group has progress `p`. Start delays are 0, 70 and 140ms. Each runs 480ms: at 0ms `p=0`, at 360ms `p=1.015`, at 480ms `p=1`. Segment 1 uses cubic `(0.4,0,0.2,1)`; segment 2 `(0,0,0.2,1)`. The final group completes at 620ms. This is a bounded spring-like settle, **not** a physical spring; there are no damping/stiffness parameters to reproduce.

`opacity = clamp(p,0,1)`; `y = 8dp*(1-p)`; `scaleX = scaleY = 0.95 + 0.05p`; `rotation = initialRotation*(1-p)` except hyphens always zero. The tiny overshoot is y=-0.12dp and scale=1.00075. Animate graphics only: no layout, board, timer, subtitle or hit-area motion. At completion all properties are exact final values and no task loops.

The app shell owns a saveable `titleEntranceConsumed` flag, initially false. It consumes the entrance when it **starts**, not after 620ms. The child latches its initial request, so the consumption recomposition does not cancel the running animation. Persisted preferences must load before starting, preventing a brief animated title for someone who already enabled Reduced Motion. Return from Settings/game/How to Play/AI Lab does not replay. Recomposition/theme changes do not replay. Activity recreation, including recreation during the entrance, restores consumed state and shows the final title. Restored process saved-state retains that decision; a truly fresh session without saved state may animate again.

Reduced Motion shows final progress immediately, and consumes a pending entrance once settings are known. Enabling it during entrance cancels/snaps to final. Disabling it again does not restart. SwiftUI can use native opacity/offset/scale/rotation with a finite elapsed-time/keyframe driver and native cubic timing curves. Keep the consumption flag in the scene/coordinator state rather than transient wordmark view identity. Do not replace the exact duration with an unconstrained spring.

## 4. Space, shapes and depth

Base spacing: 4, 8, 12, 16, 24, 32dp; screen inset is 20dp. There are deliberate component-specific gaps below (for example 6dp wells and 28dp wide game columns), so do not round every measurement onto the base scale.

Corner radii: choice 10, control 14, surface 18, board 24, well maximum 18, Material extra-large 28dp. Depth constants: surface elevation 2, control base 3, press travel 2, rim 1dp.

`FormSurface` draws a vertical `surfaceRaised -> surface` face, 0.75dp `borderSubtle` outline and normally a 2dp Compose elevation shadow, then clips content to its shape. Recessed surfaces use `lerp(recess,shadow,0.12) -> recess`, 0.75dp `border` outline, no elevation. `elevated=false` also disables elevation. Android `Modifier.shadow(2.dp)` does **not** specify an app-controlled blur radius, offset, shadow color or opacity. These are platform/rendering defaults, not missing portable magic numbers; choose a restrained native shadow by reference comparison and document the iOS adaptation. Custom board/piece shadows below are explicit unblurred path draws and must not be replaced with a dramatic ambient blur.

## 5. Board, wells and pieces

### Board dimensions and layer order

Board width equals height. Phone content width is `min(availableWidth-40dp,500dp)`. In wide mode there are two equal columns with a 28dp gap in a content width capped at 1000dp. No perspective or tilt.

For board side `B`, content inset `I=12dp`, gap `G=6dp`, the cell side is `C=(B-2I-2G)/3`. A cell center is `(I + col*(C+G)+C/2, I + row*(C+G)+C/2)`. Row-major index is `3*row+col`. Nine visual cells and nine hit areas use this same geometry. At screen width320dp, board280dp gives cells81.333dp. Never scale individual cells for emphasis or shift an empty target while a finger is down.

Back-to-front drawing:

1. Board shadow: `shadow` at alpha .24, offset `(0,3dp)`, same board size and 24dp corner, no blur.
2. Body: vertical `surfaceRaised -> surface` gradient, 24dp radius.
3. Border: `border`, stroke1dp, origin(.5,.5)dp, size reduced by1dp in each dimension, radius24dp.
4. Top highlight: `highlight` alpha .35, line `(24,1) -> (B-24,1)`dp, round caps, stroke1dp.
5. Wells and their pieces within12dp inset,6dp gaps.
6. Optional winning line over the grid, connecting the first and third winning-cell centers: `recess` stroke5dp then `text` stroke2dp, round caps. It is static, not a traveling highlight.

### Wells

Radius `R=min(18dp,0.19*C)`. Draw vertical `recess -> surface`; then1dp `border` at .5dp inset, dimensions reduced1dp; then top inset edge from `(R,2dp)` to `(C-R,2dp)`,2dp round-capped `shadow` alpha .32 (pressed .65). If pressed and enabled, add a full rounded wash `border` alpha .16.

AI target **or winning cell**: `focus` outline2dp, inset3dp, dimensions reduced6dp, same corner radius R. AI target additionally gets a1dp outline inset7dp with radius `max(1 physical pixel,R-7dp)`. Focused keyboard target adds outer3dp `focus` outline. AI double outline exists only for `AI_TARGETING`, `AI_PLACING`, `AI_SETTLING`. Winning/focus effects do not change geometry or imply input is enabled. Piece occupies well bounds with4dp padding. Highlighted disabled cells still announce disabled.

### Piece construction

Let `s=min(pieceBoxWidth,pieceBoxHeight)` after caller padding, `cx=w/2`, `cy=h/2`, radius `r=.25s`, stroke width `.19s`, thickness `t=min(2dp,.035s)`. X consists of the two diagonals from `(cx-r,cy-r)` to `(cx+r,cy+r)` and `(cx+r,cy-r)` to `(cx-r,cy+r)`. O is a circle centered `(cx,cy)` of radius r. Strokes have round caps.

Draw the same path in this order:

1. Contact shadow, translated y=`t+2dp`, `shadow` alpha .12, stroke `.19s+3dp`, no blur.
2. Sidewall, translated y=t, `xEdge`/`oEdge`, stroke `.19s`.
3. Face, no translation, vertical linear gradient from `(cx,cy-r)` to `(cx,cy+r)` with `xHighlight -> x` or `oHighlight -> o`, same stroke.

The mark is decorative; its owning cell/label provides speech. Common boxes: Home44dp; probability28dp; held-piece surface62dp with4dp piece padding; reveal132dp. Result board188dp, static, no target or cell controls. Piece placement progress p animates alpha p, scale `.96+.04p`, y=`-3dp*(1-p)` over180ms, cubic `(.2,.8,.2,1)`. It drops only3dp into a fixed square. Reduced Motion uses0ms. An already occupied well on first composition initializes settled, not a fresh drop.

## 6. Home explanatory scene

This is code-native art, not a PNG. It is noninteractive and accessibility-hidden. Scene height118dp normally or90dp compact; drawing container capped400dp wide and centered. For drawing width W and height H: `S=.82H`, `T=(H-S)/2`, `bagLeft=max(.2W-S/2,0)`, `boardLeft=.8W-S/2`. A standalone44dp X is centered in the container.

Bag path in normalized local `(u,v)` units of S, translated by `(bagLeft,T)`: move(.22,.20); cubic controls(.14,.39),(0,.63), end(.10,.84); cubic controls(.19,1.02),(.81,1.02), end(.90,.84); cubic controls(1,.63),(.86,.39), end(.78,.20); close. Shadow is full `shadow` translated down2dp; face vertical `surfaceRaised -> surface` over T..T+S;1dp border. Opening oval at(.19,.08), size(.62,.22), recess fill then1dp border. Seams run(.31,.51)->(.26,.79) and(.69,.51)->(.74,.79),1dp `borderSubtle`, round caps.

Mini-board: S square, radius .12S, full `shadow`2dp below; surfaceRaised face then1dp border. Frame .075S, gaps .035S, cell side `(S-2*.075S-2*.035S)/3`; wells recess with .19*cell corner. Center well index4 has text-colored1.5dp rim, all others borderSubtle1dp. Arrows lie at `T+.52S`, textSecondary1.5dp round stroke,4dp diagonal arrowheads. First spans `bagLeft+S+5dp` to `.5W-26dp`; second `.5W+26dp` to `boardLeft-6dp`; omit an arrow if span<4dp. No seams or title decoration should imply choosing X/O before drawing.

## 7. Controls and panels

| Component | Exact geometry/style | Semantics/state |
| --- | --- | --- |
| Primary/secondary button | min52dp high, radius14; label titleMedium centered; content padding20h/14v; base3dp; elevation2dp enabled,0 disabled; normal rim.75dp, focus2dp | Button role; fixed target. Primary action/onAction, base xEdge; secondary surface/text, base shadow. Disabled recess/textSecondary, no base |
| Button face light | top `lerp(face,highlight,.035)` enabled; bottom `lerp(face,shadow,press*.08)` | Primary rim `lerp(face,highlight,.14)`; secondary border; disabled borderSubtle; focused rim onAction primary/focus secondary |
| Choice | min48dp, radius10, padding8h/12v; labelLarge;1dp border,2dp focused | Radio semantics within group, selected text/canvas inversion plus13dp check; check2dp round strokes at(.13,.53)->(.40,.78)->(.90,.20),5dp gap |
| Back | 48x48dp, radius14;22dp icon,2dp round strokes; rim.75dp or2 focused | Single “Back” label; icon mirrors RTL; normal surface, pressed recess |
| Text action | min48dp, radius10, padding10h/12v, labelLarge textSecondary | Transparent resting; surface when pressed;2dp focus border |
| Mode row | min72dp, radius14; padding4h/12v; leading decorative mark46dp; title headlineMedium/subtitle bodyMedium; arrow start12dp | Whole row button; marks/chevron not separately spoken; recess press;2dp focus |
| Settings row | min64dp, radius14, vertical16dp; title titleLarge, description bodyMedium;12dp gap before switch | Whole row one Switch-role target; child native Switch semantics hidden;2dp focus; recess press |

Buttons animate press/release over90ms using `(.2,.8,.2,1)`: face translates down2dp * p; shade changes by p. Reduced Motion keeps immediate shade but sets translation0 and duration0. Other custom pressed/focused/selected state changes are immediate. Material3 Switch retains library-native motion; the app does not hardcode its timing or dimensions. Port it as a native iOS toggle with theme-aware tint and one accessible target, not a screenshot reconstruction.

Player indicators:7dp actor-colored dot,8dp label gap; titleMedium actor label is text when active, textSecondary otherwise. LabelMedium status has3dp top/8dp bottom padding, actor color when active, textSecondary otherwise. Active underline3dp actor color; inactive1dp borderSubtle. The two players have20dp horizontal separation or12dp vertical stack spacing. Each actor/status pair is a merged accessibility group. “Waiting” is explicit, not just dimming.

Turn instructions: headlineMedium + bodyMedium secondary detail with4dp gap. Status column is a polite live region. Held-piece62dp recessed surface at the trailing edge with12dp gap; speech is “Piece in hand: X/O” or “Placed X/O” for computer placement/settlement. No held-piece tile at TERMINAL. The computer-selected coordinate is present in text, cell semantics and the double outline.

Bag tray: non-elevated FormSurface,16dp padding. Header “Bag” titleMedium / “Next draw” labelMedium; divider borderSubtle with10dp vertical padding; equal X/O columns with16dp gap. Piece28dp, count titleMedium starts5dp later, percent headlineMedium weight700 with tabular figures2dp below, X/O semantic color. Footer bodySmall textSecondary10dp below. It announces each column as “X, N remaining, P percent next draw”. Display percent is rounded, not truncated. Footer explicitly says held piece excluded when applicable. The tray does not animate counts or conceal information for suspense.

## 8. Screen layout and state inventory

All app pages honor status/navigation safe areas and allow vertical scrolling rather than shrinking functional type. They use canvas backgrounds, not decorative image backgrounds.

| Screen/state | Hierarchy and interactions | Visual/accessibility parity |
| --- | --- | --- |
| Home dark/light | Centered wordmark, left-aligned subtitle, decorative scene, centered caption, Pick a game, Classic and Local rows, Vs Computer, difficulty choices, description, Play, footer | One wordmark heading; Medium initially selected; chosen difficulty check/semantics; Play reachable. No new badges/pills |
| Classic | Back/title, actor indicators, “Place X/O”, fixed board; no bag | Player1=X, Player2=O only here. Empty squares enabled; occupied disabled. Shared component style |
| Local handoff | Entirely separate centered presentation, mode label,56dp numbered actor tile, Fredoka headline, explanation, Ready, Home | The board and private contents are absent from composition and accessibility, not dimmed underneath. Ready explicitly reveals once |
| Human/local reveal | Modal: actor label, Fredoka “... drew X/O”,132dp recessed piece, explanation | Assertive announcement. Background semantics hidden. Human does not dismiss early or choose cell during reveal |
| Human placement | Actor/status, held piece, board, bag | “Place X/O / Choose any empty square.” Only legal empty targets active |
| Computer TURN_START | Computer actor before automatic draw, board/bag visible | Explicit “Computer's turn”; all board input locked |
| Computer REVEALING | Modal “Computer drew X/O”,132dp piece, “Computer will choose a square.” | Clearly not a human placement prompt; board inaccessible |
| AI_THINKING | “Computer is thinking”, held symbol, board/bag | No decorative spinning or guessed completion; real AI result controls exit |
| AI_TARGETING | “Square selected”; coordinate detail; static double outlined empty selected square | Computer remains actor; selected cell polite description; no taps |
| AI_PLACING | “Placing X/O”; piece committed at same target | No target movement; short contact animation only; Computer remains actor |
| AI_SETTLING | “Move placed”; unchanged committed board/target | Board stays visible and locked through hold, including terminal move |
| Result | Static modal Fredoka result title, explanatory text,188dp final board, Rematch, Home | Assertive result; one textual final-board summary, no nine phantom controls; computer terminal result waits settlement |
| How to Play | Back/heading, rules, ownership explanation, probability example, Classic explanation | Headings and numbered steps; examples do not look like selectable symbol choices |
| Settings dark/light | Feedback heading, Sound/Haptics/Reduced motion toggle rows, Theme choices, privacy text | Exactly three accessible switches, explicit selected theme; no duplicate child switch target |
| AI Lab dark/light | Back/title/subtitle, observation explanation, methods, MCTS/Q-learning actions, budgets/limitations | Functional sans and explicit experiment descriptions; no invented difficulty badges |
| Disabled/focused/pressed/selected | Same fixed component hierarchy | Native semantic state + readable non-color indicators; no hit-area shifts |

Reference filenames and production methods are in the reference manifest owned by this package. Match both themes and the transient state, not merely the most attractive Home image. The state-machine document is authoritative for transition guards; this table is visual/semantic responsibility only.

### Exact screen bounds and spacing

Home:20dp horizontal and16dp vertical padding, content max620dp. `compact = availableHeight<680dp OR fontScale>1.35`; scene90dp compact,118dp otherwise, top12dp. Caption top4/bottom20dp. Vs Computer section top18dp, description top3dp; choices top14/gap8dp; difficulty description top8/bottom12dp; footer top16dp. At fontScale>1.35, difficulty and footer stack (footer left aligned); no body copy is scaled down to keep everything above the fold.

Game:20dp horizontal/12dp vertical padding, max500dp normal. Wide only when width>=760dp **and** fontScale<1.6:32dp horizontal, max1000dp, board and info equal columns separated28dp, info children gap20dp. Header-to-players16dp, players-to-content20dp; normal instructions-to-board20dp, board-to-bag24dp; bottom24dp. Header title starts8dp after Back. Board has no artificial height crop: a short screen scrolls.

Player header stacks when `fontScale>1.5 OR (availableWidth<320dp AND fontScale>1.2)`, preventing actor names splitting in narrow columns.

Handoff:20dp outside, max400dp, centered scrollable column,20dp item spacing. Actor tile56dp, radius14dp, actor color, numeral canvas-colored headlineMedium. Emotional headline centered and assertive; bodyLarge explanation centered secondary.

Reveal/result dialog:24dp outside, width capped400dp, FormSurface;24dp inner padding,16dp column gaps, scrollable centered content. Outside tap does not dismiss; system Back routes Home. Android platform window animations explicitly disabled. Android window dim is a platform default with no app-specified numeric alpha; use a restrained native modal barrier, not a guessed token. Underlying board semantics are cleared during modal visibility. The result miniature remains static even without Reduced Motion.

Info pages:20dp horizontal/16dp vertical, max660dp; Back plus title block start12/top4dp, optional subtitle top6dp; after header28dp; bottom24dp. At fontScale>1.35, settings descriptions move below title/switch, theme options stack, How-to odds stack. Rule row padding8dp vertical, number width34dp. Probability example top22dp, inner16dp, header/body gap4dp, odds top16dp/gap16dp, percentage top6dp; detail top12dp. Settings section gaps28dp; privacy top20dp. AI algorithm rows top16/bottom4dp, description top4dp; MCTS/Q-learning buttons top8dp, explanatory copy top8dp. Native disclosure/back navigation may adapt but must not hide explanatory copy or change destinations.

Minimum control heights are48dp back/choice/text action,52dp primary/secondary,64dp settings row,72dp mode row. Board cells are width-derived; automated narrow/wide fixtures require>=48dp. Preserve at least this effective reachability on iOS; do not lower board targets merely because a platform permits smaller controls.

## 9. Motion and presentation ownership

| Effect | Trigger/owner | Normal | Reduced Motion |
| --- | --- | --- | --- |
| Home wordmark | First eligible Home entry; shell consumed state + local progress | Three groups,620ms total as above | Immediate final, no replay |
| Screen transition | AppScreen changes; app shell | Concurrent140ms fade-in/out; Compose tween default FastOutSlowIn `(0.4,0,0.2,1)` | No enter/exit transition |
| Button contact | Local press/release |90ms cubic `(.2,.8,.2,1)`,2dp travel | Immediate shade;0 travel |
| Piece placement | Existing well empty->occupied |180ms cubic `(.2,.8,.2,1)`,3dp contact/4% scale | Immediate final |
| Target/focus/player/check/winning line | Semantic state change | Static final state, no tween | Same |
| Native Switch | Checked state | Material3 implementation, not app-parametrized | No app-specific override; verify native behavior |
| Reveal/result overlay | Coordinator stage | No Android window animation | Same static overlay |

There is no looping title, bobbing board, animated probability counter, particle system, reveal flip, moving winning line or idle glow. `FormMotion.revealMillis=160` exists but is unused; it must not be mistaken for the actual reveal hold. `AnimatedContent` also has platform size-transform behavior not parametrized by this app; the current full-screen children have no intentional resize choreography.

Essential readable holds are separate from those decorative motions:

| Stage | Normal ms | Reduced ms | Completion condition |
| --- | ---: | ---: | --- |
| TURN_START |300|160|Acknowledge this presentation ID, then draw |
| REVEALING |650|500|Acknowledge this presentation ID, then human placement or AI readiness gate |
| AI_TARGETING |280|160|Acknowledge ID, apply guarded pending decision |
| AI_PLACING |340|180|Acknowledge ID, enter settlement |
| AI_SETTLING |480|320|Acknowledge ID, next actor or terminal result |

HANDOFF waits Ready; PLAYING waits a legal move; AI_THINKING waits computation; TERMINAL waits Rematch/Home. With an early AI result, computer start through settlement takes2050ms normal/1320ms reduced, excluding the following human turn. Targeting+placement+settlement totals1100/660ms. Do not insert an extra fake thinking delay when the AI is already done.

`PresentationClock` owns a cancelable delay keyed by `(presentationId,stage,reducedMotion)` and calls the ViewModel with that ID. Decorative animation completion never advances the turn. Recreation starts the full current hold again, not the remaining elapsed fraction. A motion-preference change also restarts the current hold with its applicable duration. Revision/turn tokens reject stale AI work separately; see [state-machine.json](state-machine.json). Do not schedule a second SwiftUI timer for a modal or derive game completion from a view disappearing.

For native iOS, keep a main-actor coordinator and one injectable presentation clock, immutable observations for worker AI tasks, and cancelable task identities. Use scene-phase handling intentionally: avoid invisibly consuming unread presentation stages while backgrounded, and never replay random draws on foregrounding. This is an explicit native lifecycle design decision, not an assertion that Android currently persists elapsed animation milliseconds.

## 10. Sound and haptics

Source is `FeedbackEffects` in PicPacApp, observing `effectId`. Settings independently gate sound/haptics; both default true. Reduced Motion defaults false and does not mute either. No audio files or custom haptic waveform assets exist. Android synthesizes tones through `ToneGenerator(STREAM_MUSIC,35)` (35% generator volume) and releases it on disposal.

| Domain effect | Trigger | Android haptic | Android tone / duration | Native iOS intent |
| --- | --- | --- | --- | --- |
| REVEAL | Accepted draw | TextHandleMove | TONE_PROP_BEEP,70ms | Quiet short draw cue + light selection-like contact |
| PLACE | Accepted nonterminal move | TextHandleMove | TONE_PROP_ACK,70ms | Light physical contact + short placement cue |
| WIN | Accepted line-completing move | LongPress | TONE_PROP_BEEP2,180ms | Slightly stronger single success acknowledgement |
| DRAW | Accepted filled-board/no-line move | TextHandleMove | TONE_PROP_NACK,70ms | Neutral completion, not an error alarm |

No explicit amplitude, frequency envelope or vibration duration is authored by the app. Those Android constants are not an iOS waveform specification. Use native feedback generators/sensory feedback appropriate for the chosen deployment target, tune on physical hardware, and respect native silent-mode/audio conventions plus the two app toggles. Do not invent feedback for title groups, target outlines, focus movement or bag counters. A computer terminal effect fires when its board move commits in AI_PLACING, before the delayed result dialog; do not play a second win cue on opening the result.

Known lifecycle limit: the retained Android ViewModel keeps its last effect and effectId. Recreating the UI can cause a new `LaunchedEffect` to observe that last effect again; process-restored GameUiState does not serialize effects. Android therefore does not promise exactly-once feedback across every recreation. The iOS coordinator should model ephemeral consumable effects and not intentionally reproduce an accidental replay. Physical haptic quality and audio balance are pending human release checks, not things an emulator can approve.

## 11. Accessibility and native adaptation

The wordmark is one exact heading. Decorative Home scene, piece drawings and mode badges are hidden from the accessibility tree. Info titles and section headings are headings. Choices have selected RadioButton semantics and a visual check. Settings exposes one Switch-role row per preference, not both row and thumb. Buttons keep actual disabled state.

Board cells read “Row r, column c, empty/X/O”, one-based coordinates. AI-target descriptions are “Computer selected row r, column c”; placement/settlement reads “Computer placed X/O in row r, column c”. These cell messages and turn instructions are polite live regions. Reveal, handoff and result titles are assertive. Final board is one ordered textual summary, not nine disabled miniature cells to traverse. Modal backgrounds are absent from reading order; local handoff removes all private board content entirely.

Preserve logical top-to-bottom reading order: Back/mode, actors, turn instruction/held symbol, board row-major, bag. In wide layout, keep comprehensible grouping rather than relying solely on spatial right-column order. On iOS use native modal focus isolation and VoiceOver announcements, with deliberate deduplication so multiple live-region equivalents do not talk over one another. Announcements must identify the current actor and held symbol, never say “your turn” for the computer. Screen-reader pacing is not a reason to bypass essential turn states; evaluate the short automatic reveal with an actual user/TalkBack/VoiceOver pass.

Android automation protects heading identity, run order, centered title bounds, actual glyph/layout containment,320dp width,1.0/1.3/2.0 font scales, RTL identity, theme variants, reduced-motion final state, navigation and recreation. [BrandTypographyTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandTypographyTest.kt), [BrandLifecycleTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/BrandLifecycleTest.kt), [FormPresentationTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/FormPresentationTest.kt) and [ProductFlowTest.kt](../../app/src/androidTest/java/com/thevaguebox/probabilistictictactoe/ProductFlowTest.kt) are the native parity starting points. These tests are not human TalkBack sign-off.

Dynamic Type: retain scaling for functional labels and allow multiline/scrolling/stacked layout. Only the identity is width-fitted. Map Android thresholds to effective iOS size/layout conditions; do not assume an iOS text category numerically equals Android fontScale1.35. Verify narrow iPhone landscape, small width, largest accessibility sizes, light/dark and iPad wide layout. In iOS, honor the native Reduce Motion preference in addition to the app's explicit setting; Android source currently reads its stored setting, not a separately queried system Reduce Motion flag. This native accessibility adaptation must preserve shortened readable stages and static target evidence.

### Required iOS acceptance

- Correct TTFs/checksums/license; no font substitution in the identity or emotional headlines; no display font applied to functional copy.
- Pic/Pac/Poe use x/text/o, neutral hyphens, correct dark/light contrast, exact spelling and centered visible bounds.
- At narrow widths and maximum tested text size: all five wordmark runs visible, no clipping/ellipsis, Home controls reachable, actor names whole, dialog actions scroll into reach.
- Nine equal square board targets remain stationary and sufficiently large during every computer stage; selected coordinate is visible and spoken.
- Local handoff contains no private board semantics; reveal/result contain no interactive background; final board has one useful summary.
- Shortened Reduced Motion holds still show actor, reveal, target, committed piece and settled result; no extra movement, no repeated title on return/recreation.
- Color is never sole actor/selection/ownership signal; contrast measured against actual backgrounds; focus/disabled states verified.
- No continuous animations/timers; geometry/path/text measurement is cached where practical; no AI or heavy raster creation on the UI thread.
- Actual physical-device checks for haptics, sound, title motion and screen-reader focus/announcements remain required. Record tester/device/date; do not mark them complete based on screenshots or automated semantics alone.

## 12. Portability boundaries

Must match: rules, stage ordering and guards, bag semantics, actor identity, exact title spelling/colors/font weights, board/piece proportions, finite motion durations and grouping, disabled input, modal privacy and target evidence. May adapt carefully: native system sans metrics, safe areas, back affordance/navigation conventions, native switch behavior, speech APIs, iOS audio/haptic implementation and the platform-defined2dp-equivalent surface shadow. Changes need visual comparison and must not alter product identity.

Keep native SwiftUI shapes/paths and tokens, not Android rendering binaries. The font files are the only reusable binary visual assets in this system; reference screenshots are evidence. Do not port Compose semantics property keys, Android resource IDs, dp-to-pixel integer rounding quirks or ToneGenerator enums literally. Preserve their user-facing intent. Avoid claiming physically exact light/shadow reproduction where Android itself delegates the parameters to a platform renderer. No KMP or iOS app code is introduced by this handoff.
