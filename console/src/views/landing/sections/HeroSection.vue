<script setup lang="ts">
/**
 * HeroSection — wake the code core. Value proposition and primary entries
 * must be complete within a common desktop viewport and must never wait on
 * the 3D layer (all content is plain DOM).
 */
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";

const { t } = useI18n();

defineOptions({ name: "HeroSection" });
</script>

<template>
  <section class="landing-section" aria-labelledby="landing-hero-title">
    <div class="landing-container">
      <div class="landing-block landing-reveal max-w-2xl">
        <p class="landing-eyebrow">{{ t("landing.hero.eyebrow") }}</p>
        <h1 id="landing-hero-title" class="landing-title">
          {{ t("landing.hero.title") }}
        </h1>
        <p class="landing-body">
          {{ t("landing.hero.subtitle") }}
        </p>
        <div class="mt-8 flex flex-wrap items-center gap-3">
          <Button as-child size="lg" class="landing-btn-primary">
            <RouterLink :to="{ name: 'problemset' }">
              {{ t("landing.hero.ctaPrimary") }}
            </RouterLink>
          </Button>
          <Button as-child variant="outline" size="lg" class="landing-btn-ghost">
            <RouterLink :to="{ name: 'contest-home' }">
              {{ t("landing.hero.ctaSecondary") }}
            </RouterLink>
          </Button>
        </div>
      </div>
    </div>
  </section>
</template>
