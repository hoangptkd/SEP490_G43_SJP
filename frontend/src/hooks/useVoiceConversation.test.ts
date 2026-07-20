import { describe, expect, it } from 'vitest';
import { classifyConfirmation } from './useVoiceConversation';

describe('classifyConfirmation', () => {
  it.each([
    'Tôi đã trả lời xong',
    'tôi đã xong',
    'đã xong',
    'Đã Xong',
    'Xong rồi',
    'em xong rồi',
    'mình xong rồi',
    'dạ xong',
    'hoàn thành',
    'đã hoàn thành',
    'da song',
    'tôi đã song rồi',
    'ok xong',
    'có',
    'đúng rồi',
    'dong y',
  ])('recognizes positive confirmation: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('positive');
  });

  it.each([
    'chưa',
    'chưa đâu',
    'Tôi chưa trả lời xong',
    'tôi chưa xong',
    'mình chưa trả lời xong',
    'em chưa xong',
    'tôi muốn nói thêm',
    'tiếp tục',
    'không',
    'không xong',
  ])('recognizes negative confirmation before positive substrings: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('negative');
  });

  it.each(['', 'có lẽ vậy', 'bạn hỏi lại được không', 'tôi đang chuẩn bị'])('keeps unclear confirmation unresolved: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('unknown');
  });
});
