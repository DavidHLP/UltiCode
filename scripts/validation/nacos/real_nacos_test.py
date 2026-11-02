#!/usr/bin/env python3
"""Disposable authenticated Nacos test; run only inside a private network namespace."""
from pathlib import Path
from safe_failure import summarize
from server_launch import command, parse_options
from layered_preflight import jvm_probe, input_probe
import base64,json,os,secrets,shutil,signal,subprocess,tempfile,time,urllib.request,urllib.parse,urllib.error
OPTIONS=parse_options()
ROOT=Path(__file__).resolve().parent
JAVA=Path('/usr/lib/jvm/java-17-openjdk-amd64/bin/java')
DIST=ROOT/'server-dist/nacos'
result={'scope':'disposable private-network Nacos 2.5.1, not production','passed':False,
        'server_only':OPTIONS.server_only,'launch_mode':OPTIONS.launch_mode}
server=client=None
result['stage']='isolation'
logs=[]
def stop(process):
 if process is not None and process.poll() is None:
  process.terminate()
  try:process.wait(timeout=10)
  except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
def interrupted(signum,frame):raise RuntimeError('test process interrupted')
signal.signal(signal.SIGTERM,interrupted)
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def request(path,values=None):
 data=urllib.parse.urlencode(values).encode() if values is not None else None
 try:
  with opener.open('http://127.0.0.1:8848/nacos'+path,data,timeout=2) as response:return response.status,response.read(16384)
 except urllib.error.HTTPError as error:return error.code,b''
def ready(process):
 end=time.monotonic()+100
 while time.monotonic()<end:
  if process.poll() is not None:raise RuntimeError('disposable server exited')
  try:
   if request('/v1/console/health/readiness')[0]==200:return
  except OSError:pass
  time.sleep(.5)
 raise RuntimeError('disposable server readiness timeout')
try:
 subprocess.run(['ip','link','set','lo','up'],check=True,timeout=5,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 with tempfile.TemporaryDirectory(prefix='nacos-live-') as tmp:
  home=Path(tmp);(home/'logs').mkdir();shutil.copytree(DIST/'conf',home/'conf')
  password=secrets.token_urlsafe(30)
  configuration='\nserver.address=127.0.0.1\nnacos.core.auth.enabled=true\nnacos.core.auth.system.type=nacos\nnacos.core.auth.caching.enabled=false\nnacos.core.auth.server.identity.key=fixtureIdentity\nnacos.core.auth.server.identity.value='+secrets.token_urlsafe(24)+'\nnacos.core.auth.plugin.nacos.token.secret.key='+base64.b64encode(secrets.token_bytes(48)).decode()+'\n'
  with (home/'conf/application.properties').open('a') as out:out.write(configuration)
  (home/'conf/application.properties').chmod(0o600)
  credentials=home/'client.properties';credentials.write_text('serverAddr=127.0.0.1:8848\nusername=nacos\npassword='+password+'\n');credentials.chmod(0o600)
  def start_server():
   log=(home/('server-'+str(len(logs))+'.log')).open('w');logs.append(log)
   return subprocess.Popen(command(JAVA,DIST,home,OPTIONS.launch_mode),stdout=log,stderr=subprocess.STDOUT)
  try:
   if OPTIONS.layered:
    result['stage']='jvm_probe';result['preflight']={'jvm':jvm_probe(JAVA)}
    if result['preflight']['jvm']['status']!='pass':raise RuntimeError('preflight failed')
    result['stage']='input_probe';result['preflight']['inputs']=input_probe(DIST,home)
    if not all(result['preflight']['inputs'].values()):raise RuntimeError('preflight failed')
   result['stage']='server_start'
   result['launch_inputs_readable']=JAVA.is_file() and os.access(JAVA,os.X_OK) and (DIST/'target/nacos-server.jar').is_file() and os.access(DIST/'target/nacos-server.jar',os.R_OK)
   server=start_server();ready(server);result['server_started']=True
   result['stage']='bootstrap'
   status,_=request('/v1/auth/users/admin',{'password':password})
   result['admin_bootstrap_http']=status
   if status!=200:raise RuntimeError('disposable admin bootstrap failed')
   if OPTIONS.server_only:
    result['passed']=True;result['stage']='complete'
   else:
    log=(home/'client.log').open('w');logs.append(log)
    cp=str(ROOT)+':'+(ROOT/'auth-packaged-classpath.txt').read_text()
    result['stage']='client_phase_one'
    client=subprocess.Popen([str(JAVA),'-Xmx256m','-Duser.home='+str(home),'-Dcom.google.protobuf.error_on_unsafe_pre22_gencode=true','-cp',cp,'RealNamingProbe',str(credentials),str(home)],stdout=log,stderr=subprocess.STDOUT)
    end=time.monotonic()+80
    while not (home/'phase-one').exists():
     if client.poll() is not None:raise RuntimeError('client failed before restart')
     if time.monotonic()>end:raise RuntimeError('client phase one timeout')
     time.sleep(.2)
    result['authenticated_register_subscribe_push']=True
    result['stage']='server_restart'
    stop(server);server=None;time.sleep(2)
    server=start_server();ready(server);(home/'server-restarted').write_text('ready')
    result['stage']='client_reconnect'
    client.wait(timeout=120)
    if client.returncode!=0:raise RuntimeError('client reconnect phase failed')
    result['restart_reconnect_reregister_deregister']=True;result['passed']=True;result['stage']='complete'
  finally:
   try:
    if not result['passed']:
     for log in logs:log.flush()
     result['diagnostics']=summarize(home)
     code=server.poll() if server is not None else None
     result['server_exit']='running' if code is None and server is not None else ('not_started' if server is None else ({0:'zero',1:'one',2:'two',137:'killed',-9:'killed'}.get(code,'other')))
   except Exception:
    result['diagnostic_error']=True
   finally:
    try:stop(client)
    finally:
     try:stop(server)
     finally:
      for log in logs:log.close()
except Exception as error:
 result['passed']=False
 result['failure']='fixture_failed'
 result['failure_kind']='timeout' if isinstance(error,subprocess.TimeoutExpired) else 'runtime'
finally:
 (ROOT/'real-nacos-result.json').write_text(json.dumps(result,indent=2)+'\n')
# Raw logs and exception text never leave the private temporary directory.
print(json.dumps(result))
raise SystemExit(0 if result['passed'] else 1)

