<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { SemanticBadge, type SemanticColor } from '@/components/ui/terminal'
import { accountApi, type Subscription } from '@/api/admin/account'
import { formatDateByLocale } from '@/i18n/utils'
import {
  IconCreditCard,
  IconCalendar,
  IconCheck,
  IconX,
  IconClock,
  IconLoader2,
} from '@tabler/icons-vue'

const { t } = useI18n()

const loading = ref(false)
const subscription = ref<Subscription | null>(null)
const isLoaded = ref(false)

const statusColor = computed<SemanticColor>(() => {
  if (!subscription.value) return 'success'
  switch (subscription.value.status) {
    case 'ACTIVE':
      return 'success'
    case 'CANCELLED':
      return 'neutral'
    case 'EXPIRED':
      return 'error'
    case 'PENDING':
      return 'warning'
    default:
      return 'neutral'
  }
})

const statusIcon = computed(() => {
  if (!subscription.value) return undefined
  switch (subscription.value.status) {
    case 'ACTIVE':
      return IconCheck
    case 'CANCELLED':
      return IconX
    case 'EXPIRED':
      return IconX
    case 'PENDING':
      return IconClock
    default:
      return undefined
  }
})

async function loadSubscription() {
  loading.value = true
  try {
    subscription.value = await accountApi.getSubscription()
  } catch (error) {
    console.error(error)
    // If there's no subscription, that's okay - user is on free plan
    subscription.value = null
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  return formatDateByLocale(dateStr)
}

onMounted(async () => {
  await loadSubscription()
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-0 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('billing.title') }}
          </h1>
          <p class="text-xs text-[var(--silver-500)]">{{ t('billing.subtitle') }}</p>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >

    <div v-if="loading" class="flex items-center justify-center py-12">
      <IconLoader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <div v-else class="space-y-6">
      <!-- Current Plan Card -->
      <Card>
        <CardHeader>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <IconCreditCard class="h-6 w-6 text-muted-foreground" />
              <div>
                <CardTitle>{{ t('billing.currentPlan') }}</CardTitle>
                <CardDescription>{{ t('billing.planDetails') }}</CardDescription>
              </div>
            </div>
            <SemanticBadge v-if="subscription" :color="statusColor">
              <component v-if="statusIcon" :is="statusIcon" class="h-3 w-3" />
              {{ t(`billing.status.${subscription.status}`, subscription.status) }}
            </SemanticBadge>
            <SemanticBadge v-else color="success">
              <IconCheck class="h-3 w-3" />
              {{ t('billing.status.ACTIVE') }}
            </SemanticBadge>
          </div>
        </CardHeader>
        <CardContent class="space-y-6">
          <!-- Plan Name -->
          <div>
            <h3 class="text-2xl font-semibold">
              {{ subscription ? t(`billing.plans.${subscription.plan}`, subscription.plan) : t('billing.plans.FREE') }}
            </h3>
            <p class="text-muted-foreground mt-1">
              {{
                subscription
                  ? t('billing.features.premium.description')
                  : t('billing.features.free.description')
              }}
            </p>
          </div>

          <Separator />

          <!-- Subscription Details -->
          <div v-if="subscription" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="space-y-1">
              <p class="text-sm text-muted-foreground">{{ t('billing.statusLabel') }}</p>
              <p class="font-medium">{{ t(`billing.status.${subscription.status}`, subscription.status) }}</p>
            </div>
            <div class="space-y-1">
              <p class="text-sm text-muted-foreground">{{ t('billing.startedAt') }}</p>
              <p class="font-medium flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                {{ formatDate(subscription.started_at) }}
              </p>
            </div>
            <div v-if="subscription.expires_at" class="space-y-1">
              <p class="text-sm text-muted-foreground">{{ t('billing.expiresAt') }}</p>
              <p class="font-medium flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                {{ formatDate(subscription.expires_at) }}
              </p>
            </div>
            <div v-if="subscription.cancelled_at" class="space-y-1">
              <p class="text-sm text-muted-foreground">{{ t('billing.cancelledAt') }}</p>
              <p class="font-medium flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                {{ formatDate(subscription.cancelled_at) }}
              </p>
            </div>
          </div>

          <!-- Free Plan Message -->
          <div v-else class="text-center py-6">
            <p class="text-muted-foreground">{{ t('billing.noSubscription') }}</p>
          </div>

          <!-- Features -->
          <Separator />

          <div>
            <h4 class="font-medium mb-4">
              {{
                subscription
                  ? t('billing.features.premium.title')
                  : t('billing.features.free.title')
              }}
            </h4>
            <div class="space-y-3">
              <div v-if="subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.premiumProblems') }}</span>
              </div>
              <div v-if="subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.prioritySupport') }}</span>
              </div>
              <div v-if="subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.advancedAnalytics') }}</span>
              </div>
              <div v-if="subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.unlimitedContests') }}</span>
              </div>
              <div v-if="!subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.freeProblems') }}</span>
              </div>
              <div v-if="!subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.communityForum') }}</span>
              </div>
              <div v-if="!subscription" class="flex items-start gap-3">
                <IconCheck class="h-5 w-5 text-green-500 mt-0.5" />
                <span>{{ t('billing.features.basicAnalytics') }}</span>
              </div>
              <div v-if="!subscription" class="flex items-start gap-3">
                <IconX class="h-5 w-5 text-muted-foreground mt-0.5" />
                <span class="text-muted-foreground">{{
                  t('billing.features.premiumProblems')
                }}</span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Note about upgrades -->
      <Card class="border-dashed">
        <CardContent class="pt-6">
          <div class="flex items-start gap-3">
            <IconCreditCard class="h-5 w-5 text-muted-foreground mt-0.5" />
            <div class="text-sm text-muted-foreground">
              {{ subscription ? t('billing.manageSubscription') : t('billing.upgradePrompt') }}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
    </div>
  </div>
</template>
