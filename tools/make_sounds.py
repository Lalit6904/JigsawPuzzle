"""Synthesises the two short sound cues used by the game."""
import math, random, struct, wave, os

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)


def write(name, samples):
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = int(max(-1.0, min(1.0, s)) * 32767)
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))
    print("wrote", path, len(samples) / SR, "s")


def snap():
    """Short wooden 'tok' for a piece locking into place."""
    n = int(SR * 0.11)
    rnd = random.Random(1)
    out = []
    for i in range(n):
        t = i / SR
        body = (0.55 * math.sin(2 * math.pi * 1180 * t) +
                0.22 * math.sin(2 * math.pi * 1720 * t)) * math.exp(-t * 75)
        thump = 0.40 * math.sin(2 * math.pi * 178 * t) * math.exp(-t * 42)
        click = 0.30 * (rnd.random() * 2 - 1) * math.exp(-t * 800)
        out.append((body + thump + click) * 0.6)
    # normalise
    peak = max(abs(s) for s in out) or 1.0
    return [s / peak * 0.75 for s in out]


def complete():
    """Rising four-note chime with a soft tail."""
    notes = [(523.25, 0.00), (659.25, 0.11), (783.99, 0.22), (1046.50, 0.33)]
    dur = 1.7
    n = int(SR * dur)
    out = [0.0] * n
    for f, start in notes:
        s0 = int(start * SR)
        for i in range(s0, n):
            t = (i - s0) / SR
            env = min(1.0, t / 0.012) * math.exp(-t * 2.1)
            v = (math.sin(2 * math.pi * f * t) +
                 0.30 * math.sin(4 * math.pi * f * t) +
                 0.10 * math.sin(6 * math.pi * f * t))
            out[i] += v * env * 0.32
    # gentle fade out at the tail
    fade = int(SR * 0.35)
    for i in range(fade):
        out[n - fade + i] *= 1 - i / fade
    peak = max(abs(s) for s in out) or 1.0
    return [s / peak * 0.8 for s in out]


write("snap.wav", snap())
write("complete.wav", complete())
