<script setup lang="ts">
import ProblemExplorer from "@/components/problem/ProblemExplorer.vue";
import ProblemSetSidebar from "@/components/problem/ProblemSetSidebar.vue";
import FeaturedBanners from "@/components/problem/FeaturedBanners.vue";
import { useRoute } from "vue-router";
import { computed, onMounted } from "vue";
import { useI18n } from "vue-i18n";

const route = useRoute();
const { t } = useI18n();

const category = computed(() => {
  const c = route.params.category;
  return Array.isArray(c) ? c[0] : c;
});

onMounted(() => {
  document.title = `${t("problem.list.title")} - UltiCode`;
});
</script>

<template>
  <div
    class="@container/problemset max-w-7xl mx-auto w-full animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div
      class="grid grid-cols-1 items-start gap-6 @3xl/problemset:grid-cols-[minmax(0,1fr)_18rem]"
    >
      <main class="order-2 @3xl/problemset:order-1 min-w-0 space-y-6">
        <FeaturedBanners />
        <ProblemExplorer :initial-category="category" />
      </main>

      <aside
        data-testid="problem-set-sidebar"
        class="order-1 @3xl/problemset:order-2 min-w-0 w-full max-w-md mx-auto @3xl/problemset:max-w-none @3xl/problemset:mx-0 space-y-6 @3xl/problemset:sticky @3xl/problemset:top-20"
      >
        <ProblemSetSidebar />
      </aside>
    </div>
  </div>
</template>
