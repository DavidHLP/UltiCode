import ast,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import safe_failure
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
 def test_application_logs_only(self):
  for filename,key in [('nacos.log','application_root'),('config-server.log','application_config')]:
   with tempfile.TemporaryDirectory() as d:
    logs=Path(d)/'logs';logs.mkdir();(logs/filename).write_text('BeanCreationException privateCanary https://alice:secret@example.test/')
    x=summarize(d);self.assertTrue(x['sources'][key]['signals']['bean_initialization_failed']);self.assertEqual(x['sources']['launcher_0']['state'],'missing')
    self.assertNotIn('privateCanary',json.dumps(x));self.assertNotIn('example.test',json.dumps(x))
 def test_file_symlink_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   secret=Path(d)/'secret';secret.write_text('BeanCreationException')
   (Path(d)/'server-0.log').symlink_to(secret)
   x=summarize(d);self.assertEqual(x['sources']['launcher_0']['state'],'rejected');self.assertFalse(x['signals']['bean_initialization_failed'])
 def test_logs_directory_symlink_rejected(self):
  with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as outside:
   (Path(outside)/'nacos.log').write_text('BeanCreationException');(Path(d)/'logs').symlink_to(outside,target_is_directory=True)
   x=summarize(d);self.assertEqual(x['sources']['application_root']['state'],'rejected');self.assertFalse(x['signals']['bean_initialization_failed'])
 def test_fifo_rejected_without_block(self):
  with tempfile.TemporaryDirectory() as d:
   os.mkfifo(Path(d)/'server-0.log');self.assertEqual(summarize(d)['sources']['launcher_0']['state'],'rejected')
 def test_application_tail_bounded(self):
  with tempfile.TemporaryDirectory() as d:
   logs=Path(d)/'logs';logs.mkdir();(logs/'nacos.log').write_bytes(b'BeanCreationException'+b'x'*(LIMIT+1))
   x=summarize(d);self.assertEqual(x['sources']['application_root']['size'],'tail_only');self.assertFalse(x['signals']['bean_initialization_failed'])
 def test_source_schema_does_not_echo(self):
  for source in (None,[],{'application_root':{'state':[], 'size':{},'signals':[]}}, {'privateCanary':'secret'}):
   x=sanitize_diagnostic({'diagnostics':{'sources':source}})
   self.assertNotIn('privateCanary',json.dumps(x))
 def test_unobserved_is_unknown_not_missing(self):
  x=sanitize_diagnostic({'diagnostic_error':True})
  self.assertTrue(all(v['state']=='unknown' for v in x['diagnostics']['sources'].values()))
 def test_aggregates_derived_from_validated_sources(self):
  x=sanitize_diagnostic({'diagnostics':{'files_seen':5,'read_error':True,'tail_truncated':True,'signals':{'bean_initialization_failed':True},'sources':{'application_root':{'state':'missing','size':'tail_only','signals':{'bean_initialization_failed':True}}}}})['diagnostics']
  self.assertEqual(x['files_seen'],0);self.assertFalse(x['read_error']);self.assertFalse(x['tail_truncated']);self.assertFalse(x['signals']['bean_initialization_failed'])
 def test_close_failure_still_attempts_all_descriptors(self):
  with tempfile.TemporaryDirectory() as d:
   logs=Path(d)/'logs';logs.mkdir();(logs/'nacos.log').write_text('')
   original=os.close;calls=[]
   def close(fd):
    calls.append(fd);original(fd)
    if len(calls)==1:raise OSError('synthetic close failure')
   with patch.object(safe_failure.os,'close',side_effect=close):
    self.assertEqual(safe_failure.read_fixed(d,('logs','nacos.log'))[0],'read')
   self.assertEqual(len(calls),3)
 def test_actual_read_error_classification(self):
  with patch.object(safe_failure.os,'open',side_effect=PermissionError('privateCanary')):
   x=summarize('/unused');self.assertTrue(x['read_error']);self.assertEqual(x['files_seen'],0)
   self.assertTrue(all(v['state']=='read_error' for v in x['sources'].values()));self.assertNotIn('privateCanary',json.dumps(x))
 def test_five_empty_sources(self):
  with tempfile.TemporaryDirectory() as d:
   (Path(d)/'logs').mkdir()
   for parts in safe_failure.SOURCES.values():Path(d,*parts).write_text('')
   x=sanitize_diagnostic({'diagnostics':summarize(d)})['diagnostics']
   self.assertEqual(x['files_seen'],5);self.assertTrue(all(v['state']=='read' and v['size']=='empty' for v in x['sources'].values()))
   self.assertEqual(set(x['sources']),set(safe_failure.SOURCES))
if __name__=='__main__':unittest.main()



