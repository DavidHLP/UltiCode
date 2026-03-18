export default {
  // Login
  login: {
    title: 'Login',
    subtitle: 'Admin Panel',
    username: 'Username',
    usernamePlaceholder: 'admin',
    password: 'Password',
    passwordPlaceholder: '••••••••',
    rememberMe: 'Remember me',
    forgotPassword: 'Forgot password?',
    submit: 'Login',
    loggingIn: 'Logging in...',
    invalidCredentials: 'Invalid username or password',
    accountDisabled: 'Account has been disabled',
    success: 'Login successful',
    error: 'Login failed',
  },

  // Logout
  logout: {
    title: 'Log out',
    confirm: 'Are you sure you want to log out?',
    success: 'Logged out successfully',
    error: 'Failed to log out',
  },

  // Password reset
  resetPassword: {
    title: 'Reset Password',
    email: 'Email',
    emailPlaceholder: 'admin@example.com',
    submit: 'Send Reset Link',
    sending: 'Sending...',
    success: 'Reset link sent to your email',
    error: 'Failed to send reset link',
    backToLogin: 'Back to Login',
    newPassword: 'New Password',
    confirmPassword: 'Confirm Password',
    passwordMismatch: 'Passwords do not match',
    passwordTooShort: 'Password must be at least 8 characters',
    passwordResetSuccess: 'Password reset successfully',
    passwordResetError: 'Failed to reset password',
  },

  // Session management
  session: {
    expired: 'Session expired, please log in again',
    invalid: 'Invalid session, please log in again',
  },

  // Permissions
  permissions: {
    denied: 'Permission denied',
    noAccess: 'You do not have permission to access this page',
    noAction: 'You do not have permission to perform this action',
  },
} as const
