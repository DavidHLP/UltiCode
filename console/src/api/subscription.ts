import { apiGet, apiPost } from '@/utils/request';

export interface SubscriptionCheckResult {
  hasAccess: boolean;
  subscription: {
    plan: string;
    status: string;
    expiresAt: string | null;
  } | null;
}

export interface SubscriptionPlan {
  id: string;
  name: string;
  price: number;
  currency: string;
  interval: string;
  features: string[];
}

export interface CheckoutSessionResult {
  sessionId: string;
  url: string;
}

export interface BillingPortalResult {
  url: string;
}

export interface CheckoutRequest {
  planType: 'monthly' | 'yearly';
  successUrl: string;
  cancelUrl: string;
}

export interface PortalRequest {
  returnUrl: string;
}

export const subscriptionApi = {
  async getMySubscription(): Promise<SubscriptionCheckResult> {
    return apiGet<SubscriptionCheckResult>('/subscriptions/me');
  },

  async getPlans(): Promise<{ plans: SubscriptionPlan[] }> {
    return apiGet<{ plans: SubscriptionPlan[] }>('/subscriptions/plans');
  },

  async createCheckout(data: CheckoutRequest): Promise<CheckoutSessionResult> {
    return apiPost<CheckoutSessionResult>('/subscriptions/checkout', data);
  },

  async createPortal(data: PortalRequest): Promise<BillingPortalResult> {
    return apiPost<BillingPortalResult>('/subscriptions/portal', data);
  },

  async cancelSubscription(): Promise<{ message: string; cancelAt: string }> {
    return apiPost<{ message: string; cancelAt: string }>('/subscriptions/cancel', {});
  },

  async reactivateSubscription(): Promise<{ message: string; currentPeriodEnd: string }> {
    return apiPost<{ message: string; currentPeriodEnd: string }>('/subscriptions/reactivate', {});
  },
};
