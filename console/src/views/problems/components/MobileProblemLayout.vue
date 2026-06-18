<script setup lang="ts">
import { ref, computed, inject, watch, nextTick, type Component } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PanelComponentMapKey } from "@/features/layout/panels/panel-context";
import {
  FileText,
  FlaskConical,
  History,
  Code2,
  SquareCheck,
  Terminal,
} from "lucide-vue-next";
import { useProblemContext } from "../useProblemContext";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const { contestId } = useProblemContext();

// Get the panel component map from parent
const panelComponentMap = inject<Record<number, Component>>(
  PanelComponentMapKey,
  {},
);

// Tab configuration with icons. In contest / virtual-contest context the
// solutions tab is omitted so competitors cannot see other participants'
// editorial-style write-ups while the contest is live.
const tabs = computed(() => {
  const isContest = contestId.value !== null;
  const list = [
    {
      id: "description",
      headerId: 1,
      label: t("problem.layout.problemDescription"),
      icon: FileText,
    },
    { id: "code", headerId: 4, label: t("problem.layout.code"), icon: Code2 },
    {
      id: "testcases",
      headerId: 5,
      label: t("problem.layout.testCases"),
      icon: SquareCheck,
    },
    {
      id: "testresults",
      headerId: 6,
      label: t("problem.layout.testResults"),
      icon: Terminal,
    },
  ];
  if (!isContest) {
    list.push({
      id: "solutions",
      headerId: 2,
      label: t("problem.layout.solution"),
      icon: FlaskConical,
    });
  }
  list.push({
    id: "submissions",
    headerId: 3,
    label: t("problem.layout.submissions"),
    icon: History,
  });
  return list;
});

const tabIds = computed(() => tabs.value.map((t) => t.id));

// Guards to prevent infinite loop between URL and local state
const isUpdatingFromRoute = ref(false);
const isUpdatingFromState = ref(false);

const activeTab = ref("description");

// Initialize activeTab from route
const tabParam = route.params.tab;
const initialTab = Array.isArray(tabParam) ? tabParam[0] : tabParam;
// In contest mode, fall back to description if the URL still points at the
// (now-hidden) solutions tab.
const resolvedInitial =
  contestId.value !== null && initialTab === "solutions"
    ? "description"
    : initialTab;
if (resolvedInitial && tabIds.value.includes(resolvedInitial)) {
  activeTab.value = resolvedInitial;
}

// Sync route -> activeTab
watch(
  [() => route.params.tab, tabIds],
  ([newTab, ids]) => {
    if (isUpdatingFromState.value) return;
    let tabName = Array.isArray(newTab) ? newTab[0] : newTab;
    if (contestId.value !== null && tabName === "solutions") {
      tabName = "description";
    }
    if (tabName && ids.includes(tabName) && activeTab.value !== tabName) {
      isUpdatingFromRoute.value = true;
      activeTab.value = tabName;
      nextTick(() => {
        isUpdatingFromRoute.value = false;
      });
    }
  },
);

// Sync activeTab -> route
watch(activeTab, (newTab) => {
  if (isUpdatingFromRoute.value) return;
  const currentTab = route.params.tab;
  const currentTabName = Array.isArray(currentTab) ? currentTab[0] : currentTab;
  if (newTab !== currentTabName) {
    isUpdatingFromState.value = true;
    router
      .push({
        name: route.name ?? undefined,
        params: { ...route.params, tab: newTab },
        query: route.query,
      })
      .then(() => {
        nextTick(() => {
          isUpdatingFromState.value = false;
        });
      })
      .catch(() => {
        isUpdatingFromState.value = false;
      });
  }
});

const currentComponent = computed(() => {
  const tab = tabs.value.find((t) => t.id === activeTab.value);
  if (!tab) return null;
  return panelComponentMap[tab.headerId] || null;
});
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <!-- Tab bar with scrollable tabs -->
    <Tabs v-model="activeTab" class="flex flex-col h-full">
      <!-- Mobile: Use select dropdown for tabs -->
      <div class="shrink-0 border-b px-2 py-2 sm:hidden">
        <Select v-model="activeTab">
          <SelectTrigger class="w-full">
            <SelectValue :placeholder="t('common.labels.selectTab')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem
              v-for="tab in tabs"
              :key="tab.id"
              :value="tab.id"
              class="flex items-center gap-2"
            >
              <div class="flex items-center gap-2">
                <component :is="tab.icon" class="h-4 w-4" />
                <span>{{ tab.label }}</span>
              </div>
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <!-- Tablet: Use horizontal scrollable tabs -->
      <div class="shrink-0 border-b hidden sm:block">
        <TabsList class="w-full justify-start overflow-x-auto h-auto gap-1 p-1">
          <TabsTrigger
            v-for="tab in tabs"
            :key="tab.id"
            :value="tab.id"
            class="flex items-center gap-1.5 px-3 py-1.5 text-xs whitespace-nowrap"
          >
            <component :is="tab.icon" class="h-3.5 w-3.5" />
            <span class="hidden md:inline">{{ tab.label }}</span>
          </TabsTrigger>
        </TabsList>
      </div>

      <!-- Content area -->
      <div class="flex-1 min-h-0 overflow-auto">
        <TabsContent
          v-for="tab in tabs"
          :key="tab.id"
          :value="tab.id"
          class="h-full m-0 data-[state=inactive]:hidden"
        >
          <component :is="currentComponent" v-if="activeTab === tab.id" />
        </TabsContent>
      </div>
    </Tabs>
  </div>
</template>
