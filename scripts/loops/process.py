"""Trasforma le registrazioni in loop senza giunture udibili e le esporta per l'app.

Per ogni file:
  1. File lunghi: cerca i punti di loop dove inizio e fine si somigliano di più (volume e timbro),
     preferendo momenti quieti, allinea la fase per i suoni tonali e applica una dissolvenza
     incrociata "post-fine -> inizio": il loop risultante è continuo per costruzione.
     File corti (< 20 s): sintesi granulare circolare, cioè un flusso lungo ricomposto in ordine
     casuale da frammenti della registrazione (anche leggermente ri-intonati), così lo schema non
     si riconosce.
  2. Normalizza la loudness (EBU R128) con limiter applicato "ad anello" (padding circolare),
     così il limiter non crea gradini proprio nel punto di loop.
  3. Esporta in OGG Vorbis nella cartella res/raw dell'app e crea un'anteprima della giuntura.

Uso: python process.py <cartella_sorgenti> <cartella_raw> <cartella_anteprime>
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

from common import SR, db, decode, loudness, seam_report, write_wav

TARGET_LUFS = -24.0
CEILING_DBTP = -1.0
MAX_LIMIT_DB = 4.0  # riduzione massima concessa al limiter: oltre si accetta un loop un po' più basso
RNG = np.random.default_rng(20261020)

NAMES = {
    "Asciugacapelli": "phon",
    "Cascata": "cascata",
    "Falo'": "falo",
    "Fiume": "fiume",
    "Foresta": "foresta",
    "Grilli": "grilli",
    "Mare": "mare",
    "Pioggia": "pioggia_dolce",
    "Stufa Elettrica": "stufa_elettrica",
    "Temporale": "temporale",
    "Vento": "vento",
}

# ------------------------------------------------------------------ analisi a frame

HOP = 0.1  # s
BANDS = np.geomspace(60, 16000, 9)


def frame_features(x):
    """Livello (dB) e energia per 8 bande di frequenza (dB) ogni 100 ms."""
    mono = x.mean(axis=1)
    hop = int(HOP * SR)
    win = 4096
    n = (len(mono) - win) // hop
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    frames = mono[idx] * np.hanning(win)
    spec = np.abs(np.fft.rfft(frames, axis=1)) ** 2
    freqs = np.fft.rfftfreq(win, 1 / SR)
    bands = np.stack(
        [spec[:, (freqs >= lo) & (freqs < hi)].sum(axis=1) for lo, hi in zip(BANDS[:-1], BANDS[1:])], axis=1
    )
    level = 10 * np.log10(spec.sum(axis=1) + 1e-12)
    bands_db = 10 * np.log10(bands + 1e-12)
    bands_db -= level[:, None]  # forma spettrale indipendente dal volume
    return level, bands_db


def best_loop_points(x, xfade):
    """Sceglie (s, e): il loop è x[s:e] e la coda x[e:e+xfade] sfuma dentro x[s:s+xfade]."""
    D = len(x) / SR
    level, bands = frame_features(x)
    med = np.median(level)
    k = int(xfade / HOP)
    s_max = min(0.12 * D, 25.0)
    e_min = max(0.88 * D, D - 25.0 - xfade)
    s_idx = np.arange(0, int(s_max / HOP))
    e_idx = np.arange(int(e_min / HOP), int((D - xfade) / HOP) - 1 - (4096 // int(HOP * SR)))
    e_idx = e_idx[e_idx + k < len(level)]
    s_idx = s_idx[s_idx + k < len(level)]

    win = np.arange(k)
    Ls, Le = level[s_idx[:, None] + win], level[e_idx[:, None] + win]          # (S,k), (E,k)
    Bs, Be = bands[s_idx[:, None] + win], bands[e_idx[:, None] + win]          # (S,k,8), (E,k,8)

    lvl_cost = np.abs(Ls[:, None, :] - Le[None, :, :]).mean(axis=2)            # (S,E)
    spec_cost = np.abs(Bs.mean(axis=1)[:, None, :] - Be.mean(axis=1)[None, :, :]).mean(axis=2)
    loud_s = np.maximum(0, Ls.mean(axis=1) - med)
    loud_e = np.maximum(0, Le.mean(axis=1) - med)
    loud_cost = 0.3 * (loud_s[:, None] + loud_e[None, :])
    # Varianza del livello nella finestra: dissolvere durante un picco (onda, tuono) si sente di più.
    var_cost = 0.15 * (Ls.std(axis=1)[:, None] + Le.std(axis=1)[None, :])
    discard = (s_idx[:, None] * HOP) + (D - e_idx[None, :] * HOP)
    cost = lvl_cost + 0.5 * spec_cost + loud_cost + var_cost + 0.01 * discard
    i, j = np.unravel_index(np.argmin(cost), cost.shape)
    return int(s_idx[i] * HOP * SR), int(e_idx[j] * HOP * SR), float(cost[i, j])


def align_phase(x, s, e, xfade_n):
    """Sposta di poco il punto di fine per massimizzare la correlazione (suoni tonali: niente battimenti)."""
    mono = x.mean(axis=1)
    n = int(0.3 * SR)
    ref = mono[s:s + n]
    best, best_c = 0, -2.0
    max_lag = int(0.025 * SR)
    for lag in range(-max_lag, max_lag + 1, 2):
        a = e + lag
        if a < s or a + xfade_n > len(mono):
            continue
        seg = mono[a:a + n]
        c = np.dot(ref, seg) / (np.linalg.norm(ref) * np.linalg.norm(seg) + 1e-12)
        if c > best_c:
            best, best_c = lag, c
    return (e + best, best_c) if best_c > 0.3 else (e, best_c)


def crossfade_curves(n, corr):
    """Curve di dissolvenza: a potenza costante per materiale scorrelato, lineari se correlato."""
    t = (np.arange(n) + 0.5) / n
    w = float(np.clip(corr, 0, 1))
    fade_in = w * t + (1 - w) * np.sin(np.pi * t / 2)
    fade_out = w * (1 - t) + (1 - w) * np.cos(np.pi * t / 2)
    return fade_in[:, None], fade_out[:, None]


def seal_loop(x):
    D = len(x) / SR
    xfade = float(np.clip(D * 0.03, 3.0, 6.0))
    n = int(xfade * SR)
    s, e, cost = best_loop_points(x, xfade)
    e, corr = align_phase(x, s, e, n)
    head = x[s:s + n]
    post = x[e:e + n]
    c = np.sum(head * post) / (np.linalg.norm(head) * np.linalg.norm(post) + 1e-12)
    fin, fout = crossfade_curves(n, c)
    y = x[s:e].copy()
    y[:n] = head * fin + post * fout
    info = f"loop {s/SR:.2f}s -> {e/SR:.2f}s, dissolvenza {xfade:.1f}s, costo {cost:.2f}, corr {c:+.2f}"
    return y, info


# ------------------------------------------------------------------ sintesi granulare (file corti)

def granular_loop(path, length_s, gmin, gmax, fade_s):
    pools = [decode(path)]
    for speed in (0.94, 1.06):  # stessa sorgente leggermente ri-intonata: più varietà, suono naturale
        pools.append(decode(path, filt=f"aresample={SR},asetrate={int(SR * speed)}"))
    T = int(length_s * SR)
    F = int(fade_s * SR)
    lengths = []
    while sum(lengths) < T:
        lengths.append(int(RNG.uniform(gmin, gmax) * SR))
    lengths = (np.array(lengths) * T / sum(lengths)).astype(int)
    lengths[-1] += T - lengths.sum()

    t = (np.arange(F) + 0.5) / F
    fin = np.sin(np.pi * t / 2)[:, None]
    fout = np.cos(np.pi * t / 2)[:, None]
    y = np.zeros((T, 2))
    cursor = 0
    prev = None  # (pool, fine del grano precedente nella sorgente)
    for L in lengths:
        need = L + F
        if prev is not None and RNG.random() < 0.35 and prev[1] + need <= len(pools[prev[0]]):
            p, start = prev[0], prev[1]  # a volte prosegue in modo contiguo: flusso più naturale
        else:
            p = int(RNG.integers(len(pools)))
            start = int(RNG.integers(0, len(pools[p]) - need))
        g = pools[p][start:start + need].copy()
        g *= 10 ** (RNG.uniform(-1.0, 1.0) / 20)
        g[:F] *= fin
        g[-F:] *= fout
        idx = (cursor + np.arange(need)) % T  # l'ultimo grano si richiude sull'inizio: loop perfetto
        np.add.at(y, idx, g)
        cursor += L
        prev = (p, start + L)
    return y, f"sintesi granulare {len(lengths)} frammenti, {length_s:.0f}s, dissolvenze {fade_s:.2f}s"


SHORT = {"falo": (75, 0.7, 1.5, 0.25), "fiume": (90, 1.5, 3.2, 0.5)}

# ------------------------------------------------------------------ loudness + export


def circular_process(y, af, tmp):
    """Applica un filtro ffmpeg con padding circolare: nessun transitorio al punto di loop."""
    pad = int(3 * SR)
    padded = np.concatenate([y[-pad:], y, y[:pad]])
    src, dst = tmp / "in.wav", tmp / "out.wav"
    write_wav(src, padded)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-af", af, "-c:a", "pcm_f32le", str(dst)], check=True)
    out = decode(dst)
    return out[pad:pad + len(y)]


def main():
    src_dir, raw_dir, prev_dir = map(Path, sys.argv[1:4])
    prev_dir.mkdir(parents=True, exist_ok=True)
    report = []
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for f in sorted(src_dir.glob("*.wav")):
            key = f.name.split("_loop_")[0]
            name = NAMES[key]
            if name in SHORT:
                y, how = granular_loop(f, *SHORT[name])
            else:
                y, how = seal_loop(decode(f))

            # Loudness: misura, calcola il guadagno e limita solo il necessario.
            write_wav(tmp / "m.wav", y)
            lufs, tp = loudness(tmp / "m.wav")
            gain = TARGET_LUFS - lufs
            over = tp + gain - CEILING_DBTP
            if over > MAX_LIMIT_DB:
                gain -= over - MAX_LIMIT_DB
            ceiling = 10 ** ((CEILING_DBTP - 0.5) / 20)
            y = circular_process(
                y, f"volume={gain:.2f}dB,alimiter=limit={ceiling:.4f}:attack=5:release=80:level=disabled", tmp
            )

            out = raw_dir / f"{name}.ogg"
            write_wav(tmp / "final.wav", y)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp / "final.wav"),
                            "-c:a", "libvorbis", "-q:a", "4", str(out)], check=True)

            # Verifica sul file codificato, come lo sentirà l'app.
            dec = decode(out)
            seam = seam_report(dec)
            lufs2, tp2 = loudness(out)
            # Anteprima: ultimi 6 s + primi 6 s, cioè esattamente il punto in cui il loop riparte.
            n6 = 6 * SR
            write_wav(tmp / "p.wav", np.concatenate([dec[-n6:], dec[:n6]]))
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(tmp / "p.wav"), "-c:a", "libmp3lame",
                            "-q:a", "2", str(prev_dir / f"{name}_giuntura.mp3")], check=True)

            line = (f"{name:16} {len(dec)/SR:6.1f}s  {lufs2:6.1f} LUFS  TP {tp2:5.1f}  "
                    f"giuntura: salto pct {seam['jump_percentile']:5.1f}, fine/inizio {seam['tail_vs_head_db']:+5.1f} dB  "
                    f"| {how}")
            print(line, flush=True)
            report.append(line)
    (prev_dir / "report.txt").write_text("\n".join(report), encoding="utf-8")


if __name__ == "__main__":
    main()
