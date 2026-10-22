"""Prototype of the jigsaw geometry that will be ported to Kotlin.

Pieces are cut from a W x H board with a cols x rows grid. Every internal
boundary is a single polyline shared by the two neighbouring pieces (one walks
it forward, the other reversed), which guarantees a perfect fit.
"""
import math, random

# Half of the tab profile, in units of U (= min(pieceW, pieceH)).
# (x = distance along the edge from the tab centre, y = protrusion out of the edge)
HALF = [
    (0.000, 0.255),
    (0.075, 0.245),
    (0.125, 0.195),
    (0.128, 0.135),
    (0.085, 0.095),
    (0.115, 0.045),
    (0.165, 0.000),
]


def _catmull(pts, per_seg=6):
    """Catmull-Rom through pts (endpoints duplicated)."""
    p = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(len(p) - 3):
        p0, p1, p2, p3 = p[i], p[i + 1], p[i + 2], p[i + 3]
        for s in range(per_seg):
            t = s / per_seg
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t
                       + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t
                       + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    out.append(pts[-1])
    return out


def tab_profile(u):
    """Tab outline in edge-local pixels, from left to right, bump +y."""
    left = [(-x * u, y * u) for (x, y) in reversed(HALF[1:])]
    right = [(x * u, y * u) for (x, y) in HALF[1:]]
    pts = left + [(HALF[0][0] * u, HALF[0][1] * u)] + right
    return _catmull(pts, per_seg=5)


def boundary(ax, ay, bx, by, sign, u):
    """Polyline from A to B with an optional tab. sign 0 = straight edge."""
    if sign == 0:
        return [(ax, ay), (bx, by)]
    dx, dy = bx - ax, by - ay
    L = math.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    px, py = -uy, ux          # perpendicular
    tab = tab_profile(u)
    half = HALF[-1][0] * u          # tab half-width at the edge
    start = L / 2 - half
    end = L / 2 + half
    pts = [(ax + ux * start, ay + uy * start)]
    for (tx, ty) in tab:
        d = start + (tx + half)
        pts.append((ax + ux * d + px * sign * ty,
                    ay + uy * d + py * sign * ty))
    pts.append((ax + ux * end, ay + uy * end))
    pts.append((bx, by))
    return pts


def build(cols, rows, x0, y0, w, h, seed=7):
    rnd = random.Random(seed)
    pw, ph = w / cols, h / rows
    u = min(pw, ph)

    # horizontal boundaries: hb[r][c] between (r-1,c) and (r,c)
    hb = [[0] * cols for _ in range(rows + 1)]
    for r in range(1, rows):
        for c in range(cols):
            hb[r][c] = rnd.choice((-1, 1))
    # vertical boundaries: vb[r][c] between (r,c-1) and (r,c)
    vb = [[0] * (cols + 1) for _ in range(rows)]
    for r in range(rows):
        for c in range(1, cols):
            vb[r][c] = rnd.choice((-1, 1))

    pieces = []
    for r in range(rows):
        for c in range(cols):
            ax, ay = x0 + c * pw, y0 + r * ph
            bx, by = ax + pw, ay + ph
            segs = [
                boundary(ax, ay, bx, ay, hb[r][c] * -1, u),          # top
                boundary(bx, ay, bx, by, vb[r][c + 1] * -1, u),      # right
                boundary(bx, by, ax, by, hb[r + 1][c] * 1, u),       # bottom
                boundary(ax, by, ax, ay, vb[r][c] * 1, u),           # left
            ]
            ring = []
            for seg in segs:
                for pt in seg:
                    if not ring or math.dist(ring[-1], pt) > 0.05:
                        ring.append(pt)
            if math.dist(ring[0], ring[-1]) < 0.05:
                ring.pop()
            pieces.append({"r": r, "c": c, "ring": ring, "u": u})
    return pieces, pw, ph
