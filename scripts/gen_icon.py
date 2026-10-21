"""Genera l'icona adattiva di Chiarella partendo dal logo in branding/chiarella_logo.png.

- primo piano (logo) in PNG per ogni densità, dimensionato perché ogni pixel del logo
  stia nella zona sicura (cerchio di 66 dp su 108 dp): nessuna maschera lo taglia;
- sfondo vettoriale chiaro (avorio -> salvia) con un alone luminoso dietro al viso;
- versione monocromatica per le icone a tema di Android 13+;
- icona 512 px per il Play Store e un'anteprima con le varie maschere.

Uso: python scripts/gen_icon.py   (richiede Pillow e numpy)
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
LOGO = ROOT / "branding" / "chiarella_logo.png"
RES = ROOT / "app" / "src" / "main" / "res"

BG_TOP = "F8F4EC"         # avorio caldo
BG_BOTTOM = "D6E6DA"      # salvia tenue
GLOW = "FFFFFF"
SAFE_RADIUS_DP = 33.0     # raggio della zona sicura (66 dp di diametro)
MARGIN_DP = 1.5           # piccolo respiro dal bordo della zona sicura
SPLASH_RADIUS_DP = 34.5   # splash: cerchio visibile di 36 dp di raggio
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def load_logo():
    im = Image.open(LOGO).convert("RGBA")
    a = np.asarray(im)
    ys, xs = np.nonzero(a[:, :, 3] > 8)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    alpha = np.asarray(im)[:, :, 3]
    ys, xs = np.nonzero(alpha > 8)
    cx, cy = im.width / 2, im.height / 2
    # Distanza massima dal centro di un pixel visibile: è lei che deve stare nella zona sicura.
    max_r = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2).max()
    return im, max_r


def resize(im, size):
    # Ridimensionamento con alfa premoltiplicato: niente aloni scuri sui bordi.
    return im.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def foreground(logo, max_r, px_per_dp, monochrome=False, radius_dp=SAFE_RADIUS_DP - MARGIN_DP):
    canvas = int(round(108 * px_per_dp))
    scale = radius_dp * px_per_dp / max_r
    w, h = max(1, round(logo.width * scale)), max(1, round(logo.height * scale))
    art = resize(logo, (w, h))
    if monochrome:
        alpha = art.getchannel("A")
        art = Image.new("RGBA", art.size, (255, 255, 255, 255))
        art.putalpha(alpha)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.alpha_composite(art, ((canvas - w) // 2, (canvas - h) // 2))
    return out


def background_png(size):
    """Versione bitmap dello sfondo vettoriale (per anteprime e Play Store)."""
    t = np.linspace(0, 1, size)
    gx, gy = np.meshgrid(t, t)
    k = ((gx + gy) / 2)[..., None]
    top = np.array([int(BG_TOP[i:i + 2], 16) for i in (0, 2, 4)])
    bot = np.array([int(BG_BOTTOM[i:i + 2], 16) for i in (0, 2, 4)])
    img = top * (1 - k) + bot * k
    d = np.sqrt((gx - 0.5) ** 2 + (gy - 0.44) ** 2) / 0.42
    glow = np.clip(1 - d, 0, 1)[..., None] ** 1.6 * 0.55
    img = img * (1 - glow) + 255 * glow
    return Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB").convert("RGBA")


BACKGROUND_XML = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0" android:color="#FF{BG_TOP}" />
                <item android:offset="1" android:color="#FF{BG_BOTTOM}" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M54,47.5m-45.4,0a45.4,45.4 0,1 1,90.8 0a45.4,45.4 0,1 1,-90.8 0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial"
                android:centerX="54" android:centerY="47.5" android:gradientRadius="45.4">
                <item android:offset="0" android:color="#8C{GLOW}" />
                <item android:offset="0.55" android:color="#33{GLOW}" />
                <item android:offset="1" android:color="#00{GLOW}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def mask_preview(icon, kind, size):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if kind == "cerchio":
        d.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif kind == "squircle":
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=size * 0.3, fill=255)
    else:  # goccia
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=size * 0.5, fill=255)
        d.rectangle((size // 2, size // 2, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(icon.resize((size, size), Image.LANCZOS), (0, 0), m)
    return out


def main():
    logo, max_r = load_logo()
    for name, density in DENSITIES.items():
        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        foreground(logo, max_r, density).save(folder / "ic_launcher_foreground.png", optimize=True)
        foreground(logo, max_r, density, monochrome=True).save(folder / "ic_launcher_monochrome.png", optimize=True)

    # Splash: logo bianco senza cerchio di sfondo. Android mostra l'icona ritagliata in un
    # cerchio di 2/3 del lato (36 dp su 108): il logo sta tutto in un raggio di 35 dp.
    for name, density in DENSITIES.items():
        folder = RES / f"drawable-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        foreground(logo, max_r, density, monochrome=True, radius_dp=SPLASH_RADIUS_DP).save(
            folder / "splash_logo.png", optimize=True)
    # Logo bianco ritagliato, per l'interfaccia (onboarding).
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    white = resize(logo, (720, round(720 * logo.height / logo.width)))
    alpha = white.getchannel("A")
    white = Image.new("RGBA", white.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    white.save(nodpi / "logo_white.png", optimize=True)

    (RES / "drawable" / "ic_launcher_background.xml").write_text(BACKGROUND_XML, encoding="utf-8")
    for n in ("ic_launcher", "ic_launcher_round"):
        (RES / "mipmap-anydpi-v26" / f"{n}.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    for old in ("ic_launcher_foreground.xml", "ic_launcher_monochrome.xml"):
        (RES / "drawable" / old).unlink(missing_ok=True)

    # Icona piena (Play Store) e anteprima delle maschere. L'icona adattiva è 108 dp,
    # di cui l'utente vede i 72 dp centrali: per l'anteprima si ritaglia quella parte.
    full = background_png(1080)
    full.alpha_composite(foreground(logo, max_r, 10))
    visible = full.crop((180, 180, 900, 900))
    out_dir = ROOT / "branding"
    visible.resize((512, 512), Image.LANCZOS).convert("RGB").save(out_dir / "chiarella_icon_512.png")
    sheet = Image.new("RGBA", (3 * 300 + 4 * 40, 380), (17, 20, 38, 255))
    for i, kind in enumerate(("cerchio", "squircle", "goccia")):
        sheet.alpha_composite(mask_preview(visible, kind, 300), (40 + i * 340, 40))
    sheet.convert("RGB").save(out_dir / "anteprima_icona.png")

    # Anteprima dello splash: fondo notte, logo bianco nel cerchio visibile.
    splash = Image.new("RGBA", (1080, 1080), (8, 11, 25, 255))
    icon = foreground(logo, max_r, 10, monochrome=True, radius_dp=SPLASH_RADIUS_DP)
    m = Image.new("L", (1080, 1080), 0)
    ImageDraw.Draw(m).ellipse((180, 180, 900, 900), fill=255)
    layer = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    layer.paste(icon, (0, 0), m)
    splash.alpha_composite(layer)
    splash.crop((90, 90, 990, 990)).resize((450, 450), Image.LANCZOS).convert("RGB").save(out_dir / "anteprima_splash.png")
    print(f"logo {logo.width}x{logo.height}, raggio max {max_r:.0f}px -> scala zona sicura ok")


if __name__ == "__main__":
    main()
