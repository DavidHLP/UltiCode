/**
 * Landing "code core" chapter poses — pure math, no three.js imports so the
 * morph model stays unit-testable in Node and the renderer stays a thin
 * adapter. Scene units; the renderer owns camera/framing.
 *
 * One particle lattice travels through six poses, mirroring the page
 * narrative: hero core → parse lattice → judge matrix → growth helix →
 * network constellation → finale cursor core.
 */

export const CHAPTER_COUNT = 6;

/**
 * Deterministic PRNG (LCG) so poses are stable across reloads, refreshes to
 * mid-page, and test runs.
 */
function createRng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
}

/** Chapter 0 — Hero: a stable core. Fibonacci sphere plus a denser inner shell. */
function spherePose(count: number, rng: () => number, out: Float32Array): void {
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    const inner = i % 4 === 0;
    const radius = inner ? 1.15 : 2.3;
    const y = 1 - (i / Math.max(1, count - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * i + rng() * 0.05;
    out[i * 3] = Math.cos(theta) * r * radius;
    out[i * 3 + 1] = y * radius;
    out[i * 3 + 2] = Math.sin(theta) * r * radius;
  }
}

/** Chapter 1 — Parse: text unfolds into an executable lattice. */
function latticePose(count: number, rng: () => number, out: Float32Array): void {
  const grid = Math.ceil(Math.cbrt(count));
  const spacing = 0.72;
  const half = ((grid - 1) * spacing) / 2;
  for (let i = 0; i < count; i++) {
    const x = i % grid;
    const y = Math.floor(i / grid) % grid;
    const z = Math.floor(i / (grid * grid));
    out[i * 3] = x * spacing - half + (rng() - 0.5) * 0.06;
    out[i * 3 + 1] = y * spacing - half + (rng() - 0.5) * 0.06;
    out[i * 3 + 2] = z * spacing - half + (rng() - 0.5) * 0.06;
  }
}

/** Chapter 2 — Judge: a wall of test chambers the core passes through. */
function matrixPose(count: number, rng: () => number, out: Float32Array): void {
  const cols = 20;
  const rows = Math.ceil(count / cols);
  for (let i = 0; i < count; i++) {
    const c = i % cols;
    const r = Math.floor(i / cols);
    out[i * 3] = (c - (cols - 1) / 2) * 0.42;
    out[i * 3 + 1] = (r - (rows - 1) / 2) * 0.2;
    out[i * 3 + 2] = (rng() - 0.5) * 0.3;
  }
}

/** Chapter 3 — Growth: submissions stretch into an extending helix track. */
function helixPose(count: number, rng: () => number, out: Float32Array): void {
  const turns = 3.5;
  for (let i = 0; i < count; i++) {
    const t = i / Math.max(1, count - 1);
    const angle = t * Math.PI * 2 * turns + rng() * 0.08;
    const radius = 1.25 + (rng() - 0.5) * 0.15;
    out[i * 3] = Math.cos(angle) * radius;
    out[i * 3 + 1] = (t - 0.5) * 6.4;
    out[i * 3 + 2] = Math.sin(angle) * radius;
  }
}

/** Chapter 4 — Network: personal tracks join a wider constellation of clusters. */
function constellationPose(count: number, rng: () => number, out: Float32Array): void {
  const clusters = 9;
  const golden = Math.PI * (3 - Math.sqrt(5));
  const centers: number[] = [];
  for (let c = 0; c < clusters; c++) {
    const y = 1 - (c / (clusters - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * c;
    centers.push(Math.cos(theta) * r * 2.5, y * 2.5, Math.sin(theta) * r * 2.5);
  }
  for (let i = 0; i < count; i++) {
    const c = (i % clusters) * 3;
    out[i * 3] = centers[c] + (rng() - 0.5) * 0.8;
    out[i * 3 + 1] = centers[c + 1] + (rng() - 0.5) * 0.8;
    out[i * 3 + 2] = centers[c + 2] + (rng() - 0.5) * 0.8;
  }
}

/** Chapter 5 — Finale: the network collapses back to a clean core plus a cursor. */
function corePose(count: number, rng: () => number, out: Float32Array): void {
  const cursorCount = Math.max(8, Math.floor(count * 0.08));
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    if (i >= count - cursorCount) {
      // The "cursor": a thin vertical line beside the core.
      const k = (i - (count - cursorCount)) / Math.max(1, cursorCount - 1);
      out[i * 3] = 0.95;
      out[i * 3 + 1] = (k - 0.5) * 1.8;
      out[i * 3 + 2] = 0;
      continue;
    }
    const y = 1 - (i / Math.max(1, count - cursorCount - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * i + rng() * 0.05;
    out[i * 3] = Math.cos(theta) * r * 0.85;
    out[i * 3 + 1] = y * 0.85;
    out[i * 3 + 2] = Math.sin(theta) * r * 0.85;
  }
}

/**
 * Build all chapter poses for `count` nodes. Each pose is a flat
 * `[x, y, z, ...]` buffer of length `count * 3`.
 */
export function buildPoses(count: number, seed = 20260720): Float32Array[] {
  if (!Number.isInteger(count) || count <= 0) {
    throw new RangeError(`buildPoses: count must be a positive integer, got ${count}`);
  }
  const rng = createRng(seed);
  const poses = [
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
    new Float32Array(count * 3),
  ];
  spherePose(count, rng, poses[0]);
  latticePose(count, rng, poses[1]);
  matrixPose(count, rng, poses[2]);
  helixPose(count, rng, poses[3]);
  constellationPose(count, rng, poses[4]);
  corePose(count, rng, poses[5]);
  return poses;
}
