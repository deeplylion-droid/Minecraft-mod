# Contact sheet: python3 sheet.py <out.png> <columns> <tile width> <images...> (each tile labelled with its file name)
import sys, glob, os
from PIL import Image, ImageDraw
out = sys.argv[1]; cols = int(sys.argv[2]); w = int(sys.argv[3]); files = sys.argv[4:]
ims = [Image.open(f).convert('RGB') for f in files]
h = int(w * ims[0].height / ims[0].width)
rows = (len(ims) + cols - 1) // cols
sheet = Image.new('RGB', (cols * w, rows * h), (20, 20, 20))
for i, (f, im) in enumerate(zip(files, ims)):
    im = im.resize((w, h))
    ImageDraw.Draw(im).text((4, 4), os.path.basename(f)[:-4], fill=(255, 255, 0))
    sheet.paste(im, ((i % cols) * w, (i // cols) * h))
sheet.save(out)
