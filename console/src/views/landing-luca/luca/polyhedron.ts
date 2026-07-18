/**
 * Luca scene — shared morph-target contract + re-exports of the pure baking
 * helpers.
 *
 * The scene keeps the full per-state choreography in `LucaScene.vue` (it needs
 * THREE objects, which the pure helpers deliberately avoid). This module is the
 * single source of truth for the *shape* of a morph-target packet and for the
 * tiny pure utilities (lerp, a pristine baseline) the choreography and the
 * per-frame ease both reference, so both sides stay in sync without re-deriving
 * the channel list.
 *
 * It also re-exports `./geometry` so import paths that route through
 * `luca/polyhedron` stay valid regardless of where a helper is actually used.
 */

// Re-export the pure baking helpers (values + the Halves interface) so callers
// can route every import through this barrel. A single `export *` keeps the
// value/type split correct under `verbatimModuleSyntax`.
export * from "./geometry";

/**
 * The full per-frame morph channel set. `targets()` in the scene returns one of
 * these per beat; the tick loop eases every live `morph` field toward it, then
 * applies `morph` to the THREE objects. Adding a channel = adding a field here,
 * to `PRISTINE`, to the `Morph` live object, and to the apply step — that
 * single source of truth is why the list lives in this pure module.
 */
export interface MorphTargets {
  /** Device group scale (squash in beat 01). */
  scaleX: number;
  scaleY: number;
  scaleZ: number;
  /** Device group rotation (broken lean in beat 09). */
  rotX: number;
  rotY: number;
  rotZ: number;
  /** Wireframe opacity + grey-out (cracked 02 fades to #888). */
  wireOpacity: number;
  wireGrey: number;
  /** Constant origin anchor (breathes in still 08; hidden in cracked 02). */
  anchorScale: number;
  anchorVis: number;
  /** Inner solid core + halo light (cracked 02). */
  coreVis: number;
  coreLight: number;
  /** Per-vertex snap blend + background grid plane (snapped 03). */
  snapBlend: number;
  bgGrid: number;
  /** Glowing axis line (axed 04). */
  axisVis: number;
  orbitRate: number;
  /** Door split + portal plane (opened 05). */
  openBlend: number;
  portalVis: number;
  /** Four corner sub-icosahedra (quarteted 06). */
  quartet: number;
  /** Tick ring + dial + lit fraction (timed 07). */
  tickRing: number;
  dialOpacity: number;
  tickLit: number;
  /** Starfield opacity + idle spin multiplier (both zeroed in still 08). */
  starOpacity: number;
  idleSpin: number;
  /** Per-vertex seeded offsets + magnetic pull (broken 09). */
  brokenBlend: number;
  magnetic: number;
  /** Squash micro-jitter amplitude (squashed 01). */
  jitter: number;
}

/** Linear interpolation; the per-frame ease is `a + (b - a) * t`. */
export const lerp = (a: number, b: number, t: number): number => a + (b - a) * t;

/**
 * The neutral baseline every beat starts from before its switch case overrides
 * a handful of fields. Represents the device at rest on a black field: white
 * wireframe at full opacity, origin anchor visible, every auxiliary object off.
 */
export const PRISTINE: MorphTargets = {
  scaleX: 1,
  scaleY: 1,
  scaleZ: 1,
  rotX: 0,
  rotY: 0,
  rotZ: 0,
  wireOpacity: 0.85,
  wireGrey: 0,
  anchorScale: 1,
  anchorVis: 1,
  coreVis: 0,
  coreLight: 0,
  snapBlend: 0,
  bgGrid: 0,
  axisVis: 0,
  orbitRate: 0,
  openBlend: 0,
  portalVis: 0,
  quartet: 0,
  tickRing: 0,
  dialOpacity: 0,
  tickLit: 0,
  starOpacity: 0.45,
  idleSpin: 1,
  brokenBlend: 0,
  magnetic: 0,
  jitter: 0,
};
