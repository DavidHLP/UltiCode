"""Bounded JVM17 mechanism preflight followed by at most one server attempt."""
import json,subprocess,sys,tempfile
from pathlib import Path
from trace_summary import summarize
from safe_failure import sanitize_diagnostic
from layered_preflight import sanitize,jvm_probe
ROOT=Path(__file__).resolve().parent
JAVA=Path('/usr/lib/jvm/java-17-openjdk-amd64/bin/java')
TRACE=Path('/trace')
def quiet(args,timeout):
 return subprocess.run(args,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=timeout).returncode==0

def canary():
 # No service, networking, credentials, or exception-message export.
 if jvm_probe(JAVA)['status']!='pass':return False
 path=TRACE/'canary.log';path.unlink(missing_ok=True)
 with tempfile.TemporaryDirectory(prefix='trace-canary-') as tmp:
  source=Path(tmp)/'TraceCanary.java'
  source.write_text('public class TraceCanary { public static void main(String[] a) {} }\n')
  if not quiet([str(JAVA.with_name('javac')),'-d',tmp,str(source)],20):return False
  if not quiet([str(JAVA),'-Xlog:class+load=info:file=/trace/canary.log:none:filecount=0','-cp',tmp,'TraceCanary'],15):return False
  result=summarize(path)
  good=result['read_state']=='read' and result['snapshot_read_complete'] and result['class_observed']['java_object'] and result['class_observed']['synthetic_canary']
 path.unlink(missing_ok=True)
 return good

def main():
 result={'schema':'nacos-trace-v1','mechanism_preflight':False,'server_attempted':False,'passed':False}
 try:
  result['mechanism_preflight']=canary()
  if not result['mechanism_preflight']:return result
  trace=TRACE/'server.log';trace.unlink(missing_ok=True)
  output=ROOT/'real-nacos-result.json';output.unlink(missing_ok=True)
  result['server_attempted']=True
  exit_ok=quiet(['timeout','--kill-after=10s','180s',sys.executable,str(ROOT/'trace_entry.py')],195)
  data={}
  try:
   if output.stat().st_size<=131072:data=json.loads(output.read_text())
  except (OSError,ValueError):pass
  if not isinstance(data,dict):data={}
  preflight=sanitize(data.get('preflight'))
  result.update({'trace':summarize(trace),'preflight':preflight,'readiness':data.get('server_started') is True,'bootstrap_ok':data.get('admin_bootstrap_http')==200,**sanitize_diagnostic(data)})
  result['passed']=bool(result['trace']['read_state']=='read' and result['trace']['class_observed']['java_object'] and exit_ok and data.get('server_only') is True and data.get('launch_mode')=='direct' and data.get('passed') is True and data.get('stage')=='complete' and result['readiness'] and result['bootstrap_ok'] and preflight['jvm']['status']=='pass' and all(preflight['inputs'].values()))
 except Exception:result['driver_error']=True
 return result
if __name__=='__main__':
 result=main();print('NACOS_TRACE='+json.dumps(result,sort_keys=True));sys.exit(0 if result['passed'] else 1)
