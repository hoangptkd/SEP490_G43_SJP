import { useCallback, useEffect, useRef, useState } from 'react';
import type { HandsFreeAnswerCaptureResult, HandsFreeAudioSegmentUpload } from '../types/aiInterview';
import { CandidateAudioCapture, type CapturedAudioSegment } from './handsFreeAudioCapture';

export type VoicePhase =
  | 'IDLE'
  | 'QUESTION_PLAYING'
  | 'STARTING_CAPTURE'
  | 'LISTENING'
  | 'FINALIZING_AUDIO'
  | 'TRANSCRIBING'
  | 'TRANSCRIPT_READY'
  | 'CONFIRMING'
  | 'CONTINUING'
  | 'SUBMITTING'
  | 'COMPLETED'
  | 'ERROR_RECOVERABLE';

export type ConfirmationIntent = 'positive' | 'negative' | 'unknown';

interface VoiceConversationOptions {
  questionId?: string;
  questionText?: string;
  initialTranscript: string;
  silenceMs: number;
  confirmationSilenceMs: number;
  unclearConfirmationDelayMs: number;
  disabled?: boolean;
  onTranscript: (value: string) => void;
  onFinalizeCapture: (
    segments: HandsFreeAudioSegmentUpload[],
    captureId: string,
    captureVersion: number,
    browserTranscript: string,
  ) => Promise<HandsFreeAnswerCaptureResult>;
  onConfirm: (value: string) => Promise<void>;
  onSpeechUrl?: (text: string) => Promise<string | undefined>;
}

type RecognitionMode = 'answer' | 'confirmation';
const RESTART_DELAY_MS = 250;
const VOICE_LOAD_WAIT_MS = 700;
const TRANSCRIPT_READY_DELAY_MS = 350;
const VIETNAMESE_LANG = 'vi-VN';

export function pickVietnameseVoice(voices: SpeechSynthesisVoice[]) {
  return voices.find((voice) => voice.lang?.toLowerCase() === 'vi-vn')
    || voices.find((voice) => voice.lang?.toLowerCase().startsWith('vi'))
    || voices.find((voice) => {
      const searchable = `${voice.name || ''} ${voice.lang || ''}`.toLowerCase();
      return searchable.includes('vietnamese') || searchable.includes('việt nam') || searchable.includes('tiếng việt');
    });
}

function normalizeSpeech(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd')
    .toLowerCase().replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}

export function classifyConfirmation(value: string): ConfirmationIntent {
  const normalized = normalizeSpeech(value);
  if (!normalized) return 'unknown';
  const words = normalized.split(' ');
  const negativePhrases = [
    'khong xong', 'khong dau', 'khong phai', 'chua xong', 'chua dau', 'chua tra loi xong',
    'toi chua xong', 'em chua xong', 'minh chua xong', 'toi muon noi them', 'em muon noi them',
    'minh muon noi them', 'muon noi them', 'noi them', 'tiep tuc',
  ];
  if (normalized === 'khong' || words.includes('chua')
    || negativePhrases.some((phrase) => normalized === phrase || normalized.includes(phrase))) return 'negative';
  const positivePhrases = [
    'toi da tra loi xong', 'da tra loi xong', 'tra loi xong', 'toi da xong', 'toi xong roi',
    'em da xong', 'em xong roi', 'em xong', 'minh da xong', 'minh xong roi', 'minh xong',
    'da xong', 'da xong roi', 'da song', 'da song roi', 'song roi', 'xong roi', 'hoan thanh',
    'da hoan thanh', 'ok xong', 'okay xong', 'da roi', 'duoc roi', 'dung roi', 'dong y',
  ];
  return normalized === 'xong' || normalized === 'song' || normalized === 'roi' || normalized === 'co'
    || words.includes('xong') || words.includes('song')
    || positivePhrases.some((phrase) => normalized === phrase || normalized.includes(phrase))
    ? 'positive' : 'unknown';
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
  result?: Pick<HandsFreeAnswerCaptureResult, 'finalTranscript' | 'gladiaTranscript' | 'transcriptStatus'>,
) {
  if (result?.finalTranscript?.trim()) {
    return {
      displayedTranscript: result.finalTranscript.trim(),
      gladiaTranscript: result.gladiaTranscript?.trim() || '',
      notice: result.transcriptStatus === 'standardized'
        ? 'Đã chuẩn hóa' : 'Không thể chuẩn hóa, sử dụng bản ghi nhận realtime',
    };
  }
  return {
    displayedTranscript: browserTranscript.trim(),
    gladiaTranscript: '',
    notice: 'Không thể chuẩn hóa, sử dụng bản ghi nhận realtime',
  };
}

export function canSubmitConfirmation(saving: boolean, transcript: string) {
  return !saving && Boolean(transcript.trim());
}

export function useVoiceConversation({
  questionId,
  questionText,
  initialTranscript,
  silenceMs,
  confirmationSilenceMs,
  unclearConfirmationDelayMs,
  disabled = false,
  onTranscript,
  onFinalizeCapture,
  onConfirm,
  onSpeechUrl,
}: VoiceConversationOptions) {
  const canUseBrowserSpeech = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const supported = typeof window !== 'undefined'
    && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)
    && (canUseBrowserSpeech || Boolean(onSpeechUrl));
  const [active, setActive] = useState(false);
  const [phase, setPhaseState] = useState<VoicePhase>('IDLE');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [transcriptNotice, setTranscriptNotice] = useState('');
  const [error, setError] = useState('');

  const phaseRef = useRef<VoicePhase>('IDLE');
  const activeRef = useRef(false);
  const disabledRef = useRef(disabled);
  const speakingRef = useRef(false);
  const savingRef = useRef(false);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioCaptureRef = useRef(new CandidateAudioCapture());
  const captureAvailableRef = useRef(true);
  const captureIdRef = useRef(newCaptureId());
  const captureVersionRef = useRef(0);
  const segmentsRef = useRef<CapturedAudioSegment[]>([]);
  const modeRef = useRef<RecognitionMode>('answer');
  const browserCommittedRef = useRef(initialTranscript.trim());
  const browserDraftRef = useRef(initialTranscript.trim());
  const confirmedTranscriptRef = useRef(initialTranscript.trim());
  const gladiaTranscriptRef = useRef('');
  const confirmationTranscriptRef = useRef('');
  const questionIdRef = useRef(questionId);
  const questionTextRef = useRef(questionText);
  const lifecycleTokenRef = useRef(0);
  const speechTokenRef = useRef(0);
  const answerSilenceTimerRef = useRef<number>();
  const confirmationTimerRef = useRef<number>();
  const restartTimerRef = useRef<number>();
  const readyTimerRef = useRef<number>();
  const voiceLoadTimerRef = useRef<number>();
  const voiceLoadCleanupRef = useRef<() => void>(() => undefined);
  const onTranscriptRef = useRef(onTranscript);
  const onFinalizeCaptureRef = useRef(onFinalizeCapture);
  const onConfirmRef = useRef(onConfirm);
  const onSpeechUrlRef = useRef(onSpeechUrl);
  const startAnswerListeningRef = useRef<() => void>(() => undefined);
  const startAnswerRecognitionRef = useRef<() => void>(() => undefined);
  const startConfirmationRecognitionRef = useRef<() => void>(() => undefined);
  const finalizeAnswerRef = useRef<() => void>(() => undefined);
  const askConfirmationRef = useRef<(clarify?: boolean) => void>(() => undefined);
  const handleConfirmationRef = useRef<(intent: ConfirmationIntent) => void>(() => undefined);

  const setPhase = useCallback((next: VoicePhase) => {
    phaseRef.current = next;
    setPhaseState(next);
  }, []);

  useEffect(() => { onTranscriptRef.current = onTranscript; }, [onTranscript]);
  useEffect(() => { onFinalizeCaptureRef.current = onFinalizeCapture; }, [onFinalizeCapture]);
  useEffect(() => { onConfirmRef.current = onConfirm; }, [onConfirm]);
  useEffect(() => { onSpeechUrlRef.current = onSpeechUrl; }, [onSpeechUrl]);
  useEffect(() => { disabledRef.current = disabled; }, [disabled]);

  const clearTimers = useCallback(() => {
    window.clearTimeout(answerSilenceTimerRef.current);
    window.clearTimeout(confirmationTimerRef.current);
    window.clearTimeout(restartTimerRef.current);
    window.clearTimeout(readyTimerRef.current);
    window.clearTimeout(voiceLoadTimerRef.current);
    voiceLoadCleanupRef.current();
    voiceLoadCleanupRef.current = () => undefined;
  }, []);

  const stopRecognition = useCallback(() => {
    const recognition = recognitionRef.current;
    recognitionRef.current = null;
    if (!recognition) return;
    try { recognition.abort(); } catch { /* already stopped */ }
  }, []);

  const stopAudio = useCallback(() => {
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

  const speak = useCallback((text: string, nextPhase: VoicePhase, onDone: () => void) => {
    if (!supported || !activeRef.current || disabledRef.current) return;
    // Safety barrier: system TTS must never overlap candidate capture.
    if (audioCaptureRef.current.recording) audioCaptureRef.current.abortSegment();
    speakingRef.current = true;
    stopRecognition();
    stopAudio();
    window.speechSynthesis.cancel();
    const speechToken = ++speechTokenRef.current;
    const finish = () => {
      if (speechTokenRef.current !== speechToken) return;
      speakingRef.current = false;
      stopAudio();
      if (activeRef.current && !disabledRef.current) onDone();
    };
    const browserSpeech = () => {
      if (speechTokenRef.current !== speechToken) return;
      if (!canUseBrowserSpeech) return finish();
      const utterance = new SpeechSynthesisUtterance(text);
      const voice = pickVietnameseVoice(window.speechSynthesis.getVoices());
      if (voice) utterance.voice = voice;
      utterance.lang = voice?.lang || VIETNAMESE_LANG;
      utterance.rate = 0.88;
      utterance.onstart = () => { if (speechTokenRef.current === speechToken) setPhase(nextPhase); };
      utterance.onend = finish;
      utterance.onerror = finish;
      window.speechSynthesis.speak(utterance);
    };
    const resolver = onSpeechUrlRef.current;
    if (resolver) {
      void resolver(text).then((url) => {
        if (speechTokenRef.current !== speechToken || !activeRef.current) return;
        if (!url) return browserSpeech();
        const audio = new Audio(url);
        audioRef.current = audio;
        audio.onplaying = () => { if (speechTokenRef.current === speechToken) setPhase(nextPhase); };
        audio.onended = finish;
        audio.onerror = browserSpeech;
        void audio.play().catch(browserSpeech);
      }).catch(browserSpeech);
      return;
    }
    if (window.speechSynthesis.getVoices().length) return browserSpeech();
    const startOnce = () => {
      window.clearTimeout(voiceLoadTimerRef.current);
      voiceLoadCleanupRef.current();
      voiceLoadCleanupRef.current = () => undefined;
      browserSpeech();
    };
    voiceLoadCleanupRef.current = () => window.speechSynthesis.removeEventListener('voiceschanged', startOnce);
    window.speechSynthesis.addEventListener('voiceschanged', startOnce, { once: true });
    voiceLoadTimerRef.current = window.setTimeout(startOnce, VOICE_LOAD_WAIT_MS);
  }, [canUseBrowserSpeech, setPhase, stopAudio, stopRecognition, supported]);

  const restartRecognition = useCallback((mode: RecognitionMode) => {
    window.clearTimeout(restartTimerRef.current);
    restartTimerRef.current = window.setTimeout(() => {
      if (!activeRef.current || speakingRef.current || savingRef.current || disabledRef.current) return;
      if (modeRef.current !== mode) return;
      if (mode === 'answer' && phaseRef.current === 'LISTENING') startAnswerRecognitionRef.current();
      if (mode === 'confirmation' && phaseRef.current === 'CONFIRMING') startConfirmationRecognitionRef.current();
    }, RESTART_DELAY_MS);
  }, []);

  const startAnswerRecognition = useCallback(() => {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition || !activeRef.current || phaseRef.current !== 'LISTENING') return;
    stopRecognition();
    modeRef.current = 'answer';
    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = VIETNAMESE_LANG;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      if (phaseRef.current !== 'LISTENING') return;
      let interim = '';
      let finalChunk = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript?.trim() || '';
        if (result.isFinal) finalChunk += `${text} `;
        else interim += `${text} `;
      }
      if (finalChunk.trim()) browserCommittedRef.current = `${browserCommittedRef.current} ${finalChunk}`.replace(/\s+/g, ' ').trim();
      const cleanInterim = interim.trim();
      browserDraftRef.current = `${browserCommittedRef.current} ${cleanInterim}`.replace(/\s+/g, ' ').trim();
      setInterimTranscript(cleanInterim);
      onTranscriptRef.current(browserDraftRef.current);
      if (browserDraftRef.current) {
        window.clearTimeout(answerSilenceTimerRef.current);
        answerSilenceTimerRef.current = window.setTimeout(() => finalizeAnswerRef.current(), Math.max(1_000, silenceMs));
      }
    };
    recognition.onerror = (event) => {
      if (event.error === 'aborted' || event.error === 'no-speech') return;
      const permissionDenied = event.error === 'not-allowed' || event.error === 'service-not-allowed';
      setError(permissionDenied
        ? 'Web Speech không có quyền microphone; hệ thống sẽ ưu tiên audio MediaRecorder nếu còn khả dụng.'
        : `Nhận dạng realtime tạm dừng (${event.error}); audio vẫn đang được ghi.`);
      if (captureAvailableRef.current && phaseRef.current === 'LISTENING') {
        window.clearTimeout(answerSilenceTimerRef.current);
        answerSilenceTimerRef.current = window.setTimeout(() => finalizeAnswerRef.current(), Math.max(1_000, silenceMs));
      }
    };
    recognition.onend = () => {
      if (recognitionRef.current === recognition) recognitionRef.current = null;
      if (activeRef.current && phaseRef.current === 'LISTENING' && !speakingRef.current) restartRecognition('answer');
    };
    recognitionRef.current = recognition;
    try { recognition.start(); } catch {
      setError('Không thể khởi động transcript realtime; audio vẫn được ghi để Gladia xử lý.');
    }
  }, [restartRecognition, silenceMs, stopRecognition]);
  startAnswerRecognitionRef.current = startAnswerRecognition;

  const startAnswerListening = useCallback(() => {
    if (!supported || !canStartCandidateCapture(activeRef.current, speakingRef.current, disabledRef.current)) return;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    setError('');
    setTranscriptNotice('');
    setPhase('STARTING_CAPTURE');
    void audioCaptureRef.current.startSegment(segmentsRef.current.length)
      .then(() => { captureAvailableRef.current = true; })
      .catch(() => {
        captureAvailableRef.current = false;
        setError('Không thể ghi audio. Phỏng vấn vẫn tiếp tục bằng transcript realtime.');
      })
      .finally(() => {
        if (!activeRef.current || token !== lifecycleTokenRef.current || expectedQuestion !== questionIdRef.current) {
          audioCaptureRef.current.abortSegment();
          return;
        }
        setPhase('LISTENING');
        startAnswerRecognition();
      });
  }, [setPhase, startAnswerRecognition, supported]);
  startAnswerListeningRef.current = startAnswerListening;

  const askConfirmation = useCallback((clarify = false) => {
    if (!activeRef.current || savingRef.current || audioCaptureRef.current.recording) return;
    window.clearTimeout(answerSilenceTimerRef.current);
    stopRecognition();
    confirmationTranscriptRef.current = '';
    speak(clarify ? 'Mình chưa nghe rõ. Bạn hãy nói đã xong hoặc chưa xong.' : 'Bạn đã trả lời xong chưa?',
      'CONFIRMING', () => startConfirmationRecognitionRef.current());
  }, [speak, stopRecognition]);
  askConfirmationRef.current = askConfirmation;

  const finalizeAnswer = useCallback(() => {
    if (!activeRef.current || phaseRef.current !== 'LISTENING') return;
    const token = lifecycleTokenRef.current;
    const expectedQuestion = questionIdRef.current;
    const expectedCapture = captureIdRef.current;
    setPhase('FINALIZING_AUDIO');
    stopRecognition();
    setInterimTranscript('');
    void audioCaptureRef.current.stopSegment().catch(() => null).then((segment) => {
      if (segment && segment.sequence === segmentsRef.current.length) segmentsRef.current.push(segment);
      const callbackExpected = { token, questionId: expectedQuestion, captureId: expectedCapture };
      const currentLifecycle = () => ({ active: activeRef.current, token: lifecycleTokenRef.current,
        questionId: questionIdRef.current, captureId: captureIdRef.current });
      if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
      const browserTranscript = browserDraftRef.current.trim();
      if (!segmentsRef.current.length) {
        confirmedTranscriptRef.current = browserTranscript;
        setTranscriptNotice('Không thể chuẩn hóa, sử dụng bản ghi nhận realtime');
        setPhase('TRANSCRIPT_READY');
        readyTimerRef.current = window.setTimeout(() => askConfirmationRef.current(false), TRANSCRIPT_READY_DELAY_MS);
        return;
      }
      const captureVersion = captureVersionRef.current + 1;
      setTranscriptNotice('Đang chuẩn hóa câu trả lời...');
      setPhase('TRANSCRIBING');
      const upload = () => onFinalizeCaptureRef.current(
        segmentsRef.current, expectedCapture, captureVersion, browserTranscript);
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
        confirmedTranscriptRef.current = resolved.displayedTranscript;
        onTranscriptRef.current(resolved.displayedTranscript);
        setTranscriptNotice(resolved.notice);
      }).catch(() => {
        if (!isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
        const resolved = resolveCaptureTranscript(browserTranscript);
        transcriptResolved = true;
        confirmedTranscriptRef.current = resolved.displayedTranscript;
        onTranscriptRef.current(resolved.displayedTranscript);
        setTranscriptNotice(resolved.notice);
      }).finally(() => {
        if (!transcriptResolved || !isCurrentCaptureCallback(callbackExpected, currentLifecycle())) return;
        setPhase('TRANSCRIPT_READY');
        readyTimerRef.current = window.setTimeout(() => askConfirmationRef.current(false), TRANSCRIPT_READY_DELAY_MS);
      });
    });
  }, [setPhase, stopRecognition]);
  finalizeAnswerRef.current = finalizeAnswer;

  const handleConfirmation = useCallback((intent: ConfirmationIntent) => {
    window.clearTimeout(confirmationTimerRef.current);
    stopRecognition();
    if (intent === 'negative') {
      setPhase('CONTINUING');
      confirmationTranscriptRef.current = '';
      speak('Được, bạn hãy tiếp tục câu trả lời.', 'CONTINUING', () => startAnswerListeningRef.current());
      return;
    }
    if (intent === 'unknown') {
      askConfirmationRef.current(true);
      return;
    }
    const answer = confirmedTranscriptRef.current.trim() || browserDraftRef.current.trim();
    if (!canSubmitConfirmation(savingRef.current, answer)) {
      speak('Mình chưa nghe thấy câu trả lời. Bạn hãy trả lời câu hỏi trước nhé.', 'CONTINUING',
        () => startAnswerListeningRef.current());
      return;
    }
    savingRef.current = true;
    setPhase('SUBMITTING');
    void onConfirmRef.current(answer).catch((confirmError) => {
      setPhase('ERROR_RECOVERABLE');
      setError(confirmError instanceof Error ? confirmError.message : 'Không thể lưu câu trả lời. Nội dung vẫn được giữ để thử lại.');
    }).finally(() => { savingRef.current = false; });
  }, [setPhase, speak, stopRecognition]);
  handleConfirmationRef.current = handleConfirmation;

  const startConfirmationRecognition = useCallback(() => {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition || !activeRef.current || phaseRef.current !== 'CONFIRMING') return;
    stopRecognition();
    modeRef.current = 'confirmation';
    confirmationTranscriptRef.current = '';
    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = VIETNAMESE_LANG;
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      let finalChunk = '';
      let interimChunk = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript?.trim() || '';
        if (result.isFinal) finalChunk += `${text} `;
        else interimChunk += `${text} `;
      }
      if (finalChunk) confirmationTranscriptRef.current = `${confirmationTranscriptRef.current} ${finalChunk}`.replace(/\s+/g, ' ').trim();
      const combined = `${confirmationTranscriptRef.current} ${interimChunk}`.replace(/\s+/g, ' ').trim();
      setInterimTranscript(interimChunk.trim());
      const intent = classifyConfirmation(combined);
      if (intent !== 'unknown') {
        setInterimTranscript('');
        handleConfirmationRef.current(intent);
      } else {
        window.clearTimeout(confirmationTimerRef.current);
        confirmationTimerRef.current = window.setTimeout(
          () => handleConfirmationRef.current('unknown'), Math.max(500, unclearConfirmationDelayMs));
      }
    };
    recognition.onerror = (event) => {
      if (event.error !== 'aborted' && event.error !== 'no-speech') {
        setError('Không nghe rõ câu xác nhận. Hệ thống sẽ hỏi lại.');
      }
    };
    recognition.onend = () => {
      if (recognitionRef.current === recognition) recognitionRef.current = null;
      if (activeRef.current && phaseRef.current === 'CONFIRMING') restartRecognition('confirmation');
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
      window.clearTimeout(confirmationTimerRef.current);
      confirmationTimerRef.current = window.setTimeout(
        () => handleConfirmationRef.current('unknown'), Math.max(1_000, confirmationSilenceMs));
    } catch {
      setPhase('ERROR_RECOVERABLE');
      setError('Không thể nghe câu xác nhận. Hãy thử lại.');
    }
  }, [confirmationSilenceMs, restartRecognition, setPhase, stopRecognition, unclearConfirmationDelayMs]);
  startConfirmationRecognitionRef.current = startConfirmationRecognition;

  const speakQuestion = useCallback(() => {
    const text = questionTextRef.current;
    if (!text || !activeRef.current) return;
    clearTimers();
    speak(text, 'QUESTION_PLAYING', () => startAnswerListeningRef.current());
  }, [clearTimers, speak]);

  const start = useCallback(() => {
    if (!supported || !questionIdRef.current || !questionTextRef.current || disabledRef.current) return;
    setError('');
    setTranscriptNotice('');
    activeRef.current = true;
    setActive(true);
    speakQuestion();
  }, [speakQuestion, supported]);

  const stop = useCallback(() => {
    activeRef.current = false;
    setActive(false);
    speakingRef.current = false;
    savingRef.current = false;
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    clearTimers();
    stopRecognition();
    stopAudio();
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    window.speechSynthesis?.cancel();
    setPhase('IDLE');
  }, [clearTimers, setPhase, stopAudio, stopRecognition]);

  useEffect(() => {
    const previousQuestion = questionIdRef.current;
    questionIdRef.current = questionId;
    questionTextRef.current = questionText;
    if (previousQuestion === questionId) return;
    lifecycleTokenRef.current += 1;
    clearTimers();
    stopRecognition();
    stopAudio();
    audioCaptureRef.current.dispose();
    audioCaptureRef.current = new CandidateAudioCapture();
    captureIdRef.current = newCaptureId();
    captureVersionRef.current = 0;
    segmentsRef.current = [];
    browserCommittedRef.current = initialTranscript.trim();
    browserDraftRef.current = initialTranscript.trim();
    confirmedTranscriptRef.current = initialTranscript.trim();
    gladiaTranscriptRef.current = '';
    confirmationTranscriptRef.current = '';
    setInterimTranscript('');
    setTranscriptNotice('');
    onTranscriptRef.current(initialTranscript.trim());
    window.speechSynthesis.cancel();
    if (!activeRef.current) return;
    if (questionId && questionText) {
      const timer = window.setTimeout(speakQuestion, 150);
      return () => window.clearTimeout(timer);
    }
    speak('Bạn đã hoàn thành các câu hỏi. Hãy bấm kết thúc phỏng vấn để nhận điểm và nhận xét.',
      'COMPLETED', () => setPhase('COMPLETED'));
    return undefined;
  }, [clearTimers, initialTranscript, questionId, questionText, setPhase, speak, speakQuestion, stopAudio, stopRecognition]);

  useEffect(() => () => {
    activeRef.current = false;
    lifecycleTokenRef.current += 1;
    speechTokenRef.current += 1;
    clearTimers();
    stopRecognition();
    stopAudio();
    audioCaptureRef.current.dispose();
    window.speechSynthesis?.cancel();
  }, [clearTimers, stopAudio, stopRecognition]);

  return { supported, active, phase, interimTranscript, transcriptNotice, error, start, stop };
}
