import { describe, expect, it, vi } from 'vitest';
import {
  classNames,
  formatCurrency,
  formatDate,
  truncate,
  validateEmail,
  validatePassword,
  openFileInNewTab,
} from './helpers';

describe('helperFunctions', () => {
  it('joins truthy class names', () => {
    expect(classNames('btn', undefined, false, 'primary', null, '')).toBe('btn primary');
  });

  it('returns empty string when every class is falsy', () => {
    expect(classNames(undefined, null, false, '')).toBe('');
  });

  it('formats an ISO date in en-US long form', () => {
    expect(formatDate('2026-08-16T00:00:00.000Z')).toContain('2026');
    expect(formatDate('2026-08-16T00:00:00.000Z')).toContain('August');
  });

  it('formats VND currency', () => {
    const formatted = formatCurrency(1500000);
    expect(formatted).toMatch(/1.?500.?000/);
    expect(formatted.toUpperCase()).toContain('₫');
  });

  it('formats a custom currency', () => {
    expect(formatCurrency(10, 'USD')).toMatch(/10/);
  });

  it('keeps short text unchanged', () => {
    expect(truncate('hello', 10)).toBe('hello');
  });

  it('truncates long text with ellipsis', () => {
    expect(truncate('Smart Recruitment Portal', 8)).toBe('Smart Re...');
  });

  it('accepts a valid email', () => {
    expect(validateEmail('user@srp.test')).toBe(true);
  });

  it('rejects an invalid email', () => {
    expect(validateEmail('not-an-email')).toBe(false);
    expect(validateEmail('a@b')).toBe(false);
  });

  it('requires 8 characters, uppercase and a number', () => {
    expect(validatePassword('short').valid).toBe(false);
    expect(validatePassword('nouppercase1').valid).toBe(false);
    expect(validatePassword('NoNumber').valid).toBe(false);
    expect(validatePassword('Password1')).toEqual({ valid: true, errors: [] });
  });

  it('collects every password rule failure', () => {
    const result = validatePassword('abc');
    expect(result.errors).toHaveLength(3);
  });

  it('opens a file URL in a new tab', () => {
    const open = vi.fn();
    vi.stubGlobal('window', { open });
    openFileInNewTab('https://cdn.example/cv.pdf');
    expect(open).toHaveBeenCalledWith('https://cdn.example/cv.pdf', '_blank', 'noopener,noreferrer');
    openFileInNewTab(undefined);
    expect(open).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });
});
