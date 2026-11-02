"""Offline only: all subprocess/server activity mocked; synthetic fixture secrets."""
import base64,contextlib,io,json,os,subprocess,tempfile,unittest,zipfile,runpy,shutil,sys
from pathlib import Path
from unittest.mock import patch
import layered_preflight as pre
import layered_driver as driver
import server_launch

SECRET='SYNTHETIC_DO_NOT_EMIT_abcdefgh'
class Tests(unittest.TestCase):
    def test_jvm_good_major(self):
        def run(args,**kw):
            self.assertEqual(args[1:],['-version']);self.assertEqual(kw['timeout'],10)
            kw['stdout'].write(b'openjdk version "17.0.21"\n'+SECRET.encode())
            return subprocess.CompletedProcess([],0)
        with tempfile.TemporaryDirectory() as td:
            java=Path(td)/'java';java.touch();java.chmod(0o700)
            result=pre.jvm_probe(java,run)
            self.assertEqual(result['status'],'pass');self.assertNotIn(SECRET,json.dumps(result))
    def test_jvm_failure_timeout_and_unbounded_output(self):
        with tempfile.TemporaryDirectory() as td:
            java=Path(td)/'java';java.touch();java.chmod(0o700)
            for case in ('wrong','nonzero','timeout','oversized','error'):
                def run(args,**kw):
                    if case=='timeout':raise subprocess.TimeoutExpired(SECRET,10)
                    if case=='error':raise RuntimeError(SECRET)
                    kw['stdout'].write(b'openjdk version "'+(b'21' if case=='wrong' else b'17')+b'"\n')
                    if case=='oversized':kw['stdout'].write(b'X'*65537)
                    return subprocess.CompletedProcess([],1 if case=='nonzero' else 0)
                result=pre.jvm_probe(java,run)
                self.assertNotEqual(result['status'],'pass');self.assertNotIn(SECRET,json.dumps(result))
    def test_jvm_missing(self):
        self.assertEqual(pre.jvm_probe('/nonexistent/java')['status'],'not_executable')
    def fixtures(self,root):
        dist=root/'dist';home=root/'home';(dist/'target').mkdir(parents=True);(home/'conf').mkdir(parents=True)
        with zipfile.ZipFile(dist/'target/nacos-server.jar','w') as z:
            z.writestr('META-INF/MANIFEST.MF','Main-Class: org.example.Main\r\n')
            z.writestr('org/example/Main.class',b'fake')
        props={'server.address':'127.0.0.1','nacos.core.auth.enabled':'true','nacos.core.auth.system.type':'nacos',
               'nacos.core.auth.server.identity.key':SECRET,'nacos.core.auth.server.identity.value':SECRET,
               'nacos.core.auth.plugin.nacos.token.secret.key':base64.b64encode(b'X'*48).decode()}
        (home/'conf/application.properties').write_text('\n'.join(k+'='+v for k,v in props.items()))
        (home/'conf/nacos-logback.xml').write_text('<configuration/>')
        return dist,home
    def test_inputs_all_pass_and_no_secret(self):
        with tempfile.TemporaryDirectory() as td:
            dist,home=self.fixtures(Path(td));result=pre.input_probe(dist,home)
            self.assertTrue(all(result.values()));self.assertNotIn(SECRET,json.dumps(result))
    def test_missing_jar_and_corrupt_jar(self):
        with tempfile.TemporaryDirectory() as td:
            dist,home=self.fixtures(Path(td));jar=dist/'target/nacos-server.jar'
            for content in (b'garbage',None):
                if content is None:jar.unlink()
                else:jar.write_bytes(content)
                result=pre.input_probe(dist,home)
                self.assertFalse(result['jar_structure']);self.assertFalse(result['main_class_present'])
    def test_manifest_main_class_absent(self):
        with tempfile.TemporaryDirectory() as td:
            dist,home=self.fixtures(Path(td))
            with zipfile.ZipFile(dist/'target/nacos-server.jar','w') as z:z.writestr('META-INF/MANIFEST.MF','Main-Class: nope.Main\n')
            self.assertFalse(pre.input_probe(dist,home)['main_class_present'])
    def test_invalid_config_and_xml_not_echoed(self):
        with tempfile.TemporaryDirectory() as td:
            dist,home=self.fixtures(Path(td))
            for content in ('<!DOCTYPE x><configuration/>','<configuration>'+SECRET):
                (home/'conf/nacos-logback.xml').write_text(content)
                result=pre.input_probe(dist,home);self.assertFalse(result['config_shape']);self.assertNotIn(SECRET,json.dumps(result))
    def test_output_schema_is_closed(self):
        for junk in (None,SECRET,[],{'jvm':{'status':SECRET,'major_matches':1},'inputs':{SECRET:True},SECRET:SECRET}):
            result=pre.sanitize(junk);self.assertNotIn(SECRET,json.dumps(result));self.assertFalse(result['jvm']['major_matches'])
            self.assertEqual(set(result['inputs']),set(pre.INPUT_KEYS))
    def good(self):
        return {'server_only':True,'launch_mode':'direct','server_started':True,'admin_bootstrap_http':200,
                'passed':True,'stage':'complete','preflight':{'jvm':{'status':'pass','major_matches':True,'output_bounded':True},
                'inputs':dict.fromkeys(pre.INPUT_KEYS,True)}}
    def invoke(self,data,code=0,exception=False):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);p=root/'real-nacos-result.json';p.write_text(json.dumps(self.good()))
            calls=[];out=io.StringIO()
            def run(args,**kw):
                self.assertFalse(p.exists());calls.append(args)
                if exception:raise subprocess.TimeoutExpired(SECRET,195)
                if data is not None:p.write_text(json.dumps(data))
                return subprocess.CompletedProcess([],code)
            with patch.object(driver,'ROOT',root),contextlib.redirect_stdout(out):result=driver.main(run)
            self.assertEqual(len(calls),1);self.assertEqual(calls[0][-1],'--layered')
            return result,json.loads(out.getvalue().split('=',1)[1])
    def test_single_attempt_success(self):
        code,result=self.invoke(self.good());self.assertEqual(code,0);self.assertTrue(result['passed'])
    def test_missing_result_does_not_reuse_stale_success(self):
        code,result=self.invoke(None);self.assertEqual(code,1);self.assertFalse(result['passed'])
    def test_nonzero_timeout_wrong_mode_no_bootstrap(self):
        for data,code,exc in ((self.good(),1,False),(None,0,True),
            ({**self.good(),'launch_mode':'official-equivalent'},0,False),
            ({**self.good(),'admin_bootstrap_http':401},0,False),
            ({**self.good(),'preflight':{}},0,False)):
            result,out=self.invoke(data,code,exc);self.assertEqual(result,1);self.assertFalse(out['passed'])
    def test_arbitrary_fields_and_exception_not_exported(self):
        data={**self.good(),SECRET:SECRET,'failure':SECRET,'diagnostics':{'signals':{SECRET:True},'sources':{SECRET:SECRET}}}
        code,result=self.invoke(data);self.assertNotIn(SECRET,json.dumps(result))
    def test_layered_option_limited_to_direct_server_only(self):
        for args in (['--layered'],['--layered','--server-only','--launch-mode','official-equivalent']):
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):server_launch.parse_options(args)
        self.assertTrue(server_launch.parse_options(['--layered','--server-only']).layered)
    def test_layered_fixture_stops_before_server_when_preflight_fails(self):
        here=Path(__file__).resolve().parent
        for jvm_ok in (False,True):
            with tempfile.TemporaryDirectory() as td:
                root=Path(td);dist,home=self.fixtures(root)
                target=root/'server-dist/nacos';target.parent.mkdir();shutil.copytree(dist,target)
                shutil.copytree(home/'conf',target/'conf')
                shutil.copyfile(here/'real_nacos_test.py',root/'real_nacos_test.py')
                output=io.StringIO()
                with patch.object(sys,'argv',[str(root/'real_nacos_test.py'),'--server-only','--layered']), \
                     patch('subprocess.run'),patch('subprocess.Popen') as popen, \
                     patch('layered_preflight.jvm_probe',return_value={'status':'pass' if jvm_ok else 'nonzero','major_matches':True,'output_bounded':True}), \
                     patch('layered_preflight.input_probe',return_value=dict.fromkeys(pre.INPUT_KEYS,False)), \
                     contextlib.redirect_stdout(output),self.assertRaises(SystemExit):
                    runpy.run_path(str(root/'real_nacos_test.py'),run_name='__main__')
                popen.assert_not_called()
                result=json.loads((root/'real-nacos-result.json').read_text())
                self.assertEqual(result['stage'],'input_probe' if jvm_ok else 'jvm_probe')
                self.assertFalse(result['passed']);self.assertNotIn(SECRET,output.getvalue())

    def test_forged_pass_with_wrong_major_never_passes(self):
        data=self.good();data['preflight']['jvm']['major_matches']=False
        code,result=self.invoke(data);self.assertEqual(code,1);self.assertFalse(result['passed'])

if __name__=='__main__':unittest.main()
