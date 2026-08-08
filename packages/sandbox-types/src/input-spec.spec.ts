import { describe, expect, it } from 'vitest';
import { isDFormInputSpec } from './input-spec.js';

describe('isDFormInputSpec', () => {
  it('accepts a minimal spec (name + value only)', () => {
    expect(isDFormInputSpec({ name: 'n', value: '1' })).toBe(true);
  });

  it('accepts a spec with a supported OJ type', () => {
    expect(isDFormInputSpec({ name: 'head', value: '[1,2,3]', type: 'ListNode' })).toBe(true);
  });

  it.each([
    ['non-object'],
    [null],
    [42],
    ['a string'],
    [undefined],
  ])('rejects non-object input: %s', (bad) => {
    expect(isDFormInputSpec(bad)).toBe(false);
  });

  it('rejects a spec missing name', () => {
    expect(isDFormInputSpec({ value: '1' })).toBe(false);
  });

  it('rejects a spec with a non-string name', () => {
    expect(isDFormInputSpec({ name: 42, value: '1' })).toBe(false);
  });

  it('rejects a spec with a blank name', () => {
    expect(isDFormInputSpec({ name: '', value: '1' })).toBe(false);
    expect(isDFormInputSpec({ name: '   ', value: '1' })).toBe(false);
  });

  it('rejects a spec missing value', () => {
    expect(isDFormInputSpec({ name: 'n' })).toBe(false);
  });

  it('rejects a spec with a non-string value', () => {
    expect(isDFormInputSpec({ name: 'n', value: 42 })).toBe(false);
  });

  it.each([
    ['NotARealType'],
    ['listnode'],   // wrong case
    [''],
    ['ListNode '],  // trailing space
  ])('rejects an unsupported type: %s', (badType) => {
    expect(isDFormInputSpec({ name: 'n', value: '1', type: badType })).toBe(false);
  });
});
