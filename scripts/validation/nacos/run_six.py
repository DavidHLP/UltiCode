#!/usr/bin/env python3
from pathlib import Path
from safe_failure import sanitize_diagnostic
import json,os,re,subprocess,sys
n=Path('/work/nacos'); results=[]
def run(cmd,seconds):
 try:
  p=subprocess.run(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=seconds)
  return p.returncode==0
 except subprocess.TimeoutExpired:return False
for item in json.loads((n/'inventory.json').read_text()):
 if not isinstance(item,dict) or item.get('service') not in ('auth','admin','app','submission','notification','judge'):raise RuntimeError('invalid inventory')
 if any(not isinstance(item.get(k),str) or not re.fullmatch('[0-9a-f]{64}',item[k]) for k in ('boot_sha256','module_sha256')) or type(item.get('api_classes')) is not int or item['api_classes']!=209:raise RuntimeError('invalid inventory')
 service=item['service']; cp=(n/(service+'.classpath')).read_text(); status={k:item[k] for k in ('service','boot_sha256','module_sha256','api_classes')};status['passed']=False
 if not run(['javac','-cp',cp,'-d',str(n)]+[str(n/(x+'.java')) for x in ('PackagedSources','ParserProbe','LoopbackProbe','RealNamingProbe')],60):
  status['stage']='compile';results.append(status);break
 for probe in ('PackagedSources','ParserProbe','LoopbackProbe'):
  if not run(['timeout','--kill-after=5s','30s','java','-Xmx128m','-Dcom.google.protobuf.error_on_unsafe_pre22_gencode=true','-cp',str(n)+':'+cp,probe],40):break
 else:
  (n/'auth-packaged-classpath.txt').write_text(cp)
  ok=run(['timeout','--kill-after=10s','600s','python3',str(n/'real_nacos_test.py')],615)
  try:data=json.loads((n/'real-nacos-result.json').read_text())
  except (OSError,ValueError):data={}
  if not isinstance(data,dict):data={}
  keys=('server_started','authenticated_register_subscribe_push','restart_reconnect_reregister_deregister','passed')
  status['passed']=ok and all(data.get(k) is True for k in keys) and data.get('admin_bootstrap_http')==200
  status['stage']='lifecycle'
  status.update(sanitize_diagnostic(data))
  results.append(status)
  if status['passed']:continue
  break
 status['stage']='packaged_probe';results.append(status);break
# Only fixed stage/service, hashes, counts and booleans are emitted.
print('NACOS_VALIDATION_RESULT='+json.dumps({'results':results,'passed':len(results)==6 and all(x['passed'] for x in results)},sort_keys=True),flush=True)
sys.exit(0 if len(results)==6 and all(x['passed'] for x in results) else 1)
