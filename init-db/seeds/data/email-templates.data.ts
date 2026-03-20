import type { Prisma } from '@prisma/client';

export const emailTemplatesData: Prisma.EmailTemplateCreateInput[] = [
  {
    name: 'welcome',
    subject: 'Welcome to UltiCode, {{username}}!',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Welcome to UltiCode!</h1>
      <p>Hello {{username}},</p>
      <p>Thank you for joining UltiCode, the ultimate platform for competitive programming practice.</p>
      <p>Here's what you can do to get started:</p>
      <ul>
        <li>Browse our collection of algorithm problems</li>
        <li>Practice coding in multiple languages</li>
        <li>Join contests and compete with others</li>
        <li>Track your progress and achievements</li>
      </ul>
      <p>Happy coding!</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username'],
  },
  {
    name: 'password-reset',
    subject: 'Reset Your UltiCode Password',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Password Reset Request</h1>
      <p>Hello {{username}},</p>
      <p>We received a request to reset your password. Click the button below to create a new password:</p>
      <p style="text-align: center; margin: 30px 0;">
        <a href="{{resetUrl}}" style="background-color: #2563eb; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px;">Reset Password</a>
      </p>
      <p>This link will expire in {{expirationHours}} hours.</p>
      <p>If you didn't request this password reset, you can safely ignore this email.</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'resetUrl', 'expirationHours'],
  },
  {
    name: 'email-verification',
    subject: 'Verify Your UltiCode Email',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Verify Your Email Address</h1>
      <p>Hello {{username}},</p>
      <p>Please verify your email address by clicking the button below:</p>
      <p style="text-align: center; margin: 30px 0;">
        <a href="{{verificationUrl}}" style="background-color: #2563eb; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px;">Verify Email</a>
      </p>
      <p>This link will expire in {{expirationHours}} hours.</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'verificationUrl', 'expirationHours'],
  },
  {
    name: 'subscription-confirmation',
    subject: 'Your UltiCode Premium Subscription',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Subscription Confirmed!</h1>
      <p>Hello {{username}},</p>
      <p>Thank you for subscribing to UltiCode {{planName}}!</p>
      <p><strong>Subscription Details:</strong></p>
      <ul>
        <li>Plan: {{planName}}</li>
        <li>Amount: {{amount}} {{currency}}</li>
        <li>Next billing date: {{nextBillingDate}}</li>
      </ul>
      <p>Enjoy your premium features:</p>
      <ul>
        <li>Unlimited problem submissions</li>
        <li>Access to all problem sets</li>
        <li>Priority support</li>
      </ul>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'planName', 'amount', 'currency', 'nextBillingDate'],
  },
  {
    name: 'subscription-cancelled',
    subject: 'UltiCode Subscription Cancelled',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #dc2626;">Subscription Cancelled</h1>
      <p>Hello {{username}},</p>
      <p>Your UltiCode {{planName}} subscription has been cancelled.</p>
      <p>You will continue to have access to premium features until {{endDate}}.</p>
      <p>We hope to see you again soon!</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'planName', 'endDate'],
  },
  {
    name: 'contest-reminder',
    subject: '{{contestName}} starts in {{timeRemaining}}!',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Contest Reminder</h1>
      <p>Hello {{username}},</p>
      <p>The contest <strong>{{contestName}}</strong> is starting soon!</p>
      <p><strong>Start Time:</strong> {{startTime}}</p>
      <p><strong>Duration:</strong> {{duration}}</p>
      <p style="text-align: center; margin: 30px 0;">
        <a href="{{contestUrl}}" style="background-color: #2563eb; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px;">View Contest</a>
      </p>
      <p>Good luck!</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'contestName', 'timeRemaining', 'startTime', 'duration', 'contestUrl'],
  },
  {
    name: 'achievement-unlocked',
    subject: 'Achievement Unlocked: {{achievementName}}!',
    body: `<div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
      <h1 style="color: #2563eb;">Achievement Unlocked!</h1>
      <p>Congratulations, {{username}}!</p>
      <p>You've earned a new achievement:</p>
      <div style="background-color: #f3f4f6; padding: 20px; border-radius: 8px; text-align: center; margin: 20px 0;">
        <h2 style="margin: 0;">{{achievementName}}</h2>
        <p style="color: #6b7280;">{{achievementDescription}}</p>
      </div>
      <p>Keep up the great work!</p>
      <p>The UltiCode Team</p>
    </div>`,
    variables: ['username', 'achievementName', 'achievementDescription'],
  },
];
