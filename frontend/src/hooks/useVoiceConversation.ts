import { useCallback, useEffect, useRef, useState } from 'react';

export type VoicePhase = 'idle' | 'speaking' | 'listening' | 'processing' | 'paused' | 'error';

interface VoiceConversationOptions {
  questionId?: string;
  questionText?: string;
  initialTranscript: string;
  silenceMs: number;
  disabled?: boolean;
  onTranscript: (value: string) => void;
  onSubmit: (value: string) => Promise<void>;
}

const RESTART_DELAY_MS = 250;

export function useVoiceConversation({
  questionId,
  questionText,
  initialTranscript,
  silenceMs,
  disabled = false,
  onTranscript,
  onSubmit,
}: VoiceConversationOptions) {
  const supported = typeof window !== 'undefined'
    && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)
    && 'speechSynthesis' in window;
  const [active, setActive] = useState(false);
  const [autoSubmit, setAutoSubmit] = useState(true);
  const [phase, setPhase] = useState<VoicePhase>('idle');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [error, setError] = useState('');

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const activeRef = useRef(false);
  const speakingRef = useRef(false);
  const submittingRef = useRef(false);
  const finalTranscriptRef = useRef(initialTranscript.trim());
  const silenceTimerRef = useRef<number>();
  const restartTimerRef = useRef<number>();
  const onTranscriptRef = useRef(onTranscript);
  const onSubmitRef = useRef(onSubmit);
  const autoSubmitRef = useRef(autoSubmit);

  useEffect(() => {
    onTranscriptRef.current = onTranscript;
  }, [onTranscript]);

  useEffect(() => {
    onSubmitRef.current = onSubmit;
  }, [onSubmit]);

  useEffect(() => {
    autoSubmitRef.current = autoSubmit;
  }, [autoSubmit]);

  const clearTimers = useCallback(() => {
    window.clearTimeout(silenceTimerRef.current);
    window.clearTimeout(restartTimerRef.current);
  }, []);

  const submitCurrentAnswer = useCallback(async () => {
    const value = finalTranscriptRef.current.trim();
    if (!value || submittingRef.current || disabled) return;
    submittingRef.current = true;
    clearTimers();
    recognitionRef.current?.abort();
    setInterimTranscript('');
    setPhase('processing');
    try {
      await onSubmitRef.current(value);
    } catch (submitError) {
      activeRef.current = false;
      setActive(false);
      setPhase('error');
      setError(submitError instanceof Error ? submitError.message : 'Không thể gửi câu trả lời bằng giọng nói.');
    } finally {
      submittingRef.current = false;
    }
  }, [clearTimers, disabled]);

  const scheduleSubmit = useCallback(() => {
    window.clearTimeout(silenceTimerRef.current);
    if (!autoSubmitRef.current || !finalTranscriptRef.current.trim()) return;
    silenceTimerRef.current = window.setTimeout(() => {
      void submitCurrentAnswer();
    }, Math.max(1_500, silenceMs));
  }, [silenceMs, submitCurrentAnswer]);

  const startRecognition = useCallback(() => {
    if (!supported || !activeRef.current || speakingRef.current || submittingRef.current || disabled) return;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) return;

    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'vi-VN';
    recognition.maxAlternatives = 1;
    recognition.onresult = (event) => {
      let interim = '';
      let finalChunk = '';
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript?.trim() || '';
        if (!text) continue;
        if (result.isFinal) finalChunk += `${text} `;
        else interim += `${text} `;
      }
      if (finalChunk) {
        finalTranscriptRef.current = `${finalTranscriptRef.current} ${finalChunk}`.replace(/\s+/g, ' ').trim();
      }
      const cleanInterim = interim.trim();
      setInterimTranscript(cleanInterim);
      onTranscriptRef.current(`${finalTranscriptRef.current} ${cleanInterim}`.trim());
      scheduleSubmit();
    };
    recognition.onerror = (event) => {
      if (event.error === 'aborted' || event.error === 'no-speech') return;
      const permissionDenied = event.error === 'not-allowed' || event.error === 'service-not-allowed';
      setError(permissionDenied
        ? 'Trình duyệt chưa cấp quyền microphone. Hãy cấp quyền hoặc dùng chế độ ghi âm thủ công.'
        : `Nhận dạng giọng nói tạm dừng (${event.error}).`);
      if (permissionDenied) {
        activeRef.current = false;
        setActive(false);
        setPhase('error');
      }
    };
    recognition.onend = () => {
      if (!activeRef.current || speakingRef.current || submittingRef.current || disabled) return;
      restartTimerRef.current = window.setTimeout(() => {
        try {
          recognition.start();
          setPhase('listening');
        } catch {
          setPhase('error');
          setError('Không thể khởi động lại microphone. Hãy bấm tạm dừng rồi thử lại.');
        }
      }, RESTART_DELAY_MS);
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
      setPhase('listening');
    } catch {
      setPhase('error');
      setError('Không thể khởi động microphone. Hãy kiểm tra quyền của trình duyệt.');
    }
  }, [disabled, scheduleSubmit, supported]);

  const speakQuestion = useCallback(() => {
    if (!supported || !questionText || !activeRef.current || disabled) return;
    clearTimers();
    recognitionRef.current?.abort();
    window.speechSynthesis.cancel();
    speakingRef.current = true;
    const utterance = new SpeechSynthesisUtterance(questionText);
    utterance.lang = 'vi-VN';
    utterance.rate = 0.95;
    utterance.onstart = () => setPhase('speaking');
    utterance.onend = () => {
      speakingRef.current = false;
      startRecognition();
    };
    utterance.onerror = () => {
      speakingRef.current = false;
      startRecognition();
    };
    window.speechSynthesis.speak(utterance);
  }, [clearTimers, disabled, questionText, startRecognition, supported]);

  const start = useCallback(() => {
    if (!supported || !questionId || !questionText || disabled) return;
    setError('');
    setActive(true);
    activeRef.current = true;
    finalTranscriptRef.current = initialTranscript.trim();
    onTranscriptRef.current(finalTranscriptRef.current);
    speakQuestion();
  }, [disabled, initialTranscript, questionId, questionText, speakQuestion, supported]);

  const pause = useCallback(() => {
    activeRef.current = false;
    setActive(false);
    speakingRef.current = false;
    clearTimers();
    recognitionRef.current?.abort();
    window.speechSynthesis?.cancel();
    setPhase('paused');
  }, [clearTimers]);

  useEffect(() => {
    finalTranscriptRef.current = initialTranscript.trim();
    setInterimTranscript('');
    if (activeRef.current && questionId && questionText && !disabled) {
      const timer = window.setTimeout(speakQuestion, 100);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [disabled, initialTranscript, questionId, questionText, speakQuestion]);

  useEffect(() => () => {
    activeRef.current = false;
    clearTimers();
    recognitionRef.current?.abort();
    window.speechSynthesis?.cancel();
  }, [clearTimers]);

  return {
    supported,
    active,
    autoSubmit,
    setAutoSubmit,
    phase,
    interimTranscript,
    error,
    start,
    pause,
    submitCurrentAnswer,
  };
}
