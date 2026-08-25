<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";

const { t } = useI18n();
</script>

<template>
  <section class="final-story-section" aria-labelledby="final-title">
    <div class="landscape-backdrop" aria-hidden="true" />
    <div class="final-content-container reveal-on-scroll">
      <h2 id="final-title" class="final-headline">
        {{ t("landing.finalStory.headline") }}<br />
        <span class="serif-italic">{{
          t("landing.finalStory.headlineItalic")
        }}</span>
      </h2>

      <p class="final-subtext">
        {{ t("landing.finalStory.subtext") }}
      </p>

      <div class="final-cta-block">
        <RouterLink to="/problemset" class="large-cta-btn">
          {{ t("landing.finalStory.ctaPrimary") }}
        </RouterLink>
        <RouterLink to="/login" class="sub-cta-link">
          {{ t("landing.finalStory.ctaSecondary") }}
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.final-story-section {
  position: relative;
  min-height: 820px;
  padding: 8rem 1.5rem 20rem;
  text-align: center;
  background-color: #e3e1d1;
  color: #19220e;
  overflow: hidden;
}

.final-story-section::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to bottom,
    rgba(227, 225, 209, 0.35),
    rgba(227, 225, 209, 0.1) 54%,
    rgba(32, 51, 37, 0.2)
  );
  pointer-events: none;
}

.landscape-backdrop {
  position: absolute;
  inset: -1.5rem;
  background-image: url("../../../assets/landing/algorithmic-horizon.webp");
  background-position: center;
  background-size: cover;
  opacity: 0.92;
  filter: blur(0.45px) saturate(0.8);
  transform: scale(1.025);
  pointer-events: none;
}

.final-content-container {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
}

.final-headline {
  font-family: var(--font-serif);
  font-size: 3.5rem;
  line-height: 1.2;
  font-weight: 400;
  margin-bottom: 1.5rem;
}

.serif-italic {
  font-style: italic;
  color: #38402f;
}

.final-subtext {
  font-size: 1.15rem;
  color: #59614c;
  margin-bottom: 3.5rem;
  line-height: 1.7;
}

.final-cta-block {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.25rem;
}

.large-cta-btn {
  background-color: #38402f;
  color: #ffffff;
  padding: 1rem 2.75rem;
  font-size: 1.05rem;
  font-weight: 600;
  text-decoration: none;
  border-radius: var(--radius-control);
  transition: all 0.2s ease;
}

.large-cta-btn:hover {
  background-color: #203325;
  transform: translateY(-1px);
}

.sub-cta-link {
  color: #38402f;
  font-size: 0.9rem;
  text-decoration: none;
  transition: color 0.2s ease;
}

.sub-cta-link:hover {
  color: #203325;
}

@media (max-width: 768px) {
  .final-story-section {
    min-height: 680px;
    padding: 4.5rem 1rem 15rem;
  }
  .final-headline {
    font-size: 2.1rem;
  }
  .final-subtext {
    font-size: 0.95rem;
    margin-bottom: 2rem;
  }
  .final-cta-block {
    flex-direction: column;
    gap: 0.75rem;
  }
}
</style>
