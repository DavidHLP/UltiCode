<script setup lang="ts">
/**
 * Beat 09 — "broken". Two CTAs dispatch onto the stage command channel:
 *
 *  - "今昔并存" (secondary, ghost)  → requestReverse(): the scene eases the
 *    device back to a pristine symmetric origin state (the "harmony" path).
 *  - "创造未来" (primary, solid)    → requestExplode(): the scene bursts the
 *    device into a particle cloud; this beat watches the same command and,
 *    after a short delay so the burst actually plays, navigates to register
 *    (or forum-home when already signed in).
 *
 * The beat owns the navigation; the scene owns the visual burst. They share
 * the command via the stage bus, keyed by a monotonic id so repeated clicks
 * always re-fire.
 */
import { onBeforeUnmount, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import LucaBeat from "./LucaBeat.vue";
import { useLucaStageConsumer } from "@/composables/landing/useLucaStage";

interface Props {
  n: number;
  total: number;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "right" });

// Give the scene's particle burst time to play before we yank the route.
const EXPLODE_NAV_DELAY_MS = 700;

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();
const stage = useLucaStageConsumer();

let navTimer: ReturnType<typeof setTimeout> | undefined;

const navigateAfterExplode = () => {
  if (navTimer) clearTimeout(navTimer);
  navTimer = setTimeout(() => {
    void router.push(
      authStore.isAuthenticated ? { name: "forum-home" } : { name: "register" },
    );
  }, EXPLODE_NAV_DELAY_MS);
};

// React to the explode command (whoever issued it). The id in the command
// makes every dispatch a fresh object, so the watcher fires per click.
watch(stage.command, (cmd) => {
  if (cmd?.kind === "explode") navigateAfterExplode();
});

onBeforeUnmount(() => {
  if (navTimer) clearTimeout(navTimer);
});

const onReverse = () => stage.requestReverse();
const onExplode = () => stage.requestExplode();
</script>

<template>
  <LucaBeat
    state="broken"
    :n="props.n"
    :total="props.total"
    :align="props.align"
    :eyebrow="t('landingLuca.beats.broken.eyebrow')"
    :title="t('landingLuca.beats.broken.title')"
    :subline="t('landingLuca.beats.broken.subline')"
  >
    <div class="luca-cta-row" data-luca-reveal>
      <button type="button" class="luca-cta-ghost" @click="onReverse">
        {{ t("landingLuca.beats.broken.ctaSecondary") }}
      </button>
      <button type="button" class="luca-cta-solid" @click="onExplode">
        {{ t("landingLuca.beats.broken.ctaPrimary") }}
      </button>
    </div>
  </LucaBeat>
</template>
