<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";

const { t } = useI18n();
</script>

<template>
  <section
    id="use-cases"
    class="use-cases-section"
    aria-labelledby="use-cases-title"
  >
    <div class="use-cases-container">
      <div class="section-badge reveal-on-scroll">
        {{ t("landing.useCases.badge") }}
      </div>
      <h2 id="use-cases-title" class="section-title reveal-on-scroll">
        {{ t("landing.useCases.title")
        }}<em class="section-title-em">{{
          t("landing.useCases.titleItalic")
        }}</em>
      </h2>
      <p class="section-subtitle reveal-on-scroll">
        {{ t("landing.useCases.subtitle") }}
      </p>

      <div class="scenarios-grid">
        <!-- 场景 1 -->
        <article class="scenario-card reveal-on-scroll">
          <div class="scenario-header">
            <span class="scenario-icon">01</span>
            <h3 class="scenario-name">
              {{ t("landing.useCases.case1Title") }}
            </h3>
          </div>
          <div class="scenario-ui-snip">
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case1Metric1")
              }}</span>
            </div>
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case1Metric2")
              }}</span>
            </div>
          </div>
          <p class="scenario-desc">
            {{ t("landing.useCases.case1Desc") }}
          </p>
          <div class="scenario-action-wrap">
            <RouterLink to="/problemset" class="scenario-action-link">
              {{ t("landing.useCases.case1Action") }}
            </RouterLink>
          </div>
        </article>

        <!-- 场景 2 -->
        <article class="scenario-card reveal-on-scroll">
          <div class="scenario-header">
            <span class="scenario-icon">02</span>
            <h3 class="scenario-name">
              {{ t("landing.useCases.case2Title") }}
            </h3>
          </div>
          <div class="scenario-ui-snip">
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case2Metric1")
              }}</span>
            </div>
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case2Metric2")
              }}</span>
            </div>
          </div>
          <p class="scenario-desc">
            {{ t("landing.useCases.case2Desc") }}
          </p>
          <div class="scenario-action-wrap">
            <RouterLink to="/contest" class="scenario-action-link">
              {{ t("landing.useCases.case2Action") }}
            </RouterLink>
          </div>
        </article>

        <!-- 场景 3 -->
        <article class="scenario-card reveal-on-scroll">
          <div class="scenario-header">
            <span class="scenario-icon">03</span>
            <h3 class="scenario-name">
              {{ t("landing.useCases.case3Title") }}
            </h3>
          </div>
          <div class="scenario-ui-snip">
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case3Metric1")
              }}</span>
            </div>
            <div class="snip-row">
              <span class="snip-label">{{
                t("landing.useCases.case3Metric2")
              }}</span>
            </div>
          </div>
          <p class="scenario-desc">
            {{ t("landing.useCases.case3Desc") }}
          </p>
          <div class="scenario-action-wrap">
            <RouterLink to="/problemset" class="scenario-action-link">
              {{ t("landing.useCases.case3Action") }}
            </RouterLink>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.use-cases-section {
  position: relative;
  padding: 6rem 1.5rem;
  background-color: var(--bg-sky);
  border-top: 1px solid rgba(84, 92, 69, 0.15);
  border-bottom: 1px solid rgba(84, 92, 69, 0.15);
  overflow: hidden;
}

.use-cases-section::before {
  content: "";
  position: absolute;
  inset: -1rem;
  background-image: url("../../../assets/landing/algorithmic-garden.webp");
  background-position: center;
  background-size: cover;
  opacity: 0.28;
  filter: blur(1px) saturate(0.75);
  transform: scale(1.02);
  pointer-events: none;
}

.use-cases-container {
  position: relative;
  z-index: 1;
  max-width: 1080px;
  margin: 0 auto;
  text-align: center;
}

.section-badge {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--brand-olive);
  letter-spacing: 0.15rem;
  margin-bottom: 1rem;
}

.section-title {
  font-family: var(--font-serif);
  font-size: 2.75rem;
  margin-bottom: 1rem;
}

.section-title-em {
  font-style: italic;
}

.section-subtitle {
  font-size: 1.05rem;
  color: #2d3a24;
  margin-bottom: 3.5rem;
}

.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2rem;
  text-align: left;
}

.scenario-card {
  background-color: var(--bg-card);
  border: 1px solid rgba(84, 92, 69, 0.2);
  border-radius: var(--radius-card);
  padding: 2rem 1.5rem;
  box-shadow: var(--shadow-subtle);
  display: flex;
  flex-direction: column;
  min-height: 360px;
}

.scenario-header {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}

.scenario-icon {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--brand-olive);
}

.scenario-name {
  font-family: var(--font-serif);
  font-size: 1.2rem;
  margin: 0;
}

.scenario-ui-snip {
  background-color: #eeece1;
  padding: 0.75rem;
  border-radius: var(--radius-control);
  font-family: var(--font-mono);
  font-size: 0.75rem;
  margin-bottom: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.snip-row {
  display: flex;
  justify-content: space-between;
}

.snip-value {
  font-weight: 600;
  color: #8c6228;
}

.scenario-desc {
  font-size: 0.9rem;
  color: var(--text-muted);
  line-height: 1.6;
  flex: 1;
}

.scenario-action-wrap {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px dashed var(--border-delicate);
}

.scenario-action-link {
  font-size: 0.85rem;
  color: var(--brand-olive);
  text-decoration: none;
  font-weight: 600;
  transition: all 0.2s ease;
}

.scenario-action-link:hover {
  color: var(--brand-olive-hover);
  text-decoration: underline;
}

@media (max-width: 900px) {
  .scenarios-grid {
    grid-template-columns: 1fr;
    gap: 1.25rem;
  }
}

@media (max-width: 768px) {
  .use-cases-section {
    padding: 2.5rem 1rem;
  }
  .section-title {
    font-size: 2.1rem;
  }
  .section-subtitle {
    margin-bottom: 1.75rem;
  }
  .scenario-card {
    padding: 1.25rem 1rem;
  }
  .scenario-action-wrap {
    margin-top: 1rem;
    padding-top: 0.75rem;
  }
}
</style>
