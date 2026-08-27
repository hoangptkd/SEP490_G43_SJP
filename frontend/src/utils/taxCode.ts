const VIETNAM_TAX_CODE_PATTERN = /^\d{10}(?:\d{3})?$/;

export function normalizeVietnamTaxCode(value: string): string {
  return value.trim().replace(/[\s-]/g, '');
}

export function isVietnamTaxCodeFormat(value: string): boolean {
  return VIETNAM_TAX_CODE_PATTERN.test(normalizeVietnamTaxCode(value));
}
