#!/bin/bash
set -euo pipefail
# Caller chooses an existing Xcode per process. Never switch the global selection
# or download a runtime to make a verification lane appear green.
fail() { printf 'Apple environment gate: %s\n' "$*" >&2; exit 1; }
[[ "$(sw_vers -productVersion)" == 26.* ]] || fail 'macOS 26 is required'
[[ "$(uname -m)" == arm64 ]] || fail 'arm64 is required for the approved snapshots'
version="$(xcodebuild -version)" || fail 'configured Xcode is unavailable; no toolchain is installed by this gate'
[[ "$(printf '%s\n' "$version" | sed -n '1p')" == 'Xcode 26.6' ]] || fail 'Xcode 26.6 is required'
[[ "$(printf '%s\n' "$version" | sed -n '2p')" == 'Build version 17F113' ]] || fail 'Xcode build 17F113 is required'
swift --version | grep -F 'Apple Swift version 6.3.3' || fail 'Swift 6.3.3 is required'
[[ "$(xcrun --sdk iphonesimulator --show-sdk-version)" == 26.5 ]] || fail 'iOS Simulator SDK 26.5 is required'
xcrun simctl list runtimes -j | python3 -c 'import json,sys; rs=json.load(sys.stdin)["runtimes"]; assert any(r["identifier"] == "com.apple.CoreSimulator.SimRuntime.iOS-26-5" and r.get("isAvailable") for r in rs), "Required installed iOS 26.5 runtime unavailable; do not silently substitute"'
printf '%s\n' 'PASS: Xcode 26.6 (17F113), Swift 6.3.3, arm64, installed iOS 26.5 runtime'
