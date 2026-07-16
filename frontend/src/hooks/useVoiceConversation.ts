import { useCallback, useEffect, useRef, useState } from 'react';

export type VoicePhase =
  | 'idle'
  | 'speaking-question'
  | 'listening-answer'
  | 'asking-confirmation'
  | 'listening-confirmation'
  | 'saving-answer'
  | 'awaiting-end'
  | 'error';

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
  onConfirm: (value: string) => Promise<void>;
  onSpeechUrl?: (text: string) => Promise<string | undefined>;
}

type RecognitionMode = 'answer' | 'confirmation';

const RESTART_DELAY_MS = 250;
const VOICE_LOAD_WAIT_MS = 700;
const VIETNAMESE_LANG = 'vi-VN';

export function pickVietnameseVoice(voices: SpeechSynthesisVoice[]) {
  const exactVietnamese = voices.find((voice) => voice.lang?.toLowerCase() === 'vi-vn');
  if (exactVietnamese) return exactVietnamese;

  const vietnameseByLang = voices.find((voice) => voice.lang?.toLowerCase().startsWith('vi'));
  if (vietnameseByLang) return vietnameseByLang;

  return voices.find((voice) => {
    const searchable = `${voice.name || ''} ${voice.lang || ''}`.toLowerCase();
    return searchable.includes('vietnamese')
      || searchable.includes('viet nam')
      || searchable.includes('việt nam')
      || searchable.includes('tieng viet')
      || searchable.includes('tiếng việt');
  });
}

function normalizeSpeech(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function classifyConfirmation(value: string): ConfirmationIntent {
  const normalized = normalizeSpeech(value);
  if (!normalized) return 'unknown';
  const words = normalized.split(' ');

  const negativePhrases = [
    'khong xong',
    'khong dau',
    'khong phai',
    'chua xong',
    'chua dau',
    'chua tra loi xong',
    'toi chua xong',
    'em chua xong',
    'minh chua xong',
    'toi muon noi them',
    'em muon noi them',
    'minh muon noi them',
    'muon noi them',
    'noi them',
    'tiep tuc',
  ];
  if (normalized === 'khong'
    || words.includes('chua')
    || negativePhrases.some((phrase) => normalized === phrase || normalized.includes(phrase))) {
    return 'negative';
  }

  const positivePhrases = [
    'toi da tra loi xong',
    'da tra loi xong',
    'tra loi xong',
    'toi da xong',
    'toi xong roi',
    'em da xong',
    'em xong roi',
    'em xong',
    'minh da xong',
    'minh xong roi',
    'minh xong',
    'da xong',
    'da xong roi',
    'da song',
    'da song roi',
    'song roi',
    'xong roi',
    'hoan thanh',
    'da hoan thanh',
    'ok xong',
    'okay xong',
    'da roi',
    'duoc roi',
    'dung roi',
    'dong y',
  ];
  return normalized === 'xong'
    || normalized === 'song'
    || normalized === 'roi'
    || normalized === 'co'
    || words.includes('xong')
    || words.includes('song')
    || positivePhrases.some((phrase) => normalized === phrase || normalized.includes(phrase))
    ? 'positive'
    : 'unknown';
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
  onConfirm,
  onSpeechUrl,
}: VoiceConversationOptions) {
  const canUseBrowserSpeech = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const canUseProviderSpeech = Boolean(onSpeechUrl);
  const supported = typeof window !== 'undefined'
    && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)
    && (canUseBrowserSpeech || canUseProviderSpeech);
  const [active, setActive] = useState(false);
  const [phase, setPhase] = useState<VoicePhase>('idle');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [error, setError] = useState('');

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const activeRef = useRef(false);
  const speakingRef = useRef(false);
  const savingRef = useRef(false);
  const modeRef = useRef<RecognitionMode>('answer');
  const answerCommittedRef = useRef(initialTranscript.trim());
  const answerDraftRef = useRef(initialTranscript.trim());
  const confirmationTranscriptRef = useRef('');
  const questionIdRef = useRef(questionId);
  const questionTextRef = useRef(questionText);
  const disabledRef = useRef(disabled);
  const answerSilenceTimerRef = useRef<number>();
  const confirmationTimerRef = useRef<number>();
  const restartTimerRef = useRef<number>();
  const voiceLoadTimerRef = useRef<number>();
  const voiceLoadCleanupRef = useRef<() => void>(() => undefined);
  const speechTokenRef = useRef(0);
  const onTranscriptRef = useRef(onTranscript);
  const onConfirmRef = useRef(onConfirm);
  const onSpeechUrlRef = useRef(onSpeechUrl);
  const startAnswerRecognitionRef = useRef<() => void>(() => undefined);
  const startConfirmationRecognitionRef = useRef<() => void>(() => undefined);
  const askConfirmationRef = useRef<(clarify?: boolean) => void>(() => undefined);
  const handleConfirmationRef = useRef<(intent: ConfirmationIntent) => void>(() => undefined);

  useEffect(() => {
    onTranscriptRef.current = onTranscript;
  }, [onTranscript]);

  useEffect(() => {
    onConfirmRef.current = onConfirm;
  }, [onConfirm]);

  useEffect(() => {
    onSpeechUrlRef.current = onSpeechUrl;
  }, [onSpeechUrl]);

  useEffect(() => {
    disabledRef.current = disabled;
  }, [disabled]);

  const clearTimers = useCallback(() => {
    window.clearTimeout(answerSilenceTimerRef.current);
    window.clearTimeout(confirmationTimerRef.current);
    window.clearTimeout(restartTimerRef.current);
    window.clearTimeout(voiceLoadTimerRef.current);
    voiceLoadCleanupRef.current();
    voiceLoadCleanupRef.current = () => undefined;
  }, []);

  const stopRecognition = useCallback(() => {
    const recognition = recognitionRef.current;
    recognitionRef.current = null;
    if (recognition) {
      try {
        recognition.abort();
      } catch {
        // The browser may already have stopped this recognition instance.
      }
    }
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
    } catch {
      // Ignore cleanup errors from detached media elements.
    }
  }, []);

  const speak = useCallback((text: string, nextPhase: VoicePhase, onDone: () => void) => {
    if (!supported || !activeRef.current || disabledRef.current) return;
    speakingRef.current = true;
    stopRecognition();
    stopAudio();
    window.clearTimeout(restartTimerRef.current);
    window.clearTimeout(voiceLoadTimerRef.current);
    voiceLoadCleanupRef.current();
    voiceLoadCleanupRef.current = () => undefined;
    window.speechSynthesis.cancel();
    const speechToken = speechTokenRef.current + 1;
    speechTokenRef.current = speechToken;

    const finishSpeech = () => {
      if (speechTokenRef.current !== speechToken) return;
      speakingRef.current = false;
      stopAudio();
      if (activeRef.current && !disabledRef.current) onDone();
    };

    const startBrowserSpeech = () => {
      if (speechTokenRef.current !== speechToken) return;
      if (!canUseBrowserSpeech) {
        finishSpeech();
        return;
      }
      const utterance = new SpeechSynthesisUtterance(text);
      const vietnameseVoice = pickVietnameseVoice(window.speechSynthesis.getVoices());
      if (vietnameseVoice) {
        utterance.voice = vietnameseVoice;
      }
      utterance.lang = vietnameseVoice?.lang || VIETNAMESE_LANG;
      utterance.rate = 0.88;
      utterance.pitch = 1;
      utterance.onstart = () => {
        if (speechTokenRef.current === speechToken) setPhase(nextPhase);
      };
      utterance.onend = finishSpeech;
      utterance.onerror = finishSpeech;
      window.speechSynthesis.speak(utterance);
    };

    const startStreamedSpeech = () => {
      const resolver = onSpeechUrlRef.current;
      if (!resolver) return false;
      void resolver(text)
        .then((streamUrl) => {
          if (speechTokenRef.current !== speechToken || !activeRef.current || disabledRef.current) return;
          if (!streamUrl) {
            startBrowserSpeech();
            return;
          }
          const audio = new Audio(streamUrl);
          audioRef.current = audio;
          audio.preload = 'auto';
          audio.onplaying = () => {
            if (speechTokenRef.current === speechToken) setPhase(nextPhase);
          };
          audio.onended = finishSpeech;
          audio.onerror = () => {
            if (speechTokenRef.current !== speechToken) return;
            audioRef.current = null;
            startBrowserSpeech();
          };
          void audio.play().catch(() => {
            if (speechTokenRef.current !== speechToken) return;
            audioRef.current = null;
            startBrowserSpeech();
          });
        })
        .catch(() => {
          if (speechTokenRef.current === speechToken) startBrowserSpeech();
        });
      return true;
    };

    if (startStreamedSpeech()) {
      return;
    }

    if (window.speechSynthesis.getVoices().length) {
      startBrowserSpeech();
      return;
    }

    const startOnce = () => {
      window.clearTimeout(voiceLoadTimerRef.current);
      voiceLoadCleanupRef.current();
      voiceLoadCleanupRef.current = () => undefined;
      startBrowserSpeech();
    };
    const removeVoiceListener = () => {
      window.speechSynthesis.removeEventListener('voiceschanged', startOnce);
    };
    voiceLoadCleanupRef.current = removeVoiceListener;
    window.speechSynthesis.addEventListener('voiceschanged', startOnce, { once: true });
    voiceLoadTimerRef.current = window.setTimeout(startOnce, VOICE_LOAD_WAIT_MS);
  }, [canUseBrowserSpeech, stopAudio, stopRecognition, supported]);

  const restartRecognition = useCallback((mode: RecognitionMode) => {
    window.clearTimeout(restartTimerRef.current);
    restartTimerRef.current = window.setTimeout(() => {
      if (!activeRef.current || speakingRef.current || savingRef.current || disabledRef.current) return;
      if (modeRef.current !== mode) return;
      if (mode === 'answer') startAnswerRecognitionRef.current();
      else startConfirmationRecognitionRef.current();
    }, RESTART_DELAY_MS);
  }, []);

  const startAnswerRecognition = useCallback(() => {
    if (!supported || !activeRef.current || speakingRef.current || savingRef.current || disabledRef.current) return;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) return;

    stopRecognition();
    modeRef.current = 'answer';
    const recognition = new Recognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = VIETNAMESE_LANG;
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
        answerCommittedRef.current = `${answerCommittedRef.current} ${finalChunk}`.replace(/\s+/g, ' ').trim();
      }
      const cleanInterim = interim.trim();
      setInterimTranscript(cleanInterim);
      answerDraftRef.current = `${answerCommittedRef.current} ${cleanInterim}`.replace(/\s+/g, ' ').trim();
      onTranscriptRef.current(answerDraftRef.current);

      if (answerDraftRef.current) {
        window.clearTimeout(answerSilenceTimerRef.current);
        answerSilenceTimerRef.current = window.setTimeout(() => {
          if (activeRef.current && modeRef.current === 'answer' && !speakingRef.current) {
            askConfirmationRef.current(false);
          }
        }, Math.max(1_000, silenceMs));
      }
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
      if (recognitionRef.current === recognition) recognitionRef.current = null;
      if (activeRef.current && modeRef.current === 'answer' && !speakingRef.current && !savingRef.current) {
        restartRecognition('answer');
      }
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
      setPhase('listening-answer');
    } catch {
      setPhase('error');
      setError('Không thể khởi động microphone. Hãy kiểm tra quyền của trình duyệt.');
    }
  }, [restartRecognition, silenceMs, stopRecognition, supported]);
  startAnswerRecognitionRef.current = startAnswerRecognition;

  const handleConfirmation = useCallback((intent: ConfirmationIntent) => {
    window.clearTimeout(confirmationTimerRef.current);
    if (intent === 'negative') {
      confirmationTranscriptRef.current = '';
      speak('Được, bạn hãy tiếp tục câu trả lời.', 'asking-confirmation', () => {
        startAnswerRecognitionRef.current();
      });
      return;
    }
    if (intent === 'unknown') {
      handleConfirmationRef.current('positive');
      return;
    }

    const answer = answerDraftRef.current.trim();
    if (!answer || savingRef.current) {
      speak('Mình chưa nghe thấy câu trả lời. Bạn hãy trả lời câu hỏi trước nhé.', 'asking-confirmation', () => {
        startAnswerRecognitionRef.current();
      });
      return;
    }

    savingRef.current = true;
    stopRecognition();
    setInterimTranscript('');
      setPhase('saving-answer');
      void onConfirmRef.current(answer)
      .then(() => {
        answerCommittedRef.current = '';
        answerDraftRef.current = '';
        confirmationTranscriptRef.current = '';
        onTranscriptRef.current('');
      })
      .catch((confirmError) => {
        activeRef.current = false;
        setActive(false);
        setPhase('error');
        setError(confirmError instanceof Error
          ? confirmError.message
          : 'Không thể lưu câu trả lời. Nội dung của bạn vẫn được giữ để thử lại.');
      })
      .finally(() => {
        savingRef.current = false;
      });
  }, [speak, stopRecognition]);
  handleConfirmationRef.current = handleConfirmation;

  const startConfirmationRecognition = useCallback(() => {
    if (!supported || !activeRef.current || speakingRef.current || savingRef.current || disabledRef.current) return;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) return;

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
        if (!text) continue;
        if (result.isFinal) finalChunk += `${text} `;
        else interimChunk += `${text} `;
      }
      if (!finalChunk && !interimChunk) return;

      const cleanInterim = interimChunk.trim();
      setInterimTranscript(cleanInterim);
      if (finalChunk) {
        confirmationTranscriptRef.current = `${confirmationTranscriptRef.current} ${finalChunk}`
          .replace(/\s+/g, ' ')
          .trim();
      }

      const confirmationText = `${confirmationTranscriptRef.current} ${cleanInterim}`.replace(/\s+/g, ' ').trim();
      const intent = classifyConfirmation(confirmationText);
      if (intent !== 'unknown') {
        setInterimTranscript('');
        handleConfirmationRef.current(intent);
      } else {
        window.clearTimeout(confirmationTimerRef.current);
        confirmationTimerRef.current = window.setTimeout(() => {
          handleConfirmationRef.current('positive');
        }, Math.max(500, unclearConfirmationDelayMs));
      }
    };
    recognition.onerror = (event) => {
      if (event.error === 'aborted' || event.error === 'no-speech') return;
      const permissionDenied = event.error === 'not-allowed' || event.error === 'service-not-allowed';
      if (permissionDenied) {
        activeRef.current = false;
        setActive(false);
        setPhase('error');
        setError('Trình duyệt chưa cấp quyền microphone. Hãy kiểm tra lại quyền microphone.');
      }
    };
    recognition.onend = () => {
      if (recognitionRef.current === recognition) recognitionRef.current = null;
      if (activeRef.current && modeRef.current === 'confirmation' && !speakingRef.current && !savingRef.current) {
        restartRecognition('confirmation');
      }
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
      setPhase('listening-confirmation');
      window.clearTimeout(confirmationTimerRef.current);
      confirmationTimerRef.current = window.setTimeout(() => {
        if (activeRef.current && modeRef.current === 'confirmation') handleConfirmationRef.current('positive');
      }, Math.max(1_000, confirmationSilenceMs));
    } catch {
      setPhase('error');
      setError('Không thể nghe câu xác nhận. Hãy kiểm tra microphone.');
    }
  }, [confirmationSilenceMs, restartRecognition, stopRecognition, supported, unclearConfirmationDelayMs]);
  startConfirmationRecognitionRef.current = startConfirmationRecognition;

  const askConfirmation = useCallback((clarify = false) => {
    if (!activeRef.current || savingRef.current) return;
    window.clearTimeout(answerSilenceTimerRef.current);
    window.clearTimeout(confirmationTimerRef.current);
    stopRecognition();
    confirmationTranscriptRef.current = '';
    const prompt = clarify
      ? 'Mình chưa nghe rõ. Bạn hãy nói đã xong hoặc chưa xong.'
      : 'Bạn đã trả lời xong chưa?';
    speak(prompt, 'asking-confirmation', () => {
      startConfirmationRecognitionRef.current();
    });
  }, [speak, stopRecognition]);
  askConfirmationRef.current = askConfirmation;

  const speakQuestion = useCallback(() => {
    const text = questionTextRef.current;
    if (!text || !activeRef.current) return;
    clearTimers();
    speak(text, 'speaking-question', () => {
      startAnswerRecognitionRef.current();
    });
  }, [clearTimers, speak]);

  const start = useCallback(() => {
    if (!supported || !questionIdRef.current || !questionTextRef.current || disabledRef.current) return;
    setError('');
    setActive(true);
    activeRef.current = true;
    speakQuestion();
  }, [speakQuestion, supported]);

  const stop = useCallback(() => {
    activeRef.current = false;
    setActive(false);
    speakingRef.current = false;
    savingRef.current = false;
    speechTokenRef.current += 1;
    clearTimers();
    stopRecognition();
    stopAudio();
    window.speechSynthesis?.cancel();
    setPhase('idle');
  }, [clearTimers, stopAudio, stopRecognition]);

  useEffect(() => {
    const previousQuestionId = questionIdRef.current;
    questionIdRef.current = questionId;
    questionTextRef.current = questionText;
    if (previousQuestionId === questionId) return;

    answerCommittedRef.current = initialTranscript.trim();
    answerDraftRef.current = initialTranscript.trim();
    confirmationTranscriptRef.current = '';
    setInterimTranscript('');
    onTranscriptRef.current(answerDraftRef.current);
    if (!activeRef.current) return;

    clearTimers();
    stopRecognition();
    stopAudio();
    window.speechSynthesis.cancel();
    if (questionId && questionText) {
      const timer = window.setTimeout(speakQuestion, 150);
      return () => window.clearTimeout(timer);
    }

    speak('Bạn đã hoàn thành các câu hỏi. Hãy bấm kết thúc phỏng vấn để nhận điểm và nhận xét.', 'speaking-question', () => {
      setPhase('awaiting-end');
    });
    return undefined;
  }, [clearTimers, initialTranscript, questionId, questionText, speak, speakQuestion, stopAudio, stopRecognition]);

  useEffect(() => () => {
    activeRef.current = false;
    speechTokenRef.current += 1;
    clearTimers();
    stopRecognition();
    stopAudio();
    window.speechSynthesis?.cancel();
  }, [clearTimers, stopAudio, stopRecognition]);

  return {
    supported,
    active,
    phase,
    interimTranscript,
    error,
    start,
    stop,
  };
}
