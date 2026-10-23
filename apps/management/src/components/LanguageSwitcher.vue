<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'

import { useLocale } from '@/composables/useLocale'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuGroup,
} from '@ulticode/design-system/ui/dropdown-menu'
import { Check, Globe } from '@lucide/vue'

const { availableLocales, setLocale, isCurrentLocale } = useLocale()
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" class="uc-header-icon">
        <Globe class="h-4 w-4" />
        <span class="sr-only">{{ $t('common.actions.toggleLanguage') }}</span>
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end" class="min-w-40">
      <DropdownMenuGroup
        ><DropdownMenuItem
          v-for="localeConfig in availableLocales"
          :key="localeConfig.code"
          class="cursor-pointer gap-4"
          @click="setLocale(localeConfig.code)"
        >
          <span class="flex-1">{{ localeConfig.nativeName }}</span>
          <Check
            v-if="isCurrentLocale(localeConfig.code)"
            class="size-4 shrink-0"
          /> </DropdownMenuItem
      ></DropdownMenuGroup>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
