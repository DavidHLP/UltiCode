<script setup lang="ts">
/**
 * NetworkSection — the camera pulls back: personal tracks join the wider
 * network of contests and community. Two hairline-separated groups keep the
 * structure informational rather than decorative.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";

const { t } = useI18n();

// Explicit literal keys: the i18n static checker cannot resolve dynamic
// key interpolation in templates.
const contestPoints = computed(() => [
  t("landing.network.contest.points.schedule"),
  t("landing.network.contest.points.standing"),
  t("landing.network.contest.points.ranking"),
]);
const communityPoints = computed(() => [
  t("landing.network.community.points.solutions"),
  t("landing.network.community.points.discussion"),
  t("landing.network.community.points.bookmarks"),
]);

defineOptions({ name: "NetworkSection" });
</script>

<template>
  <section class="landing-section" aria-labelledby="landing-network-title">
    <div class="landing-container">
      <div class="landing-block landing-reveal mx-auto max-w-2xl text-center">
        <p class="landing-eyebrow">{{ t("landing.network.eyebrow") }}</p>
        <h2 id="landing-network-title" class="landing-heading">
          {{ t("landing.network.title") }}
        </h2>
        <p class="landing-body">{{ t("landing.network.body") }}</p>
      </div>

      <div class="mx-auto mt-12 grid max-w-4xl gap-px sm:grid-cols-2">
        <div class="landing-network-group">
          <h3 class="landing-network-heading">
            {{ t("landing.network.contest.title") }}
          </h3>
          <ul class="landing-points">
            <li v-for="point in contestPoints" :key="point">
              {{ point }}
            </li>
          </ul>
          <Button as-child variant="outline" size="sm" class="landing-btn-ghost mt-6">
            <RouterLink :to="{ name: 'contest-home' }">
              {{ t("landing.network.contest.cta") }}
            </RouterLink>
          </Button>
        </div>

        <div class="landing-network-group">
          <h3 class="landing-network-heading">
            {{ t("landing.network.community.title") }}
          </h3>
          <ul class="landing-points">
            <li v-for="point in communityPoints" :key="point">
              {{ point }}
            </li>
          </ul>
          <Button as-child variant="outline" size="sm" class="landing-btn-ghost mt-6">
            <RouterLink :to="{ name: 'forum-home' }">
              {{ t("landing.network.community.cta") }}
            </RouterLink>
          </Button>
        </div>
      </div>
    </div>
  </section>
</template>
