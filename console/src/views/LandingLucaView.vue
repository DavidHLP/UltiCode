<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import LucaPortal from "./landing-luca/LucaPortal.vue";
import LucaNav from "./landing-luca/LucaNav.vue";
import LucaWordStack from "./landing-luca/LucaWordStack.vue";
import LucaManifesto from "./landing-luca/LucaManifesto.vue";
import LucaWork from "./landing-luca/LucaWork.vue";
import LucaCapabilities from "./landing-luca/LucaCapabilities.vue";
import LucaAwards from "./landing-luca/LucaAwards.vue";
import LucaContact from "./landing-luca/LucaContact.vue";
import LucaProblem from "./landing-luca/LucaProblem.vue";
import LucaSolution from "./landing-luca/LucaSolution.vue";
import LucaExperience from "./landing-luca/LucaExperience.vue";
import LucaAbout from "./landing-luca/LucaAbout.vue";
import LucaScrollProgress from "./landing-luca/LucaScrollProgress.vue";
import LucaHeroScene from "./landing-luca/LucaHeroScene.vue";
import { useLucaPortal } from "@/composables/landing/useLucaPortal";
import { useLucaReveal } from "@/composables/landing/useLucaReveal";
import { useLucaCursor } from "@/composables/landing/useLucaCursor";
import { useLucaScroll } from "@/composables/landing/useLucaScroll";
import "@/assets/styles/landing-luca.css";

const TWO_SUM_SLUG = "two-sum";
const PORTOR_LEAVE_MS = 600;
const PORTAL_COUNTER_TOTAL = 24;

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

const rootRef = ref<HTMLElement | null>(null);
const cursorRef = ref<HTMLElement | null>(null);
const heroRef = ref<HTMLElement | null>(null);
const worldProgress = ref(-1);

const portal = useLucaPortal();
const showPortal = ref(!portal.entered.value);
const menuOpen = ref(false);
let dismissTimer: ReturnType<typeof setTimeout> | undefined;

useLucaScroll({ locked: showPortal, world: rootRef, worldProgress });
useLucaReveal(rootRef);
const cursor = useLucaCursor(cursorRef);

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
  const delay = prefersReducedMotion() ? 0 : PORTOR_LEAVE_MS;
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
  // Reveal hero immediately so the word-art is never hidden behind the
  // intersection threshold on first paint.
  rootRef.value
    ?.querySelectorAll<HTMLElement>(".luca-hero [data-luca-reveal], .luca-hero .luca-line")
    .forEach((el) => el.classList.add("is-revealed"));
});
</script>

<template>
  <div
    ref="rootRef"
    class="luca-root"
    :class="{ 'luca-cursor-enabled': cursor.active.value }"
  >
    <a href="#luca-main" class="luca-skip">{{ t("landingLuca.nav.skipToContent") }}</a>

    <LucaScrollProgress />

    <LucaNav
      :menu-open="menuOpen"
      @toggle-menu="menuOpen = !menuOpen"
      @close-menu="menuOpen = false"
      @talk="goToSeedProblem"
    />

    <LucaHeroScene :active="!showPortal" :world-progress="worldProgress" />

    <main id="luca-main">
      <section class="luca-hero" ref="heroRef" aria-label="UltiCode">
        <div class="luca-hero-content">
          <p class="luca-hero-eyebrow" data-luca-reveal>
            {{ t("landingLuca.hero.eyebrow") }}
          </p>
          <LucaWordStack />
          <div class="luca-hero-foot">
            <p class="luca-hero-tagline" data-luca-reveal>
              {{ t("landingLuca.hero.roleLine") }}
            </p>
            <button type="button" class="luca-hero-cta" @click="goToSeedProblem">
              {{ t("landingLuca.hero.cta") }} →
            </button>
          </div>
        </div>
      </section>

      <LucaProblem />
      <LucaSolution />
      <LucaManifesto />
      <LucaExperience @primary="goToSeedProblem" />
      <LucaWork />
      <LucaCapabilities />
      <LucaAwards />
      <LucaAbout />
      <LucaContact @primary="goToSeedProblem" />
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

    <div ref="cursorRef" class="luca-cursor" aria-hidden="true"></div>

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
