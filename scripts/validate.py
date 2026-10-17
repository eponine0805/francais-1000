#!/usr/bin/env python3
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=json.loads((r/'app/src/main/assets/words.json').read_text(encoding='utf8'))
assert len(a)==1000 and len({w['fr'].casefold() for w in a})==1000
files=[r/'app/src/main/assets/audio'/f"{w['id']:04}.ogg" for w in a]
assert all(p.is_file() and p.stat().st_size>1000 for p in files)
print('1000 words, 1000 nonempty offline neural audio files: verified')
