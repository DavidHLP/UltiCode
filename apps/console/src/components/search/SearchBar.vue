<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import { Kbd } from "@/components/ui/kbd";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Search } from "lucide-vue-next";
import GlobalSearch from "./GlobalSearch.vue";

const { t } = useI18n();
const showSearch = ref(false);
const shortcutLabel = /Mac|iPhone|iPad|iPod/.test(navigator.userAgent)
  ? "⌘ K"
  : "Ctrl K";

function openSearch() {
  showSearch.value = true;
}
</script>

<template>
  <div>
    <Tooltip>
      <TooltipTrigger as-child>
        <Button
          variant="outline"
          size="sm"
          class="size-9 p-0 sm:h-[var(--uc-layout-control-height)] sm:w-44 sm:justify-start sm:gap-[var(--uc-layout-control-gap)] sm:px-[var(--uc-layout-control-padding-inline)]"
          :aria-label="t('common.actions.search')"
          @click="openSearch"
        >
          <Search data-icon="inline-start" />
          <span class="hidden sm:inline">{{ t("common.actions.search") }}</span>
          <Kbd class="ml-auto hidden lg:inline-flex">{{ shortcutLabel }}</Kbd>
        </Button>
      </TooltipTrigger>
      <TooltipContent side="bottom">
        {{ t("common.actions.search") }} · {{ shortcutLabel }}
      </TooltipContent>
    </Tooltip>

    <GlobalSearch v-model:open="showSearch" />
  </div>
</template>
