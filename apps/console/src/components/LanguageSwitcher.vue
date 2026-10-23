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
    <DropdownMenuSubContent class="min-w-40">
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="cursor-pointer gap-4"
        @click="setLocale(localeConfig.code)"
      >
        <span class="flex-1">{{ localeConfig.nativeName }}</span>
        <Check
          v-if="isCurrentLocale(localeConfig.code)"
          class="size-4 shrink-0"
        />
      </DropdownMenuItem>
    </DropdownMenuSubContent>
  </DropdownMenuSub>
</template>
