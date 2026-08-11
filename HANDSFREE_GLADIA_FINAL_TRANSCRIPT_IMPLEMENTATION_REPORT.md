# Hands-Free Gladia Final Transcript Implementation Report

Ngày hoàn tất: 2026-08-11  
Phạm vi: Phase 2 Hands-Free Virtual Interview  
Trạng thái: Đã implementation và kiểm thử; không triển khai fluency score, emotion, confidence, personality hoặc thay đổi `overallScore`.

## 1. Final Hands-Free Flow

Flow đã triển khai:

```text
QUESTION_PLAYING
  -> TTS kết thúc
STARTING_CAPTURE
  -> MediaRecorder bắt đầu candidate-only segment
LISTENING
  -> Web Speech API cập nhật browserTranscript realtime
  -> MediaRecorder ghi audio song song
FINALIZING_AUDIO
  -> dừng recognition
  -> MediaRecorder onstop hoàn tất Blob/File
TRANSCRIBING
  -> upload toàn bộ segments + questionId + captureId + captureVersion
  -> backend decode từng segment, ghép PCM, tạo WAV mono PCM 16 kHz tạm
  -> Gladia tạo transcript cho toàn answer
  -> Silero VAD phân tích cùng PCM nếu khả dụng
TRANSCRIPT_READY
  -> gladiaTranscript thay browserTranscript trên UI
  -> hoặc giữ browserTranscript khi Gladia không khả dụng
CONFIRMING
  -> mới phát câu hỏi xác nhận
SUBMITTING
  -> lưu confirmedTranscript
```

System TTS luôn gọi safety barrier dừng candidate recorder trước khi phát. Candidate capture chỉ được start từ callback kết thúc TTS.

## 2. State Machine

`useVoiceConversation` dùng các state rõ ràng:

- `IDLE`
- `QUESTION_PLAYING`
- `STARTING_CAPTURE`
- `LISTENING`
- `FINALIZING_AUDIO`
- `TRANSCRIBING`
- `TRANSCRIPT_READY`
- `CONFIRMING`
- `CONTINUING`
- `SUBMITTING`
- `COMPLETED`
- `ERROR_RECOVERABLE`

Mọi callback audio/provider quan trọng được kiểm tra bằng `lifecycleToken + questionId + captureId`. Response cũ không được ghi đè question/capture mới. `savingRef` ngăn positive confirmation được submit hai lần.

## 3. Browser Transcript vs Gladia Transcript

Ba nguồn dữ liệu được giữ tách biệt:

- `browserTranscript`: Web Speech realtime, chỉ phục vụ hiển thị tức thời và fallback.
- `gladiaTranscript`: evidence STT gốc từ audio, không bị candidate editing ghi đè.
- `confirmedTranscript`: nội dung được gửi vào endpoint confirm và dùng cho content evaluation.

Mặc định `confirmedTranscript = gladiaTranscript`. Khi Gladia lỗi, `confirmedTranscript = browserTranscript`. Manual mode có transcript editor vẫn giữ hành vi cũ; hands-free hiện hiển thị read-only nên chưa có candidate-edited branch.

Trong lúc `TRANSCRIBING`, UI không xóa browser transcript. Khi backend trả `standardized`, UI thay bằng `finalTranscript` của Gladia và hiển thị `Đã chuẩn hóa`. Khi fallback, UI giữ browser transcript và hiển thị `Không thể chuẩn hóa, sử dụng bản ghi nhận realtime`.

## 4. Audio Capture Lifecycle

`CandidateAudioCapture` quản lý đúng một reusable microphone stream và tối đa một active `MediaRecorder`:

- MIME preference: `audio/webm;codecs=opus`, `audio/webm`, `audio/ogg;codecs=opus` theo hỗ trợ runtime.
- Mỗi answer continuation tạo một container độc lập và `segmentSequence` liên tục.
- Stream được reuse trong cùng component khi track còn sống.
- Recorder/track được cleanup khi question đổi, interview dừng, component unmount, permission/recorder error hoặc stale async callback.
- Audio chỉ tồn tại trong frontend memory cho đến khi answer được confirm/question đổi.

Không ghi question TTS, confirmation prompt hoặc system audio.

## 5. Multi-Segment Handling

Một answer attempt có `captureId` ổn định. Mỗi lần candidate nói tiếp:

- thêm segment mới với sequence `0, 1, 2, ...`;
- tăng `captureVersion` tuần tự;
- gửi lại toàn bộ answer segments;
- không nối raw WebM bytes.

Backend decode từng segment độc lập bằng Phase 1 `AudioDecoder`, ghép PCM theo sequence, rồi tạo một WAV PCM mono 16 kHz để Gladia xử lý toàn answer trong một transcription job. Nếu local decoder không khả dụng, backend degrade sang transcribe từng container độc lập và ghép transcript theo sequence; VAD được đánh dấu unavailable phù hợp.

## 6. Backend Endpoint

Endpoint mới:

```text
POST /candidate/ai-interviews/sessions/{sessionId}/questions/{questionId}/answer-capture
Content-Type: multipart/form-data
Idempotency-Key: {captureId}
```

Fields:

- `audioSegments` (repeated file part)
- `captureId`
- `captureVersion`
- `segmentSequences` (repeated)
- `browserTranscript` optional
- `durationSeconds` optional repeated metadata

Response provider-neutral:

```json
{
  "questionId": "...",
  "captureId": "...",
  "captureVersion": 1,
  "browserTranscript": "...",
  "gladiaTranscript": "...",
  "finalTranscript": "...",
  "transcriptStatus": "standardized",
  "dataQuality": "AUDIO_VAD_PLUS_GLADIA",
  "vadMetrics": {}
}
```

Idempotency được lưu theo `(answerId, captureId, captureVersion)` và SHA-256 payload. Retry cùng payload trả cùng result; cùng version nhưng payload khác trả `409 IDEMPOTENCY_PAYLOAD_MISMATCH`; continuation hợp lệ phải dùng version kế tiếp. Endpoint kiểm tra question-scoped/current question và trả `409 STALE_QUESTION` cho audio cũ.

## 7. Gladia Configuration

Gladia vẫn là final STT provider. Cấu hình mới:

- languages mặc định `vi,en`;
- `code_switching = true`;
- custom vocabulary enable/intensity/max-items qua environment;
- không expose raw Gladia response.

Request bám theo [Gladia Pre-recorded API](https://docs.gladia.io/api-reference/v2/pre-recorded/init), trong đó `language_config.languages` hỗ trợ nhiều ngôn ngữ và `code_switching`. Custom vocabulary dùng cấu trúc được mô tả trong [Gladia Custom Vocabulary](https://docs.gladia.io/chapters/audio-intelligence/custom-vocabulary).

Nếu provider/account trả HTTP 400 với optional enhancements, client retry transcription initiation không có code-switch/custom-vocabulary fields thay vì làm hỏng interview.

## 8. Technical Vocabulary Handling

`TechnicalVocabularyBuilder` là provider-neutral và tổng hợp có deduplicate từ:

- configuration base vocabulary;
- interview/session title;
- job title và job skills;
- practice target role, skills, technical stack;
- question skill tag và technical-looking terms trong question.

Danh sách kỹ thuật không hard-code trong Gladia client. Base vocabulary có thể thay qua `GLADIA_BASE_VOCABULARY`; giới hạn số item và intensity có config riêng. Các recommendation về intensity vừa phải phù hợp với [Gladia recommended parameters](https://docs.gladia.io/chapters/pre-recorded-stt/recommended-parameters).

## 9. VAD Integration

Khi decode thành công, cùng `DecodedPcmAudio` được dùng cho:

```text
mono PCM 16 kHz
  +-> temporary WAV -> Gladia
  +-> SileroVadAnalyzer
```

Phase này chỉ trả:

- speech segments;
- speaking duration;
- speech onset;
- internal pause count;
- longest internal pause;
- total internal pause duration;
- pause duration ratio;
- VAD model version/status.

Không tạo `fluencyScore`. VAD exception được cô lập và không làm mất Gladia transcript. Gladia exception cũng không xóa VAD result.

## 10. Persistence

Migration `V38__add_hands_free_answer_captures.sql` bổ sung:

- `interview_answers.original_speech_transcript`;
- `interview_answers.speech_analysis_json`;
- active capture provenance;
- bảng `interview_answer_captures` cho idempotency/version/result provenance.

`transcript_text` giữ nguyên semantics confirmed transcript và chỉ được chốt qua flow confirm hiện có. Capture processing lưu Gladia evidence và analysis trước, không đặt `answeredAt`.

`speechAnalysisJson` chứa `analysisVersion`, `captureId`, `captureVersion`, `source`, `dataQuality`, transcript evidence, basic VAD metrics và model status. Không lưu raw audio, PCM, tensor, recurrent state, probability trace hoặc raw provider response.

## 11. Failure/Degradation Matrix

| Web Speech | Gladia | VAD | Kết quả |
|---|---|---|---|
| success | success | success | Gladia final + VAD metrics |
| success | success | fail | Gladia final, VAD unavailable |
| success | fail | success | browser fallback + VAD metrics |
| success | fail | fail | browser transcript only |
| fail | success | any | Gladia final từ MediaRecorder |
| success | no MediaRecorder | n/a | browser fallback; interview tiếp tục |

Frontend tự retry upload đúng cùng idempotency payload một lần. Nếu vẫn lỗi mạng, giữ browser transcript và chuyển sang confirmation. Nếu cả browser và Gladia đều rỗng, backend không chốt answer rỗng.

## 12. Race Condition Handling

Đã có guard cho:

- recognition `onend`/finalize fire lặp;
- MediaRecorder `onstop` đến muộn;
- positive confirmation lặp;
- network retry cùng payload;
- question đổi hoặc unmount khi request đang chạy;
- capture cũ trả về sau capture/question mới;
- TTS bắt đầu khi recorder còn active;
- capture result đến sau lifecycle đã bị hủy.

Backend dùng pessimistic session claim trong transaction ngắn, optimistic answer version và capture uniqueness. Provider/decode/VAD chạy ngoài DB transaction; completion transaction kiểm tra active capture/version và `answeredAt` trước khi persist.

## 13. Tests Added

Backend:

- question-scoped multipart binding;
- mocked Gladia success và Vietnamese-English technical transcript;
- whole-answer multi-segment PCM/WAV path;
- Gladia/VAD failure fallback;
- same-payload idempotency và payload conflict;
- controlled version continuation;
- stale question;
- invalid audio;
- temp cleanup;
- provenance và `overallScore` unchanged;
- Gladia code-switch/custom-vocabulary request;
- vocabulary aggregation;
- PCM WAV serialization.

Frontend:

- realtime/final transcript source resolution;
- Gladia replaces browser transcript;
- Gladia failure keeps browser transcript;
- capture gate after TTS ended;
- one active recorder, multi-segment sequence và stream reuse;
- recorder/track cleanup;
- stale question/capture/provider callback rejection;
- duplicate confirmation guard;
- Vietnamese positive/negative confirmation classification.

## 14. Test Results

- Backend full suite: **72 tests**, 0 failures, 0 errors, 5 skipped by existing conditional suites.
- Frontend full suite: **46 tests**, 0 failures.
- AI interview/VAD targeted regression suites: pass.
- Spring application context/repository validation: pass.

Các WARN/ERROR log từ `GlobalExceptionHandlerSecurityTest` là dữ liệu exception cố ý của security tests; suite kết thúc thành công.

## 15. Build Results

- Backend: `mvn -q test` pass.
- Backend package: `mvn -q -DskipTests package` pass.
- Artifact: `backend/target/recruitment-portal-0.0.1-SNAPSHOT.jar`.
- Frontend: `npm test -- --run` pass.
- Frontend production build: `npm run build` pass.

## 16. Known Limitations

- Web Speech API và MediaRecorder support vẫn phụ thuộc browser; browser không có MediaRecorder sẽ dùng transcript realtime fallback.
- FFmpeg phải được provision bằng `VAD_FFMPEG_PATH` để có primary whole-answer normalized WAV và VAD. Khi thiếu FFmpeg, Gladia vẫn có segment-level fallback nhưng punctuation/context xuyên boundary có thể kém hơn một transcription job duy nhất.
- Gladia polling hiện vẫn dùng timeout/polling strategy của project; chưa chuyển sang callback/webhook.
- Audio tạm trên application server luôn bị xóa trong `finally`, nhưng retention của bản upload nằm trong hạ tầng Gladia phải tuân theo account/DPA/provider policy.
- Hands-free transcript hiện read-only; nếu mở candidate editing sau này, cần giữ `gladiaTranscript` immutable và chỉ thay `confirmedTranscript`.
- Không có fluency/filler/repetition scoring trong Phase 2.

## 17. Next Step

Trước production rollout:

1. chạy migration V38 trên staging;
2. provision/pin FFmpeg và cấu hình `VAD_FFMPEG_PATH`;
3. xác minh Gladia account chấp nhận `vi,en`, code switching và custom vocabulary;
4. chạy browser E2E thực với Chrome/Edge, microphone thật và TTS provider thật;
5. theo dõi latency, fallback rate, payload conflict và temp-storage health.

Không bắt đầu fluency scoring trong phase này.
