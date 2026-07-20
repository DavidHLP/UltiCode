<script setup lang="ts">
/**
 * Beat 09 — "broken". Two CTAs dispatch onto the stage command channel:
 *
 *  - "今昔并存" (secondary, ghost)  → requestReverse(): the scene eases the
 *    device back to a pristine symmetric origin state (the "harmony" path).
 *  - "创造未来" (primary, solid)    → requestFutureTransition(): the scene
 *    bursts the device into a particle cloud; when the animation completes,
 *    the stage's injected callback navigates to register or forum-home.
 *
 * Navigation lives in the landing view (via the stage callback) so the beat
 * expresses only intent and the deep module owns sequencing/completion.
 */
import { useI18n } from "vue-i18n";
import LucaBeat from "./LucaBeat.vue";
import { useLucaStageConsumer } from "@/composables/landing/useLucaStage";

interface Props {
  n: number;
  total: number;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "right" });

const { t } = useI18n();
const stage = useLucaStageConsumer();

const onReverse = () => stage.requestReverse();
const onExplode = () => stage.requestFutureTransition();
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
