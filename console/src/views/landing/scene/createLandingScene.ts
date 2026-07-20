/**
 * three.js renderer for the landing micro-world — a thin adapter over the
 * pure rail/world model. Loaded via dynamic import from LandingCanvas so
 * three never blocks first paint.
 *
 * Scene inventory:
 *   - one Points cloud + one LineSegments lattice (the static world)
 *   - eight chamber meshes (own materials → causal state tint)
 *   - one ring mesh (verdict pulse)
 *   - one particle + trail (the submission the camera follows)
 *   - no post-processing, no shadows, no external assets
 *
 * Per-frame work is bounded: apply camera frame, advance the particle,
 * tint materials whose thresholds flipped. Nothing reallocates.
 */

import * as THREE from "three";
import { buildWorld, type WorldGeometry } from "./world";
import {
  buildKeyframes,
  chamberPassed,
  sampleCamera,
  sampleParticle,
  verdictGate,
  type CameraFrame,
  type CameraKeyframe,
  type RailVariant,
} from "./rail";

export interface LandingSceneOptions {
  canvas: HTMLCanvasElement;
  variant: RailVariant;
  /** Geometry detail scale (mobile ≈ 0.45). */
  detailScale: number;
  maxDpr: number;
  /** Pointer look-offset enabled (false under reduced motion). */
  interactive: boolean;
}

export interface LandingScene {
  /** Advance world state (particle, chamber states, pulse) — cheap, no draw. */
  setProgress(progress: number): void;
  /** Sample the rail at the scene's current progress and draw one frame. */
  render(): void;
  /** Direct frame render for reduced-motion chapter shots. */
  renderAt(progress: number): void;
  /** Normalized pointer (-1..1); applied as a small damped look offset. */
  setPointer(nx: number, ny: number): void;
  setSize(width: number, height: number): void;
  dispose(): void;
}

const COLOR_STRUCTURE = new THREE.Color("#2aa198");
const COLOR_PASSED = new THREE.Color("#859900");
const COLOR_PARTICLE = new THREE.Color("#b58900");
const COLOR_PARTICLE_ACCEPTED = new THREE.Color("#859900");

const TRAIL_LENGTH = 48;

export function createLandingScene(options: LandingSceneOptions): LandingScene {
  const { canvas, variant, detailScale, maxDpr, interactive } = options;

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: "low-power",
  });
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, maxDpr));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 400);

  const keyframes: CameraKeyframe[] = buildKeyframes(variant);
  const world: WorldGeometry = buildWorld(detailScale);

  // — static world: points + lattice —
  const pointGeometry = new THREE.BufferGeometry();
  pointGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(world.nodePositions, 3),
  );
  pointGeometry.setAttribute(
    "color",
    new THREE.BufferAttribute(world.nodeColors, 3),
  );
  const pointMaterial = new THREE.PointsMaterial({
    size: 0.09,
    sizeAttenuation: true,
    vertexColors: true,
    transparent: true,
    opacity: 0.85,
    depthWrite: false,
  });
  scene.add(new THREE.Points(pointGeometry, pointMaterial));

  const linePositions = new Float32Array(world.edgeIndices.length * 3);
  for (let i = 0; i < world.edgeIndices.length; i++) {
    const nodeOffset = world.edgeIndices[i] * 3;
    linePositions[i * 3] = world.nodePositions[nodeOffset];
    linePositions[i * 3 + 1] = world.nodePositions[nodeOffset + 1];
    linePositions[i * 3 + 2] = world.nodePositions[nodeOffset + 2];
  }
  const lineGeometry = new THREE.BufferGeometry();
  lineGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(linePositions, 3),
  );
  const lineMaterial = new THREE.LineBasicMaterial({
    color: COLOR_STRUCTURE,
    transparent: true,
    opacity: 0.1,
    depthWrite: false,
  });
  scene.add(new THREE.LineSegments(lineGeometry, lineMaterial));

  // — chambers: own materials so "passed" can tint per chamber —
  const chamberMaterials: THREE.LineBasicMaterial[] = [];
  const chamberPointMaterials: THREE.PointsMaterial[] = [];
  const chamberDisposables: { dispose(): void }[] = [];
  for (const chamber of world.chambers) {
    const cornerGeometry = new THREE.BufferGeometry();
    cornerGeometry.setAttribute(
      "position",
      new THREE.BufferAttribute(chamber.corners, 3),
    );
    const cornerMaterial = new THREE.PointsMaterial({
      color: COLOR_STRUCTURE.clone(),
      size: 0.16,
      sizeAttenuation: true,
      transparent: true,
      opacity: 0.9,
      depthWrite: false,
    });
    scene.add(new THREE.Points(cornerGeometry, cornerMaterial));

    const edgePositions = new Float32Array(chamber.edges.length * 3);
    for (let i = 0; i < chamber.edges.length; i++) {
      const cornerOffset = chamber.edges[i] * 3;
      edgePositions[i * 3] = chamber.corners[cornerOffset];
      edgePositions[i * 3 + 1] = chamber.corners[cornerOffset + 1];
      edgePositions[i * 3 + 2] = chamber.corners[cornerOffset + 2];
    }
    const edgeGeometry = new THREE.BufferGeometry();
    edgeGeometry.setAttribute(
      "position",
      new THREE.BufferAttribute(edgePositions, 3),
    );
    const edgeMaterial = new THREE.LineBasicMaterial({
      color: COLOR_STRUCTURE.clone(),
      transparent: true,
      opacity: 0.35,
      depthWrite: false,
    });
    scene.add(new THREE.LineSegments(edgeGeometry, edgeMaterial));

    chamberPointMaterials.push(cornerMaterial);
    chamberMaterials.push(edgeMaterial);
    chamberDisposables.push(cornerGeometry, cornerMaterial, edgeGeometry, edgeMaterial);
  }

  // — verdict ring —
  const ringGeometry = new THREE.BufferGeometry();
  ringGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(world.ringPositions, 3),
  );
  const ringMaterial = new THREE.PointsMaterial({
    color: COLOR_STRUCTURE.clone(),
    size: 0.14,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.9,
    depthWrite: false,
  });
  scene.add(new THREE.Points(ringGeometry, ringMaterial));

  // — the submission particle + trail —
  const particleGeometry = new THREE.SphereGeometry(0.16, 12, 8);
  const particleMaterial = new THREE.MeshBasicMaterial({
    color: COLOR_PARTICLE.clone(),
  });
  const particle = new THREE.Mesh(particleGeometry, particleMaterial);
  scene.add(particle);

  const trailPositions = new Float32Array(TRAIL_LENGTH * 3);
  const trailGeometry = new THREE.BufferGeometry();
  trailGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(trailPositions, 3),
  );
  const trailMaterial = new THREE.LineBasicMaterial({
    color: COLOR_PARTICLE.clone(),
    transparent: true,
    opacity: 0.35,
    depthWrite: false,
  });
  scene.add(new THREE.Line(trailGeometry, trailMaterial));

  // — mutable per-frame state (no Vue reactivity in here) —
  const frame: CameraFrame = { pos: [0, 0, 9], look: [0, 0, 0], fov: 42 };
  const particlePos: [number, number, number] = [0, 0, 0];
  let progress = 0;
  let pointerX = 0;
  let pointerY = 0;
  let dampedPointerX = 0;
  let dampedPointerY = 0;
  let lastPassedSignature = "";
  let disposed = false;

  const lookTarget = new THREE.Vector3();

  function updateTrail(): void {
    // Shift the trail back one slot and append the particle position.
    for (let i = TRAIL_LENGTH - 1; i > 0; i--) {
      trailPositions[i * 3] = trailPositions[(i - 1) * 3];
      trailPositions[i * 3 + 1] = trailPositions[(i - 1) * 3 + 1];
      trailPositions[i * 3 + 2] = trailPositions[(i - 1) * 3 + 2];
    }
    trailPositions[0] = particlePos[0];
    trailPositions[1] = particlePos[1];
    trailPositions[2] = particlePos[2];
    trailGeometry.attributes.position.needsUpdate = true;
  }

  function updateChambers(nextProgress: number): void {
    const passed = chamberPassed(nextProgress);
    const signature = passed.map(Boolean).join("");
    if (signature === lastPassedSignature) return;
    lastPassedSignature = signature;
    for (let i = 0; i < passed.length; i++) {
      const color = passed[i] ? COLOR_PASSED : COLOR_STRUCTURE;
      chamberMaterials[i].color.copy(color);
      chamberPointMaterials[i].color.copy(color);
    }
  }

  return {
    setProgress(nextProgress: number): void {
      if (disposed) return;
      const clamped = Math.min(1, Math.max(0, nextProgress));
      if (clamped === progress) return;
      progress = clamped;

      sampleParticle(progress, particlePos);
      particle.position.set(particlePos[0], particlePos[1], particlePos[2]);
      updateTrail();

      // The particle carries the verdict with it after the ring.
      const gate = verdictGate(progress);
      particleMaterial.color
        .copy(COLOR_PARTICLE)
        .lerp(COLOR_PARTICLE_ACCEPTED, Math.min(1, gate + (progress > 0.62 ? 0.85 : 0)));
      trailMaterial.color.copy(particleMaterial.color);

      ringMaterial.color
        .copy(COLOR_STRUCTURE)
        .lerp(COLOR_PASSED, gate);
      ringMaterial.size = 0.14 + gate * 0.1;

      updateChambers(progress);
    },

    render(): void {
      if (disposed) return;
      sampleCamera(keyframes, progress, frame);
      camera.position.set(frame.pos[0], frame.pos[1], frame.pos[2]);

      if (interactive) {
        // Small damped look offset — observation, never a course change.
        dampedPointerX += (pointerX - dampedPointerX) * 0.06;
        dampedPointerY += (pointerY - dampedPointerY) * 0.06;
      }
      lookTarget.set(
        frame.look[0] + dampedPointerX * 0.6,
        frame.look[1] - dampedPointerY * 0.4,
        frame.look[2],
      );
      camera.up.set(0, 1, 0);
      camera.lookAt(lookTarget);

      if (Math.abs(camera.fov - frame.fov) > 0.01) {
        camera.fov = frame.fov;
        camera.updateProjectionMatrix();
      }

      renderer.render(scene, camera);
    },

    renderAt(atProgress: number): void {
      // Reduced-motion chapter shot: pin progress, then draw.
      this.setProgress(atProgress);
      this.render();
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
      pointMaterial.dispose();
      lineGeometry.dispose();
      lineMaterial.dispose();
      for (const disposable of chamberDisposables) disposable.dispose();
      ringGeometry.dispose();
      ringMaterial.dispose();
      particleGeometry.dispose();
      particleMaterial.dispose();
      trailGeometry.dispose();
      trailMaterial.dispose();
      renderer.dispose();
    },
  };
}
