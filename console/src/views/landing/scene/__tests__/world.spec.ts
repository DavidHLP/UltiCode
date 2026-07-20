import { describe, expect, it } from "vitest";
import { buildWorld } from "../world";
import { CHAMBERS, FINALE, RING } from "../layout";

describe("buildWorld", () => {
  it("produces consistent buffers", () => {
    const world = buildWorld(1);
    expect(world.nodePositions.length).toBe(world.nodeCount * 3);
    expect(world.nodeColors.length).toBe(world.nodeCount * 3);
    expect(world.edgeIndices.length % 2).toBe(0);
    for (const index of world.edgeIndices) {
      expect(index).toBeLessThan(world.nodeCount);
    }
  });

  it("is deterministic for the same seed", () => {
    const a = buildWorld(1, 7);
    const b = buildWorld(1, 7);
    expect(Array.from(a.nodePositions)).toEqual(Array.from(b.nodePositions));
    expect(Array.from(a.edgeIndices)).toEqual(Array.from(b.edgeIndices));
  });

  it("scales node count with detailScale but keeps the structure", () => {
    const full = buildWorld(1);
    const small = buildWorld(0.45);
    expect(small.nodeCount).toBeLessThan(full.nodeCount * 0.6);
    expect(small.chambers).toHaveLength(full.chambers.length);
    expect(small.ringPositions.length).toBeLessThan(full.ringPositions.length);
  });

  it("builds eight chambers as boxes with 8 corners and 12 edges", () => {
    const world = buildWorld(1);
    expect(world.chambers).toHaveLength(CHAMBERS.length);
    for (const chamber of world.chambers) {
      expect(chamber.corners.length).toBe(8 * 3);
      expect(chamber.edges.length).toBe(12 * 2);
      for (const index of chamber.edges) {
        expect(index).toBeLessThan(8);
      }
    }
  });

  it("places ring nodes on the verdict circle", () => {
    const world = buildWorld(1);
    const count = world.ringPositions.length / 3;
    for (let i = 0; i < count; i++) {
      const dx = world.ringPositions[i * 3] - RING.center[0];
      const dy = world.ringPositions[i * 3 + 1] - RING.center[1];
      const dz = world.ringPositions[i * 3 + 2] - RING.center[2];
      expect(Math.hypot(dx, dy)).toBeCloseTo(RING.radius, 4);
      expect(dz).toBe(0);
    }
  });

  it("spans the corridor from the core to the finale node", () => {
    const world = buildWorld(1);
    let minZ = Infinity;
    let maxZ = -Infinity;
    for (let i = 0; i < world.nodeCount; i++) {
      const z = world.nodePositions[i * 3 + 2];
      minZ = Math.min(minZ, z);
      maxZ = Math.max(maxZ, z);
    }
    expect(maxZ).toBeGreaterThan(-3); // core at the origin
    // Node jitter around the finale centre stays within its cluster radius.
    expect(minZ).toBeLessThan(FINALE.center[2] - 0.5);
  });
});
