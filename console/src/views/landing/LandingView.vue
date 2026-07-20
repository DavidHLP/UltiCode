<script setup lang="ts">
/**
 * LandingView — public `/` route: a full-screen, scroll-driven monochrome
 * wasteland. One particle field morphs through six states while the camera
 * rides a rail down the corridor. All narrative content is plain DOM; the
 * canvas is a progressive enhancement layered underneath.
 *
 * Text reveal uses blur/brightness recovery (no bottom-up fades); reduced
 * motion swaps it for instant, readable sections.
 */
import { onBeforeUnmount, onMounted } from "vue";
import { useI18n } from "vue-i18n";
import LandingCanvas from "./components/LandingCanvas.vue";
import LandingChrome from "./components/LandingChrome.vue";
import HeroSection from "./sections/HeroSection.vue";
import ParseSection from "./sections/ParseSection.vue";
import MatrixSection from "./sections/MatrixSection.vue";
import GrowthSection from "./sections/GrowthSection.vue";
import NetworkSection from "./sections/NetworkSection.vue";
import FinaleSection from "./sections/FinaleSection.vue";

const { t } = useI18n();

let observer: IntersectionObserver | null = null;

onMounted(() => {
  const root = document.querySelector(".landing-root");
  if (!root || typeof IntersectionObserver === "undefined") {
    root?.querySelectorAll(".landing-reveal").forEach((el) => {
      el.classList.add("is-revealed");
    });
    return;
  }
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-revealed");
          observer?.unobserve(entry.target);
        }
      }
    },
    { threshold: 0.2 },
  );
  root
    .querySelectorAll(".landing-reveal")
    .forEach((el) => observer?.observe(el));
});

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
});

defineOptions({ name: "LandingView" });
</script>

<template>
  <div class="landing-root">
    <a href="#landing-main" class="landing-skip">
      {{ t("common.skipToContent") }}
    </a>
    <LandingCanvas />
    <!-- Film treatment: grain, scanlines, vignette — pure CSS, no GPU pass. -->
    <div class="landing-grain" aria-hidden="true" />
    <div class="landing-vignette" aria-hidden="true" />
    <LandingChrome />
    <main id="landing-main" class="relative z-10">
      <HeroSection />
      <ParseSection />
      <MatrixSection />
      <GrowthSection />
      <NetworkSection />
      <FinaleSection />
    </main>
  </div>
</template>

<style>
/*
 * Monochrome exhibition system — scoped to the landing subtree so the
 * platform's Solarized themes stay untouched. Near-black ground, silver
 * text at stepped opacities, hairline structure, no color.
 */
.landing-root {
  position: relative;
  min-height: 100vh;
  background: #0a0a0a;
  color: rgba(235, 238, 242, 0.92);
  --lw-text: rgba(235, 238, 242, 0.92);
  --lw-muted: rgba(235, 238, 242, 0.55);
  --lw-faint: rgba(235, 238, 242, 0.38);
  --lw-hairline: rgba(235, 238, 242, 0.14);
  --lw-highlight: rgba(240, 244, 248, 0.98);
}

.landing-skip {
  position: absolute;
  left: 1rem;
  top: -3rem;
  z-index: 40;
  background: var(--lw-highlight);
  color: #0a0a0a;
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  transition: top 150ms ease;
}

.landing-skip:focus-visible {
  top: 1rem;
}

/* — film treatment — */
.landing-grain {
  position: fixed;
  inset: 0;
  z-index: 20;
  pointer-events: none;
  opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='240' height='240' filter='url(%23n)' opacity='0.9'/%3E%3C/svg%3E");
}

.landing-vignette {
  position: fixed;
  inset: 0;
  z-index: 20;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      to bottom,
      transparent 0 3px,
      rgba(255, 255, 255, 0.008) 3px 4px
    ),
    radial-gradient(
      ellipse 90% 75% at 50% 45%,
      transparent 55%,
      rgba(0, 0, 0, 0.55) 100%
    );
}

/* — narrative sections — */
.landing-section {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 100vh;
  padding-block: 6rem;
}

@media (min-width: 1024px) {
  .landing-section {
    min-height: 140vh;
  }
}

.landing-container {
  margin-inline: auto;
  width: 100%;
  max-width: 72rem;
  padding-inline: 1rem;
}

@media (min-width: 640px) {
  .landing-container {
    padding-inline: 1.5rem;
  }
}

.landing-block {
  border-inline-start: 1px solid var(--lw-hairline);
  padding-inline-start: 1.5rem;
}

.landing-block.text-center,
.landing-block.mx-auto {
  border-inline-start: none;
  padding-inline-start: 0;
}

/* Blur/brightness recovery on scroll-in — never a bottom-up fade. */
.landing-reveal {
  opacity: 0.08;
  filter: blur(9px) brightness(0.65);
  transform: scale(0.995);
  transition:
    opacity 900ms ease,
    filter 900ms ease,
    transform 900ms ease;
}

.landing-reveal.is-revealed {
  opacity: 1;
  filter: blur(0) brightness(1);
  transform: none;
}

.landing-eyebrow {
  font-family: var(--uc-font-data);
  font-size: var(--uc-text-2xs, 0.625rem);
  font-weight: var(--uc-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.35em;
  color: var(--lw-faint);
  margin-bottom: 1rem;
}

.landing-title {
  font-size: clamp(2.25rem, 5.5vw, 3.75rem);
  font-weight: var(--uc-font-weight-bold);
  line-height: 1.15;
  letter-spacing: 0.02em;
  text-wrap: balance;
  color: var(--lw-highlight);
}

.landing-heading {
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: var(--uc-font-weight-bold);
  line-height: 1.2;
  letter-spacing: 0.02em;
  text-wrap: balance;
  color: var(--lw-highlight);
}

.landing-body {
  margin-top: 1.25rem;
  max-width: 36rem;
  font-size: 1.0625rem;
  line-height: 1.75;
  color: var(--lw-muted);
}

.landing-block.mx-auto .landing-body {
  margin-inline: auto;
}

.landing-points {
  margin-top: 1.5rem;
  display: grid;
  gap: 0.625rem;
  list-style: none;
  padding: 0;
}

.landing-points li {
  position: relative;
  padding-inline-start: 1.25rem;
  font-size: 0.9375rem;
  color: var(--lw-text);
}

.landing-points li::before {
  content: "";
  position: absolute;
  inset-inline-start: 0;
  top: 0.55em;
  width: 0.5rem;
  height: 1px;
  background: var(--lw-faint);
}

/* — landing buttons (override design-system variants inside the exhibition) — */
.landing-root .landing-btn-primary {
  background: var(--lw-highlight);
  color: #0a0a0a;
  border: 1px solid transparent;
  border-radius: 9999px;
  font-family: var(--uc-font-data);
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.landing-root .landing-btn-primary:hover {
  background: #ffffff;
}

.landing-root .landing-btn-ghost {
  background: transparent;
  color: var(--lw-text);
  border: 1px solid rgba(235, 238, 242, 0.28);
  border-radius: 9999px;
  font-family: var(--uc-font-data);
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.landing-root .landing-btn-ghost:hover {
  border-color: rgba(235, 238, 242, 0.6);
  background: rgba(235, 238, 242, 0.06);
}

/* — editor fragment (parse chapter) — */
.landing-editor {
  border: 1px solid var(--lw-hairline);
  background: rgba(16, 16, 16, 0.72);
}

.landing-editor-bar {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  border-bottom: 1px solid var(--lw-hairline);
  padding: 0.625rem 0.875rem;
}

.landing-editor-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 9999px;
  background: var(--lw-faint);
  opacity: 0.5;
}

.landing-editor-file {
  margin-inline-start: 0.5rem;
  font-family: var(--uc-font-data);
  font-size: 0.6875rem;
  color: var(--lw-faint);
}

.landing-editor-code {
  padding: 1rem 1.25rem;
  font-family: var(--uc-font-code);
  font-size: 0.8125rem;
  line-height: 1.7;
  overflow-x: auto;
  color: var(--lw-text);
}

/* — judge matrix — */
.landing-matrix {
  border: 1px solid var(--lw-hairline);
  background: rgba(16, 16, 16, 0.72);
  padding: 1rem 1.25rem;
}

.landing-matrix-cells {
  display: grid;
  gap: 0.5rem;
  list-style: none;
  padding: 0;
  margin: 0;
}

.landing-matrix-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  border-bottom: 1px solid rgba(235, 238, 242, 0.08);
  padding-block: 0.5rem;
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 420ms ease,
    transform 420ms ease;
}

.landing-matrix.is-active .landing-matrix-cell {
  opacity: 1;
  transform: none;
}

.landing-matrix-cell .text-muted-foreground {
  color: var(--lw-faint);
}

.landing-matrix-status {
  font-family: var(--uc-font-data);
  font-size: 0.6875rem;
  font-weight: var(--uc-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.15em;
  color: var(--lw-highlight);
}

.landing-matrix-verdict {
  margin-top: 1rem;
  font-family: var(--uc-font-data);
  font-size: 0.875rem;
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: 0.25em;
  text-transform: uppercase;
  color: var(--lw-highlight);
  opacity: 0;
  transition: opacity 500ms ease;
}

.landing-matrix.is-active .landing-matrix-verdict {
  opacity: 1;
}

.landing-matrix-note {
  margin-top: 0.5rem;
  font-size: 0.6875rem;
  color: var(--lw-faint);
}

/* — growth track: presentation attributes lose to these rules — */
.landing-track {
  display: flex;
  justify-content: center;
}

.landing-track-svg {
  width: 100%;
  max-width: 22rem;
  height: auto;
}

.landing-track-svg path {
  stroke: rgba(235, 238, 242, 0.5);
}

.landing-track-svg circle {
  fill: rgba(235, 238, 242, 0.85);
}

/* — network groups — */
.landing-network-group {
  border: 1px solid var(--lw-hairline);
  background: rgba(16, 16, 16, 0.55);
  padding: 1.75rem;
}

.landing-network-heading {
  font-size: 1.125rem;
  font-weight: var(--uc-font-weight-semibold);
  color: var(--lw-highlight);
}

/* — footer — */
.landing-footer {
  position: relative;
  z-index: 10;
  border-top: 1px solid var(--lw-hairline);
  background: #0a0a0a;
}

.landing-footer .text-muted-foreground {
  color: var(--lw-faint);
}

.landing-footer a:hover {
  color: var(--lw-highlight);
}

/* Reduced motion: instant, readable sections; no travel, no reveal. */
@media (prefers-reduced-motion: reduce) {
  .landing-reveal {
    opacity: 1;
    filter: none;
    transform: none;
    transition: none;
  }

  .landing-matrix-cell,
  .landing-matrix-verdict {
    transition: none;
    opacity: 1;
    transform: none;
  }
}
</style>
