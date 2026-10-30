"""Verifica oggettiva dei loop codificati.

Il file viene "arrotolato" (fine seguita dall'inizio) e analizzato a finestre di 50 ms.
Per la giuntura calcola quanto cambiano livello e timbro (spectral flux) e lo confronta
con tutti gli altri istanti del file: un percentile alto (> 99) indicherebbe uno stacco udibile.
"""
import sys
from pathlib import Path

import numpy as np

from common import SR, decode

W = int(0.05 * SR)


def features(mono):
    n = len(mono) // W
    frames = mono[: n * W].reshape(n, W) * np.hanning(W)
    spec = np.abs(np.fft.rfft(frames, axis=1))
    level = 20 * np.log10(np.sqrt((frames ** 2).mean(axis=1)) + 1e-9)
    norm = spec / (spec.sum(axis=1, keepdims=True) + 1e-12)
    flux = np.r_[0, np.abs(np.diff(norm, axis=0)).sum(axis=1)]
    dlevel = np.r_[0, np.abs(np.diff(level))]
    return dlevel, flux


def pct(series, i):
    return (series < series[i]).mean() * 100


def main():

    print(f"{'suono':16} {'giuntura: livello':>18} {'timbro':>7}   {'esito':>18}")
    worst = 0.0
    for f in sorted(Path(sys.argv[1]).glob("*.ogg")):
        # Questi segnali sono riprodotti una sola volta, non sono loop ambientali.
        if f.stem in ("respiro_inspira", "respiro_espira"):
            continue
        x = decode(f).mean(axis=1)
        # Rotazione: la giuntura finisce a metà file, sul bordo di una finestra.
        half = (len(x) // 2 // W) * W
        rolled = np.r_[x[half:], x[:half]]
        seam = (len(x) - half) // W
        dl, fl = features(rolled)
        # Il massimo in un intorno di ±1 finestra (il cambio può cadere a cavallo).
        s_l = max(pct(dl, seam + k) for k in (-1, 0, 1))
        s_f = max(pct(fl, seam + k) for k in (-1, 0, 1))
        worst = max(worst, s_l, s_f)
        print(f"{f.stem:16} {s_l:17.1f}% {s_f:6.1f}%   {'ok' if max(s_l, s_f) < 99 else 'DA CONTROLLARE':>18}")
    print(f"\npercentile peggiore: {worst:.1f}% (sotto 99% = indistinguibile da un istante qualunque del file)")


if __name__ == "__main__":
    main()
