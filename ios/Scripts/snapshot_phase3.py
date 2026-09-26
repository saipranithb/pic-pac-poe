#!/usr/bin/env python3
"""Actual simulator captures and read-only baseline comparison. Never blesses baselines."""
import argparse, datetime, hashlib, json, os, pathlib, re, subprocess, tempfile, time, uuid
ROOT = pathlib.Path(__file__).resolve().parents[2]
ENV = dict(os.environ, DEVELOPER_DIR=os.environ.get('XCODE_DEVELOPER_DIR', os.environ.get('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer')))
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-26-5'
BUNDLE = 'dev.saipranith.picpacpoe'
PROFILES = {
 'compact': ('iPhone-SE-3rd-generation', 'large', False),
 'regular': ('iPhone-17-Pro', 'large', False),
 'ipad': ('iPad-Pro-11-inch-M4-8GB', 'large', False),
 'compact-xxxl': ('iPhone-SE-3rd-generation', 'extra-extra-extra-large', False),
 'regular-ax3': ('iPhone-17-Pro', 'accessibility-extra-large', False),
 'ipad-ax3': ('iPad-Pro-11-inch-M4-8GB', 'accessibility-extra-large', False),
 'regular-reduced': ('iPhone-17-Pro', 'large', True),
}
SCENARIOS = ['home', 'classic', 'local-handoff', 'local-reveal', 'human-turn-start', 'human-reveal', 'human-placement', 'computer-reveal', 'computer-thinking', 'computer-targeting', 'computer-placement', 'computer-settled', 'terminal-settling-win', 'terminal-settling-draw', 'result', 'draw-result', 'settings', 'how-to', 'ai-lab']
SCENARIOS += ['home-bottom', 'human-placement-bottom', 'settings-bottom', 'how-to-bottom', 'ai-lab-bottom']
def run(*args, **kwargs):
 return subprocess.run([str(x) for x in args], cwd=ROOT, env=ENV, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout.strip()
def sim(*args): return run('xcrun','simctl',*args)
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def source():
 files = sorted(p for p in (ROOT/'ios').rglob('*') if p.is_file() and '.build' not in p.parts and 'build' not in p.parts and '__pycache__' not in p.parts)
 ios_hash = hashlib.sha256(''.join(str(p.relative_to(ROOT))+sha(p) for p in files).encode()).hexdigest()
 external = ['app/src/main/res/font/fredoka_medium.ttf', 'app/src/main/res/font/fredoka_semibold.ttf', 'app/src/main/assets/licenses/fredoka-OFL.txt', 'docs/ios-handoff/reference/screenshot-manifest.json']
 inputs = [{'path':str(p.relative_to(ROOT)), 'sha256':sha(p)} for p in files + [ROOT / name for name in external]]
 fingerprint = hashlib.sha256(json.dumps(inputs,sort_keys=True,separators=(',',':')).encode()).hexdigest()
 return {'commit':run('git','rev-parse','HEAD'),'branch':run('git','branch','--show-current'), 'status':run('git','status','--porcelain').splitlines(), 'iosSourceSHA256':ios_hash, 'buildInputSHA256':fingerprint, 'externalInputs':{name:sha(ROOT/name) for name in external}}

DEVICE_PIXELS = {'iPhone-SE-3rd-generation':(750,1334), 'iPhone-17-Pro':(1206,2622), 'iPad-Pro-11-inch-M4-8GB':(1668,2420)}
PRESENTATION_TIMEOUT_SECONDS = 120
CAPTURE_COMMAND_TIMEOUT_SECONDS = 60
def selected(value, allowed, label):
 values=value.split(',')
 if not values or len(values)!=len(set(values)) or any(x not in allowed for x in values):
  raise ValueError('Unknown or duplicate '+label+': '+value)
 return values

def canonical(scenario,theme):
 scenario=scenario.removesuffix('-bottom')
 mapping={'home':f'home-scene-{theme}-normal-412dp-x.png','classic':f'classic-{theme}.png', 'local-handoff':'local-handoff-dark.png','local-reveal':'local-reveal-dark.png','human-reveal':'human-reveal-dark.png','human-placement':'human-placement-dark.png','human-turn-start':f'computer-turn-start-{theme}.png','terminal-settling-win':f'computer-settled-{theme}.png','terminal-settling-draw':f'computer-settled-{theme}.png','draw-result':f'result-{theme}.png'}
 name=mapping.get(scenario,f'{scenario}-{theme}.png'); p=ROOT/'docs/ios-handoff/reference'/name
 digest=sha(p)
 governed=json.loads((ROOT/'docs/ios-handoff/reference/screenshot-manifest.json').read_text())
 expected=next(r['sha256'] for r in governed['screenshots'] if r['path']==name)
 assert digest==expected,'Canonical Android capture hash changed: '+name
 return {'path':str(p.relative_to(ROOT)),'sha256':digest,'comparison':'semantic geometry and intent; different state/theme where no exact Android counterpart exists'}
def compile_pixels(tmp):
 tool=tmp/'snapshot-pixels';run('xcrun','swiftc','-O',ROOT/'ios/Scripts/SnapshotPixels.swift','-o',tool);return tool

def capture_presented_frame(udid, output, name, tool, expected_pixels):
 # A restoration marker can precede SpringBoard's app-launch transition on a
 # hosted simulator. Wait for presentation within this same launch, never for
 # agreement with a baseline. A stable but incorrect UI still fails compare().
 diagnostics=output/'capture-diagnostics'/pathlib.Path(name).stem
 diagnostics.mkdir(parents=True)
 # Hosted capture/inspection can take over 25 seconds for one valid frame.
 # Budget both required observations while keeping a hung command bounded.
 probes=[];previous=None;accepted=False;failure=None;started=time.monotonic();deadline=started+PRESENTATION_TIMEOUT_SECONDS
 def timed_run(observation, stage, *args):
  command_started=time.monotonic();remaining=deadline-command_started
  if remaining<=0:raise RuntimeError('Presentation deadline exceeded: '+name)
  timeout=min(CAPTURE_COMMAND_TIMEOUT_SECONDS,remaining)
  observation[stage+'TimeoutSeconds']=timeout
  observation[stage+'StartedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
  try:return run(*args,timeout=timeout)
  except Exception as error:
   observation[stage+'Error']=type(error).__name__+': '+str(error)
   raise
  finally:observation[stage+'Seconds']=time.monotonic()-command_started
 try:
  while time.monotonic()<deadline:
   probe=diagnostics/f'probe-{len(probes)+1:03d}.png'
   observation={'path':str(probe.relative_to(output)),'requestedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
   probes.append(observation)
   timed_run(observation,'screenshot','xcrun','simctl','io',udid,'screenshot','--type=png',probe)
   inspected=json.loads(timed_run(observation,'inspection',tool,'inspect',probe))
   observation.update({'sha256':sha(probe),'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),**inspected})
   if time.monotonic()>=deadline:raise RuntimeError('Presentation deadline exceeded: '+name)
   if (inspected['width'],inspected['height'])!=expected_pixels:raise RuntimeError('Unexpected full-frame dimensions: '+name)
   digest=inspected['rgbaSHA256'] if inspected['nonblank'] and inspected['opaque'] else None
   if digest is not None and digest==previous:
    probe.replace(output/name);probes[-1]['path']=name;accepted=True
    return inspected
   previous=digest
   time.sleep(min(0.5,max(0,deadline-time.monotonic())))
  raise RuntimeError(f'No stable nonblank opaque presentation within {PRESENTATION_TIMEOUT_SECONDS} seconds: '+name)
 except Exception as error:
  failure=type(error).__name__+': '+str(error)
  raise
 finally:
  for observation in probes:
   path=output/observation['path']
   if path.is_file():observation['sha256']=sha(path)
  (diagnostics/'probes.json').write_text(json.dumps({'accepted':accepted,'sameLaunch':True,'baselineConsulted':False,'deadlineSeconds':PRESENTATION_TIMEOUT_SECONDS,'commandTimeoutSeconds':CAPTURE_COMMAND_TIMEOUT_SECONDS,'elapsedSeconds':time.monotonic()-started,'requiredConsecutiveStableFrames':2,'error':failure,'probes':probes},indent=2)+'\n')

def capture(args):
 profiles=selected(args.profiles,PROFILES,'profiles');scenarios=selected(args.scenarios,SCENARIOS,'scenarios')
 start=source();records=[]
 references={scenario+'--'+theme:canonical(scenario,theme) for scenario in scenarios for theme in ['dark','light']}
 output=args.output.resolve();output.mkdir(parents=True,exist_ok=False)
 (output/'INCOMPLETE').write_text('Capture has not completed.\n')
 with tempfile.TemporaryDirectory(prefix='picpac-snapshots-') as temp:
  tmp=pathlib.Path(temp)
  version=run('xcodebuild','-version'); assert version=='Xcode 26.6\nBuild version 17F113', version
  runtimes=json.loads(sim('list','runtimes','-j'))
  runtime=next(x for x in runtimes['runtimes'] if x['identifier']==RUNTIME and x['isAvailable'])
  assert runtime['buildversion']=='23F77','Uncalibrated simulator runtime build'
  environment={'architecture':run('uname','-m'),'macOS':run('sw_vers','-productVersion'),'swift':run('swift','--version'),'simulatorSDK':run('xcrun','--sdk','iphonesimulator','--show-sdk-version'),'runtimeBuild':runtime['buildversion']}
  assert environment['architecture']=='arm64','Uncalibrated simulator architecture'
  command=['xcodebuild','-project',str(ROOT/'ios/PicPacPoe.xcodeproj'),'-scheme','PicPacPoe','-configuration','Debug','-destination','generic/platform=iOS Simulator','-derivedDataPath',str(tmp/'build'),'CODE_SIGNING_ALLOWED=NO','CODE_SIGNING_REQUIRED=NO','build']
  with (output/'build.log').open('w') as log: subprocess.run(command,cwd=ROOT,env=ENV,stdout=log,stderr=subprocess.STDOUT,check=True)
  app=tmp/'build/Build/Products/Debug-iphonesimulator/PicPacPoe.app'
  executableHash=sha(app/'PicPacPoe');tool=compile_pixels(tmp)
  appFiles={str(p.relative_to(app)):sha(p) for p in sorted(app.rglob('*')) if p.is_file()}
  appBundleHash=hashlib.sha256(json.dumps(appFiles,sort_keys=True,separators=(',',':')).encode()).hexdigest()
  for profile in profiles:
   device,size,reduced=PROFILES[profile]
   udid=sim('create',f'PicPac Phase3 snapshots {uuid.uuid4().hex[:8]}','com.apple.CoreSimulator.SimDeviceType.'+device,RUNTIME)
   try:
    sim('boot',udid);sim('bootstatus',udid,'-b');sim('ui',udid,'content_size',size)
    sim('status_bar',udid,'override','--time','9:41','--batteryState','charged','--batteryLevel','100','--wifiBars','3','--cellularBars','4')
    sim('install',udid,app); container=pathlib.Path(sim('get_app_container',udid,BUNDLE,'data'))
    # A fresh runtime posts a first-boot Apple Intelligence banner after launch.
    # Prewarm and let OS onboarding settle; never crop or mask system UI.
    sim('launch',udid,BUNDLE,'-screenshot-scenario','home','-snapshot-home-time','0')
    time.sleep(45)
    for theme in ['dark','light']:
     sim('ui',udid,'appearance',theme);time.sleep(0.5)
     for scenario in scenarios:
      token=uuid.uuid4().hex
      fixture=scenario.removesuffix('-bottom')
      launch=['-screenshot-scenario',fixture,'-screenshot-theme',theme,'-screenshot-ready-token',token,'-snapshot-home-time','0','-AppleLanguages','(en)','-AppleLocale','en_US']
      if scenario.endswith('-bottom'):launch+=['-snapshot-scroll-bottom']
      if reduced:launch+=['-screenshot-reduced']
      sim('launch','--terminate-running-process',udid,BUNDLE,*launch)
      marker=container/'tmp'/('picpac-screenshot-ready-'+token)
      deadline=time.monotonic()+30
      while not marker.exists():
       if time.monotonic()>deadline:raise RuntimeError('No rendered readiness: '+scenario)
       time.sleep(0.05)
      time.sleep(1.25)
      name=f'{profile}--{theme}--{scenario}.png'
      inspected=capture_presented_frame(udid,output,name,tool,DEVICE_PIXELS[device])
      records.append({'path':name,'profile':profile,'theme':theme,'scenario':scenario,'device':device,'runtime':'26.5','contentSize':size,'reducedMotion':reduced or (fixture=='settings' and theme=='light'),'homeTime':0,'launchArguments':launch,'sha256':sha(output/name),'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readiness':'unique post-restoration marker + 1250ms minimum settle + two consecutive identical valid full RGBA frames','canonical':references[scenario+'--'+theme]})
      records[-1].update(inspected)
      print(name,flush=True)
   finally:
    try:sim('shutdown',udid)
    finally:sim('delete',udid)
  end=source()
  if start['buildInputSHA256']!=end['buildInputSHA256']:raise RuntimeError('Build inputs changed during capture')
  if any(sha(ROOT/ref['path'])!=ref['sha256'] for ref in references.values()):raise RuntimeError('Canonical Android reference changed during capture')
  manifest={'schemaVersion':1,'kind':'actual unsigned Debug simulator fixtures','source':start,'appExecutableSHA256':executableHash,'stableSource':True,'toolchain':version,'environment':environment,'runtime':'26.5','profiles':{name:PROFILES[name] for name in profiles},'capturePlan':{'profiles':profiles,'scenarios':scenarios,'themes':['dark','light']},'canonicalReferenceManifestSHA256':sha(ROOT/'docs/ios-handoff/reference/screenshot-manifest.json'),'captures':records,'noCrop':True,'baselinePolicy':'Review images before promotion. Comparison cannot update baselines.'}
  manifest['appBundleSHA256']=appBundleHash
  manifest['appFileSHA256']=appFiles
  (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
  def sheet(subset,destination):
   entries=[{'path':str(output/r['path']),'label':r['path'].replace('--',' / ')} for r in subset]
   plan=tmp/'sheet.json';plan.write_text(json.dumps(entries));run(tool,'sheet',plan,destination)
  (output/'contact-sheets').mkdir()
  for profile in profiles:sheet([r for r in records if r['profile']==profile],output/'contact-sheets'/f'{profile}.png')
  representative='regular' if 'regular' in profiles else profiles[0]
  sheet([r for r in records if r['profile']==representative],output/'contact-sheet.png')
 (output/'INCOMPLETE').unlink()
 checksums(output)
def checksums(output):
 (output/'checksums.sha256').write_text(''.join(sha(p)+'  '+str(p.relative_to(output))+'\n' for p in sorted(output.rglob('*')) if p.is_file() and p.name!='checksums.sha256'))
def package(root):
 root=root.resolve()
 if (root/'INCOMPLETE').exists():raise ValueError('Incomplete capture package: '+str(root))
 manifest=json.loads((root/'manifest.json').read_text())
 if manifest.get('schemaVersion')!=1 or manifest.get('stableSource') is not True or manifest.get('noCrop') is not True:
  raise ValueError('Unsupported, unstable or cropped capture package: '+str(root))
 if not re.fullmatch('[0-9a-f]{64}',manifest.get('appExecutableSHA256','')):
  raise ValueError('Missing executable provenance')
 appFiles=manifest.get('appFileSHA256',{})
 if appFiles.get('PicPacPoe')!=manifest['appExecutableSHA256'] or not appFiles.get('PicPacPoe.debug.dylib'):
  raise ValueError('Missing actual Debug application-code provenance')
 if hashlib.sha256(json.dumps(appFiles,sort_keys=True,separators=(',',':')).encode()).hexdigest()!=manifest.get('appBundleSHA256'):
  raise ValueError('App bundle provenance hash mismatch')
 if not re.fullmatch('[0-9a-f]{64}',manifest.get('source',{}).get('buildInputSHA256','')):
  raise ValueError('Missing complete build-input provenance')
 if manifest.get('environment',{}).get('runtimeBuild')!='23F77' or manifest['environment'].get('architecture')!='arm64':raise ValueError('Uncalibrated runtime build or architecture')
 captures=manifest['captures']
 names=[r['path'] for r in captures]
 if not names or len(names)!=len(set(names)):raise ValueError('Empty or duplicate capture inventory')
 if any(pathlib.Path(name).name!=name or not name.endswith('.png') for name in names):raise ValueError('Unsafe capture path')
 plan=manifest['capturePlan']
 profiles=selected(','.join(plan['profiles']),PROFILES,'manifest profiles')
 scenarios=selected(','.join(plan['scenarios']),SCENARIOS,'manifest scenarios')
 if plan['themes']!=['dark','light']:raise ValueError('Both themes must be captured')
 expected={f'{p}--{t}--{s}.png' for p in profiles for t in plan['themes'] for s in scenarios}
 if set(names)!=expected:raise ValueError('Capture inventory does not fulfill declared matrix')
 if manifest['profiles']!={p:list(PROFILES[p]) for p in profiles}:raise ValueError('Profile definition drift')
 expected_reference_hash=sha(ROOT/'docs/ios-handoff/reference/screenshot-manifest.json')
 if manifest['canonicalReferenceManifestSHA256']!=expected_reference_hash:raise ValueError('Canonical reference manifest changed')
 for record in captures:
  name=record['path'];profile=record['profile'];device,size,reduced=PROFILES[profile]
  fixture=record['scenario'].removesuffix('-bottom')
  expected_reduced=reduced or (fixture=='settings' and record['theme']=='light')
  if name!=f"{profile}--{record['theme']}--{record['scenario']}.png":raise ValueError('Capture identity mismatch: '+name)
  if record['device']!=device or record['contentSize']!=size or record['reducedMotion']!=expected_reduced or record['homeTime']!=0 or record['runtime']!=manifest['runtime']:
   raise ValueError('Capture configuration mismatch: '+name)
  if (record['width'],record['height'])!=DEVICE_PIXELS[device] or record.get('nonblank') is not True or record.get('opaque') is not True:
   raise ValueError('Invalid full-frame inspection: '+name)
  if sha(root/name)!=record['sha256']:raise ValueError('Capture hash mismatch: '+name)
  if record['canonical']!=canonical(record['scenario'],record['theme']):raise ValueError('Canonical reference drift: '+name)
 # Verify all retained package outputs, not just the image manifest. REVIEW.json
 # is written manually only after inspection and must also enter the checksum set.
 expected_files={str(p.relative_to(root)) for p in root.rglob('*') if p.is_file() and p.name!='checksums.sha256'}
 entries={}
 for line in (root/'checksums.sha256').read_text().splitlines():
  digest,name=line.split('  ',1)
  if name in entries or not re.fullmatch('[0-9a-f]{64}',digest):raise ValueError('Invalid checksum inventory')
  if not (root/name).resolve().is_relative_to(root):raise ValueError('Unsafe checksum path')
  entries[name]=digest
 if entries.keys()!=expected_files:raise ValueError('Package checksum inventory differs')
 for name,digest in entries.items():
  if sha(root/name)!=digest:raise ValueError('Package checksum mismatch: '+name)
 return manifest

def compare(args):
 if args.output.resolve().is_relative_to(args.baseline.resolve()):raise ValueError('Comparison cannot write into the baseline package')
 base=package(args.baseline);candidate=package(args.candidate)
 reviewed=False
 if args.allow_unreviewed:
  if os.environ.get('GITHUB_ACTIONS')=='true':raise ValueError('CI cannot bypass baseline review')
 else:
  approval=json.loads((args.baseline/'REVIEW.json').read_text())
  if (approval.get('schemaVersion')!=1 or approval.get('approved') is not True
      or approval.get('manifestSHA256')!=sha(args.baseline/'manifest.json')
      or approval.get('captureCount')!=len(base['captures'])
      or not all(approval.get(key) for key in ['reviewer','reviewedAt','notes'])):
   raise ValueError('Baseline requires an explicit review of this exact manifest and every capture')
  reviewed=True
 if base['toolchain']!=candidate['toolchain'] or base['runtime']!=candidate['runtime']:
  raise ValueError('Uncalibrated toolchain/runtime requires separate intentional review')
 if any(base['environment'][key]!=candidate['environment'][key] for key in ['architecture','swift','simulatorSDK','runtimeBuild']):raise ValueError('Uncalibrated capture environment')
 if base['capturePlan']!=candidate['capturePlan']:raise ValueError('Capture plans differ')
 left={r['path']:r for r in base['captures']};right={r['path']:r for r in candidate['captures']}
 if left.keys()!=right.keys():raise ValueError('Capture inventory differs')
 for name in left:
  for key in ['profile','theme','scenario','device','runtime','contentSize','reducedMotion','homeTime','width','height','canonical']:
   if left[name][key]!=right[name][key]:raise ValueError('Capture metadata differs: '+name+'/'+key)
 results=[]
 with tempfile.TemporaryDirectory(prefix='picpac-pixels-') as temp:
  tool=compile_pixels(pathlib.Path(temp))
  for name in left:
   metric=json.loads(run(tool,'compare',args.baseline/name,args.candidate/name));results.append(dict(path=name,**metric))
 report={'schemaVersion':1,'minimumPixelAgreement':0.995,'perChannelTolerance':2,'channels':'RGBA','allPixelsCompared':True,'autoBaselineReplacement':False,'baselineReviewed':reviewed,'purpose':'approved regression' if reviewed else 'unreviewed calibration only','baselineManifestSHA256':sha(args.baseline/'manifest.json'),'candidateManifestSHA256':sha(args.candidate/'manifest.json'),'pass':all(r['pass'] for r in results),'results':results}
 args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
 print(f"{sum(r['pass'] for r in results)}/{len(results)} snapshots pass; minimum agreement {min(r['agreement'] for r in results):.8f}")
 if not report['pass']:raise SystemExit(1)

parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='command',required=True)
c=sub.add_parser('capture');c.add_argument('--output',type=pathlib.Path,required=True);c.add_argument('--profiles',default=','.join(PROFILES));c.add_argument('--scenarios',default=','.join(SCENARIOS));c.set_defaults(fn=capture)
c=sub.add_parser('compare');c.add_argument('--baseline',type=pathlib.Path,required=True);c.add_argument('--candidate',type=pathlib.Path,required=True);c.add_argument('--output',type=pathlib.Path,required=True);c.add_argument('--allow-unreviewed',action='store_true',help='Local calibration only; never approves or modifies baselines');c.set_defaults(fn=compare)
if __name__=='__main__':
 args=parser.parse_args();args.fn(args)
