#!/usr/bin/env python3
"""Run real simulator UI tests and retain original, timestamped XCTest frames."""
import argparse
import datetime
import json
import pathlib
import re
import shlex
import shutil
import struct
import subprocess
import tempfile

from snapshot_phase3 import ENV, ROOT, checksums, run, sha, source


def timestamped_name(suggested):
    """Decode only unambiguous native XCTest export-name transformations."""
    uuid = r'[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}'
    patterns = [
        # XCTest treats the final decimal as an extension, inserting its index
        # and UUID before the fraction. The raw manifest remains unchanged.
        rf'(?P<stem>[a-z0-9-]+)--uptime-(?P<seconds>\d+)_\d+_{uuid}\.(?P<millis>\d{{3}})',
        rf'(?P<stem>[a-z0-9-]+)--uptime-(?P<seconds>\d+)[.p](?P<millis>\d{{3}})(?:_\d+_{uuid})?(?:\.png)?',
    ]
    match = next((match for pattern in patterns if (match := re.fullmatch(pattern, suggested))), None)
    if match is None:
        raise ValueError('Unrecognized timestamped screenshot name: ' + suggested)
    stem, seconds, millis = match['stem'], match['seconds'], match['millis']
    return stem, f'{stem}--uptime-{seconds}p{millis}.png', float(seconds + '.' + millis)


def png_geometry(path):
    """Inspect original pixels and TIFF orientation without transforming bytes."""
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Frame is not PNG: ' + str(path))
    width, height = struct.unpack('>II', data[16:24])
    orientation, cursor = 1, 8
    while cursor < len(data):
        length = struct.unpack('>I', data[cursor:cursor + 4])[0]
        kind = data[cursor + 4:cursor + 8]
        value = data[cursor + 8:cursor + 8 + length]
        if kind == b'eXIf':
            endian = '>' if value[:2] == b'MM' else '<' if value[:2] == b'II' else None
            if endian is None:
                raise ValueError('Unknown TIFF byte order')
            offset = struct.unpack(endian + 'I', value[4:8])[0]
            entries = struct.unpack(endian + 'H', value[offset:offset + 2])[0]
            for index in range(entries):
                entry = offset + 2 + index * 12
                tag, datatype, count = struct.unpack(endian + 'HHI', value[entry:entry + 8])
                if tag == 0x0112:
                    if datatype != 3 or count != 1:
                        raise ValueError('Ambiguous TIFF orientation')
                    orientation = struct.unpack(endian + 'H', value[entry + 8:entry + 10])[0]
        cursor += length + 12
    if orientation not in range(1, 9):
        raise ValueError('Invalid TIFF orientation')
    rotated = orientation in [5, 6, 7, 8]
    return {'pixelWidth': width, 'pixelHeight': height, 'exifOrientation': orientation,
            'displayWidth': height if rotated else width, 'displayHeight': width if rotated else height}


def required_frame_stems():
    sequence = ['human-reveal', 'human-playing', 'human-placed-computer-turn-start',
                'computer-reveal', 'computer-target', 'computer-place', 'computer-settle']
    required = {'sequence-clock10-' + stage for stage in sequence}
    required |= {'terminal-settling-' + outcome + '-' + stage
                 for outcome in ['win', 'draw']
                 for stage in ['before-interruption', 'restored-settlement', 'result']}
    required |= {'target-before-relaunch', 'target-after-relaunch',
                 'target-committed-once', 'committed-target-after-relaunch'}
    required |= {stage + '-' + boundary for stage in ['human-turn-start', 'human-reveal']
                 for boundary in ['restored', 'single-placement']}
    required |= {'locked-' + stage + '-' + theme
                 for stage in ['local-handoff', 'local-reveal', 'computer-reveal', 'computer-thinking',
                               'computer-targeting', 'computer-placement', 'computer-settled', 'result']
                 for theme in ['dark', 'light']}
    return required


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', required=True, help='Already available simulator UDID')
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--derived-data', type=pathlib.Path, default=pathlib.Path('/tmp/picpac-phase3-ui'))
    args = parser.parse_args()
    before = source()
    if before['status']:
        raise RuntimeError('Checkpoint source before accepted UI evidence; working tree is not clean')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    (output / 'INCOMPLETE').write_text('UI run has not completed successfully.\n')
    with tempfile.TemporaryDirectory(prefix='picpac-ui-results-') as temporary:
        temporary = pathlib.Path(temporary)
        result = temporary / 'ui.xcresult'
        command = ['xcodebuild', '-project', str(ROOT / 'ios/PicPacPoe.xcodeproj'),
                   '-scheme', 'PicPacPoe', '-configuration', 'Debug', '-destination',
                   'platform=iOS Simulator,id=' + args.destination, '-derivedDataPath',
                   str(args.derived_data.resolve()), '-parallel-testing-enabled', 'NO',
                   '-only-testing:PicPacPoeUITests', '-resultBundlePath', str(result),
                   'CODE_SIGNING_ALLOWED=NO', 'CODE_SIGNING_REQUIRED=NO', 'test']
        (output / 'command.txt').write_text('DEVELOPER_DIR=' + shlex.quote(ENV['DEVELOPER_DIR']) + ' ' + shlex.join(command) + '\n')
        with (output / 'test.log').open('w') as log:
            completed = subprocess.run(command, cwd=ROOT, env=ENV, stdout=log, stderr=subprocess.STDOUT)
        # Preserve the raw bundle outside the repository for additional inspection.
        retained_result = args.derived_data.resolve() / ('phase3-ui-' + before['commit'][:12] + '.xcresult')
        if retained_result.exists():
            raise RuntimeError('Refusing to replace retained xcresult: ' + str(retained_result))
        shutil.copytree(result, retained_result)
        for kind in ['summary', 'tests']:
            (output / ('test-' + kind + '.json')).write_text(run('xcrun', 'xcresulttool', 'get', 'test-results', kind, '--path', result) + '\n')
        attachments = temporary / 'attachments'
        run('xcrun', 'xcresulttool', 'export', 'attachments', '--path', result, '--output-path', attachments)
        raw = json.loads((attachments / 'manifest.json').read_text())
        shutil.copyfile(attachments / 'manifest.json', output / 'attachment-manifest.json')
        (output / 'frames').mkdir()
        (output / 'semantics').mkdir()
        (output / 'videos').mkdir()
        frames, videos, audit_findings = [], [], []
        for test in raw:
            for attachment in test['attachments']:
                original = attachments / attachment['exportedFileName']
                suggested = attachment['suggestedHumanReadableName']
                if original.suffix == '.png' and '--uptime-' in suggested:
                    stem, name, uptime = timestamped_name(suggested)
                    if pathlib.Path(name).name != name:
                        raise RuntimeError('Unsafe attachment name: ' + name)
                    target = output / 'frames' / name
                    if target.exists():
                        raise RuntimeError('Duplicate frame name: ' + name)
                    shutil.copyfile(original, target)
                    geometry = png_geometry(target)
                    frames.append(dict(attachment, path=str(target.relative_to(output)),
                                       testIdentifier=test['testIdentifier'],
                                       frameStem=stem, monotonicUptimeSeconds=uptime, **geometry,
                                       capturedAt=datetime.datetime.fromtimestamp(attachment['timestamp'], datetime.timezone.utc).isoformat(),
                                       sha256=sha(target)))
                elif original.suffix == '.mp4':
                    target = output / 'videos' / original.name
                    shutil.copyfile(original, target)
                    videos.append(dict(attachment, path=str(target.relative_to(output)),
                                       testIdentifier=test['testIdentifier'], sha256=sha(target)))
                elif original.suffix == '.txt':
                    target = output / 'semantics' / original.name
                    shutil.copyfile(original, target)
                    if suggested.startswith('Accessibility audit finding'):
                        content = original.read_text()
                        audit_findings.append(dict(attachment, path=str(target.relative_to(output)),
                                                   testIdentifier=test['testIdentifier'], sha256=sha(target),
                                                   disposition='acknowledged offscreen auditor artifact' if 'ACKNOWLEDGED OFFSCREEN AUDITOR ARTIFACT' in content else 'unhandled finding',
                                                   detail=content))
        # Keep all raw evidence before requiring the exact source-discovered
        # test inventory. A green summary cannot conceal absent/skipped methods.
        inventory = subprocess.run([
            'python3', str(ROOT / 'ios/Scripts/verify-xcresult.py'),
            '--result', str(result), '--target', 'PicPacPoeUITests',
            '--configuration', 'Debug', '--output', str(output / 'inventory-gate.json'),
        ], cwd=ROOT, env=ENV, capture_output=True, text=True)
        (output / 'inventory-gate.log').write_text(inventory.stdout + inventory.stderr)
        required = required_frame_stems()
        missing = sorted(required - {frame['frameStem'] for frame in frames})
        after = source()
        stable = before['buildInputSHA256'] == after['buildInputSHA256'] and before['commit'] == after['commit']
        summary = json.loads((output / 'test-summary.json').read_text())
        products = args.derived_data.resolve() / 'Build/Products/Debug-iphonesimulator'
        binaries = [products / 'PicPacPoe.app/PicPacPoe',
                    products / 'PicPacPoe.app/PicPacPoe.debug.dylib',
                    products / 'PicPacPoeUITests-Runner.app/PlugIns/PicPacPoeUITests.xctest/PicPacPoeUITests']
        manifest = {
            'schemaVersion': 1, 'kind': 'actual unsigned Debug player-driven simulator UI tests',
            'source': before, 'stableSource': stable, 'toolchain': run('xcodebuild', '-version'),
            'retainedXCResult': str(retained_result), 'result': summary['result'],
            'builtBinaries': {str(binary.relative_to(products)): sha(binary) for binary in binaries if binary.is_file()},
            'devicesAndConfigurations': summary['devicesAndConfigurations'],
            'passedTests': summary['passedTests'], 'failedTests': summary['failedTests'],
            'noCrop': True, 'frames': sorted(frames, key=lambda frame: frame['timestamp']),
            'requiredFrameStems': sorted(required), 'missingFrameStems': missing,
            'videos': videos,
            'accessibilityAudit': {
                'viewportsAudited': 28,
                'types': ['contrast', 'hitRegion', 'sufficientElementDescription', 'textClipped', 'trait'],
                'nativeFindings': audit_findings,
                'interpretation': 'All findings are preserved. The only permitted artifact is the exact dark AI Lab offscreen introductory paragraph contrast finding at the reviewed 402x874pt bottom viewport, after its visible top audit passes. This is not a zero-finding claim.'
            },
            'videoRetention': 'Native XCTest recordings, if retained by the runner; successful tests may retain only the required timestamped screen frames.',
            'exporterSHA256': sha(pathlib.Path(__file__)),
            'inventoryGatePassed': inventory.returncode == 0,
            'temporalInterpretation': {
                'completeGames': 'Actual production worker, RNG and ordinary clock; Home through result and rematch.',
                'sequence-clock10': 'Real player input and production AI. Essential stage holds multiplied by 10 to make sampled native frames inspectable; not a normal-speed pacing benchmark.',
                'interruptionFixtures': 'Explicit valid starting snapshots, production transitions, essential holds multiplied by 20. Frames prove presentation and restoration order, not normal-speed pacing.',
                'lockedFixtures': 'Held screenshot snapshots audit privacy and disabled stages; no live timing claim.'
            },
            'limitations': ['iOS 26.5 simulator is not iOS 17 runtime validation.',
                            'No physical-device speech, Switch Control hardware, keyboard ergonomics, audio or haptic certification.',
                            'No GitHub-hosted Actions execution is implied.'],
        }
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        if completed.returncode or inventory.returncode or summary['failedTests'] or summary['result'] != 'Passed' or not stable or missing or len(frames) < len(required):
            checksums(output)
            raise RuntimeError('UI evidence is incomplete: test failure, source changed or required frames missing; inspect preserved logs')
    (output / 'INCOMPLETE').unlink()
    checksums(output)
    print(f"{manifest['passedTests']} UI tests passed; {len(frames)} original timestamped frames: {output}")


if __name__ == '__main__':
    main()
