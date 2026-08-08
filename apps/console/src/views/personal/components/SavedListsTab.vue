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
import { Separator } from "@/components/ui/separator";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
} from "@/components/ui/dropdown-menu";
import {
  MoreHorizontal,
  Bookmark,
  BookmarkMinus,
  FolderInput,
  ChevronRight,
  Pencil,
  Trash2,
  List,
  MoreVertical,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";

defineProps<{
  savedLists: ProblemList[];
  categories: ProblemListCategory[];
  allSavedCount: number;
}>();

const emit = defineEmits<{
  (e: "unsave", list: ProblemList): void;
  (e: "moveToCategory", list: ProblemList, categoryId: string | null): void;
  (e: "editCategory", category: ProblemListCategory): void;
  (e: "deleteCategory", category: ProblemListCategory): void;
}>();

const { t } = useI18n();
</script>

<template>
  <!-- Uncategorized Saved Lists -->
  <div v-if="savedLists.length > 0" class="space-y-8">
    <div class="flex items-center gap-3 mb-4">
      <h3
        class="text-lg font-black uppercase tracking-widest text-muted-foreground"
      >
        {{ t("personal.problemLists.categories.uncategorized") }}
      </h3>
      <Separator class="flex-1" />
    </div>
    <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
      <Card
        v-for="list in savedLists"
        :key="list.id"
        class="group hover:shadow-[var(--shadow-float)] transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-none"
      >
        <CardHeader class="pb-3">
          <div class="flex items-start justify-between">
            <div class="space-y-1.5 flex-1 min-w-0">
              <Badge
                variant="secondary"
                class="h-5 px-1.5 text-2xs font-bold uppercase tracking-widest bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] border-[var(--accent-electric)]/20 rounded-none"
              >
                {{ t("personal.problemLists.listCard.saved") }}
              </Badge>
              <CardTitle
                class="text-lg font-bold group-hover:text-primary transition-colors truncate"
              >
                <RouterLink :to="`/problemset/list/${list.id}`">{{
                  list.name
                }}</RouterLink>
              </CardTitle>
            </div>

            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-8 w-8 rounded-full"
                >
                  <MoreHorizontal class="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" class="w-56">
                <DropdownMenuSub v-if="categories.length > 0">
                  <DropdownMenuSubTrigger class="gap-2">
                    <FolderInput class="h-4 w-4" />
                    {{ t("personal.problemLists.actions.moveToCategory") }}
                  </DropdownMenuSubTrigger>
                  <DropdownMenuSubContent>
                    <DropdownMenuItem
                      v-for="cat in categories"
                      :key="cat.id"
                      @click.prevent="emit('moveToCategory', list, cat.id)"
                    >
                      {{ cat.name }}
                    </DropdownMenuItem>
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
                <DropdownMenuItem
                  @click.prevent="emit('unsave', list)"
                  class="text-muted-foreground gap-2"
                >
                  <BookmarkMinus class="h-4 w-4" />
                  {{ t("personal.problemLists.actions.unsaveList") }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </CardHeader>
        <CardContent class="flex-1">
          <p
            v-if="list.description"
            class="text-sm text-muted-foreground line-clamp-2"
          >
            {{ list.description }}
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
  </div>

  <!-- Categories with their lists -->
  <div v-for="category in categories" :key="category.id" class="space-y-4">
    <Collapsible :default-open="true">
      <div class="flex items-center gap-3 mb-2 group/cat">
        <CollapsibleTrigger
          class="flex items-center gap-3 hover:text-primary transition-colors"
        >
          <ChevronRight
            class="h-5 w-5 transition-transform duration-300 ui-open:rotate-90 text-muted-foreground"
          />
          <h3 class="text-lg font-black uppercase tracking-widest">
            {{ category.name }}
          </h3>
          <Badge
            variant="secondary"
            class="h-5 px-1.5 rounded-full text-2xs"
            >{{ category.lists.length }}</Badge
          >
        </CollapsibleTrigger>
        <Separator class="flex-1 opacity-50" />
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8 rounded-full opacity-0 group-hover/cat:opacity-100 transition-opacity"
            >
              <MoreVertical class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem
              @click="emit('editCategory', category)"
              class="gap-2"
            >
              <Pencil class="h-4 w-4" />
              {{ t("personal.problemLists.actions.renameCategory") }}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              class="text-destructive focus:text-destructive gap-2"
              @click="emit('deleteCategory', category)"
            >
              <Trash2 class="h-4 w-4" />
              {{ t("personal.problemLists.actions.deleteCategory") }}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <CollapsibleContent class="pt-4 pb-8">
        <div
          v-if="category.lists.length === 0"
          class="flex flex-col items-center justify-center py-12 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-muted-foreground"
        >
          <FolderInput class="h-10 w-10 opacity-20 mb-3" />
          <p class="text-sm font-medium">
            {{ t("personal.problemLists.emptyStates.noListsInCategory") }}
          </p>
        </div>
        <div v-else class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          <Card
            v-for="list in category.lists"
            :key="list.id"
            class="group hover:shadow-[var(--shadow-float)] transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-none"
          >
            <CardHeader class="pb-3">
              <div class="flex items-start justify-between">
                <CardTitle
                  class="text-lg font-bold group-hover:text-primary transition-colors truncate"
                >
                  <RouterLink :to="`/problemset/list/${list.id}`">{{
                    list.name
                  }}</RouterLink>
                </CardTitle>

                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-8 w-8 rounded-full"
                    >
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" class="w-56">
                    <DropdownMenuItem
                      @click.prevent="emit('moveToCategory', list, null)"
                      class="gap-2"
                    >
                      <FolderInput class="h-4 w-4" />
                      {{
                        t("personal.problemLists.actions.removeFromCategory")
                      }}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      @click.prevent="emit('unsave', list)"
                      class="text-muted-foreground gap-2"
                    >
                      <BookmarkMinus class="h-4 w-4" />
                      {{ t("personal.problemLists.actions.unsaveList") }}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </CardHeader>
            <CardFooter class="bg-muted/20 border-t py-3 px-6 mt-auto">
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
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Empty State for Saved -->
  <div
    v-if="allSavedCount === 0 && categories.length === 0"
    class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center px-6"
  >
    <div
      class="p-0 flex items-center justify-center w-16 h-16 rounded-none bg-muted/50 mb-4 text-muted-foreground/40"
    >
      <Bookmark class="h-8 w-8" />
    </div>
    <h4 class="text-xl font-bold">
      {{ t("personal.problemLists.emptyStates.noSaved") }}
    </h4>
    <p class="text-sm text-muted-foreground mt-1 max-w-[300px]">
      {{ t("personal.problemLists.emptyStates.noSavedDesc") }}
    </p>
  </div>
</template>
