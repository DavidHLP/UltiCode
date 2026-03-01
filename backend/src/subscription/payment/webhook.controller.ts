import {
  Controller,
  Post,
  Req,
  Res,
  HttpCode,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { StripeService } from './stripe.service';

@Controller('webhooks/stripe')
export class StripeWebhookController {
  private readonly logger = new Logger(StripeWebhookController.name);

  constructor(private stripeService: StripeService) {}

  @Post()
  @HttpCode(HttpStatus.OK)
  handleWebhook(@Req() req: Request, @Res() res: Response): void {
    const signature = req.headers['stripe-signature'] as string;

    if (!signature) {
      this.logger.warn('Missing Stripe signature');
      res.status(HttpStatus.BAD_REQUEST).json({ error: 'Missing signature' });
      return;
    }

    if (!this.stripeService.isConfigured()) {
      this.logger.warn('Stripe not configured, ignoring webhook');
      res.status(HttpStatus.OK).json({ received: true });
      return;
    }

    try {
      const event = this.stripeService.verifyWebhookSignature(
        req.body,
        signature,
      );

      this.stripeService
        .handleWebhookEvent(event)
        .then(() => {
          res.json({ received: true });
        })
        .catch((error) => {
          const message =
            error instanceof Error ? error.message : 'Unknown error';
          this.logger.error(`Webhook processing error: ${message}`);
          res.status(HttpStatus.INTERNAL_SERVER_ERROR).json({ error: message });
        });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Webhook error: ${message}`);
      res.status(HttpStatus.BAD_REQUEST).json({ error: message });
    }
  }
}
