# Focused large-text layout verification

PASS for the two new regressions: 2 tests, 0 failures, 0 skips, 0 expected failures. This diagnostic run used recorded coherent dirty source; it does not replace the final clean-source full suite or governed baseline review.

The geometry test checks 24 held stage/layout combinations (six human/computer stages × normal/AX3 × top/bottom), comparing all nine native cell rectangles within 0.5pt and preserving minimum size, square shape, locked input and complete top instruction bounds. The tutorial test verifies all four combined step labels and actual scroll reachability at AX3 and AX5.

All 32 original timestamped XCTest screenshots are retained, uncropped. All eight tutorial originals were opened at full resolution: numbered markers and periods now remain together on one line, with naturally wrapping body text. Six AX3 top-stage and four AX3 bottom-stage originals were also opened: stage geometry is stable, the selected square remains aligned when the piece is placed, and the tray copy remains complete. Space reserved for changing instruction copy is intentional. Bottom geometry fixtures explicitly use the bottom anchor; the AX5 tutorial uses a real test-driven swipe.

`manifest.json` records exact source and binary hashes, device and counts; `source-before.json` and `source-after.json` preserve provenance. The original xcresult remains at the exact /tmp path in the manifest, with a full file checksum inventory. Native XCTest exports are retained in portable form. The task-owned UI simulator was shut down successfully without deleting data.

No physical-device, iOS 17 runtime, normal-speed pacing or hosted CI claim is made here. The final clean-source 26-method suite and regenerated snapshot baselines remain separate required gates.
