<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import {
  createLandingScene,
  type LandingSceneHandle,
} from "./composables/useLandingScene";
import {
  createLandingFader,
  type LandingFaderHandle,
} from "./composables/useLandingFader";

/**
 * LandingView — dark-only immersive Vue 3 host for the UltiCode landing.
 *
 * The 3D scene is a license-clean reimplementation of the reference landing's
 * particle-narrative effect; this component only owns the canvas, the scroll
 * spacer (24 × 100dvh), the DOM text overlay, and the Vue lifecycle. Always-dark
 * (#151515) by design — the reference is an always-dark canvas, so we do NOT
 * remap for light mode and we do NOT write `data-theme` (reserved to shared/theme).
 */

const canvas = ref<HTMLCanvasElement | null>(null);
const scrollerEl = ref<HTMLDivElement | null>(null);
const progress = ref(0);
const failed = ref(false);

let scene: LandingSceneHandle | null = null;
let fader: LandingFaderHandle | null = null;
let fontLink: HTMLLinkElement | null = null;
let onScroll: (() => void) | null = null;

const clamp01 = (v: number) => (v < 0 ? 0 : v > 1 ? 1 : v);

/** Reveal opacity for an act label visible within [start, end] of progress. */
const actOpacity = (start: number, end: number) => {
  const p = progress.value;
  const fade = (end - start) * 0.3;
  if (p < start || p > end) return 0;
  if (p < start + fade) return (p - start) / fade;
  if (p > end - fade) return (end - p) / fade;
  return 1;
};

onMounted(() => {
  // IBM Plex Mono (OFL) — license-clean; loaded from /public/fonts.
  fontLink = document.createElement("link");
  fontLink.rel = "stylesheet";
  fontLink.href = "/fonts/ibm-plex-mono/google-ibm-plex-mono.css";
  document.head.appendChild(fontLink);

  const canvasEl = canvas.value;
  const scroller = scrollerEl.value;
  if (!canvasEl || !scroller) return;

  const reducedMotion = window
    .matchMedia("(prefers-reduced-motion: reduce)")
    .matches;
  const isDesktop = window.matchMedia("(min-width: 768px)").matches;

  // Scene BEFORE fader: the reduced-motion fader calls scene.renderOnce() at init.
  try {
    scene = createLandingScene(canvasEl, { reducedMotion, isDesktop });
    fader = createLandingFader({ scene, scroller, reducedMotion });
  } catch (err) {
    console.error("[LandingView] WebGL scene init failed:", err);
    failed.value = true;
    return;
  }

  onScroll = () => {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    progress.value = max > 0 ? clamp01(window.scrollY / max) : 0;
  };
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
});

onUnmounted(() => {
  if (onScroll) window.removeEventListener("scroll", onScroll);
  fader?.dispose();
  scene?.dispose();
  fontLink?.remove();
  scene = null;
  fader = null;
  onScroll = null;
  fontLink = null;
});
</script>

<template>
  <div class="landing" aria-label="UltiCode landing experience">
    <canvas ref="canvas" class="landing__canvas" aria-hidden="true"></canvas>

    <div class="landing__overlay" aria-hidden="true">
      <p class="landing__hint" :style="{ opacity: 1 - clamp01(progress / 0.06) }">
        scroll to explore
      </p>
      <h2 class="landing__act" :style="{ opacity: actOpacity(0.08, 0.2) }">
        VISION
      </h2>
      <h2 class="landing__act" :style="{ opacity: actOpacity(0.4, 0.54) }">
        CRAFT
      </h2>
      <h2 class="landing__act" :style="{ opacity: actOpacity(0.72, 0.86) }">
        EXPERIENCE
      </h2>
    </div>

    <p v-if="failed" class="landing__fallback">
      UltiCode — online judge. The immersive landing could not be rendered in this
      browser.
    </p>

    <!-- Scroll length: 24 × 100dvh, scrubbed by the fader. -->
    <div ref="scrollerEl" class="landing__scroller"></div>
  </div>
</template>

<style scoped>
.landing {
  position: relative;
  /* Always-dark by design (reference is an always-dark canvas); not a theme toggle. */
  background: #151515;
}

.landing__canvas {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  z-index: 0;
}

.landing__overlay {
  position: fixed;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

.landing__hint {
  position: absolute;
  bottom: 7%;
  left: 50%;
  transform: translateX(-50%);
  margin: 0;
  font-family: "IBM Plex Mono", var(--uc-font-code), monospace;
  font-size: var(--uc-text-sm);
  letter-spacing: 0.22em;
  color: rgba(255, 255, 255, 0.55);
  text-transform: lowercase;
  white-space: nowrap;
}

.landing__act {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  margin: 0;
  font-family: "IBM Plex Mono", var(--uc-font-code), monospace;
  font-size: clamp(3rem, 11vw, 9rem);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #ffffff;
  text-shadow: 0 0 36px rgba(255, 255, 255, 0.3);
}

.landing__fallback {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 1rem;
  text-align: center;
  font-family: var(--uc-font-code);
  color: rgba(255, 255, 255, 0.7);
}

.landing__scroller {
  position: relative;
  z-index: 0;
  height: 2400dvh;
}

@media (prefers-reduced-motion: reduce) {
  .landing__act {
    text-shadow: none;
  }
}
</style>
