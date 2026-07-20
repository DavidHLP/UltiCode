<script setup lang="ts">
/**
 * CodeCoreCanvas — the fixed full-viewport 3D layer behind the landing
 * narrative. Self-contained: owns its scroll listener, rAF loop, and every
 * degradation path so the page content never depends on WebGL.
 *
 * Degradation ladder:
 *   1. WebGL init failure (or dynamic import failure) → static SVG core.
 *   2. prefers-reduced-motion → no rAF loop, no parallax, no rotation;
 *      a single frame is rendered per scroll position.
 *   3. Small screens → fewer nodes, capped DPR, no pointer parallax.
 *   4. Page hidden → animation loop stops; resumes on visibility.
 */
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import type { CodeCoreScene } from "../scene/createCodeCoreScene";

const { t } = useI18n();

const canvasRef = ref<HTMLCanvasElement | null>(null);
const failed = ref(false);
const ready = ref(false);

let scene: CodeCoreScene | null = null;
let frameId = 0;
let running = false;
let reducedMotion = false;
let interactivePointer = false;
let targetProgress = 0;
let currentProgress = 0;
let removeMediaListener: (() => void) | null = null;

function readProgress(): number {
  const doc = document.documentElement;
  const scrollable = doc.scrollHeight - window.innerHeight;
  if (scrollable <= 0) return 0;
  return Math.min(1, Math.max(0, window.scrollY / scrollable));
}

function onScroll(): void {
  targetProgress = readProgress();
  if (reducedMotion && scene) {
    // Reduced motion: settle directly, render one frame — no damping.
    currentProgress = targetProgress;
    scene.render(currentProgress);
  }
}

function tick(): void {
  if (!running || !scene) return;
  const diff = targetProgress - currentProgress;
  // Frame-rate independent damping toward the scroll target.
  if (Math.abs(diff) > 0.0005) {
    currentProgress += diff * 0.12;
    scene.render(currentProgress);
  }
  frameId = window.requestAnimationFrame(tick);
}

function startLoop(): void {
  if (running || reducedMotion || !scene) return;
  running = true;
  frameId = window.requestAnimationFrame(tick);
}

function stopLoop(): void {
  running = false;
  if (frameId) {
    window.cancelAnimationFrame(frameId);
    frameId = 0;
  }
}

function onVisibility(): void {
  if (document.hidden) {
    stopLoop();
  } else {
    startLoop();
  }
}

function onPointerMove(event: PointerEvent): void {
  if (!interactivePointer || !scene) return;
  scene.setPointer(
    (event.clientX / window.innerWidth - 0.5) * 2,
    (event.clientY / window.innerHeight - 0.5) * 2,
  );
}

function onResize(): void {
  scene?.setSize(window.innerWidth, window.innerHeight);
  if (reducedMotion) onScroll();
}

onMounted(async () => {
  const canvas = canvasRef.value;
  if (!canvas) {
    failed.value = true;
    return;
  }

  const hasMatchMedia = typeof window.matchMedia === "function";
  const motionQuery = hasMatchMedia
    ? window.matchMedia("(prefers-reduced-motion: reduce)")
    : null;
  reducedMotion = motionQuery?.matches ?? false;
  const onMotionChange = (event: MediaQueryListEvent) => {
    reducedMotion = event.matches;
    if (reducedMotion) {
      stopLoop();
      onScroll();
    } else {
      startLoop();
    }
  };
  if (motionQuery) {
    motionQuery.addEventListener("change", onMotionChange);
    removeMediaListener = () =>
      motionQuery.removeEventListener("change", onMotionChange);
  }

  const smallScreen = window.innerWidth < 768;
  interactivePointer =
    !reducedMotion &&
    hasMatchMedia &&
    window.matchMedia("(pointer: fine)").matches;

  try {
    // Dynamic import keeps three.js out of the first-paint bundle.
    const { createCodeCoreScene } = await import(
      "../scene/createCodeCoreScene"
    );
    scene = createCodeCoreScene({
      canvas,
      count: smallScreen ? 280 : 700,
      maxDpr: smallScreen ? 1.5 : 2,
      interactive: !reducedMotion,
    });
  } catch {
    // WebGL unavailable or context creation failed — static fallback.
    failed.value = true;
    return;
  }

  scene.setSize(window.innerWidth, window.innerHeight);
  targetProgress = readProgress();
  currentProgress = targetProgress;
  scene.render(currentProgress);
  ready.value = true;

  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onResize, { passive: true });
  document.addEventListener("visibilitychange", onVisibility);
  if (interactivePointer) {
    window.addEventListener("pointermove", onPointerMove, { passive: true });
  }
  startLoop();
});

onBeforeUnmount(() => {
  stopLoop();
  window.removeEventListener("scroll", onScroll);
  window.removeEventListener("resize", onResize);
  document.removeEventListener("visibilitychange", onVisibility);
  if (interactivePointer) {
    window.removeEventListener("pointermove", onPointerMove);
  }
  removeMediaListener?.();
  scene?.dispose();
  scene = null;
});
</script>

<template>
  <div class="code-core-layer" aria-hidden="false">
    <canvas
      v-show="!failed"
      ref="canvasRef"
      class="code-core-canvas"
      :class="{ 'is-ready': ready }"
      role="img"
      :aria-label="t('landing.hero.sceneAlt')"
    />
    <!-- Static fallback: WebGL failed — narrative content stays fully usable. -->
    <div v-if="failed" class="code-core-fallback" aria-hidden="true">
      <svg viewBox="0 0 200 200" class="code-core-fallback-svg">
        <g
          fill="none"
          stroke="var(--solarized-cyan)"
          stroke-opacity="0.55"
          stroke-width="1"
        >
          <circle cx="100" cy="100" r="46" />
          <ellipse cx="100" cy="100" rx="46" ry="18" />
          <ellipse cx="100" cy="100" rx="18" ry="46" />
          <circle cx="100" cy="54" r="2.5" fill="var(--solarized-green)" />
          <circle cx="146" cy="100" r="2.5" fill="var(--solarized-cyan)" />
          <circle cx="100" cy="146" r="2.5" fill="var(--solarized-cyan)" />
          <circle cx="54" cy="100" r="2.5" fill="var(--solarized-cyan)" />
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.code-core-layer {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(
      ellipse 80% 60% at 50% 40%,
      color-mix(in srgb, var(--solarized-cyan) 6%, transparent),
      transparent 70%
    ),
    var(--background);
}

.code-core-canvas {
  width: 100%;
  height: 100%;
  display: block;
  opacity: 0;
  transition: opacity 600ms ease;
}

.code-core-canvas.is-ready {
  opacity: 1;
}

.code-core-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.code-core-fallback-svg {
  width: min(46vmin, 320px);
  height: auto;
  opacity: 0.9;
}

@media (prefers-reduced-motion: reduce) {
  .code-core-canvas {
    transition: none;
  }
}
</style>
