# Hands-Free Transcript Status Constraint Fix Design

Ngày: 2026-08-11  
Phạm vi: lỗi PostgreSQL `23514` khi hands-free capture bắt đầu xử lý.

## Vấn đề

Constraint hiện tại của `interview_answers.transcript_status` chỉ cho phép:

```text
pending, processing, completed, failed
```

Phase 2 đang ghi các giá trị chi tiết mới trực tiếp vào cùng cột:

```text
processing_standardization, standardized, fallback_browser
```

Vì vậy transaction claim thất bại trước khi audio được gửi sang Gladia/VAD.

## Phương án đã chọn

Giữ nguyên semantics và constraint hiện tại của `InterviewAnswer`:

- lúc claim capture: `transcript_status = processing`;
- khi capture tạo được một final transcript hợp lệ, dù từ Gladia hay browser fallback: `transcript_status = completed`;
- khi không có transcript hợp lệ: `transcript_status = failed`.

Các trạng thái chi tiết vẫn được giữ tại:

- API response `transcriptStatus`: `standardized` hoặc `fallback_browser`;
- `interview_answer_captures.transcript_status`;
- `dataQuality`, `originalSpeechTranscript` và `speechAnalysisJson`.

Do đó frontend vẫn biết transcript có được Gladia chuẩn hóa hay đang dùng fallback, trong khi aggregate `InterviewAnswer` tiếp tục tương thích với schema cũ.

## Thay đổi code

Production code chỉ sửa tại:

- `backend/src/main/java/com/sjp/recruitment/service/HandsFreeAnswerCaptureService.java`

Không thêm migration V39 và không nới DB constraint.

Tests sẽ xác minh:

- claim dùng status `processing` hợp lệ;
- Gladia success trả response `standardized` nhưng entity lưu `completed`;
- Gladia failure trả response `fallback_browser` nhưng entity vẫn lưu `completed` nếu browser transcript hợp lệ;
- `overallScore` và confirmed transcript flow không đổi.

## Validation

Sau implementation sẽ chạy:

1. targeted hands-free backend tests;
2. toàn bộ backend tests;
3. backend package build;
4. UTF-8/Vietnamese encoding check;
5. `git diff --check`.

Tiêu chí hoàn thành: không còn ghi status ngoài constraint vào `interview_answers`, response provenance vẫn giữ chi tiết và không bắt đầu fluency scoring.
