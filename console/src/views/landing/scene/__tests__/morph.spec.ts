import { describe, expect, it } from "vitest";
import {
  chapterFloat,
  easeInOut,
  groupRotation,
  growthTint,
  nodeColorMix,
  samplePose,
  verdictPulse,
} from "../morph";
import { buildPoses } from "../poses";

describe("chapterFloat", () => {
  it("maps 0 and 1 to the first and last chapter", () => {
    expect(chapterFloat(0)).toBe(0);
    expect(chapterFloat(1)).toBe(5);
  });

  it("clamps out-of-range progress (fast scroll / refresh restore)", () => {
    expect(chapterFloat(-0.4)).toBe(0);
    expect(chapterFloat(1.7)).toBe(5);
  });

  it("spreads chapters evenly across progress", () => {
    expect(chapterFloat(0.5)).toBeCloseTo(2.5);
  });
});

describe("easeInOut", () => {
  it("is 0 at 0 and 1 at 1", () => {
    expect(easeInOut(0)).toBe(0);
    expect(easeInOut(1)).toBe(1);
  });

  it("clamps inputs outside [0, 1]", () => {
    expect(easeInOut(-1)).toBe(0);
    expect(easeInOut(2)).toBe(1);
  });
});

describe("samplePose", () => {
  const poses = buildPoses(50);
  const out = new Float32Array(50 * 3);

  it("returns the exact hero pose at progress 0", () => {
    samplePose(poses, 0, out);
    for (let k = 0; k < out.length; k++) {
      expect(out[k]).toBeCloseTo(poses[0][k], 6);
    }
  });

  it("returns the exact finale pose at progress 1", () => {
    const last = poses[poses.length - 1];
    samplePose(poses, 1, out);
    for (let k = 0; k < out.length; k++) {
      expect(out[k]).toBeCloseTo(last[k], 6);
    }
  });

  it("interpolates between neighbouring poses mid-transition", () => {
    // Progress 0.1 sits between chapter 0 and 1 (chapter float = 0.5).
    samplePose(poses, 0.1, out);
    const t = easeInOut(0.5);
    const expected = poses[0][0] + (poses[1][0] - poses[0][0]) * t;
    expect(out[0]).toBeCloseTo(expected, 5);
  });

  it("handles a single-pose input without crashing", () => {
    const single = [buildPoses(10)[0]];
    const small = new Float32Array(30);
    samplePose(single, 0.7, small);
    expect(Array.from(small)).toEqual(Array.from(single[0]));
  });
});

describe("verdictPulse", () => {
  it("peaks at the judge → growth boundary", () => {
    expect(verdictPulse(0.5)).toBeCloseTo(1, 5);
  });

  it("is negligible away from the boundary", () => {
    expect(verdictPulse(0.1)).toBeLessThan(0.01);
    expect(verdictPulse(0.9)).toBeLessThan(0.01);
  });
});

describe("growthTint", () => {
  it("is zero before the verdict and settles after it", () => {
    expect(growthTint(0.3)).toBe(0);
    expect(growthTint(0.9)).toBeCloseTo(0.18, 5);
  });
});

describe("nodeColorMix", () => {
  it("never exceeds 1 even at the pulse peak", () => {
    expect(nodeColorMix(0.5)).toBeLessThanOrEqual(1);
  });
});

describe("groupRotation", () => {
  it("stays at zero when interaction is disabled (reduced motion)", () => {
    expect(groupRotation(0.7, false)).toBe(0);
  });

  it("is scroll-driven when interactive, never autonomous", () => {
    expect(groupRotation(0, true)).toBe(0);
    expect(groupRotation(1, true)).toBeCloseTo(Math.PI * 0.6);
  });
});
