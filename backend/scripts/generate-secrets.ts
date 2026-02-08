import * as crypto from 'crypto';

function generateSecret(bytes = 32): string {
  return crypto.randomBytes(bytes).toString('base64');
}

function generatePassword(length = 24): string {
  const charset =
    'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?';
  const randomBytes = crypto.randomBytes(length);
  let password = '';
  for (let i = 0; i < length; i++) {
    password += charset[randomBytes[i] % charset.length];
  }
  return password;
}

console.log('=== Generated Secure Secrets ===\n');
console.log(`JWT_SECRET="${generateSecret(48)}"`);
console.log(`\n# Database Credentials`);
console.log(`DB_PASSWORD="${generatePassword(24)}"`);
console.log(`\n# Redis Credentials`);
console.log(`REDIS_PASSWORD="${generatePassword(24)}"`);
console.log(`\n# Docker Root Password`);
console.log(`MYSQL_ROOT_PASSWORD="${generatePassword(24)}"`);
