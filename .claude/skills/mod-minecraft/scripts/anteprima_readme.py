"""Builds a README preview picture (docs/<mod>-preview.png) from game screenshots: a row of small tiles
and a row of big tiles, each with a yellow label on black, like the other previews of the repository.

  python3 anteprima_readme.py <out.png> --small "a.png=Livello 0" "b.png=Livello 5" ... \
      --big "c.png=Livello 25" "d.png=Scoreggia (tasto Z)=240,100,1140,640"

A tile is "file=label" or "file=label=x0,y0,x1,y1" (a crop of the screenshot, in pixels, before fitting).
The small row has 6 tiles of 300x200, the big row 3 tiles of 600x360 (1800 px wide in all).
"""
import argparse, os
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def tile(spec, w, h, size):
    parts = spec.split('=')
    name, label = parts[0], parts[1] if len(parts) > 1 else ''
    im = Image.open(name).convert('RGB')
    if len(parts) > 2:
        im = im.crop(tuple(int(v) for v in parts[2].split(',')))
    r = max(w / im.width, h / im.height)
    im = im.resize((int(im.width * r + 0.5), int(im.height * r + 0.5)), Image.LANCZOS)
    x, y = (im.width - w) // 2, (im.height - h) // 2
    im = im.crop((x, y, x + w, y + h))
    if label:
        f = ImageFont.truetype(FONT, size) if os.path.exists(FONT) else ImageFont.load_default()
        d = ImageDraw.Draw(im)
        tw = d.textlength(label, font=f)
        d.rectangle((8, 8, 8 + tw + 14, 8 + size + 12), fill=(0, 0, 0))
        d.text((15, 13), label, font=f, fill=(255, 225, 90))
    return im


ap = argparse.ArgumentParser()
ap.add_argument('out')
ap.add_argument('--small', nargs='*', default=[])
ap.add_argument('--big', nargs='*', default=[])
a = ap.parse_args()
rows = []
if a.small:
    rows.append([tile(s, 1800 // len(a.small), 200, 18) for s in a.small])
if a.big:
    rows.append([tile(s, 1800 // len(a.big), 360, 22) for s in a.big])
height = sum(r[0].height for r in rows)
sheet = Image.new('RGB', (1800, height), (20, 20, 20))
y = 0
for r in rows:
    x = 0
    for t in r:
        sheet.paste(t, (x, y))
        x += t.width
    y += r[0].height
sheet.save(a.out, optimize=True)
print(a.out, sheet.size, os.path.getsize(a.out), 'bytes')
