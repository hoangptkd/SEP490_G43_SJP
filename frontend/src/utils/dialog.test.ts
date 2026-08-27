import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setDialogListener, customAlert, customConfirm, customPrompt } from './dialog';

describe('dialog utils', () => {
  beforeEach(() => {
    setDialogListener(null as any);
  });

  afterEach(() => {
    setDialogListener(null as any);
  });

  it('customAlert resolves when listener calls onClose', async () => {
    setDialogListener((options) => {
      expect(options.type).toBe('alert');
      expect(options.message).toBe('Hello');
      options.onClose(undefined);
    });

    await customAlert('Hello', 'Title');
  });

  it('customAlert falls back to window.alert when no listener', async () => {
    const mockAlert = vi.fn();
    Object.defineProperty(globalThis, 'window', {
      value: { alert: mockAlert, confirm: vi.fn(), prompt: vi.fn() },
      configurable: true,
    });

    await customAlert('test');
    expect(mockAlert).toHaveBeenCalledWith('test');
  });

  it('customConfirm resolves with boolean from listener', async () => {
    setDialogListener((options) => {
      expect(options.type).toBe('confirm');
      options.onClose(true);
    });

    const result = await customConfirm('Are you sure?');
    expect(result).toBe(true);
  });

  it('customConfirm falls back to window.confirm', async () => {
    Object.defineProperty(globalThis, 'window', {
      value: { alert: vi.fn(), confirm: vi.fn(() => false), prompt: vi.fn() },
      configurable: true,
    });

    const result = await customConfirm('ok?');
    expect(result).toBe(false);
  });

  it('customPrompt resolves with string from listener', async () => {
    setDialogListener((options) => {
      expect(options.type).toBe('prompt');
      expect(options.defaultValue).toBe('default');
      options.onClose('user input');
    });

    const result = await customPrompt('Enter:', 'default');
    expect(result).toBe('user input');
  });

  it('customPrompt falls back to window.prompt', async () => {
    Object.defineProperty(globalThis, 'window', {
      value: { alert: vi.fn(), confirm: vi.fn(), prompt: vi.fn(() => 'prompted') },
      configurable: true,
    });

    const result = await customPrompt('Enter:');
    expect(result).toBe('prompted');
  });
});
