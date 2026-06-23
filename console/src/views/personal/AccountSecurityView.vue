<script setup lang="ts">
import { useI18n } from "vue-i18n";
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

const { t } = useI18n();
</script>

<template>
  <div class="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
    <Card class="border shadow-[var(--shadow-float)] bg-card rounded-none">
      <CardHeader>
        <CardTitle class="text-lg">
          {{ t("personal.account.changePassword") }}
        </CardTitle>
        <CardDescription>
          {{ t("personal.account.changePassword") }}
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <Label htmlFor="current" class="text-xs font-bold">
            {{ t("personal.account.currentPassword") }}
          </Label>
          <Input
            id="current"
            type="password"
            autocomplete="current-password"
          />
        </div>
        <div class="space-y-2">
          <Label htmlFor="new" class="text-xs font-bold">
            {{ t("personal.account.newPassword") }}
          </Label>
          <Input id="new" type="password" autocomplete="new-password" />
        </div>
        <div class="space-y-2">
          <Label htmlFor="confirm" class="text-xs font-bold">
            {{ t("personal.account.confirmNewPassword") }}
          </Label>
          <Input
            id="confirm"
            type="password"
            autocomplete="new-password"
          />
        </div>
      </CardContent>
      <CardFooter class="bg-muted/5 border-t justify-end py-4">
        <Button class="rounded-none px-8">
          {{ t("personal.account.updatePassword") }}
        </Button>
      </CardFooter>
    </Card>

    <Card class="border-destructive/20 bg-destructive/5 rounded-none">
      <CardHeader>
        <CardTitle class="text-lg text-destructive">
          {{ t("personal.account.dangerZone") }}
        </CardTitle>
        <CardDescription>
          {{ t("personal.account.dangerZoneDesc") }}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p class="text-sm text-muted-foreground mb-4">
          {{ t("personal.account.deleteWarning") }}
        </p>
        <Button variant="destructive" class="rounded-none">
          {{ t("personal.account.deleteAccount") }}
        </Button>
      </CardContent>
    </Card>
  </div>
</template>