import math, random, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import jigsaw

W = H = 1100
OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)


def test_art(w, h):
    """Colourful stand-in artwork so piece outlines are easy to judge."""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=(int(255 * (0.15 + 0.25 * t)),
                                       int(90 + 90 * t),
                                       int(180 - 110 * t)))
    for i in range(14):
        r = random.Random(i)
        x, y = r.randint(0, w), r.randint(0, h)
        rad = r.randint(30, 130)
        c = (r.randint(0, 255), r.randint(120, 255), r.randint(120, 255))
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=c)
    return img.filter(ImageFilter.GaussianBlur(6))


def draw_pieces(base, pieces):
    out = base.copy()
    for p in pieces:
        mask = Image.new("L", base.size, 0)
        ImageDraw.Draw(mask).polygon(p["ring"], fill=255)
        layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
        layer.paste(base, (0, 0), mask)
        out = Image.alpha_composite(out.convert("RGBA"), layer).convert("RGB")
    return out


art = test_art(W, H)
cols = rows = 4
pieces, pw, ph = jigsaw.build(cols, rows, 0, 0, W, H, seed=11)

assembled = draw_pieces(art, pieces)
draw = ImageDraw.Draw(assembled)
for p in pieces:
    draw.line(p["ring"] + [p["ring"][0]], fill=(255, 255, 255), width=2)
assembled.save(os.path.join(OUT, "assembled.png"))
print("pieces", len(pieces), "pw", round(pw, 1), "ph", round(ph, 1))
print("ring pts", len(pieces[0]["ring"]))
