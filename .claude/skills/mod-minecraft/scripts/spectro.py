# Spectrograms + loudness strips of OGG files, to check a synthesized sound by eye: python3 spectro.py <out.png> <files.ogg...>
import numpy as np, subprocess, sys, os
from PIL import Image, ImageDraw
SR = 44100
files = sys.argv[2:]
rows = []
for p in files:
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', p, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    x = np.frombuffer(raw, '<i2').astype(float) / 32768
    N, H = 2048, 256
    win = np.hanning(N)
    frames = [np.abs(np.fft.rfft(x[i:i + N] * win)) for i in range(0, max(1, len(x) - N), H)]
    S = np.array(frames).T[:300]          # up to ~6.4 kHz
    S = 20 * np.log10(S + 1e-6)
    S = np.clip((S - S.max() + 70) / 70, 0, 1)
    img = (S[::-1] * 255).astype(np.uint8)
    w = img.shape[1]
    im = Image.fromarray(img).resize((max(200, w * 2), 200))
    # envelope strip
    env = np.array([np.sqrt(np.mean(x[i:i + H] ** 2)) for i in range(0, len(x), H)])
    strip = Image.new('L', (im.width, 60), 0)
    d = ImageDraw.Draw(strip)
    for i, e in enumerate(env):
        xx = int(i / len(env) * im.width)
        d.line([(xx, 60), (xx, 60 - int(min(1, e * 3) * 58))], fill=200)
    both = Image.new('L', (im.width, 270), 40)
    both.paste(im, (0, 0)); both.paste(strip, (0, 205))
    ImageDraw.Draw(both).text((4, 4), os.path.basename(p) + f' {len(x)/SR:.2f}s', fill=255)
    rows.append(both)
W = max(r.width for r in rows)
sheet = Image.new('L', (W, sum(r.height for r in rows)), 20)
y = 0
for r in rows:
    sheet.paste(r, (0, y)); y += r.height
sheet.save(sys.argv[1])
