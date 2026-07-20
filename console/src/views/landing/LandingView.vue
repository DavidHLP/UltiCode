<script setup lang="ts">
/**
 * LandingView — public `/` route. A single continuous 3D micro-narrative:
 * one code core travels through parse → judge → growth → network as the user
 * scrolls. All narrative content is plain DOM; the canvas is a progressive
 * enhancement layered underneath.
 */
import { useI18n } from "vue-i18n";
import CodeCoreCanvas from "./components/CodeCoreCanvas.vue";
import LandingNav from "./components/LandingNav.vue";
import HeroSection from "./sections/HeroSection.vue";
import ParseSection from "./sections/ParseSection.vue";
import MatrixSection from "./sections/MatrixSection.vue";
import GrowthSection from "./sections/GrowthSection.vue";
import NetworkSection from "./sections/NetworkSection.vue";
import FinaleSection from "./sections/FinaleSection.vue";

const { t } = useI18n();

defineOptions({ name: "LandingView" });
</script>

<template>
  <div class="landing-root">
    <a href="#landing-main" class="landing-skip">
      {{ t("common.skipToContent") }}
    </a>
    <CodeCoreCanvas />
    <LandingNav />
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
 * Shared landing idiom — intentionally un-scoped so the six chapter sections
 * stay thin. Hairline rails, big measure-controlled type, and zero card
 * chrome: the 3D layer is the only protagonist.
 */
.landing-root {
  position: relative;
  min-height: 100vh;
  background: var(--background);
  color: var(--foreground);
}

.landing-skip {
  position: absolute;
  left: 1rem;
  top: -3rem;
  z-index: 40;
  background: var(--primary);
  color: var(--primary-foreground);
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  transition: top 150ms ease;
}

.landing-skip:focus-visible {
  top: 1rem;
}

.landing-section {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 100vh;
  padding-block: 6rem;
}

@media (min-width: 1024px) {
  .landing-section {
    min-height: 130vh;
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
  border-inline-start: 1px solid
    color-mix(in srgb, var(--solarized-cyan) 45%, transparent);
  padding-inline-start: 1.5rem;
  background: color-mix(in srgb, var(--background) 55%, transparent);
}

.landing-block.text-center,
.landing-block.mx-auto {
  border-inline-start: none;
  padding-inline-start: 0;
  background: transparent;
}

.landing-eyebrow {
  font-family: var(--uc-font-data);
  font-size: var(--uc-text-2xs, 0.625rem);
  font-weight: var(--uc-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.3em;
  color: var(--solarized-cyan);
  margin-bottom: 1rem;
}

.landing-title {
  font-size: clamp(2.25rem, 5.5vw, 3.75rem);
  font-weight: var(--uc-font-weight-bold);
  line-height: 1.15;
  letter-spacing: -0.01em;
  text-wrap: balance;
}

.landing-heading {
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: var(--uc-font-weight-bold);
  line-height: 1.2;
  text-wrap: balance;
}

.landing-body {
  margin-top: 1.25rem;
  max-width: 36rem;
  font-size: 1.0625rem;
  line-height: 1.75;
  color: var(--muted-foreground);
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
  color: var(--foreground);
}

.landing-points li::before {
  content: "";
  position: absolute;
  inset-inline-start: 0;
  top: 0.55em;
  width: 0.5rem;
  height: 1px;
  background: var(--solarized-cyan);
}

/* Editor fragment (parse chapter) */
.landing-editor {
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--card) 88%, transparent);
}

.landing-editor-bar {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  border-bottom: 1px solid var(--border);
  padding: 0.625rem 0.875rem;
}

.landing-editor-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 9999px;
  background: var(--muted-foreground);
  opacity: 0.4;
}

.landing-editor-file {
  margin-inline-start: 0.5rem;
  font-family: var(--uc-font-data);
  font-size: 0.6875rem;
  color: var(--muted-foreground);
}

.landing-editor-code {
  padding: 1rem 1.25rem;
  font-family: var(--uc-font-code);
  font-size: 0.8125rem;
  line-height: 1.7;
  overflow-x: auto;
  color: var(--foreground);
}

/* Judge matrix (judge chapter) */
.landing-matrix {
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--card) 88%, transparent);
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
  border-bottom: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
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

.landing-matrix-status {
  font-family: var(--uc-font-data);
  font-size: 0.6875rem;
  font-weight: var(--uc-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.15em;
  color: var(--solarized-green);
}

.landing-matrix-verdict {
  margin-top: 1rem;
  font-family: var(--uc-font-data);
  font-size: 0.875rem;
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--solarized-green);
  opacity: 0;
  transition: opacity 500ms ease;
}

.landing-matrix.is-active .landing-matrix-verdict {
  opacity: 1;
}

.landing-matrix-note {
  margin-top: 0.5rem;
  font-size: 0.6875rem;
  color: var(--muted-foreground);
}

/* Growth track */
.landing-track {
  display: flex;
  justify-content: center;
}

.landing-track-svg {
  width: 100%;
  max-width: 22rem;
  height: auto;
}

/* Network groups */
.landing-network-group {
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--background) 60%, transparent);
  padding: 1.75rem;
}

.landing-network-heading {
  font-size: 1.125rem;
  font-weight: var(--uc-font-weight-semibold);
}

/* Footer */
.landing-footer {
  position: relative;
  z-index: 10;
  border-top: 1px solid var(--border);
  background: var(--background);
}

/* Reduced motion: chapters still read as a sequence, just without travel. */
@media (prefers-reduced-motion: reduce) {
  .landing-matrix-cell,
  .landing-matrix-verdict {
    transition: none;
    opacity: 1;
    transform: none;
  }
}
</style>
