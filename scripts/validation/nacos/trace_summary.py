"""Fixed class observations only; no raw data, paths, messages or arbitrary names."""
import os, stat
LIMIT = 8 * 1024 * 1024
CLASSES = {
 'java_object': b'java.lang.Object',
 'boot_properties_launcher': b'org.springframework.boot.loader.PropertiesLauncher',
 'nacos_entry_class': b'com.alibaba.nacos.Nacos',
 'spring_application': b'org.springframework.boot.SpringApplication',
 'spring_logging_system': b'org.springframework.boot.logging.LoggingSystem',
 'nacos_starting_listener': b'com.alibaba.nacos.core.listener.StartingApplicationListener',
 'synthetic_canary': b'TraceCanary',
}
def summarize(path):
 result={'read_state':'unknown','event_coverage':'unknown','snapshot_read_complete':False,'class_observed':dict.fromkeys(CLASSES,False)}
 fd=None
 try:
  fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
  meta=os.fstat(fd)
  if not stat.S_ISREG(meta.st_mode):
   result['read_state']='rejected';return result
  chunks=[];remaining=LIMIT
  while remaining:
   block=os.read(fd,min(65536,remaining))
   if not block:break
   chunks.append(block);remaining-=len(block)
  raw=b''.join(chunks)
  # Snapshot bytes only: tmpfs exhaustion can omit complete JVM records.
  # Event coverage is always unknown; missing observations are not absence.
  result['snapshot_read_complete']=bool(meta.st_size<LIMIT and len(raw)==meta.st_size and raw.endswith(b'\n'))
  lines=raw.split(b'\n')[:-1]
  for key,name in CLASSES.items():result['class_observed'][key]=any(line.startswith(name+b' source: ') for line in lines)
  result['read_state']='read'
 except FileNotFoundError:result['read_state']='missing'
 except OSError:result['read_state']='read_error'
 finally:
  if fd is not None:
   try:os.close(fd)
   except OSError:result['read_state']='read_error';result['snapshot_read_complete']=False
 return result
