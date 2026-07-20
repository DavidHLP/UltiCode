/**
 * Camera rail — scroll progress → camera frame, as pure data and pure math.
 * No three.js imports: the rail is fully unit-testable in Node, and the
 * renderer (createLandingScene.ts) stays a thin adapter.
 *
 * Model:
 *   - One ordered list of camera keyframes over progress `t ∈ [0, 1]`.
 *     Each keyframe pins position, look-at target, and FOV.
 *   - Position and look target are sampled with Catmull-Rom splines across
 *     neighbouring keyframes, so the camera passes exactly through every
 *     pinned composition with C1 continuity — no jumps, no teleporting.
 *   - FOV is smoothstep-lerped between keyframes.
 *   - Sampling is a pure function of progress: scrolling back up replays
 *     the exact same frames in reverse (reversibility requirement).
 *
 * Look targets are independent of the path tangent on purpose: keyframes
 * point the camera at narrative objects (a chamber, the ring, the helix
 * core) while it keeps moving.
 */

import { CHAPTERS, FINALE, HELIX, NETWORK, RING, type Vec3 } from "./layout";

export interface CameraKeyframe {
  /** Scroll progress anchor (0..1, strictly increasing). */
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

/**
 * Desktop rail — 28 keyframes. Movement vocabulary per chapter:
 *   hero:    slow dolly-in with a slight orbit, then round the side to enter
 *   parse:   sustained forward travel, rear → side-rear, lateral shifts
 *   matrix:  weave past chambers, turn to watch each, overhead reveal, pull
 *   verdict: decelerate, close focus, then one fast smooth release
 *   growth:  along the track, spiral out and away
 *   network: macro moves between regions, then toward the centre
 *   finale:  steady push into the bright node, settle at the cursor
 */
const DESKTOP_KEYFRAMES: readonly CameraKeyframe[] = [
  // — hero —
  { t: 0.0, pos: [0, 0.6, 9], look: [0, 0, 0], fov: 42 },
  { t: 0.05, pos: [1.2, 0.4, 6], look: [0, 0, 0], fov: 44 },
  { t: 0.1, pos: [2.2, 0.2, 3.6], look: [0, 0, -1], fov: 46 },
  { t: 0.14, pos: [1.4, 0.1, -1.5], look: [0, 0, -8], fov: 50 },
  // — parse —
  { t: 0.18, pos: [0.8, 0.3, -8], look: [0.2, 0, -16], fov: 55 },
  { t: 0.22, pos: [-1.2, 0.5, -16], look: [0, 0, -24], fov: 55 },
  { t: 0.26, pos: [0.6, -0.2, -24], look: [-0.5, 0.4, -30], fov: 52 },
  { t: 0.3, pos: [0, 0.2, -32], look: [0, 0, -44], fov: 52 },
  // — matrix —
  { t: 0.34, pos: [2.2, 0.8, -42], look: [-5, 2.5, -48], fov: 55 },
  { t: 0.38, pos: [-1.5, -0.5, -56], look: [5, -2.5, -64.5], fov: 55 },
  { t: 0.42, pos: [1.5, 0.6, -62], look: [-5, 2.5, -70], fov: 55 },
  { t: 0.46, pos: [0, 7.5, -68], look: [0, 0, -70], fov: 60 },
  { t: 0.5, pos: [0, 2.5, -84], look: [0, 0, -98], fov: 52 },
  { t: 0.52, pos: [0, 0.8, -90], look: [0, 0, -100], fov: 48 },
  // — verdict —
  { t: 0.56, pos: [0, 0.2, -96.5], look: [0, 0, -100], fov: 45 },
  { t: 0.6, pos: [0, 0, -103], look: [0, 0, -116], fov: 58 },
  { t: 0.62, pos: [0.5, 0.5, -110], look: [1, 0, -124], fov: 55 },
  // — growth —
  { t: 0.66, pos: [4.5, 0.5, -118], look: [6, 0, -128], fov: 52 },
  { t: 0.7, pos: [6.5, 1.5, -132], look: [0, 0, -136], fov: 52 },
  { t: 0.74, pos: [9, 3.5, -140], look: [0, 0, -136], fov: 55 },
  { t: 0.78, pos: [13, 7, -146], look: [0, 0, -136], fov: 58 },
  // — network —
  { t: 0.82, pos: [10, 5, -166], look: [-6, 2, -186], fov: 55 },
  { t: 0.86, pos: [-9, 3, -184], look: [6, -2, -196], fov: 55 },
  { t: 0.89, pos: [-2, -2, -196], look: [0, 0, -206], fov: 52 },
  { t: 0.92, pos: [0, 2, -206], look: [0, 0, -220], fov: 50 },
  // — finale —
  { t: 0.95, pos: [0, 1, -222], look: [0, 0, -235], fov: 46 },
  { t: 0.98, pos: [0, 0.3, -229], look: [0, 0, -235], fov: 42 },
  { t: 1.0, pos: [0, 0, -231.5], look: [0, 0, -235], fov: 40 },
];

/**
 * Mobile rail — derived from the desktop set: lateral sweeps compressed,
 * FOV variation clamped, camera lifted slightly so primary objects stay
 * clear of narrow viewports. Same progress anchors, same narrative.
 */
function toMobileKeyframe(key: CameraKeyframe): CameraKeyframe {
  const clampFov = Math.min(54, Math.max(46, key.fov));
  return {
    t: key.t,
    pos: [key.pos[0] * 0.35, key.pos[1] * 0.7 + 0.4, key.pos[2]],
    look: [key.look[0] * 0.35, key.look[1] * 0.7, key.look[2]],
    fov: clampFov,
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
 * Sample the rail at `progress`, writing the frame into `out`.
 * Pure and total: any progress (including out-of-range) yields a stable,
 * on-rail frame — fast scrolling and mid-page refresh both recover exactly.
 */
export function sampleCamera(
  keyframes: readonly CameraKeyframe[],
  progress: number,
  out: CameraFrame,
): CameraFrame {
  const count = keyframes.length;
  if (count === 0) return out;
  const p = Math.min(1, Math.max(0, progress));

  // Segment index: last keyframe whose anchor is <= p.
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
    out.pos[axis] = catmullRom(
      k0.pos[axis],
      k1.pos[axis],
      k2.pos[axis],
      k3.pos[axis],
      u,
    );
    out.look[axis] = catmullRom(
      k0.look[axis],
      k1.look[axis],
      k2.look[axis],
      k3.look[axis],
      u,
    );
  }
  out.fov = k1.fov + (k2.fov - k1.fov) * smoothstep(u);
  return out;
}

/** Chapter index for a progress value (0..CHAPTER_COUNT-1). */
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
    span > 0
      ? Math.min(1, Math.max(0, (progress - chapter.start) / span))
      : 0;
  return { index, local };
}

/**
 * Causal chamber states: a chamber flips to "passed" only after the camera
 * has travelled past it. Matrix chapter is index 2; the eight chambers are
 * distributed across its local progress.
 */
export function chamberPassed(progress: number): boolean[] {
  const { index, local } = chapterLocal(progress);
  const matrixLocal = index > 2 ? 1 : index < 2 ? 0 : local;
  return Array.from({ length: 8 }, (_, i) => matrixLocal > (i + 0.6) / 8);
}

/**
 * The verdict release: a single smooth pulse as the camera passes through
 * the ring (keyframe t = 0.6). Gaussian falloff, no flicker.
 */
export function verdictGate(progress: number): number {
  const d = (progress - 0.585) / 0.022;
  return Math.exp(-d * d);
}

/**
 * The submission particle's own path through the world — the object the
 * camera follows. Waypoints thread every chapter's focus object; sampling
 * uses the same Catmull-Rom scheme as the rail.
 */
const PARTICLE_WAYPOINTS: readonly { t: number; pos: Vec3 }[] = [
  { t: 0.0, pos: [0, 0, 0] },
  { t: 0.14, pos: [0, 0, -6] },
  { t: 0.3, pos: [0, 0, -38] },
  { t: 0.52, pos: [0, 0, -86] },
  { t: 0.58, pos: [0, 0, -100] },
  { t: 0.62, pos: [3, 0, -110] },
  { t: 0.7, pos: [HELIX.radius, 0, -128] },
  { t: 0.78, pos: [HELIX.radius, 0, -150] },
  { t: 0.86, pos: [-6, 1, -190] },
  { t: 0.92, pos: [0, 0, NETWORK.center[2]] },
  { t: 1.0, pos: [FINALE.center[0], FINALE.center[1], FINALE.center[2]] },
];

/**
 * Sample the particle position. The particle runs slightly ahead of the
 * camera (it leads, the camera follows) and waits for it at the finale.
 */
export function sampleParticle(
  progress: number,
  out: [number, number, number],
): [number, number, number] {
  const lead = Math.min(1, Math.max(0, progress * 1.04));
  const waypoints = PARTICLE_WAYPOINTS;
  const count = waypoints.length;

  let i = 0;
  while (i < count - 2 && waypoints[i + 1].t <= lead) i++;
  const span = waypoints[i + 1].t - waypoints[i].t;
  const u = span > 0 ? (lead - waypoints[i].t) / span : 0;

  const at = (index: number) =>
    waypoints[Math.min(count - 1, Math.max(0, index))].pos;
  const p0 = at(i - 1);
  const p1 = at(i);
  const p2 = at(i + 1);
  const p3 = at(i + 2);

  for (let axis = 0; axis < 3; axis++) {
    out[axis] = catmullRom(p0[axis], p1[axis], p2[axis], p3[axis], u);
  }
  return out;
}

/** Ring centre re-exported for the renderer's pulse placement. */
export const RING_CENTER = RING.center;
