#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
PROJECT_PATH="$REPOSITORY_ROOT/ios/PicPacPoe.xcodeproj"
SCHEME="PicPacPoe"
CONFIGURATION="Debug"
BUNDLE_IDENTIFIER="${PIC_PAC_BUNDLE_ID:-dev.saipranith.picpacpoe}"
DEVELOPER_DIRECTORY="${XCODE_DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
XCODEBUILD="$DEVELOPER_DIRECTORY/usr/bin/xcodebuild"
RUNTIME_IDENTIFIER="com.apple.CoreSimulator.SimRuntime.iOS-26-5"
DEVICE_TYPE_IDENTIFIER="com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
DEVICE_TYPE_NAME="iPhone 17 Pro"
EXPECTED_OS_VERSION="26.5"
REFERENCE_DIRECTORY="$REPOSITORY_ROOT/docs/ios-handoff/reference"
REFERENCE_MANIFEST="$REFERENCE_DIRECTORY/screenshot-manifest.json"
GENERATOR_SOURCE="$SCRIPT_DIR/GeneratePhase2ContactSheet.swift"
OUTPUT_ROOT="${EVIDENCE_OUTPUT_ROOT:-$REPOSITORY_ROOT/docs/ios/evidence/build-phase-2/runs}"
case "$OUTPUT_ROOT" in
  /*) ;;
  *) OUTPUT_ROOT="$REPOSITORY_ROOT/$OUTPUT_ROOT" ;;
esac
OUTPUT_ROOT_RELATIVE=""
case "$OUTPUT_ROOT" in
  "$REPOSITORY_ROOT"/*) OUTPUT_ROOT_RELATIVE="${OUTPUT_ROOT#$REPOSITORY_ROOT/}" ;;
esac
RUN_IDENTIFIER="${EVIDENCE_RUN_ID:-$(/bin/date -u +%Y%m%dT%H%M%SZ)}"
SETTLE_SECONDS="${CAPTURE_SETTLE_SECONDS:-0.90}"
HOME_SETTLE_SECONDS="${HOME_CAPTURE_SETTLE_SECONDS:-0.65}"
APPEARANCE_SETTLE_SECONDS="${APPEARANCE_SETTLE_SECONDS:-0.25}"

SCENARIOS=(
  home
  human-placement
  local-handoff
  local-reveal
  computer-targeting
  computer-settled
  result
  settings
  how-to
  ai-lab
)
THEMES=(dark light)

fail() {
  echo "capture-phase2: $*" >&2
  exit 1
}

run_xcrun() {
  DEVELOPER_DIR="$DEVELOPER_DIRECTORY" /usr/bin/xcrun "$@"
}

source_status() {
  if [[ -n "$OUTPUT_ROOT_RELATIVE" ]]; then
    /usr/bin/git -C "$REPOSITORY_ROOT" status --porcelain=v1 --untracked-files=all \
      | /usr/bin/awk -v prefix="$OUTPUT_ROOT_RELATIVE/" '{ path = substr($0, 4); if (index(path, prefix) != 1) print }'
  else
    /usr/bin/git -C "$REPOSITORY_ROOT" status --porcelain=v1 --untracked-files=all
  fi
}

canonical_record() {
  local scenario="$1"
  local theme="$2"
  case "$scenario" in
    home)
      printf 'home-scene-%s-normal-412dp-x.png\texact-theme\n' "$theme"
      ;;
    human-placement)
      if [[ "$theme" == "dark" ]]; then
        printf 'human-placement-dark.png\texact-theme\n'
      else
        printf 'human-placement-dark.png\tdark-only-layout-reference\n'
      fi
      ;;
    local-handoff)
      if [[ "$theme" == "dark" ]]; then
        printf 'local-handoff-dark.png\texact-theme\n'
      else
        printf 'local-handoff-dark.png\tdark-only-layout-reference\n'
      fi
      ;;
    local-reveal)
      if [[ "$theme" == "dark" ]]; then
        printf 'local-reveal-dark.png\texact-theme\n'
      else
        printf 'local-reveal-dark.png\tdark-only-layout-reference\n'
      fi
      ;;
    computer-targeting|computer-settled|result|settings|how-to|ai-lab)
      printf '%s-%s.png\texact-theme\n' "$scenario" "$theme"
      ;;
    *) fail "no canonical mapping for scenario '$scenario'" ;;
  esac
}

png_dimensions() {
  /usr/bin/python3 - "$1" <<'PY'
import struct
import sys
path = sys.argv[1]
with open(path, "rb") as handle:
    header = handle.read(24)
if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
    raise SystemExit(f"not a valid PNG: {path}")
width, height = struct.unpack(">II", header[16:24])
if width <= 0 or height <= 0:
    raise SystemExit(f"invalid PNG dimensions: {path}")
print(f"{width}\t{height}")
PY
}

wait_for_ready() {
  local marker="$1"
  local attempt=0
  while [[ "$attempt" -lt 200 ]]; do
    [[ ! -f "$marker" ]] || return 0
    /bin/sleep 0.05
    attempt=$((attempt + 1))
  done
  fail "app did not signal screenshot readiness within 10 seconds: $marker"
}

assert_visual_content() {
  local png="$1"
  local bmp="$TEMPORARY_DIRECTORY/visual-content.bmp"
  /usr/bin/sips -s format bmp "$png" --out "$bmp" >/dev/null
  /usr/bin/python3 - "$bmp" "$png" <<'PY'
import pathlib
import struct
import sys

bmp_path, png_path = map(pathlib.Path, sys.argv[1:])
data = bmp_path.read_bytes()
if data[:2] != b"BM" or len(data) < 54:
    raise SystemExit(f"could not inspect screenshot content: {png_path}")
offset = struct.unpack_from("<I", data, 10)[0]
width = struct.unpack_from("<i", data, 18)[0]
signed_height = struct.unpack_from("<i", data, 22)[0]
bits = struct.unpack_from("<H", data, 28)[0]
height = abs(signed_height)
if width <= 0 or height <= 0 or bits not in (24, 32):
    raise SystemExit(f"unsupported screenshot conversion for content check: {png_path}")
bytes_per_pixel = bits // 8
stride = ((width * bits + 31) // 32) * 4
colors = set()
step_x = max(1, width // 80)
step_y = max(1, height // 120)
for y in range(height // 10, height * 9 // 10, step_y):
    stored_y = y if signed_height < 0 else height - 1 - y
    row = offset + stored_y * stride
    for x in range(width // 12, width * 11 // 12, step_x):
        pixel = row + x * bytes_per_pixel
        blue, green, red = data[pixel:pixel + 3]
        colors.add((red, green, blue))
        if len(colors) >= 24:
            raise SystemExit(0)
raise SystemExit(f"screenshot appears visually blank ({len(colors)} sampled colors): {png_path}")
PY
}

[[ "$(/usr/bin/uname -s)" == "Darwin" ]] || fail "this capture tool requires macOS"
[[ "$RUN_IDENTIFIER" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || fail "EVIDENCE_RUN_ID must contain only letters, numbers, dot, underscore, or hyphen"
[[ "$SETTLE_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "CAPTURE_SETTLE_SECONDS must be a nonnegative number"
[[ "$HOME_SETTLE_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "HOME_CAPTURE_SETTLE_SECONDS must be a nonnegative number"
[[ "$APPEARANCE_SETTLE_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "APPEARANCE_SETTLE_SECONDS must be a nonnegative number"
[[ -x "$XCODEBUILD" ]] || fail "Xcode was not found at $DEVELOPER_DIRECTORY; set XCODE_DEVELOPER_DIR without changing global xcode-select"
[[ -x /usr/bin/xcrun ]] || fail "/usr/bin/xcrun is unavailable"
[[ -x /usr/bin/python3 ]] || fail "/usr/bin/python3 is unavailable"
[[ -x /usr/bin/git ]] || fail "/usr/bin/git is unavailable"
[[ -f "$PROJECT_PATH/project.pbxproj" ]] || fail "missing Xcode project: $PROJECT_PATH"
[[ -f "$REFERENCE_MANIFEST" ]] || fail "missing canonical screenshot manifest: $REFERENCE_MANIFEST"
[[ -f "$GENERATOR_SOURCE" ]] || fail "missing contact-sheet generator: $GENERATOR_SOURCE"
[[ -f "$REPOSITORY_ROOT/ios/PicPacPoe/DebugLaunchConfiguration.swift" ]] || fail "missing DebugLaunchConfiguration.swift"
/usr/bin/grep -Fq -- '-screenshot-scenario' "$REPOSITORY_ROOT/ios/PicPacPoe/DebugLaunchConfiguration.swift" \
  || fail "the Debug screenshot launch contract is unavailable"
/usr/bin/grep -Fq -- '-screenshot-theme' "$REPOSITORY_ROOT/ios/PicPacPoe/DebugLaunchConfiguration.swift" \
  || fail "the Debug theme launch contract is unavailable"
for scenario in "${SCENARIOS[@]}"; do
  [[ "$scenario" == "home" ]] && continue
  /usr/bin/grep -Fq -- ""$scenario"" "$REPOSITORY_ROOT/ios/PicPacPoe/DebugLaunchConfiguration.swift" \
    || fail "DebugLaunchConfiguration does not recognize '$scenario'"
done

TEMPORARY_DIRECTORY="$(/usr/bin/mktemp -d "${TMPDIR:-/tmp}/picpac-phase2-evidence.XXXXXX")"
SIMULATOR_UDID=""
SIMULATOR_CREATED=0
RUN_DIRECTORY="$OUTPUT_ROOT/$RUN_IDENTIFIER"
CAPTURE_DIRECTORY="$RUN_DIRECTORY/captures"
PLAN_FILE="$TEMPORARY_DIRECTORY/capture-plan.tsv"
CAPTURE_ROWS_FILE="$TEMPORARY_DIRECTORY/capture-rows.tsv"
SOURCE_STATUS_FILE="$TEMPORARY_DIRECTORY/source-status.txt"
INCOMPLETE_MARKER="$RUN_DIRECTORY/INCOMPLETE"

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  if [[ -n "$SIMULATOR_UDID" ]]; then
    run_xcrun simctl terminate "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" >/dev/null 2>&1
    run_xcrun simctl status_bar "$SIMULATOR_UDID" clear >/dev/null 2>&1
    run_xcrun simctl shutdown "$SIMULATOR_UDID" >/dev/null 2>&1
    if [[ "$SIMULATOR_CREATED" -eq 1 ]]; then
      run_xcrun simctl delete "$SIMULATOR_UDID" >/dev/null 2>&1
    fi
  fi
  /bin/rm -rf "$TEMPORARY_DIRECTORY"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[[ ! -e "$RUN_DIRECTORY" ]] || fail "evidence run already exists: $RUN_DIRECTORY"
source_status > "$SOURCE_STATUS_FILE"
SOURCE_COMMIT="$(/usr/bin/git -C "$REPOSITORY_ROOT" rev-parse --verify HEAD)"
SOURCE_BRANCH="$(/usr/bin/git -C "$REPOSITORY_ROOT" symbolic-ref --quiet --short HEAD || printf 'detached')"
SOURCE_DIRTY=false
[[ ! -s "$SOURCE_STATUS_FILE" ]] || SOURCE_DIRTY=true

/bin/mkdir -p "$CAPTURE_DIRECTORY"
printf 'Capture did not complete. Inspect xcodebuild.log and rerun with a new EVIDENCE_RUN_ID.\n' > "$INCOMPLETE_MARKER"
: > "$PLAN_FILE"
: > "$CAPTURE_ROWS_FILE"
for scenario in "${SCENARIOS[@]}"; do
  for theme in "${THEMES[@]}"; do
    record="$(canonical_record "$scenario" "$theme")"
    IFS=$'\t' read -r canonical_file comparison <<< "$record"
    printf '%s\t%s\t%s\t%s\n' "$scenario" "$theme" "$canonical_file" "$comparison" >> "$PLAN_FILE"
  done
done

/usr/bin/python3 - "$PLAN_FILE" "$REFERENCE_MANIFEST" "$REPOSITORY_ROOT" <<'PY'
import csv
import hashlib
import json
import pathlib
import sys

plan_path, manifest_path, root_path = map(pathlib.Path, sys.argv[1:])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
entries = {item["path"]: item for item in manifest["screenshots"]}
seen = set()
with plan_path.open(newline="", encoding="utf-8") as handle:
    for scenario, theme, filename, comparison in csv.reader(handle, delimiter="\t"):
        key = (scenario, theme)
        if key in seen:
            raise SystemExit(f"duplicate capture plan entry: {scenario}/{theme}")
        seen.add(key)
        item = entries.get(filename)
        if item is None:
            raise SystemExit(f"canonical file is absent from screenshot-manifest.json: {filename}")
        path = root_path / "docs" / "ios-handoff" / "reference" / filename
        if not path.is_file():
            raise SystemExit(f"canonical file is missing: {path}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != item["sha256"]:
            raise SystemExit(f"canonical checksum mismatch for {filename}: {digest}")
if len(seen) != 20:
    raise SystemExit(f"expected 20 capture plan entries, found {len(seen)}")
PY

XCODE_LABEL="$($XCODEBUILD -version | /usr/bin/paste -sd ' ' -)"
SIMULATOR_SDK_VERSION="$(run_xcrun --sdk iphonesimulator --show-sdk-version)"
MACOS_VERSION="$(/usr/bin/sw_vers -productVersion)"
printf '%s\n' "$XCODE_LABEL" > "$TEMPORARY_DIRECTORY/xcode-version.txt"
printf '%s\n' "$SIMULATOR_SDK_VERSION" > "$TEMPORARY_DIRECTORY/sdk-version.txt"

run_xcrun simctl list runtimes -j > "$TEMPORARY_DIRECTORY/runtimes.json"
run_xcrun simctl list devicetypes -j > "$TEMPORARY_DIRECTORY/device-types.json"
RUNTIME_VERSION="$(/usr/bin/python3 - "$TEMPORARY_DIRECTORY/runtimes.json" "$TEMPORARY_DIRECTORY/device-types.json" "$RUNTIME_IDENTIFIER" "$DEVICE_TYPE_IDENTIFIER" <<'PY'
import json
import sys
runtimes = json.load(open(sys.argv[1], encoding="utf-8"))["runtimes"]
device_types = json.load(open(sys.argv[2], encoding="utf-8"))["devicetypes"]
runtime_id, device_type_id = sys.argv[3], sys.argv[4]
runtime = next((item for item in runtimes if item.get("identifier") == runtime_id), None)
if runtime is None or not runtime.get("isAvailable", True):
    raise SystemExit(f"required available runtime not found: {runtime_id}")
if not any(item.get("identifier") == device_type_id for item in device_types):
    raise SystemExit(f"required device type not found: {device_type_id}")
print(runtime.get("version", ""))
PY
)"
[[ "$RUNTIME_VERSION" == "$EXPECTED_OS_VERSION" ]] \
  || fail "runtime $RUNTIME_IDENTIFIER reports '$RUNTIME_VERSION', expected '$EXPECTED_OS_VERSION'"

SIMULATOR_NAME="PicPacPoe Phase 2 Evidence $RUN_IDENTIFIER"
SIMULATOR_UDID="$(run_xcrun simctl create "$SIMULATOR_NAME" "$DEVICE_TYPE_IDENTIFIER" "$RUNTIME_IDENTIFIER")"
[[ "$SIMULATOR_UDID" =~ ^[0-9A-Fa-f-]{36}$ ]] || fail "simctl returned an invalid device identifier: $SIMULATOR_UDID"
SIMULATOR_CREATED=1
run_xcrun simctl boot "$SIMULATOR_UDID"
run_xcrun simctl bootstatus "$SIMULATOR_UDID" -b
run_xcrun simctl ui "$SIMULATOR_UDID" content_size large
run_xcrun simctl status_bar "$SIMULATOR_UDID" override \
  --time '9:41' --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4

DERIVED_DATA="$TEMPORARY_DIRECTORY/DerivedData"
BUILD_LOG="$RUN_DIRECTORY/xcodebuild.log"
if ! DEVELOPER_DIR="$DEVELOPER_DIRECTORY" "$XCODEBUILD" \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath "$DERIVED_DATA" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  build > "$BUILD_LOG" 2>&1; then
  /usr/bin/tail -n 80 "$BUILD_LOG" >&2
  fail "unsigned simulator build failed; full log: $BUILD_LOG"
fi

APP_PATH="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/PicPacPoe.app"
[[ -d "$APP_PATH" ]] || fail "built app was not found at $APP_PATH"
[[ -x "$APP_PATH/PicPacPoe" ]] || fail "built app executable is missing"
BUILT_BUNDLE_IDENTIFIER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP_PATH/Info.plist")"
[[ "$BUILT_BUNDLE_IDENTIFIER" == "$BUNDLE_IDENTIFIER" ]] \
  || fail "built bundle identifier '$BUILT_BUNDLE_IDENTIFIER' does not match expected '$BUNDLE_IDENTIFIER'"
run_xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
APP_DATA_CONTAINER="$(run_xcrun simctl get_app_container "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" data)"
[[ -d "$APP_DATA_CONTAINER/tmp" ]] || fail "installed app temporary container is unavailable"

# The first process launch on a newly created simulator can spend longer in
# system-service startup than the visual settle window. Warm the installed app
# once so every recorded frame uses the same in-app timing contract.
run_xcrun simctl ui "$SIMULATOR_UDID" appearance dark
PREWARM_TOKEN="${RUN_IDENTIFIER}-prewarm"
PREWARM_MARKER="$APP_DATA_CONTAINER/tmp/picpac-screenshot-ready-$PREWARM_TOKEN"
run_xcrun simctl launch --terminate-running-process \
  "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" \
  -screenshot-scenario home \
  -screenshot-theme dark \
  -screenshot-ready-token "$PREWARM_TOKEN" \
  -AppleLanguages '(en)' \
  -AppleLocale 'en_US' >/dev/null
wait_for_ready "$PREWARM_MARKER"
/bin/sleep "$APPEARANCE_SETTLE_SECONDS"
run_xcrun simctl terminate "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" >/dev/null

for theme in "${THEMES[@]}"; do
  run_xcrun simctl ui "$SIMULATOR_UDID" appearance "$theme"
  /bin/sleep "$APPEARANCE_SETTLE_SECONDS"
  for scenario in "${SCENARIOS[@]}"; do
    plan_record="$(/usr/bin/awk -F '\t' -v wanted_scenario="$scenario" -v wanted_theme="$theme" \
      '$1 == wanted_scenario && $2 == wanted_theme { print $3 "\t" $4 }' "$PLAN_FILE")"
    [[ -n "$plan_record" ]] || fail "capture plan lookup failed for $scenario/$theme"
    IFS=$'\t' read -r canonical_file comparison <<< "$plan_record"

    capture_name="ios-${scenario}-${theme}.png"
    capture_path="$CAPTURE_DIRECTORY/$capture_name"
    settle="$SETTLE_SECONDS"
    [[ "$scenario" != "home" ]] || settle="$HOME_SETTLE_SECONDS"
    ready_token="${RUN_IDENTIFIER}-${scenario}-${theme}"
    ready_marker="$APP_DATA_CONTAINER/tmp/picpac-screenshot-ready-$ready_token"
    [[ ! -e "$ready_marker" ]] || fail "unexpected stale screenshot readiness marker: $ready_marker"

    run_xcrun simctl launch --terminate-running-process \
      "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" \
      -screenshot-scenario "$scenario" \
      -screenshot-theme "$theme" \
      -screenshot-ready-token "$ready_token" \
      -AppleLanguages '(en)' \
      -AppleLocale 'en_US' >/dev/null
    wait_for_ready "$ready_marker"
    /bin/sleep "$settle"
    run_xcrun simctl io "$SIMULATOR_UDID" screenshot --type=png "$capture_path" >/dev/null
    assert_visual_content "$capture_path"
    run_xcrun simctl terminate "$SIMULATOR_UDID" "$BUNDLE_IDENTIFIER" >/dev/null

    dimensions="$(png_dimensions "$capture_path")"
    IFS=$'\t' read -r pixel_width pixel_height <<< "$dimensions"
    sha256="$(/usr/bin/shasum -a 256 "$capture_path" | /usr/bin/awk '{ print $1 }')"
    captured_at="$(/bin/date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$scenario" "$theme" "captures/$capture_name" \
      "docs/ios-handoff/reference/$canonical_file" "$comparison" \
      "$captured_at" "$pixel_width" "$pixel_height" "$sha256" "$settle" "$ready_token" \
      >> "$CAPTURE_ROWS_FILE"
  done
done

CURRENT_SOURCE_STATUS="$TEMPORARY_DIRECTORY/current-source-status.txt"
source_status > "$CURRENT_SOURCE_STATUS"
/usr/bin/cmp -s "$SOURCE_STATUS_FILE" "$CURRENT_SOURCE_STATUS" \
  || fail "source files changed during capture; evidence was left incomplete and must be rerun"

export PIC_PAC_SOURCE_COMMIT="$SOURCE_COMMIT"
export PIC_PAC_SOURCE_BRANCH="$SOURCE_BRANCH"
export PIC_PAC_SOURCE_DIRTY="$SOURCE_DIRTY"
export PIC_PAC_SIMULATOR_UDID="$SIMULATOR_UDID"
export PIC_PAC_SIMULATOR_NAME="$SIMULATOR_NAME"
export PIC_PAC_DEVICE_TYPE_ID="$DEVICE_TYPE_IDENTIFIER"
export PIC_PAC_DEVICE_TYPE_NAME="$DEVICE_TYPE_NAME"
export PIC_PAC_RUNTIME_ID="$RUNTIME_IDENTIFIER"
export PIC_PAC_RUNTIME_VERSION="$RUNTIME_VERSION"
export PIC_PAC_MACOS_VERSION="$MACOS_VERSION"
export PIC_PAC_XCODE_LABEL="$XCODE_LABEL"
export PIC_PAC_SIMULATOR_SDK="$SIMULATOR_SDK_VERSION"
export PIC_PAC_BUNDLE_ID="$BUNDLE_IDENTIFIER"
export PIC_PAC_DEVELOPER_DIRECTORY="$DEVELOPER_DIRECTORY"
export PIC_PAC_RUN_ID="$RUN_IDENTIFIER"
export PIC_PAC_GENERATED_AT="$(/bin/date -u +%Y-%m-%dT%H:%M:%SZ)"

MANIFEST_PATH="$RUN_DIRECTORY/manifest.json"
/usr/bin/python3 - "$CAPTURE_ROWS_FILE" "$REFERENCE_MANIFEST" "$SOURCE_STATUS_FILE" "$MANIFEST_PATH" <<'PY'
import csv
import json
import os
import pathlib
import sys

rows_path, references_path, status_path, output_path = map(pathlib.Path, sys.argv[1:])
references = json.loads(references_path.read_text(encoding="utf-8"))
reference_by_path = {item["path"]: item for item in references["screenshots"]}
status = status_path.read_text(encoding="utf-8").splitlines()
captures = []
with rows_path.open(newline="", encoding="utf-8") as handle:
    for row in csv.reader(handle, delimiter="\t"):
        scenario, theme, relative_path, canonical_path, comparison, captured_at, width, height, sha256, settle, ready_token = row
        canonical_name = pathlib.Path(canonical_path).name
        reference = reference_by_path[canonical_name]
        source_block = references["homeSceneCapture"] if canonical_name.startswith("home-scene-") else references["capture"]
        captures.append({
            "scenario": scenario,
            "theme": theme,
            "relativePath": relative_path,
            "capturedAt": captured_at,
            "sha256": sha256,
            "width": int(width),
            "height": int(height),
            "settleSeconds": float(settle),
            "launchArguments": [
                "-screenshot-scenario", scenario,
                "-screenshot-theme", theme,
                "-screenshot-ready-token", ready_token,
                "-AppleLanguages", "(en)",
                "-AppleLocale", "en_US",
            ],
            "settings": {
                "soundEnabled": scenario == "settings" and theme == "dark",
                "hapticsEnabled": scenario == "settings" and theme == "dark",
                "reducedMotion": scenario == "settings" and theme == "light",
                "theme": theme,
            },
            "canonical": {
                "relativePath": canonical_path,
                "theme": reference["theme"],
                "kind": reference["kind"],
                "sha256": reference["sha256"],
                "comparison": comparison,
                "note": reference.get("note", ""),
                "productionCommit": source_block.get("productionCommit", ""),
            },
        })

manifest = {
    "schemaVersion": 1,
    "runID": os.environ["PIC_PAC_RUN_ID"],
    "generatedAt": os.environ["PIC_PAC_GENERATED_AT"],
    "provenance": "actual-debug-simulator-fixture",
    "source": {
        "commit": os.environ["PIC_PAC_SOURCE_COMMIT"],
        "branch": os.environ["PIC_PAC_SOURCE_BRANCH"],
        "dirty": os.environ["PIC_PAC_SOURCE_DIRTY"] == "true",
        "statusAtCaptureStart": status,
        "stableDuringCapture": True,
    },
    "toolchain": {
        "developerDirectory": os.environ["PIC_PAC_DEVELOPER_DIRECTORY"],
        "xcode": os.environ["PIC_PAC_XCODE_LABEL"],
        "simulatorSDK": os.environ["PIC_PAC_SIMULATOR_SDK"],
        "macOS": os.environ["PIC_PAC_MACOS_VERSION"],
        "globalXcodeSelectChanged": False,
    },
    "simulator": {
        "name": os.environ["PIC_PAC_SIMULATOR_NAME"],
        "udid": os.environ["PIC_PAC_SIMULATOR_UDID"],
        "deviceType": os.environ["PIC_PAC_DEVICE_TYPE_NAME"],
        "deviceTypeIdentifier": os.environ["PIC_PAC_DEVICE_TYPE_ID"],
        "runtime": os.environ["PIC_PAC_RUNTIME_ID"],
        "osVersion": os.environ["PIC_PAC_RUNTIME_VERSION"],
        "ephemeral": True,
        "appearance": ["dark", "light"],
        "contentSize": "large",
        "locale": "en_US",
        "statusBar": {"time": "9:41", "batteryLevel": 100, "batteryState": "charged"},
        "applicationPrewarmed": True,
    },
    "application": {
        "bundleIdentifier": os.environ["PIC_PAC_BUNDLE_ID"],
        "scheme": "PicPacPoe",
        "configuration": "Debug",
        "codeSigningAllowed": False,
        "defaultFixtureSettings": {
            "soundEnabled": False,
            "hapticsEnabled": False,
            "reducedMotion": False,
        },
    },
    "canonicalReferenceManifest": "docs/ios-handoff/reference/screenshot-manifest.json",
    "captures": captures,
}
output_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

CONTACT_GENERATOR="$TEMPORARY_DIRECTORY/GeneratePhase2ContactSheet"
run_xcrun swiftc "$GENERATOR_SOURCE" -framework AppKit \
  -module-cache-path "$TEMPORARY_DIRECTORY/SwiftModuleCache" \
  -o "$CONTACT_GENERATOR"
CONTACT_SHEET="$RUN_DIRECTORY/review-contact-sheet.png"
"$CONTACT_GENERATOR" \
  --manifest "$MANIFEST_PATH" \
  --repository-root "$REPOSITORY_ROOT" \
  --output "$CONTACT_SHEET"
png_dimensions "$CONTACT_SHEET" >/dev/null

/usr/bin/python3 - "$MANIFEST_PATH" "$CONTACT_SHEET" <<'PY'
import hashlib
import json
import pathlib
import struct
import sys
manifest_path, sheet_path = map(pathlib.Path, sys.argv[1:])
data = sheet_path.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
    raise SystemExit("generated contact sheet is not a valid PNG")
width, height = struct.unpack(">II", data[16:24])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
manifest["derived"] = {
    "relativePath": sheet_path.name,
    "sha256": hashlib.sha256(data).hexdigest(),
    "width": width,
    "height": height,
    "kind": "canonical-versus-ios-review-sheet",
    "generator": "ios/Scripts/GeneratePhase2ContactSheet.swift",
    "cropping": False,
}
manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

CHECKSUMS="$RUN_DIRECTORY/checksums.sha256"
(
  cd "$RUN_DIRECTORY"
  for file in captures/*.png manifest.json review-contact-sheet.png xcodebuild.log; do
    /usr/bin/shasum -a 256 "$file"
  done
) > "$CHECKSUMS"

/bin/rm -f "$INCOMPLETE_MARKER"
echo "Phase 2 screenshot evidence complete:"
echo "  $RUN_DIRECTORY"
echo "  20 simulator captures"
echo "  $CONTACT_SHEET"
echo "  $MANIFEST_PATH"
