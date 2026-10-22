<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { Check, Plus, RefreshCw } from "@lucide/vue";
import type { SignedInAccount } from "@ulticode/auth-core";
import { useSidebar } from "@ulticode/design-system/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@ulticode/design-system/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@ulticode/design-system/ui/dialog";
import LoginForm from "./LoginForm.vue";

const props = defineProps<{
  client: {
    list(): Promise<SignedInAccount[]>;
    add(credentials: { username: string; password: string }): Promise<void>;
    switchTo(accountId: string): Promise<void>;
  };
  adminOnly?: boolean;
}>();
const { t } = useI18n();
const { isMobile } = useSidebar();
const menuOpen = ref(false);
const addOpen = ref(false);
const accounts = ref<SignedInAccount[]>([]);
const loading = ref(false);
const busy = ref(false);
const failed = ref(false);
const visibleAccounts = computed(() =>
  accounts.value.filter(
    (account) =>
      !props.adminOnly ||
      account.role === "ADMIN" ||
      account.role === "SUPER_ADMIN",
  ),
);

async function load(open: boolean) {
  if (!open) return;
  loading.value = true;
  failed.value = false;
  try {
    accounts.value = await props.client.list();
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
async function select(account: SignedInAccount) {
  if (busy.value || account.current) return;
  busy.value = true;
  failed.value = false;
  try {
    await props.client.switchTo(account.id);
    window.location.reload();
  } catch {
    failed.value = true;
  } finally {
    busy.value = false;
  }
}
async function add(credentials: { username: string; password: string }) {
  await props.client.add(credentials);
  window.location.reload();
}
function showAdd() {
  menuOpen.value = false;
  addOpen.value = true;
}
</script>

<template>
  <DropdownMenu v-model:open="menuOpen" @update:open="load">
    <DropdownMenuTrigger as-child><slot name="trigger" /></DropdownMenuTrigger>
    <DropdownMenuContent
      class="w-(--reka-dropdown-menu-trigger-width) min-w-56 rounded-lg"
      :side="isMobile ? 'bottom' : 'right'"
      align="end"
      :side-offset="4"
    >
      <slot name="summary" />
      <DropdownMenuSeparator v-if="$slots.summary" />
      <template v-if="accounts.length || loading || failed">
        <DropdownMenuLabel>{{ t("auth.accounts.title") }}</DropdownMenuLabel>
        <p
          v-if="loading"
          class="px-2 py-1.5 text-sm text-muted-foreground"
          role="status"
        >
          {{ t("auth.accounts.loading") }}
        </p>
        <DropdownMenuGroup v-else>
          <DropdownMenuItem
            v-for="account in visibleAccounts"
            :key="account.id"
            :disabled="busy || account.current"
            @select.prevent="select(account)"
          >
            <div class="grid min-w-0 flex-1">
              <span class="truncate">{{ account.name }}</span
              ><span class="truncate text-xs text-muted-foreground">{{
                account.email
              }}</span>
            </div>
            <Check
              v-if="account.current"
              class="size-4"
              :aria-label="t('auth.accounts.current')"
            />
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <div
          v-if="failed"
          class="px-2 py-1.5 text-sm text-destructive"
          role="alert"
        >
          {{ t("auth.accounts.failed") }}
          <button
            type="button"
            class="inline-flex items-center gap-1 underline"
            @click="load(true)"
          >
            <RefreshCw class="size-3" />{{ t("auth.accounts.retry") }}
          </button>
        </div>
        <DropdownMenuItem
          :disabled="busy || loading || accounts.length >= 5"
          @select.prevent="showAdd"
          ><Plus />{{ t("auth.accounts.add") }}</DropdownMenuItem
        >
        <p
          v-if="accounts.length >= 5"
          class="px-2 text-xs text-muted-foreground"
        >
          {{ t("auth.accounts.limit") }}
        </p>
        <DropdownMenuSeparator />
      </template>
      <slot />
    </DropdownMenuContent>
  </DropdownMenu>
  <Dialog v-model:open="addOpen">
    <DialogContent>
      <DialogHeader
        ><DialogTitle>{{ t("auth.accounts.add") }}</DialogTitle
        ><DialogDescription>{{
          t("auth.accounts.description")
        }}</DialogDescription></DialogHeader
      >
      <LoginForm
        :on-submit="add"
        :redirect-on-success="false"
        hide-heading
        hide-forgot
        hide-sign-up
        hide-o-auth
      />
    </DialogContent>
  </Dialog>
</template>
