#!/usr/bin/env python3
"""Stage only public test inputs; no repository tokens/configuration are copied."""
from pathlib import Path
import hashlib,io,json,shutil,sys,tarfile,zipfile
def select_boot(target):
 boots=[]
 for candidate in target.glob('*.jar'):
  with zipfile.ZipFile(candidate) as candidate_zip:
   if any(x.startswith('BOOT-INF/lib/') for x in candidate_zip.namelist()):boots.append(candidate)
 if len(boots)!=1:raise RuntimeError('missing or ambiguous Boot artifact')
 return boots[0]
repo=Path.cwd(); work=Path(sys.argv[1]); n=work/'nacos';n.mkdir(parents=True)
for p in (repo/'scripts/validation/nacos').iterdir():
 if p.suffix in ('.java','.py'):shutil.copyfile(p,n/p.name)
archive=Path(sys.argv[2]);expected='e0877b8e5e8f234694f595a0f25b978c3745325e8b17625b7ab95661e5d3d38e'
if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise RuntimeError('server digest mismatch')
with tarfile.open(archive) as t:t.extractall(n/'server-dist',filter='data')
module=repo/'services/platform/nacos-api-compat/target/nacos-api-compat-2.0.0.jar'
with zipfile.ZipFile(module) as z: expected_classes={p for p in z.namelist() if p.startswith('com/alibaba/nacos/api/') and p.endswith('.class')}
module_sha=hashlib.sha256(module.read_bytes()).hexdigest(); inventory=[];lib=n/'libraries';lib.mkdir()
for service,path in {'auth':'auth','admin':'admin','app':'app/app-web','submission':'submission','notification':'notification','judge':'judge'}.items():
 boot=select_boot(repo/'services'/path/'target'); cp=[];providers={};names=[]
 with zipfile.ZipFile(boot) as b:
  for name in b.namelist():
   if name.startswith('BOOT-INF/classes/com/alibaba/nacos/api/'):raise RuntimeError('class overlay')
   if not(name.startswith('BOOT-INF/lib/') and name.endswith('.jar')):continue
   short=name.rsplit('/',1)[-1];names.append(short);data=b.read(name);sha=hashlib.sha256(data).hexdigest()
   if short.startswith('nacos-api-') and short!='nacos-api-compat-2.0.0.jar':raise RuntimeError('original API')
   if short.startswith('nacos-client-') and '-pure.jar' not in short:raise RuntimeError('shaded client')
   if short=='nacos-api-compat-2.0.0.jar' and sha!=module_sha:raise RuntimeError('module mismatch')
   dest=lib/(sha+'-'+short)
   if not dest.exists():dest.write_bytes(data)
   cp.append('/work/nacos/libraries/'+dest.name)
   with zipfile.ZipFile(io.BytesIO(data)) as z:
    if len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('duplicate entries')
    for p in z.namelist():
     if p.startswith('com/alibaba/nacos/api/') and p.endswith('.class'):providers.setdefault(p,[]).append(short)
 if set(providers)!=expected_classes or any(x!=['nacos-api-compat-2.0.0.jar'] for x in providers.values()):raise RuntimeError('API identity mismatch')
 if 'protobuf-java-4.35.1.jar' not in names or any(not x.endswith('-1.83.1.jar') for x in names if x.startswith('grpc-')):raise RuntimeError('runtime drift')
 (n/(service+'.classpath')).write_text(':'.join(cp))
 inventory.append({'service':service,'boot_sha256':hashlib.sha256(boot.read_bytes()).hexdigest(),'module_sha256':module_sha,'api_classes':len(providers)})
(n/'inventory.json').write_text(json.dumps(inventory))
# Writable private scratch only; container runs root without DAC_OVERRIDE.
n.chmod(0o777)
