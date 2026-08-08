<script setup lang="ts">
import { computed } from "vue";
import { useTheme } from '@/shared/theme/src';
import { useI18n } from "vue-i18n";
import {
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { Sun, Moon, Laptop, Check } from "lucide-vue-next";

const { theme: themeRef, setTheme } = useTheme();
// vue-tsc 3.x does not auto-unwrap `Ref<T>` in template comparisons or
// function arguments; expose the value as a `ComputedRef` to match the
// project convention (see `useLocale`).
const theme = computed(() => themeRef.value);
const { t } = useI18n();
</script>

<template>
  <DropdownMenuSub>
    <DropdownMenuSubTrigger class="cursor-pointer">
      <Sun
        v-if="theme === 'light'"
        class="mr-2 h-4 w-4 text-[var(--solarized-yellow)]"
      />
      <Moon
        v-else-if="theme === 'dark'"
        class="mr-2 h-4 w-4 text-[var(--solarized-blue)]"
      />
      <Laptop v-else class="mr-2 h-4 w-4" />
      <span>{{ t("common.appearance.theme") }}</span>
    </DropdownMenuSubTrigger>
    <DropdownMenuSubContent class="min-w-40 p-1.5">
      <DropdownMenuItem
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'light'
            ? 'bg-accent/50 text-accent-foreground font-bold'
            : '',
        ]"
        @click="setTheme('light')"
      >
        <div class="flex items-center gap-3">
          <Sun class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t("common.appearance.light")
          }}</span>
        </div>
        <Check
          v-if="theme === 'light'"
          class="size-3.5 text-[var(--accent-primary)]"
        />
      </DropdownMenuItem>

      <DropdownMenuItem
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'dark'
            ? 'bg-accent/50 text-accent-foreground font-bold'
            : '',
        ]"
        @click="setTheme('dark')"
      >
        <div class="flex items-center gap-3">
          <Moon class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t("common.appearance.dark")
          }}</span>
        </div>
        <Check
          v-if="theme === 'dark'"
          class="size-3.5 text-[var(--accent-primary)]"
        />
      </DropdownMenuItem>

      <DropdownMenuItem
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'system'
            ? 'bg-accent/50 text-accent-foreground font-bold'
            : '',
        ]"
        @click="setTheme('system')"
      >
        <div class="flex items-center gap-3">
          <Laptop class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t("common.appearance.system")
          }}</span>
        </div>
        <Check
          v-if="theme === 'system'"
          class="size-3.5 text-[var(--accent-primary)]"
        />
      </DropdownMenuItem>
    </DropdownMenuSubContent>
  </DropdownMenuSub>
</template>
