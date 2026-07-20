/**
 * World layout for the landing micro-world — the single source of truth for
 * every spatial coordinate. Both the camera rail (rail.ts) and the geometry
 * builder (world.ts) derive from this data, so the narrative path and the
 * scenery can never drift apart.
 *
 * The world is one continuous corridor along -Z. The camera travels from the
 * code core at the origin down to the finale node at z = -235; nothing is a
 * separate "scene" — chapters are regions of one world.
 */

export type Vec3 = readonly [number, number, number];

/** Chapter 0 — the code core, woken at the origin. */
export const CORE = { center: [0, 0, 0] as Vec3, radius: 2.3 };

/** Chapter 1 — the parse tunnel the camera follows the submission through. */
export const TUNNEL = { startZ: -6, endZ: -38, radius: 3 };

/** Chapter 2 — eight test chambers flanking the corridor. */
export interface ChamberLayout {
  center: Vec3;
  /** Half-extent of the chamber box. */
  half: number;
}

export const CHAMBERS: readonly ChamberLayout[] = Array.from(
  { length: 8 },
  (_, i) => ({
    center: [
      i % 2 === 0 ? -5 : 5,
      i % 4 < 2 ? 2.5 : -2.5,
      -48 - i * 5.5,
    ] as Vec3,
    half: 1.6,
  }),
);

/** Chapter 3 — the verdict ring the camera punches through. */
export const RING = { center: [0, 0, -100] as Vec3, radius: 3 };

/** Chapter 4 — the growth helix of past submissions. */
export const HELIX = {
  center: [0, 0, -136] as Vec3,
  radius: 6,
  startZ: -116,
  endZ: -158,
  turns: 3,
};

/** Chapter 5 — the wider network of contests and community. */
export const NETWORK = {
  center: [0, 0, -200] as Vec3,
  radius: 16,
  clusters: 9,
};

/** Chapter 6 — the finale node that resolves into a cursor. */
export const FINALE = { center: [0, 0, -235] as Vec3, radius: 0.9 };

/** Narrative chapters as progress ranges over page scroll. */
export const CHAPTER_COUNT = 7;

export interface ChapterConfig {
  name: string;
  start: number;
  end: number;
  /**
   * Progress value of the chapter's representative composition. Reduced
   * motion renders exactly this frame instead of flying the rail.
   */
  dwell: number;
}

export const CHAPTERS: readonly ChapterConfig[] = [
  { name: "hero", start: 0.0, end: 0.14, dwell: 0.07 },
  { name: "parse", start: 0.14, end: 0.3, dwell: 0.22 },
  { name: "matrix", start: 0.3, end: 0.52, dwell: 0.46 },
  { name: "verdict", start: 0.52, end: 0.62, dwell: 0.56 },
  { name: "growth", start: 0.62, end: 0.78, dwell: 0.74 },
  { name: "network", start: 0.78, end: 0.92, dwell: 0.86 },
  { name: "finale", start: 0.92, end: 1.0, dwell: 0.98 },
] as const;
