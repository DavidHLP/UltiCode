/**
 * Morph targets — the particle state machine's data layer. Pure TypeScript:
 * given a particle count, build six position buffers (one per morph state)
 * plus per-particle randomness. The renderer uploads them as attributes and
 * blends between states in the vertex shader; nothing here runs per frame.
 *
 * Every generator assigns every particle a slot, so transitions are always
 * continuous interpolations — no scene is ever hidden and replaced.
 */

import { createNoise2D, fbm } from "./noise";
import {
  COLLAPSE_POINT,
  MONOLITHS,
  SKY,
  SLABS,
  TERRAIN,
} from "./layout";

export interface MorphTargets {
  /** Six buffers of `count * 3`, in MORPH_STATES order. */
  states: Float32Array[];
  /** Per-particle randomness: [brightness, phase, size]. */
  random: Float32Array;
  count: number;
}

/** Deterministic PRNG (LCG). */
function createRng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
}

/**
 * State 0 — terrain. A jittered grid lifted by fBm noise into ridges,
 * dunes, and gullies. Height also feeds per-particle brightness so gullies
 * read darker than ridges.
 */
function buildTerrain(count: number, rng: () => number, out: Float32Array): void {
  const noise = createNoise2D(1337);
  const cols = Math.ceil(Math.sqrt(count * (TERRAIN.width / TERRAIN.depth)));
  const rows = Math.ceil(count / cols);
  for (let i = 0; i < count; i++) {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const gx = (col / Math.max(1, cols - 1) - 0.5) * TERRAIN.width;
    const gz = 30 - (row / Math.max(1, rows - 1)) * TERRAIN.depth;
    const x = gx + (rng() - 0.5) * 1.6;
    const z = gz + (rng() - 0.5) * 1.6;
    const ridge = fbm(noise, x * 0.016, z * 0.016, 4);
    const detail = fbm(noise, x * 0.06 + 40, z * 0.06, 2) * 0.35;
    const height = (ridge * 0.5 + 0.5) * TERRAIN.maxHeight + detail;
    out[i * 3] = x;
    out[i * 3 + 1] = height - 1.2 + (rng() - 0.5) * 0.3;
    out[i * 3 + 2] = z;
  }
}

/**
 * State 1 — tunnel. Particles lift into a series of structural arches the
 * camera flies through; the rest settle into a low mist floor.
 */
function buildTunnel(count: number, rng: () => number, out: Float32Array): void {
  const arches = 7;
  const archCount = Math.floor(count * 0.62);
  for (let i = 0; i < count; i++) {
    if (i < archCount) {
      const arch = i % arches;
      const t = (i / archCount) * arches - arch;
      const angle = t * Math.PI + rng() * 0.04;
      const radius = 7.5 + (rng() - 0.5) * 0.8;
      out[i * 3] = Math.cos(angle) * radius;
      out[i * 3 + 1] = Math.sin(angle) * radius + 0.4;
      out[i * 3 + 2] = -46 - arch * 9 + (rng() - 0.5) * 1.4;
    } else {
      out[i * 3] = (rng() - 0.5) * TERRAIN.width * 0.7;
      out[i * 3 + 1] = -1 + rng() * 0.8;
      out[i * 3 + 2] = -40 - rng() * 70;
    }
  }
}

/**
 * State 2 — slabs. Eight vertical test steles; particles coat their faces,
 * with a thin residual floor of dust between them.
 */
function buildSlabs(count: number, rng: () => number, out: Float32Array): void {
  const coatCount = Math.floor(count * 0.8);
  for (let i = 0; i < count; i++) {
    if (i < coatCount) {
      const slab = SLABS[i % SLABS.length];
      const face = rng() > 0.5 ? 1 : -1;
      out[i * 3] =
        slab.x + (rng() - 0.5) * slab.width + face * 0.05;
      out[i * 3 + 1] = rng() * slab.height - 1;
      out[i * 3 + 2] = slab.z + face * (0.35 + rng() * 0.15);
    } else {
      out[i * 3] = (rng() - 0.5) * 40;
      out[i * 3 + 1] = -1.1 + rng() * 0.5;
      out[i * 3 + 2] = -100 - rng() * 50;
    }
  }
}

/**
 * State 3 — starfield. The field dissolves into a wide drifting sky with a
 * slow spiral of recorded submissions around the centre.
 */
function buildStarfield(count: number, rng: () => number, out: Float32Array): void {
  const spiralCount = Math.floor(count * 0.18);
  for (let i = 0; i < count; i++) {
    if (i < spiralCount) {
      const t = i / spiralCount;
      const angle = t * Math.PI * 10;
      const radius = 4 + t * 26;
      out[i * 3] = SKY.x + Math.cos(angle) * radius;
      out[i * 3 + 1] = SKY.y - 14 + t * 22 + (rng() - 0.5) * 0.8;
      out[i * 3 + 2] = SKY.z + Math.sin(angle) * radius * 0.5;
    } else {
      // Uniform-ish shell around the sky centre.
      const theta = rng() * Math.PI * 2;
      const phi = Math.acos(2 * rng() - 1);
      const radius = SKY.radius * (0.35 + rng() * 0.65);
      out[i * 3] = SKY.x + Math.sin(phi) * Math.cos(theta) * radius * 1.4;
      out[i * 3 + 1] = SKY.y + Math.cos(phi) * radius * 0.5;
      out[i * 3 + 2] = SKY.z + Math.sin(phi) * Math.sin(theta) * radius * 0.9;
    }
  }
}

/**
 * State 4 — monoliths. Three entry steles (problems / contests / community)
 * with a low ground mist; the gathering sky falls back into structure.
 */
function buildMonoliths(count: number, rng: () => number, out: Float32Array): void {
  const coatCount = Math.floor(count * 0.72);
  for (let i = 0; i < count; i++) {
    if (i < coatCount) {
      const mono = MONOLITHS[i % MONOLITHS.length];
      const face = rng() > 0.5 ? 1 : -1;
      out[i * 3] = mono.x + (rng() - 0.5) * mono.width + face * 0.06;
      out[i * 3 + 1] = rng() * mono.height - 1;
      out[i * 3 + 2] = mono.z + face * (0.4 + rng() * 0.2);
    } else {
      out[i * 3] = (rng() - 0.5) * 60;
      out[i * 3 + 1] = -1 + rng() * 0.7;
      out[i * 3 + 2] = -225 - rng() * 45;
    }
  }
}

/**
 * State 5 — collapse. Every particle contracts toward a single point of
 * light; a whisper-thin shell keeps the implosion readable mid-transition.
 */
function buildCollapse(count: number, rng: () => number, out: Float32Array): void {
  for (let i = 0; i < count; i++) {
    const theta = rng() * Math.PI * 2;
    const phi = Math.acos(2 * rng() - 1);
    const radius = Math.pow(rng(), 2.2) * 1.6;
    out[i * 3] =
      COLLAPSE_POINT.x + Math.sin(phi) * Math.cos(theta) * radius;
    out[i * 3 + 1] = COLLAPSE_POINT.y + Math.cos(phi) * radius;
    out[i * 3 + 2] =
      COLLAPSE_POINT.z + Math.sin(phi) * Math.sin(theta) * radius;
  }
}

/** Build all six morph-state buffers plus per-particle randomness. */
export function buildMorphTargets(count: number, seed = 20260720): MorphTargets {
  if (!Number.isInteger(count) || count <= 0) {
    throw new RangeError(
      `buildMorphTargets: count must be a positive integer, got ${count}`,
    );
  }
  const rng = createRng(seed);
  const states = [
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
  ];
  buildTerrain(count, rng, states[0]);
  buildTunnel(count, rng, states[1]);
  buildSlabs(count, rng, states[2]);
  buildStarfield(count, rng, states[3]);
  buildMonoliths(count, rng, states[4]);
  buildCollapse(count, rng, states[5]);

  const random = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    random[i * 3] = 0.25 + rng() * 0.65; // brightness
    random[i * 3 + 1] = rng() * Math.PI * 2; // breathing phase
    random[i * 3 + 2] = 0.55 + rng() * 1.1; // size factor
  }
  return { states, random, count };
}
