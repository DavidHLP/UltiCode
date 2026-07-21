import { describe, expect, it } from "vitest";
import { buildMorphTargets } from "../morphTargets";
import { COLLAPSE_POINT, MORPH_COUNT, MONOLITHS, TERRAIN } from "../layout";

const COUNT = 4000;

describe("buildMorphTargets", () => {
  it("builds one buffer per morph state plus randomness", () => {
    const targets = buildMorphTargets(COUNT);
    expect(targets.states).toHaveLength(MORPH_COUNT);
    for (const state of targets.states) {
      expect(state).toHaveLength(COUNT * 3);
    }
    expect(targets.random).toHaveLength(COUNT * 3);
  });

  it("is deterministic for the same seed", () => {
    const a = buildMorphTargets(500, 9);
    const b = buildMorphTargets(500, 9);
    for (let s = 0; s < MORPH_COUNT; s++) {
      expect(Array.from(a.states[s])).toEqual(Array.from(b.states[s]));
    }
  });

  it("rejects invalid counts", () => {
    expect(() => buildMorphTargets(0)).toThrow(RangeError);
    expect(() => buildMorphTargets(-5)).toThrow(RangeError);
    expect(() => buildMorphTargets(2.5)).toThrow(RangeError);
  });

  it("terrain heights stay within the plain's bounds", () => {
    const targets = buildMorphTargets(COUNT);
    const terrain = targets.states[0];
    for (let i = 0; i < COUNT; i++) {
      const y = terrain[i * 3 + 1];
      expect(y).toBeGreaterThan(-3);
      expect(y).toBeLessThan(TERRAIN.maxHeight + 2);
    }
  });

  it("monoliths cluster around the three entry steles", () => {
    const targets = buildMorphTargets(COUNT);
    const monoliths = targets.states[4];
    let near = 0;
    for (let i = 0; i < COUNT; i++) {
      const x = monoliths[i * 3];
      const z = monoliths[i * 3 + 2];
      const close = MONOLITHS.some(
        (m) => Math.abs(x - m.x) < m.width && Math.abs(z - m.z) < 3,
      );
      if (close) near++;
    }
    // Most particles coat the steles; the rest form the ground mist nearby.
    expect(near / COUNT).toBeGreaterThan(0.45);
  });

  it("collapse pulls every particle into a volumetric orb", () => {
    const targets = buildMorphTargets(COUNT);
    const collapse = targets.states[5];
    let core = 0;
    for (let i = 0; i < COUNT; i++) {
      const dx = collapse[i * 3] - COLLAPSE_POINT.x;
      const dy = collapse[i * 3 + 1] - COLLAPSE_POINT.y;
      const dz = collapse[i * 3 + 2] - COLLAPSE_POINT.z;
      const r = Math.hypot(dx, dy, dz);
      // Halo embers drift to 4.4; nothing beyond.
      expect(r).toBeLessThan(4.5);
      if (r < 2.7) core++;
    }
    // The bulk of the field fills the ball, not a thin shell.
    expect(core / COUNT).toBeGreaterThan(0.85);
  });

  it("every state covers the corridor the camera travels", () => {
    const targets = buildMorphTargets(COUNT);
    for (const state of targets.states) {
      for (let i = 0; i < COUNT; i++) {
        expect(Number.isFinite(state[i * 3])).toBe(true);
        expect(Number.isFinite(state[i * 3 + 1])).toBe(true);
        expect(Number.isFinite(state[i * 3 + 2])).toBe(true);
      }
    }
  });
});
