import { apiGet, apiPost } from "@/utils/request";

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
  planType: "monthly" | "yearly";
  successUrl: string;
  cancelUrl: string;
}

export interface PortalRequest {
  returnUrl: string;
}

export interface Invoice {
  id: string;
  number: string;
  status: string;
  amount: number;
  currency: string;
  createdAt: string;
  paidAt: string | null;
  invoicePdf: string | null;
  hostedUrl: string | null;
}

export interface InvoiceListResult {
  invoices: Invoice[];
  hasMore: boolean;
}

export interface UpcomingInvoice {
  amount: number;
  currency: string;
  nextPaymentAt: string | null;
}

export const subscriptionApi = {
  /**
   * Get the current user's premium access status + active subscription.
   *
   * NOTE: We call `/subscriptions/check-premium` (not `/subscriptions/me`).
   * The latter returns `Result<SubscriptionDTO>` whose `data` is serialized
   * as `null` and then dropped by `Result.@JsonInclude(NON_NULL)`, leaving
   * the frontend with `undefined` and breaking `.hasAccess` access.
   * `/check-premium` returns the correct `SubscriptionCheckResultDTO` shape.
   * See `docs/subscription-api-test-questions.md` (P0-2).
   */
  async getMySubscription(): Promise<SubscriptionCheckResult> {
    return apiGet<SubscriptionCheckResult>("/subscriptions/check-premium");
  },

  async getPlans(): Promise<{ plans: SubscriptionPlan[] }> {
    return apiGet<{ plans: SubscriptionPlan[] }>("/subscriptions/plans");
  },

  async createCheckout(data: CheckoutRequest): Promise<CheckoutSessionResult> {
    return apiPost<CheckoutSessionResult>("/subscriptions/checkout", data);
  },

  async createPortal(data: PortalRequest): Promise<BillingPortalResult> {
    return apiPost<BillingPortalResult>("/subscriptions/portal", data);
  },

  /**
   * Cancel the current user's subscription by id.
   *
   * NOTE: The backend exposes `POST /subscriptions/{id}/cancel` (path param
   * required). The previous implementation called `/subscriptions/cancel`
   * with an empty body, which returned 404. See P1-1 in the test report.
   * Callers should pass `subscription.subscription.id` from `getMySubscription()`.
   */
  async cancelSubscription(
    id: string,
  ): Promise<{ message: string; cancelAt: string }> {
    return apiPost<{ message: string; cancelAt: string }>(
      `/subscriptions/${id}/cancel`,
      {},
    );
  },

  async reactivateSubscription(): Promise<{
    message: string;
    currentPeriodEnd: string;
  }> {
    return apiPost<{ message: string; currentPeriodEnd: string }>(
      "/subscriptions/reactivate",
      {},
    );
  },

  async getInvoices(options?: {
    limit?: number;
    startingAfter?: string;
  }): Promise<InvoiceListResult> {
    const params = new URLSearchParams();
    if (options?.limit) params.set("limit", String(options.limit));
    if (options?.startingAfter)
      params.set("startingAfter", options.startingAfter);
    const query = params.toString() ? `?${params.toString()}` : "";
    return apiGet<InvoiceListResult>(`/subscriptions/invoices${query}`);
  },

  async getInvoice(invoiceId: string): Promise<
    Invoice & {
      lines: Array<{
        description: string;
        amount: number;
        currency: string;
        period: { start: string; end: string } | null;
      }>;
    }
  > {
    return apiGet(`/subscriptions/invoices/${invoiceId}`);
  },

  async getUpcomingInvoice(): Promise<UpcomingInvoice | null> {
    return apiGet<UpcomingInvoice | null>("/subscriptions/invoices/upcoming");
  },
};
