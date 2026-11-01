#!/usr/bin/env python3
"""Disposable authenticated Nacos test; run only inside a private network namespace."""
from pathlib import Path
import base64,json,os,secrets,shutil,signal,subprocess,tempfile,time,urllib.request,urllib.parse,urllib.error
ROOT=Path(__file__).resolve().parent
JAVA=Path('/usr/lib/jvm/java-17-openjdk-amd64/bin/java')
DIST=ROOT/'server-dist/nacos'
result={'scope':'disposable private-network Nacos 2.5.1, not production','passed':False}
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
   return subprocess.Popen([str(JAVA),'-Xms128m','-Xmx512m','-Dnacos.standalone=true','-DembeddedStorage=true','-Dnacos.home='+str(home),'-Dloader.path='+str(DIST/'plugins'),'-jar',str(DIST/'target/nacos-server.jar'),'--spring.config.additional-location=file:'+str(home/'conf')+'/','--logging.config='+str(home/'conf/nacos-logback.xml')],stdout=log,stderr=subprocess.STDOUT)
  try:
   result['stage']='server_start'
   server=start_server();ready(server);result['server_started']=True
   result['stage']='bootstrap'
   status,_=request('/v1/auth/users/admin',{'password':password})
   result['admin_bootstrap_http']=status
   if status!=200:raise RuntimeError('disposable admin bootstrap failed')
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
