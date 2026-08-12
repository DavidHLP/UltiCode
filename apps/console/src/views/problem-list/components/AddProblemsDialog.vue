<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { Problem } from "@/types/problem";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Search, Plus, Check } from "lucide-vue-next";
import { getDifficultyBadgeClass } from "@ulticode/design-system";
import { searchProblems } from "@/api/problem";

const props = defineProps<{
  open: boolean;
  listId: string;
  problemIdsInList: Set<number>;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
  (e: "add", problem: Problem): void;
}>();

const { t } = useI18n();

const searchQuery = ref("");
const searchResults = ref<Problem[]>([]);
const isSearching = ref(false);
const addingProblemIds = ref<Set<number>>(new Set());
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

function handleSearch() {
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(async () => {
    const query = searchQuery.value.trim();
    if (!query) {
      searchResults.value = [];
      return;
    }
    isSearching.value = true;
    try {
      searchResults.value = await searchProblems(query);
    } catch (e) {
      console.error("Failed to search problems", e);
      searchResults.value = [];
    } finally {
      isSearching.value = false;
    }
  }, 300);
}

function handleAddProblem(problem: Problem) {
  if (addingProblemIds.value.has(problem.id)) return;
  addingProblemIds.value.add(problem.id);
  emit("add", problem);
  addingProblemIds.value.delete(problem.id);
}

function handleOpenChange(value: boolean) {
  emit("update:open", value);
  if (value) {
    searchQuery.value = "";
    searchResults.value = [];
  }
}

watch(
  () => props.open,
  (value) => {
    if (!value) {
      searchQuery.value = "";
      searchResults.value = [];
    }
  },
);
</script>

<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-[650px] max-h-[80vh] flex flex-col">
      <DialogHeader>
        <DialogTitle>{{
          t("problem.problemList.detail.addProblems")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("problem.problemList.detail.editDescription") }}
        </DialogDescription>
      </DialogHeader>
      <div class="flex-1 space-y-4 py-4 overflow-hidden">
        <!-- Search Input -->
        <div class="relative">
          <Search
            class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
          />
          <Input
            v-model="searchQuery"
            :placeholder="t('problem.problemList.detail.searchPlaceholder')"
            class="pl-10"
            @input="handleSearch"
          />
        </div>

        <!-- Search Results -->
        <div class="border rounded-none overflow-hidden">
          <div
            v-if="isSearching"
            class="flex items-center justify-center py-12"
          >
            <span class="text-muted-foreground">{{
              t("problem.problemList.detail.searching")
            }}</span>
          </div>
          <div
            v-else-if="searchQuery && searchResults.length === 0"
            class="flex items-center justify-center py-12"
          >
            <span class="text-muted-foreground">{{
              t("problem.problemList.detail.noProblemsFound")
            }}</span>
          </div>
          <div
            v-else-if="!searchQuery"
            class="flex items-center justify-center py-12"
          >
            <span class="text-muted-foreground">{{
              t("problem.problemList.detail.enterSearchTerm")
            }}</span>
          </div>
          <ScrollArea v-else class="h-[320px]">
            <div class="divide-y">
              <div
                v-for="problem in searchResults"
                :key="problem.id"
                class="flex items-center justify-between px-4 py-3 hover:bg-muted/50 transition-colors"
              >
                <div class="flex items-center gap-3 min-w-0 flex-1">
                  <span
                    class="text-sm font-mono text-muted-foreground w-10 shrink-0 text-right"
                  >
                    {{ problem.id }}
                  </span>
                  <span class="text-sm font-medium truncate flex-1">
                    {{ problem.title }}
                  </span>
                  <Badge
                    :class="getDifficultyBadgeClass(problem.difficulty)"
                    class="text-xs shrink-0"
                  >
                    {{
                      t(
                        `problem.difficulty.${problem.difficulty.toLowerCase()}`,
                      )
                    }}
                  </Badge>
                </div>
                <div class="shrink-0 ml-3">
                  <Button
                    v-if="problemIdsInList.has(problem.id)"
                    variant="ghost"
                    size="sm"
                    class="text-foreground-strong gap-1 pointer-events-none"
                  >
                    <Check class="h-4 w-4" />
                    {{ t("problem.problemList.detail.added") }}
                  </Button>
                  <Button
                    v-else
                    variant="outline"
                    size="sm"
                    class="gap-1"
                    :disabled="addingProblemIds.has(problem.id)"
                    @click="handleAddProblem(problem)"
                  >
                    <Plus class="h-4 w-4" />
                    {{
                      addingProblemIds.has(problem.id)
                        ? t("problem.problemList.detail.adding")
                        : t("problem.problemList.detail.add")
                    }}
                  </Button>
                </div>
              </div>
            </div>
          </ScrollArea>
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="handleOpenChange(false)">{{
          t("problem.problemList.detail.done")
        }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
