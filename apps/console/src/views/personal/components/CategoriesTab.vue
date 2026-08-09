<script setup lang="ts">
import { RouterLink } from "vue-router";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
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
  BookmarkMinus,
  FolderInput,
  Folder,
  GripVertical,
  List,
  Pencil,
  Trash2,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ProblemListCategory } from "@/types/problem-list";

defineProps<{
  categories: ProblemListCategory[];
  allCategories: ProblemListCategory[];
}>();

const emit = defineEmits<{
  (e: "edit", category: ProblemListCategory): void;
  (e: "delete", category: ProblemListCategory): void;
  (e: "moveToCategory", listId: string, categoryId: string | null): void;
  (e: "unsave", listId: string): void;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="grid gap-6">
    <Card
      v-for="category in categories"
      :key="category.id"
      class="border-muted/60 hover:shadow-[var(--shadow-float)] transition-shadow duration-300 rounded-none overflow-hidden"
    >
      <div class="flex items-center justify-between px-6 py-4 bg-muted/30">
        <div class="flex items-center gap-4">
          <GripVertical class="h-5 w-5 text-muted-foreground/30 cursor-grab" />
          <div
            class="h-10 w-10 rounded-none bg-primary/10 flex items-center justify-center"
          >
            <Folder class="h-5 w-5 text-primary" />
          </div>
          <div>
            <h4 class="text-lg font-black tracking-tight">
              {{ category.name }}
            </h4>
            <p
              class="text-xs font-bold text-muted-foreground uppercase tracking-widest"
            >
              {{
                t("personal.problemLists.categories.listCount", {
                  count: category.lists.length,
                })
              }}
            </p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            class="rounded-full"
            @click="emit('edit', category)"
          >
            <Pencil class="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            class="rounded-full text-destructive hover:text-destructive"
            @click="emit('delete', category)"
          >
            <Trash2 class="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div class="p-4 sm:p-6">
        <div
          v-if="category.lists.length === 0"
          class="text-center py-10 bg-muted/10 rounded-none border-2 border-dashed"
        >
          <p class="text-sm text-muted-foreground italic">
            {{ t("personal.problemLists.emptyStates.noListsInCategoryDesc") }}
          </p>
        </div>
        <div v-else class="grid gap-3">
          <div
            v-for="list in category.lists"
            :key="list.id"
            class="flex items-center justify-between p-4 rounded-none bg-muted/20 hover:bg-muted/40 transition-all group"
          >
            <RouterLink
              :to="`/problemset/list/${list.id}`"
              class="flex items-center gap-4 flex-1 min-w-0"
            >
              <List class="h-4 w-4 text-primary/60" />
              <span class="font-bold truncate">{{ list.name }}</span>
              <Badge
                variant="secondary"
                class="h-5 px-1.5 rounded-full text-2xs"
              >
                {{
                  t("personal.problemLists.listCard.problemsCount", {
                    count: list.problemCount,
                  })
                }}
              </Badge>
            </RouterLink>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-8 w-8 rounded-full opacity-0 group-hover:opacity-100"
                >
                  <MoreHorizontal class="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" class="w-56">
                <DropdownMenuSub v-if="allCategories.length > 1">
                  <DropdownMenuSubTrigger class="gap-2">
                    <FolderInput class="h-4 w-4" />
                    {{ t("personal.problemLists.actions.moveToAnother") }}
                  </DropdownMenuSubTrigger>
                  <DropdownMenuSubContent>
                    <DropdownMenuItem
                      v-for="cat in allCategories.filter(
                        (c) => c.id !== category.id,
                      )"
                      :key="cat.id"
                      @click="emit('moveToCategory', list.id, cat.id)"
                    >
                      {{ cat.name }}
                    </DropdownMenuItem>
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
                <DropdownMenuItem
                  @click="emit('moveToCategory', list.id, null)"
                  class="gap-2"
                >
                  <FolderInput class="h-4 w-4" />
                  {{ t("personal.problemLists.actions.removeFromCategory") }}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  @click="emit('unsave', list.id)"
                  class="text-muted-foreground gap-2"
                >
                  <BookmarkMinus class="h-4 w-4" />
                  {{ t("personal.problemLists.actions.unsaveList") }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>
