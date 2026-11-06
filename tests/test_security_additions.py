from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class SecurityAdditionSourceTests(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_release_workflow_requires_persistent_signing_secrets(self):
        workflow = self.read(".github/workflows/build.yml")
        for secret in ("MORA_KEYSTORE_BASE64", "MORA_KEYSTORE_PASSWORD", "MORA_KEY_ALIAS", "MORA_KEY_PASSWORD"):
            self.assertIn(secret, workflow)
        self.assertIn("testDebugUnitTest", workflow)

    def test_link_cleaner_regression_tests_exist(self):
        tests = self.read("app/src/test/java/com/mora/shield/LinkerTest.kt")
        self.assertIn("removesGlobalTrackingParametersAndKeepsContent", tests)
        self.assertIn("unwrapsGoogleRedirectAndCleansDestination", tests)
        self.assertIn("preservesPlainTextAndTrailingPunctuation", tests)

    def test_sensitive_app_actions_are_reversible_and_protected(self):
        actions = self.read("app/src/main/java/com/mora/shield/security/SensitiveAppActions.kt")
        self.assertIn("pm disable-user --user 0", actions)
        self.assertIn("pm enable", actions)
        self.assertIn("com.android.systemui", actions)
        self.assertNotIn("pm clear", actions)

    def test_encrypted_export_uses_authenticated_encryption(self):
        source = self.read("app/src/main/java/com/mora/shield/security/EncryptedAuditExport.kt")
        self.assertIn("AES/GCM/NoPadding", source)
        self.assertIn("PBKDF2WithHmacSHA256", source)
        self.assertIn("210_000", source)

    def test_widget_and_persistent_traffic_sampler_exist(self):
        self.assertTrue((ROOT / "app/src/main/java/com/mora/shield/widget/MoraShieldWidget.kt").is_file())
        traffic = self.read("app/src/main/java/com/mora/shield/security/TrafficHistory.kt")
        self.assertIn("INTERVAL", traffic)
        self.assertIn("app_samples", traffic)

    def test_security_signals_are_explicitly_heuristic(self):
        source = self.read("app/src/main/java/com/mora/shield/security/NetworkRiskMonitor.kt")
        self.assertIn("bukan bukti BTS palsu", source)
        self.assertIn("wifi_encryption", source)
        self.assertIn("registered_cell_hash", source)

if __name__ == "__main__":
    unittest.main()
