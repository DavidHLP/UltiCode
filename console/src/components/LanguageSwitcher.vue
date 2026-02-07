<script setup lang="ts">
import { useLocale } from "@/composables/useLocale";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import IconGlobe from "~icons/lucide/globe";

const { availableLocales, setLocale, isCurrentLocale } = useLocale();
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="icon" class="h-8 w-8">
        <IconGlobe class="h-4 w-4" />
        <span class="sr-only">{{ $t("common.actions.toggleLanguage") }}</span>
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end">
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        :class="{ 'bg-accent': isCurrentLocale(localeConfig.code) }"
        @click="setLocale(localeConfig.code)"
      >
        <span class="mr-2">{{ localeConfig.flag }}</span>
        <span>{{ localeConfig.nativeName }}</span>
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
