/**
 * Luca scene — shared morph-target contract + pure choreography.
 *
 * The per-state morph-target computation (`applyTargets`) was previously kept
 * in `LucaScene.vue` because it needed THREE objects. The function is now
 * fully pure (no THREE imports) and lives here so it can be unit-tested
 * without a WebGL canvas. `LucaScene.vue` imports and calls it as a
 * pure helper — THREE ownership remains in the scene component.
 *
 * This module also re-exports `./geometry` so import paths that route through
 * `luca/polyhedron` stay valid regardless of where a helper is actually used.
 */

import type { LucaState } from '@/composables/landing/useLucaStage';

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

/**
 * Compute the per-state morph TARGETS for the current beat. Every beat overrides
 * a handful of PRISTINE fields; the rest stay neutral.
 *
 * This is the pure 9-state choreography table. It resets `tgtBuf` to PRISTINE
 * on entry, then writes only the state-specific overrides. The damping loop
 * never allocates a fresh MorphTargets object per tick.
 *
 * The `fragment` parameter is accepted but not used — it is reserved for future
 * per-fragment overrides and is a no-op today.
 *
 * @param state     Current LucaState
 * @param p         Beat progress 0-1 (used by 'timed' state for tick-lit fraction)
 * @param fragment  Active fragment id (future use, currently ignored)
 * @param tgtBuf    Pre-reset MorphTargets buffer to fill
 * @param harmonyMode Whether harmony (reverse-ease) mode is active (used by 'broken')
 * @param reverseT  Harmony progress 0-1 (used by 'broken' in harmony mode)
 */
export function applyTargets(
  state: LucaState,
  p: number,
  fragment: string | null,
  tgtBuf: MorphTargets,
  harmonyMode: boolean,
  reverseT: number,
): void {
  // Reset to PRISTINE so only the state-specific overrides need to be written.
  // The original closure did this every frame; the contract is preserved here.
  Object.assign(tgtBuf, PRISTINE)
  switch (state) {
    case 'squashed':
      // Device squash + full-opacity wireframe; jitter channel drives the
      // per-vertex high-frequency micro-jitter.
      tgtBuf.scaleX = 1.15
      tgtBuf.scaleY = 1.15
      tgtBuf.scaleZ = 0.6
      tgtBuf.wireOpacity = 1
      tgtBuf.jitter = 1
      break
    case 'cracked':
      // Outer wireframe fades to grey; the core sphere + halo replace the
      // origin anchor; a slow counter-rotation reads as "frictionless core".
      tgtBuf.wireOpacity = 0.18
      tgtBuf.wireGrey = 1
      tgtBuf.anchorVis = 0
      tgtBuf.coreVis = 1
      tgtBuf.coreLight = 1
      tgtBuf.idleSpin = 0.06
      break
    case 'snapped':
      // Wireframe eases onto a 0.125 grid; a faint background grid plane
      // appears at z=-3. (The 200ms click-pulse fires on enter.)
      tgtBuf.snapBlend = 1
      tgtBuf.bgGrid = 0.5
      tgtBuf.wireOpacity = 0.9
      tgtBuf.idleSpin = 0.25
      break
    case 'axed':
      // A glowing axis line through the origin; the polyhedron orbits it
      // while the camera holds.
      tgtBuf.axisVis = 1
      tgtBuf.wireOpacity = 0.7
      tgtBuf.orbitRate = 0.5
      tgtBuf.idleSpin = 0
      break
    case 'opened':
      // Polyhedron hides; the two baked halves slide apart (∓0.8); a soft
      // portal plane glows behind the gap.
      tgtBuf.openBlend = 1
      tgtBuf.portalVis = 1
      tgtBuf.wireOpacity = 0
      tgtBuf.idleSpin = 0
      break
    case 'quarteted':
      // Main polyhedron hides; four sub-icosahedra fly to the canvas corners.
      // The active pillar returns to center + flares (handled per-frame).
      tgtBuf.quartet = 1
      tgtBuf.wireOpacity = 0
      tgtBuf.idleSpin = 0
      break
    case 'timed':
      // A dial + 12 ticks light up sequentially as the beat's local scrub
      // goes 0→1; a progress arc traces from 2021 to the current tick.
      tgtBuf.tickRing = 1
      tgtBuf.dialOpacity = 0.45
      tgtBuf.tickLit = p
      tgtBuf.wireOpacity = 0.22
      tgtBuf.idleSpin = 0.1
      break
    case 'still':
      // Everything stops and fades; only the origin point remains, breathing.
      tgtBuf.starOpacity = 0
      tgtBuf.idleSpin = 0
      tgtBuf.wireOpacity = 0
      tgtBuf.anchorVis = 1
      tgtBuf.brokenBlend = 0
      break
    case 'broken':
      if (harmonyMode) {
        // Ease from the broken shape back to pristine over the reverse tween.
        const b = reverseT
        tgtBuf.brokenBlend = lerp(1, 0, b)
        tgtBuf.rotX = lerp(0.2, 0, b)
        tgtBuf.rotZ = lerp(-0.15, 0, b)
        tgtBuf.magnetic = lerp(1, 0, b)
        tgtBuf.wireOpacity = 0.85
      } else {
        // Asymmetric lean + seeded vertex offsets + magnetic pointer pull.
        tgtBuf.brokenBlend = 1
        tgtBuf.rotX = 0.2
        tgtBuf.rotZ = -0.15
        tgtBuf.magnetic = 1
        tgtBuf.wireOpacity = 0.85
      }
      break
  }
  void fragment
}
