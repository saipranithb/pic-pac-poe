# Snapshot governance

`ios/Scripts/snapshot_phase3.py` captures the actual unsigned Debug app on disposable iOS 26.5 simulators using Xcode 26.6 (17F113). The default plan is 24 presentation/scroll scenarios × seven device, Dynamic Type and Reduce Motion profiles × two themes: 336 complete screen captures. Every capture waits for a unique post-restoration readiness marker, then 1,250 ms of settling. Fresh simulators receive a 45-second Home prewarm so the first-boot system banner can finish without cropping or masking it.

The capture manifest records the source commit, branch and working state; iOS source and complete build-input fingerprints; exact external Fredoka font/OFL inputs; the actual app executable hash; the canonical Android reference manifest and per-image hashes; device, runtime, appearance, content size, motion setting and launch arguments; and full-frame dimensions, opacity, central image diversity and image checksums. Build inputs and canonical references must remain unchanged throughout capture. The settings light fixture deliberately has Reduce Motion selected, including its bottom-scroll view, and records that effective setting.

The image comparator decodes into sRGB RGBA and compares every pixel, including system safe areas and alpha. A pixel agrees only when every channel differs by at most 2/255. At least 99.5% of pixels must agree. No region is masked and no baseline image is replaced by capture or comparison. Cross-platform Android-to-iOS images remain a semantic geometry and intent comparison, rather than a same-renderer pixel test.

Comparison rejects incomplete packages, missing/duplicate captures, unknown profiles, missing themes, configuration drift, cropped or nonopaque frames, changed canonical references and incomplete or invalid checksum inventories. The baseline and candidate must have the same complete capture plan, runtime, toolchain and per-capture configuration. A failed pixel gate requires inspection and an ordinary product fix or an explicitly reviewed intentional baseline change; changing a tolerance is not a substitute.

## Approving an inspected baseline

After independently inspecting every full-resolution image and its contact sheets, write `REVIEW.json` inside the proposed baseline package with these fields:

```json
{
  "schemaVersion": 1,
  "approved": true,
  "manifestSHA256": "the SHA-256 of this exact manifest.json",
  "captureCount": 336,
  "reviewer": "the reviewer identity",
  "reviewedAt": "an ISO-8601 timestamp",
  "notes": "Specific inspection outcome, intentional changes, and before/after evidence for material fixes."
}
```

Then regenerate that package's `checksums.sha256`, including `REVIEW.json`. The comparison command requires this approval by default and verifies the exact manifest digest and capture count. The helper's `checksums(path)` function only calculates checksums; it never creates an approval. Review is a deliberate human/agent inspection record, not a conclusion inferred from a passing threshold.

Repeated same-toolchain calibration before approval may use `compare --allow-unreviewed`. Such a report is marked `unreviewed calibration only` with `baselineReviewed: false`; the option is rejected under `GITHUB_ACTIONS=true`. It never approves or modifies images. CI uses the default review requirement.

## Harness validation

A lightweight local rejection-path exercise accepted a complete two-theme metadata fixture and rejected duplicate or missing captures, a cropped declaration, unstable source, wrong dimensions, nonopaque frame, canonical hash drift, duplicate profile, motion-setting drift, an `INCOMPLETE` sentinel, an unchecksummed extra file, a missing approval and a stale approval digest. This exercise checked package governance only; it did not claim pixel calibration or approve any product image. Final simulator captures and repeated-capture measurements are separately retained in this phase's evidence package.
