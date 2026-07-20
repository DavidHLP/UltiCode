import { describe, expect, it } from "vitest";
import { buildPoses, CHAPTER_COUNT } from "../poses";

describe("buildPoses", () => {
  it("builds one buffer per chapter with count * 3 entries", () => {
    const poses = buildPoses(120);
    expect(poses).toHaveLength(CHAPTER_COUNT);
    for (const pose of poses) {
      expect(pose).toHaveLength(120 * 3);
    }
  });

  it("is deterministic for the same seed", () => {
    const a = buildPoses(64, 42);
    const b = buildPoses(64, 42);
    for (let i = 0; i < a.length; i++) {
      expect(Array.from(a[i])).toEqual(Array.from(b[i]));
    }
  });

  it("rejects non-positive node counts", () => {
    expect(() => buildPoses(0)).toThrow(RangeError);
    expect(() => buildPoses(-3)).toThrow(RangeError);
    expect(() => buildPoses(1.5)).toThrow(RangeError);
  });

  it("collapses to a tighter core in the finale than in the hero", () => {
    const count = 200;
    const poses = buildPoses(count);
    const maxRadius = (pose: Float32Array) => {
      let max = 0;
      for (let i = 0; i < count; i++) {
        const x = pose[i * 3];
        const y = pose[i * 3 + 1];
        const z = pose[i * 3 + 2];
        max = Math.max(max, Math.sqrt(x * x + y * y + z * z));
      }
      return max;
    };
    expect(maxRadius(poses[5])).toBeLessThan(maxRadius(poses[0]));
  });
});
