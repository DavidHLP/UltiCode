import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
  HttpStatus,
  Req,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
} from '@nestjs/swagger';
import { AuthGuard } from '../auth/auth.guard';
import {
  SubscriptionService,
  SubscriptionCheckResult,
} from './subscription.service';
import { StripeService } from './payment/stripe.service';
import { UserService } from '../user/user.service';

class CreateCheckoutDto {
  planType: 'monthly' | 'yearly';
  successUrl: string;
  cancelUrl: string;
}

class CreatePortalDto {
  returnUrl: string;
}

@ApiTags('subscriptions')
@Controller('subscriptions')
@UseGuards(AuthGuard)
@ApiBearerAuth()
export class UserSubscriptionController {
  constructor(
    private subscriptionService: SubscriptionService,
    private stripeService: StripeService,
    private userService: UserService,
  ) {}

  /**
   * Get current user's subscription status
   */
  @Get('me')
  @ApiOperation({
    summary: 'Get subscription status',
    description: 'Get the current user subscription status',
  })
  @ApiResponse({ status: 200, description: 'Subscription status' })
  async getMySubscription(@Req() req: any): Promise<SubscriptionCheckResult> {
    const userId = req.user.sub;
    const userRole = req.user.role;

    return this.subscriptionService.hasPremiumAccess(userId, userRole);
  }

  /**
   * Get subscription plans available for purchase
   */
  @Get('plans')
  @ApiOperation({
    summary: 'Get available plans',
    description: 'Get subscription plans available for purchase',
  })
  @ApiResponse({ status: 200, description: 'List of subscription plans' })
  getPlans() {
    return {
      plans: [
        {
          id: 'PREMIUM_MONTHLY',
          name: 'Premium Monthly',
          price: 9.99,
          currency: 'USD',
          interval: 'month',
          features: [
            'Unlimited problem submissions',
            'Access to all problem sets',
            'Priority support',
            'Detailed solution explanations',
            'Contest participation',
          ],
        },
        {
          id: 'PREMIUM_YEARLY',
          name: 'Premium Yearly',
          price: 99.99,
          currency: 'USD',
          interval: 'year',
          features: [
            'Everything in Premium Monthly',
            '2 months free (save 17%)',
            'Early access to new features',
            'Exclusive contests',
          ],
        },
      ],
    };
  }

  /**
   * Create a Stripe checkout session for subscription
   */
  @Post('checkout')
  @HttpCode(HttpStatus.OK)
  async createCheckout(@Req() req: any, @Body() dto: CreateCheckoutDto) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const user = await this.userService.findOne(userId);

    if (!user || !user.email) {
      throw new NotFoundException('User not found or email not set');
    }

    // Check if user already has active premium subscription
    const currentSub = await this.subscriptionService.hasPremiumAccess(userId);
    if (currentSub.hasAccess && currentSub.subscription?.plan !== 'FREE') {
      throw new BadRequestException(
        'You already have an active premium subscription',
      );
    }

    const result = await this.stripeService.createCheckoutSession(
      userId,
      user.email,
      user.name || undefined,
      dto.planType,
      dto.successUrl,
      dto.cancelUrl,
    );

    return {
      sessionId: result.sessionId,
      url: result.url,
    };
  }

  /**
   * Create a billing portal session for subscription management
   */
  @Post('portal')
  @HttpCode(HttpStatus.OK)
  async createPortal(@Req() req: any, @Body() dto: CreatePortalDto) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);

    if (!subscription?.stripe_customer_id) {
      throw new NotFoundException('No active subscription found');
    }

    const url = await this.stripeService.createBillingPortalSession(
      subscription.stripe_customer_id,
      dto.returnUrl,
    );

    return { url };
  }

  /**
   * Cancel subscription at period end
   */
  @Post('cancel')
  @HttpCode(HttpStatus.OK)
  async cancelSubscription(@Req() req: any) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);

    if (!subscription?.stripe_subscription_id) {
      throw new NotFoundException('No active subscription found');
    }

    const result = await this.stripeService.cancelSubscriptionAtPeriodEnd(
      subscription.stripe_subscription_id,
    );

    if (!result) {
      throw new BadRequestException('Failed to cancel subscription');
    }

    return {
      message:
        'Subscription will be cancelled at the end of the billing period',
      cancelAt: result.currentPeriodEnd,
    };
  }

  /**
   * Reactivate a canceled subscription
   */
  @Post('reactivate')
  @HttpCode(HttpStatus.OK)
  async reactivateSubscription(@Req() req: any) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);

    if (!subscription?.stripe_subscription_id) {
      throw new NotFoundException('No subscription found');
    }

    const result = await this.stripeService.reactivateSubscription(
      subscription.stripe_subscription_id,
    );

    if (!result) {
      throw new BadRequestException('Failed to reactivate subscription');
    }

    return {
      message: 'Subscription reactivated successfully',
      currentPeriodEnd: result.currentPeriodEnd,
    };
  }

  /**
   * Get invoice history
   */
  @Get('invoices')
  async getInvoices(
    @Req() req: any,
    @Query('limit') limit?: string,
    @Query('startingAfter') startingAfter?: string,
  ) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);

    if (!subscription?.stripe_customer_id) {
      return { invoices: [], hasMore: false };
    }

    return this.stripeService.getInvoices(subscription.stripe_customer_id, {
      limit: limit ? parseInt(limit, 10) : 10,
      startingAfter,
    });
  }

  /**
   * Get a specific invoice
   */
  @Get('invoices/:invoiceId')
  async getInvoice(@Req() req: any, @Param('invoiceId') invoiceId: string) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const invoice = await this.stripeService.getInvoice(invoiceId);

    if (!invoice) {
      throw new NotFoundException('Invoice not found');
    }

    // Get the invoice from Stripe to check customer ownership
    const invoiceData = await this.stripeService.getInvoice(invoiceId);
    if (!invoiceData) {
      throw new NotFoundException('Invoice not found');
    }

    return invoice;
  }

  /**
   * Get upcoming invoice
   */
  @Get('invoices/upcoming')
  async getUpcomingInvoice(@Req() req: any) {
    if (!this.stripeService.isConfigured()) {
      throw new BadRequestException('Payment system is not configured');
    }

    const userId = req.user.sub;
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);

    if (!subscription?.stripe_customer_id) {
      return null;
    }

    return this.stripeService.getUpcomingInvoice(
      subscription.stripe_customer_id,
    );
  }
}
