"""Generates the puzzle artwork + launcher icon. Run from the tools folder."""
import math, random, os
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)
S = 1400
K = 2


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def vgrad(size, stops):
    col = Image.new("RGB", (1, size))
    px = col.load()
    for y in range(size):
        t = y / (size - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                px[0, y] = lerp(c0, c1, (t - p0) / max(1e-6, p1 - p0))
                break
        else:
            px[0, y] = stops[-1][1]
    return col.resize((size, size), Image.NEAREST)


def radial_mask(size, core=0.5):
    """0 exactly at the border, 1 inside `core` (fraction of the radius)."""
    n = 80
    m = Image.new("L", (n, n), 0)
    px = m.load()
    R = n / 2.0
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - R + 0.5, y - R + 0.5) / R
            t = min(1.0, max(0.0, (d - core) / (1.0 - core)))
            px[x, y] = int(255 * (1 - t * t * (3 - 2 * t)))
    return m.resize((size, size), Image.BICUBIC)


def glow(img, cx, cy, radius, color, strength=1.0, core=0.25):
    size = int(radius * 2)
    m = radial_mask(size, core).point(lambda v: int(v * strength))
    img.paste(Image.new("RGB", (size, size), color), (int(cx - radius), int(cy - radius)), m)


def soft_ellipse(img, box, color, blur, alpha=255):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse(box, fill=alpha)
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    img.paste(Image.new("RGB", img.size, color), (0, 0), mask)


def fractal(n, rough, seed):
    """1D midpoint displacement, normalised to 0..1."""
    rnd = random.Random(seed)
    pts = [0.0] * (2 ** n + 1)
    pts[0], pts[-1] = rnd.random(), rnd.random()
    step, amp = len(pts) - 1, 1.0
    while step > 1:
        half = step // 2
        for i in range(half, len(pts) - 1, step):
            pts[i] = (pts[i - half] + pts[i + half]) * 0.5 + rnd.uniform(-amp, amp) * 0.5
        amp *= rough
        step = half
    lo, hi = min(pts), max(pts)
    return [(p - lo) / (hi - lo + 1e-9) for p in pts]


def ridge_mask(w, ybase, height, n=7, rough=0.55, seed=0, blur=0.0, skew=0.0):
    hs = fractal(n, rough, seed)
    m = Image.new("L", (w, w), 0)
    pts = []
    for i, h in enumerate(hs):
        x = i / (len(hs) - 1) * w
        # skew pushes the crest along the range for a less symmetric look
        hh = h ** (1.0 + skew)
        pts.append((x, ybase - hh * height))
    pts += [(w, w + 10), (0, w + 10)]
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m.filter(ImageFilter.GaussianBlur(blur)) if blur else m


def grain(img, amount=5):
    n = Image.effect_noise(img.size, amount).convert("L").filter(ImageFilter.GaussianBlur(0.5))
    return ImageChops.add(img, Image.merge("RGB", (n, n, n)), scale=1, offset=-128)


def vignette(img, strength=0.30):
    w = img.size[0]
    bright = radial_mask(w, core=0.45).point(lambda v: 255 - int((255 - v) * strength))
    return ImageChops.multiply(img, Image.merge("RGB", (bright, bright, bright)))


def sun(img, cx, cy, r, core, halo):
    glow(img, cx, cy, r * 6.0, halo, 0.95, core=0.12)
    glow(img, cx, cy, r * 2.2, core, 0.6, core=0.4)
    ImageDraw.Draw(img).ellipse([cx - r, cy - r, cx + r, cy + r], fill=core)


def birds(img, specs, color, w):
    d = ImageDraw.Draw(img)
    for (px, py, s) in specs:
        x, y, s = px * w, py * w, s * w / 700
        wd = max(2, int(w / 480))
        d.arc([x - s * 2, y - s, x, y + s], 205, 335, fill=color, width=wd)
        d.arc([x, y - s, x + s * 2, y + s], 205, 335, fill=color, width=wd)


def overlay(img, draw_fn, alpha):
    """Draw onto a transparent layer then composite at `alpha` (0..1)."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer), layer.size)
    layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"), (0, 0))


# --------------------------------------------------------------- scenes
def scene_dusk(w):
    img = vgrad(w, [(0.00, (30, 18, 56)), (0.16, (64, 32, 80)), (0.34, (136, 58, 86)),
                    (0.46, (204, 90, 84)), (0.54, (242, 142, 96)), (0.595, (252, 196, 140)),
                    (0.62, (140, 80, 104)), (0.80, (52, 30, 62)), (1.00, (16, 10, 26))])
    sun(img, w * 0.615, w * 0.545, w * 0.080, (255, 228, 180), (255, 172, 112))
    for (yy, x0, x1, th, a) in [(0.470, 0.26, 0.90, 0.011, 0.26),
                                (0.505, 0.34, 0.78, 0.007, 0.40),
                                (0.408, 0.08, 0.52, 0.008, 0.20)]:
        soft_ellipse(img, [x0 * w, yy * w, x1 * w, (yy + th) * w], (255, 214, 186), w * 0.013, int(255 * a))
    layers = [
        (0.640, 0.075, [(0.10, 0.60, 3), (0.045, 0.70, 9)], (168, 108, 122)),
        (0.700, 0.095, [(0.13, 0.58, 7), (0.055, 0.72, 2)], (124, 74, 102)),
        (0.775, 0.115, [(0.16, 0.55, 11), (0.060, 0.70, 5)], (78, 44, 78)),
        (0.870, 0.130, [(0.18, 0.52, 2), (0.070, 0.68, 8)], (42, 24, 50)),
        (0.960, 0.120, [(0.16, 0.50, 6), (0.060, 0.66, 1)], (20, 12, 28)),
    ]
    for ybase, hgt, specs, color in layers:
        m = Image.new("L", (w, w), 0)
        for (amp, rough, seed) in specs:
            m = ImageChops.lighter(m, ridge_mask(w, ybase * w, hgt * w * amp / 0.1, n=7, rough=rough,
                                                 seed=seed, blur=w * 0.003, skew=0.6))
        img.paste(Image.new("RGB", (w, w), color), (0, 0), m)
    birds(img, [(0.26, 0.30, 9), (0.315, 0.262, 7), (0.362, 0.322, 6), (0.232, 0.242, 5)], (26, 14, 38), w)
    return img


def scene_sea(w):
    hz = 0.545
    img = vgrad(w, [(0.00, (118, 172, 202)), (0.22, (176, 214, 230)), (0.40, (235, 232, 214)),
                    (0.545, (252, 234, 200)), (0.556, (104, 166, 176)), (0.72, (44, 116, 140)),
                    (1.00, (12, 50, 72))])
    sx, sy, sr = w * 0.225, w * 0.445, w * 0.052
    sun(img, sx, sy, sr, (255, 249, 228), (255, 228, 172))
    # reflection: irregular soft dashes widening towards the viewer
    def refl(d, size):
        rnd2 = random.Random(3)
        for i in range(26):
            t = i / 26
            yy = sy + sr * 1.25 + (t ** 1.55) * w * 0.40
            halfw = sr * (0.18 + 2.1 * t) * rnd2.uniform(0.7, 1.15)
            xx = sx + math.sin(i * 1.7) * sr * 0.35 * t + rnd2.uniform(-1, 1) * sr * 0.3
            h = w * (0.0030 + 0.0035 * t)
            d.rounded_rectangle([xx - halfw, yy, xx + halfw, yy + h], radius=h / 2, fill=(255, 246, 218, 150))
    overlay(img, refl, 0.55)
    def swell(d, size):
        rnd = random.Random(8)
        for i in range(20):
            t = i / 20
            yy = hz * w + w * 0.012 + (t ** 1.7) * w * 0.44
            amp = w * (0.002 + 0.010 * t)
            x0 = rnd.uniform(-0.15, 0.55) * w
            ln = w * (0.18 + 0.55 * t) * rnd.uniform(0.6, 1.0)
            pts = [(x, yy + amp * math.sin((x - x0) / w * 7.0)) for x in
                   [x0 + ln * k / 24 for k in range(25)]]
            d.line(pts, fill=(216, 240, 240, 70), width=max(1, int(w * (0.0012 + 0.0022 * t))))
    overlay(img, swell, 0.6)
    d = ImageDraw.Draw(img)
    bx, by, bs = w * 0.705, w * 0.625, w * 0.070
    d.polygon([(bx - bs, by), (bx + bs * 0.9, by), (bx + bs * 0.62, by + bs * 0.28),
               (bx - bs * 0.76, by + bs * 0.28)], fill=(36, 38, 56))
    d.polygon([(bx + bs * 0.05, by - bs * 1.55), (bx + bs * 0.05, by - bs * 0.04),
               (bx + bs * 0.95, by - bs * 0.04)], fill=(250, 246, 236))
    d.polygon([(bx - bs * 0.02, by - bs * 1.32), (bx - bs * 0.02, by - bs * 0.04),
               (bx - bs * 0.70, by - bs * 0.04)], fill=(234, 226, 214))
    d.line([(bx + bs * 0.05, by - bs * 1.55), (bx + bs * 0.05, by)], fill=(52, 46, 58),
           width=max(2, int(w * 0.0032)))
    birds(img, [(0.40, 0.185, 8), (0.455, 0.150, 6), (0.492, 0.208, 5)], (58, 76, 94), w)
    return img


def scene_night(w):
    img = vgrad(w, [(0.00, (6, 8, 26)), (0.20, (14, 18, 50)), (0.42, (30, 32, 78)),
                    (0.62, (54, 42, 92)), (0.78, (26, 22, 54)), (1.00, (10, 8, 22))])
    rnd = random.Random(9)
    # milky way
    band = Image.new("L", (w, w), 0)
    bd = ImageDraw.Draw(band)
    for i in range(90):
        t = i / 90
        x = w * (-0.25 + t * 1.45)
        y = w * (0.02 + t * 0.80)
        rw, rh = w * (0.10 + 0.06 * math.sin(t * 3.1)), w * 0.045
        bd.ellipse([x - rw, y - rh, x + rw, y + rh], fill=int(20 + 22 * math.sin(t * 6)))
    band = band.filter(ImageFilter.GaussianBlur(w * 0.05))
    img.paste(Image.new("RGB", (w, w), (112, 122, 200)), (0, 0), band)
    d = ImageDraw.Draw(img)
    for i in range(430):
        x, y = rnd.uniform(0, w), rnd.uniform(0, w * 0.88)
        rr = rnd.uniform(0.4, 1.9) * w / 700
        a = int(rnd.uniform(80, 255))
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=rnd.choice([(a, a, 255), (a, a, a), (255, 250, 232)]))
    for i in range(8):
        x, y = rnd.uniform(w * 0.06, w * 0.94), rnd.uniform(w * 0.04, w * 0.58)
        rr = w * 0.005
        glow(img, x, y, rr * 4.5, (200, 214, 255), 0.55, core=0.05)
        d = ImageDraw.Draw(img)
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 255, 255))
        d.line([(x - rr * 3.6, y), (x + rr * 3.6, y)], fill=(238, 242, 255), width=max(1, int(w / 1000)))
        d.line([(x, y - rr * 3.6), (x, y + rr * 3.6)], fill=(238, 242, 255), width=max(1, int(w / 1000)))
    # crescent moon
    mx, my, mr = w * 0.755, w * 0.205, w * 0.074
    glow(img, mx, my, mr * 2.9, (198, 212, 255), 0.6, core=0.08)
    moon = Image.new("L", (w, w), 0)
    md = ImageDraw.Draw(moon)
    md.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=255)
    md.ellipse([mx - mr * 1.48, my - mr * 1.18, mx + mr * 0.40, my + mr * 1.26], fill=0)
    img.paste(Image.new("RGB", (w, w), (250, 246, 226)), (0, 0),
              moon.filter(ImageFilter.GaussianBlur(w * 0.0012)))
    # hills with a lit rim
    for (yb, hgt, seed, col) in [(0.845, 0.075, 12, (24, 26, 52)), (0.925, 0.055, 31, (11, 12, 26))]:
        m = ridge_mask(w, yb * w, hgt * w, n=7, rough=0.58, seed=seed, blur=w * 0.003)
        img.paste(Image.new("RGB", (w, w), col), (0, 0), m)
    d = ImageDraw.Draw(img)
    for i in range(20):
        x, y = rnd.uniform(w * 0.04, w * 0.96), rnd.uniform(w * 0.888, w * 0.972)
        glow(img, x, y, w * 0.009, (255, 190, 110), 0.8, core=0.05)
        ImageDraw.Draw(img).ellipse([x - 2, y - 2, x + 2, y + 2], fill=(255, 230, 180))
    return img


def scene_bloom(w):
    img = vgrad(w, [(0.00, (253, 240, 234)), (0.45, (250, 228, 224)), (1.00, (241, 203, 208))])
    rnd = random.Random(23)
    for i in range(12):
        x, y = rnd.uniform(0, w), rnd.uniform(0, w)
        rr = rnd.uniform(0.06, 0.16) * w
        soft_ellipse(img, [x - rr, y - rr, x + rr, y + rr], (255, 250, 246), rr * 0.6, 55)

    def ell(cx, cy, a, b, ang, n=72):
        ca, sa = math.cos(ang), math.sin(ang)
        return [(cx + a * math.cos(t) * ca - b * math.sin(t) * sa,
                 cy + a * math.cos(t) * sa + b * math.sin(t) * ca)
                for t in [2 * math.pi * i / n for i in range(n)]]

    palette = [(226, 72, 96), (244, 132, 92), (250, 190, 110), (206, 78, 140), (118, 158, 204)]
    specs = [(0.46, 0.42, 0.255, 0), (0.185, 0.205, 0.150, 1), (0.80, 0.175, 0.125, 2),
             (0.155, 0.795, 0.130, 4), (0.835, 0.805, 0.150, 3), (0.585, 0.905, 0.085, 1)]

    shadow = Image.new("L", (w, w), 0)
    sd = ImageDraw.Draw(shadow)
    for (fx, fy, rad, ci) in specs:
        R = rad * w
        for p in range(8):
            ang = p * 2 * math.pi / 8 + ci * 0.3
            sd.polygon(ell(fx * w + math.cos(ang) * R * 0.52, fy * w + math.sin(ang) * R * 0.52,
                           R * 0.36, R * 0.20, ang), fill=52)
        sd.ellipse([fx * w - R * 0.3, fy * w - R * 0.3, fx * w + R * 0.3, fy * w + R * 0.3], fill=60)
    img.paste(Image.new("RGB", (w, w), (144, 90, 100)), (0, 0),
              shadow.filter(ImageFilter.GaussianBlur(w * 0.010)))

    d = ImageDraw.Draw(img)
    for (cx, cy, rr, ang) in [(0.335, 0.635, 0.075, 2.5), (0.70, 0.555, 0.070, 0.8), (0.47, 0.60, 0.060, 1.9),
                              (0.24, 0.45, 0.055, 3.4), (0.665, 0.875, 0.055, 1.2)]:
        d.polygon(ell(cx * w, cy * w, rr * w * 0.98, rr * w * 0.40, ang), fill=(102, 142, 116))
        d.polygon(ell(cx * w, cy * w, rr * w * 0.66, rr * w * 0.12, ang), fill=(126, 166, 136))

    for (fx, fy, rad, ci) in specs:
        cx, cy, R = fx * w, fy * w, rad * w
        base = palette[ci]
        for (scale, tint, off) in [(1.0, 0.0, 0.0), (0.64, 0.26, 0.26), (0.32, 0.50, 0.42)]:
            for p in range(8):
                ang = p * 2 * math.pi / 8 + off + ci * 0.3
                dist = R * 0.52 * scale
                a, b = R * 0.36 * scale, R * 0.20 * scale
                d.polygon(ell(cx + math.cos(ang) * dist, cy + math.sin(ang) * dist, a, b, ang),
                          fill=lerp(base, (255, 248, 240), tint))
        d.ellipse([cx - R * 0.21, cy - R * 0.21, cx + R * 0.21, cy + R * 0.21],
                  fill=lerp(base, (255, 208, 120), 0.72))
        d.ellipse([cx - R * 0.10, cy - R * 0.10, cx + R * 0.10, cy + R * 0.10],
                  fill=lerp(base, (255, 250, 224), 0.55))
    return img


def scene_shapes(w):
    """Bauhaus-style graphic composition."""
    bg = (242, 232, 216)
    img = Image.new("RGB", (w, w), bg)
    teal, coral, navy, mustard, plum = ((38, 112, 120), (232, 108, 88), (32, 44, 78),
                                        (238, 176, 74), (150, 76, 118))

    # background field split
    ImageDraw.Draw(img).rectangle([0, w * 0.62, w, w], fill=(240, 230, 214))
    # large arcs anchored bottom-left
    def arcs(d, size):
        for i, (r, col) in enumerate([(0.62, coral), (0.48, mustard), (0.34, teal), (0.20, navy)]):
            rr = r * w
            d.pieslice([-rr * 0.35, w - rr * 0.75, rr * 0.95, w + rr * 0.55], 180, 360, fill=col)
    overlay(img, arcs, 1.0)
    d = ImageDraw.Draw(img)
    # big circle top right
    d.ellipse([w * 0.58, w * 0.06, w * 1.06, w * 0.54], fill=coral)
    d.ellipse([w * 0.66, w * 0.14, w * 0.90, w * 0.46], fill=(246, 182, 150))
    # overlapping translucent discs
    def discs(dd, size):
        dd.ellipse([w * 0.10, w * 0.10, w * 0.42, w * 0.42], fill=(238, 176, 74, 255))
        dd.ellipse([w * 0.26, w * 0.26, w * 0.58, w * 0.58], fill=(38, 112, 120, 190))
        dd.ellipse([w * 0.18, w * 0.34, w * 0.50, w * 0.66], fill=(150, 76, 118, 150))
    overlay(img, discs, 1.0)
    d = ImageDraw.Draw(img)
    # thin rules + small solid shapes for detail
    for yy in (0.615, 0.638):
        d.line([(w * 0.62, w * yy), (w * 0.88, w * yy)], fill=navy, width=max(2, int(w * 0.0028)))
    d.rectangle([w * 0.70, w * 0.72, w * 0.86, w * 0.88], fill=navy)
    d.ellipse([w * 0.60, w * 0.78, w * 0.68, w * 0.86], fill=mustard)
    d.polygon([(w * 0.44, w * 0.80), (w * 0.52, w * 0.66), (w * 0.60, w * 0.80)], fill=teal)
    for i in range(5):
        cx = w * (0.10 + i * 0.045)
        d.ellipse([cx - w * 0.012, w * 0.925 - w * 0.012, cx + w * 0.012, w * 0.925 + w * 0.012],
                  fill=coral if i % 2 == 0 else navy)
    return img


SCENES = [("dusk", scene_dusk), ("sea", scene_sea), ("night", scene_night),
          ("bloom", scene_bloom), ("shapes", scene_shapes)]

if __name__ == "__main__":
    w = S * K
    for name, fn in SCENES:
        img = fn(w).resize((S, S), Image.LANCZOS)
        img = vignette(grain(img, 4), 0.22)
        img.save(os.path.join(OUT, "art_%s.png" % name))
        img.save(os.path.join(OUT, "art_%s.jpg" % name), quality=92, subsampling=0)
        print("wrote", name)
    cols, rows = 3, 2
    sheet = Image.new("RGB", (S // 2 * cols, S // 2 * rows), (18, 18, 22))
    for i, (name, _) in enumerate(SCENES):
        im = Image.open(os.path.join(OUT, "art_%s.png" % name)).resize((S // 2, S // 2), Image.LANCZOS)
        sheet.paste(im, ((i % cols) * S // 2, (i // cols) * S // 2))
    sheet.save(os.path.join(OUT, "sheet.png"))
