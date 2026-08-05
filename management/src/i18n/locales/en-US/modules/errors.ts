export default {
  // Permission errors
  permission: {
    forbiddenPage: 'You do not have permission to access this page',
    forbiddenRole: 'You do not have the required role to access this page',
  },

  // General errors
  general: {
    unknown: 'An unknown error occurred',
    network: 'Network error, please check your connection',
    timeout: 'Request timed out, please try again',
    server: 'Server error, please try again later',
    notFound: 'The requested resource was not found',
    validation: 'Data validation failed',
    unauthorized: 'Unauthorized access',
    forbidden: 'Access forbidden',
  },

  // HTTP status codes
  http: {
    400: 'Bad request',
    401: 'Unauthorized, please log in',
    403: 'Forbidden',
    404: 'Resource not found',
    405: 'Method not allowed',
    408: 'Request timeout',
    409: 'Resource conflict',
    422: 'Validation failed',
    429: 'Too many requests, please try again later',
    500: 'Internal server error',
    502: 'Bad gateway',
    503: 'Service temporarily unavailable',
    504: 'Gateway timeout',
  },

  // Form validation
  validation: {
    required: 'This field is required',
    email: 'Please enter a valid email address',
    url: 'Please enter a valid URL',
    minLength: 'Minimum {min} characters required',
    maxLength: 'Maximum {max} characters allowed',
    min: 'Minimum value is {min}',
    max: 'Maximum value is {max}',
    pattern: 'Invalid format',
    number: 'Please enter a number',
    integer: 'Please enter an integer',
    positiveNumber: 'Please enter a positive number',
    password: {
      minLength: 'Password must be at least 8 characters',
      uppercase: 'Password must contain an uppercase letter',
      lowercase: 'Password must contain a lowercase letter',
      number: 'Password must contain a number',
      special: 'Password must contain a special character',
      match: 'Passwords do not match',
    },
    username: {
      minLength: 'Username must be at least 3 characters',
      maxLength: 'Username cannot exceed 20 characters',
      pattern: 'Username can only contain letters, numbers, and underscores',
      taken: 'Username is already taken',
    },
    emailField: {
      invalid: 'Please enter a valid email address',
      taken: 'Email is already registered',
    },
  },

  // Data loading
  loading: {
    failed: 'Failed to load data',
    retry: 'Retry',
    retrying: 'Retrying...',
    noMore: 'No more data',
    refreshing: 'Refreshing...',
  },

  // File upload
  upload: {
    failed: 'File upload failed',
    tooLarge: 'File size exceeds limit',
    invalidType: 'File type not supported',
    invalidExtension: 'File extension not supported',
    emptyFile: 'File cannot be empty',
    multipleFailed: '{count} file(s) failed to upload',
  },

  // Business errors
  business: {
    userNotFound: 'User not found',
    problemNotFound: 'Problem not found',
    contestNotFound: 'Contest not found',
    submissionNotFound: 'Submission not found',
    duplicateEntry: 'Entry already exists',
    operationFailed: 'Operation failed',
    invalidOperation: 'Invalid operation',
    dependencyError: 'Dependency exists, cannot perform this operation',
  },

  // Error types (used by error.ts getErrorContext)
  errorType: {
    validation: {
      title: 'Validation Error',
      default: 'Please check your input data',
      suggestion: 'Please verify the format and range of your input',
    },
    unauthorized: {
      title: 'Unauthorized',
      message: 'Please log in to continue',
      suggestion: 'Please check your login status or log in again',
    },
    forbidden: {
      title: 'Access Denied',
      message: 'You do not have permission to perform this action',
      suggestion: 'Please contact an administrator for the required permissions',
    },
    notFound: {
      title: 'Resource Not Found',
      message: 'The requested resource does not exist',
      suggestion: 'Please verify the requested resource exists',
    },
    serverError: {
      title: 'Server Error',
      message: 'Internal server error, please try again later',
      suggestion: 'Please try again later. Contact support if the problem persists',
    },
    network: {
      title: 'Network Error',
      suggestion: 'Please check your network connection and try again',
    },
  },

  apiErrorCanceled: 'Request canceled',
} as const
