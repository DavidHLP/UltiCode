#!/usr/bin/env python3
from pathlib import Path
import json,os,subprocess,sys
n=Path('/work/nacos'); results=[]
def run(cmd,seconds):
 try:
  p=subprocess.run(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=seconds)
  return p.returncode==0
 except subprocess.TimeoutExpired:return False
for item in json.loads((n/'inventory.json').read_text()):
 service=item['service']; cp=(n/(service+'.classpath')).read_text(); status=dict(item,passed=False)
 if not run(['javac','-cp',cp,'-d',str(n)]+[str(n/(x+'.java')) for x in ('PackagedSources','ParserProbe','LoopbackProbe','RealNamingProbe')],60):
  status['stage']='compile';results.append(status);break
 for probe in ('PackagedSources','ParserProbe','LoopbackProbe'):
  if not run(['timeout','--kill-after=5s','30s','java','-Xmx128m','-Dcom.google.protobuf.error_on_unsafe_pre22_gencode=true','-cp',str(n)+':'+cp,probe],40):break
 else:
  (n/'auth-packaged-classpath.txt').write_text(cp)
  ok=run(['timeout','--kill-after=10s','600s','python3',str(n/'real_nacos_test.py')],615)
  try:data=json.loads((n/'real-nacos-result.json').read_text())
  except (OSError,ValueError):data={}
  keys=('server_started','authenticated_register_subscribe_push','restart_reconnect_reregister_deregister','passed')
  status['passed']=ok and all(data.get(k) is True for k in keys) and data.get('admin_bootstrap_http')==200
  status['stage']='lifecycle'
  stage=data.get('stage');status['lifecycle_stage']=stage if stage in ('isolation','server_start','bootstrap','client_phase_one','server_restart','client_reconnect','complete') else 'unknown'
  results.append(status)
  if status['passed']:continue
  break
 status['stage']='packaged_probe';results.append(status);break
# Only fixed stage/service, hashes, counts and booleans are emitted.
print('NACOS_VALIDATION_RESULT='+json.dumps({'results':results,'passed':len(results)==6 and all(x['passed'] for x in results)},sort_keys=True),flush=True)
sys.exit(0 if len(results)==6 and all(x['passed'] for x in results) else 1)
