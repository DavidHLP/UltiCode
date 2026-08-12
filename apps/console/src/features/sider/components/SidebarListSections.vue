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
  AlertCircle,
  RefreshCw,
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
  retryLoad: [];
}>();

defineProps<{
  data: {
    ownLists: ProblemList[];
    savedLists: ProblemList[];
    featuredLists: ProblemList[];
    categories: (ProblemListCategory & { lists: ProblemList[] })[];
  };
  allCategories: ProblemListCategory[];
  hasError?: boolean;
}>();

const isListActive = (id: string | number) => {
  return route.path === `/problemset/list/${id}`;
};

// All problem-list rows share the same public sidebar-menu contract as the
// primary problem navigation. The row keeps its action menu as a sibling while
// `[data-active]` owns the active bar, surface, and text treatment.
const listRowClass =
  "uc-sidebar-item group group/item w-full min-w-0 text-xxs font-medium";
const listSectionTriggerClass =
  "uc-sidebar-group-label group/trigger flex flex-1 items-center gap-1 normal-case tracking-wider";
</script>

<template>
  <!-- Load Failure Placeholder -->
  <div
    v-if="hasError"
    class="px-3 py-3 mx-1 my-1 flex items-start gap-2 border border-dashed border-destructive/40 bg-destructive/5 text-destructive"
  >
    <AlertCircle class="h-3.5 w-3.5 mt-0.5 shrink-0" />
    <div class="flex-1 min-w-0">
      <p class="text-xxs font-semibold leading-tight">
        {{ t("problem.banners.unableToLoad") }}
      </p>
      <button
        type="button"
        class="mt-1 inline-flex items-center gap-1 text-xxs underline underline-offset-2 hover:text-destructive/80"
        @click="emit('retryLoad')"
      >
        <RefreshCw class="h-3 w-3" />
        {{ t("common.actions.retry") }}
      </button>
    </div>
  </div>

  <!-- My Lists Section -->
  <div class="px-1 py-0.5" v-if="data.ownLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="flex items-center justify-between px-2 py-0.5 select-none">
        <CollapsibleTrigger
          :class="listSectionTriggerClass"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
          />
          <User
            class="h-3.5 w-3.5 mr-0.5 text-[var(--primary)]/70 group-hover/trigger:text-[var(--primary)] transition-colors"
          />
          <span>{{ t("sidebar.problemLists.myLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li v-for="item in data.ownLists" :key="item.id" class="group/item">
            <div
              :class="listRowClass"
              :data-active="isListActive(item.id) ? 'true' : 'false'"
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
                    class="uc-sidebar-icon-button h-5 w-5 hover:text-[var(--primary)]"
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
          :class="listSectionTriggerClass"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
          />
          <Bookmark
            class="h-3.5 w-3.5 mr-0.5 text-[var(--status-success-mark)]/70 group-hover/trigger:text-[var(--status-success-mark)] transition-colors"
          />
          <span>{{ t("sidebar.problemLists.savedLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-0.5">
        <ul class="space-y-0.5">
          <li v-for="item in data.savedLists" :key="item.id" class="group/item">
            <div
              :class="listRowClass"
              :data-active="isListActive(item.id) ? 'true' : 'false'"
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
                    class="uc-sidebar-icon-button h-5 w-5 hover:text-[var(--primary)]"
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
          :class="listSectionTriggerClass"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
          />
          <Star
            class="h-3.5 w-3.5 mr-0.5 text-[var(--status-warning-mark)]/70 group-hover/trigger:text-[var(--status-warning-mark)] transition-colors"
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
              :class="listRowClass"
              :data-active="isListActive(item.id) ? 'true' : 'false'"
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
          :class="listSectionTriggerClass"
        >
          <ChevronRight
            class="h-3 w-3 transform transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
          />
          <span class="truncate">{{ category.name }}</span>
        </CollapsibleTrigger>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              class="uc-sidebar-icon-button h-6 w-6"
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
              :class="listRowClass"
              :data-active="isListActive(item.id) ? 'true' : 'false'"
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
                    class="uc-sidebar-icon-button h-5 w-5 hover:text-[var(--primary)]"
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
