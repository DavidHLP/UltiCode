<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useEditorSettingsStore } from "@/stores/editorSettings";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Accessibility, Minus, Contrast } from "lucide-vue-next";

const { t } = useI18n();
const editorSettings = useEditorSettingsStore();
</script>

<template>
  <Popover>
    <PopoverTrigger as-child>
      <Button
        variant="ghost"
        size="icon"
        class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground data-[state=open]:bg-accent data-[state=open]:text-accent-foreground"
        :aria-label="t('problem.accessibility.title')"
        :title="t('problem.accessibility.title')"
      >
        <Accessibility class="h-3.5 w-3.5" aria-hidden="true" />
      </Button>
    </PopoverTrigger>
    <PopoverContent class="w-72" align="end">
      <div class="space-y-4">
        <h4 class="font-medium text-sm">
          {{ t("problem.accessibility.title") }}
        </h4>

        <!-- Reduce Motion -->
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1">
            <Label
              for="reduce-motion"
              class="flex items-center gap-2 text-sm font-normal cursor-pointer"
            >
              <Minus class="h-4 w-4 text-muted-foreground" aria-hidden="true" />
              {{ t("problem.accessibility.reduceMotion") }}
            </Label>
            <p class="text-xs text-muted-foreground mt-0.5">
              {{ t("problem.accessibility.reduceMotionDesc") }}
            </p>
          </div>
          <Switch
            id="reduce-motion"
            :checked="editorSettings.settings.reduceMotion"
            @update:checked="editorSettings.setReduceMotion"
          />
        </div>

        <!-- High Contrast -->
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1">
            <Label
              for="high-contrast"
              class="flex items-center gap-2 text-sm font-normal cursor-pointer"
            >
              <Contrast
                class="h-4 w-4 text-muted-foreground"
                aria-hidden="true"
              />
              {{ t("problem.accessibility.highContrast") }}
            </Label>
            <p class="text-xs text-muted-foreground mt-0.5">
              {{ t("problem.accessibility.highContrastDesc") }}
            </p>
          </div>
          <Switch
            id="high-contrast"
            :checked="editorSettings.settings.highContrast"
            @update:checked="editorSettings.setHighContrast"
          />
        </div>
      </div>
    </PopoverContent>
  </Popover>
</template>
