# Fredoka font provenance

Pic-Pac-Poe bundles Fredoka version 2.001 by Milena Brandao and Hafontia. The
font files are **unchanged official static TrueType files**, not synthesized
weights, subsets, variable-font conversions, or runtime downloads.

## Source

Google Fonts' [family metadata](https://github.com/google/fonts/blob/main/ofl/fredoka/METADATA.pb)
identifies the upstream repository and revision. The bundled files were downloaded
from that pinned [upstream commit](https://github.com/hafontia-zz/Fredoka-One/commit/35c584ff23450c9bcdf8819706e12fcdeefe1712)
on 2026-09-20:

- [Fredoka-Medium.ttf](https://github.com/hafontia-zz/Fredoka-One/blob/35c584ff23450c9bcdf8819706e12fcdeefe1712/fonts/ttf/Fredoka-Medium.ttf)
- [Fredoka-SemiBold.ttf](https://github.com/hafontia-zz/Fredoka-One/blob/35c584ff23450c9bcdf8819706e12fcdeefe1712/fonts/ttf/Fredoka-SemiBold.ttf)
- [OFL.txt](https://github.com/hafontia-zz/Fredoka-One/blob/35c584ff23450c9bcdf8819706e12fcdeefe1712/OFL.txt)

| Android resource | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/src/main/res/font/fredoka_medium.ttf` | 45,364 | `024bec999fd21bd237b2866ec5c9189a1522db63d88fb01aabafef8c5b4d6916` |
| `app/src/main/res/font/fredoka_semibold.ttf` | 47,108 | `95839d50cc746b491c4710673be1d2cc8179a132f9600851810863922ddc12f9` |

The files' `OS/2` records declare weight 500 and 600 respectively, and normal
width (class 5). Both have static TrueType `glyf` outlines and no `fvar` table.
This avoids requiring variable-font support on Android API 24 and 25. The
internal family names are `Fredoka Medium` and `Fredoka SemiBold`; both report
version 2.001. Compose associates the resources with their explicit weights.

## Redistribution

Copyright 2016 The Fredoka Project Authors (https://github.com/hafontia/Fredoka-One).

The fonts are licensed under **SIL Open Font License 1.1**. The complete upstream
copyright and license are packaged in
`app/src/main/assets/licenses/fredoka-OFL.txt`, so they travel with the app.
The upstream license does not declare a Reserved Font Name.

The OFL allows embedding and redistribution with an application, including a
commercial application, provided the font copyright and license accompany the
fonts. Fonts cannot be sold by themselves; the font software remains under the
OFL, and the authors' names must not imply endorsement. This font license does
not relicense the application code. Keep this attribution and the packaged
license if the resources are moved or reused for another native platform.

Only the Android resource filenames were normalized to lower-case underscores;
the TTF contents were not modified. SemiBold is used for the home wordmark and
result headlines; Medium is used for reveal and local-handoff headlines.
