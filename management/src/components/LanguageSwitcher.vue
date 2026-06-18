<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Check, Globe } from 'lucide-vue-next'

const { availableLocales, setLocale, isCurrentLocale } = useLocale()
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <button
        class="h-8 w-8 flex items-center justify-center text-[var(--silver-500)] hover:text-[var(--accent-primary)] border border-[var(--silver-200)] dark:border-[var(--silver-300)]/50 bg-transparent hover:bg-[var(--silver-100)]/50 hover:border-[var(--accent-primary)] transition-all rounded-none cursor-pointer outline-none focus-visible:ring-1 focus-visible:ring-[var(--accent-primary)]"
      >
        <Globe class="h-4 w-4" />
        <span class="sr-only">{{ $t('common.actions.toggleLanguage') }}</span>
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContent
      variant="terminal"
      align="end"
      class="min-w-40 p-1.5 animate-in fade-in-0 zoom-in-95 duration-200"
    >
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        variant="terminal"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          isCurrentLocale(localeConfig.code)
            ? 'bg-[var(--silver-100)]/40 text-[var(--accent-primary)] font-bold'
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
    </DropdownMenuContent>
  </DropdownMenu>
</template>
