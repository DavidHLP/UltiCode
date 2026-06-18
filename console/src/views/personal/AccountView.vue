<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  CardFooter,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import { toast } from "vue-sonner";
import {
  User,
  Lock,
  Bell,
  Globe,
  Mail,
  MapPin,
  Twitter,
  Github,
  Loader2,
  ShieldCheck,
} from "lucide-vue-next";
import {
  fetchUserProfile,
  updateMyProfile,
  type UserProfile,
} from "@/api/user";
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from "@/api/notification";
import type { NotificationPreferences } from "@/types/notification";
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const user = ref<UserProfile | null>(null);
const notificationPreferences = ref<NotificationPreferences | null>(null);
const preferencesSaving = ref(false);

async function handlePreferenceChange(
  key: keyof NotificationPreferences,
  value: boolean,
) {
  if (!notificationPreferences.value || preferencesSaving.value) return;
  const previous = notificationPreferences.value[key];
  notificationPreferences.value = {
    ...notificationPreferences.value,
    [key]: value,
  };
  preferencesSaving.value = true;
  try {
    notificationPreferences.value = await updateNotificationPreferences({
      [key]: value,
    });
    toast.success(t("personal.messages.notificationsUpdated"));
  } catch (error) {
    console.error("Failed to update notification preferences", error);
    notificationPreferences.value = {
      ...notificationPreferences.value,
      [key]: previous,
    };
    toast.error(t("common.status.error"));
  } finally {
    preferencesSaving.value = false;
  }
}

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

onMounted(async () => {
  try {
    const userId = useAuthStore().fetchCurrentUserId();
    if (!userId) return;
    user.value = await fetchUserProfile(userId);
    try {
      notificationPreferences.value = await fetchNotificationPreferences();
    } catch (error) {
      console.error("Failed to load notification preferences", error);
    }
  } catch (error) {
    console.error("Failed to load user profile", error);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.account.title')"
      :description="t('personal.account.subtitle')"
    />

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

    <div v-else class="flex flex-col lg:flex-row gap-8">
      <!-- Tabs Navigation -->
      <Tabs
        defaultValue="general"
        class="w-full flex flex-col lg:flex-row gap-8"
      >
        <aside class="lg:w-1/4">
          <TabsList
            class="flex flex-row lg:flex-col h-auto bg-transparent gap-2 p-0 justify-start overflow-x-auto lg:overflow-x-visible"
          >
            <TabsTrigger
              value="general"
              class="flex items-center gap-3 px-4 py-3 justify-start w-full text-muted-foreground data-[state=active]:text-foreground data-[state=active]:bg-secondary/50 data-[state=active]:shadow-none hover:bg-muted/50 rounded-none transition-all"
            >
              <User class="h-4 w-4" />
              <span class="font-medium">{{
                t("personal.account.tabs.general")
              }}</span>
            </TabsTrigger>
            <TabsTrigger
              value="password"
              class="flex items-center gap-3 px-4 py-3 justify-start w-full text-muted-foreground data-[state=active]:text-foreground data-[state=active]:bg-secondary/50 data-[state=active]:shadow-none hover:bg-muted/50 rounded-none transition-all"
            >
              <Lock class="h-4 w-4" />
              <span class="font-medium">{{
                t("personal.account.tabs.security")
              }}</span>
            </TabsTrigger>
            <TabsTrigger
              value="notifications"
              class="flex items-center gap-3 px-4 py-3 justify-start w-full text-muted-foreground data-[state=active]:text-foreground data-[state=active]:bg-secondary/50 data-[state=active]:shadow-none hover:bg-muted/50 rounded-none transition-all"
            >
              <Bell class="h-4 w-4" />
              <span class="font-medium">{{
                t("personal.account.tabs.notifications")
              }}</span>
            </TabsTrigger>
          </TabsList>
        </aside>

        <!-- General Settings -->
        <div class="flex-1">
          <TabsContent
            value="general"
            class="mt-0 space-y-6 animate-in fade-in slide-in-from-right-4 duration-300"
          >
            <!-- Profile Section -->
            <Card
              class="border shadow-[var(--shadow-float)] bg-card rounded-none"
            >
              <CardHeader>
                <CardTitle class="text-xl font-semibold tracking-tight">{{
                  t("personal.account.sections.publicProfile")
                }}</CardTitle>
                <CardDescription>
                  {{ t("personal.account.sections.publicProfileDesc") }}
                </CardDescription>
              </CardHeader>
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

            <!-- Social/Links Section -->
            <Card
              class="border shadow-[var(--shadow-float)] bg-card rounded-none"
            >
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
                  :disabled="saving"
                  class="rounded-full px-8"
                >
                  <Loader2 v-if="saving" class="mr-2 h-4 w-4 animate-spin" />
                  {{ t("personal.account.saveChanges") }}
                </Button>
              </CardFooter>
            </Card>
          </TabsContent>

          <!-- Password Settings -->
          <TabsContent
            value="password"
            class="mt-0 space-y-6 animate-in fade-in slide-in-from-right-4 duration-300"
          >
            <Card
              class="border shadow-[var(--shadow-float)] bg-card rounded-none"
            >
              <CardHeader>
                <CardTitle class="text-lg">{{
                  t("personal.account.changePassword")
                }}</CardTitle>
                <CardDescription>
                  {{ t("personal.account.changePassword") }}
                </CardDescription>
              </CardHeader>
              <CardContent class="space-y-4">
                <div class="space-y-2">
                  <Label htmlFor="current" class="text-xs font-bold">{{
                    t("personal.account.currentPassword")
                  }}</Label>
                  <Input
                    id="current"
                    type="password"
                    autocomplete="current-password"
                  />
                </div>
                <div class="space-y-2">
                  <Label htmlFor="new" class="text-xs font-bold">{{
                    t("personal.account.newPassword")
                  }}</Label>
                  <Input id="new" type="password" autocomplete="new-password" />
                </div>
                <div class="space-y-2">
                  <Label htmlFor="confirm" class="text-xs font-bold">{{
                    t("personal.account.confirmNewPassword")
                  }}</Label>
                  <Input
                    id="confirm"
                    type="password"
                    autocomplete="new-password"
                  />
                </div>
              </CardContent>
              <CardFooter class="bg-muted/5 border-t justify-end py-4">
                <Button class="rounded-full px-8">{{
                  t("personal.account.updatePassword")
                }}</Button>
              </CardFooter>
            </Card>

            <Card class="border-destructive/20 bg-destructive/5 rounded-none">
              <CardHeader>
                <CardTitle class="text-lg text-destructive">{{
                  t("personal.account.dangerZone")
                }}</CardTitle>
                <CardDescription>
                  {{ t("personal.account.dangerZoneDesc") }}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p class="text-sm text-muted-foreground mb-4">
                  {{ t("personal.account.deleteWarning") }}
                </p>
                <Button variant="destructive" class="rounded-full">{{
                  t("personal.account.deleteAccount")
                }}</Button>
              </CardContent>
            </Card>
          </TabsContent>

          <!-- Notification Settings -->
          <TabsContent
            value="notifications"
            class="mt-0 space-y-6 animate-in fade-in slide-in-from-right-4 duration-300"
          >
            <Card
              class="border shadow-[var(--shadow-float)] bg-card rounded-none"
            >
              <CardHeader>
                <CardTitle class="text-lg">{{
                  t("personal.account.sections.notifications")
                }}</CardTitle>
                <CardDescription>
                  {{ t("personal.account.sections.notificationsDesc") }}
                </CardDescription>
              </CardHeader>
              <CardContent class="space-y-4">
                <div class="space-y-4">
                  <div
                    class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 transition-all"
                  >
                    <div class="flex gap-4 items-center">
                      <div
                        class="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center"
                      >
                        <Mail class="h-5 w-5 text-primary" />
                      </div>
                      <div class="space-y-0.5">
                        <Label class="text-base font-bold">{{
                          t("personal.account.notificationTypes.communication")
                        }}</Label>
                        <p class="text-sm text-muted-foreground">
                          {{
                            t(
                              "personal.account.notificationTypes.communicationDesc",
                            )
                          }}
                        </p>
                      </div>
                    </div>
                    <Switch
                      :checked="notificationPreferences?.communication ?? true"
                      :disabled="preferencesSaving || !notificationPreferences"
                      @update:checked="
                        (value: boolean) =>
                          handlePreferenceChange('communication', value)
                      "
                    />
                  </div>

                  <div
                    class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 transition-all"
                  >
                    <div class="flex gap-4 items-center">
                      <div
                        class="h-10 w-10 rounded-full bg-[var(--terminal-amber)]/10 flex items-center justify-center"
                      >
                        <Bell class="h-5 w-5 text-[var(--terminal-amber)]" />
                      </div>
                      <div class="space-y-0.5">
                        <Label class="text-base font-bold">{{
                          t("personal.account.notificationTypes.marketing")
                        }}</Label>
                        <p class="text-sm text-muted-foreground">
                          {{
                            t(
                              "personal.account.notificationTypes.marketingDesc",
                            )
                          }}
                        </p>
                      </div>
                    </div>
                    <Switch
                      :checked="notificationPreferences?.marketing ?? false"
                      :disabled="preferencesSaving || !notificationPreferences"
                      @update:checked="
                        (value: boolean) =>
                          handlePreferenceChange('marketing', value)
                      "
                    />
                  </div>

                  <div
                    class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 opacity-70"
                  >
                    <div class="flex gap-4 items-center">
                      <div
                        class="h-10 w-10 rounded-full bg-[var(--terminal-green)]/10 flex items-center justify-center"
                      >
                        <ShieldCheck
                          class="h-5 w-5 text-[var(--terminal-green)]"
                        />
                      </div>
                      <div class="space-y-0.5">
                        <Label class="text-base font-bold">{{
                          t("personal.account.notificationTypes.security")
                        }}</Label>
                        <p class="text-sm text-muted-foreground">
                          {{
                            t("personal.account.notificationTypes.securityDesc")
                          }}
                        </p>
                      </div>
                    </div>
                    <Switch
                      :checked="notificationPreferences?.security ?? true"
                      disabled
                    />
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </div>
      </Tabs>
    </div>
  </PersonalPageShell>
</template>
