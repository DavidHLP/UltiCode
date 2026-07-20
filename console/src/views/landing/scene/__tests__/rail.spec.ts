import { describe, expect, it } from "vitest";
import {
  buildKeyframes,
  chapterAt,
  chapterLocal,
  chamberPassed,
  sampleCamera,
  sampleParticle,
  verdictGate,
  type CameraFrame,
} from "../rail";
import { CHAPTERS, CHAPTER_COUNT, FINALE } from "../layout";

function newFrame(): CameraFrame {
  return { pos: [0, 0, 0], look: [0, 0, 0], fov: 0 };
}

describe("buildKeyframes", () => {
  it("anchors span the full scroll range with strictly increasing t", () => {
    const keys = buildKeyframes("desktop");
    expect(keys[0].t).toBe(0);
    expect(keys[keys.length - 1].t).toBe(1);
    for (let i = 1; i < keys.length; i++) {
      expect(keys[i].t).toBeGreaterThan(keys[i - 1].t);
    }
  });

  it("mobile variant compresses lateral movement and FOV swing", () => {
    const desktop = buildKeyframes("desktop");
    const mobile = buildKeyframes("mobile");
    expect(mobile).toHaveLength(desktop.length);
    const lateral = (keys: typeof desktop) =>
      Math.max(...keys.map((k) => Math.abs(k.pos[0])));
    expect(lateral(mobile)).toBeLessThan(lateral(desktop));
    for (const key of mobile) {
      expect(key.fov).toBeGreaterThanOrEqual(46);
      expect(key.fov).toBeLessThanOrEqual(54);
    }
    // Same narrative anchors: progress mapping is shared across variants.
    for (let i = 0; i < desktop.length; i++) {
      expect(mobile[i].t).toBe(desktop[i].t);
    }
  });
});

describe("sampleCamera", () => {
  const keys = buildKeyframes("desktop");

  it("passes exactly through every pinned keyframe", () => {
    const frame = newFrame();
    for (const key of keys) {
      sampleCamera(keys, key.t, frame);
      for (let axis = 0; axis < 3; axis++) {
        expect(frame.pos[axis]).toBeCloseTo(key.pos[axis], 5);
      }
    }
  });

  it("is continuous: small scroll steps never jump the camera", () => {
    const frame = newFrame();
    let previous: readonly number[] | null = null;
    for (let p = 0; p <= 1.0001; p += 0.002) {
      sampleCamera(keys, Math.min(1, p), frame);
      if (previous) {
        for (let axis = 0; axis < 3; axis++) {
          // 0.2% of the rail must never teleport the camera; dense
          // keyframe spans (finale push-in) legitimately move ~1 unit.
          expect(Math.abs(frame.pos[axis] - previous[axis])).toBeLessThan(1.6);
          expect(Number.isFinite(frame.pos[axis])).toBe(true);
        }
      }
      previous = [...frame.pos];
    }
  });

  it("clamps out-of-range progress onto the rail (refresh / overscroll)", () => {
    const frame = newFrame();
    sampleCamera(keys, -0.5, frame);
    const atStart = [...frame.pos];
    sampleCamera(keys, 1.7, frame);
    const atEnd = [...frame.pos];
    sampleCamera(keys, 0, frame);
    expect([...frame.pos]).toEqual(atStart);
    sampleCamera(keys, 1, frame);
    expect([...frame.pos]).toEqual(atEnd);
  });

  it("is a pure function of progress — reverse scrolling replays frames", () => {
    const a = newFrame();
    const b = newFrame();
    for (const p of [0.13, 0.37, 0.59, 0.81]) {
      sampleCamera(keys, p, a);
      sampleCamera(keys, p, b);
      expect([...a.pos]).toEqual([...b.pos]);
      expect([...a.look]).toEqual([...b.look]);
      expect(a.fov).toBe(b.fov);
    }
  });

  it("really travels: position, look target, and FOV all change", () => {
    const start = newFrame();
    const end = newFrame();
    sampleCamera(keys, 0, start);
    sampleCamera(keys, 1, end);
    const travel = Math.hypot(
      end.pos[0] - start.pos[0],
      end.pos[1] - start.pos[1],
      end.pos[2] - start.pos[2],
    );
    expect(travel).toBeGreaterThan(200);
    expect(start.look[2]).not.toBe(end.look[2]);
    expect(start.fov).not.toBe(end.fov);
  });
});

describe("chapter mapping", () => {
  it("covers the whole range without gaps", () => {
    for (let p = 0; p <= 1; p += 0.001) {
      const index = chapterAt(p);
      expect(index).toBeGreaterThanOrEqual(0);
      expect(index).toBeLessThan(CHAPTER_COUNT);
      const { local } = chapterLocal(p);
      expect(local).toBeGreaterThanOrEqual(0);
      expect(local).toBeLessThanOrEqual(1);
    }
  });

  it("dwell compositions live inside their own chapter", () => {
    CHAPTERS.forEach((chapter, index) => {
      expect(chapterAt(chapter.dwell)).toBe(index);
    });
  });
});

describe("chamberPassed", () => {
  it("is causal: chambers flip in order and never un-flip", () => {
    let previousCount = 0;
    for (let p = 0; p <= 1; p += 0.005) {
      const passed = chamberPassed(p).filter(Boolean).length;
      expect(passed).toBeGreaterThanOrEqual(previousCount);
      previousCount = passed;
    }
    expect(previousCount).toBe(8);
  });

  it("starts with nothing passed", () => {
    expect(chamberPassed(0).every((v) => !v)).toBe(true);
  });
});

describe("verdictGate", () => {
  it("peaks at the ring pass and is negligible elsewhere", () => {
    expect(verdictGate(0.585)).toBeCloseTo(1, 5);
    expect(verdictGate(0.3)).toBeLessThan(0.01);
    expect(verdictGate(0.9)).toBeLessThan(0.01);
  });
});

describe("sampleParticle", () => {
  it("stays inside the world corridor and ends at the finale node", () => {
    const out: [number, number, number] = [0, 0, 0];
    sampleParticle(1, out);
    expect(out[2]).toBeCloseTo(FINALE.center[2], 5);
    for (let p = 0; p <= 1; p += 0.01) {
      sampleParticle(p, out);
      expect(out[2]).toBeLessThanOrEqual(1);
      expect(out[2]).toBeGreaterThanOrEqual(FINALE.center[2] - 1);
    }
  });
});
