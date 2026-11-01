"""Fixed signal set only. Never return matched text, exceptions, paths or credentials."""
from pathlib import Path
LIMIT=131072
SIGNALS={
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
def summarize(home):
 result={'files_seen':0,'read_error':False,'tail_truncated':False,'signals':dict.fromkeys(SIGNALS,False)}
 for name in ('server-0.log','server-1.log','server-2.log'):
  p=Path(home)/name
  try:
   with p.open('rb') as f:
    f.seek(0,2);size=f.tell();f.seek(max(0,size-LIMIT));data=f.read(LIMIT)
   result['files_seen']+=1;result['tail_truncated']|=size>LIMIT
   for key,marker in SIGNALS.items():result['signals'][key]|=marker in data
  except FileNotFoundError:pass
  except OSError:result['read_error']=True
 return result

def sanitize_diagnostic(data):
 data=data if isinstance(data,dict) else {}
 diagnostic=data.get('diagnostics');diagnostic=diagnostic if isinstance(diagnostic,dict) else {}
 signals=diagnostic.get('signals');signals=signals if isinstance(signals,dict) else {}
 count=diagnostic.get('files_seen')
 stage=data.get('stage');exit_code=data.get('server_exit')
 return {
  'diagnostics':{'files_seen':count if type(count) is int and 0<=count<=3 else 0,'read_error':diagnostic.get('read_error') is True,'tail_truncated':diagnostic.get('tail_truncated') is True,'signals':{key:signals.get(key) is True for key in SIGNALS}},
  'diagnostic_error':data.get('diagnostic_error') is True,
  'launch_inputs_readable':data.get('launch_inputs_readable') is True,
  'server_exit':exit_code if isinstance(exit_code,str) and exit_code in ('running','not_started','zero','one','two','killed','other') else 'unknown',
  'lifecycle_stage':stage if isinstance(stage,str) and stage in ('isolation','server_start','bootstrap','client_phase_one','server_restart','client_reconnect','complete') else 'unknown',
 }
