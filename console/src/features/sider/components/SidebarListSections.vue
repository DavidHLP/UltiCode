<script setup lang="ts">
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import {
  ChevronRight,
  MoreHorizontal,
  Trash2,
  FolderInput,
  Star,
  Bookmark,
  User,
  BookmarkMinus,
  Pencil,
} from "lucide-vue-next";
import type {
  ProblemList,
  ProblemListCategory,
} from "@/types/problem-list";
import { RouterLink } from "vue-router";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const emit = defineEmits<{
  deleteList: [list: ProblemList];
  unsaveList: [list: ProblemList];
  moveListToCategory: [list: ProblemList, categoryId: string | null];
  editCategory: [category: ProblemListCategory];
  deleteCategory: [category: ProblemListCategory];
}>();

defineProps<{
  data: {
    ownLists: ProblemList[];
    savedLists: ProblemList[];
    featuredLists: ProblemList[];
    categories: (ProblemListCategory & { lists: ProblemList[] })[];
  };
  allCategories: ProblemListCategory[];
}>();
</script>

<template>
  <!-- My Lists Section -->
  <div class="px-4 py-2" v-if="data.ownLists.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <User class="h-4 w-4 mr-1 text-[var(--accent-electric)]" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.myLists")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.ownLists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-none px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-40">
                  <DropdownMenuItem
                    class="text-destructive focus:text-destructive"
                    @click="emit('deleteList', item)"
                  >
                    <Trash2 class="mr-2 h-4 w-4" />
                    {{ t("common.actions.delete") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Saved Lists Section -->
  <div class="px-4 py-2" v-if="data.savedLists.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <Bookmark class="h-4 w-4 mr-1 text-[var(--terminal-green)]" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.savedLists")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.savedLists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-none px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <DropdownMenuSub v-if="allCategories.length > 0">
                    <DropdownMenuSubTrigger>
                      <FolderInput class="mr-2 h-4 w-4" />
                      {{ t("problem.problemList.detail.moveToCategory") }}
                    </DropdownMenuSubTrigger>
                    <DropdownMenuSubContent class="w-40">
                      <DropdownMenuItem
                        v-for="cat in allCategories"
                        :key="cat.id"
                        @click="emit('moveListToCategory', item, cat.id)"
                      >
                        {{ cat.name }}
                      </DropdownMenuItem>
                    </DropdownMenuSubContent>
                  </DropdownMenuSub>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem @click="emit('unsaveList', item)">
                    <BookmarkMinus class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.unsave") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Featured Section -->
  <div class="px-4 py-2" v-if="data.featuredLists.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <Star class="h-4 w-4 mr-1 text-[var(--terminal-amber)]" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.featured")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.featuredLists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-none px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- User Categories -->
  <div v-for="category in data.categories" :key="category.id" class="px-4 py-2">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <span class="text-sm font-semibold">{{ category.name }}</span>
        </CollapsibleTrigger>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
            >
              <MoreHorizontal class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-40">
            <DropdownMenuItem @click="emit('editCategory', category)">
              <Pencil class="mr-2 h-4 w-4" />
              {{ t("common.actions.edit") }}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              class="text-destructive focus:text-destructive"
              @click="emit('deleteCategory', category)"
            >
              <Trash2 class="mr-2 h-4 w-4" />
              {{ t("common.actions.delete") }}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in category.lists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-none px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <DropdownMenuItem
                    @click="emit('moveListToCategory', item, null)"
                  >
                    <FolderInput class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.removeFromCategory") }}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem @click="emit('unsaveList', item)">
                    <BookmarkMinus class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.unsave") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>
</template>
