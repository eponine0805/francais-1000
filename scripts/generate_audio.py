#!/usr/bin/env python3
"""Embed all 1000 French pronunciations as local Ogg Vorbis files.

Requires eSpeak (French voice) and ffmpeg. Called before Android build in CI.
"""
import json
import subprocess
from pathlib import Path
import sys

root=Path(__file__).resolve().parents[1]
items=json.loads((root/'app/src/main/assets/words.json').read_text(encoding='utf-8'))
out=root/'app/src/main/assets/audio'
out.mkdir(parents=True, exist_ok=True)
for n, item in enumerate(items,1):
    destination=out/f"{item['id']:04d}.ogg"
    if destination.exists() and destination.stat().st_size>400:
        continue
    french=item['fr']
    # slow and clear for absolute beginners, modest pauses between syllables
    synth=subprocess.run(['espeak','-v','fr-fr','-s','123','-p','52','--stdout',french],capture_output=True,check=True)
    enc=subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i','pipe:0',
                        '-ac','1','-ar','22050','-c:a','libvorbis','-q:a','3',str(destination)],
                       input=synth.stdout,capture_output=True)
    if enc.returncode!=0:
        raise RuntimeError(f'Audio error for {french}: {enc.stderr.decode()}')
    if n%100==0:
        print(f'{n}/{len(items)} audio generated',flush=True)
files=list(out.glob('*.ogg'))
if len(files)!=len(items):
    raise RuntimeError(f'expected {len(items)} files; found {len(files)}')
print(f'Validated {len(files)} audio clips / {sum(p.stat().st_size for p in files)/1024/1024:.2f} MiB')
