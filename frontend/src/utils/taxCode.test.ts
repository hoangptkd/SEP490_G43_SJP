import { describe, expect, it } from 'vitest';
import { isVietnamTaxCodeFormat, normalizeVietnamTaxCode } from './taxCode';

describe('Vietnam tax-code helpers', () => {
  it('accepts a 10-digit company tax code', () => {
    expect(isVietnamTaxCodeFormat('0316794479')).toBe(true);
  });

  it('accepts and normalizes a 13-digit branch tax code', () => {
    expect(normalizeVietnamTaxCode('0316794479-001')).toBe('0316794479001');
    expect(isVietnamTaxCodeFormat('0316794479-001')).toBe(true);
  });

  it('rejects letters and unsupported lengths', () => {
    expect(isVietnamTaxCodeFormat('ABC-123')).toBe(false);
    expect(isVietnamTaxCodeFormat('12345678901')).toBe(false);
  });
});
