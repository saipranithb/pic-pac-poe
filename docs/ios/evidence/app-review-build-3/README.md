# Build 2.0.0 (3) simulator baseline

This baseline captures the clean iOS source commit `11b0e25093173575f9d6ddbc1c16d9c9a2fce1cd`, which adds a Privacy policy link to Settings. It retains the complete seven-profile, two-appearance, 24-scenario inventory: 336 actual simulator screenshots. The historical Phase 3 baseline remains untouched.

The read-only [Phase 3 versus build 3 comparison](phase3-vs-build3-comparison.json) used the unchanged full-frame 99.5% agreement threshold and 2/255 per-channel tolerance. All 308 non-Settings frames passed. The 24 below-threshold Settings frames show the expected new link, revised footer, or resulting scroll displacement. Four initial Settings frames passed because the link falls below the viewport; their bottom-scroll counterparts show it. No region was masked.

The seven profile contact sheets and representative sheet were visually reviewed. All 28 Settings top and bottom frames were then opened at full resolution to check link readability, spacing, appearance, and large-text access. `baselines/REVIEW.json` records the exact captured manifest digest and inspection outcome. CI compares fresh captures with this separately approved package; it does not update baselines.
