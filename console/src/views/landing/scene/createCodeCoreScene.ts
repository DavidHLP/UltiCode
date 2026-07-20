/**
 * three.js renderer for the landing code core — a thin adapter over the pure
 * pose/morph model. Loaded via dynamic import from CodeCoreCanvas so three
 * never blocks first paint.
 *
 * Scene inventory (kept deliberately small):
 *   - one THREE.Points cloud (the core nodes)
 *   - one THREE.LineSegments lattice (persistent edges between node indices)
 *   - no post-processing, no shadows, no external assets
 */

import * as THREE from "three";
import { buildPoses } from "./poses";
import {
  groupRotation,
  nodeColorMix,
  samplePose,
  verdictPulse,
} from "./morph";

export interface CodeCoreSceneOptions {
  canvas: HTMLCanvasElement;
  /** Node count — the caller scales this down for small devices. */
  count: number;
  maxDpr: number;
  /** Pointer parallax + scroll rotation; false under reduced motion. */
  interactive: boolean;
}

export interface CodeCoreScene {
  /** Render a single frame at the given scroll progress (0..1). */
  render(progress: number): void;
  /** Nudge pointer parallax target; ignored when not interactive. */
  setPointer(nx: number, ny: number): void;
  setSize(width: number, height: number): void;
  dispose(): void;
}

// Solarized accents, resolved once — legible on both base03 and base2.
const COLOR_STRUCTURE = new THREE.Color("#2aa198"); // cyan: code/structure
const COLOR_VERDICT = new THREE.Color("#859900"); // green: Accepted only

export function createCodeCoreScene(
  options: CodeCoreSceneOptions,
): CodeCoreScene {
  const { canvas, count, maxDpr, interactive } = options;

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: "low-power",
  });
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, maxDpr));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0, 0, 7);

  const poses = buildPoses(count);
  const positions = new Float32Array(count * 3);
  samplePose(poses, 0, positions);

  const group = new THREE.Group();
  scene.add(group);

  const pointGeometry = new THREE.BufferGeometry();
  pointGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  const pointMaterial = new THREE.PointsMaterial({
    color: COLOR_STRUCTURE.clone(),
    size: 0.045,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.9,
    depthWrite: false,
  });
  const points = new THREE.Points(pointGeometry, pointMaterial);
  group.add(points);

  // Persistent edge set: each node links to its successor and a stride
  // neighbour, so edges reorganize with the morph instead of being rebuilt.
  const stride = 97;
  const edgeIndices: number[] = [];
  for (let i = 0; i < count; i++) {
    edgeIndices.push(i, (i + 1) % count);
    edgeIndices.push(i, (i + stride) % count);
  }
  const edgeCount = edgeIndices.length / 2;
  const linePositions = new Float32Array(edgeCount * 2 * 3);
  const lineGeometry = new THREE.BufferGeometry();
  lineGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(linePositions, 3),
  );
  const lineMaterial = new THREE.LineBasicMaterial({
    color: COLOR_STRUCTURE.clone(),
    transparent: true,
    opacity: 0.14,
    depthWrite: false,
  });
  const lines = new THREE.LineSegments(lineGeometry, lineMaterial);
  group.add(lines);

  let pointerX = 0;
  let pointerY = 0;
  let disposed = false;
  const mixedColor = new THREE.Color();

  function fillLinePositions(): void {
    for (let e = 0; e < edgeCount; e++) {
      const a = edgeIndices[e * 2] * 3;
      const b = edgeIndices[e * 2 + 1] * 3;
      const o = e * 6;
      linePositions[o] = positions[a];
      linePositions[o + 1] = positions[a + 1];
      linePositions[o + 2] = positions[a + 2];
      linePositions[o + 3] = positions[b];
      linePositions[o + 4] = positions[b + 1];
      linePositions[o + 5] = positions[b + 2];
    }
  }

  return {
    render(progress: number): void {
      if (disposed) return;
      samplePose(poses, progress, positions);
      fillLinePositions();
      pointGeometry.attributes.position.needsUpdate = true;
      lineGeometry.attributes.position.needsUpdate = true;

      const mix = nodeColorMix(progress);
      mixedColor.copy(COLOR_STRUCTURE).lerp(COLOR_VERDICT, mix);
      pointMaterial.color.copy(mixedColor);
      lineMaterial.color.copy(mixedColor);
      // A measured size swell at the verdict moment, nothing more.
      pointMaterial.size = 0.045 + verdictPulse(progress) * 0.02;

      group.rotation.y =
        groupRotation(progress, interactive) + pointerX * 0.08;
      group.rotation.x = pointerY * 0.05;

      renderer.render(scene, camera);
    },

    setPointer(nx: number, ny: number): void {
      if (!interactive) return;
      pointerX = nx;
      pointerY = ny;
    },

    setSize(width: number, height: number): void {
      if (disposed) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / Math.max(1, height);
      camera.updateProjectionMatrix();
    },

    dispose(): void {
      if (disposed) return;
      disposed = true;
      pointGeometry.dispose();
      lineGeometry.dispose();
      pointMaterial.dispose();
      lineMaterial.dispose();
      renderer.dispose();
    },
  };
}
