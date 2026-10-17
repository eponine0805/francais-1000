#!/usr/bin/env python3
"""Generate bundled *offline* high-quality French female speech using Kokoro-82M.

Build-time dependencies (not present on Android): kokoro-onnx, misaki-fork,
onnxruntime, soundfile, ffmpeg, espeak-ng. No user account or API required.
"""
import io
import json
import subprocess
from pathlib import Path

import soundfile as sf
from kokoro_onnx import Kokoro
from misaki.espeak import EspeakG2P

ROOT = Path(__file__).resolve().parents[1]
ITEMS = json.loads((ROOT / 'app/src/main/assets/words.json').read_text(encoding='utf-8'))
OUT = ROOT / 'app/src/main/assets/audio'
OUT.mkdir(parents=True, exist_ok=True)
MODEL = ROOT / 'models/kokoro-v1.0.onnx'
VOICES = ROOT / 'models/voices-v1.0.bin'
if not (MODEL.exists() and VOICES.exists()):
    raise RuntimeError('Kokoro voice models not found. Follow README build instructions.')

g2p = EspeakG2P(language='fr-fr')
tts = Kokoro(str(MODEL), str(VOICES))
for n, item in enumerate(ITEMS, 1):
    path = OUT / f"{item['id']:04d}.ogg"
    # Never reuse eSpeak files from an older build.
    fr = item['fr'].strip()
    phonemes, _ = g2p(fr)
    if not phonemes:
        raise RuntimeError(f'Unable to phonemize #{n}: {fr}')
    # ff_siwis is a French, female, neural voice. Slightly slower for beginners.
    audio, sample_rate = tts.create(phonemes, voice='ff_siwis', speed=0.91, is_phonemes=True)
    if len(audio) < sample_rate * 0.12:
        raise RuntimeError(f'Audio appears empty for #{n}: {fr}')
    buffer = io.BytesIO()
    sf.write(buffer, audio, sample_rate, format='WAV', subtype='PCM_16')
    result = subprocess.run(
        ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
         '-i', 'pipe:0', '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', str(path)],
        input=buffer.getvalue(), capture_output=True)
    if result.returncode:
        raise RuntimeError(f"Encoding failed for {fr}: {result.stderr.decode()}")
    if n % 50 == 0:
        print(f'Neural French female audio: {n}/{len(ITEMS)}', flush=True)
if len(list(OUT.glob('*.ogg'))) != 1000:
    raise RuntimeError('Exactly 1000 audio clips are required')
print(f'Complete: {len(ITEMS)} offline female French neural clips')
