/**
 * Pure baking helpers for the Luca 3D device.
 *
 * Framework-free math on `Float32Array` — no THREE import, no Vue import. The
 * scene builds its wireframe geometry once at startup, then asks these helpers
 * for baked copies (grid-snapped / broken-offset / left-right halves / particle
 * field) that the per-frame loop blends between. Keeping the baking pure means
 * the choreography is deterministic (seeded RNG) and unit-testable without a
 * WebGL context.
 *
 * Conventions:
 *   - A "position array" is a flat `Float32Array` of xyz triples (`[x0,y0,z0,
 *     x1,y1,z1, ...]`).
 *   - A "wireframe / edge array" groups those triples in pairs of six — every
 *     six floats is one edge (two endpoints). This matches THREE's
 *     `WireframeGeometry` layout, which is what the scene bakes from.
 */

/**
 * Mulberry32 — tiny deterministic seeded PRNG. Returns a function producing
 * floats in [0,1). Chosen so the "broken" vertex offsets and the particle field
 * are identical across reloads (no visual jitter between sessions).
 */
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
 * Round every component of `base` to the nearest multiple of `grid`. Produces
 * the "snapped" target the wireframe eases toward in the snapped (03) beat so
 * every vertex reads as lying on an engineered grid.
 */
export function bakeGridSnap(base: Float32Array, grid = 0.125): Float32Array {
  const out = new Float32Array(base.length);
  const inv = 1 / grid;
  for (let i = 0; i < base.length; i++) {
    out[i] = Math.round(base[i] * inv) / inv;
  }
  return out;
}

/**
 * Bake a sparse offset field: pick `count` unique vertex indices and give each
 * of their xyz components a small random nudge in [-amp, amp]. Every other
 * component stays 0. The scene adds this element-wise to the base positions and
 * blends the result by a `brokenBlend` channel, so the "broken" asymmetry
 * (beat 09) eases in and out rather than snapping.
 *
 * `base` is only read for its length (to size the output and count vertices).
 */
export function bakeBrokenOffsets(
  base: Float32Array,
  seed: number,
  count = 5,
  amp = 0.15,
): Float32Array {
  const out = new Float32Array(base.length);
  const rng = mulberry32(seed);
  const vertexCount = Math.floor(base.length / 3);
  if (vertexCount === 0 || count <= 0) return out;
  const chosen = new Set<number>();
  const limit = Math.min(count, vertexCount);
  let guard = 0;
  while (chosen.size < limit && guard < limit * 16) {
    chosen.add(Math.floor(rng() * vertexCount));
    guard += 1;
  }
  chosen.forEach((vi) => {
    const i = vi * 3;
    out[i] = (rng() * 2 - 1) * amp;
    out[i + 1] = (rng() * 2 - 1) * amp;
    out[i + 2] = (rng() * 2 - 1) * amp;
  });
  return out;
}

export interface Halves {
  /** Edge positions (groups of 6) whose midpoint x <= 0. */
  left: Float32Array;
  /** Edge positions whose midpoint x > 0. */
  right: Float32Array;
}

/**
 * Split a wireframe edge array (groups of six) into left/right halves by the
 * sign of each edge's midpoint x. Edges that straddle x=0 are assigned by their
 * midpoint, which keeps every edge intact on one side — the two resulting
 * LineSegments read as a clean central parting when the scene slides them apart
 * in the opened (05) beat.
 */
export function bakeHalves(edgePositions: Float32Array): Halves {
  const left: number[] = [];
  const right: number[] = [];
  const edgeCount = Math.floor(edgePositions.length / 6);
  for (let e = 0; e < edgeCount; e++) {
    const o = e * 6;
    const midX = (edgePositions[o] + edgePositions[o + 3]) * 0.5;
    const target = midX <= 0 ? left : right;
    for (let k = 0; k < 6; k++) target.push(edgePositions[o + k]);
  }
  return {
    left: Float32Array.from(left),
    right: Float32Array.from(right),
  };
}

/**
 * Distribute `count` points on (and just under) a sphere of `radius`. Uniform
 * on the sphere via the cosine method, then a 0.85–1.0 radial jitter for slight
 * volumetric depth. Used as the rest field for the explode burst: each particle
 * travels from the origin out to its field position as `explodeT` goes 0→1.
 */
export function particleFieldPositions(
  count: number,
  radius = 1.4,
  rng: () => number = Math.random,
): Float32Array {
  const out = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const u = rng() * 2 - 1;
    const phi = rng() * Math.PI * 2;
    const s = Math.sqrt(Math.max(0, 1 - u * u));
    const r = radius * (0.85 + rng() * 0.15);
    out[i * 3] = Math.cos(phi) * s * r;
    out[i * 3 + 1] = Math.sin(phi) * s * r;
    out[i * 3 + 2] = u * r;
  }
  return out;
}
