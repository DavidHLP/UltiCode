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
    class="@container/problemset max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <!-- Top Banners -->
    <section>
      <FeaturedBanners />
    </section>

    <!-- Main Content Area -->
    <div
      class="grid grid-cols-1 items-start gap-6 @3xl/problemset:grid-cols-[minmax(0,1fr)_16rem] @3xl/problemset:gap-8"
    >
      <!-- Left Column: Problem List (9 cols) -->
      <main class="order-2 @3xl/problemset:order-1 min-w-0 space-y-6">
        <ProblemExplorer :initial-category="category" />
      </main>

      <!-- Right Column: Sidebar (3 cols) -->
      <aside
        data-testid="problem-set-sidebar"
        class="order-1 @3xl/problemset:order-2 min-w-0 w-full max-w-md mx-auto @3xl/problemset:max-w-none @3xl/problemset:mx-0 space-y-6 @3xl/problemset:sticky @3xl/problemset:top-24"
      >
        <ProblemSetSidebar />
      </aside>
    </div>
  </div>
</template>
