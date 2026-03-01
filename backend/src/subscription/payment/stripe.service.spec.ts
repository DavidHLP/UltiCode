import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { StripeService } from './stripe.service';
import { SubscriptionService } from '../subscription.service';
import { PrismaService } from '../../prisma.service';
import { SubscriptionStatus } from '@prisma/client';
import Stripe from 'stripe';

// Mock Stripe
jest.mock('stripe', () => {
  return jest.fn().mockImplementation(() => ({
    customers: {
      create: jest.fn(),
    },
    checkout: {
      sessions: {
        create: jest.fn(),
      },
    },
    billingPortal: {
      sessions: {
        create: jest.fn(),
      },
    },
    subscriptions: {
      retrieve: jest.fn(),
      update: jest.fn(),
      list: jest.fn(),
    },
    invoices: {
      list: jest.fn(),
      retrieve: jest.fn(),
    },
    webhooks: {
      constructEvent: jest.fn(),
    },
  }));
});

describe('StripeService', () => {
  let service: StripeService;
  let configService: jest.Mocked<ConfigService>;
  let subscriptionService: jest.Mocked<SubscriptionService>;
  let prisma: jest.Mocked<PrismaService>;
  let mockStripe: any;

  const mockPrismaService = {
    subscription: {
      findFirst: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
    },
  };

  const mockSubscriptionService = {
    createSubscription: jest.fn(),
  };

  beforeEach(async () => {
    // Reset the Stripe mock
    (Stripe as any).mockClear();

    // Create a new mock Stripe instance for each test
    mockStripe = {
      customers: {
        create: jest.fn(),
      },
      checkout: {
        sessions: {
          create: jest.fn(),
        },
      },
      billingPortal: {
        sessions: {
          create: jest.fn(),
        },
      },
      subscriptions: {
        retrieve: jest.fn(),
        update: jest.fn(),
        list: jest.fn(),
      },
      invoices: {
        list: jest.fn(),
        retrieve: jest.fn(),
      },
      webhooks: {
        constructEvent: jest.fn(),
      },
    };

    (Stripe as any).mockImplementation(() => mockStripe);

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        StripeService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => {
              if (key === 'STRIPE_SECRET_KEY') return 'sk_test_123';
              if (key === 'STRIPE_WEBHOOK_SECRET') return 'whsec_test';
              return null;
            }),
          },
        },
        {
          provide: SubscriptionService,
          useValue: mockSubscriptionService,
        },
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<StripeService>(StripeService);
    configService = module.get(ConfigService);
    subscriptionService = module.get(SubscriptionService);
    prisma = module.get(PrismaService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('isConfigured', () => {
    it('should return true when Stripe is configured', () => {
      expect(service.isConfigured()).toBe(true);
    });
  });

  describe('getOrCreateCustomer', () => {
    it('should return existing customer ID if exists', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue({
        stripe_customer_id: 'cus_existing123',
      } as any);

      const result = await service.getOrCreateCustomer(
        'user-1',
        'test@example.com',
      );

      expect(result).toBe('cus_existing123');
      expect(mockStripe.customers.create).not.toHaveBeenCalled();
    });

    it('should create new customer if not exists', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue(null);
      mockStripe.customers.create.mockResolvedValue({ id: 'cus_new123' });

      const result = await service.getOrCreateCustomer(
        'user-1',
        'test@example.com',
        'John Doe',
      );

      expect(result).toBe('cus_new123');
      expect(mockStripe.customers.create).toHaveBeenCalledWith({
        email: 'test@example.com',
        name: 'John Doe',
        metadata: { userId: 'user-1' },
      });
    });

    it('should throw error when Stripe is not configured', async () => {
      // Create service without Stripe key
      const moduleWithoutStripe: TestingModule = await Test.createTestingModule(
        {
          providers: [
            StripeService,
            {
              provide: ConfigService,
              useValue: {
                get: jest.fn().mockReturnValue(null),
              },
            },
            {
              provide: SubscriptionService,
              useValue: mockSubscriptionService,
            },
            {
              provide: PrismaService,
              useValue: mockPrismaService,
            },
          ],
        },
      ).compile();

      const serviceWithoutStripe =
        moduleWithoutStripe.get<StripeService>(StripeService);

      await expect(
        serviceWithoutStripe.getOrCreateCustomer('user-1', 'test@example.com'),
      ).rejects.toThrow('Stripe is not configured');
    });
  });

  describe('createCheckoutSession', () => {
    it('should create checkout session for monthly plan', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue(null);
      mockStripe.customers.create.mockResolvedValue({ id: 'cus_123' });
      mockStripe.checkout.sessions.create.mockResolvedValue({
        id: 'cs_123',
        url: 'https://checkout.stripe.com/session',
      });

      const result = await service.createCheckoutSession(
        'user-1',
        'test@example.com',
        'John Doe',
        'monthly',
        'https://example.com/success',
        'https://example.com/cancel',
      );

      expect(result.sessionId).toBe('cs_123');
      expect(result.url).toBe('https://checkout.stripe.com/session');
      expect(mockStripe.checkout.sessions.create).toHaveBeenCalledWith(
        expect.objectContaining({
          customer: 'cus_123',
          mode: 'subscription',
          payment_method_types: ['card'],
          success_url: 'https://example.com/success',
          cancel_url: 'https://example.com/cancel',
        }),
      );
    });

    it('should create checkout session for yearly plan', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue(null);
      mockStripe.customers.create.mockResolvedValue({ id: 'cus_123' });
      mockStripe.checkout.sessions.create.mockResolvedValue({
        id: 'cs_456',
        url: 'https://checkout.stripe.com/session',
      });

      const result = await service.createCheckoutSession(
        'user-1',
        'test@example.com',
        'John Doe',
        'yearly',
        'https://example.com/success',
        'https://example.com/cancel',
      );

      expect(result.sessionId).toBe('cs_456');
    });
  });

  describe('createBillingPortalSession', () => {
    it('should create billing portal session', async () => {
      mockStripe.billingPortal.sessions.create.mockResolvedValue({
        url: 'https://billing.stripe.com/portal',
      });

      const result = await service.createBillingPortalSession(
        'cus_123',
        'https://example.com/return',
      );

      expect(result).toBe('https://billing.stripe.com/portal');
      expect(mockStripe.billingPortal.sessions.create).toHaveBeenCalledWith({
        customer: 'cus_123',
        return_url: 'https://example.com/return',
      });
    });
  });

  describe('getSubscriptionDetails', () => {
    it('should return subscription details', async () => {
      mockStripe.subscriptions.retrieve.mockResolvedValue({
        id: 'sub_123',
        status: 'active',
        cancel_at_period_end: false,
        items: {
          data: [{ price: { nickname: 'Premium Monthly' } }],
        },
        current_period_end: 1704067200, // Unix timestamp
      } as any);

      const result = await service.getSubscriptionDetails('sub_123');

      expect(result).toEqual({
        id: 'sub_123',
        plan: 'Premium Monthly',
        status: 'active',
        currentPeriodEnd: expect.any(Date),
        cancelAtPeriodEnd: false,
      });
    });

    it('should return null on error', async () => {
      mockStripe.subscriptions.retrieve.mockRejectedValue(
        new Error('Not found'),
      );

      const result = await service.getSubscriptionDetails('sub_invalid');

      expect(result).toBeNull();
    });
  });

  describe('cancelSubscriptionAtPeriodEnd', () => {
    it('should cancel subscription at period end', async () => {
      mockStripe.subscriptions.update.mockResolvedValue({
        id: 'sub_123',
        status: 'active',
        cancel_at_period_end: true,
        items: {
          data: [{ price: { nickname: 'Premium Monthly' } }],
        },
        current_period_end: 1704067200,
      } as any);

      const result = await service.cancelSubscriptionAtPeriodEnd('sub_123');

      expect(result?.cancelAtPeriodEnd).toBe(true);
      expect(mockStripe.subscriptions.update).toHaveBeenCalledWith('sub_123', {
        cancel_at_period_end: true,
      });
    });
  });

  describe('reactivateSubscription', () => {
    it('should reactivate subscription', async () => {
      mockStripe.subscriptions.update.mockResolvedValue({
        id: 'sub_123',
        status: 'active',
        cancel_at_period_end: false,
        items: {
          data: [{ price: { nickname: 'Premium Monthly' } }],
        },
        current_period_end: 1704067200,
      } as any);

      const result = await service.reactivateSubscription('sub_123');

      expect(result?.cancelAtPeriodEnd).toBe(false);
      expect(mockStripe.subscriptions.update).toHaveBeenCalledWith('sub_123', {
        cancel_at_period_end: false,
      });
    });
  });

  describe('getInvoices', () => {
    it('should return invoices for customer', async () => {
      mockStripe.invoices.list.mockResolvedValue({
        data: [
          {
            id: 'inv_123',
            number: 'INV-001',
            status: 'paid',
            amount_paid: 999,
            currency: 'usd',
            created: 1704067200,
            status_transitions: { paid_at: 1704153600 },
            invoice_pdf: 'https://stripe.com/invoice.pdf',
            hosted_invoice_url: 'https://stripe.com/invoice',
          },
        ],
        has_more: false,
      } as any);

      const result = await service.getInvoices('cus_123');

      expect(result.invoices).toHaveLength(1);
      expect(result.invoices[0].id).toBe('inv_123');
      expect(result.invoices[0].amount).toBe(999);
      expect(result.hasMore).toBe(false);
    });

    it('should support pagination options', async () => {
      mockStripe.invoices.list.mockResolvedValue({
        data: [],
        has_more: true,
      } as any);

      await service.getInvoices('cus_123', {
        limit: 5,
        startingAfter: 'inv_100',
      });

      expect(mockStripe.invoices.list).toHaveBeenCalledWith({
        customer: 'cus_123',
        limit: 5,
        starting_after: 'inv_100',
      });
    });
  });

  describe('getInvoice', () => {
    it('should return invoice details', async () => {
      mockStripe.invoices.retrieve.mockResolvedValue({
        id: 'inv_123',
        number: 'INV-001',
        status: 'paid',
        amount_paid: 999,
        currency: 'usd',
        created: 1704067200,
        status_transitions: { paid_at: 1704153600 },
        invoice_pdf: 'https://stripe.com/invoice.pdf',
        hosted_invoice_url: 'https://stripe.com/invoice',
        lines: {
          data: [
            {
              description: 'Premium Monthly',
              amount: 999,
              currency: 'usd',
              period: { start: 1704067200, end: 1706745600 },
            },
          ],
        },
      } as any);

      const result = await service.getInvoice('inv_123');

      expect(result?.id).toBe('inv_123');
      expect(result?.lines).toHaveLength(1);
      expect(result?.lines[0].description).toBe('Premium Monthly');
    });

    it('should return null on error', async () => {
      mockStripe.invoices.retrieve.mockRejectedValue(new Error('Not found'));

      const result = await service.getInvoice('inv_invalid');

      expect(result).toBeNull();
    });
  });

  describe('getUpcomingInvoice', () => {
    it('should return upcoming invoice info', async () => {
      mockStripe.subscriptions.list.mockResolvedValue({
        data: [
          {
            id: 'sub_123',
            currency: 'usd',
            items: {
              data: [{ price: { unit_amount: 999 } }],
            },
            current_period_end: 1706745600,
          } as any,
        ],
      } as any);

      const result = await service.getUpcomingInvoice('cus_123');

      expect(result?.amount).toBe(999);
      expect(result?.currency).toBe('USD');
      expect(result?.nextPaymentAt).toBeInstanceOf(Date);
    });

    it('should return null when no active subscription', async () => {
      mockStripe.subscriptions.list.mockResolvedValue({
        data: [],
      } as any);

      const result = await service.getUpcomingInvoice('cus_123');

      expect(result).toBeNull();
    });
  });

  describe('verifyWebhookSignature', () => {
    it('should verify and return event', () => {
      const mockEvent = {
        type: 'checkout.session.completed',
        data: { object: {} },
      };
      mockStripe.webhooks.constructEvent.mockReturnValue(mockEvent);

      const result = service.verifyWebhookSignature('payload', 'signature');

      expect(result).toEqual(mockEvent);
      expect(mockStripe.webhooks.constructEvent).toHaveBeenCalledWith(
        'payload',
        'signature',
        'whsec_test',
      );
    });
  });

  describe('handleWebhookEvent', () => {
    it('should handle checkout.session.completed event', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue(null);
      mockSubscriptionService.createSubscription.mockResolvedValue({} as any);
      mockPrismaService.subscription.updateMany.mockResolvedValue({ count: 1 });

      const event = {
        type: 'checkout.session.completed',
        data: {
          object: {
            customer: 'cus_123',
            subscription: 'sub_123',
            metadata: { userId: 'user-1', planType: 'monthly' },
          },
        },
      } as unknown as Stripe.Event;

      await service.handleWebhookEvent(event);

      expect(mockSubscriptionService.createSubscription).toHaveBeenCalled();
    });

    it('should handle customer.subscription.updated event', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue({
        id: 'sub-internal-123',
        user_id: 'user-1',
      } as any);
      mockPrismaService.subscription.update.mockResolvedValue({} as any);

      const event = {
        type: 'customer.subscription.updated',
        data: {
          object: {
            id: 'sub_123',
            customer: 'cus_123',
            status: 'active',
            cancel_at_period_end: false,
            items: { data: [] },
            current_period_end: 1706745600,
          },
        },
      } as unknown as Stripe.Event;

      await service.handleWebhookEvent(event);

      expect(mockPrismaService.subscription.update).toHaveBeenCalled();
    });

    it('should handle customer.subscription.deleted event', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue({
        id: 'sub-internal-123',
        user_id: 'user-1',
      } as any);
      mockPrismaService.subscription.update.mockResolvedValue({} as any);

      const event = {
        type: 'customer.subscription.deleted',
        data: {
          object: {
            id: 'sub_123',
            customer: 'cus_123',
          },
        },
      } as Stripe.Event;

      await service.handleWebhookEvent(event);

      expect(mockPrismaService.subscription.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: SubscriptionStatus.CANCELLED,
          }),
        }),
      );
    });

    it('should handle invoice.payment_failed event', async () => {
      mockPrismaService.subscription.findFirst.mockResolvedValue({
        id: 'sub-internal-123',
        user_id: 'user-1',
      } as any);
      mockPrismaService.subscription.update.mockResolvedValue({} as any);

      const event = {
        type: 'invoice.payment_failed',
        data: {
          object: {
            customer: 'cus_123',
          },
        },
      } as Stripe.Event;

      await service.handleWebhookEvent(event);

      expect(mockPrismaService.subscription.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: SubscriptionStatus.PENDING,
          }),
        }),
      );
    });

    it('should log unhandled event types', async () => {
      const event = {
        type: 'unknown.event.type',
        data: { object: {} },
      } as unknown as Stripe.Event;

      await service.handleWebhookEvent(event);

      // Should not throw, just log
    });
  });

  describe('service initialization', () => {
    it('should be defined', () => {
      expect(service).toBeDefined();
    });

    it('should have config service', () => {
      expect(configService).toBeDefined();
    });

    it('should have subscription service', () => {
      expect(subscriptionService).toBeDefined();
    });

    it('should have prisma service', () => {
      expect(prisma).toBeDefined();
    });
  });
});
