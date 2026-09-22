<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import { fetchUserProfile, updateMyProfile, type ProfileData } from "@/api/user";
import { toast } from "vue-sonner";
import { Loader2 } from "lucide-vue-next";
import { useAvatar } from "@/composables/useAvatar";
import {
  isAvatarUploadSessionCurrent,
  useAvatarUpload,
} from "@/composables/useAvatarUpload";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Globe, Github, Lock, Mail, MapPin, Twitter } from "lucide-vue-next";

const { t } = useI18n();
const authStore = useAuthStore();
const loading = ref(true);
const saving = ref(false);
const user = ref<ProfileData | null>(null);
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarError = ref("");
const { uploading: avatarUploading, progress: avatarProgress, upload: uploadAvatar } =
  useAvatarUpload();
const { normalizedAvatar } = useAvatar(
  computed(() => user.value?.username),
  computed(() => user.value?.avatar),
);
const saveProfile = async () => {
  if (!user.value) return;
  saving.value = true;
  try {
    // Only send fields the user can actually edit in this form.
    // id / username / email / joined_at / rank / solved_count / submission_count
    // are derived from auth or backend aggregation and must not be PATCHed.
    await updateMyProfile({
      name: user.value.name,
      bio: user.value.bio,
      location: user.value.location,
      website: user.value.website,
      twitter: user.value.twitter,
      github: user.value.github,
    });
    toast.success(t("personal.messages.profileUpdated"));
  } catch (error) {
    console.error("Failed to update profile", error);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    saving.value = false;
  }
};

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || !user.value) return;
  const accountId = user.value.id;

  avatarError.value = "";
  try {
    const avatar = await uploadAvatar(file);
    if (!isAvatarUploadSessionCurrent(accountId, user.value?.id, authStore.fetchCurrentUserId())) return;
    user.value = { ...user.value, avatar };
    authStore.setUserAvatar(avatar);
    toast.success(t("personal.profile.avatarUpdated"));
  } catch (error) {
    avatarError.value =
      error instanceof Error
        ? error.message
        : t("personal.profile.avatarUploadFailed");
    toast.error(avatarError.value);
  }
}

onMounted(async () => {
  try {
    const userId = authStore.fetchCurrentUserId();
    if (!userId) return;
    user.value = await fetchUserProfile(userId);
  } catch (error) {
    console.error("Failed to load user profile", error);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.account.loadingSettings") }}
      </p>
    </div>

    <div v-else-if="!user" class="text-center py-20">
      <p class="text-muted-foreground">
        {{ t("personal.account.loginToManage") }}
      </p>
    </div>

    <template v-else>
      <Card class="border shadow-[var(--shadow-float)] bg-card rounded-none">
        <CardHeader>
          <CardTitle class="text-xl font-semibold tracking-tight">{{
            t("personal.account.sections.publicProfile")
          }}</CardTitle>
          <CardDescription>
            {{ t("personal.account.sections.publicProfileDesc") }}
          </CardDescription>
        </CardHeader>
        <div class="flex flex-wrap items-center gap-4 border-b px-6 py-4">
          <img
            :src="normalizedAvatar"
            :alt="t('personal.profile.avatar')"
            class="h-16 w-16 rounded-full border object-cover"
          />
          <div class="min-w-0 flex-1 space-y-1">
            <p class="text-sm font-medium">{{ t("personal.profile.avatar") }}</p>
            <p class="text-xs text-muted-foreground">
              {{ t("personal.profile.avatarUploadHint") }}
            </p>
            <p
              v-if="avatarUploading"
              role="status"
              aria-live="polite"
              class="text-xs text-muted-foreground"
            >
              {{ t("personal.profile.avatarUploading", { progress: avatarProgress }) }}
            </p>
            <p v-if="avatarError" role="alert" class="text-xs text-destructive">
              {{ avatarError }}
            </p>
          </div>
          <input
            ref="avatarInput"
            type="file"
            accept=".jpg,.jpeg,.png,.gif,.webp"
            class="sr-only"
            :disabled="avatarUploading || saving"
            :aria-label="t('personal.profile.changeAvatar')"
            @change="handleAvatarChange"
          />
          <Button
            type="button"
            variant="outline"
            :disabled="avatarUploading || saving"
            @click="avatarInput?.click()"
          >
            <Loader2 v-if="avatarUploading" class="mr-2 h-4 w-4 animate-spin" />
            {{ t("personal.profile.changeAvatar") }}
          </Button>
        </div>
        <CardContent class="space-y-6">
          <div class="grid gap-6 md:grid-cols-2">
            <div class="space-y-2">
              <Label
                htmlFor="username"
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.username") }}</Label
              >
              <div class="relative">
                <Input
                  id="username"
                  v-model="user.username"
                  disabled
                  class="bg-muted/50 font-medium"
                />
                <Lock
                  class="absolute right-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
                />
              </div>
              <p class="text-xxs text-muted-foreground italic">
                {{ t("personal.account.usernameUnique") }}
              </p>
            </div>
            <div class="space-y-2">
              <Label
                htmlFor="name"
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.displayName") }}</Label
              >
              <Input
                id="name"
                v-model="user.name"
                placeholder="John Doe"
              />
              <p class="text-xxs text-muted-foreground">
                {{ t("personal.profile.displayName") }}
              </p>
            </div>
          </div>

          <div class="space-y-2">
            <Label
              htmlFor="bio"
              class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
              >{{ t("personal.profile.bio") }}</Label
            >
            <Textarea
              id="bio"
              v-model="user.bio"
              :placeholder="t('personal.profile.bioPlaceholder')"
              class="min-h-[120px] resize-none"
            />
            <p class="text-xxs text-muted-foreground">
              {{ t("personal.profile.bioPlaceholder") }}
            </p>
          </div>

          <div class="grid gap-6 md:grid-cols-2">
            <div class="space-y-2">
              <Label
                htmlFor="email"
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.account.email") }}</Label
              >
              <div class="relative">
                <Input
                  id="email"
                  v-model="user.email"
                  disabled
                  class="bg-muted/50"
                />
                <Mail
                  class="absolute right-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
                />
              </div>
            </div>
            <div class="space-y-2">
              <Label
                htmlFor="location"
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.location") }}</Label
              >
              <div class="relative">
                <Input
                  id="location"
                  v-model="user.location"
                  :placeholder="t('personal.profile.locationPlaceholder')"
                />
                <MapPin
                  class="absolute right-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
                />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card class="border shadow-[var(--shadow-float)] bg-card rounded-none">
        <CardHeader>
          <CardTitle class="text-xl font-semibold tracking-tight">{{
            t("personal.account.sections.webPresence")
          }}</CardTitle>
          <CardDescription>
            {{ t("personal.account.sections.webPresenceDesc") }}
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-9 w-9 items-center justify-center rounded-none bg-muted"
              >
                <Globe class="h-4 w-4 text-muted-foreground" />
              </div>
              <div class="flex-1">
                <Input
                  v-model="user.website"
                  :placeholder="t('personal.profile.websitePlaceholder')"
                  class="h-9"
                />
              </div>
            </div>
            <div class="flex items-center gap-3">
              <div
                class="flex h-9 w-9 items-center justify-center rounded-none bg-muted"
              >
                <Twitter class="h-4 w-4 text-muted-foreground" />
              </div>
              <div class="flex-1">
                <Input
                  v-model="user.twitter"
                  placeholder="Twitter URL"
                  class="h-9"
                />
              </div>
            </div>
            <div class="flex items-center gap-3">
              <div
                class="flex h-9 w-9 items-center justify-center rounded-none bg-muted"
              >
                <Github class="h-4 w-4 text-muted-foreground" />
              </div>
              <div class="flex-1">
                <Input
                  v-model="user.github"
                  placeholder="GitHub Profile URL"
                  class="h-9"
                />
              </div>
            </div>
          </div>
        </CardContent>
        <CardFooter class="bg-muted/5 border-t justify-end py-4">
          <Button
            @click="saveProfile"
            :disabled="saving || avatarUploading"
            class="rounded-none px-8"
          >
            <Loader2 v-if="saving" class="mr-2 h-4 w-4 animate-spin" />
            {{ t("personal.account.saveChanges") }}
          </Button>
        </CardFooter>
      </Card>
    </template>
  </div>
</template>