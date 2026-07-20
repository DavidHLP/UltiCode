/**
 * Scroll-progress → scene-state mapping for the landing code core. Pure
 * functions so the narrative timing is unit-testable and frame-rate
 * independent: everything derives from a single `progress ∈ [0, 1]`.
 */

import { CHAPTER_COUNT } from "./poses";

/** Map page progress to a continuous chapter coordinate (0 … chapters-1). */
export function chapterFloat(
  progress: number,
  chapters: number = CHAPTER_COUNT,
): number {
  if (chapters < 2) return 0;
  const clamped = Math.min(1, Math.max(0, progress));
  return clamped * (chapters - 1);
}

/** Classic smoothstep — eased enter/exit per chapter transition. */
export function easeInOut(t: number): number {
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

/**
 * Write the morphed node positions for `progress` into `out`.
 * `out` must have the same length as each pose buffer (`count * 3`).
 */
export function samplePose(
  poses: readonly Float32Array[],
  progress: number,
  out: Float32Array,
): Float32Array {
  if (poses.length === 0) return out;
  if (poses.length === 1) {
    out.set(poses[0]);
    return out;
  }
  const f = chapterFloat(progress, poses.length);
  const i = Math.min(poses.length - 2, Math.floor(f));
  const t = easeInOut(f - i);
  const a = poses[i];
  const b = poses[i + 1];
  for (let k = 0; k < out.length; k++) {
    out[k] = a[k] + (b[k] - a[k]) * t;
  }
  return out;
}

/**
 * The verdict pulse: a single restrained green release exactly at the
 * judge → growth boundary (chapter coordinate 2.5 of 5 → progress 0.5).
 * Gaussian falloff; ~0 outside ±0.15 progress.
 */
export function verdictPulse(progress: number): number {
  const d = (progress - 0.5) / 0.06;
  return Math.exp(-d * d);
}

/**
 * Residual green tint that stays after the verdict — the core keeps a
 * measured trace of "Accepted" through the growth and network chapters.
 */
export function growthTint(progress: number): number {
  const t = Math.min(1, Math.max(0, (progress - 0.52) / 0.1));
  return easeInOut(t) * 0.18;
}

/**
 * Green channel mix for the node material: pulse dominates at the verdict
 * moment, then settles into the residual growth tint.
 */
export function nodeColorMix(progress: number): number {
  return Math.min(1, verdictPulse(progress) + growthTint(progress));
}

/** Slow scroll-driven rotation; no autonomous spin (restraint rule). */
export function groupRotation(progress: number, interactive: boolean): number {
  return interactive ? progress * Math.PI * 0.6 : 0;
}
