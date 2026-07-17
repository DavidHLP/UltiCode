<script setup lang="ts">
/**
 * Three.js particle-landscape hero.
 *
 * Layered, additive-blend cinematic backdrop:
 *  - low-density ground particle field on a procedural fbm heightmap
 *  - rising ember stream above the ridge (vertical accents)
 *  - five drifting "metaball" sprites on independent Lissajous paths
 *    (soft-glowing fluid lobes that read as a 3D fluid sim at low cost)
 *  - two volumetric god-ray cones for cinematic lighting
 *  - focal additive star hovering above the ridge
 *  - billboarded word plane in the foreground, font drawn to a CanvasTexture
 *
 * Camera drifts slowly and tracks the pointer for parallax. Every layer
 * respects prefers-reduced-motion; resources are released on unmount.
 *
 * WebGL unavailable: the canvas stays blank (the page background and the
 * loading portal cover it), so the page never breaks.
 */

import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type * as THREE from "three";

const props = defineProps<{ active?: boolean; worldProgress?: number }>();

let scrubTarget = -1;
watch(
  () => props.worldProgress,
  (v) => {
    scrubTarget = typeof v === "number" ? v : -1;
  },
  { immediate: true },
);

const WORDS = ["code", "judge", "compete", "learn"] as const;

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

const PARTICLE_COUNT = isMobileTier ? 2200 : 4200;
const TERRAIN_STEP_X = 1.4;
const TERRAIN_STEP_Z = 1.2;
const WORD_PLANE_WIDTH = 14;
const WORD_PLANE_HEIGHT = 2.4;
const EMBER_COUNT = isMobileTier ? 320 : 720;
const METABALL_COUNT = 5;
const GOD_RAY_COUNT = 2;

const HERO_POS = [0, 4.2, 18] as const;
const HERO_LOOK = [0, 2.4, 0] as const;
interface Waypoint {
  p: number;
  pos: readonly [number, number, number];
  look: readonly [number, number, number];
}
const WAYPOINTS: readonly Waypoint[] = [
  { p: 0.0, pos: [0, 4.2, 18], look: [0, 2.4, 0] },
  { p: 0.1, pos: [-6, 3.5, 12], look: [-4, 2.0, -2] },
  { p: 0.2, pos: [0, 4.8, 8], look: [0, 2.4, 0] },
  { p: 0.3, pos: [0, 9.0, 6], look: [0, 3.0, -8] },
  { p: 0.42, pos: [4, 3.2, 2], look: [0, 2.4, -6] },
  { p: 0.5, pos: [-2, 3.0, -3], look: [-6, 2.2, -12] },
  { p: 0.62, pos: [0, 4.5, -8], look: [0, 2.8, -16] },
  { p: 0.74, pos: [0, 6.0, -12], look: [0, 3.0, -20] },
  { p: 0.84, pos: [3, 7.0, -10], look: [0, 5.0, -18] },
  { p: 0.92, pos: [0, 5.0, -6], look: [0, 3.0, -2] },
  { p: 1.0, pos: [0, 4.0, 4], look: [0, 2.4, 2] },
];

// Smoothstep easing helpers shared by the camera path and per-beat morphs.
const band = (pp: number, lo: number, hi: number): number => {
  if (pp <= lo) return 0;
  if (pp >= hi) return 1;
  const x = (pp - lo) / (hi - lo);
  return x * x * (3 - 2 * x);
};
const inBand = (pp: number, lo: number, hi: number): number =>
  Math.min(1, Math.max(0, (pp - lo) / (hi - lo)));

const canvasRef = ref<HTMLCanvasElement | null>(null);
const rootRef = ref<HTMLElement | null>(null);
const supportsWebGL = ref(true);

const { t } = useI18n();

// Cheap 2D value noise — no external dependency. Good enough to break up
// the particle field; we don't need simplex-grade quality at this scale.
const hash = (x: number, y: number): number => {
  let h = x * 374761393 + y * 668265263;
  h = (h ^ (h >>> 13)) * 1274126177;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
};
const smoothNoise = (x: number, y: number): number => {
  const xi = Math.floor(x);
  const yi = Math.floor(y);
  const xf = x - xi;
  const yf = y - yi;
  const a = hash(xi, yi);
  const b = hash(xi + 1, yi);
  const c = hash(xi, yi + 1);
  const d = hash(xi + 1, yi + 1);
  const u = xf * xf * (3 - 2 * xf);
  const v = yf * yf * (3 - 2 * yf);
  return a * (1 - u) * (1 - v) + b * u * (1 - v) + c * (1 - u) * v + d * u * v;
};
const fbm = (x: number, y: number): number => {
  let total = 0;
  let amp = 1;
  let freq = 1;
  let max = 0;
  for (let i = 0; i < 4; i++) {
    total += smoothNoise(x * freq, y * freq) * amp;
    max += amp;
    amp *= 0.5;
    freq *= 2;
  }
  return total / max;
};

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

  // Persistent scratch vectors — allocated once and captured by the tick
  // closure so the camera path never creates garbage per frame.
  const curPos = new THREE.Vector3(0, 4.2, 18);
  const curLook = new THREE.Vector3(0, 2.4, 0);
  const tmpPos = new THREE.Vector3();
  const tmpLook = new THREE.Vector3();

  const rect = root.getBoundingClientRect();
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x05060a, 0.038);

  const camera = new THREE.PerspectiveCamera(
    55,
    rect.width / rect.height,
    0.1,
    200,
  );
  camera.position.set(0, 4.2, 18);
  camera.lookAt(0, 2, 0);

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

  // Particle terrain — a deterministic grid; z is driven by fbm.
  const positions = new Float32Array(PARTICLE_COUNT * 3);
  const colors = new Float32Array(PARTICLE_COUNT * 3);
  const sizes = new Float32Array(PARTICLE_COUNT);
  for (let i = 0; i < PARTICLE_COUNT; i++) {
    const col = Math.floor(i / 60);
    const row = i % 60;
    const x = (col - 30) * TERRAIN_STEP_X + (Math.random() - 0.5) * 0.4;
    const z = (row - 30) * TERRAIN_STEP_Z + (Math.random() - 0.5) * 0.4;
    const n = fbm(col * 0.08, row * 0.08);
    // Two layered ridges: a tall back ridge and a softer front swell.
    const ridge =
      Math.pow(Math.max(0, 1 - Math.abs(col - 18) / 24), 1.4) * 4.2 +
      Math.pow(Math.max(0, 1 - Math.abs(col - 38) / 18), 1.8) * 2.4;
    const y = n * 2.4 + ridge - 4;
    positions[i * 3 + 0] = x;
    positions[i * 3 + 1] = y;
    positions[i * 3 + 2] = z;
    const brightness = 0.45 + Math.random() * 0.55;
    colors[i * 3 + 0] = brightness * 0.78;
    colors[i * 3 + 1] = brightness * 0.84;
    colors[i * 3 + 2] = brightness;
    sizes[i] = 0.04 + Math.random() * 0.09;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
  geometry.setAttribute("size", new THREE.BufferAttribute(sizes, 1));

  // Circular sprite texture for soft round particles — generated in 2D.
  const dot = document.createElement("canvas");
  dot.width = dot.height = 64;
  const dctx = dot.getContext("2d");
  if (dctx) {
    const gradient = dctx.createRadialGradient(32, 32, 0, 32, 32, 32);
    gradient.addColorStop(0, "rgba(255,255,255,1)");
    gradient.addColorStop(0.35, "rgba(220,230,255,0.65)");
    gradient.addColorStop(1, "rgba(0,0,0,0)");
    dctx.fillStyle = gradient;
    dctx.fillRect(0, 0, 64, 64);
  }
  const dotTexture = new THREE.CanvasTexture(dot);
  dotTexture.needsUpdate = true;

  const particleMaterial = new THREE.PointsMaterial({
    size: 0.18,
    sizeAttenuation: true,
    vertexColors: true,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    map: dotTexture,
    opacity: 0.95,
  });

  const particles = new THREE.Points(geometry, particleMaterial);
  scene.add(particles);

  // Rising embers — small additive points lifted above the ridge so the
  // scene reads as "live" instead of static terrain. Vertical velocity is
  // baked into the geometry; the loop only modulates opacity.
  const emberPositions = new Float32Array(EMBER_COUNT * 3);
  const emberSeeds = new Float32Array(EMBER_COUNT);
  for (let i = 0; i < EMBER_COUNT; i++) {
    emberPositions[i * 3 + 0] = (Math.random() - 0.5) * 56;
    emberPositions[i * 3 + 1] = Math.random() * 14 - 1;
    emberPositions[i * 3 + 2] = (Math.random() - 0.5) * 28 - 2;
    emberSeeds[i] = Math.random();
  }
  const emberGeometry = new THREE.BufferGeometry();
  emberGeometry.setAttribute(
    "position",
    new THREE.BufferAttribute(emberPositions, 3),
  );
  const emberMaterial = new THREE.PointsMaterial({
    size: 0.13,
    sizeAttenuation: true,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    color: 0xffd9a8,
    opacity: 0.85,
    map: dotTexture,
  });
  const embers = new THREE.Points(emberGeometry, emberMaterial);
  scene.add(embers);

  // Metaballs — five soft glowing lobes drifting on independent Lissajous
  // paths. Each lobe uses the same radial gradient texture (rebuilt once
  // for warm-to-cool falloff) with additive blending so overlapping lobes
  // bloom into a fluid silhouette.
  const lobeCanvas = document.createElement("canvas");
  lobeCanvas.width = lobeCanvas.height = 256;
  const lctx = lobeCanvas.getContext("2d");
  if (lctx) {
    const g = lctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    g.addColorStop(0, "rgba(220, 235, 255, 1)");
    g.addColorStop(0.22, "rgba(150, 195, 255, 0.55)");
    g.addColorStop(0.55, "rgba(110, 150, 230, 0.18)");
    g.addColorStop(1, "rgba(0, 0, 0, 0)");
    lctx.fillStyle = g;
    lctx.fillRect(0, 0, 256, 256);
  }
  const lobeTexture = new THREE.CanvasTexture(lobeCanvas);
  const metaballSprites: THREE.Sprite[] = [];
  const metaballPaths: Array<{
    ax: number;
    ay: number;
    az: number;
    fx: number;
    fy: number;
    fz: number;
    px: number;
    py: number;
    pz: number;
    scale: number;
  }> = [];
  for (let i = 0; i < METABALL_COUNT; i++) {
    const material = new THREE.SpriteMaterial({
      map: lobeTexture,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      opacity: 0.55 + Math.random() * 0.25,
    });
    const sprite = new THREE.Sprite(material);
    const scale = 3.2 + Math.random() * 2.6;
    sprite.scale.set(scale, scale, 1);
    scene.add(sprite);
    metaballSprites.push(sprite);
    metaballPaths.push({
      ax: 6 + Math.random() * 7,
      ay: 1.6 + Math.random() * 1.4,
      az: 4 + Math.random() * 3,
      fx: 0.07 + Math.random() * 0.09,
      fy: 0.11 + Math.random() * 0.13,
      fz: 0.05 + Math.random() * 0.07,
      px: Math.random() * Math.PI * 2,
      py: Math.random() * Math.PI * 2,
      pz: Math.random() * Math.PI * 2,
      scale,
    });
  }

  // God-rays — two wide additive cones descending from above the ridge,
  // each rotating slowly and pulsing. Built from ConeGeometry with the
  // open end pointing up; depth-write off so layering stays soft.
  const godRays: THREE.Mesh[] = [];
  const rayGeometry = new THREE.ConeGeometry(5.6, 22, 24, 1, true);
  for (let i = 0; i < GOD_RAY_COUNT; i++) {
    const rayMaterial = new THREE.MeshBasicMaterial({
      color: i === 0 ? 0x6ea8ff : 0xaac4ff,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      opacity: 0.16,
      side: THREE.DoubleSide,
    });
    const ray = new THREE.Mesh(rayGeometry, rayMaterial);
    ray.position.set(i === 0 ? -7 : 8, 6, -6 - i * 1.5);
    ray.rotation.set(Math.PI, 0, 0);
    ray.rotation.z = (i === 0 ? -1 : 1) * 0.18;
    scene.add(ray);
    godRays.push(ray);
  }

  // Focal star — a large additive sprite hovering above the ridge.
  const starCanvas = document.createElement("canvas");
  starCanvas.width = starCanvas.height = 256;
  const sctx = starCanvas.getContext("2d");
  if (sctx) {
    const g = sctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    g.addColorStop(0, "rgba(255,255,255,1)");
    g.addColorStop(0.18, "rgba(200,225,255,0.55)");
    g.addColorStop(0.45, "rgba(120,160,220,0.18)");
    g.addColorStop(1, "rgba(0,0,0,0)");
    sctx.fillStyle = g;
    sctx.fillRect(0, 0, 256, 256);
  }
  const starTexture = new THREE.CanvasTexture(starCanvas);
  const starMaterial = new THREE.SpriteMaterial({
    map: starTexture,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  });
  const star = new THREE.Sprite(starMaterial);
  star.position.set(-2.5, 5.5, -3);
  star.scale.set(2.6, 2.6, 1);
  scene.add(star);

  // Central 3D word plane. The texture is rendered from the active i18n
  // word; we redraw it on every swap.
  const wordTexture = new THREE.CanvasTexture(
    document.createElement("canvas"),
  );
  const wordMaterial = new THREE.MeshBasicMaterial({
    map: wordTexture,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  });
  const wordMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(WORD_PLANE_WIDTH, WORD_PLANE_HEIGHT),
    wordMaterial,
  );
  wordMesh.position.set(0, 2.4, 2);
  scene.add(wordMesh);

  const renderWordCanvas = (word: string): HTMLCanvasElement => {
    const c = document.createElement("canvas");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const widthPx = 1024;
    const heightPx = Math.round(
      (widthPx * WORD_PLANE_HEIGHT) / WORD_PLANE_WIDTH,
    );
    c.width = widthPx * dpr;
    c.height = heightPx * dpr;
    const ctx = c.getContext("2d");
    if (!ctx) return c;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, widthPx, heightPx);
    ctx.fillStyle = "#f4f4f5";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.font =
      '800 168px "LXGW WenKai", "Noto Sans SC", system-ui, sans-serif';
    ctx.shadowColor = "rgba(170, 210, 255, 0.7)";
    ctx.shadowBlur = 36;
    ctx.fillText(word.toUpperCase(), widthPx / 2, heightPx / 2);
    ctx.shadowBlur = 0;
    return c;
  };

  const renderWordTexture = (word: string) => {
    wordTexture.image = renderWordCanvas(word);
    wordTexture.needsUpdate = true;
  };

  let wordIndex = 0;
  let wordSwapAt = performance.now() + 2400;
  renderWordTexture(t(`landingLuca.hero.words.${WORDS[wordIndex]}`));

  // Light ribbon — the one extra geometry. A thin tube along a Catmull-Rom
  // curve threading the scene, faded in for the "experience" beat.
  const ribbonCurve = new THREE.CatmullRomCurve3(
    [
      new THREE.Vector3(6, 2.6, 4),
      new THREE.Vector3(3, 2.8, 0),
      new THREE.Vector3(0, 3.0, -3),
      new THREE.Vector3(-3, 2.7, -7),
      new THREE.Vector3(-6, 2.6, -11),
    ],
    false,
    "catmullrom",
    0.4,
  );
  const ribbonGeometry = new THREE.TubeGeometry(ribbonCurve, 80, 0.06, 8, false);
  const ribbonMaterial = new THREE.MeshBasicMaterial({
    color: 0x9cc4ff,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    opacity: 0,
  });
  const ribbon = new THREE.Mesh(ribbonGeometry, ribbonMaterial);
  scene.add(ribbon);

  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  const onPointerMove = (event: PointerEvent) => {
    const r = root.getBoundingClientRect();
    pointer.tx = (event.clientX - r.left) / r.width - 0.5;
    pointer.ty = (event.clientY - r.top) / r.height - 0.5;
  };

  const onResize = () => {
    const r = root.getBoundingClientRect();
    camera.aspect = r.width / r.height;
    camera.updateProjectionMatrix();
    renderer.setSize(r.width, r.height, false);
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

    if (!reduced && now >= wordSwapAt) {
      wordIndex = (wordIndex + 1) % WORDS.length;
      renderWordTexture(t(`landingLuca.hero.words.${WORDS[wordIndex]}`));
      wordSwapAt = now + 2400;
    }

    pointer.x += (pointer.tx - pointer.x) * 0.04;
    pointer.y += (pointer.ty - pointer.y) * 0.04;
    const p = scrubTarget;
    if (p >= 0) {
      const wp = WAYPOINTS;
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
      tmpLook.set(
        a.look[0] + (b.look[0] - a.look[0]) * ts,
        a.look[1] + (b.look[1] - a.look[1]) * ts,
        a.look[2] + (b.look[2] - a.look[2]) * ts,
      );
      curPos.lerp(tmpPos, 0.08);
      curLook.lerp(tmpLook, 0.08);
    } else {
      curPos.lerp(tmpPos.set(HERO_POS[0], HERO_POS[1], HERO_POS[2]), 0.04);
      curLook.lerp(
        tmpLook.set(HERO_LOOK[0], HERO_LOOK[1], HERO_LOOK[2]),
        0.04,
      );
    }
    camera.position.set(
      curPos.x + pointer.x * 0.8,
      curPos.y - pointer.y * 0.4,
      curPos.z,
    );
    camera.lookAt(curLook.x, curLook.y, curLook.z);

    if (!reduced) {
      particles.rotation.y =
        Math.sin(elapsed * 0.12) * 0.18 +
        elapsed * 0.06 +
        (p >= 0 ? band(p, 0.7, 0.78) * 0.4 : 0);
      star.material.opacity = 0.85 + Math.sin(elapsed * 1.4) * 0.12;
      wordMesh.position.y = 2.4 + Math.sin(elapsed * 0.9) * 0.08;
      wordMesh.rotation.z = Math.sin(elapsed * 0.4) * 0.012;

      // Embers: wrap the float upward; each particle keeps its own
      // horizontal phase so the stream doesn't read as a single sheet.
      const ep = emberGeometry.attributes.position as THREE.BufferAttribute;
      const arr = ep.array as Float32Array;
      for (let i = 0; i < EMBER_COUNT; i++) {
        const idx = i * 3;
        arr[idx + 1] +=
          (0.012 + emberSeeds[i] * 0.018) *
          (1 - (p >= 0 ? band(p, 0.8, 0.88) : 0) * 0.85);
        if (arr[idx + 1] > 13) arr[idx + 1] = -1.5;
        arr[idx + 0] += Math.sin(elapsed * 0.6 + emberSeeds[i] * 6.28) * 0.004;
      }
      ep.needsUpdate = true;
      embers.rotation.y = Math.sin(elapsed * 0.08) * 0.12;

      // Metaballs: each lobe traces its own slow Lissajous path and gently
      // breathes its scale; the overlapping additive sprites produce the
      // fluid-blob silhouette without an actual marching-cubes pass.
      for (let i = 0; i < METABALL_COUNT; i++) {
        const p = metaballPaths[i];
        const sprite = metaballSprites[i];
        sprite.position.x =
          Math.sin(elapsed * p.fx + p.px) * p.ax +
          Math.cos(elapsed * p.fy * 0.6 + p.px) * 1.2;
        sprite.position.y =
          2.8 + Math.sin(elapsed * p.fy + p.py) * p.ay +
          Math.cos(elapsed * 0.4 + p.py) * 0.5;
        sprite.position.z =
          Math.cos(elapsed * p.fz + p.pz) * p.az - 1;
        const breathe = 1 + Math.sin(elapsed * 0.7 + p.px) * 0.08;
        sprite.scale.set(p.scale * breathe, p.scale * breathe, 1);
        sprite.material.opacity =
          0.5 + 0.25 * (0.5 + 0.5 * Math.sin(elapsed * 0.9 + p.py));
      }

      // God-rays: very slow rotation + opacity pulse keeps them from
      // looking like static cones.
      for (let i = 0; i < GOD_RAY_COUNT; i++) {
        const ray = godRays[i];
        ray.rotation.y = elapsed * (i === 0 ? 0.05 : -0.04);
        const mat = ray.material as THREE.MeshBasicMaterial;
        mat.opacity = 0.12 + 0.06 * Math.sin(elapsed * 0.5 + i);
      }
    }

    // Per-beat morphs — driven by the same worldProgress as the camera.
    // Each band eases a layer's scale/opacity to match the section visible
    // in the foreground DOM. Runs after all idle animation so the morphs
    // compose on top.
    if (p >= 0) {
      const Bproblem = band(p, 0.06, 0.14);
      const Bsolution = band(p, 0.16, 0.24);
      const Bvision = band(p, 0.26, 0.34);
      const Bexperience = band(p, 0.38, 0.55);
      const Bwork = band(p, 0.58, 0.68);
      const Bcapabilities = band(p, 0.7, 0.78);
      const Bawards = band(p, 0.8, 0.88);
      const Babout = band(p, 0.88, 0.94);
      const Bcta = inBand(p, 0.95, 1.0);
      void Babout;
      // problem: scatter terrain + dim
      particles.scale.set(1 + Bproblem * 1.2, 1, 1 + Bproblem * 0.8);
      particleMaterial.opacity = 0.95 - Bproblem * 0.35;
      // solution: contract terrain (overlay on the scatter above by
      // lerping toward 0.9)
      particles.scale.x +=
        Bsolution > 0 ? (0.9 - (1 + Bproblem * 1.2)) * Bsolution : 0;
      particles.scale.z +=
        Bsolution > 0 ? (0.9 - (1 + Bproblem * 0.8)) * Bsolution : 0;
      // vision: widen god-rays
      for (let i = 0; i < GOD_RAY_COUNT; i++) {
        const r = godRays[i];
        r.scale.set(1 + Bvision * 0.6, 1, 1);
        (r.material as THREE.MeshBasicMaterial).opacity =
          0.12 + Bvision * 0.12 + Math.sin(elapsed * 0.5 + i) * 0.06;
      }
      // experience: light ribbon
      ribbonMaterial.opacity = Bexperience * (0.4 + 0.15 * Math.sin(elapsed * 2));
      // work: pin 4 metaballs as portals
      const portalSlots: ReadonlyArray<readonly [number, number, number]> = [
        [-6, 3, -14],
        [-2, 3, -15],
        [2, 3, -15],
        [6, 3, -14],
      ];
      for (let i = 0; i < 4; i++) {
        const s = metaballSprites[i];
        if (Bwork > 0.02) {
          s.position.set(portalSlots[i][0], portalSlots[i][1], portalSlots[i][2]);
          s.scale.set(4.5 * Bwork, 4.5 * Bwork, 1);
          (s.material as THREE.SpriteMaterial).opacity = 0.4 + Bwork * 0.4;
        }
      }
      // capabilities: flatten ridges
      particles.scale.y = 1 - Bcapabilities * 0.3;
      // awards: slow embers + brighten star (star opacity already animated
      // above; add boost)
      (star.material as THREE.SpriteMaterial).opacity += Bawards * 0.2;
      star.scale.set(2.6 + Bawards * 1.5, 2.6 + Bawards * 1.5, 1);
      // cta: lock bright word
      if (Bcta > 0.02) {
        if (wordSwapAt !== Infinity) {
          wordSwapAt = Infinity;
          renderWordTexture(t("landingLuca.hero.words.code"));
        }
        wordMaterial.opacity = 0.4 + Bcta * 0.6;
        wordMesh.scale.setScalar(1 + Bcta * 0.15);
      }
      // about: no positional change (camera waypoint handles the calm beat)
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
    geometry.dispose();
    particleMaterial.dispose();
    dotTexture.dispose();
    starMaterial.dispose();
    starTexture.dispose();
    wordMaterial.dispose();
    wordTexture.dispose();
    wordMesh.geometry.dispose();
    emberGeometry.dispose();
    emberMaterial.dispose();
    lobeTexture.dispose();
    for (const sprite of metaballSprites) {
      sprite.material.dispose();
    }
    rayGeometry.dispose();
    for (const ray of godRays) {
      (ray.material as THREE.Material).dispose();
    }
    ribbonGeometry.dispose();
    ribbonMaterial.dispose();
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