import ast,json,tempfile,unittest
from pathlib import Path
from safe_failure import summarize,SIGNALS,LIMIT,sanitize_diagnostic
class SafeDiagnostics(unittest.TestCase):
 def test_empty(self):
  with tempfile.TemporaryDirectory() as d:
   x=summarize(d);self.assertEqual(x['files_seen'],0);self.assertFalse(any(x['signals'].values()))
 def test_malicious_text_never_copied(self):
  with tempfile.TemporaryDirectory() as d:
   secret='URL=https://alice:privateCanary@evil.example/ token=privateCanary java.syntheticPrivateCanaryException'
   (Path(d)/'server-0.log').write_text(secret+'\nBeanCreationException\n')
   out=json.dumps(summarize(d));self.assertNotIn('privateCanary',out);self.assertNotIn('evil.example',out)
   self.assertTrue(summarize(d)['signals']['bean_initialization_failed'])
 def test_tail_limit(self):
  with tempfile.TemporaryDirectory() as d:
   (Path(d)/'server-0.log').write_bytes(b'ClassNotFoundException'+b'x'*(LIMIT+1))
   x=summarize(d);self.assertTrue(x['tail_truncated']);self.assertFalse(x['signals']['class_not_found'])
 def test_read_error(self):
  with tempfile.TemporaryDirectory() as d:
   (Path(d)/'server-0.log').mkdir();self.assertTrue(summarize(d)['read_error'])
 def test_all_markers_boolean_only(self):
  with tempfile.TemporaryDirectory() as d:
   (Path(d)/'server-0.log').write_bytes(b'\n'.join(SIGNALS.values()))
   x=summarize(d);self.assertTrue(all(type(v) is bool and v for v in x['signals'].values()))
 def test_diagnostic_exception_keeps_both_cleanup_paths(self):
  tree=ast.parse((Path(__file__).parent/'real_nacos_test.py').read_text())
  final=next(n.finalbody for n in ast.walk(tree) if isinstance(n,ast.Try) and n.finalbody and isinstance(n.finalbody[0],ast.Try) and any(isinstance(x,ast.Assign) and any(isinstance(t,ast.Subscript) and isinstance(t.slice,ast.Constant) and t.slice.value=='diagnostic_error' for t in x.targets) for x in ast.walk(n.finalbody[0])))
  calls=[]
  class BadLog:
   def flush(self):raise OSError('privateCanary')
   def close(self):calls.append('close')
  scope={'result':{'passed':False},'logs':[BadLog()],'client':'client','server':'server','stop':calls.append}
  exec(compile(ast.Module(body=final,type_ignores=[]),'cleanup','exec'),scope)
  self.assertEqual(calls,['client','server','close']);self.assertIs(scope['result']['diagnostic_error'],True)
  self.assertNotIn('privateCanary',json.dumps(scope['result']))
 def test_malformed_report_types(self):
  for value in (None,[],{'diagnostics':None},{'diagnostics':[]},{'diagnostics':{'signals':None}},{'diagnostics':{'signals':[]}}, {'stage':[], 'server_exit':{}}, {'diagnostics':{'files_seen':True}}):
   x=sanitize_diagnostic(value);self.assertEqual(x['diagnostics']['files_seen'],0);self.assertEqual(x['server_exit'],'unknown')
 def test_unlisted_fields_not_echoed(self):
  x=sanitize_diagnostic({'stage':'privateCanary','server_exit':'privateCanary','diagnostics':{'signals':{'privateCanary':True},'privateCanary':'secret'}})
  self.assertNotIn('privateCanary',json.dumps(x))
if __name__=='__main__':unittest.main()

