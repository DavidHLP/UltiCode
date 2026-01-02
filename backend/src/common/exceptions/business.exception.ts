import { HttpException } from '@nestjs/common';
import { ErrorCode, getHttpStatus, getErrorCodeKey } from '../error-codes';

/**
 * Business logic exception with error code support
 *
 * Use this for domain-specific errors that should be handled by the frontend
 * with internationalized error messages.
 */
export class BusinessException extends HttpException {
  public readonly errorCode: ErrorCode;
  public readonly errorKey: string;

  constructor(errorCode: ErrorCode, message?: string) {
    const httpStatus = getHttpStatus(errorCode);
    const errorKey = getErrorCodeKey(errorCode);
    const responseMessage = message || errorKey;

    super(
      {
        code: errorCode,
        message: responseMessage,
        errorKey,
      },
      httpStatus,
    );

    this.errorCode = errorCode;
    this.errorKey = errorKey;
  }

  /**
   * Create a BusinessException from an error code
   */
  static fromCode(errorCode: ErrorCode): BusinessException {
    return new BusinessException(errorCode);
  }
}
