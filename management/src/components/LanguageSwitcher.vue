<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { Check, Globe } from 'lucide-vue-next'

const { availableLocales, setLocale, isCurrentLocale } = useLocale()
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="icon" class="h-8 w-8 hover:bg-accent/50 transition-colors">
        <Globe class="h-4 w-4 text-muted-foreground hover:text-foreground transition-colors" />
        <span class="sr-only">{{ $t('common.actions.toggleLanguage') }}</span>
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent
      align="end"
      class="min-w-40 p-1.5 animate-in fade-in-0 zoom-in-95 duration-200"
    >
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="flex items-center justify-between cursor-pointer transition-all duration-200 px-3 py-2"
        :class="[
          isCurrentLocale(localeConfig.code)
            ? 'bg-accent/50 text-accent-foreground font-bold'
            : 'hover:bg-accent/30',
        ]"
        @click="setLocale(localeConfig.code)"
      >
        <div class="flex items-center gap-3">
          <span class="text-base leading-none">{{ localeConfig.flag }}</span>
          <span class="text-[11px] uppercase tracking-widest font-data">{{
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
