/**
 * Deterministic primitives shared by the landing scene.
 *
 * Seeded so that `prefers-reduced-motion` static frames render an identical
 * composition on every visit (a first-class accessibility deliverable), and so
 * the procedural dune field is stable across reloads.
 */

/** Mulberry32 seeded PRNG -> () => float in [0,1). */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Compact 2D value-noise FBM generator.
 * Used only for procedural dune displacement (license-clean geometry) — the
 * visible particle/fog noise lives in the GLSL shaders.
 */
export function makeValueNoise(rng: () => number) {
  const SIZE = 256;
  const perm = new Uint8Array(SIZE);
  for (let i = 0; i < SIZE; i++) perm[i] = i;
  for (let i = SIZE - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    const tmp = perm[i];
    perm[i] = perm[j];
    perm[j] = tmp;
  }
  const grad = (h: number) => (h & 1 ? 1 : -1);
  const fade = (t: number) => t * t * t * (t * (t * 6 - 15) + 10);
  const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
  const noise2 = (x: number, y: number) => {
    const xi = Math.floor(x) & 255;
    const yi = Math.floor(y) & 255;
    const xf = x - Math.floor(x);
    const yf = y - Math.floor(y);
    const aa = perm[(perm[xi] + yi) & 255] & 1;
    const ba = perm[(perm[xi + 1] + yi) & 255] & 1;
    const ab = perm[(perm[xi] + yi + 1) & 255] & 1;
    const bb = perm[(perm[xi + 1] + yi + 1) & 255] & 1;
    const u = fade(xf);
    const v = fade(yf);
    return lerp(lerp(grad(aa), grad(ba), u), lerp(grad(ab), grad(bb), u), v);
  };
  return (x: number, y: number, octaves = 4) => {
    let amp = 1;
    let freq = 1;
    let sum = 0;
    let norm = 0;
    for (let o = 0; o < octaves; o++) {
      sum += amp * noise2(x * freq, y * freq);
      norm += amp;
      amp *= 0.5;
      freq *= 2;
    }
    return sum / norm;
  };
}
