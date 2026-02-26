#!/usr/bin/env node
/**
 * JavaScript/TypeScript code runner
 * Executes user code in a sandboxed environment
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const ts = require('typescript');

const TIME_LIMIT_MS = parseInt(process.env.TIME_LIMIT_MS || '5000', 10);
const INPUT_FILE = process.env.INPUT_FILE || '/sandbox/input/args.json';
const CODE_FILE = process.env.CODE_FILE || '/sandbox/code/solution.js';

function parseValue(rawValue) {
  const trimmed = (rawValue || '').trim();
  if (!trimmed) return '';
  try {
    return JSON.parse(trimmed);
  } catch {
    return trimmed;
  }
}

function formatValue(value) {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return Object.prototype.toString.call(value);
  }
}

function detectEntryFunctionName(code) {
  const patterns = [
    /(?:export\s+default\s+|export\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*function\s*\(/,
    /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(?[\w\s,]*\)?\s*=>/,
  ];

  for (const pattern of patterns) {
    const match = code.match(pattern);
    if (match?.[1]) {
      return match[1];
    }
  }
  return null;
}

function transpileTypeScript(code) {
  return ts.transpileModule(code, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
      strict: false,
    },
  }).outputText;
}

async function run() {
  try {
    // Read code
    let code = fs.readFileSync(CODE_FILE, 'utf8');

    // Detect if TypeScript
    const isTypeScript = CODE_FILE.endsWith('.ts');
    if (isTypeScript) {
      code = transpileTypeScript(code);
    }

    // Detect entry function
    const entryName = detectEntryFunctionName(code);
    if (!entryName) {
      console.error(JSON.stringify({
        status: 'Compile Error',
        error: 'Unable to detect the entry function name.',
        time: 0,
        memory: 0,
      }));
      process.exit(1);
    }

    // Read inputs
    let inputs = [];
    if (fs.existsSync(INPUT_FILE)) {
      const inputData = JSON.parse(fs.readFileSync(INPUT_FILE, 'utf8'));
      inputs = inputData.args || [];
    }

    // Create sandboxed context
    const context = vm.createContext({
      console: {
        log: () => undefined,
        error: () => undefined,
        warn: () => undefined,
      },
    });
    context.globalThis = context;

    // Instrument code to capture entry function
    const instrumentedCode = `${code}\n;globalThis.__entry = typeof ${entryName} !== 'undefined' ? ${entryName} : undefined;`;

    // Compile and run setup
    const setupScript = new vm.Script(instrumentedCode, {
      filename: 'submission.js',
    });

    try {
      setupScript.runInContext(context, { timeout: TIME_LIMIT_MS });
    } catch (error) {
      console.error(JSON.stringify({
        status: 'Compile Error',
        error: error.message,
        time: 0,
        memory: 0,
      }));
      process.exit(1);
    }

    if (typeof context.__entry !== 'function') {
      console.error(JSON.stringify({
        status: 'Compile Error',
        error: `Entry function "${entryName}" was not found.`,
        time: 0,
        memory: 0,
      }));
      process.exit(1);
    }

    // Execute with input
    const args = inputs.map(input => parseValue(input));
    const startTime = Date.now();

    const invokeScript = new vm.Script('__entry(...__args)', {
      filename: 'invoke.js',
    });
    context.__args = args;

    const outputValue = invokeScript.runInContext(context, {
      timeout: TIME_LIMIT_MS,
    });

    const elapsed = Date.now() - startTime;
    const memory = process.memoryUsage().heapUsed / 1024 / 1024;

    console.log(JSON.stringify({
      status: 'Success',
      output: formatValue(outputValue),
      time: elapsed,
      memory: Math.round(memory * 100) / 100,
    }));

  } catch (error) {
    const isTimeout = error.message && error.message.includes('Script execution timed out');
    console.error(JSON.stringify({
      status: isTimeout ? 'Time Limit Exceeded' : 'Runtime Error',
      error: error.message,
      time: 0,
      memory: process.memoryUsage().heapUsed / 1024 / 1024,
    }));
    process.exit(1);
  }
}

run();
