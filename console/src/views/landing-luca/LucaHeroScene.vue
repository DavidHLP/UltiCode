<script setup lang="ts">
/**
 * Three.js particle-landscape hero.
 *
 * Renders thousands of monochrome particles on a procedural heightmap
 * (value noise), a glowing additive-blend focal star above the ridge, and a
 * large letter-spaced word rendered to a CanvasTexture and applied to a
 * billboarded plane in the foreground. Camera drifts slowly and tracks the
 * pointer for parallax.
 *
 * Reduced-motion: the rAF loop is short-circuited; a single static frame
 * is drawn and the scene is not animated.
 *
 * WebGL unavailable: the canvas stays blank (the page background and the
 * loading portal cover it), so the page never breaks.
 */

import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";

const WORDS = ["code", "judge", "compete", "learn"] as const;
const PARTICLE_COUNT = 4200;
const TERRAIN_STEP_X = 1.4;
const TERRAIN_STEP_Z = 1.2;
const WORD_PLANE_WIDTH = 14;
const WORD_PLANE_HEIGHT = 2.4;

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
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
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
  window.addEventListener("pointermove", onPointerMove, { passive: true });

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
    camera.position.x = pointer.x * 3.2;
    camera.position.y = 4.2 - pointer.y * 1.4;
    camera.lookAt(0, 2.4, 0);

    if (!reduced) {
      particles.rotation.y =
        Math.sin(elapsed * 0.12) * 0.18 + elapsed * 0.06;
      star.material.opacity = 0.85 + Math.sin(elapsed * 1.4) * 0.12;
      wordMesh.position.y = 2.4 + Math.sin(elapsed * 0.9) * 0.08;
      wordMesh.rotation.z = Math.sin(elapsed * 0.4) * 0.012;
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
    window.removeEventListener("pointermove", onPointerMove);
    geometry.dispose();
    particleMaterial.dispose();
    dotTexture.dispose();
    starMaterial.dispose();
    starTexture.dispose();
    wordMaterial.dispose();
    wordTexture.dispose();
    wordMesh.geometry.dispose();
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