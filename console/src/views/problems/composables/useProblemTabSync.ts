import { ref, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useHeaderStore } from "@/stores/headerStore";
import { problemHooks } from "@/hooks/problem-hooks";

const TAB_MAP: Record<string, number> = {
  description: 1,
  solutions: 2,
  submissions: 3,
};

const REV_TAB_MAP: Record<number, string> = {
  1: "description",
  2: "solutions",
  3: "submissions",
};

export function useProblemTabSync() {
  const route = useRoute();
  const router = useRouter();
  const headerStore = useHeaderStore();

  const lastTab = ref<string | null>(null);

  // Guards to prevent infinite loop between URL and store sync
  const isUpdatingFromRoute = ref(false);
  const isUpdatingFromStore = ref(false);

  // Sync URL to Store (when route changes, e.g. back button)
  watch(
    () => route.params.tab,
    (newTab) => {
      // Skip if we're updating from store (prevent loop)
      if (isUpdatingFromStore.value) return;

      const tabName = Array.isArray(newTab) ? newTab[0] : newTab;
      if (tabName && Object.prototype.hasOwnProperty.call(TAB_MAP, tabName)) {
        const targetId = TAB_MAP[tabName];
        if (
          targetId !== undefined &&
          headerStore.activeHeaderByGroup["problem-info"] !== targetId
        ) {
          isUpdatingFromRoute.value = true;
          headerStore.setActiveHeader("problem-info", targetId);
          // Reset flag after next tick to allow store update to complete
          nextTick(() => {
            isUpdatingFromRoute.value = false;
          });
        }
      } else if (!tabName) {
        // Default to description if no tab specified
        if (headerStore.activeHeaderByGroup["problem-info"] !== 1) {
          isUpdatingFromRoute.value = true;
          headerStore.setActiveHeader("problem-info", 1);
          nextTick(() => {
            isUpdatingFromRoute.value = false;
          });
        }
      }
    },
  );

  // Sync Store to URL (when user clicks tabs)
  watch(
    () => headerStore.activeHeaderByGroup["problem-info"],
    (newHeaderId) => {
      // Skip if we're updating from route (prevent loop)
      if (isUpdatingFromRoute.value) return;

      if (newHeaderId && newHeaderId in REV_TAB_MAP) {
        const tabName = REV_TAB_MAP[newHeaderId];
        if (!tabName) return;
        if (tabName !== lastTab.value) {
          void problemHooks.emit("problem:tab:change", {
            from: lastTab.value,
            to: tabName,
          });
          lastTab.value = tabName;
        }
        if (route.params.tab !== tabName) {
          isUpdatingFromStore.value = true;
          router
            .push({
              name: "problem-detail",
              params: { ...route.params, tab: tabName },
            })
            .then(() => {
              // Reset flag after navigation completes
              nextTick(() => {
                isUpdatingFromStore.value = false;
              });
            });
        }
      }
    },
  );

  const initializeTab = () => {
    // Restore tab from URL
    const tabParam = route.params.tab;
    const tabName = Array.isArray(tabParam) ? tabParam[0] : tabParam;
    if (tabName) {
      const targetId = TAB_MAP[tabName];
      if (targetId !== undefined) {
        headerStore.setActiveHeader("problem-info", targetId);
      }
      lastTab.value = tabName;
    } else {
      lastTab.value = "description";
    }
  };

  return {
    lastTab,
    initializeTab,
  };
}
