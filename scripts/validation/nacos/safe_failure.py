"""Fixed signal set only. Never return matched text, exceptions, paths or credentials."""
from pathlib import Path
import errno,os,stat
LIMIT=131072
SIGNALS={
 'jvm_initialization_failed':b'Error occurred during initialization of VM',
 'jvm_creation_failed':b'Could not create the Java Virtual Machine',
 'main_class_unavailable':b'Could not find or load main class',
 'native_link_failed':b'UnsatisfiedLinkError',
 'native_thread_creation_failed':b'Failed to start thread',
 'jar_unavailable':b'Unable to access jarfile',
 'jar_invalid':b'Invalid or corrupt jarfile',
 'permission_denied':b'Permission denied',
 'address_in_use':b'Address already in use',
 'java_version_mismatch':b'UnsupportedClassVersionError',
 'memory_exhausted':b'OutOfMemoryError',
 'class_missing':b'NoClassDefFoundError',
 'class_not_found':b'ClassNotFoundException',
 'dependency_initialization_failed':b'UnsatisfiedDependencyException',
 'bean_initialization_failed':b'BeanCreationException',
 'argument_invalid':b'IllegalArgumentException',
 'log_configuration_failed':b'Logback configuration error',
 'application_failed':b'APPLICATION FAILED TO START',
}
SOURCES={
 'launcher_0':('server-0.log',),
 'launcher_1':('server-1.log',),
 'launcher_2':('server-2.log',),
 'application_root':('logs','nacos.log'),
 'application_config':('logs','config-server.log'),
}
STATES=('unknown','missing','read_error','rejected','read')
SIZES=('unknown','empty','within_limit','tail_only')
def read_fixed(home,parts):
 fds=[]
 try:
  directory=os.open(home,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW);fds.append(directory)
  for part in parts[:-1]:
   directory=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW,dir_fd=directory);fds.append(directory)
  fd=os.open(parts[-1],os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=directory);fds.append(fd)
  metadata=os.fstat(fd)
  if not stat.S_ISREG(metadata.st_mode):return 'rejected','unknown',b''
  size=metadata.st_size
  data=os.pread(fd,LIMIT,max(0,size-LIMIT))
  return 'read',('empty' if size==0 else 'tail_only' if size>LIMIT else 'within_limit'),data
 except FileNotFoundError:return 'missing','unknown',b''
 except OSError as error:
  return ('rejected' if error.errno in (errno.ELOOP,errno.ENOTDIR) else 'read_error'),'unknown',b''
 finally:
  for fd in reversed(fds):
   try:os.close(fd)
   except OSError:pass
def summarize(home):
 result={'files_seen':0,'read_error':False,'tail_truncated':False,'signals':dict.fromkeys(SIGNALS,False),'sources':{}}
 for key,parts in SOURCES.items():
  state,size,data=read_fixed(home,parts)
  signals={key:marker in data for key,marker in SIGNALS.items()}
  result['sources'][key]={'state':state,'size':size,'signals':signals}
  result['files_seen']+=state=='read';result['read_error']|=state in ('read_error','rejected');result['tail_truncated']|=size=='tail_only'
  for signal,value in signals.items():result['signals'][signal]|=value
 return result

def sanitize_diagnostic(data):
 data=data if isinstance(data,dict) else {}
 diagnostic=data.get('diagnostics');diagnostic=diagnostic if isinstance(diagnostic,dict) else {}
 signals=diagnostic.get('signals');signals=signals if isinstance(signals,dict) else {}
 sources=sanitize_sources(diagnostic.get('sources'))
 stage=data.get('stage');exit_code=data.get('server_exit')
 return {
  'diagnostics':{'sources':sources,'files_seen':sum(v['state']=='read' for v in sources.values()),'read_error':any(v['state'] in ('read_error','rejected') for v in sources.values()),'tail_truncated':any(v['size']=='tail_only' for v in sources.values()),'signals':{key:any(v['signals'][key] for v in sources.values()) for key in SIGNALS}},
  'diagnostic_error':data.get('diagnostic_error') is True,
  'launch_inputs_readable':data.get('launch_inputs_readable') is True,
  'server_exit':exit_code if isinstance(exit_code,str) and exit_code in ('running','not_started','zero','one','two','killed','other') else 'unknown',
  'lifecycle_stage':stage if isinstance(stage,str) and stage in ('isolation','server_start','bootstrap','client_phase_one','server_restart','client_reconnect','complete') else 'unknown',
 }

def sanitize_sources(value):
 value=value if isinstance(value,dict) else {}
 output={}
 for key in SOURCES:
  source=value.get(key);source=source if isinstance(source,dict) else {}
  signals=source.get('signals');signals=signals if isinstance(signals,dict) else {}
  state=source.get('state');size=source.get('size')
  state=state if isinstance(state,str) and state in STATES else 'unknown'
  size=size if isinstance(size,str) and size in SIZES else 'unknown'
  if state=='read' and size=='unknown':state='unknown'
  if state!='read':size='unknown'
  output[key]={'state':state,'size':size,'signals':{k:state=='read' and signals.get(k) is True for k in SIGNALS}}
 return output

