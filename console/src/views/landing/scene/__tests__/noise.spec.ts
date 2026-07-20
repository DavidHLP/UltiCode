import { describe, expect, it } from "vitest";
import { createNoise2D, fbm } from "../noise";

describe("createNoise2D", () => {
  it("is deterministic for the same seed", () => {
    const a = createNoise2D(42);
    const b = createNoise2D(42);
    for (const [x, y] of [[0, 0], [1.5, -3.25], [100, 77]]) {
      expect(a(x, y)).toBe(b(x, y));
    }
  });

  it("differs across seeds", () => {
    const a = createNoise2D(1);
    const b = createNoise2D(2);
    expect(a(3.3, 4.4)).not.toBe(b(3.3, 4.4));
  });

  it("stays bounded over a wide sweep", () => {
    const noise = createNoise2D(7);
    let max = 0;
    for (let i = 0; i < 2000; i++) {
      const v = Math.abs(noise(i * 0.137, i * 0.291));
      max = Math.max(max, v);
      expect(Number.isFinite(v)).toBe(true);
    }
    expect(max).toBeLessThan(1.2);
    expect(max).toBeGreaterThan(0.2); // actually varies, not constant
  });
});

describe("fbm", () => {
  it("is bounded and deterministic", () => {
    const noise = createNoise2D(11);
    let max = 0;
    for (let i = 0; i < 1000; i++) {
      const v = Math.abs(fbm(noise, i * 0.05, i * 0.08, 4));
      max = Math.max(max, v);
    }
    expect(max).toBeLessThanOrEqual(1.05);
    expect(fbm(noise, 5, 6, 4)).toBe(fbm(noise, 5, 6, 4));
  });
});
