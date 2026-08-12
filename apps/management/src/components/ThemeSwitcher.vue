<script setup lang="ts">
import { computed } from 'vue'
import { useTheme } from '@/shared/theme/src'
import { useI18n } from 'vue-i18n'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sun, Moon, Laptop, Check } from 'lucide-vue-next'

const { theme: themeRef, setTheme } = useTheme()
// vue-tsc 3.x does not auto-unwrap `Ref<T>` in template comparisons or
// function arguments; expose the value as a `ComputedRef` to match the
// project convention (see `useLocale`).
const theme = computed(() => themeRef.value)
const { t } = useI18n()
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <button
        class="h-8 w-8 flex items-center justify-center text-foreground hover:text-[var(--accent-primary)] border border-control bg-transparent hover:bg-[var(--surface-highlight)]/50 hover:border-[var(--accent-primary)] transition-all rounded-none cursor-pointer outline-none focus-visible:ring-1 focus-visible:ring-[var(--accent-primary)]"
        :title="t('settings.appearance.theme')"
      >
        <Sun v-if="theme === 'light'" class="h-4 w-4 text-[var(--status-warning-mark)]" />
        <Moon v-else-if="theme === 'dark'" class="h-4 w-4 text-[var(--primary)]" />
        <Laptop v-else class="h-4 w-4" />
        <span class="sr-only">{{ t('settings.appearance.theme') }}</span>
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContent
      variant="terminal"
      align="end"
      class="min-w-40 p-1.5 animate-in fade-in-0 zoom-in-95 duration-200"
    >
      <DropdownMenuItem
        variant="terminal"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'light'
            ? 'bg-[var(--surface-highlight)]/40 text-foreground-strong font-bold'
            : '',
        ]"
        @click="setTheme('light')"
      >
        <div class="flex items-center gap-3">
          <Sun class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t('settings.appearance.light')
          }}</span>
        </div>
        <Check v-if="theme === 'light'" class="size-3.5 text-[var(--accent-primary)]" />
      </DropdownMenuItem>

      <DropdownMenuItem
        variant="terminal"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'dark'
            ? 'bg-[var(--surface-highlight)]/40 text-[var(--accent-primary)] font-bold'
            : '',
        ]"
        @click="setTheme('dark')"
      >
        <div class="flex items-center gap-3">
          <Moon class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t('settings.appearance.dark')
          }}</span>
        </div>
        <Check v-if="theme === 'dark'" class="size-3.5 text-[var(--accent-primary)]" />
      </DropdownMenuItem>

      <DropdownMenuItem
        variant="terminal"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          theme === 'system'
            ? 'bg-[var(--surface-highlight)]/40 text-[var(--accent-primary)] font-bold'
            : '',
        ]"
        @click="setTheme('system')"
      >
        <div class="flex items-center gap-3">
          <Laptop class="h-3.5 w-3.5" />
          <span class="text-xxs uppercase tracking-widest font-data">{{
            t('settings.appearance.system')
          }}</span>
        </div>
        <Check v-if="theme === 'system'" class="size-3.5 text-[var(--accent-primary)]" />
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
