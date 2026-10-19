<script setup lang="ts">
import { platformUrl } from "@/utils/platformUrl";
import { useI18n } from "vue-i18n";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@ulticode/design-system/ui/tabs";
const { t } = useI18n();
const sampleCode = `template<typename Key, typename Val>
class LRUCache {
    size_t capacity_;
    std::list<std::pair<Key, Val>> items_;
    std::unordered_map<Key, iterator> index_;
};`;
</script>
<template>
  <section
    id="proof"
    class="mx-auto grid max-w-7xl gap-10 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:py-24"
    aria-labelledby="hero-title"
  >
    <div class="flex min-w-0 flex-col items-start justify-center gap-6">
      <Badge
        variant="secondary"
        class="h-auto max-w-full whitespace-normal text-start"
        >{{ t("landing.hero.badge") }}</Badge
      >
      <h1
        id="hero-title"
        class="text-4xl font-semibold tracking-tight sm:text-5xl"
      >
        {{ t("landing.hero.title") }}
        <span class="text-primary">{{ t("landing.hero.titleItalic") }}</span>
      </h1>
      <p class="text-lg text-muted-foreground">
        {{ t("landing.hero.description") }}
      </p>
      <div class="flex flex-wrap gap-3">
        <Button size="lg" as-child
          ><a :href="platformUrl('/problemset')">{{
            t("landing.hero.ctaPrimary")
          }}</a></Button
        ><Button size="lg" variant="outline" as-child
          ><a :href="platformUrl('/contest')">{{
            t("landing.useCases.case2Action")
          }}</a></Button
        >
      </div>
    </div>
    <Card class="min-w-0">
      <CardHeader
        ><CardTitle>{{ t("landing.hero.sampleWindowTag") }}</CardTitle
        ><CardDescription>{{
          t("landing.hero.sampleFootnote")
        }}</CardDescription></CardHeader
      >
      <CardContent>
        <Tabs default-value="source"
          ><TabsList
            ><TabsTrigger value="source">Source</TabsTrigger
            ><TabsTrigger value="telemetry">Telemetry</TabsTrigger></TabsList
          >
          <TabsContent value="source">
            <pre
              class="overflow-x-auto rounded-lg bg-muted p-4 text-sm"
            ><code>{{ sampleCode }}</code></pre>
          </TabsContent>
          <TabsContent value="telemetry"
            ><dl class="grid gap-4 py-4 text-sm">
              <div>
                <dt class="text-muted-foreground">
                  {{ t("landing.hero.peakMemory") }}
                </dt>
                <dd>3,840 KiB</dd>
              </div>
              <div>
                <dt class="text-muted-foreground">
                  {{ t("landing.hero.seccompFilter") }}
                </dt>
                <dd>STRICT</dd>
              </div>
              <div>
                <dt class="text-muted-foreground">
                  {{ t("landing.hero.testsPassed") }}
                </dt>
                <dd>48 / 48</dd>
              </div>
            </dl></TabsContent
          >
        </Tabs>
      </CardContent>
      <CardFooter class="flex flex-wrap items-center justify-between gap-3"
        ><Badge variant="secondary">{{ t("landing.hero.sampleStatus") }}</Badge
        ><Button
          variant="outline"
          class="h-auto max-w-full whitespace-normal py-2"
          as-child
          ><a :href="platformUrl('/problemset')">{{
            t("landing.proof.workspaceCta")
          }}</a></Button
        ></CardFooter
      >
    </Card>
  </section>
</template>
