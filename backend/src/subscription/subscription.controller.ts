import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Param,
  Body,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { AuthGuard } from '../auth/auth.guard';
import { RolesGuard } from '../admin/guards/roles.guard';
import { RequireRoles } from '../admin/decorators/roles.decorator';
import {
  SubscriptionService,
  SubscriptionPlan,
  SubscriptionStatus,
} from './subscription.service';
import { UserService } from '../user/user.service';
import { UserRole } from '../user/user.entity';

@Controller('admin/subscriptions')
@UseGuards(AuthGuard, RolesGuard)
@RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
export class SubscriptionController {
  constructor(
    private readonly subscriptionService: SubscriptionService,
    private readonly userService: UserService,
  ) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async createSubscription(
    @Body()
    data: {
      userId: string;
      plan: SubscriptionPlan;
      status?: SubscriptionStatus;
      expiresAt?: string;
    },
  ) {
    const user = await this.userService.findOne(data.userId);
    if (!user) {
      return { error: 'User not found', statusCode: 404 };
    }

    const subscription = await this.subscriptionService.createSubscription({
      userId: data.userId,
      plan: data.plan,
      status: data.status,
      expiresAt: data.expiresAt ? new Date(data.expiresAt) : undefined,
    });

    return subscription;
  }

  @Get('user/:userId')
  async getUserSubscription(@Param('userId') userId: string) {
    const subscription =
      await this.subscriptionService.getActiveSubscription(userId);
    return subscription || { message: 'No active subscription found' };
  }

  @Patch(':id')
  async updateSubscription(
    @Param('id') id: string,
    @Body()
    data: {
      status?: SubscriptionStatus;
      expiresAt?: string;
    },
  ) {
    const subscription = await this.subscriptionService.updateSubscription(id, {
      status: data.status,
      expiresAt: data.expiresAt ? new Date(data.expiresAt) : undefined,
    });

    return subscription;
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async cancelSubscription(@Param('id') id: string) {
    await this.subscriptionService.cancelSubscription(id);
  }
}
