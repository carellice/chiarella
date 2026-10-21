"""Utility condivise per la preparazione dei loop audio (decodifica/scrittura via ffmpeg, misure)."""
import json
import re
import subprocess

import numpy as np

SR = 44100  # frequenza di lavoro e di uscita


def decode(path, sr=SR, filt=None):
    """Decodifica qualsiasi file audio in float32 stereo (N, 2) a `sr` Hz (con filtro ffmpeg opzionale)."""
    af = "aresample=resampler=soxr:precision=28"
    if filt:
        af = f"{filt},{af}"
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "2", "-ar", str(sr),
         "-af", af, "-"],
        check=True, capture_output=True,
    ).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)


def write_wav(path, x, sr=SR):
    data = np.clip(x, -1, 1).astype(np.float32).tobytes()
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ac", "2", "-ar", str(sr), "-i", "-",
         "-c:a", "pcm_f32le", str(path)],
        input=data, check=True,
    )


def loudness(path):
    """Loudness integrata (LUFS) e true peak (dBTP) misurati da ffmpeg (EBU R128)."""
    err = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(path), "-af", "loudnorm=print_format=json", "-f", "null", "-"],
        capture_output=True, text=True,
    ).stderr
    data = json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", err, re.S).group(0))
    return float(data["input_i"]), float(data["input_tp"])


def db(x):
    return 20 * np.log10(max(x, 1e-12))


def seam_report(x, sr=SR):
    """Quanto è brusco il passaggio fine -> inizio rispetto al resto del segnale.

    Confronta il salto di campione alla giuntura con la distribuzione dei salti
    normali, e l'energia degli ultimi/primi 50 ms (un 'buco' o un 'gradino' di volume si vede qui).
    """
    mono = x.mean(axis=1)
    diffs = np.abs(np.diff(mono))
    jump = abs(mono[0] - mono[-1])
    pct = (diffs < jump).mean() * 100  # percentile del salto alla giuntura
    w = int(0.05 * sr)
    rms = lambda s: np.sqrt(np.mean(s ** 2) + 1e-20)
    tail, head, body = rms(mono[-w:]), rms(mono[:w]), rms(mono)
    return {
        "jump_percentile": pct,
        "tail_vs_body_db": db(tail / body),
        "head_vs_body_db": db(head / body),
        "tail_vs_head_db": db(tail / head),
    }
