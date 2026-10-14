<script setup lang="ts">
import { computed } from "vue";
import { Button } from "@ulticode/design-system/ui/button";
import { Separator } from "@ulticode/design-system/ui/separator";
import {
  Layout,
  User,
  Check,
  FileCode,
  History,
  Settings,
  LogOut,
  LogIn,
  Paintbrush,
} from "@lucide/vue";
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@ulticode/design-system/ui/avatar";
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
} from "@ulticode/design-system/ui/dropdown-menu";
import type { ProblemDetail } from "@/types/problem-detail";
import { RouterLink } from "vue-router";
import { openAppearanceSettings } from "@ulticode/theme";
import { ProblemEdgeOperations } from "@/components/edge-operations";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import { useAvatar } from "@/composables/useAvatar";
import ContestProblemDock from "../components/ContestProblemDock.vue";

interface Props {
  currentLayout: "leet" | "classic" | "compact" | "wide";
  problem?: ProblemDetail | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "layout-change": [layout: "leet" | "classic" | "compact" | "wide"];
}>();

const { t, locale } = useI18n();
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

// --- Current user avatar (for the top-right user entry) ---
// Custom App profile avatars win; useAvatar supplies a local deterministic
// SVG when the profile has no photo, so the header never depends on a remote
// avatar service and stays consistent with the personal profile surface.
const { normalizedAvatar: currentUserAvatarUrl } = useAvatar(
  computed(() => authStore.user?.username || authStore.userName),
  computed(() => authStore.user?.avatar),
);
const currentUserInitial = computed(() =>
  (authStore.userName || "?").charAt(0).toUpperCase(),
);

// Top-right user entry: a 40px circular trigger that reads as a real
// identity chip rather than a generic person icon. Idle / hover / open /
// focused states each have a distinct visual treatment so the affordance
// is always legible against the dark header background.
const userEntryTriggerClass = "h-10 w-10 rounded-full p-0";
const userEntryFallbackClass = "bg-primary text-primary-foreground";
</script>

<template>
  <div class="flex min-w-0 items-center justify-end">
    <div class="flex min-w-0 flex-wrap items-center justify-center gap-2">
      <!-- Group 0: Contest context dock (renders only for ?contestId=...) -->
      <ContestProblemDock />

      <!-- Group 1: Problem Operations (Vote & Save) -->
      <ProblemEdgeOperations v-if="problem" :problem="problem" />

      <!-- Divider -->
      <Separator
        v-if="problem"
        orientation="vertical"
        class="h-5 w-px flex-none"
      />

      <!-- Group 2: View Settings (Layout Switching) -->
      <div class="flex items-center">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('problem.explorer.filters')"
              variant="outline"
              size="icon"
            >
              <Layout class="h-4 w-4" aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent class="w-96 max-w-[calc(100vw-2rem)] p-4">
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
                    class="relative flex flex-col items-center gap-2 p-2 cursor-pointer"
                  >
                    <!-- Layout preview container -->
                    <div
                      class="w-full aspect-[4/3] bg-muted rounded-lg flex items-center justify-center relative overflow-hidden border border-border"
                    >
                      <!-- Selected indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute inset-0 border-2 border-primary rounded-lg"
                      />

                      <!-- Layout preview visualization -->
                      <div
                        v-if="option.value === 'leet'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="flex-1 bg-background rounded-lg border border-border"
                        ></div>
                        <div class="flex flex-col gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'classic'"
                        class="w-full h-full p-2 flex flex-col gap-1.5"
                      >
                        <div
                          class="flex-1 bg-background rounded-lg border border-border"
                        ></div>
                        <div class="flex gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'compact'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div class="flex flex-col gap-1.5 w-1/3">
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-background rounded-lg border border-border"
                          ></div>
                        </div>
                        <div
                          class="flex-1 bg-background rounded-lg border border-border"
                        ></div>
                      </div>
                      <div
                        v-else
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="w-1/4 bg-background rounded-lg border border-border"
                        ></div>
                        <div
                          class="flex-1 bg-background rounded-lg border border-border"
                        ></div>
                        <div
                          class="w-1/4 bg-background rounded-lg border border-border"
                        ></div>
                      </div>

                      <!-- Check mark indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute top-1.5 right-1.5 w-5 h-5 bg-primary rounded-lg flex items-center justify-center shadow-sm"
                      >
                        <Check class="w-3 h-3 text-primary-foreground" />
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
      </div>

      <!-- Divider -->
      <Separator orientation="vertical" class="h-5 w-px flex-none" />

      <Button
        variant="ghost"
        size="icon"
        :aria-label="locale === 'zh-CN' ? '外观设置' : 'Appearance'"
        @click="openAppearanceSettings"
        ><Paintbrush aria-hidden="true"
      /></Button>

      <!-- Group 3: User Entry -->
      <div class="flex items-center">
        <!-- Guest: show login button -->
        <RouterLink v-if="!isAuthenticated" to="/login" class="flex-none">
          <Button
            :aria-label="t('auth.login.submit')"
            class="w-8 p-0 dark:hover:bg-border dark:hover:text-foreground flex items-center justify-center cursor-pointer select-none"
          >
            <LogIn class="h-4 w-4" aria-hidden="true" />
          </Button>
        </RouterLink>

        <!-- Authenticated: user dropdown -->
        <DropdownMenu v-else>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('personal.profile.title')"
              :class="userEntryTriggerClass"
            >
              <Avatar class="h-10 w-10">
                <AvatarImage
                  :src="currentUserAvatarUrl"
                  :alt="authStore.userName"
                  class="aspect-square size-full object-cover"
                />
                <AvatarFallback :class="userEntryFallbackClass">
                  {{ currentUserInitial }}
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent class="w-60 p-1.5" align="end" :side-offset="8">
            <DropdownMenuLabel class="px-2 py-1.5">
              {{ t("problem.layout.userAccount") }}
            </DropdownMenuLabel>
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
            <DropdownMenuItem
              class="cursor-pointer"
              @select="authStore.logout()"
            >
              <LogOut class="mr-2 h-4 w-4" aria-hidden="true" />
              <span>{{ t("problem.layout.logout") }}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  </div>
</template>
