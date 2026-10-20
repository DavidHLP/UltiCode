<script setup lang="ts">
/**
 * Problem solving session view (architecture-review candidate #3, top
 * recommendation).
 *
 * <p>This file is now a thin render adapter. Navigation interpretation,
 * problem load, contest context, layout policy, panel state, and the
 * panel component map all live in
 * {@link ./composables/useProblemSession}. The view only:
 * <ol>
 *   <li>Calls the session once during setup.</li>
 *   <li>Registers the provide() keys (the session also owns the
 *       setup order; this adapter simply forwards).</li>
 *   <li>Renders the template.</li>
 * </ol>
 *
 * <p>Before the deepening this file owned six connector components, two
 * {@code provide} keys, the panel map, and the layout initialiser.
 * Source-string tests pinned the setup order; the order is now
 * internal to the session composable.
 */
import { provide } from "vue";

import { useProblemSession } from "./composables/useProblemSession";

const {
  installProviders,
  isMobile,
  isSidePanelOpen,
  isNotesOpen,
  problem,
  layoutConfig,
  currentLayout,
  handleLayoutChange,
  LayoutHeaderLeft,
  LayoutHeaderCenter,
  LayoutHeaderControls,
  LayoutTree,
  MobileProblemLayout,
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  ProblemListDrawer,
  ProblemNotesDrawer,
} = useProblemSession();

installProviders(provide);
</script>

<template>
  <div class="problem-detail-page h-screen flex flex-col bg-background antialiased">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-primary focus:text-primary-foreground focus:rounded-lg"
    >
      {{ $t("common.skipToContent") }}
    </a>
    <Sheet v-model:open="isSidePanelOpen">
      <SheetContent side="left" class="p-0 w-[400px] sm:w-[540px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ $t("problem.drawer.problemList") }}</SheetTitle>
          <SheetDescription>{{
            $t("problem.drawer.noProblemsFound")
          }}</SheetDescription>
        </SheetHeader>
        <ProblemListDrawer
          :current-problem-id="problem?.id"
          @close="isSidePanelOpen = false"
        />
      </SheetContent>
    </Sheet>

    <Sheet :open="isNotesOpen" @update:open="isNotesOpen = $event">
      <SheetContent side="right" class="p-0 w-[400px] sm:w-[500px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ $t("problem.notes.title") }}</SheetTitle>
          <SheetDescription>{{
            $t("problem.notes.description")
          }}</SheetDescription>
        </SheetHeader>
        <ProblemNotesDrawer
          v-if="problem"
          :problem-id="Number(problem.id)"
          @close="isNotesOpen = false"
        />
      </SheetContent>
    </Sheet>

    <header
      class="relative flex w-full shrink-0 flex-wrap items-center justify-between gap-2 bg-background px-2.5 py-2 xl:h-12 xl:flex-nowrap xl:py-0"
    >
      <div
        class="relative z-10 flex min-w-0 w-full items-center xl:h-full xl:flex-1"
      >
        <LayoutHeaderLeft />
      </div>
      <div
        class="pointer-events-none flex w-full items-center justify-center xl:absolute xl:inset-0 xl:w-auto"
      >
        <div class="pointer-events-auto">
          <LayoutHeaderCenter />
        </div>
      </div>
      <div
        class="relative z-10 flex min-w-0 w-full items-center justify-center gap-2 xl:ml-auto xl:h-full xl:flex-1 xl:justify-end"
      >
        <LayoutHeaderControls
          :current-layout="currentLayout"
          :problem="problem"
          @layout-change="handleLayoutChange"
        />
      </div>
    </header>

    <main
      id="main-content"
      class="problem-detail-main flex-1 min-h-0 overflow-hidden w-full p-4 pt-0"
      role="main"
    >
      <MobileProblemLayout v-if="isMobile" />
      <LayoutTree
        v-else-if="layoutConfig"
        :layout="layoutConfig"
        class="h-full w-full"
      />
    </main>
  </div>
</template>

<style scoped>
@media (max-height: 400px) {
  .problem-detail-page {
    height: auto;
    min-height: 100dvh;
  }

  .problem-detail-main {
    flex: none;
    height: 100dvh;
    overflow: visible;
  }
}
</style>
