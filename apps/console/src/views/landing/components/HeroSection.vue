<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";
import foliageLeftUrl from "../../../assets/landing/foliage-left.png";
import foliageRightUrl from "../../../assets/landing/foliage-right.png";

const { t } = useI18n();
const heroSectionRef = ref<HTMLElement | null>(null);
const heroTitleLetters = computed(() => Array.from(t("landing.hero.title")));
const heroTitleAccentLetters = computed(() =>
  Array.from(t("landing.hero.titleItalic")),
);
let foliageFrame: number | null = null;

const updateFoliageParallax = () => {
  foliageFrame = null;
  const section = heroSectionRef.value;
  if (!section) return;

  const shift = Math.min(Math.max(window.scrollY, 0) * 0.1, 100);
  section.style.setProperty("--foliage-left-y", `${shift}px`);
  section.style.setProperty("--foliage-right-y", `${-shift}px`);
};

const scheduleFoliageParallax = () => {
  if (foliageFrame !== null) return;
  foliageFrame = window.requestAnimationFrame(updateFoliageParallax);
};

onMounted(() => {
  const prefersReducedMotion =
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (prefersReducedMotion) return;

  updateFoliageParallax();
  window.addEventListener("scroll", scheduleFoliageParallax, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", scheduleFoliageParallax);
  if (foliageFrame !== null) {
    window.cancelAnimationFrame(foliageFrame);
  }
});
</script>

<template>
  <section
    id="proof"
    ref="heroSectionRef"
    class="hero-section"
    aria-labelledby="hero-title"
  >
    <svg
      class="hero-wind-defs"
      width="0"
      height="0"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <filter id="landing-wind" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence
            type="fractalNoise"
            baseFrequency="0.008 0.02"
            numOctaves="2"
            seed="7"
            result="noise"
          >
            <animate
              attributeName="baseFrequency"
              values="0.008 0.02;0.012 0.03;0.008 0.02"
              dur="8s"
              repeatCount="indefinite"
            />
          </feTurbulence>
          <feDisplacementMap
            in="SourceGraphic"
            in2="noise"
            scale="8"
            xChannelSelector="R"
            yChannelSelector="G"
          />
        </filter>
      </defs>
    </svg>
    <img
      :src="foliageLeftUrl"
      class="hero-foliage hero-foliage-left"
      alt=""
      aria-hidden="true"
      draggable="false"
    />
    <img
      :src="foliageRightUrl"
      class="hero-foliage hero-foliage-right"
      alt=""
      aria-hidden="true"
      draggable="false"
    />

    <div class="hero-copy">
      <div class="institutional-badge">
        <span class="badge-dot" aria-hidden="true" />
        <span>{{ t("landing.hero.badge") }}</span>
      </div>

      <div class="headline-editorial-wrap">
        <span class="editorial-bracket" aria-hidden="true">(</span>
        <h1
          id="hero-title"
          class="hero-headline"
          :aria-label="`${t('landing.hero.title')} ${t('landing.hero.titleItalic')}`"
        >
          <span class="hero-headline-copy" aria-hidden="true">
            <span
              v-for="(letter, index) in heroTitleLetters"
              :key="`hero-title-${index}`"
              class="hero-letter"
              :style="{ animationDelay: `${0.12 + index * 0.02}s` }"
              >{{ letter }}</span
            >
          </span>
          <span class="hero-headline-gap" aria-hidden="true">&nbsp;</span>
          <span class="hero-headline-accent" aria-hidden="true">
            <span
              v-for="(letter, index) in heroTitleAccentLetters"
              :key="`hero-title-accent-${index}`"
              class="hero-letter"
              :style="{
                animationDelay: `${
                  0.12 + (heroTitleLetters.length + index) * 0.02
                }s`,
              }"
              >{{ letter }}</span
            >
          </span>
        </h1>
        <span class="editorial-bracket" aria-hidden="true">)</span>
      </div>

      <p class="hero-description">{{ t("landing.hero.description") }}</p>

      <RouterLink to="/problemset" class="primary-cta-btn">
        {{ t("landing.hero.ctaPrimary") }}
      </RouterLink>
    </div>

    <div class="hero-showcase">
      <div class="showcase-atmosphere" aria-hidden="true" />

      <div class="product-window-frame">
        <div class="product-window-panel">
          <div class="window-header">
            <div class="window-controls" aria-hidden="true">
              <span class="win-dot" />
              <span class="win-dot" />
              <span class="win-dot" />
            </div>
            <div class="window-title">
              {{ t("landing.hero.sampleWindowTag") }}
            </div>
            <div class="window-status-pill">
              {{ t("landing.hero.sampleStatus") }}
            </div>
          </div>

          <div class="window-body">
            <aside
              class="window-sidebar"
              :aria-label="t('landing.nav.workflow')"
            >
              <strong>#146</strong>
              <span>Overview</span>
              <span>Source</span>
              <span>Testcases</span>
              <span>Telemetry</span>
            </aside>

            <div class="window-main">
              <div class="execution-pipeline-bar">
                <div class="pipe-node is-done"><span>01</span> AST</div>
                <i aria-hidden="true">→</i>
                <div class="pipe-node is-done"><span>02</span> SANDBOX</div>
                <i aria-hidden="true">→</i>
                <div class="pipe-node is-done"><span>03</span> VERDICT</div>
              </div>

              <div class="window-content">
                <div class="code-gutter" aria-hidden="true">
                  <span>01</span><span>02</span><span>03</span><span>04</span
                  ><span>05</span>
                </div>
                <pre
                  class="code-body"
                ><code><span class="c-kw">template</span>&lt;<span class="c-kw">typename</span> Key, <span class="c-kw">typename</span> Val&gt;
<span class="c-kw">class</span> <span class="c-type">LRUCache</span> {
    <span class="c-type">size_t</span> capacity_;
    std::list&lt;std::pair&lt;Key, Val&gt;&gt; items_;
    std::unordered_map&lt;Key, iterator&gt; index_;
};</code></pre>
              </div>

              <div class="window-footer">
                <span
                  >{{ t("landing.hero.peakMemory") }}:
                  <strong>3,840 KiB</strong></span
                >
                <span
                  >{{ t("landing.hero.seccompFilter") }}:
                  <strong>STRICT</strong></span
                >
                <span
                  >{{ t("landing.hero.testsPassed") }}:
                  <strong>48 / 48</strong></span
                >
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="showcase-actions">
        <p>
          <strong>{{ t("landing.proof.submissionTitle") }}</strong>
          <span>{{ t("landing.hero.sampleFootnote") }}</span>
        </p>
        <div>
          <RouterLink to="/problemset" class="showcase-primary">
            {{ t("landing.proof.workspaceCta") }}
          </RouterLink>
          <RouterLink to="/contest" class="showcase-secondary">
            {{ t("landing.useCases.case2Action") }}
          </RouterLink>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero-section {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4.5rem;
  padding: 0 var(--uc-layout-page-gutter) 5.5rem;
  overflow: clip;
}

.hero-copy {
  position: relative;
  z-index: 6;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  width: 100%;
  max-width: 1200px;
  min-height: 419px;
  margin: 0 auto;
  padding-top: 5rem;
  text-align: center;
}

.institutional-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.35rem 0.8rem;
  background: var(--bg-card);
  border: 1px solid var(--border-delicate);
  border-radius: var(--radius-small);
  color: var(--text-muted);
  font-size: 0.78rem;
  margin-bottom: 0;
  animation: hero-soft-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) 0.12s both;
}

.badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #588e67;
}

.headline-editorial-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(1.25rem, 7.8vw, 6.4rem);
  width: 100%;
  margin-bottom: 0;
}

.editorial-bracket {
  font-family: var(--font-serif);
  font-size: clamp(4rem, 7.2vw, 6rem);
  font-weight: 400;
  line-height: 1.1;
  color: var(--text-primary);
  animation: hero-soft-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) 0.18s both;
}

.hero-headline {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(3.5rem, 6vw, 5rem);
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: var(--uc-locale-display-tracking);
  white-space: nowrap;
  color: var(--text-primary);
}

.hero-letter {
  display: inline-block;
  white-space: pre;
  opacity: 0;
  filter: blur(10px);
  transform: translateY(10px);
  will-change: opacity, transform, filter;
  animation: hero-letter-in 0.2s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.hero-headline-accent {
  font-style: italic;
  font-weight: 400;
}

:global(html[lang="zh-CN"] .headline-editorial-wrap) {
  gap: clamp(1.5rem, 5.2vw, 4.5rem);
}

:global(html[lang="zh-CN"] .editorial-bracket) {
  font-size: clamp(3.75rem, 6.4vw, 5.25rem);
}

:global(html[lang="zh-CN"] .hero-headline) {
  font-size: clamp(3.75rem, 5.2vw, 4.5rem);
  line-height: 1.18;
  letter-spacing: var(--uc-locale-display-tracking);
}

:global(html[lang="en-US"] .headline-editorial-wrap) {
  gap: clamp(1.5rem, 6.8vw, 5.5rem);
}

:global(html[lang="en-US"] .editorial-bracket) {
  font-size: clamp(4rem, 7vw, 5.75rem);
}

:global(html[lang="en-US"] .hero-headline) {
  font-size: clamp(4rem, 6.2vw, 5.25rem);
  line-height: 1.08;
  letter-spacing: var(--uc-locale-display-tracking);
}

.hero-description {
  max-width: 36rem;
  min-height: 4.375rem;
  margin: 0 auto;
  color: var(--text-muted);
  font-size: 1.125rem;
  line-height: 1.3;
  animation: hero-soft-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) 0.18s both;
}

.primary-cta-btn,
.showcase-primary,
.showcase-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  transition:
    transform 0.2s ease,
    background-color 0.2s ease;
}

.primary-cta-btn,
.showcase-primary {
  background: var(--brand-olive);
  color: #fff;
  padding: 0.85rem 1.65rem;
  border-radius: var(--radius-control);
}

.primary-cta-btn {
  min-height: 58px;
  animation: hero-fade-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) 0.24s both;
}

.primary-cta-btn:hover,
.showcase-primary:hover {
  background: var(--brand-olive-hover);
  transform: translateY(-1px);
}

.hero-showcase {
  position: relative;
  z-index: 3;
  width: 100%;
  max-width: 1380px;
  min-height: 787px;
  margin: 0 auto;
  padding: 5.75rem 10% 2.25rem;
  border-radius: var(--radius-panel);
  overflow: clip;
  background: var(--bg-sky);
  animation: hero-stage-in 0.65s cubic-bezier(0.22, 1, 0.36, 1) 0.3s both;
}

.hero-wind-defs {
  position: absolute;
  overflow: hidden;
  pointer-events: none;
}

.hero-foliage {
  position: absolute;
  z-index: 4;
  object-fit: contain;
  filter: url("#landing-wind") saturate(0.84) contrast(1.08);
  opacity: 0.98;
  pointer-events: none;
  user-select: none;
  transition: transform 80ms linear;
  will-change: transform, filter;
}

.hero-foliage-left {
  top: 10rem;
  left: -0.125rem;
  width: clamp(300px, 28vw, 365px);
  height: clamp(650px, 58vw, 770px);
  object-position: left top;
  transform: translate3d(0, var(--foliage-left-y, 0px), 0);
}

.hero-foliage-right {
  top: 12rem;
  right: 0.25rem;
  width: clamp(230px, 22vw, 17.7rem);
  height: clamp(760px, 75vw, 990px);
  object-position: right bottom;
  transform: translate3d(0, var(--foliage-right-y, 0px), 0);
}

.showcase-atmosphere {
  position: absolute;
  inset: -1rem;
  z-index: 0;
  background-image: url("../../../assets/landing/algorithmic-horizon.webp");
  background-position: center;
  background-size: cover;
  opacity: 0.46;
  filter: blur(1.2px) saturate(0.76);
  transform: scale(1.02);
  pointer-events: none;
}

.product-window-frame,
.product-window-panel,
.showcase-actions {
  position: relative;
  z-index: 5;
}

.product-window-frame {
  width: 100%;
  max-width: 900px;
  height: 601px;
  margin: -2.625rem auto 0;
  padding: 2.4375rem 0.625rem 0.625rem;
  border-radius: var(--radius-card);
  background: rgba(255, 255, 255, 0.2);
  animation: hero-demo-in 0.7s cubic-bezier(0.22, 1, 0.36, 1) 0.55s both;
}

.product-window-panel {
  max-width: 880px;
  margin: 0 auto;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-card);
  box-shadow: 0 28px 70px rgba(32, 51, 37, 0.2);
}

.window-header {
  min-height: 56px;
  padding: 0.75rem 1.25rem;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  background: rgba(239, 236, 225, 0.95);
  border-bottom: 1px solid var(--border-delicate);
}

.window-controls {
  display: flex;
  gap: 0.35rem;
}

.win-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0bcb0;
}

.window-title,
.window-status-pill,
.window-sidebar,
.execution-pipeline-bar,
.window-content,
.window-footer {
  font-family: var(--font-mono);
}

.window-title {
  color: var(--text-muted);
  font-size: 0.76rem;
}

.window-status-pill {
  justify-self: end;
  padding: 0.25rem 0.55rem;
  border-radius: var(--radius-small);
  background: #e2e8d0;
  color: #3f683b;
  font-size: 0.7rem;
  font-weight: 600;
  animation: demo-status-in 0.4s linear 1.36s both;
}

.window-body {
  display: grid;
  grid-template-columns: 170px minmax(0, 1fr);
  min-height: 494px;
}

.window-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem 1rem;
  background: #f4f2ea;
  border-right: 1px solid var(--border-delicate);
  color: var(--text-dim);
  font-size: 0.74rem;
}

.window-sidebar strong {
  color: var(--text-primary);
  margin-bottom: 0.5rem;
}

.execution-pipeline-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 1rem;
  border-bottom: 1px solid var(--border-delicate);
  color: var(--text-muted);
  font-size: 0.72rem;
}

.pipe-node {
  display: flex;
  gap: 0.35rem;
  align-items: center;
  opacity: 0;
  animation: demo-step-in 0.4s cubic-bezier(0.22, 1.25, 0.36, 1) both;
}

.pipe-node span {
  padding: 0.1rem 0.35rem;
  background: var(--brand-olive);
  color: #fff;
}

.execution-pipeline-bar i {
  color: var(--text-dim);
  font-style: normal;
  opacity: 0;
  animation: demo-step-in 0.4s cubic-bezier(0.22, 1.25, 0.36, 1) both;
}

.execution-pipeline-bar > :nth-child(1) {
  animation-delay: 0.82s;
}

.execution-pipeline-bar > :nth-child(2) {
  animation-delay: 0.94s;
}

.execution-pipeline-bar > :nth-child(3) {
  animation-delay: 1.06s;
}

.execution-pipeline-bar > :nth-child(4) {
  animation-delay: 1.18s;
}

.execution-pipeline-bar > :nth-child(5) {
  animation-delay: 1.3s;
}

.window-content {
  display: flex;
  min-height: 285px;
  padding: 1.5rem;
  background: #faf9f5;
  color: var(--text-primary);
  font-size: 0.85rem;
  line-height: 1.65;
}

.code-gutter {
  display: flex;
  flex-direction: column;
  padding-right: 1rem;
  color: var(--text-dim);
  opacity: 0.65;
}

.code-body {
  margin: 0;
  overflow-x: auto;
}

.c-kw {
  color: #8f4822;
  font-weight: 600;
}

.c-type {
  color: #6e5b28;
}

.window-footer {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.9rem 1.25rem;
  background: #f2efe7;
  border-top: 1px solid var(--border-delicate);
  color: var(--text-muted);
  font-size: 0.7rem;
}

.window-footer span {
  opacity: 0;
  animation: demo-status-in 0.4s linear both;
}

.window-footer span:nth-child(1) {
  animation-delay: 1.45s;
}

.window-footer span:nth-child(2) {
  animation-delay: 1.6s;
}

.window-footer span:nth-child(3) {
  animation-delay: 1.75s;
}

.showcase-actions {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 2rem;
  max-width: 980px;
  margin: 2rem auto 0;
  animation: hero-soft-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) 0.82s both;
}

.showcase-actions p {
  display: grid;
  gap: 0.3rem;
  margin: 0;
  color: var(--text-primary);
}

.showcase-actions p span {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.showcase-actions > div {
  display: flex;
  gap: 0.75rem;
}

.showcase-primary,
.showcase-secondary {
  min-height: 42px;
  padding: 0.65rem 1.2rem;
  border-radius: var(--radius-control);
  font-size: 0.84rem;
}

.showcase-secondary {
  background: var(--bg-card);
  color: var(--brand-olive);
}

@keyframes hero-soft-in {
  from {
    opacity: 0;
    transform: translateY(8px);
    filter: blur(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@keyframes hero-fade-in {
  from {
    opacity: 0;
    filter: blur(4px);
  }
  to {
    opacity: 1;
    filter: blur(0);
  }
}

@keyframes hero-letter-in {
  from {
    opacity: 0;
    transform: translateY(10px);
    filter: blur(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@keyframes hero-stage-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.995);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes hero-demo-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes demo-step-in {
  from {
    opacity: 0;
    transform: translateY(4px) scale(0.97);
  }
  72% {
    opacity: 1;
    transform: translateY(0) scale(1.015);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes demo-status-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
  72% {
    opacity: 1;
    transform: scale(1.015);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 900px) {
  .hero-copy {
    min-height: auto;
  }
  .headline-editorial-wrap {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0;
  }
  .editorial-bracket {
    display: none;
  }
  .hero-headline {
    white-space: normal;
  }
  .hero-showcase {
    min-height: 640px;
    padding: 2rem 1rem;
  }
  .product-window-frame {
    height: auto;
    margin: 0 auto;
    padding: 0.625rem;
  }
  .window-body {
    grid-template-columns: 1fr;
  }
  .window-sidebar {
    display: none;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 2rem 0.75rem 3.5rem;
  }
  .hero-copy {
    margin-bottom: 0;
  }
  .institutional-badge {
    margin-bottom: 0;
    font-size: 0.68rem;
  }
  .headline-editorial-wrap {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .editorial-bracket {
    display: none;
  }
  .hero-headline {
    font-size: 3.35rem;
    line-height: 0.98;
  }
  .hero-description {
    min-height: auto;
    padding: 0 0.5rem;
    font-size: 0.95rem;
  }
  .hero-showcase {
    min-height: auto;
    border-radius: var(--radius-card);
  }
  .showcase-atmosphere {
    opacity: 0.52;
  }
  .hero-foliage {
    width: 240px;
    height: 540px;
    opacity: 0.45;
  }
  .hero-foliage-left {
    left: -7rem;
  }
  .hero-foliage-right {
    top: 12rem;
    right: -7rem;
  }
  .product-window-panel {
    border-radius: var(--radius-card);
  }
  .window-header {
    grid-template-columns: auto 1fr;
    gap: 0.6rem;
  }
  .window-title {
    text-align: right;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .window-status-pill {
    grid-column: 1 / -1;
    justify-self: stretch;
    text-align: center;
  }
  .execution-pipeline-bar {
    gap: 0.35rem;
    font-size: 0.58rem;
  }
  .window-content {
    min-height: 235px;
    padding: 1rem;
    font-size: 0.67rem;
  }
  .window-footer {
    flex-direction: column;
  }
  .showcase-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .showcase-actions > div {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-wind-defs {
    display: none;
  }
  .institutional-badge,
  .editorial-bracket,
  .hero-headline,
  .hero-letter,
  .hero-description,
  .primary-cta-btn,
  .hero-showcase,
  .product-window-frame,
  .product-window-panel,
  .showcase-actions,
  .window-status-pill,
  .pipe-node,
  .execution-pipeline-bar i,
  .window-footer span {
    opacity: 1 !important;
    transform: none !important;
    filter: none !important;
    animation: none !important;
  }
  .hero-foliage {
    transform: none !important;
    filter: none !important;
    transition: none !important;
    will-change: auto;
  }
}
</style>
