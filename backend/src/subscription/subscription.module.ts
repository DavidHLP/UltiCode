import { Module } from '@nestjs/common';
import { SubscriptionService } from './subscription.service';
import { SubscriptionController } from './subscription.controller';
import { UserSubscriptionController } from './user-subscription.controller';
import { StripeService } from './payment/stripe.service';
import { StripeWebhookController } from './payment/webhook.controller';
import { PrismaService } from '../prisma.service';
import { UserModule } from '../user/user.module';

@Module({
  imports: [UserModule],
  controllers: [
    SubscriptionController,
    UserSubscriptionController,
    StripeWebhookController,
  ],
  providers: [SubscriptionService, StripeService, PrismaService],
  exports: [SubscriptionService, StripeService],
})
export class SubscriptionModule {}
