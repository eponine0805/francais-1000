#!/usr/bin/env python3
import json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
items=json.loads((root/'app/src/main/assets/words.json').read_text(encoding='utf-8'))
out=root/'app/src/main/assets/audio';out.mkdir(parents=True, exist_ok=True)
for n,item in enumerate(items,1):
    dest=out/f"{item['id']:04d}.ogg"
    if dest.exists() and dest.stat().st_size>400:continue
    synthesis=subprocess.run(['espeak','-v','fr-fr','-s','123','-p','52','--stdout',item['fr']],capture_output=True,check=True)
    encode=subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i','pipe:0','-ac','1','-ar','22050','-c:a','libvorbis','-q:a','3',str(dest)],input=synthesis.stdout,capture_output=True)
    if encode.returncode:raise RuntimeError(encode.stderr.decode())
    if n%100==0:print(f'{n}/{len(items)} audio generated',flush=True)
assert len(list(out.glob('*.ogg')))==1000
