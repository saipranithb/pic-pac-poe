#!/usr/bin/env python3
"""Reject incomplete XCTest suites; require every configured method to pass."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
ENV = dict(os.environ, DEVELOPER_DIR=os.environ.get('XCODE_DEVELOPER_DIR', os.environ.get('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer')))
SOURCES = {
    'PicPacPoeTests': ROOT / 'ios/PicPacPoeTests/PicPacPoeIntegrationTests.swift',
    'PicPacPoeUITests': ROOT / 'ios/PicPacPoeUITests/PicPacPoeUITests.swift',
}

def run(*args):
    return subprocess.run([str(x) for x in args], cwd=ROOT, env=ENV, check=True,
                          text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def inventory(path, configuration):
    # The Swift parser recognizes real declarations. Comments, raw/multiline
    # strings and nested local functions must not inflate a regex source count.
    conditions = ['-D', 'DEBUG'] + (['-D', 'TESTING'] if configuration == 'Test' else [])
    parsed = run('xcrun', 'swiftc', '-frontend', '-dump-parse', *conditions, path)
    current = None
    methods = []
    for line in parsed.splitlines():
        top = re.match(r'^  \((?:class_decl|extension_decl)\b.*?\] "([A-Za-z_][A-Za-z_0-9]*)"', line)
        if top:
            current = top[1]
        elif re.match(r'^  \(', line):
            current = None
        member = re.match(r'^    \(func_decl\b.*?\] "(test[A-Za-z_0-9]*\(\))"', line)
        if member and current:
            methods.append(current + '/' + member[1])
    if not methods or len(methods) != len(set(methods)):
        raise ValueError('Empty or duplicate Swift XCTest method inventory')
    return sorted(methods)


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def validate(expected, summary, nodes):
    errors = []
    cases = [node for node in walk(nodes) if node.get('nodeType') == 'Test Case']
    identifiers = [node.get('nodeIdentifier') for node in cases]
    if len(identifiers) != len(set(identifiers)):
        errors.append('Duplicate test executions are not an exact suite run')
    missing = sorted(set(expected) - set(identifiers))
    extra = sorted(set(identifiers) - set(expected), key=str)
    if missing or extra:
        errors.append(f'Test inventory differs: missing={missing}, unexpected={extra}')
    observed = {node.get('nodeIdentifier'): node.get('result') for node in cases}
    for identifier in expected:
        wanted = 'Passed'
        if observed.get(identifier) != wanted:
            errors.append(f'{identifier}: expected {wanted}, got {observed.get(identifier)}')
    wanted_counts = {'result': 'Passed', 'totalTestCount': len(expected),
                     'passedTests': len(expected), 'skippedTests': 0,
                     'failedTests': 0, 'expectedFailures': 0}
    for key, wanted in wanted_counts.items():
        if summary.get(key) != wanted:
            errors.append(f'Summary {key}: expected {wanted!r}, got {summary.get(key)!r}')
    return errors, observed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--result', required=True, type=pathlib.Path)
    parser.add_argument('--target', required=True, choices=list(SOURCES))
    parser.add_argument('--configuration', required=True, choices=['Debug', 'Test'])
    parser.add_argument('--output', required=True, type=pathlib.Path)
    args = parser.parse_args()
    source = SOURCES[args.target]
    expected = inventory(source, args.configuration)
    summary = json.loads(run('xcrun', 'xcresulttool', 'get', 'test-results', 'summary', '--path', args.result))
    nodes = json.loads(run('xcrun', 'xcresulttool', 'get', 'test-results', 'tests', '--path', args.result))
    errors, observed = validate(expected, summary, nodes)
    report = {'schemaVersion': 1, 'pass': not errors, 'target': args.target,
              'configuration': args.configuration, 'source': str(source.relative_to(ROOT)),
              'sourceSHA256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'inventoryMethod': 'Swift parser direct class/extension function declarations',
              'expectedTests': expected, 'observedTests': observed,
              'allowedSkips': {},
              'summary': summary, 'errors': errors}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    if errors:
        raise SystemExit('\n'.join('ERROR: ' + error for error in errors))
    print(f'PASS: exact {args.target} inventory; {len(expected)} passed; zero skips')


if __name__ == '__main__':
    main()
