/**
 * Judge Container Executor Service
 *
 * This HTTP server runs inside an isolated Docker container and executes
 * user-submitted JavaScript/TypeScript code safely.
 *
 * Security features:
 * - No network access (container runs with network_mode: none)
 * - Read-only root filesystem
 * - Non-root user (UID 1001)
 * - Seccomp profile restricting dangerous syscalls
 * - Memory and CPU limits via cgroups
 */

import express, { Request, Response } from 'express';
import { performance } from 'perf_hooks';
import * as vm from 'vm';
import ts from 'typescript';

const app = express();
const PORT = process.env.PORT || 3000;

// Request/Response types
interface ExecuteRequest {
  code: string;
  language: string;
  testCases: Array<{
    id: string;
    inputs?: Array<{ name: string; value: string }>;
    output?: string;
  }>;
  timeLimit?: number;
  memoryLimit?: number;
}

interface ExecuteResponse {
  success: boolean;
  result?: {
    verdict: string;
    runtime: number;
    memory: number;
    cases: Array<{
      status: string;
      time: number;
      memory: number;
      output?: string;
      expectedOutput?: string;
      detail?: string;
      inputs?: Array<{ name: string; value: string }>;
    }>;
    compileError?: string;
  };
  error?: string;
}

type JudgeStatus =
  | 'Accepted'
  | 'Wrong Answer'
  | 'Time Limit Exceeded'
  | 'Memory Limit Exceeded'
  | 'Output Limit Exceeded'
  | 'Runtime Error'
  | 'Compile Error'
  | 'Presentation Error'
  | 'System Error'
  | 'Judging'
  | 'Pending';

const TIME_LIMIT_MS = 2000;
const FLOAT_TOLERANCE = 1e-6;

// Middleware
app.use(express.json({ limit: '1mb' }));

// Health check endpoint
app.get('/health', (_req: Request, res: Response): void => {
  res.json({ status: 'healthy', timestamp: Date.now() });
});

// Main execution endpoint
app.post('/execute', async (req: Request, res: Response): Promise<void> => {
  try {
    const { code, language, testCases, timeLimit = TIME_LIMIT_MS } = req.body as ExecuteRequest;

    // Validate request
    if (!code || typeof code !== 'string') {
      res.status(400).json({
        success: false,
        error: 'Invalid or missing code'
      } as ExecuteResponse);
      return;
    }

    if (!testCases || !Array.isArray(testCases)) {
      res.status(400).json({
        success: false,
        error: 'Invalid or missing testCases'
      } as ExecuteResponse);
      return;
    }

    // Execute the code
    const result = executeCode(language, code, testCases, timeLimit);

    res.json({
      success: true,
      result
    } as ExecuteResponse);
    return;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    res.status(500).json({
      success: false,
      error: message
    } as ExecuteResponse);
  }
});

/**
 * Executes user code in VM context (still within Docker container isolation)
 * Note: The vm module here is safe because we're already inside a Docker container
 * with network disabled, read-only filesystem, and seccomp restrictions
 */
function executeCode(
  language: string,
  code: string,
  testCases: Array<{ id: string; inputs?: Array<{ name: string; value: string }>; output?: string }>,
  timeLimit: number
): ExecuteResponse['result'] {
  const normalizedLanguage = normalizeLanguage(language);
  if (!normalizedLanguage) {
    return {
      verdict: 'Compile Error',
      runtime: 0,
      memory: 0,
      cases: [],
      compileError: `Language ${language} is not supported.`
    };
  }

  const entryName = detectEntryFunctionName(code);
  if (!entryName) {
    return {
      verdict: 'Compile Error',
      runtime: 0,
      memory: 0,
      cases: [],
      compileError: 'Unable to detect the entry function name.'
    };
  }

  let compiledCode = code;
  if (normalizedLanguage === 'typescript') {
    try {
      compiledCode = ts.transpileModule(code, {
        compilerOptions: {
          target: ts.ScriptTarget.ES2020,
          module: ts.ModuleKind.CommonJS,
          strict: false
        }
      }).outputText;
    } catch (error) {
      return {
        verdict: 'Compile Error',
        runtime: 0,
        memory: 0,
        cases: [],
        compileError: error instanceof Error ? error.message : String(error)
      };
    }
  }

  // Create isolated VM context
  const context = vm.createContext({
    console: {
      log: () => undefined,
      error: () => undefined,
      warn: () => undefined
    }
  });
  context.globalThis = context;

  const instrumentedCode = `${compiledCode}\n;globalThis.__entry = typeof ${entryName} !== 'undefined' ? ${entryName} : undefined;`;

  try {
    const setupScript = new vm.Script(instrumentedCode, {
      filename: 'submission.js'
    });
    setupScript.runInContext(context, { timeout: timeLimit });
  } catch (error) {
    return {
      verdict: 'Compile Error',
      runtime: 0,
      memory: 0,
      cases: [],
      compileError: error instanceof Error ? error.message : String(error)
    };
  }

  if (typeof context.__entry !== 'function') {
    return {
      verdict: 'Compile Error',
      runtime: 0,
      memory: 0,
      cases: [],
      compileError: `Entry function "${entryName}" was not found.`
    };
  }

  const invokeScript = new vm.Script('__entry(...__args)', {
    filename: 'invoke.js'
  });

  const results: Array<{
    status: JudgeStatus;
    time: number;
    memory: number;
    output?: string;
    expectedOutput?: string;
    detail?: string;
    inputs?: Array<{ name: string; value: string }>;
  }> = [];
  let verdict: JudgeStatus = 'Accepted';
  let totalRuntime = 0;
  let maxMemory = 0;

  for (let index = 0; index < testCases.length; index++) {
    const testCase = testCases[index];
    const inputs = testCase.inputs ?? [];
    const args = inputs.map((input) => parseValue(input.value));

    context.__args = args;
    const start = performance.now();

    try {
      const outputValue = invokeScript.runInContext(context, {
        timeout: timeLimit
      }) as unknown;
      const elapsed = Math.max(1, Math.round(performance.now() - start));
      const memory = currentMemoryMb();
      const expectedOutput = testCase.output ?? '';
      const expectedValue = parseValue(expectedOutput);
      const outputText = formatValue(outputValue);

      const isAccepted = expectedOutput === '' ? true : deepEqual(outputValue, expectedValue);
      const status: JudgeStatus = isAccepted ? 'Accepted' : 'Wrong Answer';

      results.push({
        status,
        time: elapsed,
        memory,
        output: outputText,
        expectedOutput,
        inputs
      });

      totalRuntime += elapsed;
      if (memory > maxMemory) {
        maxMemory = memory;
      }

      if (!isAccepted) {
        verdict = status;
        markPendingCases(results, testCases, index + 1);
        break;
      }
    } catch (error) {
      const elapsed = Math.max(1, Math.round(performance.now() - start));
      const memory = currentMemoryMb();
      const message = error instanceof Error ? error.message : String(error);
      const status: JudgeStatus = message.includes('Script execution timed out')
        ? 'Time Limit Exceeded'
        : 'Runtime Error';

      results.push({
        status,
        time: elapsed,
        memory,
        output: '',
        expectedOutput: testCase.output ?? '',
        detail: message,
        inputs
      });

      totalRuntime += elapsed;
      if (memory > maxMemory) {
        maxMemory = memory;
      }
      verdict = status;
      markPendingCases(results, testCases, index + 1);
      break;
    }
  }

  return {
    verdict,
    runtime: totalRuntime,
    memory: maxMemory,
    cases: results
  };
}

// Helper functions
function normalizeLanguage(language: string): string | null {
  const normalized = (language || '').toLowerCase();
  if (normalized === 'javascript' || normalized === 'js') {
    return 'javascript';
  }
  if (normalized === 'typescript' || normalized === 'ts') {
    return 'typescript';
  }
  return null;
}

function detectEntryFunctionName(code: string): string | null {
  const patterns = [
    /(?:export\s+default\s+|export\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*function\s*\(/,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(?[\w\s,]*\)?\s*=>/
  ];

  for (const pattern of patterns) {
    const match = code.match(pattern);
    if (match?.[1]) {
      return match[1];
    }
  }
  return null;
}

function parseValue(rawValue: string): unknown {
  const trimmed = (rawValue ?? '').trim();
  if (!trimmed) {
    return '';
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return trimmed;
  }
}

function formatValue(value: unknown): string {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return Object.prototype.toString.call(value) as string;
  }
}

function deepEqual(a: unknown, b: unknown): boolean {
  if (typeof a === 'number' && typeof b === 'number') {
    return Math.abs(a - b) <= FLOAT_TOLERANCE;
  }
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (!deepEqual(a[i], b[i])) return false;
    }
    return true;
  }
  if (
    a &&
    b &&
    typeof a === 'object' &&
    typeof b === 'object' &&
    !Array.isArray(a) &&
    !Array.isArray(b)
  ) {
    const keysA = Object.keys(a as Record<string, unknown>);
    const keysB = Object.keys(b as Record<string, unknown>);
    if (keysA.length !== keysB.length) return false;
    for (const key of keysA) {
      if (!deepEqual((a as Record<string, unknown>)[key], (b as Record<string, unknown>)[key])) {
        return false;
      }
    }
    return true;
  }
  return a === b;
}

function currentMemoryMb(): number {
  return Math.round((process.memoryUsage().heapUsed / 1024 / 1024) * 10) / 10;
}

function markPendingCases(
  results: Array<{
    status: JudgeStatus;
    time: number;
    memory: number;
    output?: string;
    expectedOutput?: string;
    inputs?: Array<{ name: string; value: string }>;
  }>,
  testCases: Array<{ id: string; inputs?: Array<{ name: string; value: string }>; output?: string }>,
  startIndex: number
) {
  for (let index = startIndex; index < testCases.length; index++) {
    const pendingCase = testCases[index];
    results.push({
      status: 'Pending',
      time: 0,
      memory: 0,
      output: '',
      expectedOutput: pendingCase.output ?? '',
      inputs: pendingCase.inputs ?? []
    });
  }
}

// Start server
const port = typeof PORT === 'number' ? PORT : parseInt(PORT, 10);
app.listen(port, '0.0.0.0', () => {
  console.log(`Judge container executor listening on port ${port}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully...');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully...');
  process.exit(0);
});
