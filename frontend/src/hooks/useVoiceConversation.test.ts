import { describe, expect, it } from 'vitest';
import {
  canStartCandidateCapture,
  canSubmitConfirmation,
  classifyConfirmation,
  isCurrentCaptureCallback,
  resolveCaptureTranscript,
} from './useVoiceConversation';

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

describe('hands-free stale callback guard', () => {
  const expected = { token: 3, questionId: 'question-1', captureId: 'capture-1' };

  it('accepts only the current question and capture response', () => {
    expect(isCurrentCaptureCallback(expected,
      { active: true, token: 3, questionId: 'question-1', captureId: 'capture-1' },
      { questionId: 'question-1', captureId: 'capture-1' })).toBe(true);
  });

  it.each([
    { active: false, token: 3, questionId: 'question-1', captureId: 'capture-1' },
    { active: true, token: 4, questionId: 'question-1', captureId: 'capture-1' },
    { active: true, token: 3, questionId: 'question-2', captureId: 'capture-1' },
    { active: true, token: 3, questionId: 'question-1', captureId: 'capture-2' },
  ])('ignores inactive, unmounted, new-question and new-capture callbacks', (current) => {
    expect(isCurrentCaptureCallback(expected, current,
      { questionId: 'question-1', captureId: 'capture-1' })).toBe(false);
  });

  it('ignores a late provider result for an old capture', () => {
    expect(isCurrentCaptureCallback(expected,
      { active: true, token: 3, questionId: 'question-1', captureId: 'capture-1' },
      { questionId: 'question-1', captureId: 'capture-old' })).toBe(false);
  });
});

describe('hands-free transcript source of truth', () => {
  it('keeps the browser transcript visible while processing and replaces it with Gladia when ready', () => {
    const processingDisplay = resolveCaptureTranscript('em dùng spring bút và rét api');
    expect(processingDisplay.displayedTranscript).toBe('em dùng spring bút và rét api');
    const standardized = resolveCaptureTranscript('em dùng spring bút và rét api', {
      gladiaTranscript: 'Em dùng Spring Boot và REST API.',
      finalTranscript: 'Em dùng Spring Boot và REST API.',
      transcriptStatus: 'standardized',
    });
    expect(standardized.displayedTranscript).toBe('Em dùng Spring Boot và REST API.');
    expect(standardized.gladiaTranscript).toBe('Em dùng Spring Boot và REST API.');
    expect(standardized.notice).toBe('Đã chuẩn hóa');
  });

  it('keeps browser transcript when Gladia is unavailable', () => {
    expect(resolveCaptureTranscript('Bản realtime')).toEqual({
      displayedTranscript: 'Bản realtime',
      gladiaTranscript: '',
      notice: 'Không thể chuẩn hóa, sử dụng bản ghi nhận realtime',
    });
  });
});

describe('hands-free lifecycle gates', () => {
  it('starts candidate capture only after system TTS has ended', () => {
    expect(canStartCandidateCapture(true, true, false)).toBe(false);
    expect(canStartCandidateCapture(true, false, false)).toBe(true);
  });

  it('prevents duplicate positive confirmation submission', () => {
    expect(canSubmitConfirmation(false, 'Final transcript')).toBe(true);
    expect(canSubmitConfirmation(true, 'Final transcript')).toBe(false);
    expect(canSubmitConfirmation(false, '   ')).toBe(false);
  });
});
