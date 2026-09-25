Final four unsigned local build gates

PASS on clean e6c0c65c9c5ad84e2f961839c5eede753c25448e: Debug and Release for iOS Simulator and iOS device SDK. Signing and LLVM coverage were explicitly disabled. Release checks validate actual Mach-O minimum iOS 17.0 in every slice, no LLVM profile/coverage sections or instrumented compiler commands, no Debug/testing strings, bundle identity, launch screen, icons, fonts, policy and license resources. Exact commands, wall times, full app file/bundle hashes and source/toolchain provenance are in manifest.json.

The initial sandboxed attempt failed before compilation because required compiler caches and simulator services were inaccessible; its unmodified evidence is retained separately under failed-sandbox-attempt. It is not counted as an accepted build or product defect. The successful build commands used sandbox escalation and fresh outputs.

These are locally reproduced workflow gates. GitHub-hosted Actions were not run. Device-SDK compilation does not establish physical-device execution; iOS 17 compile targeting does not establish iOS 17 runtime execution. No signing, upload, push, PR or store interaction occurred.
