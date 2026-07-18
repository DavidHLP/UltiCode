<script setup lang="ts">
/**
 * Three.js scrollytelling "micro-narrative camera".
 *
 * A single, central, monochrome geometric device floats in the viewport.
 * Scroll progress (worldProgress, 0→1) drives the camera and the object as
 * one continuous cinematic shot, mapped to four acts:
 *
 *   Act 1  · 0.00–0.20  Hero      — device floats center, slow spin.
 *   Act 2  · 0.20–0.60  Zoom      — camera macro-pushes into the nucleus,
 *                                    orbiting to reveal inner wireframe detail.
 *   Act 3  · 0.60–0.80  Pivot     — device explodes into organized shards and
 *                                    collapses to pure wireframe (solid fades).
 *   Act 4  · 0.80–1.00  Outro     — camera pulls back to a distant anchor;
 *                                    the device retracts and spins quietly.
 *
 * Brutalist monochrome: every material is white or grey wireframe on a pure
 * black field (the page background paints through the alpha canvas). No warm
 * tints, no postprocessing, no external assets — under ~3k vertices so the
 * bundle stays light and 60fps holds on integrated GPUs.
 *
 * WebGL unavailable → canvas stays blank (the loading portal + page bg cover
 * it). prefers-reduced-motion → one static frame, no rAF loop.
 */

import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import type * as THREE from "three";

const props = defineProps<{ active?: boolean; worldProgress?: number }>();

// ScrollTrigger writes the page's scroll fraction here; -1 means "not yet
// driven" (e.g. the loading portal is still covering the page).
let scrubTarget = -1;
watch(
  () => props.worldProgress,
  (v) => {
    scrubTarget = typeof v === "number" ? v : -1;
  },
  { immediate: true },
);

const supportsHoverFine =
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(hover: hover) and (pointer: fine)").matches;
const isMobileTier =
  typeof navigator !== "undefined" &&
  ((navigator.hardwareConcurrency || 8) <= 4 ||
    (typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia("(max-width: 768px)").matches));

const STAR_COUNT = isMobileTier ? 320 : 700;
const SHARD_COUNT = 6;

// Camera framing is derived, not authored. Each act anchor is solved from the
// device's measured bounding sphere (center C, radius R) and the live frustum,
// so the subject is never cropped on either viewport axis; only the framing
// multipliers and the penetration profile are design parameters.
interface Waypoint {
  p: number;
  pos: readonly [number, number, number];
}

// Fit distance: the camera-Z at which a sphere of `radius` fills the smaller
// frustum axis with `margin` headroom. Max of the horizontal/vertical solves
// guarantees no crop regardless of aspect.
const fitDistance = (
  radius: number,
  fovDeg: number,
  aspect: number,
  margin: number,
): number => {
  const halfH = Math.tan(((fovDeg * Math.PI) / 180) / 2);
  const halfW = halfH * aspect;
  return (
    Math.max(
      radius / Math.max(halfH, 1e-4),
      radius / Math.max(halfW, 1e-4),
    ) * margin
  );
};

// Smoothstep easing helpers shared by the camera path and per-act morphs.
const band = (pp: number, lo: number, hi: number): number => {
  if (pp <= lo) return 0;
  if (pp >= hi) return 1;
  const x = (pp - lo) / (hi - lo);
  return x * x * (3 - 2 * x);
};

const canvasRef = ref<HTMLCanvasElement | null>(null);
const rootRef = ref<HTMLElement | null>(null);
const supportsWebGL = ref(true);

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const detectWebGL = (): boolean => {
  if (typeof window === "undefined") return false;
  try {
    const canvas = document.createElement("canvas");
    return Boolean(
      window.WebGLRenderingContext &&
        (canvas.getContext("webgl") || canvas.getContext("experimental-webgl")),
    );
  } catch {
    return false;
  }
};

let cleanup: (() => void) | null = null;

const start = async () => {
  if (typeof window === "undefined") return;
  const canvas = canvasRef.value;
  const root = rootRef.value;
  if (!canvas || !root) return;
  if (!detectWebGL()) {
    supportsWebGL.value = false;
    return;
  }

  const THREE = await import("three");

  const rect = root.getBoundingClientRect();
  const scene = new THREE.Scene();
  // Black fog so far geometry recedes into the page background — sells the
  // "infinite black field" without a second draw pass.
  scene.fog = new THREE.FogExp2(0x000000, 0.055);

  const CAMERA_FOV = 50;

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: "high-performance",
  });
  renderer.setPixelRatio(
    Math.min(window.devicePixelRatio || 1, isMobileTier ? 1.5 : 1.75),
  );
  renderer.setSize(rect.width, rect.height, false);
  renderer.setClearColor(0x000000, 0);

  // ---- Central device: a nested wireframe/glass structure ----------------
  // Cage (outer icosahedron), mid octahedron, and a glass nucleus with a
  // wireframe overlay. The nucleus is the "detail" the camera pushes into.
  const device = new THREE.Group();
  scene.add(device);

  const cageGeo = new THREE.WireframeGeometry(
    new THREE.IcosahedronGeometry(1.55, 1),
  );
  const cage = new THREE.LineSegments(
    cageGeo,
    new THREE.LineBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.85,
    }),
  );
  device.add(cage);

  const midGeo = new THREE.WireframeGeometry(
    new THREE.OctahedronGeometry(1.02, 0),
  );
  const mid = new THREE.LineSegments(
    midGeo,
    new THREE.LineBasicMaterial({
      color: 0xd2d2d2,
      transparent: true,
      opacity: 0.6,
    }),
  );
  device.add(mid);

  const nucleusGeo = new THREE.IcosahedronGeometry(0.5, 1);
  const nucleusGlass = new THREE.Mesh(
    nucleusGeo,
    new THREE.MeshBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.08,
      depthWrite: false,
    }),
  );
  device.add(nucleusGlass);
  const nucleusWire = new THREE.LineSegments(
    new THREE.WireframeGeometry(nucleusGeo),
    new THREE.LineBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.55,
    }),
  );
  device.add(nucleusWire);

  // Thin geometric separator ring — the editorial hairline in 3D.
  const ringGeo = new THREE.BufferGeometry();
  const RING_RADIUS = 2.25;
  const RING_SEGMENTS = 96;
  const ringPts = new Float32Array((RING_SEGMENTS + 1) * 3);
  for (let i = 0; i <= RING_SEGMENTS; i++) {
    const a = (i / RING_SEGMENTS) * Math.PI * 2;
    ringPts[i * 3] = Math.cos(a) * RING_RADIUS;
    ringPts[i * 3 + 1] = 0;
    ringPts[i * 3 + 2] = Math.sin(a) * RING_RADIUS;
  }
  ringGeo.setAttribute("position", new THREE.BufferAttribute(ringPts, 3));
  const ring = new THREE.LineLoop(
    ringGeo,
    new THREE.LineBasicMaterial({
      color: 0x2a2a2a,
      transparent: true,
      opacity: 0.3,
    }),
  );
  ring.rotation.x = Math.PI / 2;
  device.add(ring);

  // ---- Orbiting shards — explode outward during the pivot act ------------
  // Distributed on a sphere via the golden-angle method so they read as
  // organized, not clumped. Each shard keeps its unit direction; the tick
  // scales its radius by the explode band.
  const shards: THREE.LineSegments[] = [];
  const shardDirs: THREE.Vector3[] = [];
  const shardBaseRadius = 1.35;
  for (let i = 0; i < SHARD_COUNT; i++) {
    const y = 1 - (i / (SHARD_COUNT - 1)) * 2;
    const radius = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = i * 2.399963;
    const dir = new THREE.Vector3(
      Math.cos(theta) * radius,
      y * 0.6,
      Math.sin(theta) * radius,
    ).normalize();
    shardDirs.push(dir);
    const shardGeo = new THREE.WireframeGeometry(
      new THREE.TetrahedronGeometry(0.17, 0),
    );
    const shard = new THREE.LineSegments(
      shardGeo,
      new THREE.LineBasicMaterial({
        color: 0xe8e8e8,
        transparent: true,
        opacity: 0.7,
      }),
    );
    shard.position.copy(dir).multiplyScalar(shardBaseRadius);
    device.add(shard);
    shards.push(shard);
  }

  // ---- Sparse starfield — depth for the outro's distant perspective ------
  // Deliberately sparse (negative space); white points on the black field.
  const starPositions = new Float32Array(STAR_COUNT * 3);
  for (let i = 0; i < STAR_COUNT; i++) {
    // Shell between r=10 and r=22, biased away from the device center.
    const r = 10 + Math.random() * 12;
    const u = Math.random() * 2 - 1;
    const phi = Math.random() * Math.PI * 2;
    const s = Math.sqrt(Math.max(0, 1 - u * u));
    starPositions[i * 3] = Math.cos(phi) * s * r;
    starPositions[i * 3 + 1] = Math.sin(phi) * s * r;
    starPositions[i * 3 + 2] = u * r;
  }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute("position", new THREE.BufferAttribute(starPositions, 3));
  const starMat = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 0.05,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.5,
    depthWrite: false,
  });
  const stars = new THREE.Points(starGeo, starMat);
  scene.add(stars);

  // ---- Geometry-derived framing origin + subject bounds ----------------
  // Measure the device (cage / mid / nucleus / ring / shards) in its rest
  // pose. Stars are parented to the scene, not the device, so the backdrop
  // never inflates the subject sphere. C is the framing target; R is the
  // baseline extent — every camera anchor below is a function of the two,
  // recomputed against the live frustum so nothing crops and nothing is
  // hardcoded. Animated overshoots (the Act III explode) are baked in as
  // documented multipliers rather than measured live, which would jitter.
  device.updateWorldMatrix(true, true);
  const subjectBounds = new THREE.Box3().setFromObject(device);
  const C = subjectBounds.isEmpty()
    ? new THREE.Vector3(0, 0, 0)
    : subjectBounds.getCenter(new THREE.Vector3());
  const R = (() => {
    // True enclosing-sphere radius = max rest-pose vertex distance from C.
    // Box3.getBoundingSphere would use the box diagonal and over-frame; the
    // device is sparse wireframe, so measure the real extent instead.
    let maxR = 0;
    const v = new THREE.Vector3();
    type Geometrized = THREE.Object3D & { geometry?: THREE.BufferGeometry };
    device.traverse((obj) => {
      const attr = (obj as Geometrized).geometry?.getAttribute("position");
      if (!attr) return;
      for (let i = 0; i < attr.count; i++) {
        v.fromArray(attr.array, i * attr.itemSize);
        obj.localToWorld(v);
        const d = v.distanceTo(C);
        if (d > maxR) maxR = d;
      }
    });
    return Math.max(maxR, 1e-3);
  })();

  // ---- Camera + derived waypoint path ----------------------------------
  const camera = new THREE.PerspectiveCamera(
    CAMERA_FOV,
    rect.width / Math.max(rect.height, 1),
    0.1,
    100,
  );

  // Offset from the framing origin C. Multipliers of R are the framing design
  // parameters; the resulting distance is always solved from R + the frustum.
  const anchor = (
    x: number,
    y: number,
    z: number,
  ): readonly [number, number, number] => [C.x + x, C.y + y, C.z + z];
  // Penetration profile: orbit (rho, theta, yEye) in units of R so the camera
  // slips through the cage shell (R*0.69) and onto the nucleus (R*0.22).
  const penetrate = (rho: number, theta: number, yEye: number) =>
    anchor(Math.sin(theta) * rho, yEye, Math.cos(theta) * rho);

  const computeWaypoints = (): Waypoint[] => {
    const fit = (radius: number, margin: number) =>
      fitDistance(radius, camera.fov, camera.aspect, margin);
    return [
      // Act I — the totality: whole silhouette, loose headroom.
      { p: 0.0, pos: anchor(0, R * 0.1, fit(R, 1.22)) },
      { p: 0.2, pos: anchor(0, R * 0.08, fit(R, 1.18)) },
      // Act II — penetration: orbit the outer shell, then dive inside it.
      { p: 0.32, pos: penetrate(R * 0.95, 0.8, R * 0.18) },
      { p: 0.46, pos: penetrate(R * 0.42, -0.7, R * 0.12) },
      { p: 0.6, pos: anchor(0, R * 0.3, fit(R, 1.25)) },
      // Act III — the split: rise to witness the explode. Frame the true
      // peak extent — shards reach shardBaseRadius + explode delta in world
      // units — rather than an R-multiplier, so the explode never crops even
      // if the rest geometry is later resized and R shrinks.
      {
        p: 0.72,
        pos: anchor(0, R * 0.55, fit(Math.max(R, shardBaseRadius + 1.7), 1.28)),
      },
      { p: 0.8, pos: anchor(R * 0.15, R * 0.2, fit(R * 1.2, 1.5)) },
      // Act IV — the horizon: distant, low, profile.
      { p: 1.0, pos: anchor(R * 1.1, R * 0.06, fit(R * 1.05, 2.2)) },
    ];
  };

  let waypoints = computeWaypoints();
  const startPos = waypoints[0].pos;

  // Persistent scratch vectors — allocated once and captured by the tick
  // closure so the camera path creates no garbage per frame.
  const curPos = new THREE.Vector3(startPos[0], startPos[1], startPos[2]);
  const tmpPos = new THREE.Vector3();
  camera.position.set(startPos[0], startPos[1], startPos[2]);
  camera.lookAt(C.x, C.y, C.z);

  // ---- Interaction + sizing ---------------------------------------------
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const onPointerMove = (event: PointerEvent) => {
    const r = root.getBoundingClientRect();
    pointer.tx = (event.clientX - r.left) / r.width - 0.5;
    pointer.ty = (event.clientY - r.top) / r.height - 0.5;
  };

  const onResize = () => {
    const r = root.getBoundingClientRect();
    camera.aspect = r.width / Math.max(r.height, 1);
    camera.updateProjectionMatrix();
    renderer.setSize(r.width, r.height, false);
    // Re-derive the path: fit distance depends on the new aspect ratio.
    waypoints = computeWaypoints();
  };

  const ro = new ResizeObserver(onResize);
  ro.observe(root);
  if (supportsHoverFine) {
    window.addEventListener("pointermove", onPointerMove, { passive: true });
  }
  window.addEventListener("resize", onResize);

  const reduced = prefersReducedMotion();
  const startTime = performance.now();
  let rafId = 0;

  const tick = (now: number) => {
    const elapsed = (now - startTime) / 1000;
    const p = scrubTarget;

    // ---- Camera path ----------------------------------------------------
    if (p >= 0) {
      const wp = waypoints;
      let i = 0;
      while (i < wp.length - 2 && p > wp[i + 1].p) i++;
      const a = wp[i];
      const b = wp[i + 1];
      const span = Math.max(1e-4, b.p - a.p);
      const t = Math.min(1, Math.max(0, (p - a.p) / span));
      const ts = t * t * (3 - 2 * t);
      tmpPos.set(
        a.pos[0] + (b.pos[0] - a.pos[0]) * ts,
        a.pos[1] + (b.pos[1] - a.pos[1]) * ts,
        a.pos[2] + (b.pos[2] - a.pos[2]) * ts,
      );
      curPos.lerp(tmpPos, 0.09);
    } else {
      const h = waypoints[0].pos;
      curPos.lerp(tmpPos.set(h[0], h[1], h[2]), 0.04);
    }
    camera.position.set(
      curPos.x + pointer.x * 0.6,
      curPos.y - pointer.y * 0.3,
      curPos.z,
    );
    camera.lookAt(C.x, C.y, C.z);

    if (!reduced) {
      pointer.x += (pointer.tx - pointer.x) * 0.04;
      pointer.y += (pointer.ty - pointer.y) * 0.04;

      // Idle spin — gentler in hero/outro, faster while zoomed in so the
      // inner geometry turns over during the macro beat.
      const zoom = p >= 0 ? band(p, 0.2, 0.55) * (1 - band(p, 0.6, 0.72)) : 0;
      cage.rotation.y = elapsed * (0.12 + zoom * 0.3);
      cage.rotation.x = Math.sin(elapsed * 0.1) * 0.12;
      mid.rotation.y = -elapsed * (0.2 + zoom * 0.5);
      mid.rotation.z = elapsed * 0.08;
      nucleusWire.rotation.x = elapsed * 0.4;
      nucleusWire.rotation.y = elapsed * 0.55;
      nucleusGlass.rotation.copy(nucleusWire.rotation);
      ring.rotation.z = elapsed * 0.05;
      stars.rotation.y = elapsed * 0.01;
    }

    // ---- Per-act morphs (compose on top of the idle motion) -------------
    if (p >= 0) {
      // Act 2 — macro reveal: bring the glass nucleus up so the inner
      // wireframe reads as the detail we pushed into.
      const Breveal = band(p, 0.22, 0.34) * (1 - band(p, 0.55, 0.62));
      (nucleusGlass.material as THREE.MeshBasicMaterial).opacity =
        0.08 + Breveal * 0.14;
      (nucleusWire.material as THREE.LineBasicMaterial).opacity =
        0.55 + Breveal * 0.3;

      // Act 3 — pivot: explode shards outward + expand cage, and collapse
      // the solid nucleus to leave pure wireframe. Triangle envelope so the
      // device reassembles for the outro.
      const explode = band(p, 0.6, 0.72) * (1 - band(p, 0.74, 0.86));
      for (let i = 0; i < SHARD_COUNT; i++) {
        const d = shardDirs[i];
        const s = shards[i];
        const radius = shardBaseRadius + explode * 1.7;
        s.position.set(d.x * radius, d.y * radius, d.z * radius);
        s.rotation.x = explode * Math.PI * (i + 1) * 0.4;
        s.rotation.y = explode * Math.PI * (i + 1) * 0.3;
        (s.material as THREE.LineBasicMaterial).opacity = 0.5 + explode * 0.4;
      }
      const cageScale = 1 + explode * 0.5;
      cage.scale.setScalar(cageScale);
      // Solid fades as the structure goes to wireframe.
      (nucleusGlass.material as THREE.MeshBasicMaterial).opacity *=
        1 - explode * 0.9;
      (ring.material as THREE.LineBasicMaterial).opacity = 0.3 + explode * 0.4;

      // Act 4 — outro: dim the whole device into a distant background anchor.
      const outro = band(p, 0.82, 0.95);
      device.scale.setScalar(1 - outro * 0.18);
      starMat.opacity = 0.5 + outro * 0.25;
    }

    renderer.render(scene, camera);
    if (!reduced) rafId = requestAnimationFrame(tick);
  };

  if (reduced) {
    renderer.render(scene, camera);
  } else {
    rafId = requestAnimationFrame(tick);
  }

  cleanup = () => {
    if (rafId) cancelAnimationFrame(rafId);
    ro.disconnect();
    if (supportsHoverFine) {
      window.removeEventListener("pointermove", onPointerMove);
    }
    window.removeEventListener("resize", onResize);
    const disposeMat = (m: THREE.Material | THREE.Material[]) =>
      Array.isArray(m) ? m.forEach((x) => x.dispose()) : m.dispose();
    cageGeo.dispose();
    disposeMat(cage.material);
    midGeo.dispose();
    disposeMat(mid.material);
    nucleusGeo.dispose();
    disposeMat(nucleusGlass.material);
    disposeMat(nucleusWire.material);
    ringGeo.dispose();
    disposeMat(ring.material);
    for (const s of shards) {
      s.geometry.dispose();
      disposeMat(s.material);
    }
    starGeo.dispose();
    starMat.dispose();
    renderer.dispose();
  };
};

onMounted(() => {
  void start();
});

onBeforeUnmount(() => {
  if (cleanup) cleanup();
});
</script>

<template>
  <div ref="rootRef" class="luca-hero-scene" aria-hidden="true">
    <canvas
      v-if="supportsWebGL"
      ref="canvasRef"
      class="luca-hero-scene-canvas"
    ></canvas>
    <div v-else class="luca-hero-scene-fallback"></div>
  </div>
</template>
