<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import LucaPortal from "./landing-luca/LucaPortal.vue";
import LucaNav from "./landing-luca/LucaNav.vue";
import LucaScene from "./landing-luca/LucaScene.vue";
import LucaBeat from "./landing-luca/beats/LucaBeat.vue";
import LucaBeatEntrance from "./landing-luca/beats/LucaBeatEntrance.vue";
import LucaBeatAnatomy from "./landing-luca/beats/LucaBeatAnatomy.vue";
import LucaBeatRecords from "./landing-luca/beats/LucaBeatRecords.vue";
import LucaBeatBroken from "./landing-luca/beats/LucaBeatBroken.vue";
import { LUCA_BEATS, LUCA_BEAT_TOTAL } from "./landing-luca/beats/lucaBeats";
import { useLucaPortal } from "@/composables/landing/useLucaPortal";
import { useLucaReveal } from "@/composables/landing/useLucaReveal";
import { useLucaScroll } from "@/composables/landing/useLucaScroll";
import { useLucaStage } from "@/composables/landing/useLucaStage";
import "@/assets/styles/landing-luca.css";

const TWO_SUM_SLUG = "two-sum";
const PORTAL_LEAVE_MS = 600;
const PORTAL_COUNTER_TOTAL = 24;

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

const rootRef = ref<HTMLElement | null>(null);
const portal = useLucaPortal();
const showPortal = ref(!portal.entered.value);
const menuOpen = ref(false);
let dismissTimer: ReturnType<typeof setTimeout> | undefined;

// Stage bus: owns state / progress / fragment / reverse-explode command and
// provides them to the 3D scene + the interactive beats.
useLucaStage(rootRef);

useLucaScroll({ locked: showPortal });
useLucaReveal(rootRef);

const prefersReducedMotion = () =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// "000 / 024" → "018 / 024" so the counter matches the reference's N-of-M feel.
const counterDisplay = () => {
  const mapped = Math.round((portal.progress.value / 100) * PORTAL_COUNTER_TOTAL);
  return `${String(mapped).padStart(3, "0")} / ${String(PORTAL_COUNTER_TOTAL).padStart(3, "0")}`;
};

const goToSeedProblem = () =>
  router.push(
    authStore.isAuthenticated
      ? { name: "forum-home" }
      : { name: "problem-detail", params: { slug: TWO_SUM_SLUG } },
  );

const handleEnter = () => {
  portal.enter();
  // Let the exit animation play before unmounting; under reduced-motion the
  // CSS animation is disabled, so remove the portal on the next frame.
  const delay = prefersReducedMotion() ? 0 : PORTAL_LEAVE_MS;
  dismissTimer = setTimeout(() => {
    showPortal.value = false;
  }, delay);
};

onBeforeUnmount(() => {
  if (dismissTimer) clearTimeout(dismissTimer);
});

const handleSkip = () => {
  portal.skip();
};

onMounted(() => {
  // Reveal the first beat immediately so the headline is never hidden behind
  // the intersection threshold on first paint.
  rootRef.value
    ?.querySelector<HTMLElement>(".luca-beat [data-luca-reveal]")
    ?.classList.add("is-revealed");
});
</script>

<template>
  <div ref="rootRef" class="luca-root" :class="{ 'luca-menu-open': menuOpen }">
    <a href="#luca-main" class="luca-skip">{{ t("landingLuca.nav.skipToContent") }}</a>

    <LucaNav
      :menu-open="menuOpen"
      @toggle-menu="menuOpen = !menuOpen"
      @close-menu="menuOpen = false"
      @talk="goToSeedProblem"
    />

    <LucaScene :active="!showPortal" />

    <main id="luca-main">
      <template v-for="(beat, index) in LUCA_BEATS" :key="beat.state">
        <LucaBeatEntrance
          v-if="beat.state === 'opened'"
          :n="index + 1"
          :total="LUCA_BEAT_TOTAL"
          :align="beat.align"
        />
        <LucaBeatAnatomy
          v-else-if="beat.state === 'quarteted'"
          :n="index + 1"
          :total="LUCA_BEAT_TOTAL"
          :align="beat.align"
        />
        <LucaBeatRecords
          v-else-if="beat.state === 'timed'"
          :n="index + 1"
          :total="LUCA_BEAT_TOTAL"
          :align="beat.align"
        />
        <LucaBeatBroken
          v-else-if="beat.state === 'broken'"
          :n="index + 1"
          :total="LUCA_BEAT_TOTAL"
          :align="beat.align"
        />
        <LucaBeat
          v-else
          :state="beat.state"
          :n="index + 1"
          :total="LUCA_BEAT_TOTAL"
          :align="beat.align"
          :eyebrow="t(`landingLuca.beats.${beat.state}.eyebrow`)"
          :title="t(`landingLuca.beats.${beat.state}.title`)"
          :subline="t(`landingLuca.beats.${beat.state}.subline`)"
        />
      </template>
    </main>

    <footer class="luca-footer">
      <span>{{ t("landingLuca.footer.builtWith") }}</span>
      <nav class="luca-footer-links" :aria-label="t('landingLuca.social.label')">
        <a
          href="https://github.com/ulticode/ulticode"
          target="_blank"
          rel="noopener noreferrer"
          class="luca-footer-link"
          >{{ t("landingLuca.social.github") }}</a
        >
        <a
          href="https://ulticode.dev/docs"
          target="_blank"
          rel="noopener noreferrer"
          class="luca-footer-link"
          >{{ t("landingLuca.social.docs") }}</a
        >
        <RouterLink :to="{ name: 'forum-home' }" class="luca-footer-link">{{
          t("landingLuca.social.community")
        }}</RouterLink>
      </nav>
      <span>{{ t("landingLuca.footer.copyright") }}</span>
    </footer>

    <LucaPortal
      v-if="showPortal"
      :progress="portal.progress.value"
      :ready="portal.ready.value"
      :leaving="portal.entered.value"
      @enter="handleEnter"
      @skip="handleSkip"
    >
      <template #counter>
        {{ counterDisplay() }}
      </template>
    </LucaPortal>
  </div>
</template>
