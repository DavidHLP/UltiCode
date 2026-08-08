<template>
  <div class="subscription-view">
    <div class="subscription-header">
      <h1 class="text-2xl font-bold">{{ t("personal.subscription.title") }}</h1>
      <p class="text-muted-foreground mt-2">
        {{ t("personal.subscription.subtitle") }}
      </p>
    </div>

    <div v-if="loading" class="flex justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <template v-else>
      <!-- Current subscription status -->
      <Card v-if="currentSubscription" class="mb-8">
        <CardHeader>
          <CardTitle>{{ t("personal.subscription.currentPlan") }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center justify-between">
            <div>
              <div class="flex items-center gap-2">
                <Badge
                  :variant="
                    currentSubscription.hasAccess ? 'default' : 'secondary'
                  "
                >
                  {{ currentSubscription.subscription?.plan || "FREE" }}
                </Badge>
                <span class="text-sm text-muted-foreground">
                  {{ currentSubscription.subscription?.status }}
                </span>
              </div>
              <p
                v-if="currentSubscription.subscription?.expiresAt"
                class="text-sm text-muted-foreground mt-1"
              >
                {{ t("personal.subscription.expiresAt") }}:
                {{ formatDate(currentSubscription.subscription.expiresAt) }}
              </p>
            </div>
            <div class="flex gap-2">
              <Button
                v-if="currentSubscription.hasAccess"
                variant="outline"
                @click="openBillingPortal"
                :disabled="portalLoading"
              >
                <Settings v-if="!portalLoading" class="mr-2 h-4 w-4" />
                <Loader2 v-else class="mr-2 h-4 w-4 animate-spin" />
                {{ t("personal.subscription.manageBilling") }}
              </Button>
              <Button v-else @click="showPlans = true">
                {{ t("personal.subscription.upgrade") }}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Upcoming Invoice -->
      <Card v-if="upcomingInvoice" class="mb-8">
        <CardHeader>
          <CardTitle>{{ t("personal.subscription.nextBilling") }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center justify-between">
            <div>
              <p class="text-2xl font-bold">
                {{
                  formatCurrency(
                    upcomingInvoice.amount,
                    upcomingInvoice.currency,
                  )
                }}
              </p>
              <p
                v-if="upcomingInvoice.nextPaymentAt"
                class="text-sm text-muted-foreground"
              >
                {{ t("personal.subscription.nextPaymentAt") }}:
                {{ formatDate(upcomingInvoice.nextPaymentAt) }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Invoice History -->
      <Card v-if="invoices.length > 0" class="mb-8">
        <CardHeader>
          <CardTitle>{{ t("personal.subscription.invoiceHistory") }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="space-y-4">
            <div
              v-for="invoice in invoices"
              :key="invoice.id"
              class="flex items-center justify-between py-2 border-b last:border-0"
            >
              <div>
                <p class="font-medium">
                  {{ invoice.number || `Invoice #${invoice.id.slice(-8)}` }}
                </p>
                <p class="text-sm text-muted-foreground">
                  {{ formatDate(invoice.createdAt) }}
                </p>
              </div>
              <div class="flex items-center gap-4">
                <div class="text-right">
                  <p class="font-medium">
                    {{ formatCurrency(invoice.amount, invoice.currency) }}
                  </p>
                  <Badge
                    :variant="
                      invoice.status === 'paid' ? 'default' : 'secondary'
                    "
                    class="text-xs"
                  >
                    {{ invoice.status }}
                  </Badge>
                </div>
                <Button
                  v-if="invoice.invoicePdf"
                  variant="ghost"
                  size="sm"
                  @click="downloadInvoice(invoice.invoicePdf!)"
                >
                  <Download class="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
          <Button
            v-if="hasMoreInvoices"
            variant="outline"
            class="w-full mt-4"
            @click="loadMoreInvoices"
            :disabled="invoicesLoading"
          >
            <Loader2 v-if="invoicesLoading" class="mr-2 h-4 w-4 animate-spin" />
            {{ t("personal.subscription.loadMore") }}
          </Button>
        </CardContent>
      </Card>

      <!-- Pricing plans -->
      <div v-if="showPlans || !currentSubscription?.hasAccess">
        <h2 class="text-xl font-semibold mb-4">
          {{ t("personal.subscription.choosePlan") }}
        </h2>
        <div class="grid gap-6 md:grid-cols-2">
          <Card
            v-for="plan in plans"
            :key="plan.id"
            :class="{ 'border-primary': plan.id === 'PREMIUM_YEARLY' }"
            class="relative overflow-hidden"
          >
            <div
              v-if="plan.id === 'PREMIUM_YEARLY'"
              class="absolute right-0 top-0 bg-primary px-3 py-1 text-xs text-primary-foreground"
            >
              {{ t("personal.subscription.bestValue") }}
            </div>
            <CardHeader>
              <CardTitle>{{ plan.name }}</CardTitle>
              <div class="mt-2">
                <span class="text-3xl font-bold">${{ plan.price }}</span>
                <span class="text-muted-foreground">/{{ plan.interval }}</span>
              </div>
            </CardHeader>
            <CardContent>
              <ul class="space-y-2">
                <li
                  v-for="feature in plan.features"
                  :key="feature"
                  class="flex items-start gap-2"
                >
                  <Check
                    class="h-4 w-4 text-[var(--terminal-green)] mt-0.5 flex-shrink-0"
                  />
                  <span class="text-sm">{{ feature }}</span>
                </li>
              </ul>
              <Button
                class="w-full mt-6"
                :variant="plan.id === 'PREMIUM_YEARLY' ? 'default' : 'outline'"
                @click="
                  subscribe(plan.id === 'PREMIUM_YEARLY' ? 'yearly' : 'monthly')
                "
                :disabled="checkoutLoading === plan.id"
              >
                <Loader2
                  v-if="checkoutLoading === plan.id"
                  class="mr-2 h-4 w-4 animate-spin"
                />
                {{ t("personal.subscription.subscribe") }}
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Check, Download, Loader2, Settings } from "lucide-vue-next";
import { formatDate as sharedFormatDate } from "@/utils/datetime";
import {
  subscriptionApi,
  type Invoice,
  type UpcomingInvoice,
  type SubscriptionCheckResult,
  type SubscriptionPlan,
} from "@/api/subscription";

const { t } = useI18n();

const loading = ref(true);
const showPlans = ref(false);
const currentSubscription = ref<SubscriptionCheckResult | null>(null);
const plans = ref<SubscriptionPlan[]>([]);
const checkoutLoading = ref<string | null>(null);
const portalLoading = ref(false);
const invoices = ref<Invoice[]>([]);
const hasMoreInvoices = ref(false);
const invoicesLoading = ref(false);
const upcomingInvoice = ref<UpcomingInvoice | null>(null);

onMounted(async () => {
  await Promise.all([
    loadSubscription(),
    loadPlans(),
    loadInvoices(),
    loadUpcomingInvoice(),
  ]);
  loading.value = false;
});

async function loadSubscription() {
  try {
    currentSubscription.value = await subscriptionApi.getMySubscription();
  } catch (error) {
    console.error("Failed to load subscription:", error);
    // Surface the failure so the card is not silently empty
    toast.error(t("personal.subscription.error"), {
      description:
        error instanceof Error
          ? error.message
          : t("personal.subscription.unknownError"),
    });
  }
}

async function loadPlans() {
  try {
    const response = await subscriptionApi.getPlans();
    plans.value = response.plans;
  } catch (error) {
    // The plans endpoint is not yet implemented in the backend (P0-1).
    // Treat 404 as "not yet implemented" and hide silently; any other
    // error (auth, network, 5xx) is a real failure the user should see.
    const status = (error as { response?: { status?: number } })?.response
      ?.status;
    if (status !== 404) {
      console.error("Failed to load plans:", error);
      toast.error(t("personal.subscription.error"), {
        description:
          error instanceof Error
            ? error.message
            : t("personal.subscription.unknownError"),
      });
    }
  }
}

async function loadInvoices() {
  try {
    const result = await subscriptionApi.getInvoices({ limit: 5 });
    invoices.value = result.invoices;
    hasMoreInvoices.value = result.hasMore;
  } catch (error) {
    console.error("Failed to load invoices:", error);
  }
}

async function loadUpcomingInvoice() {
  try {
    upcomingInvoice.value = await subscriptionApi.getUpcomingInvoice();
  } catch (error) {
    console.error("Failed to load upcoming invoice:", error);
  }
}

async function loadMoreInvoices() {
  if (invoices.value.length === 0) return;

  invoicesLoading.value = true;
  try {
    const lastInvoice = invoices.value[invoices.value.length - 1];
    if (!lastInvoice) return;

    const result = await subscriptionApi.getInvoices({
      limit: 5,
      startingAfter: lastInvoice.id,
    });
    invoices.value.push(...result.invoices);
    hasMoreInvoices.value = result.hasMore;
  } catch (error) {
    console.error("Failed to load more invoices:", error);
  } finally {
    invoicesLoading.value = false;
  }
}

function downloadInvoice(url: string) {
  window.open(url, "_blank");
}

function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: currency || "USD",
  }).format(amount / 100);
}

async function subscribe(planType: "monthly" | "yearly") {
  const planId = planType === "yearly" ? "PREMIUM_YEARLY" : "PREMIUM_MONTHLY";
  checkoutLoading.value = planId;

  try {
    const baseUrl = window.location.origin;
    const result = await subscriptionApi.createCheckout({
      planType,
      successUrl: `${baseUrl}/personal/subscription?success=true`,
      cancelUrl: `${baseUrl}/personal/subscription?canceled=true`,
    });

    if (result.url) {
      window.location.href = result.url;
    }
  } catch (error) {
    toast.error(t("personal.subscription.error"), {
      description:
        error instanceof Error
          ? error.message
          : t("personal.subscription.unknownError"),
    });
  } finally {
    checkoutLoading.value = null;
  }
}

async function openBillingPortal() {
  portalLoading.value = true;

  try {
    const result = await subscriptionApi.createPortal({
      returnUrl: window.location.href,
    });

    if (result.url) {
      window.location.href = result.url;
    }
  } catch (error) {
    toast.error(t("personal.subscription.error"), {
      description:
        error instanceof Error
          ? error.message
          : t("personal.subscription.unknownError"),
    });
  } finally {
    portalLoading.value = false;
  }
}

function formatDate(dateString: string): string {
  return sharedFormatDate(dateString, undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}
</script>

<style scoped>
.subscription-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: 2rem;
}

.subscription-header {
  margin-bottom: 2rem;
}
</style>
