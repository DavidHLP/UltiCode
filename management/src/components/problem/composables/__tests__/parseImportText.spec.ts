import { describe, expect, it } from 'vitest'
import { parseImportText } from '../useTestCases'

/**
 * Focused unit tests for the test-case import parser. The two import
 * grammars (JSON array + line-oriented) used to live inline in
 * `importTestCases`, where they could not be exercised directly. They are
 * now a pure function so the rules — and the `isSample XOR isHidden`
 * invariant every emitted DTO must satisfy — are testable independent of
 * the dialog / HTTP / toast state.
 */
describe('parseImportText', () => {
  describe('JSON grammar', () => {
    it('parses a camelCase export array', () => {
      const result = parseImportText(
        JSON.stringify([
          { inputText: '1 2', outputText: '3', isSample: true, isHidden: false },
          { inputText: '4 5', outputText: '9', isSample: false, isHidden: true },
        ]),
      )
      expect(result).toEqual([
        { inputText: '1 2', outputText: '3', isSample: true, isHidden: false, explanation: undefined },
        { inputText: '4 5', outputText: '9', isSample: false, isHidden: true, explanation: undefined },
      ])
    })

    it('accepts legacy snake_case and loose {input, output} pastes', () => {
      const result = parseImportText(
        JSON.stringify([{ input: '7', output: '8', is_sample: true, is_hidden: false }]),
      )
      expect(result).toHaveLength(1)
      expect(result[0]).toMatchObject({ inputText: '7', outputText: '8', isSample: true, isHidden: false })
    })

    it('defaults a JSON case to HIDDEN and non-sample when flags are absent', () => {
      const result = parseImportText(JSON.stringify([{ inputText: 'x', outputText: 'y' }]))
      expect(result[0]).toMatchObject({ isSample: false, isHidden: true })
    })
  })

  describe('line grammar', () => {
    it('parses Input:/Output: blocks separated by ---', () => {
      const text = ['Input:', '1 2', 'Output:', '3', '---', 'Input:', '3 4', 'Output:', '7'].join('\n')
      const result = parseImportText(text)
      expect(result).toEqual([
        { inputText: '1 2', outputText: '3', isSample: false, isHidden: true },
        { inputText: '3 4', outputText: '7', isSample: false, isHidden: true },
      ])
    })

    it('parses </> marker blocks separated by ===', () => {
      const text = ['<', 'a', '>', 'b', '===', '<', 'c', '>', 'd'].join('\n')
      const result = parseImportText(text)
      expect(result).toEqual([
        { inputText: 'a', outputText: 'b', isSample: false, isHidden: true },
        { inputText: 'c', outputText: 'd', isSample: false, isHidden: true },
      ])
    })
  })

  describe('edge cases', () => {
    it('returns an empty array for blank text', () => {
      expect(parseImportText('   ')).toEqual([])
    })

    it('returns an empty array when no block yields both input and output', () => {
      expect(parseImportText('Input:\n1 2\n---\nInput:\n3 4')).toEqual([])
    })

    it('falls back to the line grammar when JSON is malformed', () => {
      const text = 'Input:\nx\nOutput:\ny'
      const result = parseImportText(text)
      expect(result).toEqual([
        { inputText: 'x', outputText: 'y', isSample: false, isHidden: true },
      ])
    })
  })
})
