import { describe, expect, it } from 'vitest'
import { normalizeTestCaseImport } from '../testCaseImport'

describe('Problem test-case import normalization', () => {
  it('normalizes camelCase, legacy snake_case, and loose JSON keys', () => {
    const result = normalizeTestCaseImport(
      JSON.stringify([
        { inputText: '1 2', outputText: '3', isSample: true, isHidden: false },
        { input: '4', output: '5', is_sample: false, is_hidden: true },
      ]),
    )

    expect(result).toEqual([
      {
        inputText: '1 2',
        outputText: '3',
        isSample: true,
        isHidden: false,
        explanation: undefined,
      },
      {
        inputText: '4',
        outputText: '5',
        isSample: false,
        isHidden: true,
        explanation: undefined,
      },
    ])
  })

  it.each([
    [{ isSample: true, isHidden: true }, 'both true'],
    [{ isSample: false, isHidden: false }, 'both false'],
    [{}, 'missing'],
  ])('canonicalizes ambiguous %s flags to HIDDEN', (flags) => {
    const [result] = normalizeTestCaseImport(
      JSON.stringify([{ inputText: 'x', outputText: 'y', ...flags }]),
    )
    expect(result).toMatchObject({ isSample: false, isHidden: true })
  })

  it('ignores non-object JSON array entries without throwing', () => {
    expect(
      normalizeTestCaseImport(JSON.stringify([null, 1, 'bad', { input: 'x', output: 'y' }])),
    ).toHaveLength(1)
  })

  it('parses Input/Output and marker line grammars as HIDDEN cases', () => {
    const inputOutput = ['Input:', '1 2', 'Output:', '3'].join('\n')
    const markers = ['<', 'a', '>', 'b', '===', '<', 'c', '>', 'd'].join('\n')

    expect(normalizeTestCaseImport(inputOutput)).toEqual([
      { inputText: '1 2', outputText: '3', isSample: false, isHidden: true },
    ])
    expect(normalizeTestCaseImport(markers)).toEqual([
      { inputText: 'a', outputText: 'b', isSample: false, isHidden: true },
      { inputText: 'c', outputText: 'd', isSample: false, isHidden: true },
    ])
  })

  it('returns no cases for blank or incomplete input', () => {
    expect(normalizeTestCaseImport('   ')).toEqual([])
    expect(normalizeTestCaseImport('Input:\n1 2\n---\nInput:\n3 4')).toEqual([])
  })
})
