/**
 * World geometry builder — turns the layout data into static GPU buffers.
 * Pure functions, no three.js: output is typed arrays the renderer uploads
 * verbatim. The world is built once; per-frame work only moves the camera,
 * the particle, and a few material colors.
 *
 * Node budget is scaled by `detailScale` (mobile ≈ 0.45) from one place.
 */

import {
  CHAMBERS,
  CORE,
  FINALE,
  HELIX,
  NETWORK,
  RING,
  TUNNEL,
} from "./layout";

export interface WorldGeometry {
  /** All ambient node positions, xyz triplets. */
  nodePositions: Float32Array;
  /** Per-node base colors (vertex colors), rgb triplets in 0..1. */
  nodeColors: Float32Array;
  /** Line edges as index pairs into the node buffer. */
  edgeIndices: Uint32Array;
  /** Eight test chambers, each a separate box (own material → state tint). */
  chambers: { corners: Float32Array; edges: Uint32Array }[];
  /** Verdict ring nodes (separate mesh so the pulse can tint it). */
  ringPositions: Float32Array;
  nodeCount: number;
}

/** Deterministic PRNG — stable world across reloads and tests. */
function createRng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
}

// Solarized accents in linear-ish 0..1 floats.
const CYAN = [0.165, 0.631, 0.596] as const;
const BLUE = [0.149, 0.545, 0.824] as const;
const GREEN = [0.522, 0.6, 0.0] as const;
const RED = [0.863, 0.196, 0.184] as const;

interface ZoneWriter {
  positions: number[];
  colors: number[];
  edges: number[];
  nodeIndex: number;
}

function pushNode(
  zone: ZoneWriter,
  x: number,
  y: number,
  z: number,
  color: readonly number[],
): void {
  zone.positions.push(x, y, z);
  zone.colors.push(color[0], color[1], color[2]);
  zone.nodeIndex++;
}

/** Link each node to its successor and a stride neighbour inside the zone. */
function linkZone(zone: ZoneWriter, start: number, stride: number): void {
  const count = zone.nodeIndex - start;
  for (let i = 0; i < count; i++) {
    zone.edges.push(start + i, start + ((i + 1) % count));
    if (count > stride) {
      zone.edges.push(start + i, start + ((i + stride) % count));
    }
  }
}

function buildCore(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    const inner = i % 4 === 0;
    const radius = (inner ? 0.5 : 1) * CORE.radius;
    const y = 1 - (i / Math.max(1, count - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * i + rng() * 0.05;
    pushNode(
      zone,
      Math.cos(theta) * r * radius,
      y * radius,
      Math.sin(theta) * r * radius,
      i % 6 === 0 ? BLUE : CYAN,
    );
  }
  linkZone(zone, start, 31);
}

function buildTunnel(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const span = TUNNEL.endZ - TUNNEL.startZ;
  for (let i = 0; i < count; i++) {
    const t = i / Math.max(1, count - 1);
    // Tube lattice: rings of nodes with a slow twist, plus jittered tokens.
    const angle = t * Math.PI * 6 + (i % 12) * ((Math.PI * 2) / 12);
    const radius = TUNNEL.radius * (i % 5 === 0 ? 0.55 : 1);
    pushNode(
      zone,
      Math.cos(angle) * radius + (rng() - 0.5) * 0.15,
      Math.sin(angle) * radius + (rng() - 0.5) * 0.15,
      TUNNEL.startZ + t * span,
      i % 7 === 0 ? BLUE : CYAN,
    );
  }
  linkZone(zone, start, 12);
}

function buildMatrixAmbient(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const first = CHAMBERS[0].center[2];
  const last = CHAMBERS[CHAMBERS.length - 1].center[2];
  for (let i = 0; i < count; i++) {
    // Data conduits between chambers: a loose spine down the corridor.
    const t = i / Math.max(1, count - 1);
    pushNode(
      zone,
      (rng() - 0.5) * 2.4,
      (rng() - 0.5) * 2.4,
      first + (last - first) * t,
      CYAN,
    );
  }
  linkZone(zone, start, 9);
}

function buildHelix(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const span = HELIX.endZ - HELIX.startZ;
  for (let i = 0; i < count; i++) {
    const t = i / Math.max(1, count - 1);
    // Past submissions ride the track; a restrained few are failed attempts
    // recorded on a wider, dimmer side orbit.
    const failed = i % 23 === 0;
    const radius = HELIX.radius * (failed ? 1.45 : 1) + (rng() - 0.5) * 0.2;
    const angle = t * Math.PI * 2 * HELIX.turns + rng() * 0.06;
    pushNode(
      zone,
      HELIX.center[0] + Math.cos(angle) * radius,
      HELIX.center[1] + (rng() - 0.5) * 0.4,
      HELIX.startZ + t * span,
      failed ? RED : i % 3 === 0 ? GREEN : CYAN,
    );
  }
  linkZone(zone, start, 17);
}

function buildNetwork(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const golden = Math.PI * (3 - Math.sqrt(5));
  const centers: number[] = [];
  for (let c = 0; c < NETWORK.clusters; c++) {
    const y = 1 - (c / (NETWORK.clusters - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * c;
    centers.push(
      NETWORK.center[0] + Math.cos(theta) * r * NETWORK.radius,
      NETWORK.center[1] + y * NETWORK.radius * 0.6,
      NETWORK.center[2] + Math.sin(theta) * r * NETWORK.radius * 0.7,
    );
  }
  const clusterHeads: number[] = [];
  for (let i = 0; i < count; i++) {
    const c = (i % NETWORK.clusters) * 3;
    if (Math.floor(i / NETWORK.clusters) === 0) clusterHeads.push(zone.nodeIndex);
    pushNode(
      zone,
      centers[c] + (rng() - 0.5) * 3.2,
      centers[c + 1] + (rng() - 0.5) * 3.2,
      centers[c + 2] + (rng() - 0.5) * 3.2,
      i % 5 === 0 ? BLUE : CYAN,
    );
  }
  linkZone(zone, start, NETWORK.clusters);
  // Inter-cluster links: the network reads as one connected structure.
  for (let c = 0; c < clusterHeads.length; c++) {
    zone.edges.push(
      clusterHeads[c],
      clusterHeads[(c + 1) % clusterHeads.length],
    );
    zone.edges.push(
      clusterHeads[c],
      clusterHeads[(c + 4) % clusterHeads.length],
    );
  }
}

function buildFinale(zone: ZoneWriter, count: number, rng: () => number): void {
  const start = zone.nodeIndex;
  const cursorCount = Math.max(6, Math.floor(count * 0.25));
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    if (i >= count - cursorCount) {
      // The cursor: a thin vertical line beside the node.
      const k = (i - (count - cursorCount)) / Math.max(1, cursorCount - 1);
      pushNode(
        zone,
        FINALE.center[0] + 1.1,
        FINALE.center[1] + (k - 0.5) * 2,
        FINALE.center[2],
        GREEN,
      );
      continue;
    }
    const y = 1 - (i / Math.max(1, count - cursorCount - 1)) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * i + rng() * 0.05;
    pushNode(
      zone,
      FINALE.center[0] + Math.cos(theta) * r * FINALE.radius,
      FINALE.center[1] + y * FINALE.radius,
      FINALE.center[2] + Math.sin(theta) * r * FINALE.radius,
      CYAN,
    );
  }
  linkZone(zone, start, 7);
}

function buildRing(count: number): Float32Array {
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const angle = (i / count) * Math.PI * 2;
    positions[i * 3] = RING.center[0] + Math.cos(angle) * RING.radius;
    positions[i * 3 + 1] = RING.center[1] + Math.sin(angle) * RING.radius;
    positions[i * 3 + 2] = RING.center[2];
  }
  return positions;
}

function buildChamber(center: readonly number[], half: number) {
  const corners = new Float32Array(8 * 3);
  let n = 0;
  for (const sx of [-1, 1]) {
    for (const sy of [-1, 1]) {
      for (const sz of [-1, 1]) {
        corners[n++] = center[0] + sx * half;
        corners[n++] = center[1] + sy * half;
        corners[n++] = center[2] + sz * half;
      }
    }
  }
  // Corner order: x outer, y middle, z inner → box edges below.
  const edges = new Uint32Array([
    0, 1, 2, 3, 4, 5, 6, 7, // z edges
    0, 2, 1, 3, 4, 6, 5, 7, // y edges
    0, 4, 1, 5, 2, 6, 3, 7, // x edges
  ]);
  return { corners, edges };
}

/**
 * Build the whole world. `detailScale` (0..1) scales node counts; structure
 * and coordinates are identical at every scale so the rail always matches.
 */
export function buildWorld(detailScale = 1, seed = 20260720): WorldGeometry {
  const scale = Math.min(1, Math.max(0.2, detailScale));
  const rng = createRng(seed);
  const zone: ZoneWriter = { positions: [], colors: [], edges: [], nodeIndex: 0 };

  buildCore(zone, Math.round(260 * scale), rng);
  buildTunnel(zone, Math.round(300 * scale), rng);
  buildMatrixAmbient(zone, Math.round(80 * scale), rng);
  buildHelix(zone, Math.round(260 * scale), rng);
  buildNetwork(zone, Math.round(320 * scale), rng);
  buildFinale(zone, Math.round(80 * scale), rng);

  return {
    nodePositions: new Float32Array(zone.positions),
    nodeColors: new Float32Array(zone.colors),
    edgeIndices: new Uint32Array(zone.edges),
    chambers: CHAMBERS.map((chamber) =>
      buildChamber(chamber.center, chamber.half),
    ),
    ringPositions: buildRing(Math.round(64 * scale)),
    nodeCount: zone.nodeIndex,
  };
}
