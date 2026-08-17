import { describe, expect, it } from 'vitest';
import { buildExcelXml, sanitizeFileName } from './exportCsv';

describe('excelExportHelpers', () => {
  it('strips illegal filename characters', () => {
    expect(sanitizeFileName('A<>:"/\\|?*B')).toBe('AB');
  });

  it('falls back to export when the name is empty', () => {
    expect(sanitizeFileName('   ')).toBe('export');
  });

  it('builds spreadsheet xml with headers and escaped cells', () => {
    const xml = buildExcelXml({
      sheetName: 'Ung vien',
      title: 'Danh sách',
      headers: ['STT', 'Tên'],
      rows: [[1, 'A & B']],
    });
    expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    expect(xml).toContain('Danh sách');
    expect(xml).toContain('A &amp; B');
    expect(xml).toContain('ss:Type="Number">1</Data>');
  });
});
