/**
 * Wasteland layout — single source of truth for the monochrome world:
 * chapter timing, terrain extent, and morph-state anchors. Both the camera
 * rail and the morph-target builder derive from this data.
 *
 * The world is one continuous particle plain along -Z. Chapters are not
 * separate scenes: every particle owns a target position in each of the six
 * morph states, and scroll blends the whole field continuously.
 */

/** Six morph states, indexed 0..5 — the particle state machine. */
export const MORPH_STATES = [
  "terrain", // hero: the plain wakes out of noise
  "tunnel", // parse: particles lift into a structural gate
  "slabs", // judge: vertical test steles rise from the field
  "starfield", // growth: submissions dissolve into a drifting sky
  "monoliths", // network: three entry steles (problems/contests/community)
  "collapse", // finale: everything contracts to one point of light
] as const;

export type MorphState = (typeof MORPH_STATES)[number];

export const MORPH_COUNT = MORPH_STATES.length;

export interface ChapterConfig {
  name: string;
  /** Morph state this chapter dwells in. */
  state: number;
  start: number;
  end: number;
  /** Progress of the chapter's representative composition (reduced motion). */
  dwell: number;
  /** Z anchor of the chapter's in-world title plane. */
  titleZ: number;
}

export const CHAPTERS: readonly ChapterConfig[] = [
  { name: "hero", state: 0, start: 0.0, end: 0.16, dwell: 0.08, titleZ: -34 },
  { name: "parse", state: 1, start: 0.16, end: 0.34, dwell: 0.25, titleZ: -78 },
  { name: "matrix", state: 2, start: 0.34, end: 0.52, dwell: 0.43, titleZ: -132 },
  { name: "growth", state: 3, start: 0.52, end: 0.7, dwell: 0.61, titleZ: -188 },
  { name: "network", state: 4, start: 0.7, end: 0.88, dwell: 0.79, titleZ: -238 },
  { name: "finale", state: 5, start: 0.88, end: 1.0, dwell: 0.95, titleZ: -296 },
] as const;

export const CHAPTER_COUNT = CHAPTERS.length;

/** Terrain plain extent. */
export const TERRAIN = {
  width: 170, // x: -85..85
  depth: 380, // z: 30..-350
  maxHeight: 7.5,
};

/** Test steles (matrix chapter): eight slabs in two ranks. */
export interface Stele {
  x: number;
  z: number;
  width: number;
  height: number;
}

export const SLABS: readonly Stele[] = Array.from({ length: 8 }, (_, i) => ({
  x: i % 2 === 0 ? -7 : 7,
  z: -108 - Math.floor(i / 2) * 9,
  width: 6,
  height: 11 + (i % 3) * 1.5,
}));

/** Entry monoliths (network chapter): problems / contests / community. */
export const MONOLITHS = [
  { x: -15, z: -238, width: 7, height: 22 },
  { x: 0, z: -244, width: 8, height: 27 },
  { x: 15, z: -238, width: 7, height: 22 },
] as const;

/** Starfield centre (growth chapter). */
export const SKY = { x: 0, y: 26, z: -190, radius: 90 };

/** Collapse point (finale). */
export const COLLAPSE_POINT = { x: 0, y: 1.2, z: -305 } as const;

/** Particle budgets per device class. */
export const PARTICLE_BUDGET = { desktop: 50000, mobile: 14000 } as const;
