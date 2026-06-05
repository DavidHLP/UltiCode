<script setup lang="ts">
import { computed } from "vue";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Layout,
  User,
  Check,
  FileCode,
  History,
  Settings,
  LogOut,
  LogIn,
} from "lucide-vue-next";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import type { ProblemDetail } from "@/types/problem-detail";
import { RouterLink } from "vue-router";
import { ProblemEdgeOperations } from "@/components/edge-operations";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";

interface Props {
  currentLayout: "leet" | "classic" | "compact" | "wide";
  problem?: ProblemDetail | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "layout-change": [layout: "leet" | "classic" | "compact" | "wide"];
}>();

const { t } = useI18n();
const authStore = useAuthStore();
const isAuthenticated = computed(() => authStore.isAuthenticated);

// Layout options
const layoutOptions = computed(() => [
  {
    id: "leet",
    label: t("problem.layout.leet"),
    value: "leet",
  },
  {
    id: "classic",
    label: t("problem.layout.classic"),
    value: "classic",
  },
  {
    id: "compact",
    label: t("problem.layout.compact"),
    value: "compact",
  },
  {
    id: "wide",
    label: t("problem.layout.wide"),
    value: "wide",
  },
]);

const selectedLayout = computed({
  get: () => props.currentLayout,
  set: (value: string) => {
    emit("layout-change", value as "leet" | "classic" | "compact" | "wide");
  },
});
</script>

<template>
  <div class="flex min-w-60 flex-1 items-center justify-end overflow-hidden">
    <div
      class="flex items-center overflow-hidden rounded-none focus:outline-none"
    >
      <ProblemEdgeOperations :problem="problem" />

      <div class="relative group/nav-back flex items-center">
        <!-- Layout button with Dropdown Menu -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('problem.explorer.filters')"
              class="header-btn w-8 p-0"
            >
              <Layout class="h-4 w-4" aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent class="w-96 p-4">
            <div class="space-y-3">
              <div class="text-sm font-semibold text-foreground">
                {{ t("problem.explorer.filters") }}
              </div>
              <DropdownMenuRadioGroup
                :model-value="selectedLayout"
                @update:model-value="
                  (value) =>
                    value &&
                    emit(
                      'layout-change',
                      value as 'leet' | 'classic' | 'compact' | 'wide',
                    )
                "
              >
                <div class="grid grid-cols-2 gap-4">
                  <DropdownMenuRadioItem
                    v-for="option in layoutOptions"
                    :key="option.id"
                    :value="option.value"
                    class="relative flex flex-col items-center gap-2 p-2 rounded-none border-2 transition-all duration-200 cursor-pointer data-[state=checked]:border-[var(--accent-electric)] data-[state=unchecked]:border-border hover:border-muted-foreground hover:bg-muted"
                  >
                    <!-- Layout preview container -->
                    <div
                      class="w-full aspect-[4/3] bg-muted rounded-none flex items-center justify-center relative overflow-hidden border border-border"
                    >
                      <!-- Selected indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute inset-0 border-2 border-[var(--accent-electric)] rounded-none"
                      />

                      <!-- Layout preview visualization -->
                      <div
                        v-if="option.value === 'leet'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div class="flex flex-col gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'classic'"
                        class="w-full h-full p-2 flex flex-col gap-1.5"
                      >
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div class="flex gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'compact'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div class="flex flex-col gap-1.5 w-1/3">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                      </div>
                      <div
                        v-else
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="w-1/4 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div
                          class="w-1/4 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                      </div>

                      <!-- Check mark indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute top-1.5 right-1.5 w-5 h-5 bg-[var(--accent-electric)] rounded-none flex items-center justify-center shadow-[var(--shadow-float)]"
                      >
                        <Check class="w-3 h-3 text-white" />
                      </div>
                    </div>

                    <!-- Label -->
                    <span class="text-xs font-medium text-foreground mt-1">{{
                      option.label
                    }}</span>
                  </DropdownMenuRadioItem>
                </div>
              </DropdownMenuRadioGroup>
            </div>
          </DropdownMenuContent>
        </DropdownMenu>

        <Separator
          orientation="vertical"
          class="h-7 w-px flex-none bg-[var(--border)]"
        />

        <!-- Guest: show login button -->
        <RouterLink v-if="!isAuthenticated" to="/login" class="flex-none">
          <Button
            :aria-label="t('auth.login.submit')"
            class="header-btn w-8 p-0"
          >
            <LogIn class="h-4 w-4" aria-hidden="true" />
          </Button>
        </RouterLink>

        <!-- Authenticated: user dropdown -->
        <DropdownMenu v-else>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('personal.profile.title')"
              class="header-btn w-8 p-0"
            >
              <User class="h-4 w-4" aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent class="w-56" align="end">
            <DropdownMenuLabel>{{
              t("problem.layout.userAccount")
            }}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <RouterLink to="/personal">
                <DropdownMenuItem class="cursor-pointer">
                  <User class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.profile.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/personal/solutions">
                <DropdownMenuItem class="cursor-pointer">
                  <FileCode class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.solutions.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/personal/submissions">
                <DropdownMenuItem class="cursor-pointer">
                  <History class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.submissions.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <RouterLink to="/personal/account">
              <DropdownMenuItem class="cursor-pointer">
                <Settings class="mr-2 h-4 w-4" aria-hidden="true" />
                <span>{{ t("personal.account.title") }}</span>
              </DropdownMenuItem>
            </RouterLink>
            <DropdownMenuSeparator />
            <DropdownMenuItem class="text-[var(--terminal-red)] cursor-pointer">
              <LogOut class="mr-2 h-4 w-4" aria-hidden="true" />
              <span>{{ t("problem.layout.logout") }}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  </div>
</template>
