<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";

const { t } = useI18n();
</script>

<template>
  <section id="proof" class="hero-section" aria-labelledby="hero-title">
    <div class="hero-copy">
      <div class="institutional-badge">
        <span class="badge-dot" aria-hidden="true" />
        <span>{{ t("landing.hero.badge") }}</span>
      </div>

      <div class="headline-editorial-wrap">
        <span class="editorial-bracket" aria-hidden="true">(</span>
        <h1 id="hero-title" class="hero-headline">
          {{ t("landing.hero.title") }}
          <span class="hero-headline-accent">{{
            t("landing.hero.titleItalic")
          }}</span>
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
          <aside class="window-sidebar" :aria-label="t('landing.nav.workflow')">
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
  padding: 4rem 1.25rem 5.5rem;
  overflow: hidden;
}

.hero-copy {
  max-width: 900px;
  margin: 0 auto 4rem;
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
  margin-bottom: 2rem;
}

.badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #588e67;
}

.headline-editorial-wrap {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 2rem;
  margin-bottom: 1.75rem;
}

.editorial-bracket {
  font-family: var(--font-serif);
  font-size: clamp(4rem, 8vw, 7rem);
  font-weight: 400;
  line-height: 1;
  color: var(--text-primary);
}

.hero-headline {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(3.5rem, 7.2vw, 6.7rem);
  font-weight: 400;
  line-height: 0.95;
  letter-spacing: -0.045em;
  color: var(--text-primary);
}

.hero-headline-accent {
  font-style: italic;
  font-weight: 400;
}

.hero-description {
  max-width: 620px;
  margin: 0 auto 2rem;
  color: var(--text-muted);
  font-size: 1.05rem;
  line-height: 1.55;
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

.primary-cta-btn:hover,
.showcase-primary:hover {
  background: var(--brand-olive-hover);
  transform: translateY(-1px);
}

.hero-showcase {
  position: relative;
  max-width: 1380px;
  min-height: 760px;
  margin: 0 auto;
  padding: 4rem 10% 2.25rem;
  border-radius: var(--radius-panel);
  overflow: hidden;
  background: var(--bg-sky);
}

.showcase-atmosphere {
  position: absolute;
  inset: -1rem;
  background-image: url("../../../assets/landing/algorithmic-garden.webp");
  background-position: center;
  background-size: cover;
  opacity: 0.66;
  filter: blur(0.8px) saturate(0.82);
  transform: scale(1.02);
  pointer-events: none;
}

.product-window-panel,
.showcase-actions {
  position: relative;
  z-index: 1;
}

.product-window-panel {
  max-width: 980px;
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
}

.window-body {
  display: grid;
  grid-template-columns: 170px minmax(0, 1fr);
  min-height: 430px;
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
}

.pipe-node span {
  padding: 0.1rem 0.35rem;
  background: var(--brand-olive);
  color: #fff;
}

.execution-pipeline-bar i {
  color: var(--text-dim);
  font-style: normal;
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

.showcase-actions {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 2rem;
  max-width: 980px;
  margin: 2rem auto 0;
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

@media (max-width: 900px) {
  .hero-showcase {
    min-height: 640px;
    padding: 2rem 1rem;
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
    margin-bottom: 2.5rem;
  }
  .institutional-badge {
    margin-bottom: 1.25rem;
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
</style>
