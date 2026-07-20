import { describe, expect, it } from "vitest";
import {
  buildKeyframes,
  chapterAt,
  chapterLocal,
  morphFloat,
  sampleCamera,
  type CameraFrame,
} from "../rail";
import { CHAPTERS, CHAPTER_COUNT, MORPH_COUNT } from "../layout";

function newFrame(): CameraFrame {
  return { pos: [0, 0, 0], look: [0, 0, 0], fov: 0 };
}

describe("buildKeyframes", () => {
  it("anchors span the full scroll range, strictly increasing", () => {
    const keys = buildKeyframes("desktop");
    expect(keys[0].t).toBe(0);
    expect(keys[keys.length - 1].t).toBe(1);
    for (let i = 1; i < keys.length; i++) {
      expect(keys[i].t).toBeGreaterThan(keys[i - 1].t);
    }
  });

  it("mobile variant compresses lateral drift and clamps FOV", () => {
    const desktop = buildKeyframes("desktop");
    const mobile = buildKeyframes("mobile");
    const lateral = (keys: typeof desktop) =>
      Math.max(...keys.map((k) => Math.abs(k.pos[0])));
    expect(lateral(mobile)).toBeLessThan(lateral(desktop));
    for (const key of mobile) {
      expect(key.fov).toBeGreaterThanOrEqual(48);
      expect(key.fov).toBeLessThanOrEqual(56);
    }
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

  it("is continuous: small scroll steps never teleport the camera", () => {
    const frame = newFrame();
    let previous: readonly number[] | null = null;
    for (let p = 0; p <= 1.0001; p += 0.002) {
      sampleCamera(keys, Math.min(1, p), frame);
      if (previous) {
        for (let axis = 0; axis < 3; axis++) {
          expect(Math.abs(frame.pos[axis] - previous[axis])).toBeLessThan(1.6);
          expect(Number.isFinite(frame.pos[axis])).toBe(true);
        }
      }
      previous = [...frame.pos];
    }
  });

  it("clamps out-of-range progress onto the rail", () => {
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

  it("is pure in progress — reverse scrolling replays frames exactly", () => {
    const a = newFrame();
    const b = newFrame();
    for (const p of [0.11, 0.37, 0.59, 0.83]) {
      sampleCamera(keys, p, a);
      sampleCamera(keys, p, b);
      expect([...a.pos]).toEqual([...b.pos]);
      expect([...a.look]).toEqual([...b.look]);
      expect(a.fov).toBe(b.fov);
    }
  });

  it("descends from altitude and travels the full corridor", () => {
    const start = newFrame();
    const end = newFrame();
    sampleCamera(keys, 0, start);
    sampleCamera(keys, 1, end);
    expect(start.pos[1]).toBeGreaterThan(20); // opens above the plain
    expect(end.pos[1]).toBeLessThan(4); // settles low
    expect(start.pos[2] - end.pos[2]).toBeGreaterThan(300); // full -Z travel
  });
});

describe("morphFloat", () => {
  it("maps scroll to the continuous morph coordinate", () => {
    expect(morphFloat(0)).toBe(0);
    expect(morphFloat(1)).toBe(MORPH_COUNT - 1);
    expect(morphFloat(0.5)).toBeCloseTo((MORPH_COUNT - 1) / 2);
  });

  it("clamps out-of-range progress", () => {
    expect(morphFloat(-1)).toBe(0);
    expect(morphFloat(2)).toBe(MORPH_COUNT - 1);
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
