"""Bounded server-only comparison. Does not execute commands or emit arguments.

official-equivalent mirrors the official 2.5.1 standalone JVM arguments;
it does not claim to execute the distribution's shell startup wrapper.
"""
import argparse

def parse_options(args=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--server-only', action='store_true')
    parser.add_argument('--layered', action='store_true')
    parser.add_argument('--launch-mode', choices=('direct', 'official-equivalent'), default='direct')
    result = parser.parse_args(args)
    if result.launch_mode != 'direct' and not result.server_only:
        parser.error('comparison mode is limited to server-only diagnostics')
    if result.layered and (not result.server_only or result.launch_mode != 'direct'):
        parser.error('layered diagnostics require direct server-only mode')
    return result

def command(java, dist, home, mode):
    java, dist, home = str(java), str(dist), str(home)
    if mode == 'direct':
        return [java, '-Xms128m', '-Xmx512m', '-Dnacos.standalone=true',
                '-DembeddedStorage=true', '-Dnacos.server.ip=127.0.0.1', '-Dnacos.home='+home,
                '-Dloader.path='+dist+'/plugins', '-jar', dist+'/target/nacos-server.jar',
                '--spring.config.additional-location=file:'+home+'/conf/',
                '--logging.config='+home+'/conf/nacos-logback.xml']
    if mode != 'official-equivalent':
        raise ValueError('unknown launch mode')
    # Same official standalone defaults; keep the disposable home separate from
    # the read-only distribution. No caller-supplied JVM/environment arguments.
    return [java, '-Xms512m', '-Xmx512m', '-Xmn256m', '-Dnacos.standalone=true',
            '-Dnacos.member.list=',
            '-Xlog:gc*:file='+home+'/logs/nacos_gc.log:time,tags:filecount=10,filesize=100m',
            '-Dloader.path='+','.join(dist+'/plugins'+suffix for suffix in ('','/health','/cmdb','/selector')),
            '-Dnacos.home='+home, '-jar', dist+'/target/nacos-server.jar',
            '--spring.config.additional-location=file:'+home+'/conf/',
            '--logging.config='+home+'/conf/nacos-logback.xml',
            '--server.max-http-header-size=524288', 'nacos.nacos']
