import { useCallback, useEffect, useRef, useState } from 'react';
import type {
  AiInterviewSpeechTicket,
  AiInterviewTranscriptionTicket,
  HandsFreeAnswerCaptureResult,
  HandsFreeAudioSegmentUpload,
  InterviewConversationState,
} from '../types/aiInterview';
import { CandidateAudioCapture, type CapturedAudioSegment } from './handsFreeAudioCapture';
import { PcmAudioStreamPlayer } from './pcmAudioStreamPlayer';
import type {
  AnswerTranscriptionProvider,
  AnswerTranscriptionProviderKind,
  AnswerTranscriptionUpdate,
} from './answerTranscriptionProvider';
import { createAnswerTranscriptionProvider } from './answerTranscriptionProviderFactory';

export type VoicePhase =
  | 'IDLE'
  | 'AI_SPEAKING'
  | 'LISTENING'
  | 'WAITING_FOR_CONTINUATION'
  | 'PROCESSING_AUDIO'
  | 'REVIEWING_TRANSCRIPT'
  | 'ANSWER_CONFIRMED'
  | 'NEXT_QUESTION'
  | 'ERROR_RECOVERABLE';

export type ConfirmationIntent = 'positive' | 'negative' | 'unknown';

interface VoiceConversationOptions {
  questionId?: string;
  questionText?: string;
  initialTranscript: string;
  initialRawTranscript?: string;
  initialConversationState?: InterviewConversationState;
  confirmationPromptDelayMs: number;
  confirmationAutoFinalizeMs: number;
  recognitionRestartDelayMs: number;
  voiceLoadWaitMs: number;
  nextQuestionDelayMs: number;
  answerTranscriptionProvider: AnswerTranscriptionProviderKind;
  onCreateTranscriptionTicket?: () => Promise<AiInterviewTranscriptionTicket>;
  disabled?: boolean;
  onTranscript: (value: string) => void;
  onFinalizeCapture: (
    segments: HandsFreeAudioSegmentUpload[],
    captureId: string,
    captureVersion: number,
    browserTranscript: string,
    transcriptionProvider: AnswerTranscriptionProviderKind,
  ) => Promise<HandsFreeAnswerCaptureResult>;
  onConfirm: (finalTranscript: string, rawTranscript?: string) => Promise<void>;
  onReplayQuestion: () => Promise<void>;
  onSpeechUrl?: (text: string) => Promise<AiInterviewSpeechTicket | undefined>;
}

type RecognitionMode = 'answer' | 'confirmation';
const VIETNAMESE_LANG = 'vi-VN';
const CONFIRMATION_PROMPT = 'Bạn đã trả lời xong chưa?';

export type ConfirmationResolution = 'DONE' | 'NOT_DONE' | 'CONTINUED_ANSWER' | 'NO_RESPONSE';

const ALLOWED_TRANSITIONS: Record<VoicePhase, ReadonlySet<VoicePhase>> = {
  IDLE: new Set(['AI_SPEAKING', 'LISTENING', 'REVIEWING_TRANSCRIPT', 'NEXT_QUESTION']),
  AI_SPEAKING: new Set(['LISTENING', 'WAITING_FOR_CONTINUATION', 'REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE']),
  LISTENING: new Set(['WAITING_FOR_CONTINUATION', 'PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'AI_SPEAKING', 'ERROR_RECOVERABLE']),
  WAITING_FOR_CONTINUATION: new Set(['LISTENING', 'PROCESSING_AUDIO', 'AI_SPEAKING', 'ERROR_RECOVERABLE']),
  PROCESSING_AUDIO: new Set(['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE']),
  REVIEWING_TRANSCRIPT: new Set(['LISTENING', 'ANSWER_CONFIRMED', 'AI_SPEAKING', 'ERROR_RECOVERABLE']),
  ANSWER_CONFIRMED: new Set(['NEXT_QUESTION', 'ERROR_RECOVERABLE']),
  NEXT_QUESTION: new Set(['AI_SPEAKING', 'LISTENING', 'REVIEWING_TRANSCRIPT', 'IDLE']),
  ERROR_RECOVERABLE: new Set(['LISTENING', 'PROCESSING_AUDIO', 'REVIEWING_TRANSCRIPT', 'ANSWER_CONFIRMED', 'IDLE']),
};

export function canTransitionVoiceState(from: VoicePhase, to: VoicePhase) {
  return from === to || to === 'IDLE' || to === 'NEXT_QUESTION'
    || ALLOWED_TRANSITIONS[from].has(to);
}

export function canFinalizeSpokenAnswer(phase: VoicePhase, saving: boolean) {
  return !saving && (phase === 'LISTENING' || phase === 'WAITING_FOR_CONTINUATION');
}

export function canContinueReviewedAnswer(phase: VoicePhase, saving: boolean) {
  return !saving && phase === 'REVIEWING_TRANSCRIPT';
}

export function resolveResumedVoicePhase(state?: InterviewConversationState): VoicePhase {
  return state || 'IDLE';
}

export function pickVietnameseVoice(voices: SpeechSynthesisVoice[]) {
  return voices.find((voice) => voice.lang?.toLowerCase() === 'vi-vn')
    || voices.find((voice) => voice.lang?.toLowerCase().startsWith('vi-'));
}

function normalizeSpeech(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd')
    .toLowerCase().replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}

function normalizeConfirmationPhrase(value: string) {
  return normalizeSpeech(value)
    .replace(/^(?:da vang|vang|da)\s+/, '')
    .replace(/\s+a$/, '')
    .trim();
}

const NEGATIVE_CONFIRMATION_PHRASES = new Set([
  'khong', 'khong dau', 'khong xong', 'khong phai', 'chua', 'chua dau', 'chua xong',
  'chua tra loi xong', 'toi chua xong', 'toi chua tra loi xong', 'em chua xong',
  'em chua tra loi xong', 'minh chua xong', 'minh chua tra loi xong',
  'toi muon noi them', 'em muon noi them', 'minh muon noi them', 'muon noi them',
  'noi them', 'tiep tuc', 'toi muon tiep tuc', 'em muon tiep tuc', 'minh muon tiep tuc',
]);

const POSITIVE_CONFIRMATION_PHRASES = new Set([
  'toi da tra loi xong', 'da tra loi xong', 'tra loi xong', 'toi da xong', 'toi xong roi',
  'em da xong', 'em xong roi', 'em xong', 'minh da xong', 'minh xong roi', 'minh xong',
  'xong', 'xong roi', 'song', 'song roi', 'toi da song roi', 'hoan thanh', 'da hoan thanh',
  'ok xong', 'okay xong', 'roi', 'duoc roi', 'dung roi', 'dong y', 'co',
]);

const WEAK_POSITIVE_CONFIRMATION_PHRASES = new Set([
  'co', 'roi', 'duoc roi', 'dung roi', 'dong y',
]);

const WEAK_NEGATIVE_CONFIRMATION_PHRASES = new Set(['khong', 'chua']);

export function classifyConfirmation(value: string): ConfirmationIntent {
  const normalized = normalizeConfirmationPhrase(value);
  if (!normalized) return 'unknown';
  if (NEGATIVE_CONFIRMATION_PHRASES.has(normalized)) return 'negative';
  return POSITIVE_CONFIRMATION_PHRASES.has(normalized) ? 'positive' : 'unknown';
}

export function shouldSettleConfirmationImmediately(value: string) {
  const normalized = normalizeConfirmationPhrase(value);
  const intent = classifyConfirmation(value);
  return (intent === 'negative' && !WEAK_NEGATIVE_CONFIRMATION_PHRASES.has(normalized))
    || (intent === 'positive' && !WEAK_POSITIVE_CONFIRMATION_PHRASES.has(normalized));
}

export function resolveConfirmationResponse(value: string): ConfirmationResolution {
  const cleaned = value.trim();
  if (!cleaned) return 'NO_RESPONSE';
  const intent = classifyConfirmation(cleaned);
  if (intent === 'positive') return 'DONE';
  if (intent === 'negative') return 'NOT_DONE';
  return 'CONTINUED_ANSWER';
}

export function preserveTranscriptBeforeRecognitionSwitch(committed: string, draft: string) {
  const normalizedDraft = draft.replace(/\s+/g, ' ').trim();
  return normalizedDraft || committed.replace(/\s+/g, ' ').trim();
}

export function appendTranscriptPart(existing: string, next: string) {
  return `${existing} ${next}`.replace(/\s+/g, ' ').trim();
}

function newCaptureId() {
  return globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

export function isCurrentCaptureCallback(
  expected: { token: number; questionId?: string; captureId: string },
  current: { active: boolean; token: number; questionId?: string; captureId: string },
  result?: { questionId: string; captureId: string },
) {
  return current.active
    && expected.token === current.token
    && expected.questionId === current.questionId
    && expected.captureId === current.captureId
    && (!result || (result.questionId === expected.questionId && result.captureId === expected.captureId));
}

export function canStartCandidateCapture(active: boolean, systemSpeaking: boolean, disabled: boolean) {
  return active && !systemSpeaking && !disabled;
}

export function resolveCaptureTranscript(
  browserTranscript: string,
  result?: Pick<HandsFreeAnswerCaptureResult,
    'rawTranscript' | 'correctedTranscript' | 'correctionStatus' | 'correctionCount'
    | 'gladiaTranscript' | 'transcriptStatus'>,
) {
  const rawTranscript = result?.rawTranscript?.trim() || browserTranscript.trim();
  const correctionSucceeded = result?.correctionStatus === 'CORRECTED'
    || result?.correctionStatus === 'UNCHANGED';
  const displayedTranscript = correctionSucceeded
    ? result?.correctedTranscript?.trim() || rawTranscript
    : rawTranscript;
  let notice = 'Đã hoàn tất audio. Transcript Web Speech được giữ nguyên.';
  if (!result) notice = 'Không thể xử lý audio. Transcript Web Speech vẫn được giữ lại.';
  else if (result.correctionStatus === 'CORRECTED') {
    notice = `AI đã sửa ${result.correctionCount || 0} lỗi nhận dạng có độ tin cậy cao. Hãy kiểm tra trước khi xác nhận.`;
  } else if (result.correctionStatus === 'UNCHANGED') {
    notice = 'AI đã kiểm tra và không phát hiện lỗi nhận dạng đủ chắc chắn để sửa.';
  } else if (result.correctionStatus === 'FAILED') {
    notice = 'Không thể kiểm tra lỗi nhận dạng. Transcript Web Speech vẫn được giữ nguyên.';
  } else if (result.correctionStatus === 'PENDING') {
    notice = 'Chưa hoàn tất kiểm tra lỗi nhận dạng. Transcript Web Speech vẫn được giữ nguyên.';
  }
  return {
    rawTranscript,
    displayedTranscript,
    gladiaTranscript: '',
    notice,
  };
}

export function canSubmitConfirmation(saving: boolean, transcript: string) {
  return !saving && Boolean(transcript.trim());
}

export function shouldScheduleConfirmationPrompt(transcript: string) {
  return Boolean(transcript.trim());
}

export function shouldEnterManualFallbackForRecognitionError(error: string) {
  return error !== 'aborted' && error !== 'no-speech';
}

export function useVoiceConversation({
  questionId,
  questionText,
  initialTranscript,
  initialRawTranscript,
  initialConversationState,
  confirmationPromptDelayMs,
  confirmationAutoFinalizeMs,
  recognitionRestartDelayMs,
  voiceLoadWaitMs,
  nextQuestionDelayMs,
  answerTranscriptionProvider,
  onCreateTranscriptionTicket,
  disabled = false,
  onTranscript,
  onFinalizeCapture,
  onConfirm,
  onReplayQuestion,
  onSpeechUrl,
}: VoiceConversationOptions) {
  const canUseBrowserSpeech = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const canUseAnswerRecognition = answerTranscriptionProvider === 'speechmatics_realtime'
    ? Boolean(onCreateTranscriptionTicket)
    : Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
  const supported = typeof window !== 'undefined'
    && canUseAnswerRecognition
    && (canUseBrowserSpeech || Boolean(onSpeechUrl));
  const [active, setActive] = useState(false);
  const resumedPhase = resolveResumedVoicePhase(initialConversationState);
  const [phase, setPhaseState] = useState<VoicePhase>(resumedPhase);
  const [manualFallback, setManualFallback] = useState(false);
  const [rawTranscript, setRawTranscript] = useState(initialRawTranscript?.trim() || '');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [transcriptNotice, setTranscriptNotice] = useState('');
  const [error, setError] = useState('');

  const phaseRef = useRef<VoicePhase>(resumedPhase);
  const activeRef = useRef(false);
  const disabledRef = useRef(disabled);
  const speakingRef = useRef(false);
  const savingRef = useRef(false);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const answerProviderRef = useRef<AnswerTranscriptionProvider | null>(null);
  const confirmationProviderRef = useRef<AnswerTranscriptionProvider | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const pcmPlayerRef = useRef<PcmAudioStreamPlayer | null>(null);
  const audioCaptureRef = useRef(new CandidateAudioCapture());
  const captureAvailableRef = useRef(true);
  const captureIdRef = useRef(newCaptureId());
  const captureVersionRef = useRef(0);
  const segmentsRef = useRef<CapturedAudioSegment[]>([]);
  const modeRef = useRef<RecognitionMode>('answer');
  const browserCommittedRef = useRef(initialTranscript.trim());
  const browserDraftRef = useRef(initialTranscript.trim());
  const rawTranscriptRef = useRef(initialRawTranscript?.trim() || '');
  const confirmedTranscriptRef = useRef(initialTranscript.trim());
  const gladiaTranscriptRef = useRef('');
  const questionIdRef = useRef(questionId);
  const questionTextRef = useRef(questionText);
  const lifecycleTokenRef = useRef(0);
  const speechTokenRef = useRef(0);
  const confirmationCycleRef = useRef(0);
  const confirmationHandledRef = useRef(false);
  const confirmationSegmentActiveRef = useRef(false);
  const confirmationCommittedRef = useRef('');
  const confirmationDraftRef = useRef('');
  const confirmationPromptTimerRef = useRef<number>();
  const confirmationAutoFinalizeTimerRef = useRef<number>();
  const restartTimerRef = useRef<number>();
  const voiceLoadTimerRef = useRef<number>();
  const voiceLoadCleanupRef = useRef<() => void>(() => undefined);
  const onTranscriptRef = useRef(onTranscript);
  const onFinalizeCaptureRef = useRef(onFinalizeCapture);
  const onConfirmRef = useRef(onConfirm);
  const onReplayQuestionRef = useRef(onReplayQuestion);
  const onSpeechUrlRef = useRef(onSpeechUrl);
  const startAnswerListeningRef = useRef<(schedulePromptWithoutNewResult?: boolean) => void>(() => undefined);
  const startAnswerRecognitionRef = useRef<() => void>(() => undefined);
  const startConfirmationListeningRef = useRef<() => void>(() => undefined);
  const startConfirmationRecognitionRef = useRef<() => void>(() => undefined);
  const askForConfirmationRef = useRef<() => void>(() => undefined);
  const settleConfirmationRef = useRef<(
    resolution: ConfirmationResolution,
    spokenText?: string,
  ) => void>(() => undefined);
  const finalizeAnswerRef = useRef<() => void>(() => undefined);

  const setPhase = useCallback((next: VoicePhase) => {
    if (!canTransitionVoiceState(phaseRef.current, next)) return false;
    phaseRef.current = next;
    setPhaseState(next);
    return true;
  }, []);

  useEffect(() => { onTranscriptRef.current = onTranscript; }, [onTranscript]);
  useEffect(() => { onFinalizeCaptureRef.current = onFinalizeCapture; }, [onFinalizeCapture]);
  useEffect(() => { onConfirmRef.current = onConfirm; }, [onConfirm]);
  useEffect(() => { onReplayQuestionRef.current = onReplayQuestion; }, [onReplayQuestion]);
  useEffect(() => { onSpeechUrlRef.current = onSpeechUrl; }, [onSpeechUrl]);
  useEffect(() => { disabledRef.current = disabled; }, [disabled]);
  useEffect(() => {
    if (!initialConversationState || activeRef.current) return;
    setPhase(resolveResumedVoicePhase(initialConversationState));
  }, [initialConversationState, setPhase]);

  const clearTimers = useCallback(() => {
    window.clearTimeout(confirmationPromptTimerRef.current);
    window.clearTimeout(confirmationAutoFinalizeTimerRef.current);
    window.clearTimeout(restartTimerRef.current);
    window.clearTimeout(voiceLoadTimerRef.current);
    voiceLoadCleanupRef.current();
    voiceLoadCleanupRef.current = () => undefined;
  }, []);

  const stopRecognition = useCallback(() => {
    const confirmationProvider = confirmationProviderRef.current;
    confirmationProviderRef.current = null;
    confirmationProvider?.dispose();
    const recognition = recognitionRef.current;
    recognitionRef.current = null;
    if (!recognition) return;
    try { recognition.abort(); } catch { /* already stopped */ }
  }, []);

  const disposeAnswerProvider = useCallback(() => {
    const provider = answerProviderRef.current;
    answerProviderRef.current = null;
    provider?.dispose();
  }, []);

  const stopAudio = useCallback(() => {
    pcmPlayerRef.current?.stop();
    const audio = audioRef.current;
    audioRef.current = null;
    if (!audio) return;
    audio.onended = null;
    audio.onerror = null;
    audio.onplaying = null;
    try {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    } catch { /* detached element */ }
  }, []);

  const enterManualFallback = useCallback((message?: string) => {
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    modeRef.current = 'answer';
    captureAvailableRef.current = false;
    speakingRef.current = false;
    activeRef.current = true;
    setActive(true);
    setManualFallback(true);
    setInterimTranscript('');
    setPhase(rawTranscriptRef.current || browserDraftRef.current
      ? 'REVIEWING_TRANSCRIPT' : 'LISTENING');
    window.speechSynthesis?.cancel();
    setError(message || 'Đã chuyển sang nhập transcript thủ công. Nội dung hiện tại vẫn được giữ nguyên.');
  }, [clearTimers, disposeAnswerProvider, setPhase, stopAudio, stopRecognition]);

  const speak = useCallback((
    text: string,
    nextPhase: VoicePhase,
    onDone: () => void,
    onStarted?: () => void,
  ) => {
    if (!supported || !activeRef.current || disabledRef.current) return;
    // Safety barrier: system TTS must never overlap candidate capture.
    if (audioCaptureRef.current.recording) audioCaptureRef.current.abortSegment();
    answerProviderRef.current?.pause();
    speakingRef.current = true;
    stopRecognition();
    stopAudio();
    window.speechSynthesis?.cancel();
    const speechToken = ++speechTokenRef.current;
    let started = false;
    let finished = false;
    const markStarted = () => {
      if (speechTokenRef.current !== speechToken || started) return;
      started = true;
      setPhase(nextPhase);
      onStarted?.();
    };
    const finish = () => {
      if (speechTokenRef.current !== speechToken || finished) return;
      finished = true;
      speakingRef.current = false;
      stopAudio();
      if (activeRef.current && !disabledRef.current) onDone();
    };
    const browserSpeech = () => {
      if (speechTokenRef.current !== speechToken) return;
      if (!canUseBrowserSpeech) return finish();
      const utterance = new SpeechSynthesisUtterance(text);
      const voice = pickVietnameseVoice(window.speechSynthesis.getVoices());
      if (!voice) {
        setError('Không tìm thấy giọng đọc tiếng Việt phù hợp. Bạn có thể đọc câu hỏi trên màn hình và tiếp tục trả lời.');
        finish();
        return;
      }
      utterance.voice = voice;
      utterance.lang = voice.lang;
      utterance.rate = 0.94;
      utterance.pitch = 1;
      utterance.onstart = markStarted;
      utterance.onend = finish;
      utterance.onerror = finish;
      window.speechSynthesis.speak(utterance);
    };
    const resolver = onSpeechUrlRef.current;
    if (resolver) {
      const providerSpeechFailed = () => {
        if (speechTokenRef.current !== speechToken || finished) return;
        setError('Không thể phát trọn vẹn giọng đọc. Hãy đọc câu hỏi trên màn hình và tiếp tục trả lời.');
        finish();
      };
      void resolver(text).then((ticket) => {
        if (speechTokenRef.current !== speechToken || !activeRef.current) return;
        if (!ticket?.streamUrl) return providerSpeechFailed();
        if (ticket.contentType?.toLowerCase().startsWith('text/event-stream')) {
          const player = pcmPlayerRef.current || new PcmAudioStreamPlayer();
          pcmPlayerRef.current = player;
          void player.play(ticket.streamUrl, markStarted).then(finish).catch(providerSpeechFailed);
          return;
        }
        const audio = new Audio(ticket.streamUrl);
        audioRef.current = audio;
        audio.onplaying = markStarted;
        audio.onended = finish;
        audio.onerror = providerSpeechFailed;
        void audio.play().catch(providerSpeechFailed);
      }).catch(providerSpeechFailed);
      return;
    }
    if (window.speechSynthesis?.getVoices().length) return browserSpeech();
    const startOnce = () => {
      window.clearTimeout(voiceLoadTimerRef.current);
      voiceLoadCleanupRef.current();
      voiceLoadCleanupRef.current = () => undefined;
      browserSpeech();
    };
    voiceLoadCleanupRef.current = () => window.speechSynthesis?.removeEventListener('voiceschanged', startOnce);
    window.speechSynthesis?.addEventListener('voiceschanged', startOnce, { once: true });
    voiceLoadTimerRef.current = window.setTimeout(startOnce, voiceLoadWaitMs);
  }, [canUseBrowserSpeech, setPhase, stopAudio, stopRecognition, supported, voiceLoadWaitMs]);

  const restartRecognition = useCallback((mode: RecognitionMode) => {
    window.clearTimeout(restartTimerRef.current);
    restartTimerRef.current = window.setTimeout(() => {
      if (!activeRef.current || speakingRef.current || savingRef.current || disabledRef.current) return;
      if (modeRef.current !== mode) return;
      if (mode === 'answer' && phaseRef.current === 'LISTENING') {
        startAnswerRecognitionRef.current();
      } else if (mode === 'confirmation' && phaseRef.current === 'WAITING_FOR_CONTINUATION'
        && !confirmationHandledRef.current) {
        startConfirmationRecognitionRef.current();
      }
    }, recognitionRestartDelayMs);
  }, [recognitionRestartDelayMs]);

  const stopConfirmationSegment = useCallback((preserveAsAnswer: boolean) => {
    confirmationSegmentActiveRef.current = false;
    return audioCaptureRef.current.stopSegment().catch(() => null).then((segment) => {
      if (preserveAsAnswer && segment && segment.sequence === segmentsRef.current.length) {
        segmentsRef.current.push(segment);
      }
      return segment;
    });
  }, []);

  const settleConfirmation = useCallback((
    resolution: ConfirmationResolution,
    spokenText = '',
  ) => {
    if (!activeRef.current || phaseRef.current !== 'WAITING_FOR_CONTINUATION'
      || modeRef.current !== 'confirmation' || confirmationHandledRef.current) return;
    const cycle = confirmationCycleRef.current;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    confirmationHandledRef.current = true;
    window.clearTimeout(confirmationAutoFinalizeTimerRef.current);
    stopRecognition();
    setInterimTranscript('');
    void stopConfirmationSegment(resolution === 'CONTINUED_ANSWER').then(() => {
      if (!activeRef.current || cycle !== confirmationCycleRef.current
        || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current) return;
      modeRef.current = 'answer';
      confirmationCommittedRef.current = '';
      confirmationDraftRef.current = '';
      if (resolution === 'CONTINUED_ANSWER') {
        const continued = spokenText.trim();
        if (continued) {
          const provider = answerProviderRef.current;
          if (provider) provider.appendExternalTranscript(continued);
          else {
            browserCommittedRef.current = appendTranscriptPart(browserCommittedRef.current, continued);
            browserDraftRef.current = browserCommittedRef.current;
            onTranscriptRef.current(browserDraftRef.current);
          }
        }
        setTranscriptNotice('Đã ghi nhận phần trả lời tiếp theo. Hệ thống đang tiếp tục lắng nghe.');
        startAnswerListeningRef.current(true);
        return;
      }
      if (resolution === 'NOT_DONE') {
        setTranscriptNotice('Được rồi, bạn có thể tiếp tục trả lời.');
        startAnswerListeningRef.current();
        return;
      }
      setTranscriptNotice(resolution === 'DONE'
        ? 'Đã ghi nhận bạn trả lời xong. Đang tạo transcript draft để bạn kiểm tra...'
        : 'Không có phản hồi thêm. Hệ thống đang tạo transcript draft để bạn kiểm tra...');
      finalizeAnswerRef.current();
    });
  }, [stopConfirmationSegment, stopRecognition]);
  settleConfirmationRef.current = settleConfirmation;

  const scheduleConfirmationAutoFinalize = useCallback(() => {
    window.clearTimeout(confirmationAutoFinalizeTimerRef.current);
    const cycle = confirmationCycleRef.current;
    confirmationAutoFinalizeTimerRef.current = window.setTimeout(() => {
      if (cycle !== confirmationCycleRef.current || confirmationHandledRef.current
        || modeRef.current !== 'confirmation' || phaseRef.current !== 'WAITING_FOR_CONTINUATION') return;
      const spokenText = confirmationDraftRef.current.trim();
      settleConfirmationRef.current(resolveConfirmationResponse(spokenText), spokenText);
    }, confirmationAutoFinalizeMs);
  }, [confirmationAutoFinalizeMs]);

  const startConfirmationRecognition = useCallback(() => {
    if (answerTranscriptionProvider === 'speechmatics_realtime') {
      if (!activeRef.current || modeRef.current !== 'confirmation'
        || phaseRef.current !== 'WAITING_FOR_CONTINUATION' || confirmationHandledRef.current) return;
      const stream = audioCaptureRef.current.getMediaStream();
      if (!stream || !onCreateTranscriptionTicket) {
        enterManualFallback('Không thể mở Speechmatics để nghe phản hồi xác nhận. Nội dung hiện tại vẫn được giữ.');
        return;
      }
      stopRecognition();
      const cycle = confirmationCycleRef.current;
      const provider = createAnswerTranscriptionProvider('speechmatics_realtime', {
        onUpdate: (update) => {
          if (confirmationProviderRef.current !== provider || cycle !== confirmationCycleRef.current
            || confirmationHandledRef.current || modeRef.current !== 'confirmation'
            || phaseRef.current !== 'WAITING_FOR_CONTINUATION') return;
          confirmationCommittedRef.current = update.committedTranscript;
          confirmationDraftRef.current = appendTranscriptPart(
            update.committedTranscript,
            update.interimTranscript,
          );
          setInterimTranscript(confirmationDraftRef.current);
          scheduleConfirmationAutoFinalize();
          if (update.committedTranscript && !update.interimTranscript
            && shouldSettleConfirmationImmediately(update.committedTranscript)) {
            settleConfirmationRef.current(
              resolveConfirmationResponse(update.committedTranscript),
              update.committedTranscript,
            );
          }
        },
        onTerminalError: () => {
          if (confirmationProviderRef.current !== provider || confirmationHandledRef.current) return;
          const spokenText = confirmationDraftRef.current.trim();
          settleConfirmationRef.current(resolveConfirmationResponse(spokenText), spokenText);
        },
      }, {
        recognitionRestartDelayMs,
        createSpeechmaticsTicket: onCreateTranscriptionTicket,
      });
      confirmationProviderRef.current = provider;
      void provider.start(stream, '').catch(() => {
        if (confirmationProviderRef.current !== provider || cycle !== confirmationCycleRef.current) return;
        provider.dispose();
        confirmationProviderRef.current = null;
        enterManualFallback('Không thể khởi động Speechmatics để nghe phản hồi xác nhận. Nội dung hiện tại vẫn được giữ.');
      });
      return;
    }
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition || !activeRef.current || modeRef.current !== 'confirmation'
      || phaseRef.current !== 'WAITING_FOR_CONTINUATION' || confirmationHandledRef.current) return;
    stopRecognition();
    const cycle = confirmationCycleRef.current;
    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = VIETNAMESE_LANG;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      if (cycle !== confirmationCycleRef.current || confirmationHandledRef.current
        || modeRef.current !== 'confirmation' || phaseRef.current !== 'WAITING_FOR_CONTINUATION') return;
      let interim = '';
      let finalChunk = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript?.trim() || '';
        if (result.isFinal) finalChunk += `${text} `;
        else interim += `${text} `;
      }
      if (finalChunk.trim()) {
        confirmationCommittedRef.current = `${confirmationCommittedRef.current} ${finalChunk}`
          .replace(/\s+/g, ' ').trim();
      }
      const cleanInterim = interim.trim();
      confirmationDraftRef.current = `${confirmationCommittedRef.current} ${cleanInterim}`
        .replace(/\s+/g, ' ').trim();
      setInterimTranscript(confirmationDraftRef.current);
      scheduleConfirmationAutoFinalize();
      if (finalChunk.trim() && !cleanInterim) {
        const spokenText = confirmationCommittedRef.current;
        if (shouldSettleConfirmationImmediately(spokenText)) {
          settleConfirmationRef.current(resolveConfirmationResponse(spokenText), spokenText);
        }
      }
    };
    recognition.onerror = (event) => {
      if (!shouldEnterManualFallbackForRecognitionError(event.error)) return;
      enterManualFallback(event.error === 'not-allowed' || event.error === 'service-not-allowed'
        ? 'Trình duyệt không có quyền sử dụng microphone. Nội dung câu trả lời hiện tại vẫn được giữ.'
        : `Nhận dạng phản hồi xác nhận đã dừng (${event.error}). Nội dung hiện tại được giữ để bạn nhập tay.`);
    };
    recognition.onend = () => {
      if (recognitionRef.current === recognition) recognitionRef.current = null;
      if (activeRef.current && cycle === confirmationCycleRef.current
        && modeRef.current === 'confirmation' && phaseRef.current === 'WAITING_FOR_CONTINUATION'
        && !confirmationHandledRef.current && !speakingRef.current) restartRecognition('confirmation');
    };
    recognitionRef.current = recognition;
    try { recognition.start(); } catch {
      enterManualFallback('Không thể nghe phản hồi xác nhận. Nội dung câu trả lời hiện tại vẫn được giữ để bạn nhập tay.');
    }
  }, [answerTranscriptionProvider, enterManualFallback, onCreateTranscriptionTicket, recognitionRestartDelayMs,
    restartRecognition, scheduleConfirmationAutoFinalize, stopRecognition]);
  startConfirmationRecognitionRef.current = startConfirmationRecognition;

  const startConfirmationListening = useCallback(() => {
    if (!activeRef.current || disabledRef.current || savingRef.current) return;
    const cycle = confirmationCycleRef.current;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    modeRef.current = 'confirmation';
    confirmationHandledRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    setInterimTranscript('');
    if (!setPhase('WAITING_FOR_CONTINUATION')) {
      modeRef.current = 'answer';
      confirmationHandledRef.current = true;
      return;
    }
    setTranscriptNotice('Bạn có thể nói “đã xong”, “chưa xong” hoặc tiếp tục câu trả lời. Nếu bạn im lặng, hệ thống sẽ tự hoàn tất.');
    const capture = audioCaptureRef.current;
    void capture.startSegment(segmentsRef.current.length).then(() => {
      if (!activeRef.current || cycle !== confirmationCycleRef.current
        || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current
        || capture !== audioCaptureRef.current
        || phaseRef.current !== 'WAITING_FOR_CONTINUATION' || confirmationHandledRef.current
        || modeRef.current !== 'confirmation') {
        capture.abortSegment();
        return;
      }
      confirmationSegmentActiveRef.current = true;
      startConfirmationRecognitionRef.current();
      scheduleConfirmationAutoFinalize();
    }).catch(() => {
      if (!activeRef.current || capture !== audioCaptureRef.current
        || cycle !== confirmationCycleRef.current || token !== lifecycleTokenRef.current
        || expectedQuestion !== questionIdRef.current) return;
      confirmationSegmentActiveRef.current = false;
      enterManualFallback('Không thể ghi phản hồi xác nhận từ microphone. Câu trả lời hiện tại vẫn được giữ để bạn nhập tay.');
    });
  }, [enterManualFallback, scheduleConfirmationAutoFinalize, setPhase]);
  startConfirmationListeningRef.current = startConfirmationListening;

  const askForConfirmation = useCallback(() => {
    if (!activeRef.current || disabledRef.current || savingRef.current || speakingRef.current
      || modeRef.current !== 'answer' || phaseRef.current !== 'LISTENING'
      || !browserDraftRef.current.trim()) return;
    window.clearTimeout(confirmationPromptTimerRef.current);
    const cycle = confirmationCycleRef.current + 1;
    confirmationCycleRef.current = cycle;
    confirmationHandledRef.current = false;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    if (!setPhase('AI_SPEAKING')) return;
    // Web Speech may still expose the latest answer only as an interim result. Once recognition
    // is stopped to ask for confirmation, that interim result will never become final, so promote
    // the complete visible draft before switching modes. Otherwise a continued answer would be
    // appended to the older committed buffer and the visible part could disappear.
    const provider = answerProviderRef.current;
    const preservedTranscript = provider
      ? provider.snapshotTranscript()
      : preserveTranscriptBeforeRecognitionSwitch(
        browserCommittedRef.current,
        browserDraftRef.current,
      );
    browserCommittedRef.current = preservedTranscript;
    browserDraftRef.current = preservedTranscript;
    onTranscriptRef.current(preservedTranscript);
    provider?.pause();
    stopRecognition();
    setInterimTranscript('');
    setTranscriptNotice(CONFIRMATION_PROMPT);
    void audioCaptureRef.current.stopSegment().catch(() => null).then((segment) => {
      if (!activeRef.current || cycle !== confirmationCycleRef.current
        || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current
        || phaseRef.current !== 'AI_SPEAKING') return;
      if (segment && segment.sequence === segmentsRef.current.length) segmentsRef.current.push(segment);
      speak(CONFIRMATION_PROMPT, 'AI_SPEAKING', () => startConfirmationListeningRef.current());
    });
  }, [setPhase, speak, stopRecognition]);
  askForConfirmationRef.current = askForConfirmation;

  const startAnswerRecognition = useCallback(() => {
    if (!activeRef.current || phaseRef.current !== 'LISTENING') return;
    modeRef.current = 'answer';
    const existing = answerProviderRef.current;
    if (existing) {
      void existing.resume().catch(() => {
        setError('Không thể tiếp tục nguồn transcript hiện tại. Nội dung đã nhận vẫn được giữ lại.');
        finalizeAnswerRef.current();
      });
      return;
    }

    const stream = audioCaptureRef.current.getMediaStream();
    if (!stream) {
      enterManualFallback('Không thể dùng microphone cho nhận dạng giọng nói. Hãy nhập transcript thủ công.');
      return;
    }
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    const applyUpdate = (update: AnswerTranscriptionUpdate) => {
      if (!activeRef.current || token !== lifecycleTokenRef.current
        || expectedQuestion !== questionIdRef.current || modeRef.current !== 'answer') return;
      browserCommittedRef.current = update.committedTranscript;
      browserDraftRef.current = appendTranscriptPart(update.committedTranscript, update.interimTranscript);
      setInterimTranscript(update.interimTranscript);
      onTranscriptRef.current(browserDraftRef.current);
      if (phaseRef.current === 'LISTENING' && shouldScheduleConfirmationPrompt(browserDraftRef.current)) {
        window.clearTimeout(confirmationPromptTimerRef.current);
        confirmationPromptTimerRef.current = window.setTimeout(
          () => askForConfirmationRef.current(),
          confirmationPromptDelayMs,
        );
      }
    };
    const onTerminalError = (message: string) => {
      if (!activeRef.current || token !== lifecycleTokenRef.current
        || expectedQuestion !== questionIdRef.current) return;
      setError(message);
      setTranscriptNotice('Nhận dạng giọng nói đã dừng. Đang giữ transcript đã nhận để bạn kiểm tra.');
      if (phaseRef.current === 'LISTENING') finalizeAnswerRef.current();
    };
    const provider = createAnswerTranscriptionProvider(
      answerTranscriptionProvider,
      { onUpdate: applyUpdate, onTerminalError },
      {
        recognitionRestartDelayMs,
        createSpeechmaticsTicket: onCreateTranscriptionTicket,
      },
    );
    answerProviderRef.current = provider;
    void provider.start(stream, browserCommittedRef.current).catch(() => {
      if (answerProviderRef.current !== provider || token !== lifecycleTokenRef.current
        || expectedQuestion !== questionIdRef.current) return;
      provider.dispose();
      answerProviderRef.current = null;
      enterManualFallback('Không thể khởi động nhận dạng giọng nói. Hãy nhập transcript thủ công hoặc thử microphone lại.');
    });
  }, [answerTranscriptionProvider, confirmationPromptDelayMs, enterManualFallback,
    onCreateTranscriptionTicket, recognitionRestartDelayMs]);
  startAnswerRecognitionRef.current = startAnswerRecognition;

  const startAnswerListening = useCallback((schedulePromptWithoutNewResult = false) => {
    if (!supported || !canStartCandidateCapture(activeRef.current, speakingRef.current, disabledRef.current)) return;
    if (!setPhase('LISTENING')) return;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    const capture = audioCaptureRef.current;
    let captureStarted = false;
    setError('');
    setManualFallback(false);
    modeRef.current = 'answer';
    confirmationHandledRef.current = true;
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    void capture.startSegment(segmentsRef.current.length)
      .then(() => {
        captureStarted = true;
        captureAvailableRef.current = true;
      })
      .catch(() => {
        if (!activeRef.current || capture !== audioCaptureRef.current
          || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current) return;
        captureAvailableRef.current = false;
        enterManualFallback('Không thể ghi audio từ microphone. Hãy nhập transcript thủ công hoặc kiểm tra quyền rồi thử lại.');
      })
      .finally(() => {
        if (!activeRef.current || capture !== audioCaptureRef.current
          || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current) {
          capture.abortSegment();
          return;
        }
        if (!captureStarted || !captureAvailableRef.current || phaseRef.current !== 'LISTENING') return;
        startAnswerRecognition();
        if (schedulePromptWithoutNewResult && shouldScheduleConfirmationPrompt(browserDraftRef.current)) {
          window.clearTimeout(confirmationPromptTimerRef.current);
          confirmationPromptTimerRef.current = window.setTimeout(
            () => askForConfirmationRef.current(),
            confirmationPromptDelayMs,
          );
        }
      });
  }, [confirmationPromptDelayMs, enterManualFallback, setPhase, startAnswerRecognition, supported]);
  startAnswerListeningRef.current = startAnswerListening;

  const finalizeAnswer = useCallback(() => {
    if (!activeRef.current || !canFinalizeSpokenAnswer(phaseRef.current, savingRef.current)) return;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    const expectedCapture = captureIdRef.current;
    window.clearTimeout(confirmationPromptTimerRef.current);
    window.clearTimeout(confirmationAutoFinalizeTimerRef.current);
    confirmationHandledRef.current = true;
    modeRef.current = 'answer';
    setPhase('PROCESSING_AUDIO');
    stopRecognition();
    setInterimTranscript('');
    const provider = answerProviderRef.current;
    answerProviderRef.current = null;
    const finishTranscription = provider
      ? provider.finish().catch(() => ({
        source: answerTranscriptionProvider,
        transcript: browserDraftRef.current.trim(),
      })).finally(() => provider.dispose())
      : Promise.resolve({
        source: answerTranscriptionProvider,
        transcript: browserDraftRef.current.trim(),
      });
    void Promise.all([
      audioCaptureRef.current.stopSegment().catch(() => null),
      finishTranscription,
    ]).then(([segment, transcription]) => {
      if (segment && segment.sequence === segmentsRef.current.length) segmentsRef.current.push(segment);
      const callbackExpected = { token, questionId: expectedQuestion, captureId: expectedCapture };
      const currentLifecycle = () => ({ active: activeRef.current, token: lifecycleTokenRef.current,
        questionId: questionIdRef.current, captureId: captureIdRef.current });
      if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
      const browserTranscript = (transcription.transcript || browserDraftRef.current).trim();
      browserCommittedRef.current = browserTranscript;
      browserDraftRef.current = browserTranscript;
      onTranscriptRef.current(browserTranscript);
      if (!segmentsRef.current.length) {
        rawTranscriptRef.current = browserTranscript;
        setRawTranscript(browserTranscript);
        confirmedTranscriptRef.current = browserTranscript;
        setTranscriptNotice('Không thể xử lý audio. Transcript realtime vẫn được giữ lại.');
        onTranscriptRef.current(browserTranscript);
        setPhase('REVIEWING_TRANSCRIPT');
        return;
      }
      const captureVersion = captureVersionRef.current + 1;
      setTranscriptNotice('Đang hoàn tất audio...');
      setPhase('PROCESSING_AUDIO');
      const upload = () => onFinalizeCaptureRef.current(
        segmentsRef.current,
        expectedCapture,
        captureVersion,
        browserTranscript,
        transcription.source,
      );
      let transcriptResolved = false;
      void upload().catch((firstError) => {
        if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle())) {
          throw firstError;
        }
        return upload();
      }).then((result) => {
        if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle(), result)) return;
        const resolved = resolveCaptureTranscript(browserTranscript, result);
        captureVersionRef.current = result.captureVersion;
        transcriptResolved = true;
        gladiaTranscriptRef.current = resolved.gladiaTranscript;
        rawTranscriptRef.current = resolved.rawTranscript;
        setRawTranscript(resolved.rawTranscript);
        confirmedTranscriptRef.current = resolved.displayedTranscript;
        onTranscriptRef.current(resolved.displayedTranscript);
        setTranscriptNotice(resolved.notice);
      }).catch(() => {
        if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
        const resolved = resolveCaptureTranscript(browserTranscript);
        transcriptResolved = true;
        rawTranscriptRef.current = resolved.rawTranscript;
        setRawTranscript(resolved.rawTranscript);
        confirmedTranscriptRef.current = resolved.displayedTranscript;
        onTranscriptRef.current(resolved.displayedTranscript);
        setTranscriptNotice(resolved.notice);
      }).finally(() => {
        if (!transcriptResolved || !isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
        setPhase('REVIEWING_TRANSCRIPT');
      });
    });
  }, [answerTranscriptionProvider, setPhase, stopRecognition]);
  finalizeAnswerRef.current = finalizeAnswer;

  const completeSpokenAnswer = useCallback(() => {
    if (!canFinalizeSpokenAnswer(phaseRef.current, savingRef.current)) return;
    if (modeRef.current === 'confirmation') {
      settleConfirmationRef.current('DONE');
      return;
    }
    finalizeAnswerRef.current();
  }, []);

  const continueAnswer = useCallback((reviewedTranscript?: string) => {
    if (!canContinueReviewedAnswer(phaseRef.current, savingRef.current)) return;
    if (typeof reviewedTranscript === 'string') {
      const preserved = reviewedTranscript.trim();
      browserCommittedRef.current = preserved;
      browserDraftRef.current = preserved;
      confirmedTranscriptRef.current = preserved;
      onTranscriptRef.current(preserved);
    }
    setTranscriptNotice('Đang nghe phần trả lời tiếp theo...');
    activeRef.current = true;
    setActive(true);
    startAnswerListeningRef.current();
  }, []);

  const confirmTranscript = useCallback((value: string) => {
    const answer = value.trim();
    const canConfirm = phaseRef.current === 'REVIEWING_TRANSCRIPT'
      || phaseRef.current === 'ERROR_RECOVERABLE'
      || (manualFallback && phaseRef.current === 'LISTENING');
    if (!canConfirm || !canSubmitConfirmation(savingRef.current, answer)) return;
    savingRef.current = true;
    confirmedTranscriptRef.current = answer;
    setPhase('ANSWER_CONFIRMED');
    void onConfirmRef.current(answer, rawTranscriptRef.current || undefined).then(() => {
      setPhase('NEXT_QUESTION');
      setTranscriptNotice('Được rồi. Câu trả lời đã được lưu.');
    }).catch((confirmError) => {
      setPhase('ERROR_RECOVERABLE');
      setError(confirmError instanceof Error ? confirmError.message : 'Không thể lưu câu trả lời. Nội dung vẫn được giữ để thử lại.');
    }).finally(() => { savingRef.current = false; });
  }, [manualFallback, setPhase]);

  const speakQuestion = useCallback(() => {
    const text = questionTextRef.current;
    if (!text || !activeRef.current) return;
    clearTimers();
    speak(text, 'AI_SPEAKING', () => startAnswerListeningRef.current());
  }, [clearTimers, speak]);

  const replayQuestion = useCallback(async () => {
    if (!activeRef.current || disabledRef.current || savingRef.current) return;
    if (['PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'AI_SPEAKING'].includes(phaseRef.current)) return;
    const text = questionTextRef.current;
    if (!text || !questionIdRef.current) return;
    const resumeManualFallback = manualFallback;
    const discardCurrentSegment = modeRef.current === 'confirmation'
      || confirmationSegmentActiveRef.current;
    const token = ++lifecycleTokenRef.current;
    speechTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    window.speechSynthesis?.cancel();
    setError('');
    setPhase('AI_SPEAKING');
    const segment = await audioCaptureRef.current.stopSegment().catch(() => null);
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    modeRef.current = 'answer';
    if (!discardCurrentSegment && segment && segment.sequence === segmentsRef.current.length) {
      segmentsRef.current.push(segment);
    }
    if (!activeRef.current || token !== lifecycleTokenRef.current || disabledRef.current) return;
    speak(text, 'AI_SPEAKING', () => {
      if (resumeManualFallback) {
        setPhase(rawTranscriptRef.current || browserDraftRef.current
          ? 'REVIEWING_TRANSCRIPT' : 'LISTENING');
      } else startAnswerListeningRef.current();
    }, () => {
      void onReplayQuestionRef.current().catch((replayError) => {
        if (token !== lifecycleTokenRef.current) return;
        setError(replayError instanceof Error
          ? replayError.message
          : 'Không thể ghi nhận lần đọc lại. Phỏng vấn vẫn tiếp tục.');
      });
    });
  }, [clearTimers, disposeAnswerProvider, manualFallback, setPhase, speak, stopAudio, stopRecognition]);

  const retryVoice = useCallback(() => {
    if (!supported || disabledRef.current) return;
    lifecycleTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    pcmPlayerRef.current?.dispose();
    pcmPlayerRef.current = null;
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    modeRef.current = 'answer';
    captureAvailableRef.current = true;
    setManualFallback(false);
    activeRef.current = true;
    setActive(true);
    setError('');
    setTranscriptNotice('');
    startAnswerListeningRef.current();
  }, [clearTimers, disposeAnswerProvider, stopAudio, stopRecognition, supported]);

  const useManualFallback = useCallback(() => {
    enterManualFallback('Bạn đang nhập transcript thủ công. Nội dung đã có vẫn được giữ nguyên.');
  }, [enterManualFallback]);

  const start = useCallback(() => {
    if (!supported || !questionIdRef.current || !questionTextRef.current || disabledRef.current) return;
    setError('');
    setTranscriptNotice('');
    activeRef.current = true;
    setActive(true);
    if (phaseRef.current === 'REVIEWING_TRANSCRIPT') return;
    speakQuestion();
  }, [speakQuestion, supported]);

  const stop = useCallback(() => {
    activeRef.current = false;
    setActive(false);
    speakingRef.current = false;
    savingRef.current = false;
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    pcmPlayerRef.current?.dispose();
    pcmPlayerRef.current = null;
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    modeRef.current = 'answer';
    window.speechSynthesis?.cancel();
    setPhase('IDLE');
  }, [clearTimers, disposeAnswerProvider, setPhase, stopAudio, stopRecognition]);

  useEffect(() => {
    const previousQuestion = questionIdRef.current;
    const continueManually = manualFallback;
    questionIdRef.current = questionId;
    questionTextRef.current = questionText;
    if (previousQuestion === questionId) return;
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    confirmationSegmentActiveRef.current = false;
    confirmationCommittedRef.current = '';
    confirmationDraftRef.current = '';
    modeRef.current = 'answer';
    captureIdRef.current = newCaptureId();
    captureVersionRef.current = 0;
    segmentsRef.current = [];
    browserCommittedRef.current = initialTranscript.trim();
    browserDraftRef.current = initialTranscript.trim();
    confirmedTranscriptRef.current = initialTranscript.trim();
    rawTranscriptRef.current = initialRawTranscript?.trim() || '';
    setRawTranscript(rawTranscriptRef.current);
    gladiaTranscriptRef.current = '';
    setInterimTranscript('');
    setTranscriptNotice('');
    onTranscriptRef.current(initialTranscript.trim());
    window.speechSynthesis?.cancel();
    setPhase('NEXT_QUESTION');
    if (!activeRef.current) return;
    if (questionId && questionText) {
      if (continueManually) {
        const timer = window.setTimeout(() => {
          speak(questionText, 'AI_SPEAKING', () => setPhase('LISTENING'));
        }, nextQuestionDelayMs);
        return () => window.clearTimeout(timer);
      }
      const timer = window.setTimeout(speakQuestion, nextQuestionDelayMs);
      return () => window.clearTimeout(timer);
    }
    setTranscriptNotice('Bạn đã hoàn thành các câu hỏi. Hãy bấm kết thúc phỏng vấn để nhận điểm và nhận xét.');
    return undefined;
  }, [clearTimers, disposeAnswerProvider, initialRawTranscript, initialTranscript, manualFallback, nextQuestionDelayMs,
    questionId, questionText, setPhase, speak, speakQuestion, stopAudio, stopRecognition]);

  useEffect(() => () => {
    activeRef.current = false;
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    confirmationCycleRef.current += 1;
    confirmationHandledRef.current = true;
    clearTimers();
    stopRecognition();
    disposeAnswerProvider();
    stopAudio();
    pcmPlayerRef.current?.dispose();
    pcmPlayerRef.current = null;
    audioCaptureRef.current.dispose();
    window.speechSynthesis?.cancel();
  }, [clearTimers, disposeAnswerProvider, stopAudio, stopRecognition]);

  return {
    supported,
    active,
    phase,
    manualFallback,
    rawTranscript,
    interimTranscript,
    transcriptNotice,
    error,
    start,
    stop,
    replayQuestion,
    done: completeSpokenAnswer,
    continueAnswer,
    confirmTranscript,
    retryVoice,
    useManualFallback,
  };
}
