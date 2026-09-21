#!/usr/bin/env python3
"""Run real simulator UI tests and retain original, timestamped XCTest frames."""
import argparse
import datetime
import json
import pathlib
import re
import shlex
import shutil
import subprocess
import tempfile

from snapshot_phase3 import ENV, ROOT, checksums, run, sha, source


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
        frames = []
        for test in raw:
            for attachment in test['attachments']:
                original = attachments / attachment['exportedFileName']
                suggested = attachment['suggestedHumanReadableName']
                uptime = re.search(r'--uptime-(\d+\.\d+)', suggested)
                if original.suffix == '.png' and uptime:
                    name = suggested[:uptime.end()] + '.png'
                    if pathlib.Path(name).name != name:
                        raise RuntimeError('Unsafe attachment name: ' + name)
                    target = output / 'frames' / name
                    if target.exists():
                        raise RuntimeError('Duplicate frame name: ' + name)
                    shutil.copyfile(original, target)
                    frames.append(dict(attachment, path=str(target.relative_to(output)),
                                       testIdentifier=test['testIdentifier'],
                                       monotonicUptimeSeconds=float(uptime.group(1)),
                                       capturedAt=datetime.datetime.fromtimestamp(attachment['timestamp'], datetime.timezone.utc).isoformat(),
                                       sha256=sha(target)))
                elif original.suffix == '.txt':
                    shutil.copyfile(original, output / 'semantics' / original.name)
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
        if completed.returncode or summary['failedTests'] or summary['result'] != 'Passed' or not stable:
            checksums(output)
            raise RuntimeError('UI evidence is incomplete: test failure or source changed; inspect preserved logs')
    (output / 'INCOMPLETE').unlink()
    checksums(output)
    print(f"{manifest['passedTests']} UI tests passed; {len(frames)} original timestamped frames: {output}")


if __name__ == '__main__':
    main()
