export default {
  // Login page
  login: {
    title: "Login to your account",
    subtitle: "Enter your username and password to login",
    username: "Username",
    usernamePlaceholder: "Enter your username",
    password: "Password",
    passwordPlaceholder: "Enter your password",
    forgotPassword: "Forgot password?",
    submit: "Login",
    submitting: "Logging in...",
    orContinueWith: "Or continue with",
    loginWithGithub: "Login with GitHub",
    noAccount: "Don't have an account?",
    signUp: "Sign up",
    rememberMe: "Remember me",
  },

  // Register page
  register: {
    title: "Create an account",
    subtitle: "Enter your information to create a new account",
    username: "Username",
    usernamePlaceholder: "Enter a username",
    email: "Email",
    emailPlaceholder: "Enter your email address",
    password: "Password",
    passwordPlaceholder: "Enter a password",
    confirmPassword: "Confirm Password",
    confirmPasswordPlaceholder: "Re-enter your password",
    submit: "Sign up",
    submitting: "Creating account...",
    hasAccount: "Already have an account?",
    login: "Login",
    termsAgreement: "By signing up, you agree to our",
    termsOfService: "Terms of Service",
    and: "and",
    privacyPolicy: "Privacy Policy",
  },

  // Forgot password page
  forgotPassword: {
    title: "Forgot Password",
    subtitle: "Enter your email address and we'll send you a reset link",
    email: "Email",
    emailPlaceholder: "Enter your registered email",
    submit: "Send Reset Link",
    submitting: "Sending...",
    backToLogin: "Back to login",
    rememberPassword: "Remember your password?",
    successMessage:
      "If the email exists, a reset link has been sent to your inbox",
  },

  // Reset password page
  resetPassword: {
    title: "Reset Password",
    subtitle: "Please enter your new password",
    newPassword: "New Password",
    newPasswordPlaceholder: "Enter new password",
    confirmPassword: "Confirm New Password",
    confirmPasswordPlaceholder: "Re-enter new password",
    submit: "Reset Password",
    submitting: "Resetting...",
    successMessage:
      "Password reset successful. Please login with your new password.",
  },

  // Toast messages
  messages: {
    loginSuccess: "Login successful",
    loginFailed: "Login failed",
    registerSuccess: "Account created successfully",
    registerFailed: "Registration failed",
    logoutSuccess: "Logged out successfully",
    passwordResetSuccess: "Password reset successful",
    passwordResetFailed: "Password reset failed",
    emailSent: "Email sent",
    requestFailed: "Failed to process request",
    invalidCredentials: "Invalid username or password",
    accountNotFound: "Account not found",
    emailAlreadyExists: "Email already in use",
    usernameAlreadyExists: "Username already taken",
    sessionExpired: "Session expired. Please login again.",
  },

  // Validation messages
  validation: {
    usernameRequired: "Username is required",
    usernameMinLength: "Username must be at least 3 characters",
    usernameMaxLength: "Username cannot exceed 20 characters",
    usernameInvalid:
      "Username can only contain letters, numbers, and underscores",
    emailRequired: "Email is required",
    emailInvalid: "Please enter a valid email address",
    passwordRequired: "Password is required",
    passwordMinLength: "Password must be at least 6 characters",
    passwordMaxLength: "Password cannot exceed 50 characters",
    passwordMismatch: "Passwords do not match",
  },
} as const;
