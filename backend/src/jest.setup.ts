import * as dotenv from 'dotenv';
import * as path from 'path';

// Load environment variables from .env file for tests
// __dirname is the directory of this file (backend/src/jest.setup.ts)
// .env is in backend/.env
const envPath = path.resolve(__dirname, '../.env');

// Suppress dotenv console output during tests
const originalLog = console.log;
console.log = () => {};
try {
  dotenv.config({ path: envPath });
} finally {
  console.log = originalLog;
}
