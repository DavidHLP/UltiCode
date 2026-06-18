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
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";
import { RouterLink, useRoute } from "vue-router";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const route = useRoute();

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

const isListActive = (id: string | number) => {
  return route.path === `/problemset/list/${id}`;
};
</script>

<template>
  <!-- My Lists Section -->
  <div class="px-1 py-0.5" v-if="data.ownLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="flex items-center justify-between px-2 py-0.5 select-none">
        <CollapsibleTrigger
          class="group/trigger flex flex-1 items-center gap-1 text-2xs font-semibold tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] hover:text-[var(--accent-electric)] transition-colors"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--silver-400)] dark:text-[var(--silver-500)]"
          />
          <User
            class="h-3.5 w-3.5 mr-0.5 text-[var(--accent-electric)]/70 group-hover/trigger:text-[var(--accent-electric)] transition-colors"
          />
          <span>{{ t("sidebar.problemLists.myLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li v-for="item in data.ownLists" :key="item.id" class="group/item">
            <div
              :class="[
                'flex items-center justify-between gap-1 rounded-none px-3 py-1.5 transition-all duration-200 border-l-2 h-8.5 select-none text-xxs font-medium',
                isListActive(item.id)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/5 text-[var(--accent-electric)] font-semibold pl-3'
                  : 'border-transparent text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
              ]"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-2xs text-muted-foreground font-data">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity hover:text-[var(--accent-electric)]"
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
  <div class="px-1 py-0.5" v-if="data.savedLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="flex items-center justify-between px-2 py-0.5 select-none">
        <CollapsibleTrigger
          class="group/trigger flex flex-1 items-center gap-1 text-2xs font-semibold tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] hover:text-[var(--accent-electric)] transition-colors"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--silver-400)] dark:text-[var(--silver-500)]"
          />
          <Bookmark
            class="h-3.5 w-3.5 mr-0.5 text-[var(--terminal-green)]/70 group-hover/trigger:text-[var(--terminal-green)] transition-colors"
          />
          <span>{{ t("sidebar.problemLists.savedLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li v-for="item in data.savedLists" :key="item.id" class="group/item">
            <div
              :class="[
                'flex items-center justify-between gap-1 rounded-none px-3 py-1.5 transition-all duration-200 border-l-2 h-8.5 select-none text-xxs font-medium',
                isListActive(item.id)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/5 text-[var(--accent-electric)] font-semibold pl-3'
                  : 'border-transparent text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
              ]"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-2xs text-muted-foreground font-data">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity hover:text-[var(--accent-electric)]"
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
  <div class="px-1 py-0.5" v-if="data.featuredLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="flex items-center justify-between px-2 py-0.5 select-none">
        <CollapsibleTrigger
          class="group/trigger flex flex-1 items-center gap-1 text-2xs font-semibold tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] hover:text-[var(--accent-electric)] transition-colors"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--silver-400)] dark:text-[var(--silver-500)]"
          />
          <Star
            class="h-3.5 w-3.5 mr-0.5 text-[var(--terminal-amber)]/70 group-hover/trigger:text-[var(--terminal-amber)] transition-colors"
          />
          <span>{{ t("sidebar.problemLists.featured").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li
            v-for="item in data.featuredLists"
            :key="item.id"
            class="group/item"
          >
            <div
              :class="[
                'flex items-center justify-between gap-1 rounded-none px-3 py-1.5 transition-all duration-200 border-l-2 h-8.5 select-none text-xxs font-medium',
                isListActive(item.id)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/5 text-[var(--accent-electric)] font-semibold pl-3'
                  : 'border-transparent text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
              ]"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-2xs text-muted-foreground font-data">{{
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
  <div
    v-for="category in data.categories"
    :key="category.id"
    class="px-1 py-0.5"
  >
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="flex items-center justify-between px-2 py-0.5 select-none">
        <CollapsibleTrigger
          class="group/trigger flex flex-1 items-center gap-1 text-2xs font-semibold tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] hover:text-[var(--accent-electric)] transition-colors"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--silver-400)] dark:text-[var(--silver-500)]"
          />
          <span class="truncate">{{ category.name }}</span>
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

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li v-for="item in category.lists" :key="item.id" class="group/item">
            <div
              :class="[
                'flex items-center justify-between gap-1 rounded-none px-3 py-1.5 transition-all duration-200 border-l-2 h-8.5 select-none text-xxs font-medium',
                isListActive(item.id)
                  ? 'border-[var(--accent-electric)] bg-[var(--accent-electric)]/5 text-[var(--accent-electric)] font-semibold pl-3'
                  : 'border-transparent text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:bg-[var(--accent-electric)]/4 hover:text-[var(--accent-electric)] hover:translate-x-0.5',
              ]"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-2xs text-muted-foreground font-data">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity hover:text-[var(--accent-electric)]"
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
