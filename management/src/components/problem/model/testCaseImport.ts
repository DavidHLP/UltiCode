import { mapCaseScopeToFlags, type CaseScope } from './testCaseScope'

/**
 * Canonical shape produced by the focused import-normalization module.
 *
 * <p>This is the single source of truth for the data structure the
 * {@code model/testCaseImport} module emits. It is the same shape the
 * management composable hands to {@code testCasesApi.bulkImportTestCases}
 * (see {@code api/admin/test-cases.ts::BulkImportTestCaseDto}) and the
 * same shape the backend accepts on {@code /admin/problems/{}/test-cases/bulk}.
 *
 * <p>Invariant: every emitted case carries both {@code isSample} and
 * {@code isHidden} explicitly so the wire payload always satisfies the
 * backend's {@code isSample XOR isHidden} guard.
 */
export interface NormalizedTestCaseImport {
  inputText: string
  outputText: string
  isSample: boolean
  isHidden: boolean
  explanation?: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * Resolve loose JSON flags to one safe author intent. Explicit HIDDEN wins;
 * missing or contradictory pairs default to HIDDEN so ambiguous imported data
 * can never become a public sample accidentally.
 */
function normalizeImportedScope(candidate: Record<string, unknown>): CaseScope {
  const isHidden = candidate.isHidden ?? candidate.is_hidden
  const isSample = candidate.isSample ?? candidate.is_sample
  if (isHidden === true) return 'HIDDEN'
  if (isSample === true) return 'SAMPLE'
  return 'HIDDEN'
}

function normalizeJsonCase(candidate: Record<string, unknown>): NormalizedTestCaseImport {
  return {
    inputText: String(candidate.inputText ?? candidate.input_text ?? candidate.input ?? ''),
    outputText: String(candidate.outputText ?? candidate.output_text ?? candidate.output ?? ''),
    ...mapCaseScopeToFlags(normalizeImportedScope(candidate)),
    explanation:
      candidate.explanation != null ? String(candidate.explanation) : undefined,
  }
}

/**
 * Normalize the two accepted free-form import grammars into canonical test
 * cases. JSON accepts current, legacy, and loose keys. The line grammar always
 * creates HIDDEN cases.
 */
export function normalizeTestCaseImport(text: string): NormalizedTestCaseImport[] {
  if (!text.trim()) return []

  try {
    const parsed: unknown = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed.filter(isRecord).map(normalizeJsonCase)
    }
  } catch {
    // Not JSON; fall through to the line grammar.
  }

  const lines = text.split('\n').filter((line) => line.trim())
  const result: NormalizedTestCaseImport[] = []
  let currentInput = ''
  let currentOutput = ''
  let isOutput = false

  const appendCurrent = () => {
    if (!currentInput || !currentOutput) return
    result.push({
      inputText: currentInput.trim(),
      outputText: currentOutput.trim(),
      ...mapCaseScopeToFlags('HIDDEN'),
    })
  }

  for (const line of lines) {
    if (line.startsWith('---') || line.startsWith('===')) {
      appendCurrent()
      currentInput = ''
      currentOutput = ''
      isOutput = false
      continue
    }
    if (line.toLowerCase().startsWith('output:') || line.startsWith('>')) {
      isOutput = true
      continue
    }
    if (line.toLowerCase().startsWith('input:') || line.startsWith('<')) {
      isOutput = false
      continue
    }
    if (isOutput) {
      currentOutput += (currentOutput ? '\n' : '') + line
    } else {
      currentInput += (currentInput ? '\n' : '') + line
    }
  }

  appendCurrent()
  return result
}
