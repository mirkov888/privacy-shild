#!/usr/bin/env python3
"""Apply Mora Shield Phase 5 central policy UI patch. Safe to rerun."""
from pathlib import Path
import shutil, sys

root = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else Path.cwd()
settings = root / 'app/src/main/java/com/mora/shield/ScreenSettings.kt'
source = Path(__file__).parent / 'app/src/main/java/com/mora/shield/MoraSystemManagerPolicyCard.kt'
target = root / 'app/src/main/java/com/mora/shield/MoraSystemManagerPolicyCard.kt'
if not settings.is_file():
    raise SystemExit(f'ERROR: tidak menemukan {settings}')
text = settings.read_text(encoding='utf-8')
anchor = '        MonitorCard(ctx)\n'
insert = '        MoraDownloaderControlCard()\n        MoraSystemManagerPolicyCard()\n'
if 'MoraSystemManagerPolicyCard()' not in text:
    if anchor not in text:
        raise SystemExit('ERROR: anchor MonitorCard(ctx) tidak ditemukan; tidak ada file yang diubah.')
    text = text.replace(anchor, anchor + insert, 1)
    settings.write_text(text, encoding='utf-8')
target.parent.mkdir(parents=True, exist_ok=True)
if not target.exists() or target.read_bytes() != source.read_bytes():
    shutil.copy2(source, target)
print('PASS: Mora Shield Phase 5 central policy UI installed (idempotent).')
print('NOTE: Downloader card is included only if its source file already exists in this branch.')
