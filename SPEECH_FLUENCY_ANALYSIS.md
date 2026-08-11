# Speech Fluency Analysis Audit

Ngày audit: 2026-08-11  
Phạm vi: codebase hiện tại của Smart Recruitment Portal; chỉ audit và đề xuất, chưa sửa implementation hoặc database migration.

## Executive Summary

Kết luận chính:

- Virtual Interview hiện có **hai luồng nhập câu trả lời khác nhau**:
  1. **Ghi âm thủ công**: `MediaRecorder` → backend → Gladia V2 pre-recorded → transcript.
  2. **Phỏng vấn rảnh tay**: Web Speech API của trình duyệt → transcript → backend; luồng này **không đi qua Gladia và không giữ audio**.
- Gladia hiện được backend gọi theo kiểu **batch/asynchronous pre-recorded**, không phải real-time. Backend upload file, tạo transcription job, rồi poll kết quả.
- Source code hiện chỉ đọc `result.transcription.full_transcript`. Word timestamps, utterance timestamps, confidence và metadata của Gladia không được map, lưu hoặc trả ra API.
- Dữ liệu đang lưu đủ để tính deterministic `totalWords`, `fillerCount`, `fillerRate` và `repetitionCount` từ transcript. Chưa đủ để tính đáng tin cậy `speakingDuration`, `speechRateWpm`, pause metrics và `responseLatency` cho mọi luồng.
- Gladia V2 có sẵn utterances, word-level `start`, `end`, `confidence` và audio metadata trong response. Vì vậy MVP nên **mở rộng projection của response Gladia và phân tích ở backend**, chưa thêm VAD.
- Không nên thay đổi `overallScore` hiện tại trong MVP. Nên trả `contentScore` hiện hữu và `fluencyScore` riêng; chỉ tạo `fluencyScore` khi đủ timed evidence. Sau calibration mới cân nhắc điểm tổng hợp.
- Không cần bảng mới. Đề xuất nhỏ nhất là một cột JSONB trên `interview_answers`, nhưng chỉ tạo migration sau khi report/schema được duyệt.
- Trường `ai_answer_feedbacks.confidence_score` hiện tồn tại nhưng không được sử dụng. Không được tái sử dụng trường này cho fluency hoặc “độ tự tin” vì khác nghĩa và dễ dẫn tới suy luận bị cấm.

## 1. Current Virtual Interview Flow

### 1.1 Luồng ghi âm thủ công có Gladia

```text
Candidate microphone
  → navigator.mediaDevices.getUserMedia({ audio: true })
  → MediaRecorder
  → Blob/File answer-<timestamp>.webm giữ trong state trình duyệt
  → POST multipart /candidate/ai-interviews/sessions/{sessionId}/questions/current/audio
  → AiInterviewController.transcribeCurrentQuestion
  → AiInterviewService.validateAudio
  → GladiaTranscriptionClient
      → POST /v2/upload
      → nhận audio_url
      → POST /v2/pre-recorded
      → nhận id
      → GET /v2/pre-recorded/{id} mỗi 2 giây, tối đa 30 lần
      → đọc result.transcription.full_transcript
  → InterviewAnswer.transcriptText + durationSeconds được lưu
  → frontend nhận { questionId, transcript, transcriptStatus }
  → ứng viên có thể sửa transcript
  → POST .../questions/{questionId}/answer
  → transcript cuối được chốt vào InterviewAnswer
  → POST .../finish
  → ShopAiKeyClient.evaluateAnswer cho từng answer chưa chấm
  → AiAnswerFeedback được lưu
  → ShopAiKeyClient.summarizeSession
  → AiSessionFeedback + InterviewSession.overallScore được lưu
  → frontend hiển thị result
```

Evidence:

- Ghi microphone và tạo file: `frontend/src/App.tsx:6389-6421`.
- Upload audio và nhận transcript: `frontend/src/App.tsx:6423-6437`, `frontend/src/services/aiInterviewService.ts:61-75`.
- Backend endpoint: `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:79-85`.
- Validation và persistence: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:234-263`, `:668-705`.
- Gladia upload/start/poll: `backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java`.
- Chốt answer nhưng chưa chấm ngay: `AiInterviewService.java:265-343`.
- Chấm từng answer và tổng kết khi finish: `AiInterviewService.java:282-311`, `:345-364`, `:505-530`.
- Test hiện tại xác nhận deferred evaluation: `backend/src/test/java/com/sjp/recruitment/service/AiInterviewDeferredEvaluationTest.java`.

Điểm đáng chú ý: audio file chỉ tồn tại trong memory của frontend và request multipart. Backend không gọi storage service, không set `InterviewAnswer.audioUrl`, và bỏ `audio_url` do Gladia trả về sau khi dùng để tạo job.

### 1.2 Luồng phỏng vấn rảnh tay không dùng Gladia

```text
Question text
  → ShopAIKey TTS stream hoặc browser SpeechSynthesis
  → khi câu hỏi đọc xong
  → browser SpeechRecognition/webkitSpeechRecognition, lang = vi-VN
  → interim/final transcript trong useVoiceConversation
  → sau khoảng im lặng cấu hình, hệ thống hỏi xác nhận
  → transcript được POST thẳng tới .../questions/{questionId}/confirm
  → InterviewAnswer được lưu
  → khi finish mới chạy AI evaluation và summary như luồng trên
```

Evidence:

- Browser STT: `frontend/src/hooks/useVoiceConversation.ts:338-403`.
- Confirmation và lưu transcript: `useVoiceConversation.ts:406-450`, `:453-526`.
- Tích hợp vào interview room: `frontend/src/App.tsx:6453-6468`, `:6532-6543`, `:6608-6650`.
- Backend confirm endpoint: `AiInterviewController.java:104-110`.

Luồng này không tạo `MediaRecorder`, không upload audio, không gọi Gladia, không lưu duration và không có word timestamps. Cấu hình mặc định trong `application.yml` là `voice-streaming-enabled: true` và `voice-provider: browser_web_speech`, nên đây có thể là luồng chính trên browser được hỗ trợ.

### 1.3 Mapping trách nhiệm hiện tại

| Trách nhiệm | File/component/service |
|---|---|
| Record microphone thủ công | `frontend/src/App.tsx`, `AiInterviewRoom.startRecording/stopRecording` |
| Speech recognition rảnh tay | `frontend/src/hooks/useVoiceConversation.ts` |
| Gửi audio tới backend | `frontend/src/services/aiInterviewService.ts`, `uploadAudio` |
| Gửi audio tới Gladia | `backend/.../service/ai/GladiaTranscriptionClient.java`, `uploadAudio` |
| Nhận/process Gladia response | `GladiaTranscriptionClient.startTranscription/pollTranscript` |
| Lưu candidate answer | `backend/.../service/AiInterviewService.java`, `transcribeCurrentQuestion/confirmAnswer` |
| Entity/repository answer | `InterviewAnswer.java`, `InterviewAnswerRepository.java` |
| Gửi answer cho AI | `AiInterviewService.evaluateAnswer` → `ShopAiKeyClient.evaluateAnswer` |
| Lưu per-answer result | `AiAnswerFeedback.java`, `AiAnswerFeedbackRepository.java` |
| Tạo/lưu session result | `AiInterviewService.generateSummary`, `AiSessionFeedback`, `InterviewSession` |
| Assemble API result | `AiInterviewResponseAssembler.java` |

## 2. Current Gladia Integration

### 2.1 Kiểu tích hợp

- API: Gladia V2.
- Mode: **pre-recorded batch/asynchronous**, dù request HTTP từ frontend đang chờ backend hoàn tất.
- Caller: frontend gọi backend; **backend gọi Gladia**, frontend không thấy API key.
- Transport:
  - `POST /v2/upload` multipart field `audio`.
  - `POST /v2/pre-recorded` với `audio_url`.
  - `GET /v2/pre-recorded/{id}` để poll.
- Poll policy hard-coded trong client: 30 lần, delay 2 giây; tổng cửa sổ xấp xỉ 60 giây.
- Ngôn ngữ request hiện tại: `language_config.languages = ["vi"]`.
- Không cấu hình `code_switching`, `accurate_words_timestamps`, diarization hoặc các audio intelligence feature.
- Diarization không cần thiết vì một candidate là speaker duy nhất.

### 2.2 Audio có được lưu không?

- Frontend: chỉ giữ `File` trong React state cho tới khi đổi câu hỏi/unmount.
- Backend/application storage: **không lưu**.
- Database có cột `interview_answers.audio_url`, nhưng không có code set giá trị này.
- Gladia upload trả `audio_url`, nhưng client chỉ dùng tạm để start transcription rồi bỏ.
- Provider có thể có retention policy riêng; codebase không cấu hình hoặc kiểm soát retention. Cần kiểm tra điều khoản Gladia trước production, nhưng không nên lưu provider URL chỉ để phục vụ fluency.

### 2.3 Response Gladia chính xác mà code hiện dùng

Code không có typed DTO; dùng `Map` và chỉ truy cập các field sau:

```text
POST /v2/upload response:
  audio_url

POST /v2/pre-recorded response:
  id

GET /v2/pre-recorded/{id} response:
  status
  result.transcription.full_transcript
```

Status được xử lý:

- `done`: lấy non-blank `full_transcript`.
- `error` hoặc `failed`: báo provider failure.
- Các status khác: tiếp tục poll.

Backend trả frontend đúng ba field:

```json
{
  "questionId": "...",
  "transcript": "...",
  "transcriptStatus": "completed"
}
```

Không có source code hoặc fixture nào hiện lưu toàn bộ raw Gladia response. Vì vậy không thể khẳng định payload thực tế của tài khoản/provider tại runtime ngoài các field code đang đọc. Report chỉ phân biệt:

- **Provider schema có field** theo tài liệu chính thức.
- **Application hiện có dữ liệu dùng được** khi field được map/persist/expose.

## 3. Available Gladia Data

Theo [Gladia V2 Get pre-recorded result](https://docs.gladia.io/api-reference/v2/pre-recorded/get) và [Gladia V1→V2 migration response example](https://docs.gladia.io/chapters/pre-recorded-stt/migration-from-v1), response `done` có thể chứa:

```text
file.audio_duration
result.metadata.audio_duration
result.transcription.full_transcript
result.transcription.languages[]
result.transcription.utterances[].text
result.transcription.utterances[].start
result.transcription.utterances[].end
result.transcription.utterances[].confidence
result.transcription.utterances[].words[].word
result.transcription.utterances[].words[].start
result.transcription.utterances[].words[].end
result.transcription.utterances[].words[].confidence
```

Hiện trạng trong application:

| Data | Provider schema | Code đọc | DB lưu | API trả frontend |
|---|---:|---:|---:|---:|
| Full transcript | Có | Có | Có | Có |
| Recording duration do frontend đo | Không phải Gladia | Có | Có, integer/ceil | Không |
| Gladia audio duration | Có | Không | Không | Không |
| Word text | Có | Không | Không | Không |
| Word start/end | Có | Không | Không | Không |
| Word confidence | Có | Không | Không | Không |
| Utterance start/end | Có | Không | Không | Không |
| Utterance confidence | Có | Không | Không | Không |
| Detected languages | Có | Không | Không | Không |

`confidence` của Gladia chỉ nên dùng làm **ASR evidence quality**, không phải candidate confidence và không tham gia suy luận cảm xúc/personality.

Code hiện chỉ ép tiếng Việt. Với phỏng vấn Việt/Anh, cần benchmark trước khi đổi thành `languages: ["vi", "en"]` và `code_switching: true`. Tài liệu [Gladia code switching recommendations](https://docs.gladia.io/chapters/pre-recorded-stt/recommended-parameters) khuyến nghị giới hạn danh sách ngôn ngữ dự kiến; không nên bật tự do cho 100+ ngôn ngữ.

## 4. Metrics We Can Calculate Now

“Hiện tại có dữ liệu” trong bảng dưới nghĩa là dữ liệu đang được application map/lưu, không chỉ tồn tại trong schema của provider.

| Metric | Nguồn dữ liệu cần | Hiện tại có dữ liệu đó? | Tính trực tiếp sau khi map Gladia? | Cần raw audio/VAD? | Độ khó |
|---|---|---|---|---|---|
| `totalWords` | Transcript đã normalize hoặc Gladia words | **YES**, cả hai luồng có transcript | **YES** | NO | Thấp |
| `speakingDurationSeconds` | Speech segments hoặc timed words/utterances | **NO**. Manual chỉ có tổng recording duration gồm silence; hands-free không có duration | **YES, xấp xỉ** bằng union utterance/word intervals | NO cho MVP; YES nếu cần VAD-grounded duration | Trung bình |
| `speechRateWpm` | `totalWords` + speaking duration | **NO** cho rate theo actual speaking time. Chỉ có thể tính recording-rate thô ở manual | **YES** sau khi map timing | NO cho MVP | Trung bình |
| `fillerCount` | Transcript/tokens; tốt hơn nếu có utterance và pause context | **YES** | **YES** | NO | Trung bình do false positive/ASR omission |
| `fillerRate` | `fillerCount / totalWords` | **YES** | **YES** | NO | Thấp |
| `repetitionCount` | Token sequence; timing giúp tăng precision | **YES** | **YES** | NO | Trung bình |
| `pauseCount` | Gap giữa word `end` và word kế tiếp `start` | **NO** | **YES** | NO nếu timing đủ ổn | Thấp–trung bình |
| `longPauseCount` | Word gaps + configurable threshold | **NO** | **YES** | NO nếu timing đủ ổn | Thấp–trung bình |
| `longestPauseSeconds` | Max valid word gap | **NO** | **YES** | NO nếu timing đủ ổn | Thấp |
| `responseLatencySeconds` | Mốc kết thúc câu hỏi/capture start + first speech onset | **NO** | **PARTIAL**: first word start chỉ đo từ đầu file, không tự biết lúc câu hỏi kết thúc | Không bắt buộc VAD nếu capture được căn đúng; nếu không cần frontend timing/capture change | Trung bình–cao |

### 4.1 Những metric có thể triển khai ngay từ transcript

- `totalWords`
- `filler.count`
- `filler.rate`
- `repetition.count`
- `repetition.rate` nếu score cần rate

Các metric này vẫn cần analyzer mới; code hiện chưa tính chúng. Transcript-only result phải mang `dataQuality: transcriptOnly` và không được giả vờ có pause/speech-rate data.

### 4.2 Quy ước word count

Tiếng Việt dùng khoảng trắng theo âm tiết nhiều hơn theo “word” ngôn ngữ học. Vì vậy WPM Việt không so trực tiếp với WPM Anh. MVP cần định nghĩa operational `totalWords` nhất quán:

- Nếu có Gladia words: đếm lexical word items sau normalize, bỏ punctuation-only item.
- Nếu transcript-only: Unicode-normalize, tách whitespace/punctuation nhưng giữ token chữ/số có nghĩa.
- Ghi `tokenizationVersion` trong analysis để calibration có thể tái lập.
- Threshold speech rate phải calibration riêng cho tập Việt/Anh của sản phẩm.

## 5. Metrics Requiring Raw Audio/VAD

### 5.1 Không bắt buộc VAD trong MVP

Nếu word/utterance timestamps của Gladia đủ ổn qua benchmark, có thể tính:

- pause gaps,
- first speech onset trong file,
- approximate speaking duration,
- speech rate.

Thuật toán pause:

```text
gapSeconds = max(0, currentWord.start - previousWord.end)
```

- Sort word evidence theo `start`.
- Reject/flag timestamp âm, `end < start`, non-finite value và out-of-order bất thường.
- Không tính leading silence là pause; dùng nó cho speech onset/latency.
- Không tính trailing silence là giữa-câu pause.
- `pause.count` chỉ đếm gap đạt `normalThreshold`; micro pause vẫn có thể giữ trong diagnostic nhưng không tăng count.
- `longPauseCount` đếm gap đạt `longPauseThreshold`.
- Hai threshold bắt buộc lấy từ configuration và `long > normal` phải được validate.

Approximate speaking duration nên lấy union của timed speech intervals, không dùng toàn bộ recording duration. Cần benchmark hai cách:

1. Union utterance intervals.
2. Span từ first word đến last word trừ các silence gap đã phân loại.

Chọn một algorithm version sau khi so với sample audio được gán nhãn; không thay đổi âm thầm giữa các bản release.

### 5.2 Khi nào mới thêm VAD

Chỉ thêm VAD nếu benchmark cho thấy Gladia timing không đủ cho pause/speaking duration, hoặc cần full coverage độc lập provider. VAD chỉ trả speech/silence segments; không dùng để suy luận emotion, confidence, stress hoặc personality.

Raw browser audio hiện thường là WebM/Opus. Cả Silero và WebRTC VAD đều cần PCM phù hợp, nên bổ sung VAD kéo theo decode/resample pipeline. Đây là dependency/operational cost lớn hơn bản thân detector.

### 5.3 Response latency

`firstWord.start` của Gladia là speech onset so với đầu file audio. Nó chỉ là `responseLatencySeconds` đúng nghĩa nếu recording bắt đầu đúng lúc câu hỏi/TTS kết thúc. Hiện manual recording bắt đầu khi user bấm nút; khoảng do dự trước khi bấm không được ghi. Hands-free biết callback “question audio ended” nhưng không ghi timestamp/audio onset.

Đề xuất:

- MVP manual: lưu tên semantic chính xác `speechOnsetSecondsFromRecordingStart`; chỉ alias sang response latency nếu product chấp nhận định nghĩa này.
- Full response latency: frontend cần capture `questionPromptEndedAt` và bắt đầu audio capture cùng thời điểm; analyzer dùng first timed word/VAD speech start.

## 6. Recommended Libraries

### 6.1 MVP khuyến nghị: không thêm thư viện

Stack hiện tại là Java 17/Spring Boot 3.2 + React 18. Spring/Jackson đã đủ để map typed Gladia response; Java code thuần đủ cho token rules, gaps và score. Đây là lựa chọn ít thay đổi nhất.

### 6.2 Silero VAD

[Silero VAD repository chính thức](https://github.com/snakers4/silero-vad) cung cấp model ONNX/JIT, hỗ trợ nhiều ngôn ngữ và trả speech timestamps. Với backend Java có thể chạy ONNX Runtime, nhưng cần:

- thêm ONNX Runtime dependency/model lifecycle,
- decode WebM/Opus sang mono PCM,
- resample về 8 kHz/16 kHz,
- quản lý model resource, memory và concurrency,
- thêm golden-audio integration tests.

Đánh giá: phù hợp nhất nếu thật sự cần VAD; độ khó trung bình–cao; **không thêm ở MVP**.

### 6.3 WebRTC VAD

[py-webrtcvad repository](https://github.com/wiseman/py-webrtcvad) minh họa detector frame-based, yêu cầu 16-bit mono PCM ở sample rate 8/16/32/48 kHz và frame 10/20/30 ms. Codebase không có Python service; dùng binding/wrapper cộng đồng cho Java hoặc thêm sidecar sẽ tăng kiến trúc và vận hành.

Đánh giá: nhanh, đơn giản về thuật toán nhưng mismatch với stack và input WebM hiện tại; **không khuyến nghị cho MVP**.

### 6.4 Kết luận thư viện

| Lựa chọn | Accuracy tiềm năng | Thay đổi stack | Khuyến nghị |
|---|---:|---:|---|
| Gladia word/utterance timestamps | Đủ để benchmark MVP | Rất thấp | **Chọn** |
| Silero VAD ONNX | Tốt cho speech/silence | Trung bình–cao | Fallback phase sau |
| WebRTC VAD | Tốt cho binary voiced frames | Cao trong stack hiện tại | Không chọn |

## 7. Proposed Fluency Analyzer Architecture

### 7.1 Ba phương án

1. **Gladia timing downstream — khuyến nghị**  
   Đổi `GladiaTranscriptionClient` từ trả `String` sang typed result; chạy analyzer deterministic ở backend; không lưu raw audio. Đủ cho phần lớn metric với thay đổi nhỏ nhất.

2. **Transcript-only**  
   Không đổi Gladia projection; chỉ tính word/filler/repetition. Thay đổi ít hơn nhưng không đạt mục tiêu pause, actual speech rate và latency; không thể tạo score đầy đủ.

3. **Gladia + VAD song song**  
   Decode raw audio và chạy Silero song song/tuần tự. Chính xác hơn về speech/silence nhưng thêm dependency, model, audio conversion và chi phí test. Chưa có evidence để biện minh.

Chọn phương án 1, giữ phương án 3 làm fallback sau benchmark.

### 7.2 Data flow đề xuất

```text
Manual Candidate Audio
  → existing backend upload endpoint
  → GladiaTranscriptionClient
      → full transcript
      → languages
      → utterances/words/start/end/confidence
      → audio duration
  → SpeechFluencyAnalyzer
      → deterministic metrics
      → rule-based score khi đủ evidence
      → metric-only feedback
  → persist original provider evidence + analysis on InterviewAnswer
  → candidate-confirmed transcript vẫn đi vào ShopAiKey content evaluation
  → result trả riêng contentScore và fluencyAnalysis
```

```text
Hands-free Browser SpeechRecognition
  → transcript-only FluencyAnalysis
  → totalWords/filler/repetition
  → timed metrics = null
  → fluencyScore = null, scoreStatus = insufficientData
```

Điểm tích hợp tốt nhất là ngay sau `gladiaTranscriptionClient.transcribe(file)` trong `AiInterviewService.transcribeCurrentQuestion`, trước khi response trả về frontend. Analyzer không gọi LLM và không thay Gladia.

### 7.3 Content score và fluency score

- `AiAnswerFeedback.overallScore` hiện là content score từ ShopAiKey/fallback.
- `InterviewSession.overallScore` hiện là trung bình per-answer content score.
- MVP không đổi semantics hai field trên.
- Thêm `fluencyScore` riêng trong `fluencyAnalysis` khi đủ timed evidence.
- Không cộng fluency vào content score cho tới khi có dataset calibration và product duyệt weight tổng hợp.
- Nếu phase sau cần combined score, dùng configuration riêng `contentWeight`/`fluencyWeight`, persist formula version và vẫn expose hai component.

## 8. Proposed Modules/Classes

Package đề xuất: `backend/src/main/java/com/sjp/recruitment/service/fluency`.

| Module/class | Trách nhiệm |
|---|---|
| `GladiaTranscriptionResult` | Typed projection: transcript, audio duration, languages, utterances, words |
| `GladiaWord` / `GladiaUtterance` | Provider timing/confidence evidence; không chứa business scoring |
| `FluencyAnalysisInput` | Provider-neutral input: original transcript, timed words, capture metadata, source |
| `SpeechFluencyAnalyzer` | Orchestrate detectors/calculators; trả immutable result |
| `TranscriptTokenizer` | Unicode-safe tokenization Việt/Anh; versioned normalization |
| `FillerDetector` | Strong/context-dependent filler events, evidence offsets và count |
| `RepetitionDetector` | Longest-match-first single-word/short-phrase immediate repetition |
| `PauseAnalyzer` | Validate timings, gap classification, longest pause, speech onset |
| `SpeechRateCalculator` | Word count, speaking duration, WPM; null-safe |
| `FluencyScoringEngine` | Rule-based component scores + configured weighted score |
| `FluencyFeedbackGenerator` | Tạo câu feedback chỉ từ measured metrics |
| `FluencyProperties` | Bind/validate toàn bộ thresholds, lists, weights, algorithm switches |
| `FluencyAnalysisResult` | Version, source, quality, metrics, score/status, feedback, evidence summary |

Không cần class/service cho emotion, personality, face, pitch/prosody, stress, body language, eye contact hoặc diarization.

### 8.1 Filler detection design

Normalize Unicode, lowercase và punctuation nhưng giữ token index/timestamp gốc để trả evidence.

Nhóm mặc định:

- Strong: `ờ`, `ừ`, `ừm`, `uh`, `um`, `hmm`.
- Context-dependent: `à`, `thì`, `kiểu như`, `nói chung là`, `like`, `you know`.

Rules:

- Strong filler: count khi là standalone token/phrase.
- Context-dependent chỉ count khi có ít nhất một hesitation cue cấu hình được, ví dụ:
  - ở đầu utterance hoặc sau pause đạt threshold,
  - bị lặp,
  - nằm sát strong filler,
  - là parenthetical phrase có boundary rõ.
- `thì` không count trong cấu trúc ngữ pháp bình thường như “nếu ... thì ...”.
- `like` không count trong lexical use như “I like Spring Boot”.
- `you know` không count khi có object/complement trực tiếp như “you know Java” hoặc “you know how...”.
- Khi context không đủ, trả event `candidateContextualFiller` cho diagnostics nhưng không tăng strong count/score penalty.
- Lists, context window và cue rules nằm trong config; mỗi event ghi matched phrase/category để audit.

ASR có thể bỏ hoặc chuẩn hóa vocal fillers. Đây là limitation của transcript-based detection; không dùng LLM để đoán filler bị thiếu.

### 8.2 Repetition/hesitation detection design

Rule-based, longest-match-first:

1. Tokenize và normalize nhưng giữ vị trí/timing.
2. Tìm exact repeated phrase với độ dài tối đa cấu hình, ưu tiên phrase dài trước.
3. Cho phép giữa hai span có punctuation hoặc strong filler giới hạn bởi config.
4. Merge overlapping detections thành một hesitation event.
5. Chỉ xét immediate/near-immediate repetition; không count từ/cụm xuất hiện lại ở phần khác của câu trả lời.

Kỳ vọng:

- `em em nghĩ rằng` → một repeated single-word event.
- `Spring Spring Boot` → một repeated single-word event.
- `theo em theo em thì` → một repeated short-phrase event, không đồng thời đếm thêm từng token.
- `tôi nghĩ à tôi nghĩ` → một repeated short-phrase event với filler ở giữa.

Giảm false positive:

- Không count các occurrence cách xa nhau.
- Không double-count nested/overlapping span.
- Có configurable allowlist cho collocation/emphasis đã được dataset xác nhận.
- Nếu chỉ có transcript và không có timing, đánh dấu evidence quality thấp hơn thay vì suy diễn ý định.

## 9. Proposed Data Model

### 9.1 Đề xuất nhỏ nhất

Không tạo bảng mới. Sau khi schema được duyệt, thêm một cột:

```text
interview_answers.speech_analysis_json jsonb null
```

Entity dùng `@JdbcTypeCode(SqlTypes.JSON)` theo pattern đã có ở `InterviewSession.practiceContext`.

JSON camelCase theo convention API/project:

```json
{
  "analysisVersion": "fluency-v1",
  "tokenizationVersion": "vi-en-v1",
  "configVersion": "fluency-calibration-v1",
  "source": "gladiaPreRecorded",
  "dataQuality": "timedTranscript",
  "originalTranscript": "...",
  "audioDurationSeconds": 74.2,
  "timedWords": [
    { "word": "em", "startSeconds": 1.7, "endSeconds": 1.9, "confidence": 0.97 }
  ],
  "metrics": {
    "totalWords": 152,
    "speakingDurationSeconds": 71.5,
    "speechRateWpm": 127.5,
    "filler": { "count": 6, "rate": 0.039 },
    "repetition": { "count": 3, "rate": 0.02 },
    "pause": {
      "count": 7,
      "longPauseCount": 2,
      "longestPauseSeconds": 2.8
    },
    "speechOnsetSecondsFromRecordingStart": 1.7,
    "responseLatencySeconds": null
  },
  "fluencyScore": null,
  "scoreStatus": "calibrationDisabled",
  "feedback": []
}
```

Lý do giữ compact timed words cùng analysis:

- recalibrate pause thresholds mà không cần giữ raw audio,
- audit từng metric,
- không phụ thuộc raw provider JSON có thể đổi schema,
- giữ original Gladia transcript riêng với transcript đã được candidate sửa.

Nếu privacy/storage policy không cho giữ timed words, chỉ lưu aggregate metrics nhưng chấp nhận không thể recompute theo threshold mới.

### 9.2 Không dùng các field hiện có sai nghĩa

- `InterviewAnswer.durationSeconds`: hiện là whole recording duration do client gửi và làm tròn lên; không đổi nghĩa thành speaking duration.
- `InterviewAnswer.audioUrl`: không tự động lưu provider URL.
- `AiAnswerFeedback.confidenceScore`: không dùng cho ASR confidence hoặc candidate confidence.
- `AiAnswerFeedback.overallScore`: giữ là content score hiện tại.

### 9.3 API response

Thêm optional `fluencyAnalysis` vào `AiInterviewAnswerResponse`; frontend type dùng camelCase tương ứng. Với transcript-only hoặc malformed timing, trả metric có thể đo và `fluencyScore: null`, không trả 0 vì 0 mang nghĩa “rất kém” thay vì “thiếu dữ liệu”.

## 10. Proposed Configuration

Mở rộng prefix hiện tại `app.ai-interview`:

```yaml
app:
  ai-interview:
    fluency:
      enabled: false
      analysis-version: fluency-v1
      tokenization-version: vi-en-v1
      config-version: fluency-calibration-v1
      require-timed-transcript-for-score: true

      pause:
        normal-threshold-seconds: ${FLUENCY_PAUSE_NORMAL_THRESHOLD_SECONDS}
        long-pause-threshold-seconds: ${FLUENCY_PAUSE_LONG_THRESHOLD_SECONDS}

      filler:
        strong:
          - "ờ"
          - "ừ"
          - "ừm"
          - "uh"
          - "um"
          - "hmm"
        context-dependent:
          - "à"
          - "thì"
          - "kiểu như"
          - "nói chung là"
          - "like"
          - "you know"
        context-window-tokens: ${FLUENCY_FILLER_CONTEXT_WINDOW_TOKENS}

      repetition:
        max-phrase-tokens: ${FLUENCY_REPETITION_MAX_PHRASE_TOKENS}
        max-intervening-filler-tokens: ${FLUENCY_REPETITION_MAX_INTERVENING_FILLER_TOKENS}
        max-gap-seconds: ${FLUENCY_REPETITION_MAX_GAP_SECONDS}

      speech-rate:
        target-min-wpm: ${FLUENCY_SPEECH_RATE_TARGET_MIN_WPM}
        target-max-wpm: ${FLUENCY_SPEECH_RATE_TARGET_MAX_WPM}
        floor-wpm: ${FLUENCY_SPEECH_RATE_FLOOR_WPM}
        ceiling-wpm: ${FLUENCY_SPEECH_RATE_CEILING_WPM}

      scoring:
        enabled: false
        speech-rate-weight: ${FLUENCY_SCORE_SPEECH_RATE_WEIGHT}
        filler-weight: ${FLUENCY_SCORE_FILLER_WEIGHT}
        repetition-weight: ${FLUENCY_SCORE_REPETITION_WEIGHT}
        pause-weight: ${FLUENCY_SCORE_PAUSE_WEIGHT}
        filler-rate-bands: ${FLUENCY_SCORE_FILLER_RATE_BANDS}
        repetition-rate-bands: ${FLUENCY_SCORE_REPETITION_RATE_BANDS}
        pause-frequency-bands: ${FLUENCY_SCORE_PAUSE_FREQUENCY_BANDS}
        long-pause-bands: ${FLUENCY_SCORE_LONG_PAUSE_BANDS}
```

Không cung cấp arbitrary numeric defaults trong business logic. Khi `fluency.enabled=false`, app vẫn start mà không cần calibration values. Khi bật analyzer/score, startup validation phải:

- yêu cầu các threshold/weight cần thiết,
- bảo đảm threshold tăng dần và không âm,
- bảo đảm weight không âm và tổng bằng 1,
- fail fast nếu config thiếu hoặc mâu thuẫn,
- lưu `configVersion` cùng result.

### 10.1 Rule-based scoring engine

Inputs:

- `speechRateWpm`
- `fillerRate`
- `repetitionRate`
- `pauseFrequencyPerSpeakingMinute`
- `longPauseCount`

Mỗi component map sang 0–100 bằng configurable band/linear interpolation. Tổng:

```text
fluencyScore =
    speechRateComponent * speechRateWeight
  + fillerComponent * fillerWeight
  + repetitionComponent * repetitionWeight
  + pauseComponent * pauseWeight
```

Rules:

- Clamp kết quả về 0–100 ở boundary layer.
- Không divide-by-zero.
- Không coi missing metric là điểm 100 hoặc 0.
- Với policy khuyến nghị, thiếu timed metrics → `fluencyScore = null`, `scoreStatus = insufficientData`.
- Score engine không gọi AI/LLM.
- Mọi band/weight/version phải persisted để có thể giải thích và calibration lại.

### 10.2 Metric-only feedback

Feedback do code deterministic tạo, ví dụ:

- “Bạn sử dụng 8 từ/cụm từ đệm trong câu trả lời.”
- “Bạn có 3 khoảng dừng dài hơn ngưỡng cấu hình.”
- “Tốc độ nói trung bình là 128 từ/phút theo tokenization vi-en-v1.”
- “Bạn lặp lại từ/cụm từ 4 lần.”

Không phát sinh các câu “căng thẳng”, “thiếu tự tin”, “hướng nội” hoặc suy luận tâm lý khác. LLM content evaluation tiếp tục chỉ nhận transcript, không nhận raw audio.

## 11. Proposed Testing Strategy

### 11.1 Unit tests bắt buộc

| Test | Input | Expected |
|---|---|---|
| Filler strong | `ờ em nghĩ Spring Boot khá tốt` | `filler.count > 0` |
| Context-dependent false positive | `nếu có lỗi thì em rollback` | `thì` không bị count |
| English lexical false positive | `I like Spring Boot` | `like` không bị count |
| Discourse English filler | utterance/pause + `like` + continuation | count theo configured context rule |
| Single-word repetition | `em em nghĩ rằng` | `repetition.count = 1` |
| Extended token repetition | `Spring Spring Boot` | `repetition.count = 1` |
| Phrase repetition | `theo em theo em thì` | một phrase event, không double-count |
| Filler-separated repetition | `tôi nghĩ à tôi nghĩ` | một repetition event |
| Natural distant reuse | cùng từ xuất hiện ở hai câu xa nhau | không count |
| Pause | word1.end = 5, word2.start = 8 | gap = 3 giây; class theo config |
| Overlapping timestamps | word1.end > word2.start | không tạo negative pause |
| Speech rate | 120 words, 60 giây speaking | 120 WPM |
| Empty answer | empty transcript/timing | không crash; counts = 0; timed metric/score null hợp lệ |
| Very short answer | duration 0 hoặc không có word | không divide-by-zero |
| No filler | transcript không có filler | `filler.count = 0` |
| Missing timings | transcript-only | text metrics có; pause/rate/score null |

### 11.2 Provider contract tests

- Fixture Gladia `done` với `metadata`, `full_transcript`, utterances và words.
- Map đúng `word/start/end/confidence`.
- Empty/missing `utterances` không crash; downgrade data quality.
- `status=error/failed` giữ behavior hiện tại.
- Malformed/non-monotonic timing bị reject/flag, không sinh score sai.
- Code-switch fixture `vi` + `en`.
- Test original provider transcript không bị overwrite khi candidate chỉnh content transcript.

Không gọi Gladia thật trong unit test. Trước implementation cần capture một anonymized staging payload thực để khóa contract fixture.

### 11.3 Service/integration tests

- `transcribeCurrentQuestion` persist transcript + speech analysis atomically.
- Confirm edited transcript giữ provider evidence ban đầu.
- Finish vẫn chấm content như hiện tại và không thay `overallScore`.
- Response assembler trả optional fluency analysis đúng.
- Hands-free transcript-only không tạo fabricated timed metrics/score.
- Retry/idempotency không nhân đôi analysis events.

### 11.4 Calibration tests

- Golden audio/transcript set Việt và Việt/Anh có human-labeled fillers, repetitions và pause intervals.
- Đo precision/recall theo từng detector, không chỉ test happy path.
- So Gladia word-gap với labeled silence; chỉ thêm VAD nếu sai số vượt acceptance criteria do product đặt.
- Snapshot theo `analysisVersion`/`configVersion` để phát hiện score drift.

## 12. Files That Would Need Modification

Chưa sửa các file dưới đây. Danh sách cho phase implementation sau khi report được duyệt.

### 12.1 Existing backend files

- `backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java`
  - trả typed result thay vì `String`, map timing/confidence/metadata.
- `backend/src/main/java/com/sjp/recruitment/config/AiInterviewProperties.java`
  - thêm nested fluency configuration và validation.
- `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java`
  - gọi analyzer sau transcription; persist analysis; giữ content evaluation flow.
- `backend/src/main/java/com/sjp/recruitment/model/entity/InterviewAnswer.java`
  - map optional JSONB analysis.
- `backend/src/main/java/com/sjp/recruitment/service/AiInterviewResponseAssembler.java`
  - expose optional analysis.
- `backend/src/main/java/com/sjp/recruitment/model/dto/response/AiInterviewAnswerResponse.java`
  - thêm `fluencyAnalysis`.
- `backend/src/main/java/com/sjp/recruitment/model/dto/response/AiInterviewTranscriptResponse.java`
  - optional analysis/data-quality nếu UI cần preview.
- `backend/src/main/resources/application.yml`
  - thêm disabled-by-default config.
- Một Flyway migration mới
  - chỉ sau schema approval; thêm `speech_analysis_json jsonb`.

### 12.2 New backend files

- Typed Gladia response records trong `service/ai` hoặc package `service/ai/gladia`.
- Các module trong `service/fluency` nêu ở mục 8.
- DTO response fluency trong `model/dto/response`.
- Unit/contract/service tests và anonymized Gladia fixture.

### 12.3 Frontend files

- `frontend/src/types/aiInterview.ts`
  - thêm optional fluency types.
- `frontend/src/App.tsx`
  - hiển thị metric/score/quality riêng trong result; không suy luận tâm lý.
- `frontend/src/hooks/useVoiceConversation.ts`
  - **chỉ phase full coverage** nếu quyết định capture audio/timing cho hands-free.
- `frontend/src/services/aiInterviewService.ts`
  - chỉ cần chỉnh type/endpoint nếu response contract thay đổi.

### 12.4 Dependency files

- `backend/pom.xml`: **không cần đổi cho MVP Gladia timestamps**.
- Chỉ đổi `pom.xml` nếu phase sau được duyệt dùng ONNX/Silero.

## 13. Risks / Limitations

1. **Hai STT path không tương đương**: manual dùng Gladia, hands-free dùng browser Web Speech. Full timed score không thể bao phủ hands-free nếu không thêm capture path.
2. **ASR có thể bỏ filler**: transcript-based detection không thấy âm bị provider loại/normalize. Không được dùng LLM để đoán phần đã mất.
3. **Transcript có thể được candidate sửa**: timed words khớp original Gladia transcript, không nhất thiết khớp final content transcript. Cần persist hai nguồn tách biệt.
4. **Language config hiện là Việt-only**: English code-switching có thể bị nhận sai; thay đổi config cần benchmark.
5. **WPM Việt khác WPM Anh**: whitespace/token count không phản ánh cùng đơn vị ngôn ngữ học; threshold cần dataset nội bộ.
6. **Word-gap không hoàn toàn đồng nghĩa pause chủ ý**: timestamp/segmentation/noise có sai số. Cần benchmark và data-quality flag.
7. **Response latency chưa có anchor đúng**: manual click-to-record bỏ qua thời gian do dự trước click; hands-free chưa lưu onset.
8. **Client duration không authoritative**: frontend đo wall clock và gửi integer làm tròn lên; chỉ nên dùng validation/display, không làm actual speaking duration.
9. **No raw audio replay**: không thể chạy VAD/calibration lại sau request nếu không giữ audio. Đây là privacy-friendly trade-off; timed evidence giúp giảm nhu cầu giữ audio.
10. **Provider retention**: Gladia upload URL bị bỏ nhưng provider vẫn có thể giữ file theo policy ngoài codebase.
11. **Current DB field naming trap**: `confidence_score` dễ bị hiểu nhầm; phải tránh dùng cho ASR/candidate confidence.
12. **Score fairness**: không thay content overall hoặc hiển thị fluency score trước khi thresholds/weights được calibration.
13. **Long-running transaction**: `transcribeCurrentQuestion` hiện poll provider trong `@Transactional`; analyzer không nên làm transaction dài hơn. Có thể refactor boundary ở phase sau nếu đo được contention, nhưng không cần refactor unrelated trong MVP.
14. **No live local database container**: tại thời điểm audit, `smart_recruitment_db` không chạy. Database conclusions dựa trên JPA entities và toàn bộ Flyway migrations V1/V2 liên quan AI interview, không dựa trên query production data.

## 14. Implementation Plan

### Phase 0 — Contract and calibration prerequisites

1. Capture một Gladia V2 `done` response đã anonymize từ staging cho audio Việt và Việt/Anh.
2. Xác nhận response thực có utterances/words/start/end/confidence với account/config hiện tại.
3. Chốt operational definitions: token count, speaking duration algorithm, pause count và response latency.
4. Chốt retention policy cho compact timed words; không lưu raw audio mặc định.

### Phase 1 — Typed Gladia evidence, no scoring

1. Đổi Gladia client sang typed projection nhưng giữ endpoint/provider flow.
2. Thêm contract tests cho exact nested fields.
3. Thêm analyzer modules cho total words, filler, repetition, pause, speaking duration và WPM.
4. Chạy shadow/log-only trong môi trường test; không đổi UI score/overall score.
5. Benchmark timing với labeled samples.

### Phase 2 — Persistence and API, after schema approval

1. Thêm một JSONB column vào `interview_answers` qua Flyway migration mới.
2. Persist original transcript, compact timing evidence, metrics và version.
3. Expose optional `fluencyAnalysis` trong response DTO/TypeScript.
4. Hiển thị deterministic metrics và data-quality label ở result page.
5. Giữ AI content evaluation và `overallScore` hoàn toàn như hiện tại.

### Phase 3 — Rule-based scoring after calibration

1. Đưa approved thresholds/weights vào config, disabled by default.
2. Thêm startup validation và config versioning.
3. Bật score cho `timedTranscript` only.
4. Sinh metric-only feedback bằng code.
5. Theo dõi score distribution/drift; không suy luận emotion/confidence/personality.

### Phase 4 — Hands-free coverage decision

Sau khi MVP manual ổn định, chọn một trong hai:

- Giữ hands-free transcript-only và hiển thị `insufficientData` cho timed score; thay đổi ít nhất.
- Hoặc capture audio song song trong hands-free, dùng Web Speech chỉ cho live interim UI và gửi audio qua backend/Gladia khi chốt answer. Phương án này cần thiết kế segment merge/confirmation kỹ để không làm hỏng UX hiện tại.

Không nên chuyển toàn hệ thống sang Gladia real-time chỉ để có fluency; đó là thay đổi kiến trúc lớn và không cần thiết cho MVP.

### Phase 5 — VAD only if evidence requires it

1. Đặt acceptance criteria cho Gladia pause/speaking-duration error.
2. Nếu không đạt, prototype Silero ONNX offline trên cùng golden audio set.
3. Đánh giá decode/resample cost, latency, memory và deployment.
4. Chỉ sau benchmark mới phê duyệt dependency/migration/retention thay đổi.

## Final Recommendation

Triển khai theo thứ tự **Gladia typed timing → deterministic backend analyzer → optional JSONB persistence → calibrated score**. Không thêm VAD, không thay Gladia, không chạm emotion/confidence/personality, không gửi raw audio sang LLM và không thay semantics của điểm content hiện tại. Hạn chế lớn cần product chấp nhận hoặc xử lý ở phase riêng là chế độ hands-free hiện bypass Gladia; MVP phải biểu diễn thiếu dữ liệu bằng `null/insufficientData`, không fabricate metric hoặc score.
