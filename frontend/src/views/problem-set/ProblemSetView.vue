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
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <!-- Top Banners -->
    <section>
      <FeaturedBanners />
    </section>

    <!-- Main Content Area -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
      <!-- Left Column: Problem List (9 cols) -->
      <main class="lg:col-span-9 space-y-6">
        <ProblemExplorer :initial-category="category" />
      </main>

      <!-- Right Column: Sidebar (3 cols) -->
      <aside class="hidden lg:block lg:col-span-3 space-y-6 sticky top-24">
        <ProblemSetSidebar />
      </aside>
    </div>
  </div>
</template>
