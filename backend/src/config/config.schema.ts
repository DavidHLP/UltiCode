/**
 * Configuration validation schema using Joi-style validation
 * This validates all environment variables at startup
 */

interface SchemaProperty {
  type: 'string' | 'number' | 'boolean';
  description: string;
  required?: boolean;
  default?: string | number | boolean;
  validator?: (value: string) => boolean | void;
}

interface ConfigSchema {
  [key: string]: SchemaProperty;
}

export const configValidationSchema: ConfigSchema = {
  // Database
  DATABASE_URL: {
    type: 'string',
    description: 'Database connection URL',
    required: true,
  },

  // Redis
  REDIS_HOST: {
    type: 'string',
    description: 'Redis host',
    default: 'localhost',
  },
  REDIS_PORT: {
    type: 'number',
    description: 'Redis port',
    default: 6379,
  },
  REDIS_PASSWORD: {
    type: 'string',
    description: 'Redis password',
    default: '',
  },

  // JWT
  JWT_SECRET: {
    type: 'string',
    description: 'JWT secret key (must be at least 32 characters)',
    required: true,
    validator: (value: string) => {
      if (value.length < 32) {
        throw new Error('JWT_SECRET must be at least 32 characters long');
      }
      return true;
    },
  },
  JWT_ACCESS_EXPIRY: {
    type: 'string',
    description: 'JWT access token expiry duration',
    default: '15m',
  },

  // OAuth (GitHub)
  GITHUB_CLIENT_ID: {
    type: 'string',
    description: 'GitHub OAuth client ID',
    default: 'mock_client_id',
  },
  GITHUB_REDIRECT_URI: {
    type: 'string',
    description: 'GitHub OAuth redirect URI',
    default: 'http://localhost:4001/auth/github/callback',
  },
  FRONTEND_URL: {
    type: 'string',
    description: 'Frontend application URL',
    default: 'http://localhost:5173',
  },

  // Cookies
  COOKIE_SECURE: {
    type: 'boolean',
    description: 'Cookie secure flag',
    default: false,
  },
  COOKIE_SAME_SITE: {
    type: 'string',
    description: 'Cookie same-site policy',
    default: 'lax',
    validator: (value: string) => {
      if (!['strict', 'lax', 'none'].includes(value)) {
        throw new Error('COOKIE_SAME_SITE must be one of: strict, lax, none');
      }
      return true;
    },
  },
  COOKIE_DOMAIN: {
    type: 'string',
    description: 'Cookie domain',
  },

  // Server
  PORT: {
    type: 'number',
    description: 'Server port',
    default: 9001,
  },

  // Build info
  npm_package_version: {
    type: 'string',
    description: 'Application version from package.json',
    default: '1.0.0',
  },
};

/**
 * Validates environment variables against the schema
 * Throws an error if validation fails
 */
export function validateConfig(config: Record<string, unknown>): void {
  const errors: string[] = [];

  for (const [key, schema] of Object.entries(configValidationSchema)) {
    const value = config[key];

    // Check required fields
    if (schema.required && (value === undefined || value === null)) {
      errors.push(`${key} is required`);
      continue;
    }

    // Skip validation if not required and not provided
    if (value === undefined || value === null) {
      if (schema.default !== undefined) {
        config[key] = schema.default;
      }
      continue;
    }

    // Type validation
    switch (schema.type) {
      case 'string':
        if (typeof value !== 'string') {
          errors.push(`${key} must be a string`);
        }
        break;
      case 'number':
        if (typeof value !== 'number') {
          let parsed: number;
          if (typeof value === 'string') {
            parsed = parseInt(value, 10);
          } else if (typeof value === 'boolean') {
            parsed = value ? 1 : 0;
          } else {
            errors.push(`${key} must be a number`);
            break;
          }
          if (isNaN(parsed)) {
            errors.push(`${key} must be a number`);
          } else {
            config[key] = parsed;
          }
        }
        break;
      case 'boolean':
        if (typeof value !== 'boolean') {
          if (value === 'true' || value === '1') {
            config[key] = true;
          } else if (value === 'false' || value === '0') {
            config[key] = false;
          } else {
            errors.push(`${key} must be a boolean`);
          }
        }
        break;
    }

    // Custom validator
    if (schema.validator && typeof schema.validator === 'function') {
      try {
        schema.validator(value as string);
      } catch (err) {
        errors.push(
          `${key}: ${err instanceof Error ? err.message : 'validation failed'}`,
        );
      }
    }

    // Apply default if value is empty string
    if (value === '' && schema.default !== undefined) {
      config[key] = schema.default;
    }
  }

  // Apply defaults for missing optional values
  for (const [key, schema] of Object.entries(configValidationSchema)) {
    if (config[key] === undefined && schema.default !== undefined) {
      config[key] = schema.default;
    }
  }

  if (errors.length > 0) {
    throw new Error(`Configuration validation failed:\n${errors.join('\n')}`);
  }
}
