import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { Request } from 'express';
import {
  SupportedLocale,
  DEFAULT_LOCALE,
  LOCALE_HEADER_KEY,
  matchSupportedLocale,
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
    const preferredHeader = request.headers[LOCALE_HEADER_KEY];
    const preferredLocale = Array.isArray(preferredHeader)
      ? preferredHeader[0]
      : preferredHeader;

    const matchedPreferred = matchSupportedLocale(preferredLocale);
    if (matchedPreferred) return matchedPreferred;

    const header = request.headers['accept-language'];

    if (!header) return DEFAULT_LOCALE;

    const languages = header.split(',').map((lang) => {
      const [code, qValue] = lang.trim().split(';q=');
      return { code: code.trim(), quality: qValue ? parseFloat(qValue) : 1.0 };
    });

    // Sort by quality (highest first)
    languages.sort((a, b) => b.quality - a.quality);

    for (const { code } of languages) {
      const matched = matchSupportedLocale(code);
      if (matched) return matched;
    }

    return DEFAULT_LOCALE;
  },
);
