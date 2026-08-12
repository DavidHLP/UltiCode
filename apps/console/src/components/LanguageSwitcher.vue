<script setup lang="ts">
import { useLocale } from "@/composables/useLocale";
import {
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import IconGlobe from "~icons/lucide/globe";
import { Check } from "lucide-vue-next";

const { availableLocales, setLocale, isCurrentLocale } = useLocale();
</script>

<template>
  <DropdownMenuSub>
    <DropdownMenuSubTrigger class="cursor-pointer rounded-md">
      <IconGlobe class="mr-2 h-4 w-4" />
      <span>{{ $t("common.actions.toggleLanguage") }}</span>
    </DropdownMenuSubTrigger>
    <DropdownMenuSubContent
      class="min-w-40 rounded-lg border-border-control bg-surface-elevated p-1.5 shadow-float"
    >
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="flex cursor-pointer items-center justify-between rounded-md px-3 py-2 transition-colors duration-200"
        :class="[
          isCurrentLocale(localeConfig.code)
            ? 'bg-surface-highlight text-foreground-strong font-semibold'
            : 'text-foreground-muted',
        ]"
        @click="setLocale(localeConfig.code)"
      >
        <div class="flex items-center gap-3">
          <span
            aria-hidden="true"
            class="inline-flex size-6 shrink-0 items-center justify-center rounded-sm border border-border-control bg-surface-sunken font-data text-[10px] font-semibold tracking-wide text-foreground-muted"
          >
            {{ localeConfig.code.split("-")[0].toUpperCase() }}
          </span>
          <span class="text-xxs uppercase tracking-widest font-data">{{
            localeConfig.nativeName
          }}</span>
        </div>
        <Check
          v-if="isCurrentLocale(localeConfig.code)"
          class="size-3.5 text-primary animate-in zoom-in-50 duration-300"
        />
      </DropdownMenuItem>
    </DropdownMenuSubContent>
  </DropdownMenuSub>
</template>
