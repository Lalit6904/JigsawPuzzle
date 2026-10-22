"""Builds the adaptive-icon foreground/legacy bitmaps and a gradient background."""
import math, os
from PIL import Image, ImageDraw, ImageFilter
import jigsaw

HERE = os.path.dirname(__file__)
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
SS = 4  # supersample


def piece_ring(cell, u):
    """One piece with a tab on all four sides, centred on (0,0)..(cell,cell)."""
    ax, ay, bx, by = 0.0, 0.0, cell, cell
    segs = [
        jigsaw.boundary(ax, ay, bx, ay, -1, u),
        jigsaw.boundary(bx, ay, bx, by, -1, u),
        jigsaw.boundary(bx, by, ax, by, 1, u),
        jigsaw.boundary(ax, by, ax, ay, 1, u),
    ]
    ring = []
    for seg in segs:
        for pt in seg:
            if not ring or math.dist(ring[-1], pt) > 0.05:
                ring.append(pt)
    if math.dist(ring[0], ring[-1]) < 0.05:
        ring.pop()
    return ring


def gradient(size, top, bottom, diagonal=True):
    g = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(g)
    for y in range(size):
        t = y / max(1, size - 1)
        col = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=col)
    return g


def render_piece(canvas_px, cell_px, top, bottom, outline=None, shadow=True):
    """RGBA layer with a single jigsaw piece drawn in a gradient."""
    ring = piece_ring(cell_px, cell_px)
    pad = cell_px * 0.40
    ox, oy = pad, pad
    W = int(cell_px + pad * 2)
    scale = canvas_px / W

    big = int(W * SS)
    f = big / W

    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).polygon([(ox * f + x * f, oy * f + y * f) for (x, y) in ring], fill=255)

    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    grad = gradient(big, top, bottom).convert("RGBA")
    layer.paste(grad, (0, 0), mask)

    if shadow:
        sh = Image.new("L", (big, big), 0)
        sh.paste(mask.filter(ImageFilter.GaussianBlur(big * 0.035)), (0, int(big * 0.022)))
        shadow_layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        shadow_layer.paste(Image.new("RGBA", (big, big), (0, 0, 0, 90)), (0, 0),
                           sh.point(lambda v: int(v * 0.55)))
        layer = Image.alpha_composite(shadow_layer, layer)

    if outline:
        out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        edge = Image.new("L", (big, big), 0)
        ImageDraw.Draw(edge).line([(ox * f + x * f, oy * f + y * f) for (x, y) in ring + [ring[0]]],
                                 fill=255, width=int(big * 0.012), joint="curve")
        out.paste(Image.new("RGBA", (big, big), outline + (255,)), (0, 0), edge)
        layer = Image.alpha_composite(layer, out)

    return layer.resize((canvas_px, canvas_px), Image.LANCZOS)


def write_adaptive():
    # 108dp canvas, artwork kept inside the safe zone
    for density, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
        canvas = px
        cell = int(canvas * 0.42)
        piece = render_piece(canvas, cell, (255, 196, 107), (255, 118, 92), outline=(255, 255, 255))
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        piece.save(os.path.join(folder, "ic_launcher_foreground.png"))
        print("fg", density, px)


def write_legacy():
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        s = px * SS
        bg = gradient(s, (36, 30, 58), (12, 14, 26))
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)
        icon = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        icon.paste(bg, (0, 0), mask)

        cell = int(s * 0.44)
        piece = render_piece(s, cell, (255, 196, 107), (255, 118, 92), outline=(255, 255, 255))
        icon = Image.alpha_composite(icon, piece)
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        icon.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
        print("legacy", density, px)


def write_background():
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    xml = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="#FF241E3A"
        android:endColor="#FF0C0E1A"
        android:type="linear" />
</shape>
"""
    with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    print("background drawable")


if __name__ == "__main__":
    write_adaptive()
    write_legacy()
    write_background()
