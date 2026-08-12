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
        class="flex h-8 w-8 items-center justify-center rounded-md border border-border-control bg-surface-sunken text-foreground-muted transition-colors hover:border-primary hover:bg-surface-highlight hover:text-foreground-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Globe class="h-4 w-4" />
        <span class="sr-only">{{ $t('common.actions.toggleLanguage') }}</span>
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContent
      align="end"
      class="min-w-40 rounded-lg border-border-control bg-surface-elevated p-1.5 shadow-float animate-in fade-in-0 zoom-in-95 duration-200"
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
            {{ localeConfig.code.split('-')[0].toUpperCase() }}
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
    </DropdownMenuContent>
  </DropdownMenu>
</template>
