/**
 * Utility for consistent BigInt handling across the application
 * BigInt values should always be serialized as strings to avoid precision loss
 */
export class BigIntUtil {
  /**
   * Convert BigInt ID to string for API responses
   */
  static toString(value: bigint | string | number): string {
    if (typeof value === 'bigint') {
      return value.toString();
    }
    if (typeof value === 'number') {
      return String(value);
    }
    return value;
  }

  /**
   * Parse input to BigInt for database queries
   */
  static toBigInt(value: string | number | bigint): bigint {
    if (typeof value === 'bigint') {
      return value;
    }
    return BigInt(value);
  }

  /**
   * Convert array of BigInt IDs to strings
   */
  static toStringArray(values: (bigint | string | number)[]): string[] {
    return values.map((v) => this.toString(v));
  }

  /**
   * Convert array of inputs to BigInt array
   */
  static toBigIntArray(values: (string | number | bigint)[]): bigint[] {
    return values.map((v) => this.toBigInt(v));
  }
}
