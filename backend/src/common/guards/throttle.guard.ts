import { Injectable, ExecutionContext, Logger, Inject } from '@nestjs/common';
import {
  ThrottlerGuard,
  ThrottlerException,
  getOptionsToken,
  getStorageToken,
} from '@nestjs/throttler';
import type {
  ThrottlerModuleOptions,
  ThrottlerStorage,
} from '@nestjs/throttler';
import type { Response, Request } from 'express';
import { Reflector } from '@nestjs/core';

export const THROTTLE_KEY = 'throttle_config';

// Rate limit configurations
export const THROTTLE_CONFIGS = {
  global: { limit: 100, ttl: 60000 }, // 100/min default
  submission: { limit: 10, ttl: 60000 }, // 10/min for code submissions
  search: { limit: 30, ttl: 60000 }, // 30/min for search
  auth: { limit: 5, ttl: 300000 }, // 5/5min for auth
  admin: { limit: 50, ttl: 60000 }, // 50/min for admin
};

// Type for throttle config
type ThrottleConfigType = { limit: number; ttl: number };

// Combined decorator that works as both class and method decorator

export function CustomThrottle(config: ThrottleConfigType): any {
  return function (
    target: unknown,
    propertyKey?: string | symbol,
    descriptor?: TypedPropertyDescriptor<unknown>,
  ): TypedPropertyDescriptor<unknown> | void {
    if (propertyKey !== undefined && descriptor !== undefined) {
      // Used as method decorator: store metadata on the method
      if (descriptor.value) {
        Reflect.defineMetadata(THROTTLE_KEY, config, descriptor.value);
      }
      return descriptor;
    } else {
      // Used as class decorator: store metadata on the class constructor
      Reflect.defineMetadata(THROTTLE_KEY, config, target as object);
    }
  };
}

// Pre-configured decorators
/* eslint-disable @typescript-eslint/no-unsafe-return */
export const ThrottleSubmission = () =>
  CustomThrottle(THROTTLE_CONFIGS.submission);

export const ThrottleSearch = () => CustomThrottle(THROTTLE_CONFIGS.search);

export const ThrottleAuth = () => CustomThrottle(THROTTLE_CONFIGS.auth);

export const ThrottleAdmin = () => CustomThrottle(THROTTLE_CONFIGS.admin);
/* eslint-enable @typescript-eslint/no-unsafe-return */

@Injectable()
export class CustomThrottlerGuard extends ThrottlerGuard {
  private readonly logger = new Logger(CustomThrottlerGuard.name);

  constructor(
    @Inject(getOptionsToken()) options: ThrottlerModuleOptions,
    @Inject(getStorageToken()) storageService: ThrottlerStorage,
    reflector: Reflector,
  ) {
    super(options, storageService, reflector);
  }

  protected getThrottlerLimit(
    context: ExecutionContext,
    _throttlerName: string,
  ): number {
    // Check for custom config on handler
    const handlerConfig = this.reflector.get<ThrottleConfigType>(
      THROTTLE_KEY,
      context.getHandler(),
    );

    if (handlerConfig) {
      return handlerConfig.limit;
    }

    // Check for custom config on controller class
    const classConfig = this.reflector.get<ThrottleConfigType>(
      THROTTLE_KEY,
      context.getClass(),
    );

    if (classConfig) {
      return classConfig.limit;
    }

    // Default global limit
    return THROTTLE_CONFIGS.global.limit;
  }

  protected getThrottlerTtl(
    context: ExecutionContext,
    _throttlerName: string,
  ): number {
    // Check for custom config on handler
    const handlerConfig = this.reflector.get<ThrottleConfigType>(
      THROTTLE_KEY,
      context.getHandler(),
    );

    if (handlerConfig) {
      return handlerConfig.ttl;
    }

    // Check for custom config on controller class
    const classConfig = this.reflector.get<ThrottleConfigType>(
      THROTTLE_KEY,
      context.getClass(),
    );

    if (classConfig) {
      return classConfig.ttl;
    }

    // Default global TTL
    return THROTTLE_CONFIGS.global.ttl;
  }

  async canActivate(context: ExecutionContext): Promise<boolean> {
    try {
      return await super.canActivate(context);
    } catch (error) {
      if (error instanceof ThrottlerException) {
        // Add custom headers for rate limit info
        const response = context.switchToHttp().getResponse<Response>();
        const req = context.switchToHttp().getRequest<Request>();

        // Get current rate limit info
        const handlerConfig = this.reflector.get<ThrottleConfigType>(
          THROTTLE_KEY,
          context.getHandler(),
        );
        const config = handlerConfig ?? THROTTLE_CONFIGS.global;

        response.setHeader('X-RateLimit-Limit', String(config.limit));
        response.setHeader(
          'X-RateLimit-Reset',
          String(Date.now() + config.ttl),
        );
        response.setHeader('X-RateLimit-Remaining', '0');

        // Log rate limit hit
        this.logger.warn(
          `Rate limit exceeded for ${req.method} ${req.url} from IP: ${req.ip}`,
        );
      }
      throw error;
    }
  }
}
