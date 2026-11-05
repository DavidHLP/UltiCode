"""One direct server-only fixture; add only JVM class-load tracing."""
import runpy,sys
from pathlib import Path
import server_launch
original_command=server_launch.command
def traced_command(java,dist,home,mode):
 if mode!='direct':raise ValueError('trace is direct only')
 args=original_command(java,dist,home,mode)
 return [args[0],'-Xlog:class+load=info:file=/trace/server.log:none:filecount=0',*args[1:]]
if __name__=='__main__':
 server_launch.command=traced_command
 sys.argv=['real_nacos_test.py','--server-only','--launch-mode','direct','--layered']
 runpy.run_path(str(Path(__file__).with_name('real_nacos_test.py')),run_name='__main__')
