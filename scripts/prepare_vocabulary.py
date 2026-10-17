#!/usr/bin/env python3
"""Validate the carefully curated 1000-item catalog before packaging."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
items=json.loads((root/'app/src/main/assets/words.json').read_text(encoding='utf-8'))
assert len(items)==1000
assert len({x['fr'].casefold() for x in items})==1000
assert len({x['id'] for x in items})==1000
for i,x in enumerate(items,1):
    assert x['id']==i and x['lesson']==(i-1)//100+1
    assert x['fr'].strip() and x['ja'].strip()
print('Editorial catalog verified: 1000 unique words and phrases in 10 lessons')
