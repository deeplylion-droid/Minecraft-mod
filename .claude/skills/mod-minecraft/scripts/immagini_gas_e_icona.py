"""Gas puff particle textures (16x16, white so the particle tints them) and the mod icon of Chiappe
Colossali, as an example of pixel art made with numpy + PIL (shapes from distance fields, a few shade
levels with light from the top left, a dark outline, NEAREST upscaling).

  python3 immagini_gas_e_icona.py <assets/<mod_id> folder>   (writes textures/particle/gas_0..3.png and icon.png)
"""
import numpy as np, sys, os
from PIL import Image

ROOT = sys.argv[1]
tex = os.path.join(ROOT, 'textures', 'particle')
os.makedirs(tex, exist_ok=True)
rng = np.random.default_rng(7)


def puff(seed, n_blobs):
    r = np.random.default_rng(seed)
    S = 16
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    field = np.zeros((S, S))
    for i in range(n_blobs):
        cx, cy = r.uniform(5, 11), r.uniform(5, 11)
        rad = r.uniform(3.0, 5.2) if i else 4.6
        if i == 0:
            cx, cy = 8, 8.5
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        field = np.maximum(field, 1 - d / rad)
    inside = field > 0
    edge = (field > 0) & (field < 0.22)
    # light from the top left
    shade = 1 - 0.035 * ((xx - 8) + (yy - 8)) - 0.25 * (1 - field)
    levels = np.array([0.62, 0.74, 0.86, 1.0])
    shade = levels[np.clip(np.digitize(shade, [0.7, 0.82, 0.94]), 0, 3)]
    a = np.where(edge, 0.55, 0.9) * inside
    # a few lighter swirls
    for _ in range(3):
        sx, sy = r.integers(4, 12, 2)
        if inside[sy, sx] and not edge[sy, sx]:
            shade[sy, sx] = 1.0
            a[sy, sx] = 1.0
    img = np.zeros((S, S, 4), np.uint8)
    g = (shade * 255).astype(np.uint8)
    img[..., 0] = g
    img[..., 1] = g
    img[..., 2] = g
    img[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


for i, (seed, n) in enumerate([(1, 4), (2, 5), (3, 3), (4, 6)]):
    puff(seed, n).save(os.path.join(tex, f'gas_{i}.png'))

# ------------------------------------------------------------------ icon: two big denim cheeks and a cloud
S = 64
yy, xx = np.mgrid[0:S, 0:S] + 0.5
img = np.zeros((S, S, 4), float)


def put(mask, rgb, alpha=1.0):
    for c in range(3):
        img[..., c] = np.where(mask, rgb[c], img[..., c])
    img[..., 3] = np.where(mask, alpha, img[..., 3])


denim = np.array([60, 66, 150]) / 255
denim_dark = np.array([38, 42, 104]) / 255
denim_light = np.array([96, 104, 196]) / 255
outline = np.array([22, 22, 60]) / 255
cheeks = []
for cx in (21.5, 42.5):
    d = np.sqrt((xx - cx) ** 2 + ((yy - 36) * 1.0) ** 2)
    cheeks.append((cx, d))
hips = (xx > 7) & (xx < 57) & (yy > 12) & (yy < 36)
body = hips.copy()
for cx, d in cheeks:
    body |= d < 15.5
put(body, outline)
inner_all = ((xx > 8.5) & (xx < 55.5) & (yy > 13.5) & (yy < 36)) | (cheeks[0][1] < 14.0) | (cheeks[1][1] < 14.0)
put(inner_all, denim)
for cx, d in cheeks:
    inner = d < 14.0
    # shading: lighter top left of each cheek, darker bottom right
    t = ((xx - cx) * 0.6 + (yy - 36) * 0.8) / 14.0
    shade = np.where(t < -0.35, 2, np.where(t < 0.45, 1, 0))
    col = np.stack([np.choose(shade, [denim_dark[c], denim[c], denim_light[c]]) for c in range(3)], -1)
    m = inner & (yy > 24)
    for c in range(3):
        img[..., c] = np.where(m, col[..., c], img[..., c])
    hl = np.sqrt((xx - (cx - 5)) ** 2 + (yy - 30) ** 2) < 2.6
    put(hl, np.array([170, 178, 240]) / 255)
# the crack, the back pockets' stitches and the waistband with its belt
crack = (np.abs(xx - 32) < 0.9) & (yy > 22) & (yy < 49)
put(crack, outline)
for cx in (21.5, 42.5):
    st = (np.abs(yy - 27) < 0.6) & (np.abs(xx - cx) < 6) & (((xx.astype(int)) % 2) == 0)
    put(st, np.array([210, 170, 90]) / 255)
band = (yy > 13.5) & (yy < 19) & (xx > 8.5) & (xx < 55.5)
put(band, np.array([110, 72, 40]) / 255)
put(band & (np.abs(xx - 32) < 3.5), np.array([220, 190, 80]) / 255)
put(band & (np.abs(xx - 32) < 1.5) & (np.abs(yy - 16) < 1.2), np.array([110, 72, 40]) / 255)
# the cloud, bottom right
cloud = np.zeros((S, S), bool)
for cx, cy, rad in [(52, 56, 6.5), (58, 51, 5), (45, 60, 4.5), (59, 59, 4.5)]:
    cloud |= np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) < rad
cloud &= ~body
put(cloud, np.array([128, 140, 48]) / 255, 0.95)
for cx, cy, rad in [(51, 55, 2.6), (58, 50, 2.0)]:
    put(np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) < rad, np.array([170, 182, 80]) / 255, 0.95)
# stink lines
for x0, y0 in [(5, 3), (12, 1), (54, 2)]:
    for k in range(6):
        x = int(x0 + np.sin(k * 1.3) * 1.5)
        y = y0 + k
        if 0 <= x < S and 0 <= y < S:
            img[y, x] = [128 / 255, 140 / 255, 48 / 255, 1]
icon = Image.fromarray((img * 255).astype(np.uint8), 'RGBA').resize((128, 128), Image.NEAREST)
icon.save(os.path.join(ROOT, 'icon.png'))
print('ok')
