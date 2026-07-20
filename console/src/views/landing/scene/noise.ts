/**
 * Seeded Simplex noise (2D) + fBm — pure TypeScript, no dependencies, fully
 * deterministic so terrain and morph targets are identical across reloads,
 * devices, and test runs. Based on Stefan Gustavson's classic algorithm.
 */

const GRADIENTS = [
  [1, 1], [-1, 1], [1, -1], [-1, -1],
  [1, 0], [-1, 0], [0, 1], [0, -1],
] as const;

const F2 = 0.5 * (Math.sqrt(3) - 1);
const G2 = (3 - Math.sqrt(3)) / 6;

export interface Noise2D {
  (x: number, y: number): number;
}

/** Build a deterministic 2D simplex noise sampler from a seed. */
export function createNoise2D(seed: number): Noise2D {
  // Seeded permutation table (Fisher–Yates with an LCG).
  let s = seed >>> 0;
  const rand = () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
  const perm = new Uint8Array(512);
  const base = new Uint8Array(256);
  for (let i = 0; i < 256; i++) base[i] = i;
  for (let i = 255; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    const tmp = base[i];
    base[i] = base[j];
    base[j] = tmp;
  }
  for (let i = 0; i < 512; i++) perm[i] = base[i & 255];

  return (x: number, y: number): number => {
    const skew = (x + y) * F2;
    const i = Math.floor(x + skew);
    const j = Math.floor(y + skew);
    const unskew = (i + j) * G2;
    const x0 = x - (i - unskew);
    const y0 = y - (j - unskew);

    const i1 = x0 > y0 ? 1 : 0;
    const j1 = x0 > y0 ? 0 : 1;

    const x1 = x0 - i1 + G2;
    const y1 = y0 - j1 + G2;
    const x2 = x0 - 1 + 2 * G2;
    const y2 = y0 - 1 + 2 * G2;

    const ii = i & 255;
    const jj = j & 255;

    let n = 0;

    let t = 0.5 - x0 * x0 - y0 * y0;
    if (t > 0) {
      const g = GRADIENTS[perm[ii + perm[jj]] % 8];
      t *= t;
      n += t * t * (g[0] * x0 + g[1] * y0);
    }

    t = 0.5 - x1 * x1 - y1 * y1;
    if (t > 0) {
      const g = GRADIENTS[perm[ii + i1 + perm[jj + j1]] % 8];
      t *= t;
      n += t * t * (g[0] * x1 + g[1] * y1);
    }

    t = 0.5 - x2 * x2 - y2 * y2;
    if (t > 0) {
      const g = GRADIENTS[perm[ii + 1 + perm[jj + 1]] % 8];
      t *= t;
      n += t * t * (g[0] * x2 + g[1] * y2);
    }

    // Canonical simplex output is roughly [-1, 1] after this scaling.
    return 70 * n;
  };
}

/**
 * Fractal Brownian motion over a noise sampler. Output stays within
 * roughly [-1, 1] for the octave counts used here.
 */
export function fbm(
  noise: Noise2D,
  x: number,
  y: number,
  octaves: number,
  lacunarity = 2,
  gain = 0.5,
): number {
  let amplitude = 1;
  let frequency = 1;
  let sum = 0;
  let norm = 0;
  for (let o = 0; o < octaves; o++) {
    sum += amplitude * noise(x * frequency, y * frequency);
    norm += amplitude;
    amplitude *= gain;
    frequency *= lacunarity;
  }
  return norm > 0 ? sum / norm : 0;
}
