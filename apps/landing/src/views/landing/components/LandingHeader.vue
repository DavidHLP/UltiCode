<script setup lang="ts">
import { ref } from "vue";
import { useLocale } from "@/composables/useLocale";
import { platformUrl } from "@/utils/platformUrl";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@ulticode/design-system/ui/sheet";
import { openAppearanceSettings } from "@ulticode/theme";
import { Code, Menu, Paintbrush } from "@lucide/vue";
const { t, locale, toggleLocale } = useLocale();
const open = ref(false);
const items = [
  { href: "#proof", key: "proof" },
  { href: "#workflow", key: "workflow" },
  { href: "#governance", key: "governance" },
];
</script>
<template>
  <header class="sticky top-0 z-30 border-b bg-background">
    <div
      class="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6"
    >
      <a :href="platformUrl('/')" class="flex items-center gap-2 font-semibold"
        ><Code aria-hidden="true" />UltiCode</a
      >
      <nav
        class="hidden items-center gap-1 md:flex"
        :aria-label="t('landing.navigation')"
      >
        <Button v-for="item in items" :key="item.key" variant="ghost" as-child
          ><a :href="item.href">{{ t(`landing.nav.${item.key}`) }}</a></Button
        >
      </nav>
      <div class="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          :aria-label="t('landing.appearance')"
          @click="openAppearanceSettings"
          ><Paintbrush
        /></Button>
        <Button variant="ghost" size="sm" @click="toggleLocale">{{
          locale === "zh-CN" ? "EN" : "中"
        }}</Button>
        <Button variant="ghost" class="hidden sm:inline-flex" as-child
          ><a :href="platformUrl('/login')">{{
            t("landing.nav.login")
          }}</a></Button
        >
        <Button as-child
          ><a :href="platformUrl('/problemset')">{{
            t("landing.nav.enter")
          }}</a></Button
        >
        <Sheet v-model:open="open">
          <SheetTrigger as-child
            ><Button
              variant="outline"
              size="icon"
              class="md:hidden"
              :aria-label="t('landing.openMenu')"
              ><Menu /></Button
          ></SheetTrigger>
          <SheetContent>
            <SheetHeader
              ><SheetTitle>UltiCode</SheetTitle
              ><SheetDescription>{{
                t("landing.navigation")
              }}</SheetDescription></SheetHeader
            >
            <nav
              class="flex flex-col gap-2 p-4"
              :aria-label="t('landing.navigation')"
            >
              <Button
                v-for="item in items"
                :key="item.key"
                variant="ghost"
                as-child
                ><a :href="item.href" @click="open = false">{{
                  t(`landing.nav.${item.key}`)
                }}</a></Button
              >
              <Button variant="outline" as-child
                ><a :href="platformUrl('/login')">{{
                  t("landing.nav.login")
                }}</a></Button
              >
            </nav>
          </SheetContent>
        </Sheet>
      </div>
    </div>
  </header>
</template>
