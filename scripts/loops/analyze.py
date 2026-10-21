"""Analizza i file sorgente: durata, livelli, silenzi agli estremi e qualità della giuntura del loop."""
import sys
from pathlib import Path

import numpy as np

from common import decode, db, loudness, seam_report, SR

src = Path(sys.argv[1])
print(f"{'file':34} {'dur':>7} {'LUFS':>6} {'TP':>5} {'peak':>6} {'seam%':>6} {'tail/head':>9} {'sil_in':>6} {'sil_out':>7}")
for f in sorted(src.glob("*.wav")):
    x = decode(f)
    lufs, tp = loudness(f)
    mono = np.abs(x).max(axis=1)
    thr = 10 ** (-60 / 20)
    loud = np.nonzero(mono > thr)[0]
    sil_in = loud[0] / SR if len(loud) else 0
    sil_out = (len(x) - 1 - loud[-1]) / SR if len(loud) else 0
    s = seam_report(x)
    print(f"{f.name:34} {len(x)/SR:7.2f} {lufs:6.1f} {tp:5.1f} {db(np.abs(x).max()):6.1f} "
          f"{s['jump_percentile']:6.1f} {s['tail_vs_head_db']:9.1f} {sil_in:6.2f} {sil_out:7.2f}")
