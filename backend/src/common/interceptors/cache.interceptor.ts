import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
  SetMetadata,
  applyDecorators,
} from '@nestjs/common';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import type { Cache as CacheType } from 'cache-manager';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Inject } from '@nestjs/common';
import { Reflector } from '@nestjs/core';

export const CACHE_KEY_METADATA = 'cache:key';
export const CACHE_TTL_METADATA = 'cache:ttl';

@Injectable()
export class CacheInterceptor implements NestInterceptor {
  constructor(
    @Inject(CACHE_MANAGER) private cacheManager: CacheType,
    private reflector: Reflector,
  ) {}

  async intercept(
    context: ExecutionContext,
    next: CallHandler,
  ): Promise<Observable<unknown>> {
    const request = context.switchToHttp().getRequest();

    // Only cache GET requests
    if (request.method !== 'GET') {
      return next.handle();
    }

    // Get cache key from decorator or generate from request
    const cacheKey =
      this.reflector.get<string>(CACHE_KEY_METADATA, context.getHandler()) ||
      this.generateCacheKey(request);

    // Get TTL from decorator or use default
    const ttl =
      this.reflector.get<number>(CACHE_TTL_METADATA, context.getHandler()) ||
      60; // Default 60 seconds

    // Try to get from cache
    const cachedResponse = await this.cacheManager.get(cacheKey);
    if (cachedResponse) {
      return of(cachedResponse);
    }

    // If not in cache, execute handler and cache result
    return next.handle().pipe(
      tap(async (response) => {
        if (response) {
          await this.cacheManager.set(cacheKey, response, ttl * 1000);
        }
      }),
    );
  }

  private generateCacheKey(request: any): string {
    const url = request.url;
    const userId = request.user?.id || 'anonymous';
    const query = JSON.stringify(request.query || {});
    return `cache:${url}:${userId}:${query}`;
  }
}

// Decorator to set custom cache key
export function CacheKey(key: string): MethodDecorator {
  return SetMetadata(CACHE_KEY_METADATA, key);
}

// Decorator to set custom TTL
export function CacheTTL(ttl: number): MethodDecorator {
  return SetMetadata(CACHE_TTL_METADATA, ttl);
}

// Combined decorator for cache configuration
export function Cache(key: string, ttl = 60): MethodDecorator {
  return applyDecorators(CacheKey(key), CacheTTL(ttl));
}
