# Preserved earlier runs

These logs are historical diagnosis, not accepted final gates. The original /tmp logs and xcresults remain untouched.

- `phase3-hosted-performance.log`: interrupted before a complete test result. Its result bundle could not finish recording.
- `phase3-hosted-build-final.log`: successful earlier Test build.
- `phase3-hosted-performance-final.log`: 12 executed tests with eight failure assertions across two methods. The speech test constructed a domain-invalid turn token22 instead of18. The UIKit-created SwiftUI host omitted the active scene environment, so its Home stayed inactive and its game stayed at turnStart. Those test-harness defects were corrected in checkpoint2a04a485. The actual app scene and live gameplay had separately passed XCUITest.
- The search-only method in that failed suite passed, but the suite and its Home/cadence payload are rejected as final acceptance evidence.

The sibling accepted recovery run has12passed/0failures/0skips, active X/O frames, an exact live Hard stage sequence, and pixel-identical inactive frames.
