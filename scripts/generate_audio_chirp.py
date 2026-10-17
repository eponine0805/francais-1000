#!/usr/bin/env python3
"""Create offline Ogg clips from licensed Google Cloud Chirp 3: HD female French speech.

Cloud authentication exists only during build. Never package or print credentials.
Run --samples to audition oui, non, je suis BEFORE creating a complete set.

Requires GOOGLE_TTS_API_KEY or GOOGLE_TTS_SERVICE_ACCOUNT_JSON secret, and enabled Cloud Text-to-Speech API.
"""
import argparse
import base64
import io
import json
import os
import subprocess
import time
from pathlib import Path

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'app/src/main/assets/words.json'
VOICE=os.environ.get('FRENCH_TTS_VOICE','fr-FR-Chirp3-HD-Aoede')
API_URL='https://texttospeech.googleapis.com/v1/text:synthesize'
SCOPES=['https://www.googleapis.com/auth/cloud-platform']
SAMPLE={'oui','non','je suis','bonjour','merci'}


def auth():
    if os.environ.get('GOOGLE_TTS_API_KEY','').strip():
        return None  # Restricted Google Cloud TTS API key stored only as an Actions secret.
    secret=os.environ.get('GOOGLE_TTS_SERVICE_ACCOUNT_JSON','').strip()
    if not secret:
        raise SystemExit('Set GOOGLE_TTS_API_KEY or GOOGLE_TTS_SERVICE_ACCOUNT_JSON; no premium audio generated.')
    data=json.loads(secret)
    if data.get('type')!='service_account':
        raise SystemExit('Expected a service account JSON document')
    credentials=service_account.Credentials.from_service_account_info(data,scopes=SCOPES)
    credentials.refresh(Request())
    return credentials


def obtain_audio(session,credentials,word):
    payload={
      'input':{'text':word},
      'voice':{'languageCode':'fr-FR','name':VOICE,'ssmlGender':'FEMALE'},
      'audioConfig':{'audioEncoding':'MP3','speakingRate':0.92},
    }
    for attempt in range(7):
        key=os.environ.get('GOOGLE_TTS_API_KEY','').strip()
        if key:
            headers={}
            params={'key':key}
        else:
            if not credentials.valid:credentials.refresh(Request())
            headers={'Authorization':'Bearer '+credentials.token}
            params=None
        resp=session.post(API_URL,json=payload,headers=headers,params=params,timeout=90)
        if resp.ok:
            result=resp.json()
            raw=base64.b64decode(result['audioContent'],validate=True)
            if len(raw)<1200:raise RuntimeError('Suspiciously short MP3 for '+repr(word))
            return raw
        if resp.status_code not in (429,500,502,503,504) or attempt==6:
            raise RuntimeError(f'Text-to-Speech HTTP {resp.status_code} for {word!r}: {resp.text[:300]}')
        time.sleep(min(2**attempt,30))
    raise RuntimeError('Unreachable')


def convert(mp3, destination):
    destination.parent.mkdir(parents=True,exist_ok=True)
    # Preserve entire spoken onset, unlike the earlier model-specific trimming workaround.
    res=subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-nostdin','-y',
                        '-i','pipe:0','-ac','1','-ar','48000','-c:a','libvorbis',
                        '-q:a','8',str(destination)],input=mp3,stderr=subprocess.PIPE)
    if res.returncode:raise RuntimeError(res.stderr.decode('utf8','replace'))
    probe=subprocess.run(['ffprobe','-v','error','-show_entries','format=duration',
                          '-of','default=noprint_wrappers=1:nokey=1',str(destination)],
                         capture_output=True,text=True,check=True)
    duration=float(probe.stdout)
    if not 0.17<=duration<=18:raise RuntimeError(f'Unexpected duration {duration:.3f}s for {destination.name}')
    return duration


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--mode',choices=['samples','full'],default='samples')
    args=parser.parse_args()
    words=json.loads(SOURCE.read_text(encoding='utf8'))
    assert len(words)==1000 and len({w['id'] for w in words})==1000
    if args.mode=='samples':
        selection=[w for w in words if w['fr'].casefold() in SAMPLE]
        if len(selection)!=len(SAMPLE):raise SystemExit('Some QA sample words are missing')
        dest=ROOT/'premium-audio-samples'
    else:
        selection=words
        dest=ROOT/'app/src/main/assets/audio'
    dest.mkdir(parents=True,exist_ok=True)
    credentials=auth()
    session=requests.Session()
    for idx,item in enumerate(selection,1):
        target=dest/f"{item['id']:04d}.ogg"
        mp3=obtain_audio(session,credentials,item['fr'])
        duration=convert(mp3,target)
        if idx<=12 or idx%50==0:print(f'{idx}/{len(selection)}  #{item["id"]:04d}  {item["fr"]}  {duration:.2f}s',flush=True)
        time.sleep(0.7)  # Cap throughput under typical provider request quotas.
    if len(list(dest.glob('*.ogg')))!=len(selection):
        raise RuntimeError('Audio output count does not match word count')
    manifest={'voice':VOICE,'mode':args.mode,'word_count':len(selection),'engine':'Google Cloud Chirp 3: HD','notes':'Needs human review of isolated words before commercial use'}
    (dest/'voice_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
    print('Generation complete; quality approval is still a separate step.')

if __name__=='__main__':main()
