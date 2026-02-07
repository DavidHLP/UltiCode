import { Injectable, NotFoundException } from '@nestjs/common';
import { UserService } from '../../user/user.service';
import { PrismaService } from '../../prisma.service';

@Injectable()
export class AccountService {
  constructor(
    private userService: UserService,
    private prisma: PrismaService,
  ) {}

  async getSubscription(userId: string) {
    const user = await this.userService.findOne(userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    // Get subscription from Prisma
    const subscription = await this.prisma.subscription.findFirst({
      where: {
        user_id: userId,
      },
      orderBy: {
        started_at: 'desc',
      },
    });

    if (!subscription) {
      // Return null - no subscription means free plan
      return null;
    }

    return {
      id: subscription.id,
      plan: subscription.plan,
      status: subscription.status,
      started_at: subscription.started_at,
      expires_at: subscription.expires_at,
      cancelled_at: subscription.cancelled_at,
    };
  }
}
