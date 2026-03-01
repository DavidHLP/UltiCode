import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Stripe from 'stripe';
import { SubscriptionService, SubscriptionPlan } from '../subscription.service';
import { PrismaService } from '../../prisma.service';
import { SubscriptionStatus } from '@prisma/client';

export interface CreateCheckoutSessionResult {
  sessionId: string;
  url: string;
}

export interface SubscriptionDetails {
  id: string;
  plan: string;
  status: string;
  currentPeriodEnd: Date | null;
  cancelAtPeriodEnd: boolean;
}

// Stripe price IDs for different plans
// In production, these should be configured via environment variables
const STRIPE_PRICES: Record<string, Record<string, string>> = {
  monthly: {
    premium:
      process.env.STRIPE_PRICE_PREMIUM_MONTHLY || 'price_premium_monthly',
  },
  yearly: {
    premium: process.env.STRIPE_PRICE_PREMIUM_YEARLY || 'price_premium_yearly',
  },
};

@Injectable()
export class StripeService {
  private readonly logger = new Logger(StripeService.name);
  private stripe: Stripe | null = null;

  constructor(
    private configService: ConfigService,
    private subscriptionService: SubscriptionService,
    private prisma: PrismaService,
  ) {
    const secretKey = this.configService.get<string>('STRIPE_SECRET_KEY');
    if (secretKey) {
      this.stripe = new Stripe(secretKey, {
        apiVersion: '2026-02-25.clover',
      } as Stripe.StripeConfig);
    } else {
      this.logger.warn(
        'STRIPE_SECRET_KEY not configured. Payment features will be disabled.',
      );
    }
  }

  /**
   * Check if Stripe is properly configured
   */
  isConfigured(): boolean {
    return !!this.stripe;
  }

  /**
   * Get or create a Stripe customer for a user
   */
  async getOrCreateCustomer(
    userId: string,
    email: string,
    name?: string,
  ): Promise<string> {
    if (!this.stripe) {
      throw new Error('Stripe is not configured');
    }

    // Check if user already has a customer ID
    const existingSubscription = await this.prisma.subscription.findFirst({
      where: { user_id: userId, stripe_customer_id: { not: null } },
      select: { stripe_customer_id: true },
    });

    if (existingSubscription?.stripe_customer_id) {
      return existingSubscription.stripe_customer_id;
    }

    // Create new customer
    const customer = await this.stripe.customers.create({
      email,
      name: name || undefined,
      metadata: { userId },
    });

    return customer.id;
  }

  /**
   * Create a checkout session for subscription
   */
  async createCheckoutSession(
    userId: string,
    email: string,
    name: string | undefined,
    planType: 'monthly' | 'yearly',
    successUrl: string,
    cancelUrl: string,
  ): Promise<CreateCheckoutSessionResult> {
    if (!this.stripe) {
      throw new Error('Stripe is not configured');
    }

    const customerId = await this.getOrCreateCustomer(userId, email, name);
    const priceId = STRIPE_PRICES[planType]?.premium;

    if (!priceId) {
      throw new Error(`Invalid plan type: ${planType}`);
    }

    const session = await this.stripe.checkout.sessions.create({
      customer: customerId,
      mode: 'subscription',
      payment_method_types: ['card'],
      line_items: [
        {
          price: priceId,
          quantity: 1,
        },
      ],
      success_url: successUrl,
      cancel_url: cancelUrl,
      metadata: {
        userId,
        planType,
      },
    });

    return {
      sessionId: session.id,
      url: session.url || '',
    };
  }

  /**
   * Create a billing portal session for subscription management
   */
  async createBillingPortalSession(
    customerId: string,
    returnUrl: string,
  ): Promise<string> {
    if (!this.stripe) {
      throw new Error('Stripe is not configured');
    }

    const session = await this.stripe.billingPortal.sessions.create({
      customer: customerId,
      return_url: returnUrl,
    });

    return session.url;
  }

  /**
   * Get subscription details from Stripe
   */
  async getSubscriptionDetails(
    subscriptionId: string,
  ): Promise<SubscriptionDetails | null> {
    if (!this.stripe) {
      return null;
    }

    try {
      const subscription =
        await this.stripe.subscriptions.retrieve(subscriptionId);

      const currentPeriodEnd = (subscription as any).current_period_end;
      return {
        id: subscription.id,
        plan: subscription.items.data[0]?.price.nickname || 'unknown',
        status: subscription.status,
        currentPeriodEnd: currentPeriodEnd
          ? new Date(currentPeriodEnd * 1000)
          : null,
        cancelAtPeriodEnd: subscription.cancel_at_period_end,
      };
    } catch {
      return null;
    }
  }

  /**
   * Cancel a subscription at period end
   */
  async cancelSubscriptionAtPeriodEnd(
    subscriptionId: string,
  ): Promise<SubscriptionDetails | null> {
    if (!this.stripe) {
      return null;
    }

    const subscription = await this.stripe.subscriptions.update(
      subscriptionId,
      {
        cancel_at_period_end: true,
      },
    );

    const currentPeriodEnd = (subscription as any).current_period_end;
    return {
      id: subscription.id,
      plan: subscription.items.data[0]?.price.nickname || 'unknown',
      status: subscription.status,
      currentPeriodEnd: currentPeriodEnd
        ? new Date(currentPeriodEnd * 1000)
        : null,
      cancelAtPeriodEnd: subscription.cancel_at_period_end,
    };
  }

  /**
   * Reactivate a canceled subscription
   */
  async reactivateSubscription(
    subscriptionId: string,
  ): Promise<SubscriptionDetails | null> {
    if (!this.stripe) {
      return null;
    }

    const subscription = await this.stripe.subscriptions.update(
      subscriptionId,
      {
        cancel_at_period_end: false,
      },
    );

    const currentPeriodEnd = (subscription as any).current_period_end;
    return {
      id: subscription.id,
      plan: subscription.items.data[0]?.price.nickname || 'unknown',
      status: subscription.status,
      currentPeriodEnd: currentPeriodEnd
        ? new Date(currentPeriodEnd * 1000)
        : null,
      cancelAtPeriodEnd: subscription.cancel_at_period_end,
    };
  }

  /**
   * Get invoices for a customer
   */
  async getInvoices(
    customerId: string,
    options?: { limit?: number; startingAfter?: string },
  ): Promise<{
    invoices: Array<{
      id: string;
      number: string;
      status: string;
      amount: number;
      currency: string;
      createdAt: Date;
      paidAt: Date | null;
      invoicePdf: string | null;
      hostedUrl: string | null;
    }>;
    hasMore: boolean;
  }> {
    if (!this.stripe) {
      throw new Error('Stripe is not configured');
    }

    const invoices = await this.stripe.invoices.list({
      customer: customerId,
      limit: options?.limit || 10,
      starting_after: options?.startingAfter,
    });

    return {
      invoices: invoices.data.map((invoice) => ({
        id: invoice.id,
        number: invoice.number || '',
        status: invoice.status || 'draft',
        amount: invoice.amount_paid,
        currency: invoice.currency.toUpperCase(),
        createdAt: new Date(invoice.created * 1000),
        paidAt: invoice.status_transitions?.paid_at
          ? new Date(invoice.status_transitions.paid_at * 1000)
          : null,
        invoicePdf: invoice.invoice_pdf ?? null,
        hostedUrl: invoice.hosted_invoice_url ?? null,
      })),
      hasMore: invoices.has_more,
    };
  }

  /**
   * Get a specific invoice by ID
   */
  async getInvoice(invoiceId: string): Promise<{
    id: string;
    number: string;
    status: string;
    amount: number;
    currency: string;
    createdAt: Date;
    paidAt: Date | null;
    invoicePdf: string | null;
    hostedUrl: string | null;
    lines: Array<{
      description: string;
      amount: number;
      currency: string;
      period: { start: Date; end: Date } | null;
    }>;
  } | null> {
    if (!this.stripe) {
      return null;
    }

    try {
      const invoice = await this.stripe.invoices.retrieve(invoiceId);

      return {
        id: invoice.id,
        number: invoice.number || '',
        status: invoice.status || 'draft',
        amount: invoice.amount_paid,
        currency: invoice.currency.toUpperCase(),
        createdAt: new Date(invoice.created * 1000),
        paidAt: invoice.status_transitions?.paid_at
          ? new Date(invoice.status_transitions.paid_at * 1000)
          : null,
        invoicePdf: invoice.invoice_pdf ?? null,
        hostedUrl: invoice.hosted_invoice_url ?? null,
        lines: invoice.lines.data.map((line) => ({
          description: line.description || '',
          amount: line.amount,
          currency: line.currency.toUpperCase(),
          period: line.period
            ? {
                start: new Date(line.period.start * 1000),
                end: new Date(line.period.end * 1000),
              }
            : null,
        })),
      };
    } catch {
      return null;
    }
  }

  /**
   * Get upcoming invoice for a subscription
   */
  async getUpcomingInvoice(customerId: string): Promise<{
    amount: number;
    currency: string;
    nextPaymentAt: Date | null;
  } | null> {
    if (!this.stripe) {
      return null;
    }

    try {
      // Get the active subscription to determine next billing date
      const subscriptions = await this.stripe.subscriptions.list({
        customer: customerId,
        status: 'active',
        limit: 1,
      });

      const activeSub = subscriptions.data[0];
      if (!activeSub) {
        return null;
      }

      const currentPeriodEnd = (activeSub as any).current_period_end;

      return {
        amount: activeSub.items.data.reduce(
          (sum, item) => sum + (item.price.unit_amount || 0),
          0,
        ),
        currency: activeSub.currency?.toUpperCase() || 'USD',
        nextPaymentAt: currentPeriodEnd
          ? new Date(currentPeriodEnd * 1000)
          : null,
      };
    } catch {
      return null;
    }
  }

  /**
   * Handle webhook event from Stripe
   */
  async handleWebhookEvent(event: Stripe.Event): Promise<void> {
    this.logger.log(`Processing webhook event: ${event.type}`);

    switch (event.type) {
      case 'checkout.session.completed':
        await this.handleCheckoutCompleted(event.data.object);
        break;

      case 'customer.subscription.created':
      case 'customer.subscription.updated':
        await this.handleSubscriptionUpdated(event.data.object);
        break;

      case 'customer.subscription.deleted':
        await this.handleSubscriptionDeleted(event.data.object);
        break;

      case 'invoice.payment_failed':
        await this.handlePaymentFailed(event.data.object);
        break;

      default:
        this.logger.debug(`Unhandled event type: ${event.type}`);
    }
  }

  /**
   * Verify webhook signature
   */
  verifyWebhookSignature(
    payload: string | Buffer,
    signature: string,
  ): Stripe.Event {
    if (!this.stripe) {
      throw new Error('Stripe is not configured');
    }

    const webhookSecret = this.configService.get<string>(
      'STRIPE_WEBHOOK_SECRET',
    );

    if (!webhookSecret) {
      throw new Error('STRIPE_WEBHOOK_SECRET not configured');
    }

    return this.stripe.webhooks.constructEvent(
      payload,
      signature,
      webhookSecret,
    );
  }

  private async handleCheckoutCompleted(
    session: Stripe.Checkout.Session,
  ): Promise<void> {
    const userId = session.metadata?.userId;
    const planType = session.metadata?.planType;

    if (!userId || !session.customer) {
      this.logger.error('Missing metadata in checkout session');
      return;
    }

    const customerId =
      typeof session.customer === 'string'
        ? session.customer
        : session.customer.id;

    const subscriptionId =
      typeof session.subscription === 'string'
        ? session.subscription
        : session.subscription?.id;

    // Calculate expiration based on plan type
    const expiresAt = new Date();
    if (planType === 'yearly') {
      expiresAt.setFullYear(expiresAt.getFullYear() + 1);
    } else {
      expiresAt.setMonth(expiresAt.getMonth() + 1);
    }

    // Create or update subscription
    await this.subscriptionService.createSubscription({
      userId,
      plan:
        planType === 'yearly'
          ? SubscriptionPlan.PREMIUM_YEARLY
          : SubscriptionPlan.PREMIUM_MONTHLY,
      status: SubscriptionStatus.ACTIVE,
      expiresAt,
    });

    // Update with Stripe IDs
    await this.prisma.subscription.updateMany({
      where: { user_id: userId, status: SubscriptionStatus.ACTIVE },
      data: {
        stripe_customer_id: customerId,
        stripe_subscription_id: subscriptionId,
        stripe_current_period_end: expiresAt,
      },
    });

    this.logger.log(`Subscription created for user ${userId}`);
  }

  private async handleSubscriptionUpdated(
    subscription: Stripe.Subscription,
  ): Promise<void> {
    const customerId =
      typeof subscription.customer === 'string'
        ? subscription.customer
        : subscription.customer.id;

    // Find subscription by Stripe customer ID
    const existingSub = await this.prisma.subscription.findFirst({
      where: { stripe_customer_id: customerId },
    });

    if (!existingSub) {
      this.logger.warn(
        `No subscription found for Stripe customer ${customerId}`,
      );
      return;
    }

    const currentPeriodEndTs = (subscription as any).current_period_end;
    const currentPeriodEnd = currentPeriodEndTs
      ? new Date(currentPeriodEndTs * 1000)
      : null;
    const status = this.mapStripeStatus(subscription.status);

    await this.prisma.subscription.update({
      where: { id: existingSub.id },
      data: {
        status: status,
        stripe_subscription_id: subscription.id,
        stripe_current_period_end: currentPeriodEnd,
        expires_at: currentPeriodEnd,
        cancelled_at:
          subscription.canceled_at && subscription.cancel_at_period_end
            ? new Date(subscription.canceled_at * 1000)
            : null,
      },
    });

    this.logger.log(
      `Subscription ${subscription.id} updated for user ${existingSub.user_id}`,
    );
  }

  private async handleSubscriptionDeleted(
    subscription: Stripe.Subscription,
  ): Promise<void> {
    const customerId =
      typeof subscription.customer === 'string'
        ? subscription.customer
        : subscription.customer.id;

    const existingSub = await this.prisma.subscription.findFirst({
      where: { stripe_customer_id: customerId },
    });

    if (!existingSub) {
      return;
    }

    await this.prisma.subscription.update({
      where: { id: existingSub.id },
      data: {
        status: SubscriptionStatus.CANCELLED,
        cancelled_at: new Date(),
      },
    });

    this.logger.log(
      `Subscription ${subscription.id} cancelled for user ${existingSub.user_id}`,
    );
  }

  private async handlePaymentFailed(invoice: Stripe.Invoice): Promise<void> {
    if (!invoice.customer) {
      return;
    }

    const customerId =
      typeof invoice.customer === 'string'
        ? invoice.customer
        : invoice.customer.id;

    const existingSub = await this.prisma.subscription.findFirst({
      where: { stripe_customer_id: customerId },
    });

    if (!existingSub) {
      return;
    }

    // Set subscription to pending status on payment failure
    await this.prisma.subscription.update({
      where: { id: existingSub.id },
      data: { status: SubscriptionStatus.PENDING },
    });

    this.logger.warn(
      `Payment failed for subscription of user ${existingSub.user_id}`,
    );
  }

  private mapStripeStatus(
    status: Stripe.Subscription.Status,
  ): SubscriptionStatus {
    const statusMap: Record<string, SubscriptionStatus> = {
      active: SubscriptionStatus.ACTIVE,
      canceled: SubscriptionStatus.CANCELLED,
      incomplete: SubscriptionStatus.PENDING,
      incomplete_expired: SubscriptionStatus.EXPIRED,
      past_due: SubscriptionStatus.PENDING,
      paused: SubscriptionStatus.PENDING,
      trialing: SubscriptionStatus.ACTIVE,
      unpaid: SubscriptionStatus.PENDING,
    };
    return statusMap[status] || SubscriptionStatus.PENDING;
  }
}
