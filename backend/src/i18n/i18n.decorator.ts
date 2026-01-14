import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { Request } from 'express';
import {
  SupportedLocale,
  LOCALE_HEADER_KEY,
  matchSupportedLocale,
  parseAcceptLanguageHeaderWithMatch,
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
    return parseAcceptLanguageHeaderWithMatch(header);
  },
);
