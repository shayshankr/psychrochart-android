"""
HVAC Suite launcher icons — Option 1: Airflow + Thermometer (teal)
Generates PNG icons at every Android density + updates adaptive-icon XMLs.
"""
from PIL import Image, ImageDraw
import math, os

RES_DIR = r"C:\Users\shays\Desktop\interview\psychrochart-android\app\src\main\res"

DENSITIES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

TEAL_BG = (0,   105,  92)   # #00695C
WHITE   = (255, 255, 255)
AMBER   = (255, 179,   0)   # #FFB300


def draw_icon(size: int) -> Image.Image:
    """Draw the HVAC Suite icon at `size` × `size` pixels."""
    img  = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    s    = size / 108.0   # scale: design is in 108dp units

    # ── Background (full square; Android clips to shape) ──────────────────
    draw.rectangle([0, 0, size - 1, size - 1], fill=TEAL_BG)

    # ── 3 airflow sine waves ──────────────────────────────────────────────
    lw   = max(2, round(5.5 * s))
    x0   = 12 * s
    x1   = 74 * s
    ampl = 11 * s
    for yc_dp in (30.0, 54.0, 78.0):
        yc  = yc_dp * s
        pts = [
            (x0 + t / 80.0 * (x1 - x0),
             yc - ampl * math.sin(t / 80.0 * 2 * math.pi))
            for t in range(81)
        ]
        draw.line(pts, fill=WHITE, width=lw)

    # ── Thermometer ───────────────────────────────────────────────────────
    cx      = 87 * s          # shaft centre-x
    sh_hw   = 3  * s          # shaft half-width  (6 dp total)
    sh_top  = 20 * s          # top of shaft
    sh_bot  = 75 * s          # bottom of shaft (flush with bulb top)
    bl_cy   = 83 * s          # bulb centre-y
    bl_r    = 8  * s          # bulb outer radius
    mc_top  = 47 * s          # mercury top inside shaft
    mc_hw   = 1.6 * s         # mercury half-width in shaft
    mc_r    = 5.5 * s         # mercury radius in bulb

    # Shaft body (white rectangle + rounded top cap)
    draw.rectangle([cx - sh_hw, sh_top, cx + sh_hw, sh_bot], fill=WHITE)
    draw.ellipse([cx - sh_hw, sh_top - sh_hw,
                  cx + sh_hw, sh_top + sh_hw], fill=WHITE)

    # Bulb (white circle)
    draw.ellipse([cx - bl_r, bl_cy - bl_r,
                  cx + bl_r, bl_cy + bl_r], fill=WHITE)

    # Mercury column in shaft (amber)
    draw.rectangle([cx - mc_hw, mc_top, cx + mc_hw, sh_bot], fill=AMBER)

    # Mercury in bulb (amber)
    draw.ellipse([cx - mc_r, bl_cy - mc_r,
                  cx + mc_r, bl_cy + mc_r], fill=AMBER)

    return img


# ── XML templates ──────────────────────────────────────────────────────────────

ADAPTIVE_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

BACKGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FF00695C"/>
</shape>
"""

FOREGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Airflow wave 1 (top) -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="5.5"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:pathData="M 12,30 Q 26,20 43,30 Q 60,40 74,30"/>

    <!-- Airflow wave 2 (middle) -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="5.5"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:pathData="M 12,54 Q 26,44 43,54 Q 60,64 74,54"/>

    <!-- Airflow wave 3 (bottom) -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="5.5"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:pathData="M 12,78 Q 26,68 43,78 Q 60,88 74,78"/>

    <!-- Thermometer shaft: white rounded-top flat-bottom capsule -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 84,23 a3,3,0,0,1,6,0 L 90,75 L 84,75 Z"/>

    <!-- Thermometer bulb: white circle -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 87,83 m-8,0 a8,8,0,1,0,16,0 a8,8,0,1,0,-16,0"/>

    <!-- Mercury in shaft: amber fill -->
    <path
        android:fillColor="#FFB300"
        android:pathData="M 85.5,47 L 88.5,47 L 88.5,75 L 85.5,75 Z"/>

    <!-- Mercury in bulb: amber fill -->
    <path
        android:fillColor="#FFB300"
        android:pathData="M 87,83 m-5.5,0 a5.5,5.5,0,1,0,11,0 a5.5,5.5,0,1,0,-11,0"/>

    <!-- Tick marks on thermometer shaft -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.5"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:pathData="M 91,35 L 95,35 M 91,50 L 95,50 M 91,65 L 95,65"/>

</vector>
"""


def main():
    drawable_dir = os.path.join(RES_DIR, "drawable")
    anydpi_dir   = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    os.makedirs(drawable_dir, exist_ok=True)
    os.makedirs(anydpi_dir,   exist_ok=True)

    # ── PNG icons at every density ─────────────────────────────────────────
    for folder, px in DENSITIES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        icon = draw_icon(px)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = os.path.join(out_dir, name)
            icon.save(path)
            print(f"  Saved  {path}  ({px}x{px})")

    # ── Adaptive icon XML (API 26+) ────────────────────────────────────────
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = os.path.join(anydpi_dir, name)
        with open(path, "w", encoding="utf-8") as f:
            f.write(ADAPTIVE_XML)
        print(f"  Wrote  {path}")

    # ── Drawable: background colour ────────────────────────────────────────
    bg_path = os.path.join(drawable_dir, "ic_launcher_background.xml")
    with open(bg_path, "w", encoding="utf-8") as f:
        f.write(BACKGROUND_XML)
    print(f"  Wrote  {bg_path}")

    # ── Drawable: foreground vector ────────────────────────────────────────
    fg_path = os.path.join(drawable_dir, "ic_launcher_foreground.xml")
    with open(fg_path, "w", encoding="utf-8") as f:
        f.write(FOREGROUND_XML)
    print(f"  Wrote  {fg_path}")

    print("\nAll launcher icon assets written successfully.")


if __name__ == "__main__":
    main()
