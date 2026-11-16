import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "mora_identity", Path(__file__).parents[1] / "mora_identity.py"
)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class DesktopIdentityTests(unittest.TestCase):
    def test_firefox_profile_and_diagnostics(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "ff", "firefox", "iphone")
            self.assertTrue((profile / "user.js").is_file())
            self.assertTrue((profile / "diagnostics.html").is_file())
            self.assertIn("general.useragent.override", (profile / "user.js").read_text())
            self.assertEqual(m.profiles(Path(root))[0]["platform_test_label"], "iphone")

    def test_chromium_uses_isolated_data_directory_and_webrtc_flag(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "brave", "brave", "windows")
            launcher = (profile / "launch.sh").read_text()
            self.assertIn("--user-data-dir=", launcher)
            self.assertIn("--user-agent=", launcher)
            self.assertIn("disable_non_proxied_udp", launcher)

    def test_existing_profile_never_overwritten(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "safe", "firefox", "linux")
            (profile / "keep").write_text("yes")
            with self.assertRaises(FileExistsError):
                m.create_profile(Path(root), "safe", "chrome", "android")
            self.assertEqual((profile / "keep").read_text(), "yes")

    def test_rejects_path_traversal_and_control_characters(self):
        with self.assertRaises(ValueError):
            m.safe_id("../escape")
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                m.create_profile(Path(root), "bad", "firefox", "linux", "bad\nua")

    def test_android_exchange_import_export_round_trip(self):
        with tempfile.TemporaryDirectory() as source, tempfile.TemporaryDirectory() as target:
            profile = m.create_profile(Path(source), "android-profile", "chrome", "android",
                                       model="Pixel compatibility profile")
            exchange = Path(source) / "profiles.json"
            self.assertEqual(m.export_exchange(Path(source), exchange), 1)
            payload = json.loads(exchange.read_text())
            self.assertEqual(payload["profiles"][0]["platform"], "ANDROID")
            imported = m.import_exchange(Path(target), exchange)
            self.assertEqual(imported, ["android-profile"])
            restored = json.loads((Path(target) / "android-profile" / "profile.json").read_text())
            self.assertEqual(restored["user_agent"], json.loads((profile / "profile.json").read_text())["user_agent"])
            self.assertEqual(restored["platform_test_label"], "android")

    def test_diagnostic_report_comparison(self):
        with tempfile.TemporaryDirectory() as root:
            before = Path(root) / "before.json"
            after = Path(root) / "after.json"
            before.write_text(json.dumps({"userAgent": "A", "timezone": "UTC", "canvas": {"hash": "a"}}))
            after.write_text(json.dumps({"userAgent": "B", "timezone": "Asia/Jakarta", "canvas": {"hash": "b"}}))
            result = m.compare_reports(before, after)
            self.assertEqual(result["changed"], 3)
            self.assertEqual({item["field"] for item in result["changes"]}, {"userAgent", "timezone", "canvas"})

    def test_backup_and_restore_are_non_overwriting(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            profile = m.create_profile(root_path, "backup", "firefox", "linux")
            archive = m.backup_profile(root_path, "backup", root_path / "export.zip")
            self.assertTrue(archive.is_file())
            import shutil
            shutil.rmtree(profile)
            restored = m.restore_profile(root_path, "backup", archive)
            self.assertTrue((restored / "profile.json").is_file())
            with self.assertRaises(FileExistsError):
                m.restore_profile(root_path, "backup", archive)

    def test_rollback_restores_last_backup_without_losing_current_state(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            profile = m.create_profile(root_path, "restore", "firefox", "linux")
            original = json.loads((profile / "profile.json").read_text())
            m.backup_profile(root_path, "restore", root_path / "manual.zip")
            changed = dict(original)
            changed["model"] = "manually changed"
            (profile / "profile.json").write_text(json.dumps(changed))
            restored = m.rollback_profile(root_path, "restore")
            current = json.loads((restored / "profile.json").read_text())
            self.assertEqual(current["model"], original["model"])
            self.assertTrue((root_path / ".rollback" / "restore.pre-rollback.zip").is_file())
            self.assertTrue((root_path / ".rollback" / "restore.pre-rollback").is_dir())

    def test_rejects_zip_slip(self):
        import zipfile
        with tempfile.TemporaryDirectory() as root:
            archive = Path(root) / "unsafe.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("../escape.txt", "unsafe")
                zipped.writestr("profile.json", json.dumps({
                    "id": "bad", "browser": "firefox", "platform_test_label": "linux",
                    "user_agent": m.PLATFORMS["linux"]
                }))
            with self.assertRaises(ValueError):
                m.restore_profile(Path(root), "bad", archive)
            self.assertFalse((Path(root).parent / "escape.txt").exists())

    def test_firefox_privacy_controls_are_recorded_and_written(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "firefox-guard", "firefox", "linux",
                                       protect_canvas=True, protect_webgl=True)
            prefs = (profile / "user.js").read_text()
            metadata = json.loads((profile / "profile.json").read_text())
            self.assertIn("privacy.resistFingerprinting", prefs)
            self.assertIn("webgl.disabled", prefs)
            self.assertTrue(metadata["protection"]["canvas"]["applied"])
            self.assertTrue(metadata["protection"]["webgl"]["applied"])

    def test_chromium_marks_canvas_unsupported_and_webgl_opt_in(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "chromium-guard", "chromium", "linux",
                                       protect_canvas=True, protect_webgl=True)
            launcher = (profile / "launch.sh").read_text()
            metadata = json.loads((profile / "profile.json").read_text())
            self.assertIn("--disable-webgl", launcher)
            self.assertFalse(metadata["protection"]["canvas"]["applied"])
            self.assertIn("not available", metadata["protection"]["canvas"]["method"])

    def test_diagnostics_uri_is_local_file(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "diagnostic", "firefox", "linux")
            uri = m.diagnostics_uri(Path(root), "diagnostic")
            self.assertTrue(uri.startswith("file://"))
            self.assertTrue(uri.endswith("/diagnostics.html"))

    def test_audit_report_detects_ua_client_hints_mismatch(self):
        report = {
            "userAgent": "Mozilla/5.0 (Linux; Android 15) Chrome/131.0.0.0",
            "clientHints": {"platform": "Windows", "mobile": False},
            "canvas": {"available": True}, "webgl": {"available": True},
            "webRtc": {"peerConnection": False}, "timezone": "Asia/Jakarta",
            "language": "id-ID", "screen": {"width": 1080, "height": 2400},
        }
        result = m.audit_report(report)
        self.assertEqual(result["summary"]["mismatches"], 1)
        mismatch = next(item for item in result["checks"] if item["surface"] == "clientHints")
        self.assertEqual(mismatch["status"], "mismatch")
        self.assertIn("tidak menguji IP publik", result["note"])

    def test_profile_launch_uses_mora_isolated_firefox_directory(self):
        with tempfile.TemporaryDirectory() as root:
            profile = m.create_profile(Path(root), "launch-check", "firefox", "linux")
            with patch.object(m.shutil, "which", return_value="/usr/bin/firefox"), \
                 patch.object(m.subprocess, "Popen") as popen:
                m.launch_profile(Path(root), "launch-check", diagnostics=True)
                command = popen.call_args.args[0]
                self.assertIn("-no-remote", command)
                self.assertIn("-profile", command)
                self.assertIn(str(profile), command)
                self.assertTrue(command[-1].startswith("file://"))

    def test_cli_protection_switches_write_profile_capabilities(self):
        with tempfile.TemporaryDirectory() as root:
            result = m.main([
                "--root", root, "create", "--id", "protected",
                "--browser", "firefox", "--platform", "linux",
                "--protect-canvas", "--protect-webgl"
            ])
            self.assertEqual(result, 0)
            metadata = json.loads((Path(root) / "protected" / "profile.json").read_text())
            self.assertTrue(metadata["protection"]["canvas"]["applied"])
            self.assertTrue(metadata["protection"]["webgl"]["applied"])

    def test_doctor_reports_supported_browser_families(self):
        result = m.doctor()
        self.assertEqual(set(result), set(m.BROWSERS))
        self.assertTrue(all("installed" in item for item in result.values()))


if __name__ == "__main__":
    unittest.main()
