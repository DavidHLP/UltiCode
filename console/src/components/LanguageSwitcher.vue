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
    <DropdownMenuSubTrigger class="cursor-pointer">
      <IconGlobe class="mr-2 h-4 w-4" />
      <span>{{ $t("common.actions.toggleLanguage") }}</span>
    </DropdownMenuSubTrigger>
    <DropdownMenuSubContent class="min-w-40 p-1.5">
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          isCurrentLocale(localeConfig.code)
            ? 'bg-accent/50 text-accent-foreground font-bold'
            : '',
        ]"
        @click="setLocale(localeConfig.code)"
      >
        <div class="flex items-center gap-3">
          <span class="text-base leading-none">{{ localeConfig.flag }}</span>
          <span class="text-xxs uppercase tracking-widest font-data">{{
            localeConfig.nativeName
          }}</span>
        </div>
        <Check
          v-if="isCurrentLocale(localeConfig.code)"
          class="size-3.5 text-[var(--accent-primary)] animate-in zoom-in-50 duration-300"
        />
      </DropdownMenuItem>
    </DropdownMenuSubContent>
  </DropdownMenuSub>
</template>
