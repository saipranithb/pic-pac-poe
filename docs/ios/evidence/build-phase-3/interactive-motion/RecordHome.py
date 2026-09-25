"""Record an already visible live Home; never alters app state or crops video."""
import pathlib, subprocess, hashlib, json, time, datetime, signal, sys
out = pathlib.Path(__file__).resolve().parent
name, udid = sys.argv[1:]
assert name in ['home-normal', 'home-reduced']
container = pathlib.Path(subprocess.check_output(['xcrun', 'simctl', 'get_app_container', udid, 'dev.saipranith.picpacpoe', 'data'], text=True).strip())
store = container / 'Library/Application Support/PicPacPoeUITests/manual-phase3'
settings = json.loads((store / 'settings.json').read_text())
assert settings['reducedMotion'] == (name == 'home-reduced')
def state():
    return {p.name: {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'mtimeNS': p.stat().st_mtime_ns, 'bytes': p.stat().st_size} for p in sorted(store.iterdir()) if p.is_file()}
before = state()
started = datetime.datetime.now(datetime.timezone.utc).isoformat()
with (out / (name + '-record.log')).open('w') as log:
    proc = subprocess.Popen(['xcrun', 'simctl', 'io', udid, 'recordVideo', '--codec=h264', str(out / (name + '.mov'))], stdout=log, stderr=subprocess.STDOUT)
    time.sleep(12)
    proc.send_signal(signal.SIGINT)
    result = proc.wait(timeout=30)
after = state()
assert before == after, 'Home motion changed persisted test-session files'
assert result == 0
report = {'kind': name + ', observed twelve-second interval', 'startedAt': started, 'endedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'recordExitStatus': result, 'file': name + '.mov', 'noCrop': True, 'ordinaryClock': True, 'fixtureTimeOverride': None, 'settings': settings, 'beforePersistedFiles': before, 'afterPersistedFiles': after, 'persistedBytesAndModificationTimesUnchanged': True, 'limitation': 'File invariance is direct runtime evidence. Zero RNG/feedback calls are separately proven by hosted injected dependency tests.'}
(out / (name + '.json')).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
