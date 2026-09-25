#!/usr/bin/env python3
"""Read-only shared-contract and unsigned-workflow governance checks."""
import hashlib
import json
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
errors = []
canonical = root / 'docs/ios-handoff/golden-fixtures.json'
staged = root / 'ios/Packages/PicPacKit/Tests/PicPacKitTests/Resources/golden-fixtures.json'
if canonical.read_bytes() != staged.read_bytes():
    errors.append('Swift golden fixture resource differs byte-for-byte from canonical JSON')
fixtures = json.loads(canonical.read_bytes())
groups = fixtures['requiredFixtureGroups']
ids = [case['id'] for group in groups for case in fixtures[group]]
if len(groups) != len(set(groups)) or len(ids) != len(set(ids)):
    errors.append('duplicate fixture group or ID')
print(f'Fixtures: {len(groups)} groups, {len(ids)} unique IDs; SHA-256 {hashlib.sha256(canonical.read_bytes()).hexdigest()}')
for name in ('ci.yml', 'ios-ci.yml'):
    text = (root / '.github/workflows' / name).read_text()
    if not re.search(r'^permissions:\n  contents: read\n', text, re.M):
        errors.append(f'{name}: requires workflow-level contents: read')
    if re.search(r'^\s*\w[\w-]*:\s*write\s*$', text, re.M):
        errors.append(f'{name}: contains a write permission')
    if 'persist-credentials: false' not in text:
        errors.append(f'{name}: checkout must not retain credentials')
    if re.search(r'secrets\.|pull_request_target|xcode-select\s+--switch|-allowProvisioningUpdates', text):
        errors.append(f'{name}: unsigned verification must not use credentials, privileged PRs or provisioning')
    for action in re.findall(r'^\s*uses:\s*([^\s#]+)', text, re.M):
        if not re.fullmatch(r'[\w./-]+@[0-9a-f]{40}', action):
            errors.append(f'{name}: action must be pinned to full SHA: {action}')
provenance = json.loads((root / 'docs/ios/evidence/build-phase-3/native-asset-provenance.json').read_text())
for item in provenance['files']:
    asset = root / item['path']
    if not asset.is_file() or asset.stat().st_size != item['bytes'] or hashlib.sha256(asset.read_bytes()).hexdigest() != item['sha256']:
        errors.append(f"native icon provenance differs: {item['path']}; inspect intentional changes before recording new hashes")
# Strict release is an intentional unresolved identity gate only. Do not turn
# arbitrary verifier errors into success using `|| true`.
ordinary = subprocess.run([sys.executable, str(root / 'docs/ios-handoff/verify-handoff.py')], cwd=root, text=True, capture_output=True)
print(ordinary.stdout, end='')
print(ordinary.stderr, end='', file=sys.stderr)
if ordinary.returncode:
    errors.append('ordinary handoff verifier failed')
strict = subprocess.run([sys.executable, str(root / 'docs/ios-handoff/verify-handoff.py'), '--strict-release'], cwd=root, text=True, capture_output=True)
identity = json.loads((root / 'docs/ios-handoff/release-identity.json').read_bytes())['git']
strict_output = strict.stdout + strict.stderr
strict_errors = [line for line in strict_output.splitlines() if line.startswith('ERROR:')]
expected = 'ERROR: Strict release: git.releaseCommit must be 40 hex characters and git.releaseTag must be non-null/nonempty'
if identity['releaseCommit'] is None and identity['releaseTag'] is None:
    if strict.returncode != 1 or strict_errors != [expected] or '1 error(s).' not in strict_output:
        errors.append('strict handoff verifier failed for a reason beyond null releaseCommit/releaseTag')
    else:
        print('EXPECTED RELEASE GATE: strict verifier fails solely for null releaseCommit and releaseTag')
elif strict.returncode:
    errors.append('strict handoff verifier failed with resolved release identity')
if errors:
    print('\n'.join('ERROR: ' + message for message in errors), file=sys.stderr)
    raise SystemExit(1)
print('PASS: shared bytes, fixture IDs, read-only SHA-pinned credential-free CI, ordinary and strict-release governance')
