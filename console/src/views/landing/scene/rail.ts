/**
 * Camera rail — scroll progress → camera frame over the wasteland, as pure
 * data and pure math (unit-testable in Node, no three.js). Same spline
 * model as before: Catmull-Rom through pinned keyframes for position and
 * look target, smoothstep-lerped FOV, pure function of progress so reverse
 * scrolling replays exactly.
 *
 * Movement vocabulary: slow high-altitude opening, a heavy descent after
 * the first scroll, then low, inertial travel down the -Z corridor with
 * small lateral drift — no sudden cuts, no fast rotations.
 */

import { CHAPTERS, MORPH_COUNT } from "./layout";

export type Vec3 = readonly [number, number, number];

export interface CameraKeyframe {
  t: number;
  pos: Vec3;
  look: Vec3;
  fov: number;
}

export interface CameraFrame {
  pos: [number, number, number];
  look: [number, number, number];
  fov: number;
}

export type RailVariant = "desktop" | "mobile";

const DESKTOP_KEYFRAMES: readonly CameraKeyframe[] = [
  // hero — high above the plain, then the first-scroll descent
  { t: 0.0, pos: [0, 26, 34], look: [0, 0, -30], fov: 55 },
  { t: 0.08, pos: [0, 12, 8], look: [0, 2, -40], fov: 52 },
  { t: 0.16, pos: [0, 6, -16], look: [0, 2, -60], fov: 50 },
  // parse — low glide into the arches
  { t: 0.25, pos: [-3.5, 4.5, -50], look: [2, 2, -90], fov: 50 },
  { t: 0.34, pos: [2, 4, -84], look: [-2, 3, -118], fov: 51 },
  // matrix — weave between the steles, slight rise to read the field
  { t: 0.43, pos: [-2.5, 5, -112], look: [7, 5, -126], fov: 52 },
  { t: 0.52, pos: [0, 9, -140], look: [0, 3, -172], fov: 54 },
  // growth — drift outward and upward into the sky
  { t: 0.61, pos: [-7, 12, -168], look: [0, 16, -196], fov: 54 },
  { t: 0.7, pos: [0, 16, -196], look: [0, 12, -228], fov: 55 },
  // network — descend toward the monoliths, pass between them
  { t: 0.79, pos: [4, 8, -222], look: [0, 8, -244], fov: 52 },
  { t: 0.88, pos: [0, 5, -254], look: [0, 3, -290], fov: 49 },
  // finale — slow push into the point of light
  { t: 0.95, pos: [0, 3, -276], look: [0, 1.2, -305], fov: 45 },
  { t: 1.0, pos: [0, 2.2, -291], look: [0, 1.2, -305], fov: 42 },
];

/** Mobile rail — shorter, steadier: less lateral drift, clamped FOV. */
function toMobileKeyframe(key: CameraKeyframe): CameraKeyframe {
  return {
    t: key.t,
    pos: [key.pos[0] * 0.3, key.pos[1] * 0.85 + 0.6, key.pos[2]],
    look: [key.look[0] * 0.3, key.look[1], key.look[2]],
    fov: Math.min(56, Math.max(48, key.fov)),
  };
}

export function buildKeyframes(
  variant: RailVariant = "desktop",
): CameraKeyframe[] {
  if (variant === "mobile") return DESKTOP_KEYFRAMES.map(toMobileKeyframe);
  return DESKTOP_KEYFRAMES.map((key) => ({ ...key }));
}

/** Uniform Catmull-Rom basis across four scalar control values. */
function catmullRom(p0: number, p1: number, p2: number, p3: number, u: number): number {
  const u2 = u * u;
  const u3 = u2 * u;
  return (
    0.5 *
    (2 * p1 +
      (-p0 + p2) * u +
      (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 +
      (-p0 + 3 * p1 - 3 * p2 + p3) * u3)
  );
}

function smoothstep(t: number): number {
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

/**
 * Sample the rail at `progress`, writing into `out`. Pure and total:
 * out-of-range progress clamps onto the rail (refresh / overscroll).
 */
export function sampleCamera(
  keyframes: readonly CameraKeyframe[],
  progress: number,
  out: CameraFrame,
): CameraFrame {
  const count = keyframes.length;
  if (count === 0) return out;
  const p = Math.min(1, Math.max(0, progress));

  let i = 0;
  while (i < count - 2 && keyframes[i + 1].t <= p) i++;

  const span = keyframes[i + 1].t - keyframes[i].t;
  const u = span > 0 ? (p - keyframes[i].t) / span : 0;

  const at = (index: number) =>
    keyframes[Math.min(count - 1, Math.max(0, index))];
  const k0 = at(i - 1);
  const k1 = at(i);
  const k2 = at(i + 1);
  const k3 = at(i + 2);

  for (let axis = 0; axis < 3; axis++) {
    out.pos[axis] = catmullRom(k0.pos[axis], k1.pos[axis], k2.pos[axis], k3.pos[axis], u);
    out.look[axis] = catmullRom(k0.look[axis], k1.look[axis], k2.look[axis], k3.look[axis], u);
  }
  out.fov = k1.fov + (k2.fov - k1.fov) * smoothstep(u);
  return out;
}

/**
 * Scroll progress → continuous morph coordinate (0..MORPH_COUNT-1). The
 * vertex shader blends between floor/ceil states — the field is always
 * mid-transition, never swapped.
 */
export function morphFloat(progress: number): number {
  const p = Math.min(1, Math.max(0, progress));
  return p * (MORPH_COUNT - 1);
}

/** Chapter index for a progress value. */
export function chapterAt(progress: number): number {
  const p = Math.min(1, Math.max(0, progress));
  for (let i = CHAPTERS.length - 1; i >= 0; i--) {
    if (p >= CHAPTERS[i].start) return i;
  }
  return 0;
}

/** Chapter index plus local progress (0..1) inside the chapter. */
export function chapterLocal(progress: number): { index: number; local: number } {
  const index = chapterAt(progress);
  const chapter = CHAPTERS[index];
  const span = chapter.end - chapter.start;
  const local =
    span > 0 ? Math.min(1, Math.max(0, (progress - chapter.start) / span)) : 0;
  return { index, local };
}
