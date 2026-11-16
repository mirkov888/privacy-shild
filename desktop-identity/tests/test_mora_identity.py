import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("mora_identity",Path(__file__).parents[1]/"mora_identity.py")
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
class DesktopIdentityTests(unittest.TestCase):
 def test_firefox_profile_and_diagnostics(self):
  with tempfile.TemporaryDirectory() as t:
   p=m.create_profile(Path(t),"ff","firefox","iphone")
   self.assertTrue((p/"user.js").is_file()); self.assertTrue((p/"diagnostics.html").is_file())
   self.assertIn("general.useragent.override",(p/"user.js").read_text())
   self.assertEqual(m.profiles(Path(t))[0]["platform_test_label"],"iphone")
 def test_chromium_isolated_data_directory(self):
  with tempfile.TemporaryDirectory() as t:
   p=m.create_profile(Path(t),"brave","brave","windows")
   s=(p/"launch.sh").read_text()
   self.assertIn("--user-data-dir=",s); self.assertIn("--user-agent=",s)
   self.assertIn("disable_non_proxied_udp",s)
 def test_existing_profile_never_overwritten(self):
  with tempfile.TemporaryDirectory() as t:
   p=m.create_profile(Path(t),"safe","firefox","linux"); (p/"keep").write_text("yes")
   with self.assertRaises(FileExistsError): m.create_profile(Path(t),"safe","chrome","android")
   self.assertEqual((p/"keep").read_text(),"yes")
 def test_rejects_traversal(self):
  with self.assertRaises(ValueError): m.safe_id("../escape")
 def test_rejects_invalid_ua(self):
  with tempfile.TemporaryDirectory() as t:
   with self.assertRaises(ValueError): m.create_profile(Path(t),"bad","firefox","linux","bad\nua")
if __name__=="__main__": unittest.main()
