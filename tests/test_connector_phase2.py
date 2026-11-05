import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "sdk/src/main/java/com/mora/connector/sdk"
HOST = ROOT / "app/src/main/java/com/mora/shield/connector"
CORE = ROOT / "app/src/main/java/com/mora/shield/core"


class ConnectorContractTests(unittest.TestCase):
    def test_message_constants_are_unique(self):
        source = (SDK / "MoraConnectorContract.kt").read_text()
        values = [int(v) for v in re.findall(r"const val MSG_[A-Z_]+ = (\d+)", source)]
        self.assertEqual(len(values), len(set(values)))
        self.assertIn(99, values)

    def test_host_and_sdk_share_wire_keys(self):
        contract = (SDK / "MoraConnectorContract.kt").read_text()
        client = (SDK / "MoraConnectorClient.kt").read_text()
        service = (HOST / "MoraConnectorService.kt").read_text()
        constants = set(re.findall(r"const val (KEY_[A-Z0-9_]+)", contract))
        for used in re.findall(r"MoraConnectorContract\.(KEY_[A-Z0-9_]+)", client + service):
            self.assertIn(used, constants, used)

    def test_uid_and_signer_authentication_present(self):
        service = (HOST / "MoraConnectorService.kt").read_text()
        security = (HOST / "ConnectorSecurity.kt").read_text()
        self.assertIn("message.sendingUid", service)
        self.assertIn("getPackagesForUid(uid)", security)
        self.assertIn('getInstance("SHA-256")', security)
        self.assertIn("MoraCore.markConnectorConnected", service)

    def test_connector_surface_has_no_shell_executor(self):
        sources = "\n".join(p.read_text() for p in list(HOST.glob("*.kt")) + list(SDK.glob("*.kt")))
        self.assertNotIn('ProcessBuilder("su"', sources)
        self.assertNotIn('Runtime.getRuntime().exec', sources)
        self.assertNotIn("/system/bin/sh", sources)

    def test_remote_commands_are_allowlisted_and_audited(self):
        router = (CORE / "CommandRouter.kt").read_text()
        service = (HOST / "MoraConnectorService.kt").read_text()
        self.assertIn('request.commandId !in advertised', router)
        self.assertIn('policies.evaluate(principal, request.commandId, approvedByUser)', router)
        self.assertIn('MoraCore.completeRemoteCommand', service)
        self.assertIn('COMMAND_TIMEOUT_MS = 30_000L', service)
        self.assertIn('MAX_EVENTS_PER_WINDOW = 120', service)

    def test_policy_engine_fails_closed_when_signer_digest_is_missing(self):
        policy = (CORE / "PolicyEngine.kt").read_text()
        self.assertIn('app.certificateSha256.isBlank() || !app.certificateSha256.equals(principal.certificateSha256, true)', policy)

    def test_session_tokens_guard_inbound_messages_and_remote_commands(self):
        contract = (SDK / "MoraConnectorContract.kt").read_text()
        service = (HOST / "MoraConnectorService.kt").read_text()
        client = (SDK / "MoraConnectorClient.kt").read_text()
        self.assertIn('KEY_SESSION_TOKEN = "session_token"', contract)
        self.assertIn('isCurrentSession(principal, sessionToken)', service)
        self.assertIn('session.sessionToken == sessionToken', service)
        self.assertIn('putString(MoraConnectorContract.KEY_SESSION_TOKEN, session.sessionToken)', service)
        self.assertIn('incomingToken == sessionToken', client)
        self.assertIn('sessionToken?.let { putString(MoraConnectorContract.KEY_SESSION_TOKEN, it) }', client)

    def test_connected_registry_state_survives_refresh(self):
        registry = (CORE / "AppRegistry.kt").read_text()
        self.assertIn('previous?.connectionState == "CONNECTED" -> "CONNECTED"', registry)


class InstallerTests(unittest.TestCase):
    def test_installer_is_idempotent_and_manifest_is_valid(self):
        # The fixture contains the minimum Phase-1 Core source layout.
        fixture = Path(tempfile.mkdtemp(prefix="mora-connector-phase2-"))
        try:
            shield_fixture = Path("/mnt/data/mora-shield-fixture")
            if not shield_fixture.exists():
                self.skipTest("Phase-1 fixture is not available")
            shutil.copytree(shield_fixture, fixture, dirs_exist_ok=True)

            installer = ROOT / "apply-mora-connector-phase2.py"
            subprocess.run([sys.executable, str(installer), str(fixture)], check=True, capture_output=True, text=True)
            manifest = fixture / "app/src/main/AndroidManifest.xml"
            ET.parse(manifest)
            first = manifest.read_text()
            self.assertEqual(first.count('android:name=".connector.MoraConnectorService"'), 1)
            self.assertEqual(first.count('android:name="com.mora.shield.permission.CONNECTOR"'), 1)
            self.assertEqual(first.count('android:permission="com.mora.shield.permission.CONNECTOR"'), 1)

            readme = fixture / "README.md"
            first_readme = readme.read_text()
            subprocess.run([sys.executable, str(installer), str(fixture)], check=True, capture_output=True, text=True)
            self.assertEqual(manifest.read_text(), first)
            self.assertEqual(readme.read_text(), first_readme)

            app_gradle = (fixture / "app/build.gradle.kts").read_text()
            self.assertIn('versionName = "3.6.0"', app_gradle)
            self.assertRegex(app_gradle, r"versionCode\s*=\s*13")
            self.assertTrue((fixture / "connector-sdk/src/main/java/com/mora/connector/sdk/MoraConnectorClient.kt").is_file())
            self.assertTrue((fixture / "app/src/main/java/com/mora/shield/core/PolicyEngine.kt").is_file())
        finally:
            shutil.rmtree(fixture, ignore_errors=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)
