<script setup lang="ts">
/**
 * LandingCanvas — fixed full-viewport 3D layer carrying the monochrome
 * wasteland: one particle field, six morph states, a camera rail. Owns its
 * scroll listener, dt-damped rAF loop, and every degradation path so page
 * content never depends on WebGL.
 *
 * Degradation ladder:
 *   1. WebGL/init failure → static fallback mark; content unaffected.
 *   2. prefers-reduced-motion → no loop: each chapter renders its pinned
 *      dwell composition, swapped on scroll.
 *   3. Small screens → mobile rail, 14k particles, capped DPR, no pointer.
 *   4. Page hidden → loop stops; resumes on visibility.
 */
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import type { WastelandScene } from "../scene/createWastelandScene";
import { CHAPTERS, MONOLITHS } from "../scene/layout";
import { chapterAt } from "../scene/rail";

const { t } = useI18n();

const canvasRef = ref<HTMLCanvasElement | null>(null);
const failed = ref(false);
const ready = ref(false);

let scene: WastelandScene | null = null;
let frameId = 0;
let running = false;
let reducedMotion = false;
let interactivePointer = false;
let targetProgress = 0;
let renderedProgress = 0;
let lastFrameTime = 0;
let lastDwellChapter = -1;
let removeMediaListener: (() => void) | null = null;

function readProgress(): number {
  const doc = document.documentElement;
  const scrollable = doc.scrollHeight - window.innerHeight;
  if (scrollable <= 0) return 0;
  return Math.min(1, Math.max(0, window.scrollY / scrollable));
}

function renderDwellShot(): void {
  if (!scene) return;
  const chapter = chapterAt(targetProgress);
  if (chapter === lastDwellChapter) return;
  lastDwellChapter = chapter;
  scene.renderAt(CHAPTERS[chapter].dwell);
}

function onScroll(): void {
  targetProgress = readProgress();
  if (reducedMotion) renderDwellShot();
}

function tick(now: number): void {
  if (!running || !scene) return;
  const dt = Math.min(0.1, Math.max(0.001, (now - lastFrameTime) / 1000));
  lastFrameTime = now;

  const diff = targetProgress - renderedProgress;
  if (Math.abs(diff) > 0.0004) {
    // Heavy, inertial travel: exponential approach, frame-rate independent.
    const alpha = 1 - Math.exp(-dt * 6);
    renderedProgress += diff * alpha;
    scene.setProgress(renderedProgress);
  }
  scene.render(dt);
  frameId = window.requestAnimationFrame(tick);
}

function startLoop(): void {
  if (running || reducedMotion || !scene) return;
  running = true;
  lastFrameTime = performance.now();
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

/** DOM link hovers pull particles toward the matching monolith. */
function onGather(event: Event): void {
  if (!scene) return;
  const detail = (event as CustomEvent<{ monolith: number; strength: number }>)
    .detail;
  const monolith = MONOLITHS[detail.monolith];
  if (!monolith) return;
  scene.setGather(
    monolith.x,
    monolith.height * 0.5,
    monolith.z,
    detail.strength,
  );
}

function onResize(): void {
  scene?.setSize(window.innerWidth, window.innerHeight);
  if (reducedMotion) {
    lastDwellChapter = -1;
    renderDwellShot();
  }
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
      lastDwellChapter = -1;
      renderDwellShot();
    } else {
      renderedProgress = targetProgress;
      scene?.setProgress(renderedProgress);
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
    const { createWastelandScene } = await import(
      "../scene/createWastelandScene"
    );
    scene = createWastelandScene({
      canvas,
      variant: smallScreen ? "mobile" : "desktop",
      maxDpr: smallScreen ? 1.5 : 2,
      interactive: !reducedMotion,
      labels: {
        chapters: [
          t("landing.hero.eyebrow"),
          t("landing.parse.eyebrow"),
          t("landing.matrix.eyebrow"),
          t("landing.growth.eyebrow"),
          t("landing.network.eyebrow"),
          "",
        ],
        brand: "ULTICODE",
      },
    });
  } catch {
    // WebGL unavailable or init failed — static fallback.
    failed.value = true;
    return;
  }

  scene.setSize(window.innerWidth, window.innerHeight);
  targetProgress = readProgress();
  renderedProgress = targetProgress;
  scene.setProgress(renderedProgress);

  if (reducedMotion) {
    renderDwellShot();
  } else {
    scene.render(0.016);
  }
  ready.value = true;

  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onResize, { passive: true });
  window.addEventListener("ulticode:landing-gather", onGather);
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
  window.removeEventListener("ulticode:landing-gather", onGather);
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
  <div class="landing-canvas-layer">
    <canvas
      v-show="!failed"
      ref="canvasRef"
      class="landing-canvas"
      :class="{ 'is-ready': ready }"
      role="img"
      :aria-label="t('landing.hero.sceneAlt')"
    />
    <!-- Static fallback: WebGL failed — narrative content stays fully usable. -->
    <div v-if="failed" class="landing-canvas-fallback" aria-hidden="true">
      <svg viewBox="0 0 200 200" class="landing-canvas-fallback-svg">
        <g
          fill="none"
          stroke="rgba(235, 238, 242, 0.55)"
          stroke-width="1"
        >
          <circle cx="100" cy="100" r="46" />
          <ellipse cx="100" cy="100" rx="46" ry="18" />
          <ellipse cx="100" cy="100" rx="18" ry="46" />
          <circle cx="100" cy="100" r="2.5" fill="rgba(235, 238, 242, 0.9)" />
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.landing-canvas-layer {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: #0a0a0a;
}

.landing-canvas {
  width: 100%;
  height: 100%;
  display: block;
  opacity: 0;
  transition: opacity 900ms ease;
}

.landing-canvas.is-ready {
  opacity: 1;
}

.landing-canvas-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.landing-canvas-fallback-svg {
  width: min(46vmin, 320px);
  height: auto;
  opacity: 0.9;
}

@media (prefers-reduced-motion: reduce) {
  .landing-canvas {
    transition: none;
  }
}
</style>
