import * as dotenv from 'dotenv';
import * as path from 'path';

// Load environment variables from .env file for E2E tests
const envPath = path.resolve(__dirname, '../.env');
dotenv.config({ path: envPath });

// Set test timeout
jest.setTimeout(30000);

// Mock jsdom and dompurify for E2E tests
jest.mock('jsdom', () => ({
  JSDOM: class MockJSDOM {
    window = {
      document: {
        createElement: () => ({ innerHTML: '', setAttribute: () => {} }),
        createTextNode: (text: string) => ({ textContent: text }),
      },
    };
    constructor() {}
  },
}));

jest.mock('dompurify', () => {
  const mockPurify = {
    sanitize: (input: string) => input,
    setConfig: () => {},
    addHook: () => {},
  };
  // Return a function that returns the mock purifier (DOMPurify is called with window)
  return mockPurify;
});
