import ast,tempfile,unittest,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
class Contract(unittest.TestCase):
 def select(self,path):
  tree=ast.parse((HERE/'prepare.py').read_text());node=next(x for x in tree.body if isinstance(x,ast.FunctionDef) and x.name=='select_boot')
  scope={'zipfile':zipfile};exec(compile(ast.Module(body=[node],type_ignores=[]),'select_boot','exec'),scope)
  return scope['select_boot'](path)
 def test_exec_jar_selected_over_thin(self):
  with tempfile.TemporaryDirectory() as tmp:
   d=Path(tmp)
   with zipfile.ZipFile(d/'submission.jar','w') as z:z.writestr('a.class','thin')
   with zipfile.ZipFile(d/'submission-exec.jar','w') as z:z.writestr('BOOT-INF/lib/a.jar','nested')
   self.assertEqual(self.select(d).name,'submission-exec.jar')
 def test_no_boot_fails(self):
  with tempfile.TemporaryDirectory() as tmp:
   with self.assertRaises(RuntimeError):self.select(Path(tmp))
 def test_ambiguous_boot_fails(self):
  with tempfile.TemporaryDirectory() as tmp:
   for name in ('a.jar','b.jar'):
    with zipfile.ZipFile(Path(tmp)/name,'w') as z:z.writestr('BOOT-INF/lib/a.jar','nested')
   with self.assertRaises(RuntimeError):self.select(Path(tmp))
 def test_sources_parse(self):
  for name in ('prepare.py','run_six.py','real_nacos_test.py'):ast.parse((HERE/name).read_text())
from test_safe_failure import SafeDiagnostics, FailureShapes
if __name__=='__main__':unittest.main()
