import { describe, expect, it } from 'vitest';
import {
  canStartCandidateCapture,
  canContinueReviewedAnswer,
  canFinalizeSpokenAnswer,
  canSubmitConfirmation,
  canTransitionVoiceState,
  classifyConfirmation,
  appendTranscriptPart,
  isCurrentCaptureCallback,
  pickVietnameseVoice,
  preserveTranscriptBeforeRecognitionSwitch,
  resolveCaptureTranscript,
  resolveConfirmationResponse,
  resolveResumedVoicePhase,
  shouldSettleConfirmationImmediately,
  shouldEnterManualFallbackForRecognitionError,
  shouldScheduleConfirmationPrompt,
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
    'dạ em xong rồi ạ',
    'vâng, tôi đã trả lời xong ạ',
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
    'dạ em chưa xong ạ',
  ])('recognizes negative confirmation before positive substrings: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('negative');
  });

  it.each(['', 'có lẽ vậy', 'bạn hỏi lại được không', 'tôi đang chuẩn bị'])('keeps unclear confirmation unresolved: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('unknown');
  });

  it.each([
    'Tôi chưa từng làm việc nhóm theo Scrum',
    'Tôi đã hoàn thành API và đang mô tả kết quả',
    'Sau khi xử lý xong truy vấn, thời gian phản hồi giảm còn 200ms',
  ])('does not mistake continued answer content for a confirmation: %s', (value) => {
    expect(classifyConfirmation(value)).toBe('unknown');
    expect(resolveConfirmationResponse(value)).toBe('CONTINUED_ANSWER');
  });

  it('maps an empty confirmation timeout to automatic completion', () => {
    expect(resolveConfirmationResponse('')).toBe('NO_RESPONSE');
    expect(resolveConfirmationResponse('đã xong')).toBe('DONE');
    expect(resolveConfirmationResponse('chưa xong')).toBe('NOT_DONE');
  });

  it('fast-paths explicit confirmations but debounces ambiguous one-word replies', () => {
    expect(shouldSettleConfirmationImmediately('đã xong')).toBe(true);
    expect(shouldSettleConfirmationImmediately('chưa xong')).toBe(true);
    expect(shouldSettleConfirmationImmediately('có')).toBe(false);
    expect(shouldSettleConfirmationImmediately('rồi')).toBe(false);
    expect(shouldSettleConfirmationImmediately('không')).toBe(false);
    expect(shouldSettleConfirmationImmediately('chưa')).toBe(false);
    expect(shouldSettleConfirmationImmediately('Tôi đang nói tiếp về caching')).toBe(false);
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
  it('preserves an interim answer before switching to confirmation and appends continued speech', () => {
    const committedBeforePrompt = '';
    const visibleInterimDraft = 'Trong thời gian thực tập tôi đã phát triển hệ thống';
    const preserved = preserveTranscriptBeforeRecognitionSwitch(committedBeforePrompt, visibleInterimDraft);

    expect(preserved).toBe(visibleInterimDraft);
    expect(appendTranscriptPart(preserved, 'và tối ưu truy vấn SQL')).toBe(
      'Trong thời gian thực tập tôi đã phát triển hệ thống và tối ưu truy vấn SQL',
    );
  });

  it('falls back to the committed answer when there is no interim draft', () => {
    expect(preserveTranscriptBeforeRecognitionSwitch('Câu trả lời đã chốt', '   '))
      .toBe('Câu trả lời đã chốt');
  });

  it('keeps the user-edited review as the base when the candidate continues answering', () => {
    const staleRawTranscript = 'Trong dự án em dùng spring bút để viết API';
    const reviewedTranscript = 'Trong dự án em dùng Spring Boot để viết API';
    const resumedBase = preserveTranscriptBeforeRecognitionSwitch(reviewedTranscript, reviewedTranscript);

    expect(appendTranscriptPart(resumedBase, 'và Redis để cache dữ liệu')).toBe(
      'Trong dự án em dùng Spring Boot để viết API và Redis để cache dữ liệu',
    );
    expect(resumedBase).not.toBe(staleRawTranscript);
  });

  it('keeps Web Speech authoritative after background audio processing completes', () => {
    const processingDisplay = resolveCaptureTranscript('em dùng spring bút và rét api');
    expect(processingDisplay.displayedTranscript).toBe('em dùng spring bút và rét api');
    const completed = resolveCaptureTranscript('em dùng spring bút và rét api', {
      gladiaTranscript: 'Em dùng Spring Boot và REST API.',
      rawTranscript: 'em dùng spring bút và rét api',
      transcriptStatus: 'web_speech',
    });
    expect(completed.displayedTranscript).toBe('em dùng spring bút và rét api');
    expect(completed.rawTranscript).toBe('em dùng spring bút và rét api');
    expect(completed.gladiaTranscript).toBe('');
    expect(completed.notice).toBe('Đã hoàn tất audio. Transcript Web Speech được giữ nguyên.');
  });

  it('keeps Web Speech when background audio processing is unavailable', () => {
    expect(resolveCaptureTranscript('Bản realtime')).toEqual({
      rawTranscript: 'Bản realtime',
      displayedTranscript: 'Bản realtime',
      gladiaTranscript: '',
      notice: 'Không thể xử lý audio. Transcript Web Speech vẫn được giữ lại.',
    });
  });

  it('retains the Gladia Live display path behind its explicit provider flag', () => {
    expect(resolveCaptureTranscript('Bản Web Speech', {
      gladiaTranscript: 'Bản Gladia Live',
      rawTranscript: 'Bản Gladia Live',
      transcriptStatus: 'standardized',
    }, 'gladia_live')).toEqual({
      rawTranscript: 'Bản Gladia Live',
      displayedTranscript: 'Bản Gladia Live',
      gladiaTranscript: 'Bản Gladia Live',
      notice: 'Đã tạo transcript draft từ Gladia',
    });
  });

  it('shows the validated correction while retaining raw Web Speech audit text', () => {
    expect(resolveCaptureTranscript('em dùng spring bút', {
      rawTranscript: 'em dùng spring bút',
      correctedTranscript: 'em dùng Spring Boot',
      correctionStatus: 'CORRECTED',
      correctionCount: 1,
      transcriptStatus: 'web_speech',
    })).toEqual({
      rawTranscript: 'em dùng spring bút',
      displayedTranscript: 'em dùng Spring Boot',
      gladiaTranscript: '',
      notice: 'AI đã sửa 1 lỗi nhận dạng có độ tin cậy cao. Hãy kiểm tra trước khi xác nhận.',
    });
  });

  it('uses raw Web Speech when transcript correction fails', () => {
    const resolved = resolveCaptureTranscript('em dùng spring bút', {
      rawTranscript: 'em dùng spring bút',
      correctedTranscript: 'nội dung không được tin cậy',
      correctionStatus: 'FAILED',
      correctionCount: 0,
      transcriptStatus: 'web_speech',
    });

    expect(resolved.displayedTranscript).toBe('em dùng spring bút');
    expect(resolved.rawTranscript).toBe('em dùng spring bút');
    expect(resolved.notice).toBe(
      'Không thể kiểm tra lỗi nhận dạng. Transcript Web Speech vẫn được giữ nguyên.',
    );
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

  it('allows Done only while listening and blocks it while processing', () => {
    expect(canFinalizeSpokenAnswer('LISTENING', false)).toBe(true);
    expect(canFinalizeSpokenAnswer('WAITING_FOR_CONTINUATION', false)).toBe(true);
    expect(canFinalizeSpokenAnswer('PROCESSING_AUDIO', false)).toBe(false);
    expect(canFinalizeSpokenAnswer('LISTENING', true)).toBe(false);
  });

  it('allows continuing only from transcript review', () => {
    expect(canContinueReviewedAnswer('REVIEWING_TRANSCRIPT', false)).toBe(true);
    expect(canContinueReviewedAnswer('LISTENING', false)).toBe(false);
    expect(canContinueReviewedAnswer('REVIEWING_TRANSCRIPT', true)).toBe(false);
  });

  it('keeps waiting before the candidate has started speaking', () => {
    expect(shouldScheduleConfirmationPrompt('')).toBe(false);
    expect(shouldScheduleConfirmationPrompt('   ')).toBe(false);
    expect(shouldScheduleConfirmationPrompt('Tôi bắt đầu trả lời')).toBe(true);
  });

  it('does not treat temporary no-speech as a microphone failure', () => {
    expect(shouldEnterManualFallbackForRecognitionError('no-speech')).toBe(false);
    expect(shouldEnterManualFallbackForRecognitionError('aborted')).toBe(false);
    expect(shouldEnterManualFallbackForRecognitionError('not-allowed')).toBe(true);
    expect(shouldEnterManualFallbackForRecognitionError('network')).toBe(true);
  });
});

describe('conversation state machine', () => {
  it('blocks invalid processing races and preserves a resumable review state', () => {
    expect(canTransitionVoiceState('PROCESSING_AUDIO', 'LISTENING')).toBe(false);
    expect(canTransitionVoiceState('PROCESSING_AUDIO', 'REVIEWING_TRANSCRIPT')).toBe(true);
    expect(canTransitionVoiceState('REVIEWING_TRANSCRIPT', 'ANSWER_CONFIRMED')).toBe(true);
    expect(resolveResumedVoicePhase('REVIEWING_TRANSCRIPT')).toBe('REVIEWING_TRANSCRIPT');
  });

  it('allows confirmation speech to transition into confirmation listening', () => {
    expect(canTransitionVoiceState('LISTENING', 'AI_SPEAKING')).toBe(true);
    expect(canTransitionVoiceState('AI_SPEAKING', 'WAITING_FOR_CONTINUATION')).toBe(true);
    expect(canTransitionVoiceState('WAITING_FOR_CONTINUATION', 'PROCESSING_AUDIO')).toBe(true);
  });
});

describe('Vietnamese TTS voice selection', () => {
  const voice = (name: string, lang: string) => ({ name, lang }) as SpeechSynthesisVoice;

  it('prefers vi-VN and never selects a non-Vietnamese voice by name', () => {
    const englishNamedVietnamese = voice('Vietnamese Demo', 'en-US');
    const vietnamese = voice('Microsoft HoaiMy', 'vi-VN');
    expect(pickVietnameseVoice([englishNamedVietnamese, vietnamese])).toBe(vietnamese);
    expect(pickVietnameseVoice([englishNamedVietnamese])).toBeUndefined();
  });
});
