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
import { SubscriptionService } from './subscription.service';
import { UserService } from '../user/user.service';
import { UserRole } from '../user/user.service';
import {
  CreateSubscriptionDto,
  UpdateSubscriptionDto,
} from './dto/subscription.dto';

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
  async createSubscription(@Body() dto: CreateSubscriptionDto) {
    const user = await this.userService.findOne(dto.userId);
    if (!user) {
      return { error: 'User not found', statusCode: 404 };
    }

    const subscription = await this.subscriptionService.createSubscription({
      userId: dto.userId,
      plan: dto.plan,
      status: dto.status,
      expiresAt: dto.expiresAt ? new Date(dto.expiresAt) : undefined,
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
    @Body() dto: UpdateSubscriptionDto,
  ) {
    const subscription = await this.subscriptionService.updateSubscription(id, {
      status: dto.status,
      expiresAt: dto.expiresAt ? new Date(dto.expiresAt) : undefined,
    });

    return subscription;
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async cancelSubscription(@Param('id') id: string) {
    await this.subscriptionService.cancelSubscription(id);
  }
}
