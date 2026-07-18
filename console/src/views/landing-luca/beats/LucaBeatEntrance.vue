<script setup lang="ts">
/**
 * Beat 05 — "opened". The polyhedron opens a doorway in the scene; the DOM
 * beat owns the entrance CTA. Clicking it drops the reader onto the seed
 * problem (or the forum feed when already signed in), mirroring the nav
 * "talk" action in the landing root.
 */
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import LucaBeat from "./LucaBeat.vue";

interface Props {
  n: number;
  total: number;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "right" });

const TWO_SUM_SLUG = "two-sum";

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

const onEnter = () => {
  void router.push(
    authStore.isAuthenticated
      ? { name: "forum-home" }
      : { name: "problem-detail", params: { slug: TWO_SUM_SLUG } },
  );
};
</script>

<template>
  <LucaBeat
    state="opened"
    :n="props.n"
    :total="props.total"
    :align="props.align"
    :eyebrow="t('landingLuca.beats.opened.eyebrow')"
    :title="t('landingLuca.beats.opened.title')"
    :subline="t('landingLuca.beats.opened.subline')"
  >
    <button type="button" class="luca-beat-cta" data-luca-reveal @click="onEnter">
      {{ t("landingLuca.beats.opened.cta") }}
    </button>
  </LucaBeat>
</template>
