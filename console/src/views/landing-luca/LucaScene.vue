<script setup lang="ts">
/**
 * Three.js scrollytelling device — the 9-beat "copy × geometry" cut.
 *
 * One central wireframe polyhedron + a constant white point at the world
 * origin. The stage bus (useLucaStage) publishes the current beat's state and
 * its local 0→1 scrub progress; this scene plays the matching choreography so
 * the 3D and the typography move as a single system. Every state visibly
 * mutates the device — no two consecutive beats share a state.
 *
 * Brutalist monochrome: white/grey wireframe on a pure black field (the page
 * background paints through the alpha canvas). No postprocessing — bloom/glow
 * is faked with additive-blended lines and radial-gradient sprites so the
 * <40-draw-call / <30k-triangle budget holds on integrated GPUs.
 *
 * All nine state-specific objects (inner core + halo, snap grid plane, axis
 * line, split halves, portal plane, four corner sub-icosahedra, tick ring +
 * dial + progress arc, the breathing anchor, the broken offsets + magnetic
 * pull, and the explode particle field) are built ONCE at startup and then
 * toggled via eased morph channels — nothing is created or disposed per beat
 * except the particle Points, which are created lazily on first explode.
 *
 * Camera framing is solved from the device's measured bounding sphere and the
 * live frustum (fitDistance) — never hardcoded coordinates.
 *
 * Section-09 commands:
 *   - reverse → a ~2.5s gsap tween (power2.inOut) eases the device back to a
 *     pristine symmetric origin state and sits there (the "harmony" path).
 *   - explode → the polyhedron bursts into an additive THREE.Points cloud
 *     expanding outward over ~700ms while the wireframe hides. The DOM beat
 *     owns the router push on the same command; the scene only plays the burst.
 *
 * WebGL unavailable → canvas stays blank (portal + page bg cover it).
 * prefers-reduced-motion / narrow viewport → one static frame, no rAF loop.
 *
 * The canvas is `position: fixed; pointer-events: none`; the magnetic pull in
 * the broken beat reads the pointer from a window 'pointermove' listener.
 */

import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import gsap from "gsap";
import type * as THREE from "three";
import {
  useLucaStageConsumer,
  type LucaCommand,
  type LucaState,
} from "@/composables/landing/useLucaStage";
import {
  PRISTINE,
  lerp,
  bakeGridSnap,
  bakeBrokenOffsets,
  bakeHalves,
  particleFieldPositions,
  mulberry32,
} from "./luca/polyhedron";
import type { MorphTargets } from "./luca/polyhedron";

const props = defineProps<{ active?: boolean }>();

const stage = useLucaStageConsumer();

// Reactivity bridge: the rAF tick reads plain locals (no per-frame unwrap),
// kept fresh by watchers on the stage refs.
let curState: LucaState = "squashed";
let curProgress = 0;
let curFragment: string | null = null;
watch(() => stage.state.value, (v) => (curState = v), { immediate: true });
watch(() => stage.progress.value, (v) => (curProgress = v), { immediate: true });
watch(
  () => stage.activeFragment.value,
  (v) => (curFragment = v),
  { immediate: true },
);

// Command + state-enter dispatchers are populated by start() once THREE/gsap
// are alive; until then (jsdom, SSR) the watches below no-op.
let commandHandler: ((cmd: LucaCommand) => void) | null = null;
let stateEnterHandler: ((s: LucaState) => void) | null = null;
watch(
  () => stage.command.value,
  (cmd) => {
    if (cmd && commandHandler) commandHandler(cmd);
  },
);
watch(
  () => stage.state.value,
  (s) => {
    if (stateEnterHandler) stateEnterHandler(s);
  },
);

const isMobileTier =
  typeof navigator !== "undefined" &&
  ((navigator.hardwareConcurrency || 8) <= 4 ||
    (typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia("(max-width: 768px)").matches));

const STAR_COUNT = isMobileTier ? 320 : 700;
const PARTICLE_COUNT = isMobileTier ? 1200 : 2000;

// Fit distance: camera-Z at which a sphere of `radius` fills the smaller
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
    Math.max(radius / Math.max(halfH, 1e-4), radius / Math.max(halfW, 1e-4)) *
    margin
  );
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

// Pillar (beat 06) key → canvas-corner mapping, in reading order so the
// quarteted anatomy reads top-to-bottom, left-to-right.
const PILLAR_KEYS = ["editor", "judge", "contest", "community"] as const;
const PILLAR_NDC: ReadonlyArray<readonly [number, number]> = [
  [-0.92, 0.8], // editor  → top-left
  [0.92, 0.8], // judge   → top-right
  [-0.92, -0.8], // contest → bottom-left
  [0.92, -0.8], // community → bottom-right
];

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

  // ---- Helper: additive radial-gradient sprite (fake bloom, no postfx) ----
  const makeRadialSprite = (
    innerAlpha: number,
    scale: number,
  ): THREE.Sprite => {
    const c = document.createElement("canvas");
    c.width = 128;
    c.height = 128;
    const ctx = c.getContext("2d");
    if (ctx) {
      const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
      g.addColorStop(0, `rgba(255,255,255,${innerAlpha})`);
      g.addColorStop(0.4, "rgba(255,255,255,0.25)");
      g.addColorStop(1, "rgba(255,255,255,0)");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, 128, 128);
    }
    const tex = new THREE.CanvasTexture(c);
    const mat = new THREE.SpriteMaterial({
      map: tex,
      blending: THREE.AdditiveBlending,
      transparent: true,
      opacity: 0,
      depthWrite: false,
    });
    const sp = new THREE.Sprite(mat);
    sp.scale.setScalar(scale);
    return sp;
  };

  // ---- Central device group --------------------------------------------
  const device = new THREE.Group();
  scene.add(device);

  // Main wireframe polyhedron. Its geometry position attribute is rewritten
  // every frame to blend base ↔ grid-snap, apply seeded broken offsets, the
  // squash micro-jitter, and the broken-beat magnetic pull.
  const polyGeo = new THREE.IcosahedronGeometry(1.4, 1);
  const polyWireGeo = new THREE.WireframeGeometry(polyGeo);
  const wireMat = new THREE.LineBasicMaterial({
    color: new THREE.Color(0xffffff),
    transparent: true,
    opacity: 0.85,
  });
  const polyhedron = new THREE.LineSegments(polyWireGeo, wireMat);
  device.add(polyhedron);

  // Baked wireframe states (pure math, framework-free).
  const wireBase = new Float32Array(
    (polyhedron.geometry.getAttribute("position").array as Float32Array),
  );
  const wireSnap = bakeGridSnap(wireBase, 0.125);
  let wireBrokenOffsets = bakeBrokenOffsets(wireBase, 1337, 5, 0.15);
  const halves = bakeHalves(wireBase);
  const wireLive = new Float32Array(wireBase.length);

  // Split halves (opened 05) — built once, slid apart by `openBlend`.
  const halfMat = new THREE.LineBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0.9,
  });
  const leftHalfGeo = new THREE.BufferGeometry();
  leftHalfGeo.setAttribute("position", new THREE.BufferAttribute(halves.left, 3));
  const leftHalf = new THREE.LineSegments(leftHalfGeo, halfMat);
  leftHalf.visible = false;
  device.add(leftHalf);
  const rightHalfGeo = new THREE.BufferGeometry();
  rightHalfGeo.setAttribute(
    "position",
    new THREE.BufferAttribute(halves.right, 3),
  );
  const rightHalf = new THREE.LineSegments(rightHalfGeo, halfMat);
  rightHalf.visible = false;
  device.add(rightHalf);

  // The constant anchor: a tiny white point at the world origin. Hides only in
  // the cracked beat (the core sphere takes its place).
  const anchorGeo = new THREE.IcosahedronGeometry(0.05, 0);
  const anchorMat = new THREE.MeshBasicMaterial({ color: 0xffffff });
  const anchor = new THREE.Mesh(anchorGeo, anchorMat);
  scene.add(anchor);

  // ---- Cracked (02): inner core sphere + halo sprite -------------------
  const coreGeo = new THREE.IcosahedronGeometry(0.25, 1);
  const coreMat = new THREE.MeshBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0,
  });
  const coreSphere = new THREE.Mesh(coreGeo, coreMat);
  scene.add(coreSphere);
  const coreHalo = makeRadialSprite(0.9, 1.6);
  scene.add(coreHalo);

  // ---- Snapped (03): background grid plane at z=-3 ---------------------
  const bgGridPts: number[] = [];
  const GRID_EXT = 6;
  const GRID_STEP = 0.5;
  for (let x = -GRID_EXT; x <= GRID_EXT; x += GRID_STEP) {
    bgGridPts.push(x, -GRID_EXT, -3, x, GRID_EXT, -3);
  }
  for (let y = -GRID_EXT; y <= GRID_EXT; y += GRID_STEP) {
    bgGridPts.push(-GRID_EXT, y, -3, GRID_EXT, y, -3);
  }
  const bgGridGeo = new THREE.BufferGeometry();
  bgGridGeo.setAttribute(
    "position",
    new THREE.BufferAttribute(Float32Array.from(bgGridPts), 3),
  );
  const bgGridMat = new THREE.LineBasicMaterial({
    color: 0x3a3a3a,
    transparent: true,
    opacity: 0,
  });
  const bgGrid = new THREE.LineSegments(bgGridGeo, bgGridMat);
  scene.add(bgGrid);

  // ---- Axed (04): glowing axis line + central glow ---------------------
  const axisPts = Float32Array.of(-8, 0, 0, 8, 0, 0);
  const axisGeo = new THREE.BufferGeometry();
  axisGeo.setAttribute("position", new THREE.BufferAttribute(axisPts, 3));
  const axisMat = new THREE.LineBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const axisLine = new THREE.Line(axisGeo, axisMat);
  scene.add(axisLine);
  const axisGlow = makeRadialSprite(0.8, 1.2);
  scene.add(axisGlow);

  // ---- Opened (05): portal plane behind the gap ------------------------
  const portalTexCanvas = document.createElement("canvas");
  portalTexCanvas.width = portalTexCanvas.height = 128;
  const pctx = portalTexCanvas.getContext("2d");
  if (pctx) {
    const g = pctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, "rgba(255,255,255,0.55)");
    g.addColorStop(0.5, "rgba(255,255,255,0.12)");
    g.addColorStop(1, "rgba(255,255,255,0)");
    pctx.fillStyle = g;
    pctx.fillRect(0, 0, 128, 128);
  }
  const portalTex = new THREE.CanvasTexture(portalTexCanvas);
  const portalMat = new THREE.MeshBasicMaterial({
    map: portalTex,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const portalGeo = new THREE.PlaneGeometry(3.2, 3.2);
  const portalPlane = new THREE.Mesh(portalGeo, portalMat);
  portalPlane.position.set(0, 0, -0.6);
  scene.add(portalPlane);

  // ---- Quarteted (06): four corner sub-icosahedra ----------------------
  const cornerGeo = new THREE.IcosahedronGeometry(0.3, 0);
  const corners: THREE.Mesh[] = [];
  const cornerMats: THREE.MeshBasicMaterial[] = [];
  for (let i = 0; i < 4; i++) {
    const m = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0,
      wireframe: true,
    });
    const mesh = new THREE.Mesh(cornerGeo, m);
    mesh.visible = false;
    corners.push(mesh);
    cornerMats.push(m);
    scene.add(mesh);
  }

  // ---- Timed (07): dial ring + 12 ticks (vertex colors) + progress arc -
  const RING_R = 2.0;
  const TICK_IN = 1.9;
  const TICK_OUT = 2.1;
  const TICK_COUNT = 12;
  const tickPts = new Float32Array(TICK_COUNT * 2 * 3);
  const tickCols = new Float32Array(TICK_COUNT * 2 * 3);
  for (let i = 0; i < TICK_COUNT; i++) {
    const a = (i / TICK_COUNT) * Math.PI * 2;
    const cx = Math.cos(a);
    const sy = Math.sin(a);
    const o = i * 6;
    tickPts[o] = cx * TICK_IN;
    tickPts[o + 1] = sy * TICK_IN;
    tickPts[o + 2] = 0;
    tickPts[o + 3] = cx * TICK_OUT;
    tickPts[o + 4] = sy * TICK_OUT;
    tickPts[o + 5] = 0;
  }
  const tickGeo = new THREE.BufferGeometry();
  tickGeo.setAttribute("position", new THREE.BufferAttribute(tickPts, 3));
  tickGeo.setAttribute("color", new THREE.BufferAttribute(tickCols, 3));
  const tickMat = new THREE.LineBasicMaterial({
    vertexColors: true,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const tickMarks = new THREE.LineSegments(tickGeo, tickMat);
  scene.add(tickMarks);

  // Faint full-circle dial behind the ticks.
  const DIAL_SEG = 128;
  const dialPts = new Float32Array((DIAL_SEG + 1) * 3);
  for (let i = 0; i <= DIAL_SEG; i++) {
    const a = (i / DIAL_SEG) * Math.PI * 2;
    dialPts[i * 3] = Math.cos(a) * RING_R;
    dialPts[i * 3 + 1] = Math.sin(a) * RING_R;
    dialPts[i * 3 + 2] = 0;
  }
  const dialGeo = new THREE.BufferGeometry();
  dialGeo.setAttribute("position", new THREE.BufferAttribute(dialPts, 3));
  const dialMat = new THREE.LineBasicMaterial({
    color: 0x2a2a2a,
    transparent: true,
    opacity: 0,
  });
  const dial = new THREE.LineLoop(dialGeo, dialMat);
  scene.add(dial);

  // Progress arc: full 64-segment ring, drawRange.count is set per frame from
  // the lit fraction so it traces from the 2021 tick to the current tick.
  const ARC_SEG = 64;
  const arcPts = new Float32Array((ARC_SEG + 1) * 3);
  for (let i = 0; i <= ARC_SEG; i++) {
    const a = (i / ARC_SEG) * Math.PI * 2;
    arcPts[i * 3] = Math.cos(a) * RING_R;
    arcPts[i * 3 + 1] = Math.sin(a) * RING_R;
    arcPts[i * 3 + 2] = 0;
  }
  const arcGeo = new THREE.BufferGeometry();
  arcGeo.setAttribute("position", new THREE.BufferAttribute(arcPts, 3));
  arcGeo.setDrawRange(0, 0);
  const arcMat = new THREE.LineBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  const arc = new THREE.Line(arcGeo, arcMat);
  scene.add(arc);

  // ---- Starfield (depth, faded to 0 in still) --------------------------
  const starPositions = new Float32Array(STAR_COUNT * 3);
  for (let i = 0; i < STAR_COUNT; i++) {
    const r = 10 + Math.random() * 12;
    const u = Math.random() * 2 - 1;
    const phi = Math.random() * Math.PI * 2;
    const s = Math.sqrt(Math.max(0, 1 - u * u));
    starPositions[i * 3] = Math.cos(phi) * s * r;
    starPositions[i * 3 + 1] = Math.sin(phi) * s * r;
    starPositions[i * 3 + 2] = u * r;
  }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute(
    "position",
    new THREE.BufferAttribute(starPositions, 3),
  );
  const starMat = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 0.05,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.45,
    depthWrite: false,
  });
  const stars = new THREE.Points(starGeo, starMat);
  scene.add(stars);

  // ---- Explode particle field (created lazily on first explode) --------
  let particles: THREE.Points | null = null;
  let particleLive: Float32Array | null = null;
  const particleField = particleFieldPositions(
    PARTICLE_COUNT,
    1.4,
    mulberry32(99),
  );
  const ensureParticles = (): THREE.Points => {
    if (particles) return particles;
    particleLive = new Float32Array(PARTICLE_COUNT * 3); // start at origin
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(particleLive, 3));
    const mat = new THREE.PointsMaterial({
      color: 0xffffff,
      size: 0.06,
      sizeAttenuation: true,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    });
    particles = new THREE.Points(geo, mat);
    particles.visible = false;
    scene.add(particles);
    return particles;
  };

  // ---- Geometry-derived framing origin + subject bounds ----------------
  device.updateWorldMatrix(true, true);
  const subjectBounds = new THREE.Box3().setFromObject(device);
  const C = subjectBounds.isEmpty()
    ? new THREE.Vector3(0, 0, 0)
    : subjectBounds.getCenter(new THREE.Vector3());
  const R = (() => {
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

  // ---- Camera ------------------------------------------------------------
  const camera = new THREE.PerspectiveCamera(
    CAMERA_FOV,
    rect.width / Math.max(rect.height, 1),
    0.1,
    100,
  );
  let eyeZ = fitDistance(R, camera.fov, camera.aspect, 1.35);
  camera.position.set(C.x, C.y + R * 0.08, C.z + eyeZ);
  camera.lookAt(C.x, C.y, C.z);

  // ---- Eased morph state -------------------------------------------------
  // One live packet; each frame eases toward `targets()` then drives the THREE
  // objects. Channels that need per-state geometry (snap/broken/jitter/magnetic)
  // are applied in the per-vertex wireframe step.
  const ease = 0.12;
  const morph: MorphTargets = { ...PRISTINE };

  // Reverse-harmony state (beat 09). When harmonyMode is on, targets() returns
  // pristine for the broken beat; reverseT (0→1 over 2.5s via gsap) blends the
  // transition so the return reads as a deliberate easing, not a flip.
  let harmonyMode = false;
  let reverseActive = false;
  let reverseT = 0;
  const reverseProxy = { t: 0 };
  let reverseTween: gsap.core.Animation | null = null;

  // Explode state (beat 09). Manual ramp in the tick loop over ~700ms.
  let explodeActive = false;
  let explodeStart = 0;
  let explodeT = 0;

  // Snapped click-pulse multiplier (gsap, 200ms), applied on top of device
  // scale so it never fights the per-frame ease.
  const clickProxy = { v: 1 };
  let clickTween: gsap.core.Animation | null = null;

  // Compute the per-state morph TARGETS for the current beat. Every beat
  // overrides a handful of PRISTINE fields; the rest stay neutral. This switch
  // is the literal 9-state choreography table.
  const targets = (
    state: LucaState,
    p: number,
    fragment: string | null,
  ): MorphTargets => {
    const t: MorphTargets = { ...PRISTINE };
    switch (state) {
      case "squashed":
        // Device squash + full-opacity wireframe; jitter channel drives the
        // per-vertex high-frequency micro-jitter.
        t.scaleX = 1.15;
        t.scaleY = 1.15;
        t.scaleZ = 0.6;
        t.wireOpacity = 1;
        t.jitter = 1;
        break;
      case "cracked":
        // Outer wireframe fades to grey; the core sphere + halo replace the
        // origin anchor; a slow counter-rotation reads as "frictionless core".
        t.wireOpacity = 0.18;
        t.wireGrey = 1;
        t.anchorVis = 0;
        t.coreVis = 1;
        t.coreLight = 1;
        t.idleSpin = 0.06;
        break;
      case "snapped":
        // Wireframe eases onto a 0.125 grid; a faint background grid plane
        // appears at z=-3. (The 200ms click-pulse fires on enter.)
        t.snapBlend = 1;
        t.bgGrid = 0.5;
        t.wireOpacity = 0.9;
        t.idleSpin = 0.25;
        break;
      case "axed":
        // A glowing axis line through the origin; the polyhedron orbits it
        // while the camera holds.
        t.axisVis = 1;
        t.wireOpacity = 0.7;
        t.orbitRate = 0.5;
        t.idleSpin = 0;
        break;
      case "opened":
        // Polyhedron hides; the two baked halves slide apart (∓0.8); a soft
        // portal plane glows behind the gap.
        t.openBlend = 1;
        t.portalVis = 1;
        t.wireOpacity = 0;
        t.idleSpin = 0;
        break;
      case "quarteted":
        // Main polyhedron hides; four sub-icosahedra fly to the canvas corners.
        // The active pillar returns to center + flares (handled per-frame).
        t.quartet = 1;
        t.wireOpacity = 0;
        t.idleSpin = 0;
        break;
      case "timed":
        // A dial + 12 ticks light up sequentially as the beat's local scrub
        // goes 0→1; a progress arc traces from 2021 to the current tick.
        t.tickRing = 1;
        t.dialOpacity = 0.45;
        t.tickLit = p;
        t.wireOpacity = 0.22;
        t.idleSpin = 0.1;
        break;
      case "still":
        // Everything stops and fades; only the origin point remains, breathing.
        t.starOpacity = 0;
        t.idleSpin = 0;
        t.wireOpacity = 0;
        t.anchorVis = 1;
        t.brokenBlend = 0;
        break;
      case "broken":
        if (harmonyMode) {
          // Ease from the broken shape back to pristine over the reverse tween.
          const b = reverseActive ? reverseT : 1;
          t.brokenBlend = lerp(1, 0, b);
          t.rotX = lerp(0.2, 0, b);
          t.rotZ = lerp(-0.15, 0, b);
          t.magnetic = lerp(1, 0, b);
          t.wireOpacity = 0.85;
        } else {
          // Asymmetric lean + seeded vertex offsets + magnetic pointer pull.
          t.brokenBlend = 1;
          t.rotX = 0.2;
          t.rotZ = -0.15;
          t.magnetic = 1;
          t.wireOpacity = 0.85;
        }
        break;
    }
    void fragment;
    return t;
  };

  // ---- State-enter effects ----------------------------------------------
  stateEnterHandler = (s: LucaState) => {
    if (s === "snapped") {
      // 200ms scale click-pulse 1 → 0.97 → 1 (power3.out).
      if (clickTween) clickTween.kill();
      clickProxy.v = 1;
      clickTween = gsap
        .timeline()
        .to(clickProxy, {
          v: 0.97,
          duration: 0.1,
          ease: "power3.out",
        })
        .to(clickProxy, {
          v: 1,
          duration: 0.1,
          ease: "power3.out",
        });
    }
    // Re-bake the seeded broken offsets on every entry into the broken beat
    // so each visit paints a different asymmetry (timestamp seed → distinct
    // per visit).
    if (s === "broken") {
      wireBrokenOffsets = bakeBrokenOffsets(
        wireBase,
        (performance.now() | 0) || 1,
        5,
        0.15,
      );
    }
    // Entering broken resets the harmony path so the asymmetry is observable
    // again on re-entry.
    if (s === "broken" && harmonyMode) {
      if (reverseTween) reverseTween.kill();
      harmonyMode = false;
      reverseActive = false;
      reverseT = 0;
      reverseProxy.t = 0;
    }
  };

  // ---- Section-09 command dispatch --------------------------------------
  commandHandler = (cmd: LucaCommand) => {
    if (cmd.kind === "reverse") {
      // ~2.5s power2.inOut ease back to a pristine symmetric origin state.
      if (reverseTween) reverseTween.kill();
      harmonyMode = true;
      reverseActive = true;
      reverseT = 0;
      reverseProxy.t = 0;
      reverseTween = gsap.to(reverseProxy, {
        t: 1,
        duration: 2.5,
        ease: "power2.inOut",
        onUpdate: () => {
          reverseT = reverseProxy.t;
        },
        onComplete: () => {
          reverseActive = false;
          reverseT = 1;
        },
      });
    } else if (cmd.kind === "explode") {
      // Burst into an additive particle cloud expanding outward over ~700ms;
      // hide the wireframe. The DOM beat owns the navigation.
      ensureParticles();
      explodeActive = true;
      explodeStart = performance.now();
      explodeT = 0;
    }
  };

  // ---- Interaction + sizing ---------------------------------------------
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const onPointerMove = (event: PointerEvent) => {
    pointer.tx = event.clientX / window.innerWidth - 0.5;
    pointer.ty = event.clientY / window.innerHeight - 0.5;
  };

  const onResize = () => {
    const r = root.getBoundingClientRect();
    camera.aspect = r.width / Math.max(r.height, 1);
    camera.updateProjectionMatrix();
    eyeZ = fitDistance(R, camera.fov, camera.aspect, 1.35);
    camera.position.set(C.x, C.y + R * 0.08, C.z + eyeZ);
    camera.lookAt(C.x, C.y, C.z);
    renderer.setPixelRatio(
      Math.min(window.devicePixelRatio || 1, isMobileTier ? 1.5 : 1.75),
    );
    renderer.setSize(r.width, r.height, false);
  };

  const ro = new ResizeObserver(onResize);
  ro.observe(root);
  window.addEventListener("pointermove", onPointerMove, { passive: true });
  window.addEventListener("resize", onResize);

  const isNarrowViewport =
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(max-width: 768px)").matches;
  const reduced = prefersReducedMotion() || isNarrowViewport;
  const startTime = performance.now();
  let rafId = 0;
  let orbitAngle = 0;

  // Reusable scratch vectors (avoid per-frame allocation).
  const ndcToWorld = (
    nx: number,
    ny: number,
    out: THREE.Vector3,
  ): THREE.Vector3 => {
    out.set(nx, ny, 0.5).unproject(camera);
    out.sub(camera.position).normalize();
    const dx = out.x, dy = out.y, dz = out.z;
    const tt = Math.abs(dz) < 1e-4 ? 0 : (0 - camera.position.z) / dz;
    out.set(
      camera.position.x + dx * tt,
      camera.position.y + dy * tt,
      camera.position.z + dz * tt,
    );
    return out;
  };

  // Per-frame scratch + last-timestamp (declared before tick so the closure
  // never references them across a temporal-dead-zone boundary).
  let lastNow = 0;
  const scratch = new THREE.Vector3();

  const tick = (now: number) => {
    if (props.active === false) {
      rafId = requestAnimationFrame(tick);
      return;
    }
    const dt = Math.min(0.05, (now - (lastNow || now)) / 1000);
    lastNow = now;
    const elapsed = (now - startTime) / 1000;
    const tgt = targets(curState, curProgress, curFragment);
    const E = ease;

    // Explode ramp (manual, ~700ms): particles expand from origin to field.
    if (explodeActive) {
      explodeT = Math.min(1, (now - explodeStart) / 700);
    }

    // Ease every live channel toward its per-state target.
    const chase = (key: keyof MorphTargets): void => {
      morph[key] = morph[key] + (tgt[key] - morph[key]) * E;
    };
    (Object.keys(morph) as Array<keyof MorphTargets>).forEach((k) => chase(k));

    // ---- Apply morph to the device group --------------------------------
    device.scale.set(
      morph.scaleX * clickProxy.v,
      morph.scaleY * clickProxy.v,
      morph.scaleZ * clickProxy.v,
    );
    device.rotation.set(morph.rotX, morph.rotY, morph.rotZ);

    // Wireframe color: white (0) ↔ grey #888 (1).
    wireMat.color.setRGB(
      1 - morph.wireGrey * (1 - 0x88 / 0xff),
      1 - morph.wireGrey * (1 - 0x88 / 0xff),
      1 - morph.wireGrey * (1 - 0x88 / 0xff),
    );

    // Main polyhedron visibility: hidden while doors are open, during the
    // quarteted corner spread, or mid-explode.
    const mainVisible =
      morph.openBlend < 0.5 && morph.quartet < 0.5 && !explodeActive;
    polyhedron.visible = mainVisible;
    wireMat.opacity = morph.wireOpacity;

    // Anchor (constant origin point; breathing in still).
    anchor.visible = morph.anchorVis > 0.02;
    const breath = curState === "still" ? Math.sin(elapsed * Math.PI / 2) * 0.15 + 1 : 1;
    anchor.scale.setScalar(morph.anchorScale * breath * morph.anchorVis);

    // Cracked core + halo.
    coreMat.opacity = morph.coreVis;
    coreSphere.visible = morph.coreVis > 0.02;
    coreSphere.rotation.y = -elapsed * 0.3;
    (coreHalo.material as THREE.SpriteMaterial).opacity = morph.coreLight * 0.9;
    coreHalo.visible = morph.coreLight > 0.02;

    // Snapped background grid.
    bgGridMat.opacity = morph.bgGrid;

    // Axed axis line + central glow.
    axisMat.opacity = morph.axisVis;
    axisLine.visible = morph.axisVis > 0.02;
    (axisGlow.material as THREE.SpriteMaterial).opacity = morph.axisVis * 0.7;
    axisGlow.visible = morph.axisVis > 0.02;

    // Opened halves + portal.
    const door = morph.openBlend > 0.02;
    leftHalf.visible = door;
    rightHalf.visible = door;
    const slide = 0.8 * morph.openBlend;
    leftHalf.position.x = -slide;
    rightHalf.position.x = slide;
    portalMat.opacity = morph.portalVis;
    portalPlane.visible = morph.portalVis > 0.02;

    // Stars (fade to 0 in still).
    starMat.opacity = morph.starOpacity;

    // ---- Per-vertex wireframe update (snap / broken / jitter / magnetic) -
    // Only touches the main polyhedron geometry; halves/corners have their own.
    if (mainVisible) {
      let cx = 0;
      let cy = 0;
      if (morph.magnetic > 0.01) {
        camera.updateMatrixWorld(true);
        const cur = ndcToWorld(pointer.x * 2, -pointer.y * 2, scratch);
        cx = cur.x;
        cy = cur.y;
      }
      const jitterAmp = 0.02 * morph.jitter;
      const brokenAmp = morph.brokenBlend;
      const snapAmp = morph.snapBlend;
      const magAmp = 0.1 * morph.magnetic;
      for (let i = 0; i < wireBase.length; i += 3) {
        // Base ↔ grid-snap blend.
        let vx = lerp(wireBase[i], wireSnap[i], snapAmp);
        let vy = lerp(wireBase[i + 1], wireSnap[i + 1], snapAmp);
        let vz = lerp(wireBase[i + 2], wireSnap[i + 2], snapAmp);
        // Seeded broken offsets.
        vx += wireBrokenOffsets[i] * brokenAmp;
        vy += wireBrokenOffsets[i + 1] * brokenAmp;
        vz += wireBrokenOffsets[i + 2] * brokenAmp;
        // Squash micro-jitter: a coherent surface vibration along the radial
        // pseudo-normal. Keyed off the BASE position (not the array index) so
        // WireframeGeometry's duplicated shared corners share one offset and
        // edges don't tear apart — reads as friction under pressure, not a
        // per-frame glitch.
        if (jitterAmp > 0) {
          const bx = wireBase[i];
          const by = wireBase[i + 1];
          const bz = wireBase[i + 2];
          const nl = Math.hypot(bx, by, bz) || 1e-4;
          const phase = bx * 4.1 + by * 3.7 + bz * 5.3;
          const wave =
            Math.sin(elapsed * 30 + phase) * 0.6 +
            Math.sin(elapsed * 47 + phase * 1.7) * 0.4;
          const d = wave * jitterAmp;
          vx += (bx / nl) * d;
          vy += (by / nl) * d;
          vz += (bz / nl) * d;
        }
        // Magnetic pull toward cursor (broken beat).
        if (magAmp > 0) {
          const dx = cx - vx;
          const dy = cy - vy;
          const len = Math.hypot(dx, dy);
          if (len > 1e-4) {
            const pull = Math.min(magAmp, len * 0.3);
            vx += (dx / len) * pull;
            vy += (dy / len) * pull;
          }
        }
        wireLive[i] = vx;
        wireLive[i + 1] = vy;
        wireLive[i + 2] = vz;
      }
      const attr = polyhedron.geometry.getAttribute("position") as THREE.BufferAttribute;
      (attr.array as Float32Array).set(wireLive);
      attr.needsUpdate = true;

      // Idle spin / orbit on the polyhedron itself (device holds scale + lean).
      if (morph.orbitRate > 0.01) {
        orbitAngle += dt * morph.orbitRate;
        polyhedron.rotation.y = orbitAngle;
        polyhedron.rotation.x = 0;
      } else if (morph.idleSpin > 0.01) {
        polyhedron.rotation.x = Math.sin(elapsed * 0.1) * 0.12 * morph.idleSpin;
        polyhedron.rotation.y = elapsed * 0.12 * morph.idleSpin;
      }
    }

    // ---- Quarteted: four corner sub-icosahedra --------------------------
    const quartetOn = morph.quartet > 0.02;
    if (quartetOn) {
      camera.updateMatrixWorld(true);
      for (let i = 0; i < 4; i++) {
        const [nx, ny] = PILLAR_NDC[i];
        ndcToWorld(nx, ny, scratch);
        const bob = Math.sin(elapsed * 1.5 + i * 1.7) * 0.05;
        const key = PILLAR_KEYS[i];
        const focused = curFragment === key;
        const otherDimmed = curFragment !== null && !focused;
        const targetScale = focused ? 1.8 : otherDimmed ? 0.7 : 1;
        const targetOpacity = focused ? 1 : otherDimmed ? 0.3 : 0.85 * morph.quartet;
        // Focused pillar returns to center + flares.
        const tx = focused ? 0 : scratch.x;
        const ty = focused ? 0 : scratch.y + bob;
        const tz = focused ? 0.4 : scratch.z;
        corners[i].visible = true;
        corners[i].position.x += (tx - corners[i].position.x) * E;
        corners[i].position.y += (ty - corners[i].position.y) * E;
        corners[i].position.z += (tz - corners[i].position.z) * E;
        const s = corners[i].scale.x + (targetScale - corners[i].scale.x) * E;
        corners[i].scale.setScalar(s);
        cornerMats[i].opacity += (targetOpacity - cornerMats[i].opacity) * E;
      }
    } else {
      for (let i = 0; i < 4; i++) {
        corners[i].visible = false;
        cornerMats[i].opacity = 0;
      }
    }

    // ---- Timed: tick colors + dial + progress arc -----------------------
    const tickOpacity = morph.tickRing;
    tickMat.opacity = tickOpacity;
    dialMat.opacity = morph.dialOpacity;
    arcMat.opacity = tickOpacity;
    tickMarks.visible = tickOpacity > 0.02;
    dial.visible = morph.dialOpacity > 0.02;
    arc.visible = tickOpacity > 0.02;
    if (tickOpacity > 0.02) {
      const litF = morph.tickLit * TICK_COUNT;
      const colAttr = tickGeo.getAttribute("color") as THREE.BufferAttribute;
      const colArr = colAttr.array as Float32Array;
      for (let i = 0; i < TICK_COUNT; i++) {
        // Each tick eases in over a 0.8-wide window so the ring lights
        // sequentially with a soft leading edge.
        const local = Math.max(0, Math.min(1, litF - i));
        const ramp = local >= 0.8 ? 1 : local / 0.8;
        const c = 0.12 + ramp * 0.88;
        const o = i * 6;
        colArr[o] = c;
        colArr[o + 1] = c;
        colArr[o + 2] = c;
        colArr[o + 3] = c;
        colArr[o + 4] = c;
        colArr[o + 5] = c;
      }
      colAttr.needsUpdate = true;
      const segCount = Math.max(0, Math.min(ARC_SEG + 1, Math.ceil(litF / TICK_COUNT * ARC_SEG) + 1));
      arcGeo.setDrawRange(0, segCount);
    }

    // ---- Explode particles ----------------------------------------------
    if (explodeActive && particles && particleLive) {
      particles.visible = true;
      const posAttr = particles.geometry.getAttribute("position") as THREE.BufferAttribute;
      const arr = posAttr.array as Float32Array;
      const e = explodeT;
      // Ease-out expansion: fast burst, settling tail.
      const exp = 1 - (1 - e) * (1 - e);
      for (let i = 0; i < particleLive.length; i += 3) {
        arr[i] = particleField[i] * exp;
        arr[i + 1] = particleField[i + 1] * exp;
        arr[i + 2] = particleField[i + 2] * exp;
      }
      posAttr.needsUpdate = true;
      (particles.material as THREE.PointsMaterial).opacity = Math.min(1, e * 1.6);
    }

    // ---- Camera parallax (pointer) --------------------------------------
    if (!reduced) {
      pointer.x += (pointer.tx - pointer.x) * 0.04;
      pointer.y += (pointer.ty - pointer.y) * 0.04;
      camera.position.x = C.x + pointer.x * 0.6;
      camera.position.y = C.y + R * 0.08 - pointer.y * 0.3;
      camera.lookAt(C.x, C.y, C.z);
      if (morph.idleSpin <= 0.01 && morph.orbitRate <= 0.01) {
        stars.rotation.y = elapsed * 0.01;
      }
    }

    renderer.render(scene, camera);
    if (!reduced) rafId = requestAnimationFrame(tick);
  };

  if (reduced) {
    // One static frame: apply the initial beat's targets once and render.
    const tgt = targets(curState, curProgress, curFragment);
    Object.assign(morph, tgt);
    device.scale.set(morph.scaleX, morph.scaleY, morph.scaleZ);
    device.rotation.set(morph.rotX, morph.rotY, morph.rotZ);
    wireMat.opacity = morph.wireOpacity;
    anchor.scale.setScalar(morph.anchorScale * morph.anchorVis);
    anchor.visible = morph.anchorVis > 0.02;
    starMat.opacity = morph.starOpacity;
    renderer.render(scene, camera);
  } else {
    rafId = requestAnimationFrame(tick);
  }

  cleanup = () => {
    if (rafId) cancelAnimationFrame(rafId);
    ro.disconnect();
    window.removeEventListener("pointermove", onPointerMove);
    window.removeEventListener("resize", onResize);
    if (reverseTween) reverseTween.kill();
    if (clickTween) clickTween.kill();
    const disposeMat = (m: THREE.Material | THREE.Material[]): void =>
      Array.isArray(m) ? m.forEach((x) => x.dispose()) : m.dispose();
    polyGeo.dispose();
    polyWireGeo.dispose();
    disposeMat(wireMat);
    leftHalfGeo.dispose();
    rightHalfGeo.dispose();
    disposeMat(halfMat);
    anchorGeo.dispose();
    disposeMat(anchorMat);
    coreGeo.dispose();
    disposeMat(coreMat);
    (coreHalo.material as THREE.SpriteMaterial).map?.dispose();
    disposeMat(coreHalo.material);
    bgGridGeo.dispose();
    disposeMat(bgGridMat);
    axisGeo.dispose();
    disposeMat(axisMat);
    (axisGlow.material as THREE.SpriteMaterial).map?.dispose();
    disposeMat(axisGlow.material);
    portalTex.dispose();
    portalGeo.dispose();
    disposeMat(portalMat);
    cornerGeo.dispose();
    cornerMats.forEach((m) => m.dispose());
    tickGeo.dispose();
    disposeMat(tickMat);
    dialGeo.dispose();
    disposeMat(dialMat);
    arcGeo.dispose();
    disposeMat(arcMat);
    starGeo.dispose();
    disposeMat(starMat);
    if (particles) {
      particles.geometry.dispose();
      disposeMat(particles.material);
    }
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
  <div ref="rootRef" class="luca-stage" aria-hidden="true">
    <canvas v-if="supportsWebGL" ref="canvasRef" class="luca-stage-canvas"></canvas>
    <div v-else class="luca-stage-fallback"></div>
  </div>
</template>
