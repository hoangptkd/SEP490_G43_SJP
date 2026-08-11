# Silero VAD Integration Audit

Ngày audit: 2026-08-11  
Phạm vi: Smart Recruitment Portal, frontend React/TypeScript và backend Spring Boot 3.2.3/Java 17.  
Trạng thái: **audit/thiết kế, chưa implementation**.

## Kết luận điều hành

- Giữ **Gladia** làm Speech-to-Text (STT). Silero chỉ nhận PCM để xác định khoảng **speech/silence**.
- Không dùng xác suất VAD để suy luận emotion, confidence, personality hoặc bất kỳ đặc điểm tâm lý nào.
- Không thay đổi công thức hay giá trị `overallScore` hiện tại. Nếu bổ sung fluency metrics sau này, chúng phải là dữ liệu mô tả độc lập cho đến khi có một quyết định scoring riêng.
- Input do `MediaRecorder` tạo hiện **không được khóa container/codec**. Trên Chromium thường là WebM/Opus, nhưng code chỉ biết chính xác tại runtime qua `recorder.mimeType`.
- Backend hiện nhận `MultipartFile` và gửi nguyên file sang Gladia; project chưa có decoder PCM, ONNX Runtime, FFmpeg, JavaCV hay TarsosDSP.
- Phương án ít rủi ro nhất cho codebase hiện tại là: **FFmpeg process được provision rõ trong Ubuntu VPS** để decode/resample, sau đó chạy **Silero VAD v6.2.1 `silero_vad.onnx` (opset 16)** bằng `com.microsoft.onnxruntime:onnxruntime:1.23.1` CPU.
- Một `OrtEnvironment` và một `OrtSession` có thể dùng chung toàn ứng dụng. Recurrent state và context của Silero phải tách biệt và reset cho từng audio answer/capture.
- Không được chèn decode/VAD vào giữa transaction dài hiện tại. Cần tách claim/persist thành transaction ngắn và chạy Gladia + VAD bên ngoài transaction.
- Hands-free cần ghi âm song song với Web Speech API, nhưng phải dừng/pause capture trước lúc hệ thống đọc câu xác nhận để không thu giọng TTS. Upload phải gắn `sessionId + questionId + captureId` và có idempotency.

## 1. Current Audio Format

### 1.1 MediaRecorder tạo format/container/codec gì?

Trong [`frontend/src/App.tsx`](frontend/src/App.tsx), luồng ghi thủ công hiện làm như sau:

- Xin microphone bằng `navigator.mediaDevices.getUserMedia({ audio: true })` tại dòng 6386.
- Khởi tạo `new MediaRecorder(stream)` tại dòng 6389, **không truyền `mimeType`**.
- Khi dừng, tạo `Blob` với `recorder.mimeType || 'audio/webm'` tại dòng 6395.
- Luôn đặt tên file có đuôi `.webm` tại dòng 6396.

Vì không chỉ định MIME khi khởi tạo, browser tự chọn container/codec. Kết luận chính xác từ source là:

- **Container/codec không được source code bảo đảm.**
- MIME thực tế là giá trị runtime của `recorder.mimeType`.
- Trên Chrome/Edge thông thường có khả năng cao là `audio/webm;codecs=opus`, nhưng đây chỉ là kỳ vọng theo browser, không phải invariant của hệ thống.
- Tên file `.webm` có thể không khớp container thực nếu browser chọn MIME khác.

Trước implementation cần kiểm tra `MediaRecorder.isTypeSupported(...)`, chọn danh sách ưu tiên rõ ràng và lưu MIME thực tế. Không được suy codec chỉ từ extension.

### 1.2 MIME type frontend gửi backend là gì?

Có hai lớp Content-Type:

1. HTTP request là `multipart/form-data`, được set trong [`frontend/src/services/aiInterviewService.ts`](frontend/src/services/aiInterviewService.ts) dòng 68.
2. Part `file` dùng `File.type`, được kế thừa từ `Blob.type`: thường là `recorder.mimeType`, fallback `audio/webm`.

Do đó frontend **không cố định** file part là một MIME duy nhất. Nó thường gửi `audio/webm;codecs=opus` hoặc `audio/webm`, tùy browser/runtime.

### 1.3 Backend nhận audio dưới kiểu nào?

[`AiInterviewController.java`](backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java) dòng 79-84 nhận:

- `@RequestPart("file") MultipartFile file`
- `@RequestPart(value = "durationSeconds", required = false) Integer durationSeconds`

Endpoint hiện tại là:

```text
POST /candidate/ai-interviews/sessions/{sessionId}/questions/current/audio
Content-Type: multipart/form-data
```

Backend không nhận sẵn PCM hay sample array; nó nhận một multipart upload còn nguyên container/codec.

## 2. Current Audio Flow

### 2.1 Ghi âm thủ công

```text
getUserMedia
  -> MediaRecorder (browser tự chọn MIME/codec)
  -> Blob/File
  -> multipart file + durationSeconds
  -> Spring MultipartFile
  -> validate MIME + 12-byte magic header
  -> Gladia upload/poll STT
  -> lưu transcript
```

[`AiInterviewService.java`](backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java) dòng 668-705 chỉ xác thực:

- file không rỗng;
- size/duration trong giới hạn;
- MIME có vẻ là audio/WebM/Ogg;
- magic header giống WAV, Ogg, MP3 hoặc WebM.

Kiểm tra này nhận diện sơ bộ container, **không xác minh codec, channel count, sample rate, duration thật hoặc khả năng decode**.

[`GladiaTranscriptionClient.java`](backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java) dòng 28-68 gửi cùng `MultipartFile` sang Gladia và dùng `file.getContentType()`, fallback `audio/webm`. Audio hiện không được lưu lâu dài trong backend và không được decode cục bộ.

### 2.2 Hands-free hiện tại

[`frontend/src/hooks/useVoiceConversation.ts`](frontend/src/hooks/useVoiceConversation.ts) hiện chỉ dùng Web Speech API:

```text
đọc câu hỏi
  -> dừng SpeechRecognition trong lúc TTS phát
  -> câu hỏi phát xong
  -> startAnswerRecognition
  -> nhận interim/final transcript
  -> hết khoảng silence phía client
  -> dừng recognition, đọc câu xác nhận
  -> nghe "đã xong/chưa xong"
  -> positive: onConfirm(transcript)
  -> negative: đọc lời mời tiếp tục rồi startAnswerRecognition lại
```

Hands-free hiện **không tạo audio file và không gọi endpoint audio**. Vì vậy Silero chưa có dữ liệu để xử lý ở mode này.

### 2.3 Transactional flow hiện tại

`transcribeCurrentQuestion(...)` có `@Transactional` tại dòng 233. Transaction hiện bao trùm cả:

- load/claim answer;
- upload audio sang Gladia;
- start STT;
- poll kết quả mạng;
- persist transcript/status.

Nếu thêm FFmpeg và ONNX ngay vào method này, thời gian giữ transaction/DB connection sẽ dài hơn và failure surface tăng. Đây là điểm cần refactor trước integration, dù Gladia vẫn là STT chính.

## 3. Decoder Options

### 3.1 Thư viện decode audio đang tồn tại

Audit `backend/pom.xml` và source cho thấy:

- Không có audio decoder chuyển WebM/Opus thành PCM.
- Apache Tika đang có, nhưng mục đích là nhận diện/parser metadata; không nên coi là WebM/Opus PCM decoder.
- Không có ONNX Runtime hoặc Silero.

### 3.2 FFmpeg, JavaCV, TarsosDSP, javax.sound hoặc tương tự

| Lựa chọn | Hiện có trong project | Nhận xét |
|---|---:|---|
| FFmpeg executable/process | Không | Không có code `ProcessBuilder`, không có Docker/provisioning cài FFmpeg. |
| JavaCV/FFmpeg binding | Không | Không có dependency `org.bytedeco`. |
| TarsosDSP | Không | Không có dependency/source sử dụng. |
| `javax.sound.sampled` | Có trong JDK Java 17 nhưng không được dùng | Java Sound mặc định hỗ trợ tốt WAV/AIFF/AU và PCM; không phải decoder WebM/Opus tiêu chuẩn. |
| Thư viện audio pure Java khác | Không | Không thấy WebM demuxer, Opus decoder hoặc resampler. |

Oracle xác nhận Java Sound reference implementation tập trung vào AIFF/AU/WAV và PCM/a-law/mu-law; format khác cần SPI/plugin. Vì vậy `AudioSystem` đơn thuần không giải quyết input WebM/Opus. Tham khảo [Java Sound Technology](https://docs.oracle.com/javase/8/docs/technotes/guides/sound/index.html) và [Java Sound troubleshooting](https://docs.oracle.com/en/java/javase/17/troubleshoot/java-sound.html).

### 3.3 Khả năng gọi native binary của runtime/deployment

Repo có tài liệu deploy backend dưới dạng systemd service trên Ubuntu VPS, build JAR bằng Maven và restart bằng `systemctl` trong [`docs/UBUNTU_VPS_DEPLOY.md`](docs/UBUNTU_VPS_DEPLOY.md). Không có container sandbox hoặc policy trong repo ngăn JVM dùng `ProcessBuilder`.

Kết luận:

- **Về kỹ thuật JVM/OS:** có thể gọi native process nếu user của service có quyền execute.
- **Về năng lực đã được project bảo đảm:** chưa có. Repo không cài/pin FFmpeg, không kiểm tra binary khi startup, không khai báo absolute path, và không cung cấp systemd unit để audit quyền/giới hạn.
- Trước implementation phải xác nhận kiến trúc VPS (`x86_64` hay ARM), user chạy service, `NoNewPrivileges`, `PrivateTmp`, filesystem permissions và version FFmpeg thực tế.

### 3.4 So sánh ba phương án

| Tiêu chí | FFmpeg process | JavaCV/FFmpeg binding | Pure Java audio library |
|---|---|---|---|
| WebM/Opus coverage | Rất tốt | Rất tốt | Không đồng đều; thường cần ghép demux + Opus + resample |
| Thay đổi Maven/JAR | Nhỏ | Lớn; native artifacts đáng kể | Trung bình đến lớn, nhiều thành phần |
| Native dependency | Binary bên ngoài, dễ health-check | Native libs nhúng/binding | Có thể tránh native, tùy codec lib |
| Packaging Ubuntu VPS | Cần provision FFmpeg rõ ràng | Phức tạp theo OS/architecture/classifier | Dễ hơn nếu thực sự pure Java |
| Isolation khi decoder crash/hang | Process riêng, có thể timeout/kill | Cùng JVM/native memory | Cùng JVM |
| API/code complexity | Thấp nếu output raw PCM/temp file | Cao hơn | Cao nhất cho WebM/Opus + resample đúng |
| Upgrade/CVE surface | FFmpeg OS package riêng | FFmpeg + JavaCV/JavaCPP artifacts | Nhiều library nhỏ, coverage khó kiểm chứng |
| Phù hợp codebase hiện tại | **Tốt nhất** | Không ưu tiên | Không ưu tiên |

JavaCV chính thức cung cấp wrappers cho FFmpeg qua JavaCPP và platform binaries; `javacv-platform` có thể kéo binaries cho nhiều platform nếu không giới hạn classifier, làm tăng packaging/compatibility surface. Tham khảo [JavaCV repository](https://github.com/bytedeco/javacv).

## 4. Recommended Decoder

Chọn **FFmpeg process** với điều kiện FFmpeg trở thành dependency vận hành được khai báo và kiểm tra, không phải giả định ngầm.

### 4.1 Pipeline WebM/Opus -> mono PCM 16 kHz

Đề xuất FFmpeg đọc input đã spool vào temp file và xuất raw signed 16-bit little-endian PCM:

```text
ffmpeg
  -nostdin
  -hide_banner
  -loglevel error
  -i <validated-temporary-input>
  -map 0:a:0
  -vn
  -ac 1
  -ar 16000
  -f s16le
  <validated-temporary-output>
```

Ý nghĩa đầu ra:

- một channel (`-ac 1`);
- sample rate 16,000 Hz (`-ar 16000`);
- signed PCM 16-bit little-endian (`-f s16le`);
- không header, mỗi sample đúng 2 byte.

FFmpeg có tài liệu chính thức về CLI và raw PCM format tại [ffmpeg documentation](https://www.ffmpeg.org/ffmpeg-all.html).

### 4.2 Guardrails bắt buộc

- Dùng `ProcessBuilder` với danh sách argument cố định; **không qua shell**.
- Binary path là config absolute path, ví dụ `vad.decoder.ffmpeg-path`; không lấy từ tên file/user input.
- Tên temp do server sinh ngẫu nhiên; không dùng `getOriginalFilename()` làm path.
- Có timeout, drain `stderr`, kill process khi timeout và giới hạn output.
- Sau decode phải kiểm tra output không rỗng, số byte chẵn, duration PCM không vượt giới hạn và tương thích duration khai báo trong tolerance định cấu hình.
- Xóa input/output temp trong `finally` và giới hạn quyền file.
- Có startup health indicator kiểm tra binary/version/codec support.
- Pin dải version FFmpeg trong runbook/provisioning và theo dõi security updates.

Không nên stream `stdout` vô hạn mà không có output cap. Với giới hạn hiện tại 180 giây, raw PCM tối đa dự kiến là `180 * 16000 * 2 = 5,760,000` byte; đây là cap dễ kiểm soát.

## 5. Silero ONNX Model Choice

### 5.1 Model đề xuất

- Release: **Silero VAD v6.2.1**, release ngày 2026-02-24.
- File: `src/silero_vad/data/silero_vad.onnx`.
- ONNX opset: **16**.
- Size của file đã audit: **2,327,524 bytes**.
- SHA-256 của artifact đã audit: `1A153A22F4509E292A94E67D6F9B85E8DEB25B4988682B7E174C65279D8788E3`.
- License upstream: MIT; cần giữ attribution/license notice.

Lý do chọn file này:

- Đây là model mặc định được loader ONNX của release chọn cho opset 16.
- Hỗ trợ 8 kHz và 16 kHz, streaming/stateful theo caller.
- Java example trong cùng tag dùng `silero_vad.onnx` và ONNX Runtime 1.23.1.
- `silero_vad_op18_ifless.onnx` mới hơn về graph form nhưng không phải default path của loader/Java example; chưa có lợi ích đủ rõ để nhận thêm rủi ro tương thích.
- `silero_vad_16k_op15.onnx` phù hợp khi buộc dùng opset 15, nhưng không cần thiết cho thiết kế này.

Tham khảo [Silero VAD release v6.2.1](https://github.com/snakers4/silero-vad/releases) và [version/model matrix](https://github.com/snakers4/silero-vad/wiki/Version-history-and-Available-Models).

### 5.2 Quản lý artifact

- Vendor model vào backend resource hoặc artifact store nội bộ ở implementation phase.
- Không tải model từ internet trong runtime request/startup production.
- Pin tag, filename, byte size và SHA-256; fail health check nếu hash sai.
- Kiểm tra model bằng test vectors trước release. ONNX Runtime cũng lưu ý ứng dụng phải tự xác thực độ phù hợp và độ an toàn của model; xem [ORT model validation guidance](https://onnxruntime.ai/docs/).

## 6. ONNX Runtime Integration Design

### 6.1 Dependency Java cần thêm

Dependency CPU đề xuất, **chưa thêm trong bước audit**:

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.23.1</version>
</dependency>
```

Đây là version được Java example của Silero v6.2.1 pin. Artifact CPU chính thức hỗ trợ Windows x64, Linux x64 và macOS x64; Java 17 của project đáp ứng yêu cầu Java 8+. Cần xác nhận VPS là Linux x64 trước implementation. Tham khảo [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html).

Không dùng `onnxruntime_gpu`: model nhỏ, workload ngắn, và project không có CUDA provisioning.

### 6.2 Thành phần logic đề xuất

```text
AudioIngestionService
  -> TemporaryAudioResource
  -> AudioDecoder (FfmpegAudioDecoder)
  -> PcmAudio {16 kHz, mono, s16le}
  -> SileroVadAnalyzer
       -> shared SileroModelRuntime (OrtEnvironment + OrtSession)
       -> per-answer SileroInferenceState
       -> VadSegmenter
  -> VadAnalysisResult {segments, durations, status, modelVersion}
```

Ranh giới trách nhiệm:

- Decoder chỉ chuẩn hóa audio.
- Runtime chỉ chạy tensor inference.
- Segmenter chỉ biến speech probability thành speech/silence segments.
- Metrics chỉ tính duration/ratio/pause từ segments.
- Không module nào ánh xạ VAD probability sang emotion/confidence/personality/overallScore.

### 6.3 Transaction boundary đề xuất

```text
Transaction A (ngắn)
  -> validate session/question/captureId
  -> claim answer attempt, processing status/version

Ngoài transaction
  -> spool upload một lần
  -> Gladia STT
  -> FFmpeg decode + Silero VAD

Transaction B (ngắn)
  -> verify claim/captureId/version vẫn hợp lệ
  -> persist transcript và VAD result/status
```

Gladia và VAD có thể chạy song song sau khi spool input, nhưng không dùng chung một `InputStream`. Nếu cần đơn giản hóa phase đầu, chạy tuần tự ngoài transaction cũng an toàn hơn việc chạy trong transaction.

## 7. Tensor/Input/Output Design

Thiết kế dưới đây áp dụng cho `silero_vad.onnx` v6.2.1, batch size 1, sample rate 16 kHz.

### 7.1 Input tensors

| Tên input | Kiểu | Shape | Giá trị |
|---|---|---|---|
| `input` | `float32` | `[1, 576]` | 64 mẫu context trước + 512 mẫu frame hiện tại |
| `state` | `float32` | `[2, 1, 128]` | recurrent state; zero khi bắt đầu answer |
| `sr` | `int64` | `[1]` | `[16000]` |

Model chính thức nhận frame logic 512 mẫu ở 16 kHz. Wrapper phải nối thêm 64 mẫu context, nên tensor `input` gửi model có 576 phần tử. Ở frame đầu, context là zero.

### 7.2 Outputs

| Tên output | Kiểu/shape dự kiến | Cách dùng |
|---|---|---|
| `output` | `float32 [1,1]` | Speech probability của frame hiện tại |
| `stateN` | `float32 [2,1,128]` | State mới cho frame tiếp theo |

Chỉ `output[0][0]` được đưa vào thuật toán segmentation. `stateN` không phải metric; nó chỉ là recurrent state nội bộ.

### 7.3 Stateful inference và lifecycle

`OrtSession.run(...)` không tự lưu state giữa các lần gọi. Tuy nhiên pipeline Silero là **stateful ở cấp caller**, vì mỗi frame kế tiếp cần:

- `stateN` của frame trước;
- 64 sample cuối của input audio frame trước làm context.

Lifecycle bắt buộc cho mỗi audio answer/capture:

```text
new answer/capture
  -> state = zeros [2,1,128]
  -> context = zeros [1,64]
  -> for each 512-sample frame:
       build input [context + frame]
       run(input, state, sr)
       probability = output
       state = stateN
       context = last 64 samples of current frame input buffer
  -> close per-frame native resources
  -> discard state/context at answer end
```

Không được tái sử dụng state/context từ question trước, candidate khác hoặc request khác.

### 7.4 PCM sample -> float tensor

FFmpeg output là signed 16-bit little-endian. Mỗi cặp byte được đổi thành Java `short`, rồi chuẩn hóa:

```text
sample16 = signed little-endian short
floatSample = sample16 / 32768.0f
```

Kết quả nằm trong khoảng `[-1.0, 1.0)`; nếu nguồn decode khác có thể clamp về `[-1.0, 1.0]`. Không dùng absolute value, không normalize theo peak của từng answer vì sẽ thay đổi đặc tính model.

Frame cuối thiếu mẫu được zero-pad đến 512 để inference; timestamp/end segment phải clamp về số sample thật, không tính padding thành audio thật.

## 8. VAD Segmentation Algorithm

### 8.1 Sample rate và frame/chunk size

- Sample rate chuẩn: **16,000 Hz**.
- Channel: **mono**.
- Frame: **512 samples = 32 ms**.
- Context model: **64 samples = 4 ms**.
- Không đưa trực tiếp frame MediaRecorder/WebM vào ONNX; luôn decode/resample trước.

Silero hỗ trợ 8 kHz và 16 kHz, nhưng 16 kHz phù hợp hơn cho mic interview và tránh thêm nhánh xử lý. Tham khảo [Silero FAQ](https://github.com/snakers4/silero-vad/wiki/FAQ).

### 8.2 Thuật toán segment

1. Chạy model tuần tự trên từng frame 512 mẫu để lấy `speechProbability`.
2. Khi chưa ở speech, mở segment nếu probability `>= speechThreshold`.
3. Khi đang ở speech, chỉ đánh dấu candidate end nếu probability `< negativeThreshold`.
4. Chỉ đóng segment khi trạng thái thấp kéo dài ít nhất `minSilenceDuration`.
5. Nếu speech quay lại trước thời gian đó, hủy candidate end để tránh cắt câu bởi pause rất ngắn.
6. Loại segment có duration thật `< minSpeechDuration`.
7. Có thể thêm `speechPadDuration` ở hai đầu rồi clamp vào `[0, audioDuration]`.
8. Silence segments là phần bù giữa các speech segments; pause giữa speech segments được tính từ `next.start - previous.end`.

Nên dùng hysteresis tương tự upstream: `negativeThreshold` thấp hơn `speechThreshold`, thay vì một threshold duy nhất gây rung trạng thái ở vùng nhiễu. Upstream dùng mặc định tham chiếu `max(threshold - 0.15, 0.01)`; đây chưa phải giá trị production của project.

### 8.3 Config threshold đề xuất, chưa hard-code cuối cùng

Các property cần externalize:

```text
vad.segmentation.speech-threshold
vad.segmentation.negative-threshold
vad.segmentation.min-speech-duration-ms
vad.segmentation.min-silence-duration-ms
vad.segmentation.speech-pad-ms
```

Baseline upstream để bắt đầu benchmark, **không phải quyết định cuối cùng**:

| Config | Baseline tham chiếu upstream | Cách chốt cho SJP |
|---|---:|---|
| speech probability threshold | 0.50 | Sweep trên tập audio tiếng Việt thực tế theo browser/mic/noise. |
| negative threshold | speech threshold - 0.15 | Kiểm tra false split và false continuation. |
| min speech duration | 250 ms | Benchmark từ ngắn hơn đến dài hơn để không bỏ từ/câu trả lời rất ngắn. |
| min silence duration | 100 ms | Benchmark cao hơn cho định nghĩa pause trong interview; không đồng nhất với client confirmation timeout. |
| speech pad | 30 ms | Kiểm tra onset/offset accuracy, không dùng để inflate speaking duration. |

Silero khuyến nghị tune `threshold`, `min_speech_duration_ms` và `min_silence_duration_ms` trên dữ liệu của ứng dụng; xem [Silero tuning FAQ](https://github.com/snakers4/silero-vad/wiki/FAQ) và [upstream segmentation implementation](https://github.com/snakers4/silero-vad/blob/master/src/silero_vad/utils_vad.py).

### 8.4 Output dùng để tạo speech segments

Chỉ dùng chuỗi `output` speech probability theo frame cùng timestamp sample index. `stateN` không tạo segment trực tiếp.

Kết quả lưu/return nên gồm tối thiểu:

- `speechSegments[{startMs,endMs}]`;
- `speechDurationMs`;
- `silenceDurationMs`;
- `speechRatio`;
- `pauseCount` theo định nghĩa config;
- `modelVersion`, `decoderVersion`, `status`.

Không dùng mean/max probability làm confidence score của ứng viên. Nếu giữ probability cho debug/QA, phải giới hạn retention và không đưa vào đánh giá con người.

## 9. Resource/Concurrency Design

### 9.1 OrtEnvironment và OrtSession

- `OrtEnvironment.getEnvironment()` dùng **một instance toàn app**.
- Một `OrtSession` sau khi khởi tạo có thể reuse và chạy `run` từ nhiều thread. Maintainer ONNX Runtime xác nhận Java Environment là singleton và Session thread-safe sau construction; xem [ONNX Runtime thread-safety discussion](https://github.com/microsoft/onnxruntime/discussions/10107).
- Không tạo session cho mỗi request; việc này tốn model load, native memory và thread pools.
- `SessionOptions` khởi tạo một lần; bắt đầu với `interOpNumThreads=1` và `intraOpNumThreads=1`, giống wrapper chính thức của Silero, rồi benchmark.
- Thêm bounded executor/semaphore cho số audio được VAD đồng thời để chống CPU/memory saturation. Shared session thread-safe không có nghĩa là concurrency vô hạn an toàn.

ONNX Runtime session tự có threading; cấu hình quá nhiều session/thread có thể gây contention. Tham khảo [ORT thread management](https://onnxruntime.ai/docs/performance/tune-performance/threading.html).

### 9.2 State isolation

Tách hai lớp:

- `SileroModelRuntime`: immutable/shared, giữ environment/session/model metadata.
- `SileroInferenceState`: per answer/request, giữ `float[2][1][128] state`, `float[1][64] context`, current sample và segmenter state.

Không đặt `state`, `context`, `triggered`, `tempEnd` trong singleton bean mutable. Đây là rủi ro concurrency lớn nhất khi port trực tiếp Java example upstream, vì example gắn state vào cùng object chứa session.

### 9.3 Memory và cleanup

- Đóng `OnnxTensor` input/state/sr và `OrtSession.Result` bằng try-with-resources ở mỗi inference.
- Đóng `OrtSession` tại bean destruction; environment đóng sau session khi app shutdown.
- Đóng `SessionOptions` sau khi session được tạo nếu API/version cho phép và không còn dùng.
- Đóng mọi stream/channel và xóa temp files trong `finally`.
- Draining `stderr` của FFmpeg phải chạy an toàn để process không block vì đầy pipe.
- Theo dõi heap và native memory của ONNX Runtime; Java GC không thay thế `close()` cho native handles.
- Với 180 giây: PCM `s16le` khoảng 5.76 MB; nếu giữ toàn bộ `float[]` khoảng 11.52 MB. Có thể stream frame để giảm heap, nhưng cần giữ segment metadata, không cần giữ toàn probability array ở production.
- Nếu Gladia và VAD chạy song song, spool upload một lần ra temp input và mở reader riêng; không dùng cùng `MultipartFile.getInputStream()` đồng thời.

## 10. Hands-Free Audio Capture Design

### 10.1 Nguyên tắc

- Web Speech API tiếp tục cung cấp interim/final transcript realtime cho UX.
- `MediaRecorder` chạy song song chỉ để tạo audio upload cho Gladia/VAD.
- Không capture câu hỏi/TTS, câu hỏi xác nhận hoặc câu nói “tiếp tục trả lời”; nếu không, VAD sẽ coi giọng hệ thống là speech của candidate.
- Một answer attempt có một `captureId` ổn định, dù SpeechRecognition tự restart nhiều lần.

### 10.2 Lifecycle bắt buộc

```text
QUESTION_ENDED
  Câu hỏi/TTS phát xong hoàn toàn.
  Tạo captureId cho questionId hiện tại.

START_CAPTURE
  Xin/reuse microphone stream.
  Start MediaRecorder.
  Start Web Speech answer recognition.

CANDIDATE_SPEAKS
  Web Speech cập nhật transcript realtime.
  MediaRecorder thu cùng khoảng candidate answer.
  Recognition restart nội bộ không được tạo captureId mới.

STOP/PAUSE_CAPTURE_FOR_CONFIRMATION
  Khi client silence timer hết: dừng answer recognition.
  Pause MediaRecorder trước khi phát "Bạn đã trả lời xong chưa?".
  Không thu TTS và confirmation speech.

CONFIRM_ANSWER
  Positive:
    stop MediaRecorder, đợi onstop/dataavailable cuối,
    freeze transcript + audio + questionId + captureId,
    chuyển sang UPLOADING và chỉ submit một lần.
  Negative:
    phát lời mời tiếp tục,
    sau khi lời phát kết thúc mới resume MediaRecorder,
    restart answer recognition, giữ cùng captureId.

UPLOAD_AUDIO
  Gửi multipart atomically hoặc finalize bằng captureId.
  Thành công -> COMMITTED; chuyển question mới.
  Retry -> giữ nguyên captureId và payload đã freeze.
```

### 10.3 Pause/resume và fallback

Ưu tiên một `MediaRecorder` với `pause()/resume()` để tạo một container hợp lệ, nhưng phải test Chrome/Edge/Safari target. Trước lúc gọi `pause`, chờ state `recording`; trước upload phải chờ `onstop` và data chunk cuối.

Nếu browser không hỗ trợ pause ổn định:

- Tạo nhiều capture segments với cùng `captureId` và `sequence`.
- Upload parts theo thứ tự; backend decode từng part rồi nối PCM, có thể chèn boundary silence được cấu hình.
- Không nối byte các WebM Blob độc lập bằng phép concatenate đơn giản vì có thể tạo container không hợp lệ.

### 10.4 Nguy cơ duplicate/mismatch hiện tại

Nguy cơ **có thực** nếu thêm capture một cách trực tiếp:

- Positive confirmation có thể fire từ timer, recognition result và UI gần đồng thời.
- Network retry có thể upload cùng audio hai lần.
- Endpoint `/questions/current/audio` có thể xử lý nhầm question nếu UI/backend đã advance question.
- `onstop` bất đồng bộ: transcript có thể được submit trước khi audio Blob hoàn tất.
- SpeechRecognition restart có thể vô tình tạo nhiều recorder/capture.
- Candidate sửa transcript sau STT nhưng audio gắn với transcript cũ.

Cần frontend state machine tối thiểu:

```text
idle -> recording -> paused-for-confirmation -> stopping -> uploading -> committed
                                      \-> recording (negative confirmation)
```

Chỉ một transition sang `uploading` được phép cho mỗi `captureId`; disable/nop các confirm event sau đó.

### 10.5 Correlation mechanism

Đề xuất payload/identity:

- `sessionId`: từ route/session hiện tại.
- `questionId`: explicit, không suy từ “current”.
- `captureId`: UUID client tạo khi `QUESTION_ENDED`/`START_CAPTURE`.
- `attemptNumber` hoặc `captureSequence`: backend cấp hoặc validate monotonic.
- `transcript`: bản candidate xác nhận.
- `transcriptHash`: SHA-256 UTF-8 của transcript đã canonicalize; dùng audit/check, không thay transcript gốc.
- `audioSegments[].sequence` nếu cần fallback multi-part.

Endpoint hands-free nên question-scoped, ví dụ:

```text
POST /candidate/ai-interviews/sessions/{sessionId}/questions/{questionId}/answer-captures
Idempotency-Key: {captureId}
multipart: audio (hoặc audioSegments), transcript, captureId, durationSeconds, transcriptHash
```

Backend cần unique constraint/idempotency trên `(answer_id, capture_id)` và trả lại cùng kết quả nếu retry cùng payload. Nếu cùng `captureId` nhưng hash/audio metadata khác, trả conflict thay vì ghi đè.

Một request multipart atomically chứa transcript + audio là phương án đơn giản nhất để tránh mismatch. Nếu phải dùng two-phase upload, `confirmAnswer` chỉ được finalize bằng `captureId` đã upload, không bằng “current question”.

Web Speech transcript phục vụ realtime/candidate-confirmed content; Gladia transcript vẫn là STT provider output. Nếu hai transcript khác nhau, lưu provenance riêng, không âm thầm gắn audio của capture A với transcript của capture B.

## 11. Error Handling

Nguyên tắc degradation: lỗi VAD **không được làm mất Gladia transcript hay chặn answer**. Kết quả VAD có trạng thái rõ ràng thay vì số 0 giả.

| Failure | Hành vi đề xuất | Không được làm |
|---|---|---|
| Audio decode fail | Ghi `vadStatus=decode_failed`; Gladia tiếp tục; log code + captureId; cho phép retry VAD nếu còn artifact trong retention ngắn. | Không đặt speech duration = 0; không fail STT thành công. |
| ONNX model load fail | Feature health `DOWN/DEGRADED`; `vad.enabled` vẫn có thể degrade; alert ops; Gladia flow hoạt động. | Không crash toàn app trong rollout ban đầu nếu `vad.required=false`. |
| Inference fail | Hủy partial VAD result, reset/discard state, `vadStatus=inference_failed`; transcript vẫn persist. | Không lưu segments một phần như completed. |
| Invalid audio | Reject nếu container/content thật invalid cho cả STT; nếu Gladia vẫn xử lý nhưng local decoder không, đánh dấu decode failure riêng. | Không tin MIME/extension hoặc 12-byte magic là đủ. |
| FFmpeg missing/timeout/non-zero | `decoder_unavailable`/`decode_timeout`/`decode_failed`; health/metrics/alert; kill process. | Không treo request vô hạn. |
| Invalid PCM | Kiểm tra byte count chẵn, sample count > 0, duration cap; VAD unavailable. | Không cấp tensor có shape sai. |
| Capture duplicate | Idempotently trả kết quả trước nếu payload trùng; conflict nếu payload khác. | Không gọi Gladia/VAD lần hai cho retry giống nhau. |
| Question mismatch/stale | `409` với mã rõ; giữ payload client để retry đúng question. | Không route theo implicit current question. |

Model load có thể chọn hai policy qua config:

- Rollout/shadow: `vad.required=false` (đề xuất ban đầu), app khởi động nhưng health thể hiện degraded.
- Sau khi vận hành ổn định: cân nhắc `vad.required=true` để fail-fast cho deployment cấu hình sai, nhưng đây là quyết định vận hành riêng.

## 12. Security/Privacy

- Audio phỏng vấn là dữ liệu cá nhân nhạy cảm; chỉ thu sau consent rõ và hiển thị trạng thái microphone/recording.
- Không thu khi phát câu hỏi, prompt xác nhận hoặc khi tab/session đã kết thúc.
- Dừng mọi mic track ở stop/unmount/error/question change; tránh microphone còn mở ngầm.
- Không lưu raw audio lâu hơn mục đích STT/VAD. Định nghĩa TTL cho temp file và cleanup khi process crash/restart.
- Temp directory riêng, permission tối thiểu, random filename, không public URL.
- Không ghi raw audio, transcript, tensor, probability trace hay provider key vào log.
- Process FFmpeg không chạy qua shell; arguments/path không lấy trực tiếp từ user.
- Giới hạn upload, decoded duration, output bytes, timeout và concurrency để chống decompression bomb/DoS.
- Verify MIME bằng probe/decode thực, không tin client MIME và extension.
- Vendor model từ nguồn tin cậy, pin SHA-256 và license; không cho request chỉ định model path.
- Dependency scanning phải bao gồm ONNX Runtime native library và FFmpeg package.
- VAD chỉ đo speech/silence. Không dùng để suy luận sức khỏe, cảm xúc, tự tin, tính cách, giới tính, tuổi hoặc accent.

## 13. Files To Modify

Danh sách dự kiến cho implementation phase; **audit này chưa sửa các file này**.

### Backend hiện có

- `backend/pom.xml`: thêm ONNX Runtime dependency.
- `backend/src/main/resources/application.yml`: config feature/model/decoder/threshold/timeouts/concurrency.
- `backend/src/main/java/com/sjp/recruitment/config/AiInterviewProperties.java`: hoặc tách `VadProperties` typed config.
- `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java`: question-scoped/capture-aware multipart endpoint.
- `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java`: tách transaction và orchestration STT/VAD.
- `backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java`: có thể nhận temp resource/path abstraction thay vì đọc lại multipart trực tiếp.
- Entity/repository/response/migration liên quan `InterviewAnswer`: lưu capture idempotency, VAD status/result/model version; exact schema cần quyết định ở implementation design.
- `docs/UBUNTU_VPS_DEPLOY.md`: provision/check FFmpeg và VAD health, không ghi secret.

### Backend mới dự kiến

- `config/VadProperties.java`
- `service/audio/AudioDecoder.java`
- `service/audio/FfmpegAudioDecoder.java`
- `service/audio/DecodedPcmAudio.java`
- `service/vad/SileroModelRuntime.java`
- `service/vad/SileroInferenceState.java`
- `service/vad/SileroVadAnalyzer.java`
- `service/vad/VadSegmenter.java`
- `model/.../VadAnalysisResult.java`
- model resource `backend/src/main/resources/models/silero-vad/v6.2.1/silero_vad.onnx` và license/metadata/checksum.
- Unit/integration tests cho decoder, tensor, state reset, segmentation, concurrency, degradation và idempotency.

### Frontend

- `frontend/src/hooks/useVoiceConversation.ts`: MediaRecorder lifecycle song song, state machine, pause/resume, cleanup.
- `frontend/src/services/aiInterviewService.ts`: question-scoped capture upload và idempotency data.
- `frontend/src/App.tsx` hoặc component interview tương ứng: wiring audio payload/capture state, không tạo duplicate manual/hands-free uploader.
- Types API interview: `captureId`, status/provenance nếu response cần hiển thị.
- Browser tests cho Chrome/Edge/Safari target và permission/error/unmount flow.

## 14. Dependencies To Add

### Maven

| Dependency | Version đề xuất | Mục đích | Ghi chú |
|---|---:|---|---|
| `com.microsoft.onnxruntime:onnxruntime` | `1.23.1` | CPU inference cho `silero_vad.onnx` | Pin theo Java example Silero v6.2.1; xác nhận Linux x64. |

Không đề xuất thêm JavaCV, TarsosDSP hoặc audio pure-Java stack trong phương án chọn.

### Runtime/OS

| Dependency | Version | Mục đích |
|---|---|---|
| FFmpeg executable | Pin theo Ubuntu/package policy sau khi kiểm tra VPS | Decode WebM/Opus và resample/downmix sang PCM s16le 16 kHz mono |
| Silero model artifact | v6.2.1 `silero_vad.onnx`, SHA-256 đã nêu | VAD inference |

FFmpeg là dependency triển khai bắt buộc của decoder dù không nằm trong Maven. Cần đưa vào provisioning/runbook/health check để deploy có tính tái lập.

## 15. Risks

| Risk | Mức | Mitigation |
|---|---:|---|
| Browser tự chọn codec, extension `.webm` không phản ánh thực tế | Cao | Explicit MIME negotiation, gửi MIME thật, server probe/decode. |
| FFmpeg không có trên VPS hoặc systemd user không execute được | Cao | Provision, absolute path, startup health, deployment smoke test. |
| Chèn VAD vào transaction hiện tại làm giữ DB connection lâu | Cao | Hai transaction ngắn, external processing ở ngoài. |
| Shared mutable Silero state làm nhiễm audio giữa users | Rất cao | Shared session nhưng per-request state/context; concurrency tests. |
| Hands-free thu cả TTS/confirmation | Rất cao | Pause trước system speech; resume sau system speech end. |
| Upload trùng hoặc transcript/audio mismatch | Rất cao | `captureId`, explicit `questionId`, atomic multipart, idempotency constraint/state machine. |
| FFmpeg process DoS/hang/output bomb | Cao | Timeout, output/duration cap, bounded concurrency, kill/cleanup. |
| Native memory leak từ tensor/result/session | Cao | try-with-resources, bean lifecycle, native-memory monitoring. |
| Threshold không phù hợp tiếng Việt/noisy mic | Trung bình | Benchmark dataset đại diện; externalized config; shadow rollout. |
| Bỏ câu trả lời rất ngắn do min speech duration | Trung bình | Tune bằng short-answer cases; không chốt mặc định upstream mù quáng. |
| VAD được diễn giải sai thành confidence/personality | Cao | Contract/schema/UI naming rõ; cấm dùng raw probability trong scoring. |
| ORT Maven artifact không hỗ trợ architecture VPS | Cao nếu ARM | Kiểm tra `uname -m` trước implementation; chọn build phù hợp hoặc dừng. |
| Audio retention/privacy không rõ | Cao | Consent, TTL, restricted temp storage, no content logs. |
| JavaCV/pure Java làm tăng packaging/codec complexity nếu đổi hướng | Trung bình | Giữ FFmpeg process làm decoder chuẩn, chỉ đổi sau prototype có số liệu. |

## 16. Recommended Implementation Plan

Chưa thực hiện trong audit này. Thứ tự đề xuất:

1. **Khóa contract và baseline**
   - Xác nhận browser support matrix, VPS architecture/systemd user và FFmpeg availability.
   - Ghi sample runtime `recorder.mimeType` trên browser mục tiêu.
   - Chốt schema VAD chỉ speech/silence và cam kết không ảnh hưởng `overallScore`.

2. **Prototype decoder tách biệt**
   - Provision một version FFmpeg trong dev/CI/VPS staging.
   - Test WebM/Opus, Ogg/Opus, WAV/PCM, invalid/truncated files.
   - Verify output mono s16le 16 kHz, duration và resource limits.

3. **Prototype Silero runtime**
   - Vendor v6.2.1 model + license/checksum.
   - Thêm ORT 1.23.1 CPU; kiểm tra input/output names/shapes từ loaded session lúc startup.
   - Golden tests cho PCM conversion, 512-sample framing, zero padding, state/context reset.

4. **Implement segmentation/config**
   - Port hysteresis algorithm có source attribution/test vectors.
   - Externalize threshold/min speech/min silence/pad.
   - Tạo benchmark tiếng Việt gồm silence, background noise, câu ngắn, câu dài, microphone/browser khác nhau; sau đó mới chốt config.

5. **Refactor orchestration/transaction**
   - Claim trong transaction ngắn.
   - Spool upload một lần; Gladia và VAD chạy ngoài transaction.
   - Persist kết quả có capture/version check trong transaction ngắn.
   - VAD failure không rollback Gladia transcript.

6. **Thêm correlation/idempotency**
   - Endpoint explicit `questionId`.
   - `captureId` UUID và unique constraint.
   - Atomic transcript + audio upload hoặc two-phase finalize bắt buộc reference capture.
   - Tests duplicate retry, stale question, concurrent confirmation.

7. **Tích hợp hands-free sau khi lifecycle được test**
   - Start capture chỉ sau `QUESTION_ENDED`.
   - Web Speech và MediaRecorder chạy song song khi candidate answer.
   - Pause trước confirmation TTS; resume sau negative flow; stop/freeze trước positive upload.
   - Cleanup mic trên stop/unmount/error/question change.

8. **Shadow rollout và observability**
   - `vad.enabled`/`vad.required=false`, bounded concurrency.
   - Theo dõi decode success, model load, inference latency, queue depth, native memory và disagreement QA.
   - Không hiển thị/score VAD cho đến khi benchmark được review.

9. **Production readiness gate**
   - Dependency/license/security scan.
   - Load/concurrency test với audio 180 giây.
   - Chaos tests: FFmpeg missing/hang, corrupt model, ORT exception, disk full/temp cleanup.
   - Privacy/retention review và deployment rollback plan.

## Quyết định cuối audit

**Go có điều kiện** cho hướng `FFmpeg process -> mono PCM s16le 16 kHz -> Silero VAD v6.2.1 ONNX Runtime CPU`, với các điều kiện chặn implementation production:

1. FFmpeg phải được provision/health-check trong Ubuntu deployment.
2. Transaction phải được tách khỏi network/decode/inference.
3. Silero state/context phải per answer, không dùng chung.
4. Hands-free phải tuân thủ lifecycle tại mục 10 và không thu giọng hệ thống.
5. Upload phải correlation/idempotent bằng explicit `questionId + captureId`.
6. VAD chỉ tạo speech/silence metrics, không thay `overallScore` và không suy luận emotion/confidence/personality.

