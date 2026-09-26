#!/usr/bin/env python3
"""Verify an unsigned Release simulator or device-SDK artifact; never changes the artifact."""
import argparse
import hashlib
import os
import pathlib
import plistlib
import re
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('app', type=pathlib.Path)
parser.add_argument('--platform', choices=['iphonesimulator', 'iphoneos'], default='iphonesimulator')
args = parser.parse_args()
app = args.app
errors = []
with (app / 'Info.plist').open('rb') as stream:
    info = plistlib.load(stream)
for key, expected in {
    'CFBundleIdentifier': 'dev.saipranith.picpacpoe',
    'CFBundleShortVersionString': '2.0.0',
    'CFBundleVersion': '3',
    'MinimumOSVersion': '17.0',
    'UIDeviceFamily': [1, 2],
    'ITSAppUsesNonExemptEncryption': False,
}.items():
    if info.get(key) != expected:
        errors.append(f'{key}: expected {expected!r}, got {info.get(key)!r}')
expected_platform = {'iphonesimulator': 'iPhoneSimulator', 'iphoneos': 'iPhoneOS'}[args.platform]
if info.get('CFBundleSupportedPlatforms') != [expected_platform]:
    errors.append(f'expected a {expected_platform} artifact')
for key in ('CFBundleIcons', 'CFBundleIcons~ipad'):
    primary = info.get(key, {}).get('CFBundlePrimaryIcon', {})
    if primary.get('CFBundleIconName') != 'AppIcon' or not primary.get('CFBundleIconFiles'):
        errors.append(f'{key}: missing compiled native AppIcon')
if not (app / 'Assets.car').is_file():
    errors.append('compiled asset catalog is missing')
if info.get('UILaunchScreen') != {'UIColorName': 'LaunchBackground'}:
    errors.append('semantic launch background is missing')
manifest_path = app / 'PrivacyInfo.xcprivacy'
if not manifest_path.is_file():
    errors.append('app privacy manifest is missing')
else:
    with manifest_path.open('rb') as stream:
        manifest = plistlib.load(stream)
    if manifest != {'NSPrivacyTracking': False}:
        errors.append(f'app privacy manifest differs from audited declaration: {manifest!r}')
root = pathlib.Path(__file__).resolve().parents[2]
for name, source in {
    'fredoka_medium.ttf': 'app/src/main/res/font/fredoka_medium.ttf',
    'fredoka_semibold.ttf': 'app/src/main/res/font/fredoka_semibold.ttf',
    'fredoka-OFL.txt': 'app/src/main/assets/licenses/fredoka-OFL.txt',
}.items():
    if not (app / name).is_file() or (app / name).read_bytes() != (root / source).read_bytes():
        errors.append(f'canonical bundled resource missing or changed: {name}')
policies = list(app.rglob('picpac_rl_policy_v1.bin'))
if len(policies) != 1 or hashlib.sha256(policies[0].read_bytes()).hexdigest() != '9b05cc725ab8b52cecb940b6c823cb66e843acf462511c87d2ab3e1c834152b1':
    errors.append('canonical Q-learning policy must be bundled exactly once, byte-identically')
if list(app.rglob('golden-fixtures.json')):
    errors.append('Release app embeds test-only golden fixtures')
for forbidden in ('_CodeSignature', 'embedded.mobileprovision'):
    if (app / forbidden).exists():
        errors.append(f'unsigned artifact contains {forbidden}')
if list(app.rglob('*.xctest')):
    errors.append('Release app embeds a test bundle')
executable = app / info['CFBundleExecutable']
binary = executable.read_bytes()
# Check the executable load commands as well as the declarative plist. This is
# compile-time deployment evidence, not a substitute for an iOS 17 runtime run.
build_info = subprocess.run(
    ['xcrun', 'vtool', '-show-build', str(executable)],
    check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    env=dict(os.environ, DEVELOPER_DIR=os.environ.get('XCODE_DEVELOPER_DIR', os.environ.get('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer'))),
).stdout
platforms = re.findall(r'^\s*platform\s+(\S+)', build_info, re.MULTILINE)
minimums = re.findall(r'^\s*minos\s+(\S+)', build_info, re.MULTILINE)
expected_macho = {'iphonesimulator': 'IOSSIMULATOR', 'iphoneos': 'IOS'}[args.platform]
if not platforms or any(value != expected_macho for value in platforms):
    errors.append(f'Mach-O platform mismatch: {platforms}')
if len(minimums) != len(platforms) or any(value != '17.0' for value in minimums):
    errors.append(f'Mach-O deployment target mismatch: {minimums}')
# Instrumented code can exist without DEBUG launch switches. Inspect every
# Mach-O slice's actual sections, including statically linked package code.
sections = subprocess.run(
    ['xcrun', 'llvm-objdump', '--section-headers', str(executable)],
    check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    env=dict(os.environ, DEVELOPER_DIR=os.environ.get('XCODE_DEVELOPER_DIR', os.environ.get('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer'))),
).stdout
if re.search(r'__llvm_(?:prf|cov)', sections):
    errors.append('Release binary contains LLVM coverage/profile instrumentation')
# User-facing identifiers are legitimate in Release. Only development launch
# switches/fixture and diagnostic type symbols are forbidden here.
for marker in (
    b'-screenshot-', b'-snapshot-', b'-ui-test-', b'-ui-testing', b'DebugLaunchConfiguration',
):
    if marker in binary:
        errors.append(f'Release binary contains development hook {marker!r}')
if errors:
    print('\n'.join('ERROR: ' + message for message in errors), file=sys.stderr)
    raise SystemExit(1)
print(f'PASS: unsigned Release {args.platform} artifact; Mach-O iOS 17 minimum; iPhone+iPad; native icon/launch assets and canonical resources; no development launch hooks or LLVM coverage instrumentation; compile-time compatibility only')
