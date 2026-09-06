"""Export Qetara's existing Android vector to legacy and desktop formats.

Requires Pillow. No network access or new artwork is involved. The path is the
single M/L/Q/H/V/Z contour already used by the adaptive Android launcher icon.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
NS = "{http://schemas.android.com/apk/res/android}"
RES = ROOT / "app/src/main/res"
vector = ET.parse(RES / "drawable/ic_launcher_foreground.xml").getroot()
group = vector.find("group")
path = group.find("path")
tokens = re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+)", path.get(NS + "pathData"))
points = []
x = y = 0.0
i = 0
while i < len(tokens):
    command = tokens[i]
    i += 1
    if command in ("M", "L"):
        x, y = map(float, tokens[i:i + 2])
        i += 2
        points.append((x, y))
    elif command == "Q":
        cx, cy, end_x, end_y = map(float, tokens[i:i + 4])
        i += 4
        for step in range(1, 33):
            t = step / 32
            points.append(((1-t)**2*x + 2*(1-t)*t*cx + t*t*end_x,
                           (1-t)**2*y + 2*(1-t)*t*cy + t*t*end_y))
        x, y = end_x, end_y
    elif command == "H":
        x = float(tokens[i])
        i += 1
        points.append((x, y))
    elif command == "V":
        y = float(tokens[i])
        i += 1
        points.append((x, y))
    elif command != "Z":
        raise ValueError(f"Unsupported path command: {command}")

scale_x = float(group.get(NS + "scaleX", "1"))
scale_y = float(group.get(NS + "scaleY", "1"))
pivot_x = float(group.get(NS + "pivotX", "0"))
pivot_y = float(group.get(NS + "pivotY", "0"))
viewport = float(vector.get(NS + "viewportWidth"))
background = ET.parse(RES / "drawable/ic_launcher_background.xml").getroot().find("path").get(NS + "fillColor")
size = 1024
canvas = Image.new("RGBA", (size, size), background)
coordinates = [(((x-pivot_x)*scale_x+pivot_x)/viewport*size,
                ((y-pivot_y)*scale_y+pivot_y)/viewport*size) for x, y in points]
ImageDraw.Draw(canvas).polygon(coordinates, fill=path.get(NS + "fillColor"))

for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    target = RES / ("mipmap-" + density)
    target.mkdir(exist_ok=True)
    square = canvas.resize((px, px), Image.Resampling.LANCZOS)
    square.save(target / "ic_launcher.webp", lossless=True)
    mask = Image.new("L", (size, size))
    ImageDraw.Draw(mask).ellipse((0, 0, size-1, size-1), fill=255)
    rounded = canvas.copy()
    rounded.putalpha(mask)
    rounded.resize((px, px), Image.Resampling.LANCZOS).save(target / "ic_launcher_round.webp", lossless=True)

desktop = ROOT / "pc/src/main/resources"
canvas.save(desktop / "qetara.png")
canvas.save(desktop / "qetara.ico", sizes=[(16,16), (24,24), (32,32), (48,48), (64,64), (128,128), (256,256)])
canvas.save(desktop / "qetara.icns")
print("Exported existing Qetara vector for Android, Windows, Linux and macOS.")
