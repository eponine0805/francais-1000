import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
words=json.loads((root/'app/src/main/assets/words.json').read_text(encoding='utf-8'))
assert len(words)==1000
assert [x['id'] for x in words]==list(range(1,1001))
assert len(set(x['fr'].casefold() for x in words))==1000
assert all(x['lesson']==i//100+1 for i,x in enumerate(words))
assert all(x['ja'] and x['fr'] for x in words)
audio=root/'app/src/main/assets/audio'
assert len(list(audio.glob('*.ogg')))==1000
assert all((audio/f"{x['id']:04d}.ogg").stat().st_size>400 for x in words)
print('PASS: 1000 words, 1000 offline audio clips.')
