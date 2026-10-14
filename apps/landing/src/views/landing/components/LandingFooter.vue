<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { platformUrl } from "@/utils/platformUrl";
import { Separator } from "@ulticode/design-system/ui/separator";
const { t } = useI18n();
const columns = [
  { name: "platform", links: ["/problemset", "/contest", "/forum", "/login"] },
  { name: "spec", links: ["#proof", "#workflow", "#use-cases", "#governance"] },
  {
    name: "inst",
    links: ["#governance", "#governance", "#proof", "#main-content"],
  },
];
</script>
<template>
  <footer class="border-t px-4 py-10 sm:px-6">
    <div class="mx-auto flex max-w-7xl flex-col gap-8">
      <div class="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
        <div class="flex flex-col gap-3">
          <span class="font-semibold">UltiCode</span>
          <p class="text-sm text-muted-foreground">
            {{ t("landing.footer.manifesto") }}
          </p>
        </div>
        <nav
          v-for="column in columns"
          :key="column.name"
          class="flex flex-col gap-3"
          :aria-label="t(`landing.footer.${column.name}ColTitle`)"
        >
          <h3 class="text-sm font-semibold">
            {{ t(`landing.footer.${column.name}ColTitle`) }}
          </h3>
          <ul class="flex flex-col gap-2 text-sm text-muted-foreground">
            <li v-for="(link, index) in column.links" :key="link + index">
              <a
                :href="link.startsWith('#') ? link : platformUrl(link)"
                class="hover:text-foreground hover:underline"
                >{{ t(`landing.footer.${column.name}Link${index + 1}`) }}</a
              >
            </li>
          </ul>
        </nav>
      </div>
      <Separator />
      <div
        class="flex flex-wrap justify-between gap-3 text-sm text-muted-foreground"
      >
        <span>{{ t("landing.footer.copyright") }}</span
        ><span>{{ t("landing.footer.systemNominal") }}</span>
      </div>
    </div>
  </footer>
</template>
