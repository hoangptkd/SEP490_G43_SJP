# Hands-Free Multipart Content-Type Fix Design

Ngày: 2026-08-11  
Phạm vi: lỗi HTTP 415 khi hands-free upload audio để Gladia chuẩn hóa transcript.

## Vấn đề

`frontend/src/services/api.ts` cấu hình Axios instance với header mặc định:

```text
Content-Type: application/json
```

`finalizeHandsFreeCapture` truyền `FormData` nhưng chỉ thêm `Idempotency-Key`, vì vậy request thực tế vẫn mang `application/json`. Backend endpoint `answer-capture` chỉ consume `multipart/form-data` nên Spring từ chối request trước khi controller được gọi:

```text
HttpMediaTypeNotSupportedException: Content-Type 'application/json' is not supported
```

Frontend sau đó đi đúng nhánh degrade hiện có và hiển thị `Không thể chuẩn hóa, sử dụng bản ghi nhận realtime`. Vì request chưa vào backend nên Gladia và VAD hoàn toàn chưa chạy.

## Phương án đã chọn

Sửa cục bộ `finalizeHandsFreeCapture` để gửi đồng thời:

```text
Content-Type: multipart/form-data
Idempotency-Key: {captureId}
```

Đây là phương án ít rủi ro nhất vì:

- nhất quán với `uploadAudio` và các upload service hiện có;
- không thay đổi interceptor Axios toàn cục;
- không thay backend contract;
- không ảnh hưởng các JSON API khác.

Không chọn backend nhận `application/json`, vì audio binary và repeated multipart parts không thuộc JSON contract của endpoint.

## Thay đổi code

Chỉ sửa production code tại:

- `frontend/src/services/aiInterviewService.ts`

Thêm regression test frontend cho `finalizeHandsFreeCapture` để xác minh:

- body là `FormData`;
- request có `Content-Type: multipart/form-data`;
- `Idempotency-Key` vẫn bằng `captureId`;
- các repeated fields `audioSegments`, `segmentSequences`, `durationSeconds` vẫn được giữ.

Không sửa state machine, backend endpoint, Gladia client, VAD, persistence hoặc scoring.

## Error Handling

Failure/degradation hiện có được giữ nguyên. Sau fix:

- multipart hợp lệ đi vào backend để Gladia xử lý;
- lỗi provider/network thật vẫn fallback browser transcript;
- HTTP 415 do JSON Content-Type không còn xảy ra cho hands-free capture.

## Validation

Sau implementation sẽ chạy:

1. frontend service regression test;
2. toàn bộ frontend tests;
3. frontend production build;
4. UTF-8/Vietnamese encoding check cho các file đã sửa.

Tiêu chí hoàn thành: request hands-free mang multipart Content-Type, test/build pass và không thay đổi `overallScore` hay thêm fluency scoring.
