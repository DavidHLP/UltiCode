<script setup lang="ts">
import { RouterLink } from "vue-router";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import {
  MoreHorizontal,
  Pencil,
  Trash2,
  List,
  Globe,
  Lock,
  Plus,
  LayoutGrid,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ProblemList } from "@/types/problem-list";

defineProps<{
  lists: ProblemList[];
}>();

const emit = defineEmits<{
  (e: "delete", list: ProblemList): void;
  (e: "create"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <div
    v-if="lists.length === 0"
    class="flex flex-col items-center justify-center py-24 text-center px-6 border-2 border-dashed border-muted/50 rounded-none bg-muted/5"
  >
    <div
      class="p-0 flex items-center justify-center w-16 h-16 rounded-none bg-muted/50 mb-4"
    >
      <LayoutGrid class="h-8 w-8 text-muted-foreground/50" />
    </div>
    <h4 class="text-xl font-bold">
      {{ t("personal.problemLists.emptyStates.noLists") }}
    </h4>
    <p class="text-sm text-muted-foreground mt-1 max-w-[300px] mb-8">
      {{ t("personal.problemLists.emptyStates.noListsDesc") }}
    </p>
    <Button
      size="lg"
      @click="emit('create')"
      class="rounded-full gap-2 px-8 h-10 font-bold"
    >
      <Plus class="h-4 w-4" />
      {{ t("personal.problemLists.emptyStates.createFirst") }}
    </Button>
  </div>

  <div v-else class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
    <Card
      v-for="list in lists"
      :key="list.id"
      class="group hover:shadow-[var(--shadow-float)] transition-all duration-300 border-muted/60 overflow-hidden flex flex-col rounded-none"
    >
      <CardHeader class="pb-3">
        <div class="flex items-start justify-between">
          <div class="space-y-1.5 flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <Badge
                v-if="list.isPublic"
                variant="secondary"
                class="h-5 px-1.5 text-2xs font-semibold uppercase tracking-widest bg-[var(--terminal-green)]/10 text-[var(--terminal-green)] border-[var(--terminal-green)]/20 rounded-none"
              >
                <Globe class="h-3 w-3 mr-1" />
                {{ t("personal.problemLists.listCard.public") }}
              </Badge>
              <Badge
                v-else
                variant="outline"
                class="h-5 px-1.5 text-2xs font-semibold uppercase tracking-widest text-muted-foreground border-muted-foreground/20 rounded-none"
              >
                <Lock class="h-3 w-3 mr-1" />
                {{ t("personal.problemLists.listCard.private") }}
              </Badge>
            </div>
            <CardTitle
              class="text-lg font-semibold group-hover:text-primary transition-colors truncate"
            >
              <RouterLink :to="`/problemset/list/${list.id}`">{{
                list.name
              }}</RouterLink>
            </CardTitle>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="icon" class="h-8 w-8 rounded-full">
                <MoreHorizontal class="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" class="w-48">
              <DropdownMenuItem as-child class="gap-2">
                <RouterLink :to="`/problemset/list/${list.id}`">
                  <Pencil class="h-4 w-4" />
                  {{ t("personal.problemLists.actions.editList") }}
                </RouterLink>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                class="text-destructive focus:text-destructive gap-2"
                @click.prevent="emit('delete', list)"
              >
                <Trash2 class="h-4 w-4" />
                {{ t("personal.problemLists.actions.deleteList") }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </CardHeader>
      <CardContent class="flex-1">
        <p
          v-if="list.description"
          class="text-sm text-muted-foreground line-clamp-2 min-h-[40px]"
        >
          {{ list.description }}
        </p>
        <p v-else class="text-sm text-muted-foreground/40 italic">
          {{ t("personal.problemLists.listCard.noDescription") }}
        </p>
      </CardContent>
      <CardFooter class="bg-muted/20 border-t py-3 px-6">
        <div
          class="flex items-center gap-2 text-xs font-bold text-muted-foreground"
        >
          <List class="h-4 w-4 text-primary/70" />
          {{
            t("personal.problemLists.listCard.problemCount", {
              count: list.problemCount,
            })
          }}
        </div>
      </CardFooter>
    </Card>
  </div>
</template>
