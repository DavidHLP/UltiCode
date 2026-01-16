import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';

export enum SubscriptionPlan {
  FREE = 'FREE',
  PREMIUM_MONTHLY = 'PREMIUM_MONTHLY',
  PREMIUM_YEARLY = 'PREMIUM_YEARLY',
}

export enum SubscriptionStatus {
  ACTIVE = 'ACTIVE',
  CANCELLED = 'CANCELLED',
  EXPIRED = 'EXPIRED',
  PENDING = 'PENDING',
}

export interface SubscriptionCheckResult {
  hasAccess: boolean;
  subscription: {
    plan: string;
    status: string;
    expiresAt: Date | null;
  } | null;
}

@Injectable()
export class SubscriptionService {
  constructor(private prisma: PrismaService) {}

  /**
   * Check if user has active premium access
   * Admins and super admins always have premium access
   */
  async hasPremiumAccess(
    userId: string,
    userRole?: string,
  ): Promise<SubscriptionCheckResult> {
    // Admin and super admin users always have premium access
    if (userRole === 'ADMIN' || userRole === 'SUPER_ADMIN') {
      return {
        hasAccess: true,
        subscription: {
          plan: 'ADMIN',
          status: 'ACTIVE',
          expiresAt: null,
        },
      };
    }

    const subscription = await this.prisma.subscription.findFirst({
      where: {
        user_id: userId,
        status: 'ACTIVE',
      },
      orderBy: {
        created_at: 'desc',
      },
    });

    if (!subscription) {
      return {
        hasAccess: false,
        subscription: null,
      };
    }

    // Check if subscription has expired
    if (subscription.expires_at && subscription.expires_at < new Date()) {
      // Update status to expired
      await this.prisma.subscription.update({
        where: { id: subscription.id },
        data: { status: 'EXPIRED' },
      });

      return {
        hasAccess: false,
        subscription: {
          plan: subscription.plan,
          status: 'EXPIRED',
          expiresAt: subscription.expires_at,
        },
      };
    }

    // Check if user has premium plan
    const hasPremium =
      subscription.plan === 'PREMIUM_MONTHLY' ||
      subscription.plan === 'PREMIUM_YEARLY';

    return {
      hasAccess: hasPremium,
      subscription: {
        plan: subscription.plan,
        status: subscription.status,
        expiresAt: subscription.expires_at,
      },
    };
  }

  /**
   * Get active subscription for a user
   */
  async getActiveSubscription(userId: string) {
    return this.prisma.subscription.findFirst({
      where: {
        user_id: userId,
        status: 'ACTIVE',
      },
      orderBy: {
        created_at: 'desc',
      },
    });
  }

  /**
   * Create a new subscription
   */
  async createSubscription(data: {
    userId: string;
    plan: SubscriptionPlan;
    status?: SubscriptionStatus;
    expiresAt?: Date;
  }) {
    return this.prisma.subscription.create({
      data: {
        user_id: data.userId,
        plan: data.plan,
        status: data.status || 'ACTIVE',
        expires_at: data.expiresAt,
      },
    });
  }

  /**
   * Update subscription status
   */
  async updateSubscription(
    id: string,
    data: {
      status?: SubscriptionStatus;
      expiresAt?: Date;
      cancelledAt?: Date;
    },
  ) {
    return this.prisma.subscription.update({
      where: { id },
      data: {
        status: data.status,
        expires_at: data.expiresAt,
        cancelled_at: data.cancelledAt,
      },
    });
  }

  /**
   * Cancel subscription
   */
  async cancelSubscription(id: string) {
    return this.prisma.subscription.update({
      where: { id },
      data: {
        status: 'CANCELLED',
        cancelled_at: new Date(),
      },
    });
  }
}
