#!/usr/bin/env python3
"""Auditable baseline invariants. NOT a native-speaker audio/language audit."""
import json,random,re
from pathlib import Path
data=json.loads((Path(__file__).resolve().parents[1]/'app/src/main/assets/words.json').read_text(encoding='utf8'))
assert len(data)==1000
assert [x['id'] for x in data]==list(range(1,1001))
assert len({x['fr'].casefold() for x in data})==1000
assert all(x['lesson']==(x['id']-1)//100+1 and x['ja'] and x['ipa'].strip('/') for x in data)
fixed={"174":"/kɥizine/","199":"/kɔ̃dɥiʁ/","200":"/sɥivʁ/","230":"/bɔn nɥi/","305":"/yn kɥizin/","360":"/œ̃ paʁaplɥi/","399":"/yn kɥijɛʁ/","442":"/də lɥil/","445":"/œ̃ fʁɥi/","473":"/œ̃ biskɥi/","514":"/ʒɥɛ̃/","515":"/ʒɥijɛ/","527":"/yn nɥi/","547":"/ɑ̃sɥit/","560":"/œ̃ nɥaʒ/","561":"/la plɥi/","600":"/œ̃ bʁɥi/","766":"/ɑ̃nɥijø/","917":"/kɔ̃stʁɥiʁ/","918":"/detʁɥiʁ/"}
for key,value in fixed.items():
    assert data[int(key)-1]['ipa']==value,(key,value)
def near(a,b):
    if a==b: return True
    clean=lambda s: re.sub(r'（[^）]*）','',s).replace('・','/').replace('、','/').replace('／','/')
    return any(len(x.strip())>=2 and len(y.strip())>=2 and (x.strip() in y.strip() or y.strip() in x.strip())
               for x in clean(a).split('/') for y in clean(b).split('/'))
for seed in range(10):
    rng=random.Random(seed)
    for w in data:
        pool=[v['ja'] for v in data if v['id']!=w['id'] and v['lesson']==w['lesson'] and v['ja']!=w['ja']]
        rng.shuffle(pool);pick=[w['ja']]
        for candidate in pool:
            if not any(near(candidate,p) for p in pick):pick.append(candidate)
            if len(pick)==4:break
        assert len(pick)==4 and len(set(pick))==4,(w['id'],pick)
for start in range(1,11):
    for end in range(start,11):
        pool=list(range((start-1)*100+1,end*100+1))
        for count in (20,40,60,80,100,len(pool)):
            rng=random.Random(start*101+end*17+count);ids=rng.sample(pool,count)
            assert len(set(ids))==count and all((start-1)*100<x<=end*100 for x in ids)
print('OK: catalog 1000; 10000 4-choice trials; 330 lesson range trials; 20 IPA regression assertions')
