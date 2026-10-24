<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'
import {
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuItem,
  DropdownMenuSubTrigger,
} from '@ulticode/design-system/ui/dropdown-menu'
import { Check, Globe } from '@lucide/vue'

const { availableLocales, setLocale, isCurrentLocale } = useLocale()
</script>

<template>
  <DropdownMenuSub>
    <DropdownMenuSubTrigger>
      <Globe />
      {{ $t('common.actions.toggleLanguage') }}
    </DropdownMenuSubTrigger>
    <DropdownMenuSubContent class="min-w-40">
      <DropdownMenuItem
        v-for="localeConfig in availableLocales"
        :key="localeConfig.code"
        class="cursor-pointer gap-4"
        @select="setLocale(localeConfig.code)"
      >
        <span class="flex-1">{{ localeConfig.nativeName }}</span>
        <Check v-if="isCurrentLocale(localeConfig.code)" class="size-4 shrink-0" />
      </DropdownMenuItem>
    </DropdownMenuSubContent>
  </DropdownMenuSub>
</template>
