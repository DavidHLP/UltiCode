import { describe, it, expect } from "vitest";
import { Vector2 } from "three";
import { advanceTrail } from "../MousePointer";

describe("advanceTrail", () => {
  it("preserves trail capacity and prepends the new head", () => {
    const trail = [new Vector2(0, 0), new Vector2(1, 1), new Vector2(2, 2)];
    const smooth = new Vector2(0, 0);
    const mouse = new Vector2(10, 10);
    advanceTrail(trail, smooth, mouse, 1.0); // lerp 1 -> smooth snaps to mouse
    expect(trail).toHaveLength(3); // capacity preserved
    expect(trail[0].x).toBe(10); // newest head = mouse
    expect(trail[0].y).toBe(10);
    expect(trail[2].x).toBe(1); // oldest (2,2) dropped; (1,1) is now last
  });

  it("lerps the smoothed pointer partway toward the mouse", () => {
    const trail: Vector2[] = [];
    for (let i = 0; i < 5; i++) trail.push(new Vector2(0, 0));
    const smooth = new Vector2(0, 0);
    const mouse = new Vector2(1, 0);
    advanceTrail(trail, smooth, mouse, 0.5);
    expect(smooth.x).toBeCloseTo(0.5);
    expect(trail[0].x).toBeCloseTo(0.5); // head = smoothed position
  });

  it("drops the oldest sample (ring buffer semantics)", () => {
    const trail = [new Vector2(9, 9), new Vector2(8, 8)];
    advanceTrail(trail, new Vector2(0, 0), new Vector2(1, 1), 1.0);
    // after: head=(1,1), then (9,9); oldest (8,8) dropped
    expect(trail).toHaveLength(2);
    expect(trail[0].x).toBe(1);
    expect(trail[1].x).toBe(9);
  });

  it("keeps capacity stable across many advances", () => {
    const trail: Vector2[] = [];
    for (let i = 0; i < 32; i++) trail.push(new Vector2(0, 0));
    const smooth = new Vector2(0, 0);
    for (let i = 0; i < 100; i++) {
      advanceTrail(trail, smooth, new Vector2(i, 0), 0.2);
    }
    expect(trail).toHaveLength(32);
  });
});
