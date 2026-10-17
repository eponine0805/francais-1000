#!/usr/bin/env python3
"""Generate 1000 offline French female clips with Supertonic 3. Build-time only."""
import io
import json
import subprocess
from pathlib import Path
import numpy as np
import soundfile as sf
from supertonic import TTS

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app/src/main/assets/words.json"
OUT = ROOT / "app/src/main/assets/audio"
PREVIEWS = ROOT / "quality-previews"
SAMPLE_RATE = 44100
SAMPLES = {"oui", "non", "je suis", "bonjour", "merci", "j'ai", "c'est"}

def utterance(s):
    s = s.strip()
    if not s:
        raise ValueError("Empty phrase")
    if s[-1] not in ".!?…":
        s += "."
    return s[0].upper() + s[1:]

def encode_ogg(samples, output):
    buffer = io.BytesIO()
    sf.write(buffer, samples, SAMPLE_RATE, format="WAV", subtype="PCM_16")
    proc = subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
         "-i", "pipe:0", "-ac", "1", "-ar", str(SAMPLE_RATE),
         "-c:a", "libvorbis", "-q:a", "8", str(output)],
        input=buffer.getvalue(), capture_output=True)
    if proc.returncode:
        raise RuntimeError(proc.stderr.decode("utf-8", "replace"))
    if output.stat().st_size < 1800:
        raise RuntimeError(f"Small output: {output}")

def main():
    items = json.loads(WORDS.read_text(encoding="utf-8"))
    assert len(items) == 1000
    assert {v["id"] for v in items} == set(range(1, 1001))
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    tts = TTS(model="supertonic-3", auto_download=True)
    female = tts.get_voice_style(voice_name="F2")
    report = []
    for idx, item in enumerate(items, start=1):
        word = item["fr"].strip()
        word_id = item["id"]
        output = OUT / f"{word_id:04d}.ogg"
        np.random.seed(110000 + word_id)
        signal, duration = tts.synthesize(
            utterance(word), voice_style=female, lang="fr",
            total_steps=10, speed=0.98, verbose=False)
        samples = np.asarray(signal, dtype=np.float32).reshape(-1)
        if not np.isfinite(samples).all() or samples.size < SAMPLE_RATE * 0.16:
            raise RuntimeError(f"Invalid sound #{word_id}: {word}")
        peak = float(np.max(np.abs(samples)))
        if peak < 0.0005:
            raise RuntimeError(f"Unvoiced sound #{word_id}: {word}")
        active = np.flatnonzero(np.abs(samples) > max(0.00015, peak * 0.004))
        first = max(0, int(active[0]) - int(SAMPLE_RATE * 0.025))
        last = min(samples.size, int(active[-1]) + int(SAMPLE_RATE * 0.045))
        samples = samples[first:last]
        samples = np.pad(samples, (int(SAMPLE_RATE * 0.035), int(SAMPLE_RATE * 0.065)))
        seconds = samples.size / SAMPLE_RATE
        if not 0.2 <= seconds <= 11:
            raise RuntimeError(f"Unusual duration ({seconds:.2f}s) #{word_id}: {word}")
        encode_ogg(samples, output)
        if word.casefold() in SAMPLES:
            (PREVIEWS / output.name).write_bytes(output.read_bytes())
            report.append({"id": word_id, "word": word, "utterance": utterance(word),
                           "duration_s": round(seconds, 3), "bytes": output.stat().st_size})
        if idx == 1 or idx % 50 == 0:
            print(f"French female F2: {idx}/1000", flush=True)
    assert len(list(OUT.glob("*.ogg"))) == 1000
    (OUT / "voice_manifest.json").write_text(json.dumps({
        "engine": "Supertonic 3", "model": "Supertone/supertonic-3",
        "voice": "F2", "language": "fr", "sample_rate_hz": SAMPLE_RATE,
        "steps": 10, "items": 1000, "license": "BigScience OpenRAIL-M",
        "disclosure": "AI-generated French female voice; native-speaker QA not complete"
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    (PREVIEWS / "qa_samples.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Successfully generated 1000 offline French female clips.")

if __name__ == "__main__":
    main()
