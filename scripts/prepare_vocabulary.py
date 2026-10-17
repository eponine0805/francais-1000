#!/usr/bin/env python3
"""Select exactly 100 expressions per lesson and export the fixed offline catalog."""
from pathlib import Path
import json
import re
import subprocess
import argparse

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'scripts'/'vocab_source.txt'
OUTPUT=ROOT/'app'/'src'/'main'/'assets'/'words.json'

def build():
    lessons=[]
    for line in SRC.read_text(encoding='utf-8').splitlines():
        line=line.strip()
        if not line: continue
        if line.startswith('### '):
            lessons.append({'title':line[4:].strip(), 'raw':[]})
            continue
        fr,ja=line.split('|',1)
        lessons[-1]['raw'].append((fr.strip(),ja.strip()))
    assert len(lessons)==10
    selected=[];used=set()
    for idx,lesson in enumerate(lessons):
        vocab=[]
        for fr,ja in lesson['raw']:
            if fr.casefold() in used: continue
            # Lesson 10: exactly 55 action verbs, followed by 45 abstract nouns.
            if idx==9:
                is_noun=fr.startswith(('un ', 'une ', 'le ', 'la ', "l'", 'les ', 'du ', 'de ', 'des '))
                if len(vocab)<55 and is_noun:continue
                if len(vocab)>=55 and not is_noun:continue
            vocab.append((fr,ja))
            used.add(fr.casefold())
            if len(vocab)==100:break
        if len(vocab)<100:
            raise ValueError(f'Lesson {idx+1} has only {len(vocab)} unique words')
        for fr,ja in vocab:
            assert fr and ja and '|' not in fr
            selected.append({'id':len(selected)+1,'lesson':idx+1,'fr':fr,'ja':ja})
    assert len(selected)==1000
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text(json.dumps(selected,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    print(f'Exported {len(selected)} words ({OUTPUT.stat().st_size:,} bytes); 10 lessons.')

if __name__=='__main__':build()
