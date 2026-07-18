<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";

/**
 * Fixed top nav. Auto-hides on scroll-down and reveals on scroll-up so the
 * chrome recedes while the 3D narrative is read. Hides only after the page has
 * scrolled a little, and never hides while the mobile menu is open.
 */
interface Props {
  menuOpen: boolean;
}
const props = defineProps<Props>();
const emit = defineEmits<{
  toggleMenu: [];
  closeMenu: [];
  talk: [];
}>();

const { t } = useI18n();

const hidden = ref(false);
let lastY = 0;

const onScroll = () => {
  if (typeof window === "undefined") return;
  const y = window.scrollY || window.pageYOffset || 0;
  if (props.menuOpen) {
    hidden.value = false;
    lastY = y;
    return;
  }
  const delta = y - lastY;
  // Ignore micro-scroll; only toggle on deliberate direction.
  if (y < 80) {
    hidden.value = false;
  } else if (delta > 6) {
    hidden.value = true;
  } else if (delta < -6) {
    hidden.value = false;
  }
  lastY = y;
};

onMounted(() => {
  if (typeof window === "undefined") return;
  lastY = window.scrollY || 0;
  window.addEventListener("scroll", onScroll, { passive: true });
});
onBeforeUnmount(() => {
  if (typeof window !== "undefined") window.removeEventListener("scroll", onScroll);
});

const onTalk = () => {
  emit("talk");
  emit("closeMenu");
};
// Close the mobile menu on Escape so keyboard users are never trapped.
const onKeydown = (event: KeyboardEvent) => {
  if (props.menuOpen && event.key === "Escape") emit("closeMenu");
};
</script>

<template>
  <header class="luca-nav" :class="{ 'is-nav-hidden': hidden }" @keydown="onKeydown">
    <RouterLink :to="{ name: 'landing' }" class="luca-brandmark" @click="emit('closeMenu')">
      <span class="luca-brandmark-dot" aria-hidden="true"></span>
      {{ t("landingLuca.brand") }}
    </RouterLink>

    <nav class="luca-nav-links" :aria-label="t('landingLuca.nav.primaryNav')">
      <RouterLink :to="{ name: 'landing' }" class="luca-nav-link">{{
        t("landingLuca.nav.home")
      }}</RouterLink>
      <RouterLink :to="{ name: 'problemset' }" class="luca-nav-link">{{
        t("landingLuca.nav.problems")
      }}</RouterLink>
      <RouterLink :to="{ name: 'contest-list' }" class="luca-nav-link">{{
        t("landingLuca.nav.contests")
      }}</RouterLink>
      <RouterLink :to="{ name: 'forum-home' }" class="luca-nav-link">{{
        t("landingLuca.nav.community")
      }}</RouterLink>
    </nav>

    <button
      type="button"
      class="luca-talk"
      :aria-label="t('landingLuca.nav.talk')"
      @click="onTalk"
    >
      {{ t("landingLuca.nav.talk") }}
    </button>

    <button
      type="button"
      class="luca-burger"
      :aria-expanded="menuOpen"
      :aria-label="menuOpen ? t('landingLuca.nav.closeMenu') : t('landingLuca.nav.openMenu')"
      @click="emit('toggleMenu')"
    >
      <span aria-hidden="true"></span>
      <span aria-hidden="true"></span>
      <span aria-hidden="true"></span>
    </button>
  </header>

  <div v-if="menuOpen" class="luca-menu" :aria-label="t('landingLuca.nav.mobileNav')">
    <RouterLink :to="{ name: 'landing' }" class="luca-menu-link" @click="emit('closeMenu')">{{
      t("landingLuca.nav.home")
    }}</RouterLink>
    <RouterLink :to="{ name: 'problemset' }" class="luca-menu-link" @click="emit('closeMenu')">{{
      t("landingLuca.nav.problems")
    }}</RouterLink>
    <RouterLink :to="{ name: 'contest-list' }" class="luca-menu-link" @click="emit('closeMenu')">{{
      t("landingLuca.nav.contests")
    }}</RouterLink>
    <RouterLink :to="{ name: 'forum-home' }" class="luca-menu-link" @click="emit('closeMenu')">{{
      t("landingLuca.nav.community")
    }}</RouterLink>
    <button type="button" class="luca-menu-cta" @click="onTalk">
      {{ t("landingLuca.nav.talk") }} →
    </button>
  </div>
</template>
