import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "apply-mora-shield-phase3.py"


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.project = Path(self.tmp.name) / "shield"
        (self.project / "app/src/main/java/com/mora/shield/connector").mkdir(parents=True)
        (self.project / "app/src/main/java/com/mora/shield/core").mkdir(parents=True)
        (self.project / "app/build.gradle.kts").write_text(
            'android { defaultConfig { versionCode = 13; versionName = "3.6.0" } }\n', encoding="utf-8"
        )
        (self.project / "app/src/main/java/com/mora/shield/MainActivity.kt").write_text(
            'package com.mora.shield\nclass MainActivity {\n    override fun onResume() {\n        super.onResume()\n    }\n}\n', encoding="utf-8"
        )
        (self.project / "app/src/main/java/com/mora/shield/ScreenTools.kt").write_text(
            'package com.mora.shield\nfun ToolsScreen() {\n        LinkCard()\n        MoraCoreCard()\n}\n', encoding="utf-8"
        )
        (self.project / "app/src/main/java/com/mora/shield/core/MoraCore.kt").write_text(
            'package com.mora.shield.core\nsuspend fun appPolicy(packageName: String) = Unit\n', encoding="utf-8"
        )
        (self.project / "app/src/main/java/com/mora/shield/connector/MoraConnectorService.kt").write_text(
            'package com.mora.shield.connector\nobject MoraConnectorService { suspend fun dispatchCommand() = Unit }\n', encoding="utf-8"
        )

    def tearDown(self):
        self.tmp.cleanup()

    def run_installer(self):
        return subprocess.run(
            ["python3", str(INSTALLER), str(self.project)],
            check=True, capture_output=True, text=True
        )

    def test_installer_is_idempotent(self):
        self.run_installer()
        first = (self.project / "app/src/main/java/com/mora/shield/MainActivity.kt").read_text()
        self.run_installer()
        second = (self.project / "app/src/main/java/com/mora/shield/MainActivity.kt").read_text()
        self.assertEqual(first, second)
        self.assertEqual(second.count("bindKnownCompanions(this)"), 1)
        tools = (self.project / "app/src/main/java/com/mora/shield/ScreenTools.kt").read_text()
        self.assertEqual(tools.count("MoraDownloaderControlCard()"), 1)
        self.assertEqual(tools.count("MoraCoreCard()"), 1)
        gradle = (self.project / "app/build.gradle.kts").read_text()
        self.assertIn('versionName = "3.6.1"', gradle)
        self.assertIn("versionCode = 14", gradle)
        self.assertTrue((self.project / "app/src/main/java/com/mora/shield/connector/MoraConnectorWakeManager.kt").is_file())
        self.assertTrue((self.project / "app/src/main/java/com/mora/shield/MoraDownloaderControlCard.kt").is_file())


if __name__ == "__main__":
    unittest.main()
