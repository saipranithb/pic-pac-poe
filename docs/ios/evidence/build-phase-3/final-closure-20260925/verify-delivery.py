"""Read-only final evidence/source guard; never manufactures a verdict or checksum.

Run after final evidence assembly and the intentional UI README update. It also
works after the delivery commit. The tested checkpoint is fixed independently of
HEAD; every tracked/untracked delivery path is checked against a narrow allowlist.
"""
import hashlib,json,pathlib,re,subprocess
ROOT=pathlib.Path('/Users/bavabangaru/Developer/pic-pac-poe-ios-native-parity')
CANON=pathlib.Path('/Users/bavabangaru/Developer/pic-pac-poe')
E=ROOT/'docs/ios/evidence/build-phase-3'
EXPECTED='e6c0c65c9c5ad84e2f961839c5eede753c25448e'
INPUTS='2be2a20569eb23b3b22553c8e39ed8ce25ebeeb23fbab3665a8a3d175eb2dec3'
CANONICAL='dc7cb397ad335783c13c7976d4cef816d2fc0909'
README='ios/PicPacPoeUITests/README.md'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def cmd(*a):return subprocess.check_output(a,cwd=ROOT,text=True).strip()
def names(*a):return set(x.decode() for x in subprocess.check_output(a,cwd=ROOT).split(b'\0') if x)
def load(p):return json.loads(p.read_text())
def allowed(p):return p==README or p.startswith('docs/ios/')
def safe(root,name):
 rel=pathlib.PurePosixPath(name);assert not rel.is_absolute() and '..' not in rel.parts,name
 p=root/name;assert p.resolve().is_relative_to(root.resolve()),(root,name);return p

def exact_source(record):
 assert record['commit']==EXPECTED and record['branch']=='codex/ios-native-parity' and not record['status'],record
 assert record['buildInputSHA256']==INPUTS,record

def inventory_gate(path,count,target):
 j=load(path);assert j['pass'] is True and not j['errors'] and j['target']==target
 assert len(j['expectedTests'])==count and len(set(j['expectedTests']))==count
 assert set(j['observedTests'])==set(j['expectedTests']) and all(v=='Passed' for v in j['observedTests'].values())
 assert not j['allowedSkips'];s=j['summary'];assert s['passedTests']==count and s['failedTests']==0 and s['skippedTests']==0 and s['totalTestCount']==count
 assert sha(ROOT/j['source'])==j['sourceSHA256'];return j

assert cmd('git','branch','--show-current')=='codex/ios-native-parity'
subprocess.run(['git','merge-base','--is-ancestor',EXPECTED,'HEAD'],cwd=ROOT,check=True)
assert cmd('git','-C',str(CANON),'rev-parse','HEAD')==CANONICAL
assert not cmd('git','-C',str(CANON),'status','--porcelain=v1','--untracked-files=all')
# Original 191-file inventory is preserved; the independent 267-file inventory
# additionally includes game-core/game-ai/game-tools, Gradle and top-level inputs.
source_path=E/'final-closure-20260925/tested-complete-source-inventory.json'
if not source_path.exists():source_path=pathlib.Path('/tmp/picpac-phase3-complete-source-inventory-e6c0c65-20260925.json')
source=load(source_path);assert source['sourceCommit']==EXPECTED
assert source['allowedDeliveryDocumentationPrefix']=='docs/ios/' and source['onlyAllowedNativeDelta']==README
expected_tracked=names('git','ls-tree','-r','-z','--name-only',EXPECTED)
assert set(source['files'])=={p for p in expected_tracked if not p.startswith('docs/ios/')}
changes=sorted(p for p,h in source['files'].items() if not safe(ROOT,p).is_file() or sha(ROOT/p)!=h)
assert changes==[README],changes
# All changed tracked paths, including new tracked files, plus untracked paths.
# docs/ios-handoff is deliberately NOT inside the allowed docs/ios prefix.
tracked_delta=names('git','diff',EXPECTED,'--name-only','-z','--')
untracked=names('git','ls-files','--others','--exclude-standard','-z')
assert all(allowed(p) for p in tracked_delta|untracked),sorted(p for p in tracked_delta|untracked if not allowed(p))
assert {p for p in tracked_delta if p.startswith('ios/')}=={README}
# Mode-only source changes must not escape content hashes.
for line in cmd('git','diff','--raw','--no-renames','--no-abbrev',EXPECTED,'--').splitlines():
 metadata,path=line.split('\t',1);old_mode,new_mode,*_=metadata.lstrip(':').split()
 assert allowed(path) and (path!=README or old_mode==new_mode),(path,old_mode,new_mode)

baseline=E/'baselines';repeat=E/'repeated-captures';bm=load(baseline/'manifest.json');rm=load(repeat/'manifest.json')
for package,m in [(baseline,bm),(repeat,rm)]:
 exact_source(m['source']);assert m['stableSource'] is True and m['noCrop'] is True and len(m['captures'])==336
 assert len({r['path'] for r in m['captures']})==336
 for capture in m['captures']:
  assert sha(safe(package,capture['path']))==capture['sha256']
  assert sha(safe(ROOT,capture['canonical']['path']))==capture['canonical']['sha256']
assert bm['capturePlan']==rm['capturePlan'] and bm['toolchain']==rm['toolchain'] and bm['environment']==rm['environment'] and bm['runtime']==rm['runtime']
snap=load(E/'snapshot-comparison.json');assert snap['pass'] is True and snap['baselineReviewed'] is True and len(snap['results'])==336
assert snap['baselineManifestSHA256']==sha(baseline/'manifest.json') and snap['candidateManifestSHA256']==sha(repeat/'manifest.json')
assert snap['minimumPixelAgreement']==0.995 and snap['perChannelTolerance']==2 and snap['channels']=='RGBA' and snap['allPixelsCompared'] is True and snap['autoBaselineReplacement'] is False
assert len({r['path'] for r in snap['results']})==336 and {r['path'] for r in snap['results']}=={r['path'] for r in bm['captures']}
assert all(r['pass'] is True and 0.995<=r['agreement']<=1.0 for r in snap['results'])
base=load(baseline/'REVIEW.json');assert base['approved'] is True and base['captureCount']==336 and base['manifestSHA256']==sha(baseline/'manifest.json')
assert all(base.get(k) for k in ['reviewer','reviewedAt','notes'])

uiroot=E/'ui-flows-layout-final-20260925';ui=load(uiroot/'manifest.json');exact_source(ui['source'])
assert ui['result']=='Passed' and ui['passedTests']==26 and ui['failedTests']==0 and ui['inventoryGatePassed'] is True and ui['stableSource'] is True and not ui['missingFrameStems'] and ui['noCrop'] is True
assert len(ui['frames'])>=98
inventory_gate(uiroot/'inventory-gate.json',26,'PicPacPoeUITests')

buildroot=E/'unsigned-builds-layout-final-20260925';build=load(buildroot/'manifest.json');exact_source(build['source'])
assert build['pass'] is True and build['stableSource'] is True and len(build['builds'])==4
assert {(b['configuration'],b['sdk']) for b in build['builds']}=={(c,s) for c in ['Debug','Release'] for s in ['iphonesimulator','iphoneos']}
for b in build['builds']:
 assert b['exitCode']==0 and b['unsigned'] is True and b['machoMinimums'] and all(x=='17.0' for x in b['machoMinimums'])
 assert len(b['machoMinimums'])==len(b['machoPlatforms']) and all(x==('IOS' if b['sdk']=='iphoneos' else 'IOSSIMULATOR') for x in b['machoPlatforms'])
 assert b['identity']['MinimumOSVersion']=='17.0' and b['identity']['CFBundleIdentifier']=='dev.saipranith.picpacpoe'
 assert b['appBundleSHA256']==hashlib.sha256(json.dumps(b['appFileSHA256'],sort_keys=True,separators=(',',':')).encode()).hexdigest()
 assert 'CODE_SIGNING_ALLOWED=NO' in b['command'] and 'CODE_SIGNING_REQUIRED=NO' in b['command']
 if b['configuration']=='Release':assert b['coverageSectionsAbsent'] is True and b['instrumentedCompilerCommands']==0 and b['verificationExitCode']==0

smokeroot=E/'release-launch-layout-final-20260925';smoke=load(smokeroot/'manifest.json');exact_source(smoke['source'])
release=next(b for b in build['builds'] if (b['configuration'],b['sdk'])==('Release','iphonesimulator'))
assert smoke['pass'] is True and smoke['commandsPassed'] is True and smoke['shutdownConfirmed'] is True and smoke['launchArguments']==[]
assert smoke['appBundleSHA256']==release['appBundleSHA256'] and smoke['appFileSHA256']==release['appFileSHA256']
assert smoke['device']['udid']=='97FAA70C-E2D7-4893-9D76-FE172A792433' and smoke['device']['state']=='Shutdown'
assert smoke['visualReview']['outcome']=='PASS' and smoke['visualReview']['viewDetail']=='original'
assert sha(smokeroot/'release-launch-full-frame.png')==smoke['screenshotSHA256']
assert sha(safe(smokeroot,smoke['consumedBuildManifestCopy']))==smoke['sourceBuildManifestSHA256']
launches=[c for c in smoke['commands'] if c['command'][:3]==['xcrun','simctl','launch']]
assert len(launches)==1 and launches[0]['command']==['xcrun','simctl','launch',smoke['device']['udid'],'dev.saipranith.picpacpoe']
assert all(c['exitCode']==0 for c in smoke['commands'])

hostroot=E/'lifecycle-performance/post-layout-20260925';inventory_gate(hostroot/'hosted-inventory.json',12,'PicPacPoeTests')
before=load(hostroot/'source-before.json');after=load(hostroot/'source-after.json')
for record in [before,after]:
 assert record['sourceCommit']==EXPECTED and record['branch']=='codex/ios-native-parity' and not record['gitStatusPorcelain']
 assert record['canonicalCommit']==CANONICAL and not record['canonicalStatusPorcelain']
 assert all(source['files'][p]==h for p,h in record['trackedSourceSHA256'].items())
assert before['trackedSourceSHA256']==after['trackedSourceSHA256']

for p in [E/'REVIEW.md',ROOT/'docs/ios/VERIFICATION.md',E/'manifest.json',E/'APP-STORE-PHASE-1-PROMPT.md',E/'SNAPSHOT-GOVERNANCE.md']:
 assert p.is_file() and not re.search(r'PENDING|verification in progress|PENDINGFINAL',p.read_text()),str(p)
count=0;counts={}
for inventory in sorted(E.rglob('checksums.sha256')):
 seen=set()
 for line in inventory.read_text().splitlines():
  digest,name=line.split('  ',1);assert re.fullmatch('[0-9a-f]{64}',digest)
  assert name not in seen,(inventory,name);seen.add(name);p=safe(inventory.parent,name)
  assert p.is_file() and sha(p)==digest,(inventory,name);count+=1
 counts[str(inventory.relative_to(E))]=len(seen)
 if inventory==E/'checksums.sha256':
  # The final root inventory is exact and includes every nested checksum file.
  # Historical parent inventories keep their original membership and hashes.
  actual={str(p.relative_to(E)) for p in E.rglob('*') if p.is_file() and p!=inventory}
  assert seen==actual,{'missingFromRootInventory':sorted(actual-seen),'staleRootEntries':sorted(seen-actual)}
assert 'checksums.sha256' in counts,'Final exact root checksum inventory is required'
assert counts['unsigned-builds-layout-final-20260925/checksums.sha256']==17
assert counts['release-launch-layout-final-20260925/checksums.sha256']==5
print(json.dumps({'pass':True,'checksumEntries':count,'completeSourceInventoryFiles':len(source['files']),'nativeDeliveryChanges':changes,'allDeliveryPathsAllowed':True,'canonicalClean':True,'sourceCheckpoint':EXPECTED},indent=2))
