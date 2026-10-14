<script setup lang="ts">
import { useI18n } from "vue-i18n";
import engineeringStillLifeUrl from "@/assets/landing/engineering-still-life.webp";

const { t } = useI18n();
</script>

<template>
  <section
    id="governance"
    class="governance-section"
    aria-labelledby="governance-title"
  >
    <div class="governance-container">
      <div class="control-emblem reveal-on-scroll" aria-hidden="true">⌘</div>

      <h2 id="governance-title" class="section-title reveal-on-scroll">
        {{ t("landing.governance.title") }}
        <em>{{ t("landing.governance.titleItalic") }}</em>
      </h2>
      <p class="section-subtitle reveal-on-scroll">
        {{ t("landing.governance.subtitle") }}
      </p>

      <div class="governance-grid">
        <article class="governance-copy reveal-on-scroll">
          <h3>{{ t("landing.governance.item1Title") }}</h3>
          <p>{{ t("landing.governance.item1Text") }}</p>
          <strong>✓ {{ t("landing.governance.statCaption") }}</strong>
        </article>

        <figure class="governance-image reveal-on-scroll">
          <img
            :src="engineeringStillLifeUrl"
            alt=""
            loading="lazy"
            decoding="async"
          />
        </figure>

        <article class="governance-copy reveal-on-scroll">
          <h3>{{ t("landing.governance.item2Title") }}</h3>
          <p>{{ t("landing.governance.item2Text") }}</p>
          <strong>✓ 100% {{ t("landing.governance.statCaption") }}</strong>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.governance-section {
  padding: 7rem 1.5rem 8rem;
  text-align: center;
}

.governance-container {
  max-width: 1180px;
  margin: 0 auto;
}

.control-emblem {
  width: 72px;
  height: 72px;
  margin: 0 auto 1.5rem;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-delicate);
  border-radius: 50%;
  color: var(--brand-olive);
  font-family: var(--font-serif);
  font-size: 2rem;
  --reveal-delay: 0ms;
}

.section-title {
  margin: 0 0 1.25rem;
  font-family: var(--font-serif);
  font-size: clamp(3rem, 6vw, 5rem);
  font-weight: 400;
  line-height: 0.98;
  letter-spacing: -0.035em;
  --reveal-delay: 80ms;
}

.section-title em {
  display: block;
  font-weight: 400;
}

.section-subtitle {
  max-width: 660px;
  margin: 0 auto 5.5rem;
  color: var(--text-muted);
  font-size: 1rem;
  line-height: 1.55;
  --reveal-delay: 160ms;
}

.governance-grid {
  display: grid;
  grid-template-columns: 1fr 1.1fr 1fr;
  gap: 3.5rem;
  align-items: center;
}

.governance-copy {
  text-align: center;
}

.governance-grid > :nth-child(1) {
  --reveal-delay: 240ms;
}

.governance-grid > :nth-child(2) {
  --reveal-delay: 360ms;
}

.governance-grid > :nth-child(3) {
  --reveal-delay: 480ms;
}

.governance-copy h3 {
  margin: 0 0 1.25rem;
  font-family: var(--font-serif);
  font-size: 2rem;
  font-weight: 400;
  line-height: 1.12;
}

.governance-copy p {
  margin: 0 0 2rem;
  color: var(--text-muted);
  font-size: 0.92rem;
  line-height: 1.65;
}

.governance-copy strong {
  color: var(--brand-olive);
  font-size: 0.82rem;
}

.governance-image {
  margin: 0;
  padding: 1rem;
  background: var(--bg-card);
  border-radius: var(--radius-panel);
  overflow: hidden;
  transform: rotate(-2deg);
}

.governance-image img {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: block;
  object-fit: cover;
  border-radius: var(--radius-card);
  filter: saturate(0.78) blur(0.3px);
}

@media (max-width: 900px) {
  .governance-grid {
    grid-template-columns: 1fr;
    gap: 2.5rem;
  }
  .governance-image {
    order: 2;
    max-width: 420px;
    margin: 0 auto;
  }
  .governance-copy:last-child {
    order: 3;
  }
}

@media (max-width: 768px) {
  .governance-section {
    padding: 4.5rem 1rem 5rem;
  }
  .section-title {
    font-size: 3.15rem;
  }
  .section-subtitle {
    margin-bottom: 3rem;
  }
  .governance-copy h3 {
    font-size: 1.8rem;
  }
}
</style>
