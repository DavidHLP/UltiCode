import json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import trace_summary as t
import trace_driver as driver
import trace_entry as entry
class Tests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.p=Path(self.tmp.name)/'trace'
 def test_secret_never_exported(self):
  self.p.write_bytes(b'com.alibaba.nacos.Nacos source: file:/SECRET_CANARY\n')
  r=t.summarize(self.p);self.assertTrue(r['class_observed']['nacos_entry_class']);self.assertNotIn('SECRET',json.dumps(r));self.assertTrue(r['snapshot_read_complete'])
 def test_embedded_marker_is_not_event(self):
  self.p.write_bytes(b'error message com.alibaba.nacos.Nacos source: injected\n');self.assertFalse(any(t.summarize(self.p)['class_observed'].values()))
 def test_partial_record(self):
  self.p.write_bytes(b'com.alibaba.nacos.Nacos source: partial');r=t.summarize(self.p);self.assertFalse(r['snapshot_read_complete']);self.assertFalse(any(r['class_observed'].values()))
 def test_bounded_read(self):
  self.p.write_bytes(b'x'*101)
  with patch.object(t,'LIMIT',100):self.assertFalse(t.summarize(self.p)['snapshot_read_complete'])
 def test_missing(self):self.assertEqual(t.summarize(self.p)['read_state'],'missing')
 def test_symlink(self):
  target=self.p.with_name('target');target.write_bytes(b'secret');self.p.symlink_to(target);self.assertEqual(t.summarize(self.p)['read_state'],'read_error')
 def test_fifo(self):
  os.mkfifo(self.p);self.assertEqual(t.summarize(self.p)['read_state'],'rejected')
 def test_no_server_after_failed_canary(self):
  with patch.object(driver,'canary',return_value=False),patch.object(driver,'quiet') as q:
   self.assertFalse(driver.main()['server_attempted']);q.assert_not_called()
 def test_no_server_after_canary_exception(self):
  with patch.object(driver,'canary',side_effect=RuntimeError('PRIVATE')):
   r=driver.main();self.assertFalse(r['server_attempted']);self.assertNotIn('PRIVATE',json.dumps(r))
 def test_only_adds_one_jvm_option(self):
  old=entry.original_command('/java','/dist','/home','direct');new=entry.traced_command('/java','/dist','/home','direct')
  self.assertEqual(new[:1]+new[2:],old);self.assertEqual(new[1],'-Xlog:class+load=info:file=/trace/server.log:none:filecount=0')
 def test_refuses_other_mode(self):
  with self.assertRaises(ValueError):entry.traced_command('/java','/dist','/home','official-equivalent')
 def test_at_most_one_server_attempt_failure(self):
  with patch.object(driver,'TRACE',Path(self.tmp.name)),patch.object(driver,'ROOT',Path(self.tmp.name)),patch.object(driver,'canary',return_value=True),patch.object(driver,'quiet',return_value=False) as q:
   r=driver.main();self.assertTrue(r['server_attempted']);self.assertFalse(r['passed']);self.assertEqual(q.call_count,1)
 def test_timeout_no_retry_no_raw_exception(self):
  with patch.object(driver,'TRACE',Path(self.tmp.name)),patch.object(driver,'ROOT',Path(self.tmp.name)),patch.object(driver,'canary',return_value=True),patch.object(driver,'quiet',side_effect=RuntimeError('PRIVATE')) as q:
   r=driver.main();self.assertEqual(q.call_count,1);self.assertFalse(r['passed']);self.assertNotIn('PRIVATE',json.dumps(r))
if __name__=='__main__':unittest.main()
