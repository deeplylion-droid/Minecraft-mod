"""Fart sounds for Chiappe Colossali: a buzzing sphincter (Rosenberg-like flow pulses with jitter and
shimmer), moving cheek resonances (STFT filter), wet crackles, air noise, sputtering ends, and for the
atomic one a blast and a long rumble with a room. Mono OGG Vorbis via ffmpeg.

  python3 synth_scoregge.py <output folder>      (writes fart_small1.ogg ... fart_atomic2.ogg)

An example of the method for any synthesized sound: build the source (pulses, oscillators, noise),
shape it with time-varying filters (stft_filter), add details (crackles), a small room (room), then
finish() (normalize, soft clip, trim the tail, fade) and write() through ffmpeg. Check the result with
spectro.py: in the sandbox there is no audio."""
import numpy as np, subprocess, sys, os

SR = 44100
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)


def stft_filter(x, hfunc, N=2048, H=256):
    win = np.hanning(N)
    pad = np.concatenate([np.zeros(N), x, np.zeros(N)])
    out = np.zeros(len(pad))
    norm = np.zeros(len(pad))
    freqs = np.fft.rfftfreq(N, 1 / SR)
    for start in range(0, len(pad) - N, H):
        seg = pad[start:start + N] * win
        S = np.fft.rfft(seg)
        tc = (start + N / 2 - N) / SR
        S *= hfunc(freqs, tc)
        y = np.fft.irfft(S, N) * win
        out[start:start + N] += y
        norm[start:start + N] += win ** 2
    out = out / np.maximum(norm, 1e-3)
    return out[N:N + len(x)]


def resonances(freqs, peaks, lp, hp=35.0):
    h = np.zeros_like(freqs) + 0.15
    for f, bw, g in peaks:
        h += g / (1.0 + ((freqs - f) / bw) ** 2)
    h *= 1.0 / (1.0 + (freqs / lp) ** 4)
    h *= (freqs / hp) ** 2 / (1.0 + (freqs / hp) ** 2)
    return h


def flow_pulses(dur, f0, rng, jitter, shimmer, env, sputter_from, oq=(0.55, 0.75), gap_prob=0.55, extra_tail=0.4):
    """Glottal-like flow: one pulse per period, period from f0(x), amplitude from env(x)."""
    n = int((dur + extra_tail) * SR)
    flow = np.zeros(n)
    t = 0.0
    ph1, ph2 = rng.uniform(0, 6.28, 2)
    while t < dur:
        x = t / dur
        f = f0(x) * (1 + 0.10 * np.sin(2 * np.pi * 3.3 * t + ph1) + 0.05 * np.sin(2 * np.pi * 8.1 * t + ph2))
        T = max(1.0 / f * (1 + jitter * rng.standard_normal()), 0.002)
        if x > sputter_from and rng.random() < gap_prob * (x - sputter_from) / (1 - sputter_from):
            t += T * rng.uniform(1.5, 5.0)
            continue
        a = env(x) * max(0.15, 1 + shimmer * rng.standard_normal())
        if x > sputter_from and rng.random() < 0.25:
            a *= rng.uniform(1.2, 1.8)   # a louder blat now and then
        To = T * rng.uniform(*oq)
        Tp, Tn = 0.62 * To, 0.38 * To
        i0 = int(t * SR)
        m = int(To * SR) + 1
        tt = np.arange(m) / SR
        g = np.where(tt < Tp, 0.5 * (1 - np.cos(np.pi * tt / Tp)), np.cos(np.pi * (tt - Tp) / (2 * Tn)))
        g = np.clip(g, 0, 1)
        end = min(n, i0 + m)
        flow[i0:end] += a * g[:end - i0]
        t += T
    return flow


def crackles(n, rng, rate, dur, env, band=(1200, 4500)):
    out = np.zeros(n)
    count = int(rate * dur)
    for _ in range(count):
        x = rng.random() ** 0.7
        i = int(x * dur * SR)
        m = int(rng.uniform(0.002, 0.008) * SR)
        burst = rng.standard_normal(m) * np.exp(-np.arange(m) / (m / 3)) * env(x) * rng.uniform(0.3, 1.0)
        end = min(n, i + m)
        out[i:end] += burst[:end - i]
    f = np.fft.rfftfreq(n, 1 / SR)
    S = np.fft.rfft(out)
    S *= np.exp(-((np.log(f + 1) - np.log(np.sqrt(band[0] * band[1]))) ** 2) / (2 * 0.6 ** 2))
    return np.fft.irfft(S, n)


def room(x, rt, mix, rng, predelay=0.012):
    n = int(rt * 1.5 * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-6.9 * t / rt)
    f = np.fft.rfftfreq(n, 1 / SR)
    S = np.fft.rfft(ir) / (1 + (f / 3500) ** 2)
    ir = np.fft.irfft(S, n)
    ir /= np.sqrt(np.sum(ir ** 2))
    d = int(predelay * SR)
    wet = np.convolve(x, ir)[:len(x) + n]
    out = np.concatenate([x, np.zeros(n)])
    out[d:] += mix * wet[:len(out) - d] * 0.6
    return out


def finish(x, drive, fade=0.06, longest=None):
    if longest:
        x = x[:int(longest * SR)]
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9)
    x = np.tanh(drive * x) / np.tanh(drive)
    # trim the tail where the sound has died away (10 ms RMS under -46 dB)
    h = int(0.01 * SR)
    rms = np.sqrt(np.convolve(x ** 2, np.ones(h) / h, mode='same'))
    idx = np.where(rms > 0.005 * np.max(rms))[0]
    if len(idx):
        x = x[:min(len(x), idx[-1] + int(0.03 * SR))]
    m = min(len(x) // 4, int(fade * SR))
    x[-m:] *= np.linspace(1, 0, m) ** 2
    x[:64] *= np.linspace(0, 1, 64)
    return 0.92 * x / np.max(np.abs(x))


def env_shape(attack, swell, release_from, rng):
    ph = rng.uniform(0, 6.28)

    def env(x):
        a = min(1.0, x / attack) if attack > 0 else 1.0
        s = 1 + swell * np.sin(2 * np.pi * 1.7 * x + ph)
        r = 1.0 if x < release_from else max(0.0, 1 - (x - release_from) / (1 - release_from)) ** 0.7
        return a * s * r
    return env


def fart(name, dur, f_start, f_end, seed, jitter=0.07, shimmer=0.22, sputter_from=0.7, wet=30, peaks=None,
         lp=2600, drive=2.0, air=0.05, squeak_end=False, rt=0.35, mix=0.12, attack=0.06):
    rng = np.random.default_rng(seed)
    env = env_shape(attack, 0.25, 0.8, rng)

    def f0(x):
        f = f_start * (f_end / f_start) ** x
        if squeak_end and x > 0.82:
            f *= 1 + 2.2 * ((x - 0.82) / 0.18) ** 2
        return f
    flow = flow_pulses(dur, f0, rng, jitter, shimmer, env, sputter_from)
    sig = np.diff(flow, prepend=0.0)              # radiation: the closing of each pulse buzzes
    sig = 0.7 * sig / (np.max(np.abs(sig)) + 1e-9) + 0.3 * flow / (np.max(np.abs(flow)) + 1e-9) * 0.4
    n = len(sig)
    peaks = peaks or [(180, 60, 2.5), (520, 120, 1.3), (1250, 300, 0.5)]
    wob = rng.uniform(0, 6.28)

    def H(freqs, t):
        k = 1 + 0.18 * np.sin(2 * np.pi * 2.3 * t + wob)    # the cheeks flap: the resonances move
        return resonances(freqs, [(f * k, bw, g) for f, bw, g in peaks], lp)
    sig = stft_filter(sig, H)
    sig = sig / (np.max(np.abs(sig)) + 1e-9)
    noise = rng.standard_normal(n)
    noise = stft_filter(noise, lambda f, t: resonances(f, [(900, 500, 1.0)], 3000, hp=300))
    envs = np.array([env(min(1.0, i / SR / dur)) for i in range(0, n, 64)])
    envs = np.repeat(envs, 64)[:n]
    sig += air * noise / (np.max(np.abs(noise)) + 1e-9) * envs
    sig += 0.35 * crackles(n, rng, wet, dur, env) / 1.0
    sig = room(sig, rt, mix, rng)
    out = finish(sig, drive)
    write(name, out)


def atomic(name, seed):
    rng = np.random.default_rng(seed)
    dur = 3.6
    n = int((dur + 1.0) * SR)
    t = np.arange(n) / SR
    # the blast: a sinking boom and a roar of low noise
    boom = np.sin(2 * np.pi * np.cumsum(30 + 70 * np.exp(-t / 0.12)) / SR) * np.exp(-t / 0.55)
    roar = rng.standard_normal(n)
    f = np.fft.rfftfreq(n, 1 / SR)
    S = np.fft.rfft(roar) / (1 + (f / 260) ** 3) * ((f / 25) ** 2 / (1 + (f / 25) ** 2))
    roar = np.fft.irfft(S, n)
    roar = roar / np.max(np.abs(roar)) * np.exp(-t / 0.9) * np.minimum(1, t / 0.01)
    blast = 1.0 * boom + 0.8 * roar
    # the fart itself: very low, enormous, then it rises into a squeak
    env = env_shape(0.04, 0.3, 0.86, rng)

    def f0(x):
        f = 27 * (42 / 27) ** x
        if x > 0.78:
            f *= 1 + 5.0 * ((x - 0.78) / 0.22) ** 2.2
        return f
    flow = flow_pulses(dur, f0, rng, 0.09, 0.28, env, 0.72, gap_prob=0.35, extra_tail=1.0)
    sig = np.diff(flow, prepend=0.0)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) + 0.25 * flow / (np.max(np.abs(flow)) + 1e-9)
    sig = sig[:n] if len(sig) >= n else np.concatenate([sig, np.zeros(n - len(sig))])
    wob = rng.uniform(0, 6.28)

    def H(freqs, tt):
        k = 1 + 0.22 * np.sin(2 * np.pi * 1.4 * tt + wob)
        return resonances(freqs, [(110 * k, 45, 3.0), (330 * k, 90, 1.6), (900 * k, 250, 0.6)], 1800, hp=22)
    sig = stft_filter(sig, H)
    sig = sig / np.max(np.abs(sig))
    sig += 0.4 * crackles(n, rng, 80, dur, env, band=(800, 3500))
    mix = 0.9 * sig + 0.85 * blast
    mix = room(mix, 1.6, 0.35, rng, predelay=0.03)
    out = finish(mix, 3.0, fade=0.6, longest=5.6)
    write(name, out)


def write(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2').tobytes()
    path = os.path.join(OUT, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '5', path], input=pcm, check=True)
    print(name, round(len(x) / SR, 2), 's')


# small: short and high, a toot or a quick flutter
fart('fart_small1', 0.32, 150, 175, 11, sputter_from=0.8, wet=8, peaks=[(240, 70, 2.2), (700, 160, 1.2), (1600, 400, 0.4)], lp=3200, drive=1.8)
fart('fart_small2', 0.48, 175, 120, 12, sputter_from=0.65, wet=14, peaks=[(220, 70, 2.2), (640, 150, 1.2), (1500, 400, 0.4)], lp=3000, drive=2.0)
fart('fart_small3', 0.40, 130, 190, 13, sputter_from=0.75, wet=10, peaks=[(260, 80, 2.0), (760, 170, 1.1), (1700, 420, 0.4)], lp=3400, drive=1.8, squeak_end=True)
# medium: a proper one, lower and longer, sputtering at the end
fart('fart_medium1', 0.85, 95, 70, 21, sputter_from=0.62, wet=30, lp=2600, drive=2.2)
fart('fart_medium2', 1.05, 80, 105, 22, sputter_from=0.7, wet=34, lp=2500, drive=2.2, squeak_end=True)
fart('fart_medium3', 0.75, 110, 75, 23, sputter_from=0.55, wet=40, lp=2700, drive=2.4)
# big: deep, long and wet, with a room
fart('fart_big1', 1.7, 62, 48, 31, sputter_from=0.6, wet=70, peaks=[(140, 50, 2.8), (420, 110, 1.4), (1000, 260, 0.5)], lp=2200, drive=2.6, rt=0.7, mix=0.2, air=0.08)
fart('fart_big2', 2.0, 55, 70, 32, sputter_from=0.65, wet=80, peaks=[(130, 50, 2.8), (400, 100, 1.4), (950, 250, 0.5)], lp=2100, drive=2.8, rt=0.8, mix=0.22, air=0.08, squeak_end=True)
fart('fart_big3', 1.5, 70, 45, 33, sputter_from=0.5, wet=90, peaks=[(150, 55, 2.6), (450, 120, 1.3), (1050, 280, 0.5)], lp=2300, drive=2.6, rt=0.7, mix=0.2, air=0.1)
# atomic: a blast, then the end of the world
atomic('fart_atomic1', 41)
atomic('fart_atomic2', 42)
