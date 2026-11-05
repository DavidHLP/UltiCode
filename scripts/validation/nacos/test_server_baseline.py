"""Offline command/lifecycle contracts; never start Java or contact services."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import server_launch
import safe_failure
import server_baseline_matrix

class Reply:
    status = 200
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, count): return b'{}'

class Opener:
    def __init__(self): self.urls = []
    def open(self, url, data=None, timeout=None):
        self.urls.append(url)
        return Reply()

class Process:
    def __init__(self, failed=False): self.failed = failed; self.stopped = False
    def poll(self): return 1 if self.failed else (0 if self.stopped else None)
    def terminate(self): self.stopped = True
    def wait(self, timeout=None): return 0

class BaselineTests(unittest.TestCase):
    def test_default_preserves_lifecycle_direct_mode(self):
        args = server_launch.parse_options([])
        self.assertFalse(args.server_only)
        self.assertEqual(args.launch_mode, 'direct')

    def test_workflow_keeps_isolation_and_only_selects_server_matrix(self):
        workflow=(HERE.parents[2]/'.github/workflows/nacos-compat-validation.yml').read_text()
        for required in ('--network none --cap-drop ALL --cap-add NET_ADMIN',
                         '--security-opt no-new-privileges --memory 3g --cpus 2 --pids-limit 256',
                         '--tmpfs /tmp:rw,nosuid,nodev,size=512m',
                         '--env HOME=/tmp "$image" python3 /work/nacos/trace_driver.py',
                         'persist-credentials: false'):
            self.assertIn(required,workflow)
        self.assertNotIn('upload-artifact@',workflow)

    def test_invalid_mode_and_unbounded_combination_rejected(self):
        for args in (['--launch-mode','external'], ['--launch-mode','official-equivalent']):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                server_launch.parse_options(args)

    def test_direct_command_adds_only_explicit_loopback_ip(self):
        self.assertEqual(server_launch.command('/j','/d','/h','direct'),[
            '/j','-Xms128m','-Xmx512m','-Dnacos.standalone=true','-DembeddedStorage=true',
            '-Dnacos.server.ip=127.0.0.1',
            '-Dnacos.home=/h','-Dloader.path=/d/plugins','-jar','/d/target/nacos-server.jar',
            '--spring.config.additional-location=file:/h/conf/','--logging.config=/h/conf/nacos-logback.xml'])

    def test_official_equivalent_matches_actual_official_shell_arguments(self):
        # Run the inspected official script with a fake java executable. It
        # records argv, never starts a JVM or contacts any service.
        source = HERE/'fixtures/nacos-2.5.1-startup.sh'
        with tempfile.TemporaryDirectory() as td:
            base=Path(td)/'nacos';(base/'bin').mkdir(parents=True)
            script=base/'bin/startup.sh';shutil.copyfile(source,script)
            java_home=Path(td)/'jdk';(java_home/'bin').mkdir(parents=True)
            java=java_home/'bin/java';capture=Path(td)/'argv.json'
            java.write_text('#!'+sys.executable+'\nimport sys,json,os\n'
                'if sys.argv[1:]==["-version"]:\n print(\'openjdk version "17.0.21"\',file=sys.stderr)\n'
                'else:\n target=os.environ["CAPTURE"]; temporary=target+".tmp"\n'
                ' with open(temporary,"w") as output: output.write(json.dumps(sys.argv))\n'
                ' os.replace(temporary,target)\n')
            java.chmod(0o700)
            env={'PATH':os.defpath,'HOME':td,'JAVA_HOME':str(java_home),'CAPTURE':str(capture)}
            p=subprocess.run(['bash',str(script),'-m','standalone'],env=env,
                             stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5)
            self.assertEqual(p.returncode,0)
            until=time.monotonic()+3
            while not capture.exists() and time.monotonic()<until:time.sleep(.01)
            actual=json.loads(capture.read_text())
            self.assertEqual(actual,server_launch.command(java,base,base,'official-equivalent'))

    def test_command_argv_has_no_secret_or_environment_interpolation(self):
        with patch.dict(os.environ, {'JAVA_OPT_EXT':'secret-injection', 'JAVA_TOOL_OPTIONS':'secret-injection'}):
            for mode in ('direct','official-equivalent'):
                result = server_launch.command('/j','/d','/h',mode)
                self.assertNotIn('secret-injection', repr(result))
                self.assertEqual(result.count('-jar'),1)
                self.assertIn('-Dnacos.standalone=true',result)

    def run_fixture(self, mode, failed=False):
        # Copy the executable fixture into a throwaway synthetic distribution.
        # Mock every subprocess and HTTP operation before invoking its top level.
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            for filename in ('real_nacos_test.py',): shutil.copyfile(HERE/filename, root/filename)
            conf=root/'server-dist/nacos/conf';conf.mkdir(parents=True)
            (conf/'application.properties').write_text('')
            (conf/'nacos-logback.xml').write_text('<configuration/>')
            process=Process(failed); opener=Opener(); commands=[]; output=io.StringIO()
            def popen(command, **kwargs): commands.append(command); return process
            with patch.object(sys,'argv',[str(root/'real_nacos_test.py'),'--server-only','--launch-mode',mode]), \
                 patch('subprocess.run') as run, patch('subprocess.Popen',side_effect=popen), \
                 patch('urllib.request.build_opener',return_value=opener), \
                 patch('secrets.token_urlsafe',return_value='NEVER_EMIT_SECRET'), \
                 patch('secrets.token_bytes',return_value=b'X'*48), \
                 contextlib.redirect_stdout(output), self.assertRaises(SystemExit) as raised:
                runpy.run_path(str(root/'real_nacos_test.py'),run_name='__main__')
            data=json.loads((root/'real-nacos-result.json').read_text())
            self.assertEqual(len(commands),1)  # No SDK client process.
            self.assertFalse(any('RealNamingProbe' in arg for arg in commands[0]))
            self.assertEqual(run.call_args.args[0],['ip','link','set','lo','up'])
            self.assertTrue(all(url.startswith('http://127.0.0.1:8848/nacos/') for url in opener.urls))
            self.assertNotIn('NEVER_EMIT_SECRET',output.getvalue())
            home=next(arg.split('=',1)[1] for arg in commands[0] if arg.startswith('-Dnacos.home='))
            self.assertFalse(Path(home).exists())  # Sensitive scratch removed.
            return data,raised.exception.code,process,opener

    def test_direct_baseline_stops_after_bootstrap(self):
        data,code,process,opener=self.run_fixture('direct')
        self.assertEqual(code,0);self.assertTrue(data['passed']);self.assertTrue(process.stopped)
        self.assertTrue(data['server_only']);self.assertEqual(data['stage'],'complete')
        self.assertEqual(data['admin_bootstrap_http'],200)
        self.assertFalse(data.get('authenticated_register_subscribe_push',False))
        self.assertEqual(len(opener.urls),2)

    def test_official_equivalent_baseline_stops_after_bootstrap(self):
        data,code,process,opener=self.run_fixture('official-equivalent')
        self.assertEqual(code,0);self.assertTrue(process.stopped)
        self.assertEqual(data['launch_mode'],'official-equivalent')

    def test_failed_server_has_no_bootstrap_or_client(self):
        data,code,process,opener=self.run_fixture('direct',True)
        self.assertEqual(code,1);self.assertFalse(data['passed'])
        self.assertEqual(data['stage'],'server_start');self.assertEqual(opener.urls,[])
        self.assertEqual(data['server_exit'],'one')

    def test_jvm_signal_whitelist_never_returns_text(self):
        with tempfile.TemporaryDirectory() as td:
            home=Path(td)
            for signal, marker in safe_failure.SIGNALS.items():
                (home/'server-0.log').write_bytes(marker+b' PRIVATE_SECRET')
                result=safe_failure.summarize(home)
                self.assertTrue(result['signals'][signal])
                self.assertNotIn('PRIVATE_SECRET',json.dumps(result))
                sanitized=safe_failure.sanitize_diagnostic({'diagnostics':result})
                self.assertTrue(sanitized['diagnostics']['signals'][signal])

    def test_unknown_log_retains_unknown_cause(self):
        with tempfile.TemporaryDirectory() as td:
            home=Path(td);(home/'server-0.log').write_text('unclassified private diagnostic')
            result=safe_failure.summarize(home)
            self.assertFalse(any(result['signals'].values()))
            self.assertEqual(result['sources']['launcher_0']['state'],'read')
            self.assertNotIn('unclassified',json.dumps(result))

    def run_matrix(self, behavior):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);seen=[];out=io.StringIO()
            def run(args, **kwargs):
                mode=args[-1];seen.append(mode)
                self.assertFalse((root/'real-nacos-result.json').exists())
                return behavior(root,mode)
            with patch.object(server_baseline_matrix,'ROOT',root),contextlib.redirect_stdout(out):
                code=server_baseline_matrix.main(run)
            return code,json.loads(out.getvalue().split('=',1)[1]),seen

    def good(self,root,mode):
        (root/'real-nacos-result.json').write_text(json.dumps({'server_only':True,
            'launch_mode':mode,'server_started':True,'admin_bootstrap_http':200,
            'passed':True,'stage':'complete','untrusted':'PRIVATE_SECRET'}))
        return subprocess.CompletedProcess([],0)

    def test_matrix_success_requires_both_modes_and_sanitizes(self):
        code,data,seen=self.run_matrix(self.good)
        self.assertEqual(code,0);self.assertTrue(data['passed'])
        self.assertEqual(seen,['direct','official-equivalent'])
        self.assertNotIn('PRIVATE_SECRET',json.dumps(data))

    def test_matrix_missing_result_does_not_reuse_prior_success(self):
        def behavior(root,mode):
            if mode=='direct': return self.good(root,mode)
            return subprocess.CompletedProcess([],0)
        code,data,seen=self.run_matrix(behavior)
        self.assertEqual(code,1);self.assertFalse(data['results'][1]['passed'])

    def test_matrix_nonzero_exit_cannot_pass_from_json(self):
        def behavior(root,mode):
            self.good(root,mode);return subprocess.CompletedProcess([],1)
        code,data,seen=self.run_matrix(behavior)
        self.assertEqual(code,1);self.assertFalse(data['passed'])

    def test_matrix_timeout_continues_comparison_safely(self):
        def behavior(root,mode): raise subprocess.TimeoutExpired('fixed',195)
        code,data,seen=self.run_matrix(behavior)
        self.assertEqual(code,1);self.assertEqual(len(seen),2)

    def test_matrix_wrong_mode_or_no_bootstrap_never_passes(self):
        for change in ({'launch_mode':'wrong'},{'server_only':False},{'admin_bootstrap_http':401}):
            def behavior(root,mode):
                self.good(root,mode)
                p=root/'real-nacos-result.json';data=json.loads(p.read_text());data.update(change)
                p.write_text(json.dumps(data));return subprocess.CompletedProcess([],0)
            code,data,seen=self.run_matrix(behavior)
            self.assertEqual(code,1);self.assertFalse(data['passed'])

if __name__ == '__main__': unittest.main()

