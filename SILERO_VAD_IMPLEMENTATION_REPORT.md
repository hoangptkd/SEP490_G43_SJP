# Silero VAD Phase 1 Implementation Report

Ngày hoàn thành: 2026-08-11  
Phạm vi: Audio Decoder + Silero VAD Engine độc lập cho hands-free Virtual Interview.  
Không thuộc phạm vi: frontend hands-free, `useVoiceConversation`, `InterviewSession`, `InterviewAnswer`, Gladia, LLM, fluency score và `overallScore`.

## 1. Files Added

### Production code

- `backend/src/main/java/com/sjp/recruitment/config/VadProperties.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/AudioCaptureSegment.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/AudioDecoder.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/AudioDecoderHealth.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/AudioDecodingException.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/DecodedPcmAudio.java`
- `backend/src/main/java/com/sjp/recruitment/service/audio/FfmpegAudioDecoder.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/SileroInferenceState.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/SileroModelContract.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/SileroModelHealth.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/SileroModelRuntime.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/SileroVadAnalyzer.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadAnalysisException.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadAnalysisResult.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadHealthIndicator.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadInferenceOutput.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadModel.java`
- `backend/src/main/java/com/sjp/recruitment/service/vad/VadSegmenter.java`

### Model resources

- `backend/src/main/resources/models/silero-vad/v6.2.1/silero_vad.onnx`
- `backend/src/main/resources/models/silero-vad/v6.2.1/METADATA.md`
- `backend/src/main/resources/models/silero-vad/v6.2.1/LICENSE`

### Tests

- `backend/src/test/java/com/sjp/recruitment/service/audio/FfmpegAudioDecoderTest.java`
- `backend/src/test/java/com/sjp/recruitment/service/vad/SileroVadAnalyzerTest.java`
- `backend/src/test/java/com/sjp/recruitment/service/vad/SileroModelRuntimeLinuxTest.java`
- `backend/src/test/java/com/sjp/recruitment/service/vad/SileroModelRuntimeOptionalFailureTest.java`
- `backend/src/test/java/com/sjp/recruitment/service/vad/SileroVadPerformanceLinuxTest.java`

### Existing files modified

- `backend/pom.xml`: ONNX Runtime CPU dependency.
- `backend/src/main/resources/application.yml`: independent `app.vad` configuration.

Không sửa frontend hoặc Virtual Interview business flow.

## 2. Dependencies Added

Maven:

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.23.1</version>
</dependency>
```

Runtime dependency ngoài Maven:

- FFmpeg executable, absolute path qua `VAD_FFMPEG_PATH`.
- Test Linux dùng FFmpeg `8.0.1-3ubuntu2` tại `/usr/bin/ffmpeg`.

Backend JAR hiện tại build thành công, kích thước khoảng `173.69 MiB`. ONNX Runtime CPU artifact chứa native runtime nên làm deployment artifact lớn hơn; đây là điểm cần theo dõi khi đóng gói production.

## 3. FFmpeg Integration

`FfmpegAudioDecoder` dùng `ProcessBuilder` trực tiếp, không dùng shell. Command có fixed argument structure:

```text
<absolute-ffmpeg-path>
-nostdin -hide_banner -loglevel error -y
-i <random-temporary-input>
-map 0:a:0 -vn -ac 1 -ar 16000 -f s16le
<random-temporary-output>
```

Guardrails đã implement:

- FFmpeg path phải absolute, là regular file và executable.
- Startup chạy `<ffmpeg> -version` với timeout.
- `vad.required=false` cho phép health degraded thay vì làm app fail.
- Không truyền original filename vào filesystem path.
- Mỗi segment có random temporary directory riêng.
- Không qua shell và không nội suy user input vào command options.
- Decode timeout bằng polling; process được `destroy` rồi `destroyForcibly` nếu cần.
- `stderr` được drain trên bounded executor; chỉ giữ tối đa 64 KiB để tránh pipe blocking/unbounded memory.
- Theo dõi output file trong khi process chạy; kill nếu vượt output cap.
- Kiểm tra exit code, output tồn tại, byte length dương/chẵn, size và duration.
- Process, stream, executor và temp files được cleanup trong `finally`/`@PreDestroy`.
- `VadHealthIndicator` trả health detail cho decoder và model.

Không lưu raw audio vào database/object storage. Input container và PCM chỉ tồn tại dưới dạng temporary request-processing files rồi bị xóa; kết quả chỉ chứa segments/metrics.

## 4. Model Contract

Model vendored:

- Silero VAD `v6.2.1`.
- File `silero_vad.onnx`, opset 16.
- Size `2,327,524` bytes.
- SHA-256 `1A153A22F4509E292A94E67D6F9B85E8DEB25B4988682B7E174C65279D8788E3`.
- MIT license và metadata được đặt cạnh model.
- Runtime không download model từ network.

Startup thực hiện:

1. Đọc resource và verify SHA-256.
2. Tạo shared `OrtEnvironment` và `OrtSession` CPU với inter-op/intra-op thread bằng 1.
3. Inspect exact input names: `input`, `state`, `sr`.
4. Inspect exact output names: `output`, `stateN`.
5. Verify tensor type: `input/state/output/stateN` là FLOAT, `sr` là INT64.
6. Verify rank/fixed dimensions mà metadata khai báo.
7. Chạy dry inference tại startup với input `[1,576]`, state `[2,1,128]`, sample rate `[1]`.
8. Verify output runtime thực tế là `[1,1]` và state mới là `[2,1,128]`.

Điểm phát hiện qua test thật: metadata của `stateN` là dynamic `[-1,-1,-1]`, không phải metadata `[2,-1,128]`. Validator không giả định sai dimension này; thay vào đó nó bắt buộc startup dry-run xác nhận shape thực `[2,1,128]`. Nếu name/type/rank/dry-run contract sai, health là `model_contract_mismatch`, session bị đóng và không inference.

## 5. Audio Normalization

Mỗi capture segment được FFmpeg normalize độc lập thành:

- mono;
- 16,000 Hz;
- signed PCM 16-bit little-endian (`s16le`);
- raw PCM không có container header.

PCM bytes được chuyển thành Java `short[]` theo little-endian. Khi inference:

```text
floatSample = signedShort / 32768.0f
```

Không peak-normalize theo answer. Frame cuối thiếu 512 real samples được zero-pad chỉ cho tensor; `audioDurationSeconds` và segment end dùng sample count thật, không tính padding.

## 6. Multi-Segment Handling

Input abstraction:

```text
AudioCaptureSegment
  sequence
  bytes hoặc path (đúng một nguồn)
  mimeType
```

Decoder:

1. Reject null/empty list.
2. Sort theo `sequence`.
3. Yêu cầu sequence unique, không âm và contiguous.
4. Validate mỗi segment có đúng một source, không rỗng, MIME được hỗ trợ và không vượt input cap.
5. Decode từng container riêng thành PCM 16 kHz mono.
6. Validate output của từng segment.
7. Concatenate `short[]` PCM theo sequence.
8. Enforce tổng output size và tổng duration của answer.

Raw WebM/Ogg/container bytes không được concatenate. Integration test truyền hai WebM/Opus tone với input list đảo thứ tự và xác nhận PCM output được ghép đúng sequence/frequency.

## 7. ONNX Resource Lifecycle

Shared toàn app:

- một process-wide `OrtEnvironment` từ `OrtEnvironment.getEnvironment()`;
- một `OrtSession` đã validate, thread-safe cho concurrent `run`.

Per answer:

- `SileroInferenceState` mới;
- recurrent state 256 float values;
- context 64 samples;
- sample cursor;
- `VadSegmenter` và toàn bộ triggered/candidate-end state.

Mọi answer bắt đầu recurrent state/context bằng zero. Không có mutable state per-answer trong Spring singleton.

Native resource cleanup:

- `OnnxTensor` input/state/sample-rate và `OrtSession.Result` dùng try-with-resources ở mỗi frame.
- Candidate session bị đóng nếu startup contract validation fail.
- Shared session đóng ở `@PreDestroy`.
- Process-wide environment không bị component đóng sớm vì có thể được ONNX consumer khác dùng chung.
- Analyzer có fair `Semaphore` với `maxConcurrentAnalyses` và acquire timeout.

Sau benchmark đầu, các allocation `frame/context/state/modelInput` theo từng frame đã được thay bằng buffer per-answer tái sử dụng. State vẫn không share giữa answers.

## 8. Segmentation Algorithm

Config độc lập dưới `app.vad.segmentation`:

- `speechThreshold`;
- `negativeThreshold`;
- `minSpeechDurationMs`;
- `minSilenceDurationMs`;
- `speechPadMs`.

Giá trị trong config là baseline externalized, không phải threshold production đã calibrate.

Algorithm:

- 512 real audio samples/frame tại 16 kHz, tương đương 32 ms.
- Nối 64 context samples với 512 frame samples thành model input `[1,576]`.
- Mở speech khi probability `>= speechThreshold`.
- Bắt đầu candidate end khi probability `< negativeThreshold`.
- Chỉ đóng speech khi candidate silence đạt `minSilenceDurationMs`.
- Bỏ raw speech segment ngắn hơn `minSpeechDurationMs`.
- Pad hai đầu theo `speechPadMs`, clamp vào audio duration và merge nếu padding làm overlap.
- Final partial frame được infer bằng zero padding nhưng timestamps clamp theo real sample count.

`minSilenceDurationMs` chỉ là VAD segmentation hysteresis. Nó không phải `conversationSilenceTimeout` của UI và không phải business-level `fluencyPauseThreshold`. Phase 1 không tạo hai threshold business/UI đó. Speech segments giữ đủ timestamp để Phase sau áp `fluencyPauseThreshold` riêng.

Không persist speech probability trace.

## 9. Metrics Produced

`VadAnalysisResult` trả:

- `speechSegments[{startMs,endMs}]`;
- `audioDurationSeconds`;
- `speakingDurationSeconds`;
- `speechOnsetSeconds` hoặc `null`;
- `speechEndSeconds` hoặc `null`;
- `internalPauseCount`;
- `longestInternalPauseSeconds`;
- `totalInternalPauseDurationSeconds`;
- `pauseDurationRatio`;
- decode/inference duration cho observability;
- status/error code/model version.

Internal pause là gap dương giữa hai speech segments cuối cùng. Leading silence và trailing silence không được đưa vào pause count hoặc total internal pause.

```text
pauseDurationRatio = totalInternalPauseDuration
                   / (speakingDuration + totalInternalPauseDuration)
```

Nếu denominator bằng 0, ratio bằng 0.

Không tạo confidence, nervousness, emotion, personality, fluency score hoặc bất kỳ thay đổi nào với `overallScore`.

## 10. Error Handling

Controlled decoder codes gồm:

- empty input/segment;
- invalid sequence/source;
- unsupported MIME;
- input/output quá lớn;
- output quá dài;
- decoder unavailable;
- timeout/decode failure;
- invalid PCM.

Controlled analyzer statuses gồm:

- `COMPLETED`;
- `DISABLED`;
- `DECODER_UNAVAILABLE`;
- `MODEL_UNAVAILABLE`;
- `INVALID_AUDIO`;
- `DECODE_FAILED`;
- `INFERENCE_FAILED`;
- `BUSY`.

Khi `vad.required=false`, lỗi FFmpeg/model/inference/concurrency trả status degraded, không trả partial speech metrics và không làm Spring context fail. Đây là contract để Phase sau có thể tiếp tục transcript-only. Khi `vad.required=true`, startup hoặc analysis failure của dependency bắt buộc sẽ fail rõ ràng.

Health check không inference nếu checksum/model contract sai. Không expose raw stderr đầy đủ; decoder chỉ giữ một message giới hạn và không dùng original filename/path từ user.

## 11. Test Results

Không test nào gọi external API/network provider.

### Final backend suite trên Windows host

```text
mvn -q test
Tests: 60
Failures: 0
Errors: 0
Skipped: 5
Exit code: 0
```

Các skip là test cần Linux/native FFmpeg trên host Windows không có binary và benchmark opt-in. Spring context vẫn start thành công với `vad.required=false`, xác nhận graceful degradation.

### Linux x86_64/WSL với FFmpeg thật và ONNX native thật

- Full backend suite: exit code 0.
- FFmpeg integration: 5/5 pass, 0 skip.
- Silero model load/contract/dry inference: pass, 0 skip.
- Analyzer deterministic/state isolation tests: pass.
- Targeted Phase 1 + performance run: 14 tests, 0 failure/error/skip.
- Các failure-mode tests bổ sung sau cùng (missing model, inference failure, concurrency busy) pass trên host.

Coverage theo fixture yêu cầu:

1. Silence-only: pass.
2. Continuous speech: pass.
3. Speech-silence-speech: pass.
4. Multiple capture segments, decode riêng và ghép PCM theo sequence: pass với FFmpeg thật.
5. Invalid segment: pass với controlled code.
6. Empty input: pass.
7. Truncated WebM: pass với controlled failure.
8. Unsupported MIME/container: pass.
9. Final partial 512 frame: pass, duration không tính zero padding.
10. Concurrent analyses: pass; one-frame answers đều nhận zero initial state.
11. Repeated analysis: pass; output metrics deterministic.

### Backend build

```text
mvn -q -DskipTests package
Exit code: 0
Artifact: backend/target/recruitment-portal-0.0.1-SNAPSHOT.jar
```

## 12. Performance Result

Môi trường benchmark:

- WSL2 Ubuntu 26.04 x86_64;
- OpenJDK 17.0.19;
- FFmpeg 8.0.1;
- ONNX Runtime CPU 1.23.1;
- synthetic WebM/Opus 48 kHz mono tone, decode về PCM 16 kHz mono;
- VAD concurrency = 1.

Kết quả cuối sau giảm per-frame allocations:

| Audio | Input WebM bytes | Decode | ONNX inference | Approx heap delta |
|---:|---:|---:|---:|---:|
| 30 giây | 266,058 | 185 ms | 217 ms | 11,534,336 bytes |
| 180 giây | 1,611,004 | 492 ms | 734 ms | 59,768,832 bytes |

Benchmark đầu của 180 giây quan sát khoảng 97.5 MB heap delta. Reuse frame/context/model-input buffers đã giảm còn khoảng 59.8 MB và inference giảm từ 775 ms xuống 734 ms trong lần đo cuối.

`approxHeapDelta` là chênh lệch used heap trước/sau, không phải peak RSS/native memory chính xác và chịu ảnh hưởng GC. ONNX tensor/native allocations vẫn cần quan sát bằng production metrics/JFR. Không có bottleneck latency rõ ràng ở giới hạn 180 giây trên môi trường test.

## 13. Known Limitations

- Chưa tích hợp hands-free frontend, capture lifecycle, API upload hoặc Interview business flow theo đúng Phase 1.
- Threshold hiện là baseline config, chưa calibrate bằng audio interview tiếng Việt/browser/microphone thực tế.
- Semantic speech tests dùng deterministic fake model probabilities để kiểm tra segmentation chính xác; performance fixture là synthetic tone, không đại diện chất lượng nhận diện speech thực tế.
- Host Windows hiện không load được native DLL của ONNX Runtime 1.23.1 trong môi trường local, nhưng `vad.required=false` degrade đúng và Linux x86_64 native test pass. Production Ubuntu phải kiểm tra architecture/runtime package trong deployment smoke test.
- FFmpeg chưa được provision bởi repo deployment; production phải cài/pin FFmpeg và đặt `VAD_FFMPEG_PATH` absolute.
- Temporary file deletion là best-effort sau khi process terminate; production nên giám sát temp directory và có startup janitor cho crash leftovers nếu policy yêu cầu chặt hơn.
- `internalPauseCount` hiện đếm mọi internal gap còn lại sau VAD segmentation/padding. Phase sau phải áp business `fluencyPauseThreshold` riêng nếu cần đếm long pauses.
- ONNX Runtime làm JAR lớn; có thể đánh giá platform-specific packaging sau khi deployment pipeline ổn định.
- Approx memory không bao gồm peak native memory chính xác; cần JFR/native-memory tracking dưới concurrent production-like load.

## 14. Next Hands-Free Integration Step

Phase tiếp theo, chưa thực hiện ở đây:

1. Thêm hands-free capture state machine trong `useVoiceConversation` nhưng tiếp tục giữ Web Speech API cho realtime transcript.
2. Start `MediaRecorder` chỉ sau khi question audio/TTS kết thúc.
3. Stop từng capture segment trước khi hệ thống đọc confirmation; không giả định pause/resume ổn định.
4. Nếu candidate tiếp tục, tạo capture segment tiếp theo cùng answer/capture ID với sequence tăng dần.
5. Upload ordered `List<AudioCaptureSegment>` cùng explicit `questionId`, `captureId`, transcript và idempotency key.
6. Backend gọi Phase 1 engine để decode từng WebM/Opus segment riêng, nối PCM rồi phân tích.
7. Nếu VAD degraded và `required=false`, vẫn hoàn tất transcript-only flow.
8. Persist chỉ speech segments/derived metrics/status/model version khi schema được duyệt; không persist probability trace hoặc raw audio.
9. Giữ `conversationSilenceTimeout`, VAD `minSilenceDuration` và future `fluencyPauseThreshold` là ba khái niệm/config độc lập.
10. Không dùng VAD cho emotion/confidence/personality và không thay `overallScore`.

