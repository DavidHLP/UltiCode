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
 * Brutalist monochrome: warm base2 wireframe + cyan glow on a Solarized base03
 * field (the page background paints through the alpha canvas). No
 * postprocessing — bloom/glow is faked with additive-blended lines and
 * radial-gradient sprites so the <40-draw-call / <30k-triangle budget holds on
 * integrated GPUs.
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
 *     The scene calls `reportCommandCompleted` in its `onComplete` callback.
 *   - explode → the polyhedron bursts into an additive THREE.Points cloud
 *     expanding outward over ~700ms while the wireframe hides.  Navigation is
 *     deferred until the scene reports completion via the stage callback; the beat
 *     expresses intent via `requestFutureTransition` and the deep module sequences
 *     the animation and auth-aware router push.
 * WebGL unavailable → terminal handler installed, queued commands completed synchronously.
 * Reduced motion / narrow viewport → reduced guard in the real handler completes
 *   commands synchronously; a static frame is still rendered.
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
  applyTargets,
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
// A fast CTA click during startup is queued and replayed once the handler
// is installed, so no command is silently dropped.
let commandHandler: ((cmd: LucaCommand) => void) | null = null;
let stateEnterHandler: ((s: LucaState) => void) | null = null;
let queuedCommand: LucaCommand | null = null;
watch(
  () => stage.command.value,
  (cmd) => {
    if (!cmd) return;
    if (commandHandler) {
      commandHandler(cmd);
    } else {
      // Handler not yet installed (still booting); queue and replay once ready.
      queuedCommand = cmd;
    }
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

// Frame-rate-independent exponential damping. `rate` is in 1/s; higher = snappier.
// Equivalent to "ease per frame" only when dt is the implied frame time, so the
// same rate produces identical visible motion at 30 / 60 / 120 fps. Used for
// every morph channel, the topology presence envelopes, and pointer parallax.
const damp = (
  cur: number,
  target: number,
  rate: number,
  dt: number,
): number => cur + (target - cur) * (1 - Math.exp(-rate * dt));

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
  // Install a terminal handler that immediately completes any queued command.
  // Used when canvas/root are absent or WebGL is unavailable, so no command
  // hangs waiting for an animation that cannot play.
  const installTerminalHandler = () => {
    commandHandler = (cmd) => stage.reportCommandCompleted(cmd.id);
    if (queuedCommand) {
      commandHandler(queuedCommand);
      queuedCommand = null;
    }
  };
  if (!canvas || !root) {
    installTerminalHandler();
    return;
  }
  if (!detectWebGL()) {
    supportsWebGL.value = false;
    installTerminalHandler();
    return;
  }

  const THREE = await import("three");

  // Solarized dark palette (canonical hex). Neutrals + single cyan accent —
  // referenced by every material/sprite/vertex-color below so the scene reads
  // as one Solarized system.
  const SOLARIZED = {
    base03: 0x002b36,
    base02: 0x073642,
    base01: 0x586e75,
    base1: 0x93a1a1,
    base2: 0xeee8d5,
    base3: 0xfdf6e3,
    cyan: 0x2aa198,
  } as const;

  const rect = root.getBoundingClientRect();
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(SOLARIZED.base03, 0.055);

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
      g.addColorStop(0, `rgba(42,161,152,${innerAlpha})`);
      g.addColorStop(0.4, "rgba(42,161,152,0.25)");
      g.addColorStop(1, "rgba(42,161,152,0)");
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
    color: SOLARIZED.base2,
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
    color: SOLARIZED.base2,
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

  // The constant anchor: a tiny base3 (brightest) point at the world origin.
  // Hides only in the cracked beat (the core sphere takes its place).
  const anchorGeo = new THREE.IcosahedronGeometry(0.05, 0);
  const anchorMat = new THREE.MeshBasicMaterial({ color: SOLARIZED.base3 });
  const anchor = new THREE.Mesh(anchorGeo, anchorMat);
  scene.add(anchor);

  // ---- Cracked (02): inner core sphere + halo sprite -------------------
  const coreGeo = new THREE.IcosahedronGeometry(0.25, 1);
  const coreMat = new THREE.MeshBasicMaterial({
    color: SOLARIZED.cyan,
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
    color: SOLARIZED.base02,
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
    color: SOLARIZED.cyan,
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
    g.addColorStop(0, "rgba(42,161,152,0.55)");
    g.addColorStop(0.5, "rgba(42,161,152,0.12)");
    g.addColorStop(1, "rgba(42,161,152,0)");
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
      color: SOLARIZED.base2,
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
    color: SOLARIZED.base02,
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
    color: SOLARIZED.cyan,
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
    color: SOLARIZED.base1,
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
      color: SOLARIZED.cyan,
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
  // One live packet; each frame eases toward the per-state targets (written
  // into `tgtBuf` by `applyTargets()`) then drives the THREE
  // objects. Channels that need per-state geometry (snap/broken/jitter/magnetic)
  // are applied in the per-vertex wireframe step.
  //
  // Per-channel damping rates (1/s). Higher = snappier. Grouped so fades land
  // first, blends next, scales next, rotations last — every channel arrives
  // together instead of one snapping while another trails.
  const RATES: Record<keyof MorphTargets, number> = {
    // Opacity / visibility — fast so fades never smear.
    wireOpacity: 10,
    starOpacity: 10,
    anchorVis: 9,
    coreVis: 10,
    coreLight: 10,
    bgGrid: 10,
    axisVis: 10,
    portalVis: 10,
    dialOpacity: 10,
    tickRing: 10,
    tickLit: 11,
    // Color / topology blends.
    wireGrey: 7,
    snapBlend: 7,
    brokenBlend: 7,
    openBlend: 7,
    quartet: 7,
    magnetic: 6,
    jitter: 7,
    // Scale channels.
    scaleX: 6,
    scaleY: 6,
    scaleZ: 6,
    anchorScale: 5,
    // Rotation / orbit — slowest, so leans and orbits glide.
    rotX: 4,
    rotY: 4,
    rotZ: 4,
    orbitRate: 3.5,
    idleSpin: 4,
  };
  const MORPH_KEYS = Object.keys(PRISTINE) as Array<keyof MorphTargets>;
  const morph: MorphTargets = { ...PRISTINE };
  // Reusable target buffer — written in place by applyTargets() every frame so
  // the damping loop never allocates a fresh MorphTargets object per tick.
  const tgtBuf: MorphTargets = { ...PRISTINE };

  // Topology presence envelopes (scale-rate damping, target = the matching
  // morph channel). Used to grow-in / shrink-out the toggleable objects
  // (core, axis, portal, halves, corners, grid, dial/ticks/arc) AND to
  // cross-fade the main polyhedron ↔ split halves ↔ four corners. Defined
  // once here so the tick closure never allocates.
  const aux = {
    main: 1, // main polyhedron (1) ↔ halves/corners (0)
    half: 0, // split halves envelope (opened 05)
    corner: 0, // four corners envelope (quarteted 06)
    core: 0, // inner core sphere (cracked 02)
    axis: 0, // axis line + glow (axed 04)
    portal: 0, // portal plane (opened 05)
    grid: 0, // bg grid plane (snapped 03)
    ring: 0, // dial + ticks + arc (timed 07)
  };

  // Reverse-harmony state (beat 09). When harmonyMode is on, applyTargets()
  // writes pristine into tgtBuf for the broken beat; reverseT (0→1 over 2.5s
  // via gsap) blends the transition so the return reads as a deliberate
  // easing, not a flip.
  let harmonyMode = false;
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
      reverseT = 0;
      reverseProxy.t = 0;
    }
  };

  const isNarrowViewport =
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(max-width: 768px)").matches;
  const reduced = prefersReducedMotion() || isNarrowViewport;

  // ---- Section-09 command dispatch --------------------------------------
  // Track the explode command id whose completion should trigger navigation.
  // We do NOT reset explodeActive on completion so the final exploded visual
  // stays on screen until the route change takes effect.
  let currentExplodeCommandId: number | null = null;
  commandHandler = (cmd: LucaCommand) => {
    if (reduced) {
      // Reduced motion / narrow viewport: no animation. Complete synchronously.
      stage.reportCommandCompleted(cmd.id);
      return;
    }
    if (cmd.kind === "reverse") {
      // ~2.5s power2.inOut ease back to a pristine symmetric origin state.
      if (reverseTween) reverseTween.kill();
      harmonyMode = true;
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
          reverseT = 1;
        },
      });
    } else if (cmd.kind === "explode") {
      // Burst into an additive particle cloud expanding outward over ~700ms;
      // hide the wireframe. Navigation is driven by the stage's completion
      // callback once the scene reports the animation is done.
      ensureParticles();
      explodeActive = true;
      explodeStart = performance.now();
      explodeT = 0;
      currentExplodeCommandId = cmd.id;
    }
  };

  // Replay any command that arrived before the handler was installed.
  if (queuedCommand) {
    commandHandler(queuedCommand);
    queuedCommand = null;
  }

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
  const startTime = performance.now();
  let rafId = 0;
  let orbitAngle = 0;
  // Last rotation mode (orbit / idle / none) so we can re-seed orbitAngle from
  // the live polyhedron.rotation.y on a mode change and avoid a handoff snap.
  let lastMode: "none" | "orbit" | "idle" = "none";

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
    const dt = Math.min(1 / 30, (now - (lastNow || now)) / 1000);
    lastNow = now;
    const elapsed = (now - startTime) / 1000;
    applyTargets(curState, curProgress, curFragment, tgtBuf, harmonyMode, reverseT);
    // explodeActive so the final exploded visual stays on screen.
    if (explodeActive) {
      explodeT = Math.min(1, (now - explodeStart) / 700);
      if (explodeT >= 1 && currentExplodeCommandId !== null) {
        const id = currentExplodeCommandId;
        currentExplodeCommandId = null;
        stage.reportCommandCompleted(id);
      }
    }

    // Frame-rate-independent damping toward per-state targets. Each channel
    // uses its own RATES entry (1/s) so fades, blends, scales, and rotations
    // arrive together instead of one snapping while another trails. Uses the
    // hoisted MORPH_KEYS list and reads tgtBuf in place — no per-frame keys
    // array, arrow, or target object is allocated.
    for (let i = 0; i < MORPH_KEYS.length; i++) {
      const k = MORPH_KEYS[i];
      morph[k] = damp(morph[k], tgtBuf[k], RATES[k], dt);
    }

    // Topology presence envelopes (scale-rate damping). Each toggleable
    // object's scale tracks its envelope, and its material opacity is the
    // matching morph channel multiplied by the envelope — so newly-shown
    // objects brighten-then-grow while hidden ones dim-then-shrink, with
    // overlap rather than a binary cut. The main polyhedron cross-fades
    // continuously against the split halves and the four corners.
    const mainTarget =
      (1 - morph.openBlend) * (1 - morph.quartet) * (explodeActive ? 0 : 1);
    aux.main = damp(aux.main, mainTarget, 6, dt);
    aux.half = damp(aux.half, morph.openBlend, 6, dt);
    aux.corner = damp(aux.corner, morph.quartet, 6, dt);
    aux.core = damp(aux.core, morph.coreVis > 0.01 ? 1 : 0, 6, dt);
    aux.axis = damp(aux.axis, morph.axisVis > 0.01 ? 1 : 0, 6, dt);
    aux.portal = damp(aux.portal, morph.portalVis > 0.01 ? 1 : 0, 6, dt);
    aux.grid = damp(aux.grid, morph.bgGrid > 0.01 ? 1 : 0, 6, dt);
    aux.ring = damp(
      aux.ring,
      morph.tickRing > 0.01 || morph.dialOpacity > 0.01 ? 1 : 0,
      6,
      dt,
    );

    // ---- Apply morph to the device group --------------------------------
    device.scale.set(
      morph.scaleX * clickProxy.v,
      morph.scaleY * clickProxy.v,
      morph.scaleZ * clickProxy.v,
    );
    device.rotation.set(morph.rotX, morph.rotY, morph.rotZ);

    // Wireframe color: base2 bright ↔ base01 dim (Solarized neutrals).
    wireMat.color.setRGB(
      0xee / 0xff + morph.wireGrey * (0x58 / 0xff - 0xee / 0xff),
      0xe8 / 0xff + morph.wireGrey * (0x6e / 0xff - 0xe8 / 0xff),
      0xd5 / 0xff + morph.wireGrey * (0x75 / 0xff - 0xd5 / 0xff),
    );

    // Main polyhedron: cross-fades against the split halves (opened) and the
    // four corners (quarteted) via aux.main — shrinks + fades simultaneously
    // so the body morphs between topologies, never a binary cut. The wireframe
    // fade-out on explode rides the same envelope.
    polyhedron.scale.setScalar(aux.main);
    polyhedron.visible = aux.main > 0.01;
    wireMat.opacity = morph.wireOpacity * aux.main;

    // Anchor (constant origin point; breathing in still).
    anchor.visible = morph.anchorVis > 0.01;
    const breath = curState === "still" ? Math.sin(elapsed * Math.PI / 2) * 0.15 + 1 : 1;
    anchor.scale.setScalar(morph.anchorScale * breath * morph.anchorVis);

    // Cracked core + halo — grow-in / shrink-out with the core envelope.
    coreSphere.scale.setScalar(aux.core);
    coreMat.opacity = morph.coreVis * aux.core;
    coreSphere.visible = morph.coreVis * aux.core > 0.01;
    coreSphere.rotation.y = -elapsed * 0.3;
    const coreHaloMat = coreHalo.material as THREE.SpriteMaterial;
    coreHaloMat.opacity = morph.coreLight * 0.9 * aux.core;
    coreHalo.scale.setScalar(1.6 * aux.core);
    coreHalo.visible = morph.coreLight * aux.core > 0.01;

    // Snapped background grid — blooms in from the origin.
    bgGrid.scale.setScalar(aux.grid);
    bgGridMat.opacity = morph.bgGrid * aux.grid;

    // Axed axis line + central glow — draws in from the center.
    axisLine.scale.setScalar(aux.axis);
    axisMat.opacity = morph.axisVis * aux.axis;
    axisLine.visible = morph.axisVis * aux.axis > 0.01;
    const axisGlowMat = axisGlow.material as THREE.SpriteMaterial;
    axisGlowMat.opacity = morph.axisVis * 0.7 * aux.axis;
    axisGlow.scale.setScalar(1.2 * aux.axis);
    axisGlow.visible = morph.axisVis * aux.axis > 0.01;

    // Opened halves + portal — halves grow from 0 while sliding apart and the
    // main polyhedron shrinks (cross-fade, overlap not sequence). Portal plane
    // blooms in behind the gap.
    const slide = 0.8 * aux.half;
    leftHalf.position.x = -slide;
    rightHalf.position.x = slide;
    leftHalf.scale.setScalar(aux.half);
    rightHalf.scale.setScalar(aux.half);
    halfMat.opacity = 0.9 * aux.half;
    const halfVis = aux.half > 0.01;
    leftHalf.visible = halfVis;
    rightHalf.visible = halfVis;
    portalPlane.scale.setScalar(aux.portal);
    portalMat.opacity = morph.portalVis * aux.portal;
    portalPlane.visible = morph.portalVis * aux.portal > 0.01;

    // Stars (fade to 0 in still).
    starMat.opacity = morph.starOpacity;

    // ---- Per-vertex wireframe update (snap / broken / jitter / magnetic) -
    // Only touches the main polyhedron geometry; halves/corners have their own.
    // Runs while the polyhedron is still fading in/out so its deformation stays
    // live during the cross-fade.
    if (polyhedron.visible) {
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
      // Unified under one accumulator: on any mode change we re-seed orbitAngle
      // from the live rotation.y so idle↔orbit transitions are continuous
      // (orbitRate and idleSpin damping already glide the angular SPEED).
      const mode =
        morph.orbitRate > 0.01 ? "orbit" : morph.idleSpin > 0.01 ? "idle" : "none";
      if (mode !== "none" && mode !== lastMode) {
        orbitAngle = polyhedron.rotation.y;
      }
      lastMode = mode;
      if (mode === "orbit") {
        orbitAngle += dt * morph.orbitRate;
        polyhedron.rotation.y = orbitAngle;
        polyhedron.rotation.x = 0;
      } else if (mode === "idle") {
        orbitAngle += dt * 0.12 * morph.idleSpin;
        polyhedron.rotation.y = orbitAngle;
        polyhedron.rotation.x = Math.sin(elapsed * 0.1) * 0.12 * morph.idleSpin;
      }
    }

    // ---- Quarteted: four corner sub-icosahedra --------------------------
    // Corner scale + opacity multiply by aux.corner so the four bodies grow-in
    // as the main polyhedron shrinks (cross-fade), then shrink-out on exit.
    // Per-pillar focus (center return + flare + dim-others) damps with dt so
    // the focus hand-off glides at any frame rate.
    if (aux.corner > 0.01) {
      camera.updateMatrixWorld(true);
      const env = aux.corner;
      for (let i = 0; i < 4; i++) {
        const [nx, ny] = PILLAR_NDC[i];
        ndcToWorld(nx, ny, scratch);
        const bob = Math.sin(elapsed * 1.5 + i * 1.7) * 0.05;
        const key = PILLAR_KEYS[i];
        const focused = curFragment === key;
        const otherDimmed = curFragment !== null && !focused;
        const targetScale = (focused ? 1.8 : otherDimmed ? 0.7 : 1) * env;
        const targetOpacity = (focused ? 1 : otherDimmed ? 0.3 : 0.85) * env;
        // Focused pillar returns to center + flares.
        const tx = focused ? 0 : scratch.x;
        const ty = focused ? 0 : scratch.y + bob;
        const tz = focused ? 0.4 : scratch.z;
        corners[i].position.x = damp(corners[i].position.x, tx, 7, dt);
        corners[i].position.y = damp(corners[i].position.y, ty, 7, dt);
        corners[i].position.z = damp(corners[i].position.z, tz, 7, dt);
        const s = damp(corners[i].scale.x, targetScale, 7, dt);
        corners[i].scale.setScalar(s);
        const o = damp(cornerMats[i].opacity, targetOpacity, 10, dt);
        cornerMats[i].opacity = o;
        corners[i].visible = o > 0.01;
      }
    } else {
      for (let i = 0; i < 4; i++) {
        corners[i].visible = false;
        cornerMats[i].opacity = 0;
        corners[i].scale.setScalar(0);
      }
    }

    // ---- Timed: tick colors + dial + progress arc -----------------------
    // Ring blooms in from the origin (aux.ring drives scale); each element's
    // opacity is its morph channel multiplied by the envelope.
    const ringEnv = aux.ring;
    const tickOpacity = morph.tickRing * ringEnv;
    const dialOpacity = morph.dialOpacity * ringEnv;
    dial.scale.setScalar(ringEnv);
    tickMarks.scale.setScalar(ringEnv);
    arc.scale.setScalar(ringEnv);
    tickMat.opacity = tickOpacity;
    dialMat.opacity = dialOpacity;
    arcMat.opacity = tickOpacity;
    tickMarks.visible = tickOpacity > 0.01;
    dial.visible = dialOpacity > 0.01;
    arc.visible = tickOpacity > 0.01;
    if (tickOpacity > 0.01) {
      const litF = morph.tickLit * TICK_COUNT;
      const colAttr = tickGeo.getAttribute("color") as THREE.BufferAttribute;
      const colArr = colAttr.array as Float32Array;
      for (let i = 0; i < TICK_COUNT; i++) {
        // Each tick eases in over a 0.8-wide window so the ring lights
        // sequentially with a soft leading edge.
        const local = Math.max(0, Math.min(1, litF - i));
        const ramp = local >= 0.8 ? 1 : local / 0.8;
        // Keep the original ramp shape (0.12 → 1.0) but retint endpoints:
        // dim end = Solarized base02, bright end = Solarized cyan.
        const t = 0.12 + ramp * 0.88;
        const cr = 0x07 / 0xff + t * (0x2a / 0xff - 0x07 / 0xff);
        const cg = 0x36 / 0xff + t * (0xa1 / 0xff - 0x36 / 0xff);
        const cb = 0x42 / 0xff + t * (0x98 / 0xff - 0x42 / 0xff);
        const o = i * 6;
        colArr[o] = cr;
        colArr[o + 1] = cg;
        colArr[o + 2] = cb;
        colArr[o + 3] = cr;
        colArr[o + 4] = cg;
        colArr[o + 5] = cb;
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
    // Damped with dt so the parallax glide is identical at any frame rate.
    if (!reduced) {
      pointer.x = damp(pointer.x, pointer.tx, 2.5, dt);
      pointer.y = damp(pointer.y, pointer.ty, 2.5, dt);
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
    applyTargets(curState, curProgress, curFragment, tgtBuf, harmonyMode, reverseT)
    Object.assign(morph, tgtBuf)
    device.scale.set(morph.scaleX, morph.scaleY, morph.scaleZ)
    device.rotation.set(morph.rotX, morph.rotY, morph.rotZ)
    starMat.opacity = morph.starOpacity
    renderer.render(scene, camera)
  } else {
    rafId = requestAnimationFrame(tick)
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
