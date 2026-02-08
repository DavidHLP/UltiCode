<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { ProblemLayout as ProblemLayoutType } from "@/hooks/problem-hooks";
import type { ProblemDetail } from "@/types/problem-detail";
import type { LayoutNode } from "@/stores/headerStore";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import LayoutHeaderLeft from "../headers/LayoutHeaderLeft.vue";
import LayoutHeaderCenter from "../headers/LayoutHeaderCenter.vue";
import LayoutHeaderControls from "../headers/LayoutHeaderControls.vue";
import LayoutTree from "@/features/layout/tree/LayoutTree.vue";
import ProblemListDrawer from "@/components/problem/ProblemListDrawer.vue";
import ProblemNotesDrawer from "@/components/problem/ProblemNotesDrawer.vue";

interface Props {
  problem: ProblemDetail | null;
  isSidePanelOpen: boolean;
  isNotesOpen: boolean;
  currentLayout: ProblemLayoutType;
  layoutConfig: LayoutNode | null;
}

defineProps<Props>();
const emit = defineEmits<{
  (e: "update:isSidePanelOpen", value: boolean): void;
  (e: "update:isNotesOpen", value: boolean): void;
  (e: "layoutChange", layout: ProblemLayoutType): void;
}>();

const { t } = useI18n();

const handleLayoutChange = (layout: ProblemLayoutType) => {
  emit("layoutChange", layout);
};
</script>

<template>
  <div class="h-screen flex flex-col bg-[#f0f0f0] antialiased">
    <Sheet
      :open="isSidePanelOpen"
      @update:open="(val: boolean) => emit('update:isSidePanelOpen', val)"
    >
      <SheetContent side="left" class="p-0 w-[400px] sm:w-[540px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ t("problem.drawer.problemList") }}</SheetTitle>
          <SheetDescription>{{
            t("problem.drawer.noProblemsFound")
          }}</SheetDescription>
        </SheetHeader>
        <ProblemListDrawer
          :current-problem-id="problem?.id"
          @close="emit('update:isSidePanelOpen', false)"
        />
      </SheetContent>
    </Sheet>

    <Sheet
      :open="isNotesOpen"
      @update:open="(val: boolean) => emit('update:isNotesOpen', val)"
    >
      <SheetContent side="right" class="p-0 w-[400px] sm:w-[500px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ t("problem.notes.title") }}</SheetTitle>
          <SheetDescription>
            {{ t("problem.notes.description") }}
          </SheetDescription>
        </SheetHeader>
        <ProblemNotesDrawer
          v-if="problem"
          :problem-id="Number(problem.id)"
          @close="emit('update:isNotesOpen', false)"
        />
      </SheetContent>
    </Sheet>

    <header
      class="relative flex h-12 w-full min-w-[100px] shrink-0 items-center justify-between gap-2 bg-[#f0f0f0] px-2.5"
    >
      <div
        class="relative z-10 flex h-full min-w-[240px] flex-1 items-center overflow-hidden"
      >
        <LayoutHeaderLeft />
      </div>
      <div
        class="pointer-events-none absolute inset-0 flex items-center justify-center"
      >
        <div class="pointer-events-auto">
          <LayoutHeaderCenter />
        </div>
      </div>
      <div
        class="relative z-10 ml-auto flex h-full flex-1 items-center justify-end gap-2"
      >
        <LayoutHeaderControls
          :current-layout="currentLayout"
          :problem="problem"
          @layout-change="handleLayoutChange"
        />
      </div>
    </header>

    <!-- Dynamic layout area -->
    <main class="flex-1 min-h-0 overflow-hidden w-full p-4 pt-0">
      <LayoutTree
        v-if="layoutConfig"
        :layout="layoutConfig"
        class="h-full w-full"
      />
    </main>
  </div>
</template>
