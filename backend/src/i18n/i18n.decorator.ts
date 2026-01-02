import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { Request } from 'express';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  SUPPORTED_LOCALES,
} from './i18n.constants';

/**
 * Parameter decorator to extract and parse the locale from Accept-Language header
 * @example
 * @Get()
 * async findAll(@Locale() locale: SupportedLocale) {
 *   return this.service.findAll(locale);
 * }
 */
export const Locale = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): SupportedLocale => {
    const request = ctx.switchToHttp().getRequest<Request>();
    const header = request.headers['accept-language'];

    if (!header) return DEFAULT_LOCALE;

    const languages = header.split(',').map((lang) => {
      const [code, qValue] = lang.trim().split(';q=');
      return { code: code.trim(), quality: qValue ? parseFloat(qValue) : 1.0 };
    });

    // Sort by quality (highest first)
    languages.sort((a, b) => b.quality - a.quality);

    for (const { code } of languages) {
      // Exact match
      if (SUPPORTED_LOCALES.includes(code as SupportedLocale)) {
        return code as SupportedLocale;
      }
      // Partial match (e.g., "zh" matches "zh-CN")
      const partial = SUPPORTED_LOCALES.find((l) =>
        l.toLowerCase().startsWith(code.toLowerCase().split('-')[0]),
      );
      if (partial) return partial;
    }

    return DEFAULT_LOCALE;
  },
);
