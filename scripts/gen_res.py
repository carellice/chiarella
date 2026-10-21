# Genera le risorse vettoriali (icone di notifica e widget) dell'app.
import os

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

MOON = ("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 "
        "-0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4"
        "C12.92,3.04 12.46,3 12,3z")

ICONS = {
    "ic_stat_chiarella": MOON,
    "ic_play": "M8,5.14v13.72c0,0.79 0.87,1.27 1.54,0.84l10.79,-6.86c0.62,-0.39 0.62,-1.29 0,-1.69L9.54,4.3C8.87,3.87 8,4.35 8,5.14z",
    "ic_pause": "M8,19c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v10c0,1.1 0.9,2 2,2zM14,7v10c0,1.1 0.9,2 2,2s2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2z",
    "ic_stop": "M8,6h8c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2H8c-1.1,0 -2,-0.9 -2,-2V8c0,-1.1 0.9,-2 2,-2z",
    "ic_timer_add": "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
}

FILES = {}

for name, path in ICONS.items():
    FILES[f"drawable/{name}.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="{path}" />
</vector>
"""

# L'icona dell'app (sfondo, logo, monocromatica) è generata da scripts/gen_icon.py.

FILES["drawable/widget_bg.xml"] = """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="315" android:startColor="#FF141A3F" android:centerColor="#FF221D58" android:endColor="#FF3A2A78" />
    <corners android:radius="28dp" />
</shape>
"""
FILES["drawable/widget_play_bg.xml"] = """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient android:angle="315" android:startColor="#FF8B7CF6" android:endColor="#FF5EC8F2" />
</shape>
"""
FILES["drawable/widget_chip_bg.xml"] = """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#1FFFFFFF" />
    <stroke android:width="1dp" android:color="#24FFFFFF" />
    <corners android:radius="16dp" />
</shape>
"""
FILES["drawable/widget_chip_active_bg.xml"] = """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="0" android:startColor="#998B7CF6" android:endColor="#995EC8F2" />
    <stroke android:width="1dp" android:color="#66FFFFFF" />
    <corners android:radius="16dp" />
</shape>
"""
FILES["xml/chiarella_widget_info.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="110dp"
    android:minHeight="100dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="100dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:previewImage="@mipmap/ic_launcher"
    android:widgetCategory="home_screen" />
"""

for rel, content in FILES.items():
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("wrote", rel)
