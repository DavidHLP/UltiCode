<script setup lang="ts">
import { useLocale } from "@/composables/useLocale";
import {
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
  DropdownMenuItem,
} from "@ulticode/design-system/ui/dropdown-menu";
import IconGlobe from "~icons/lucide/globe";
import { Check } from "@lucide/vue";

const { availableLocales, setLocale, isCurrentLocale } = useLocale();
</script>

<template>
  <DropdownMenuSub>
    <DropdownMenuSubTrigger class="cursor-pointer">
      <IconGlobe class="mr-2 h-4 w-4" />
      <span>{{ $t("common.actions.toggleLanguage") }}</span>
    </DropdownMenuSubTrigger>
    <DropdownMenuSubContent class="min-w-40 p-1.5">
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="flex cursor-pointer items-center justify-between px-3 py-2"
        :class="[
          isCurrentLocale(localeConfig.code)
            ? 'bg-accent text-foreground font-semibold'
            : 'text-muted-foreground',
        ]"
        @click="setLocale(localeConfig.code)"
      >
        <div class="flex items-center gap-3">
          <span
            aria-hidden="true"
            class="inline-flex size-6 shrink-0 items-center justify-center rounded-sm border border-input bg-muted font-mono text-xs font-semibold tracking-wide text-muted-foreground"
          >
            {{ localeConfig.code.split("-")[0].toUpperCase() }}
          </span>
          <span class="text-xs uppercase tracking-widest font-mono">{{
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
