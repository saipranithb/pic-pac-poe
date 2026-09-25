import datetime, hashlib, json, os, pathlib, subprocess, sys
ROOT = pathlib.Path('/Users/bavabangaru/Developer/pic-pac-poe-ios-native-parity')
OUT = pathlib.Path('/tmp/picpac-phase3-recovery-lifecycle-20260925')
OUT.mkdir(exist_ok=False)
ENV = dict(os.environ, DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer', XCODE_DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer')
SIM = '97FAA70C-E2D7-4893-9D76-FE172A792433'
DD = '/tmp/picpac-phase3-recovery-lifecycle-derived-20260925'
RESULT = str(OUT / 'hosted.xcresult')
commands = []
def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()
def text(command): return subprocess.check_output(command,cwd=ROOT,env=ENV,text=True).strip()
def fingerprint():
    files = text(['git','ls-files','ios','docs/ios/fixtures']).splitlines()
    return {'capturedAtUTC':now(), 'sourceCommit':text(['git','rev-parse','HEAD']), 'branch':text(['git','branch','--show-current']), 'gitStatusPorcelain':text(['git','status','--porcelain']), 'trackedSourceSHA256':{f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in files if (ROOT/f).is_file()}, 'canonicalCommit':text(['git','-C','/Users/bavabangaru/Developer/pic-pac-poe','rev-parse','HEAD']), 'canonicalStatusPorcelain':text(['git','-C','/Users/bavabangaru/Developer/pic-pac-poe','status','--porcelain'])}
def run(name, command):
    entry={'name':name,'command':command,'startedAtUTC':now(),'environmentOverrides':{'DEVELOPER_DIR':ENV['DEVELOPER_DIR'],'XCODE_DEVELOPER_DIR':ENV['XCODE_DEVELOPER_DIR']}}
    print('START',name,entry['startedAtUTC'],flush=True)
    with (OUT/(name+'.log')).open('w') as stream:
        result=subprocess.run(command,cwd=ROOT,env=ENV,stdout=stream,stderr=subprocess.STDOUT)
    entry.update(endedAtUTC=now(),exitCode=result.returncode)
    commands.append(entry);(OUT/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print('END',name,result.returncode,entry['endedAtUTC'],flush=True)
    if result.returncode: raise SystemExit('Failed '+name+'; original log retained at '+str(OUT/(name+'.log')))
before=fingerprint();(OUT/'source-before.json').write_text(json.dumps(before,indent=2)+'\n')
assert before['sourceCommit']==sys.argv[1], 'Run must name exact checkpoint'
assert before['canonicalCommit']=='dc7cb397ad335783c13c7976d4cef816d2fc0909' and not before['canonicalStatusPorcelain']
run('environment',['sh','-c','xcodebuild -version; swift --version; sw_vers; uname -m; sysctl -n machdep.cpu.brand_string; uptime'])
run('simulator-before',['xcrun','simctl','list','devices','booted','--json'])
devices=json.loads((OUT/'simulator-before.log').read_text())
assert not any(devices['devices'].values()), 'Expected no other booted simulators for cadence measurement'
run('simulator-boot',['xcrun','simctl','boot',SIM])
run('simulator-bootstatus',['xcrun','simctl','bootstatus',SIM,'-b'])
run('hosted',['xcodebuild','-project','ios/PicPacPoe.xcodeproj','-scheme','PicPacPoe','-configuration','Test','-only-testing:PicPacPoeTests','-destination','platform=iOS Simulator,id='+SIM,'-parallel-testing-enabled','NO','-derivedDataPath',DD,'-resultBundlePath',RESULT,'CODE_SIGNING_ALLOWED=NO','CODE_SIGNING_REQUIRED=NO','test'])
app=pathlib.Path(DD)/'Build/Products/Test-iphonesimulator/PicPacPoe.app'
appfiles={str(p.relative_to(app)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(app.rglob('*')) if p.is_file()}
(OUT/'built-app.json').write_text(json.dumps({'configuration':'Test','bundlePath':str(app),'filesSHA256':appfiles,'bundleInventorySHA256':hashlib.sha256(json.dumps(appfiles,sort_keys=True).encode()).hexdigest()},indent=2)+'\n')
run('hosted-inventory',['python3','ios/Scripts/verify-xcresult.py','--result',RESULT,'--target','PicPacPoeTests','--configuration','Test','--output',str(OUT/'hosted-inventory.json')])
run('hosted-attachments',['xcrun','xcresulttool','export','attachments','--path',RESULT,'--output-path',str(OUT/'attachments')])
run('simulator-after',['xcrun','simctl','list','devices','booted','--json'])
run('simulator-shutdown',['xcrun','simctl','shutdown',SIM])
run('package-final',['swift','test','--package-path','ios/Packages/PicPacKit'])
run('performance-release',['swift','test','--configuration','release','--package-path','ios/Packages/PicPacKit','--filter','PerformanceAuditTests'])
after=fingerprint();(OUT/'source-after.json').write_text(json.dumps(after,indent=2)+'\n')
assert before['sourceCommit']==after['sourceCommit'] and before['trackedSourceSHA256']==after['trackedSourceSHA256'], 'Source changed during measurements'
assert before['canonicalCommit']==after['canonicalCommit'] and not after['canonicalStatusPorcelain']
print('COMPLETE',str(OUT),flush=True)
