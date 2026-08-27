# Thiết kế khắc phục Candidate Audit ngoài AI

Ngày: 2026-08-10

Trạng thái: Đã thống nhất thiết kế, chờ duyệt đặc tả trước khi triển khai

Phạm vi: Các mục CIB ngoài AI trong `docs/audit/CANDIDATE_IMPROVEMENT_BACKLOG.md`

## 1. Mục tiêu

Khép lại các vấn đề Candidate Audit không thuộc AI mà không làm thay đổi các luồng AI Interview, AI Question Generation hoặc AI Evaluation. Kết quả phải bảo đảm:

- các luồng xác thực, hồ sơ, CV và ứng tuyển đúng hợp đồng và an toàn hơn;
- danh sách dữ liệu lớn có phân trang, lọc và xử lý request cạnh tranh;
- Candidate có đủ trang và thao tác còn thiếu như company detail, job alerts và quản lý saved jobs;
- form, dialog, thông báo lỗi và điều hướng đáp ứng accessibility cơ bản;
- frontend build và lint thành công, backend/frontend test thành công;
- văn bản tiếng Việt giữ nguyên UTF-8;
- trạng thái từng CIB được cập nhật bằng bằng chứng kiểm thử.

## 2. Ngoài phạm vi

Không thay đổi:

- prompt, rubric, scoring, transcript, audio, TTS/STT hoặc lifecycle của AI Interview;
- rate limiter, benchmark hay consent riêng của AI;
- luồng Employer/Admin không liên quan trực tiếp đến việc sửa build hoặc hợp đồng dùng chung;
- lịch sử Git bằng thao tác rewrite. File CV đang được track sẽ được bỏ khỏi index hiện tại; xóa khỏi lịch sử và xoay vòng credential thật là bước vận hành riêng vì có tính phá hủy/ngoài repository.

## 3. Nguyên tắc triển khai

1. Triển khai theo bốn đợt, mỗi đợt độc lập và có kiểm thử.
2. Ưu tiên tương thích ngược. Endpoint danh sách mới trả `PageResponse<T>`; frontend Candidate được chuyển đồng bộ. Endpoint công khai đang có chỉ thay đổi khi toàn bộ consumer trong repository đã được cập nhật.
3. Không thêm hạ tầng bắt buộc mới. Rate limiting dùng abstraction có implementation trong tiến trình, cấu hình được và có thể thay bằng Redis sau này.
4. Snapshot dữ liệu đã nộp là nguồn hiển thị chính; dữ liệu live chỉ là fallback cho record cũ.
5. UI mới được tách theo route/domain, không tiếp tục tăng kích thước `App.tsx`.
6. Không nuốt lỗi thành dữ liệu rỗng. Mỗi request có trạng thái loading, empty, error và retry rõ ràng.

## 4. Đợt 1 — Bảo mật, tính đúng dữ liệu và baseline build

### 4.1 JWT và phiên đăng nhập

- `resetPassword` phải tăng `tokenVersion` trong cùng transaction với đổi mật khẩu và đánh dấu reset token đã dùng.
- Thêm test chứng minh JWT phát hành trước logout, đổi mật khẩu, reset mật khẩu hoặc deactivate đều không còn hợp lệ.
- Login, register, forgot-password và reset-password đi qua `AuthRateLimiter`.
- Khóa rate limit được tạo từ loại hành động, định danh chuẩn hóa và địa chỉ client đáng tin cậy. Không ghi password/token vào log.
- Trả `429 Too Many Requests` với mã lỗi ổn định và thời gian thử lại; cấu hình giới hạn nằm trong `application.yml`/environment.

### 4.2 Secret và file riêng tư

- Thay credential có hình dạng credential thật trong `.env.example` bằng placeholder.
- Production không được khởi động với JWT secret mặc định; local/test vẫn có cấu hình riêng rõ ràng.
- Cấu hình payment bật nhưng thiếu credential phải fail fast bằng validation cấu hình.
- Bỏ các PDF trong `backend/uploads` khỏi Git index, giữ rule ignore và không đọc/nội suy nội dung PDF.
- Upload CV tiếp tục kiểm tra dung lượng, MIME và magic bytes. File được lưu bằng khóa nội bộ; API không trả URL Cloudinary/public dài hạn mà trả endpoint download có xác thực ownership hoặc quyền Employer trên application tương ứng.
- `StorageService` phải hỗ trợ stream hoặc URL ký ngắn hạn cho private object. Local storage nằm ngoài static resource path. Chính sách malware ở giai đoạn này là PDF-only, kiểm tra signature/size và ghi nhận điểm tích hợp scanner; audit ghi rõ residual risk do chưa có dịch vụ quét bên ngoài.

### 4.3 Snapshot CV bất biến

- Map các cột snapshot CV hiện có vào `Application`.
- Khi submit, lưu snapshot gồm tên CV, loại CV, URL file hoặc dữ liệu builder cần để render, cùng version/id tham chiếu.
- Application detail luôn ưu tiên snapshot; chỉ fallback sang CV hiện tại cho application cũ chưa có snapshot.
- Việc sửa hoặc xóa CV sau khi ứng tuyển không thay đổi nội dung mà Candidate/Employer nhìn thấy trên hồ sơ đã nộp.
- Thêm test cho upload CV, builder CV, CV không thuộc user, chọn cả hai loại và không chọn loại nào.

### 4.4 Hợp đồng validation và lỗi build hiện tại

- Backend đặt constraint cho cover letter, preferred location và job report; frontend dùng cùng giới hạn và hiển thị lỗi theo field.
- Sửa toàn bộ TypeScript build error và ESLint error/warning hiện hữu, gồm type của dashboard/settings/applications và dependency của hooks.
- Không dùng `any` hoặc bỏ qua TypeScript để làm build xanh.

### 4.5 Offer state machine

- Thay boolean query parameter bằng command DTO chứa hành động enum rõ ràng.
- Backend kiểm tra ownership, trạng thái offer hiện tại, thời hạn và transition hợp lệ trước khi mutate.
- Thêm optimistic version hoặc conditional update để hai request đồng thời chỉ có một request thành công; request còn lại trả `409 Conflict`.
- Frontend khóa action trong khi gửi, cập nhật lại application sau thành công hoặc conflict và hiển thị trạng thái mới nhất.

## 5. Đợt 2 — API danh sách, tìm kiếm và tính ổn định của request

### 5.1 Chuẩn phân trang

Tạo response dùng chung:

```text
PageResponse<T>
├── items
├── page
├── size
├── totalItems
├── totalPages
├── first
└── last
```

Áp dụng cho CV, CV versions, saved jobs, notifications và applications của Candidate. Backend giới hạn `size`, chuẩn hóa `page >= 0` và trả metadata. Frontend giữ page trong URL khi phù hợp, hiển thị số trang và tự điều chỉnh khi page vượt giới hạn.

### 5.2 Job search/filter/sort

- Đồng bộ parser URL với backend cho category và các filter được UI công bố.
- Bổ sung job type và work mode nếu chúng đã có trong model dữ liệu.
- Validate `minSalary <= maxSalary`; trả lỗi có thể hiển thị thay vì âm thầm bỏ qua.
- Đổi nhãn “Phù hợp nhất” nếu backend chưa có relevance thực; mặc định dùng “Mới nhất”.
- Pagination có số trang, trang hiện tại và nút trước/sau.
- Khi có nhiều trang, pagination có nút đầu/cuối và bộ chọn page size từ tập giá trị giới hạn đã định nghĩa.
- Mỗi lần đổi filter tạo request mới có `AbortController` hoặc request id; response cũ không được ghi đè response mới.

### 5.3 Hiệu năng truy vấn

- Thay hai truy vấn `exists` trên từng job bằng truy vấn batch theo danh sách job id cho `saved` và `applied`.
- Thêm migration PostgreSQL cho index phục vụ các điều kiện filter/sort thực tế. Với tìm kiếm chứa chuỗi, chuẩn hóa `pg_trgm`/GIN trong migration triển khai hoặc ghi rõ bước cấp quyền extension nếu database user không có quyền.
- Lưu bằng chứng `EXPLAIN (ANALYZE, BUFFERS)` trên bộ dữ liệu kiểm thử đủ lớn; query plan không được quay lại hai truy vấn `exists` cho từng row.

### 5.4 Điều hướng giữ ngữ cảnh

- Link từ job list/saved jobs truyền `from` gồm path, query và vị trí scroll.
- Back từ job detail/application detail trở về nguồn hợp lệ; nếu không có state thì dùng route mặc định.
- Khôi phục scroll sau khi danh sách và dữ liệu cần thiết đã render.

## 6. Đợt 3 — Hoàn thiện tính năng Candidate

### 6.1 Profile readiness và dữ liệu có cấu trúc

- Thay `List<Object>` bằng DTO có validation cho education, work experience, project và certification.
- Bổ sung các trường đã được audit yêu cầu: headline, years/level of experience, LinkedIn và portfolio khi schema hỗ trợ; nếu chưa có thì thêm migration.
- Skills được nhập bằng chip/tag, trim và deduplicate không phân biệt hoa thường.
- Profile readiness trả danh sách mục thiếu có mã ổn định; UI hiển thị checklist và link/focus tới khu vực cần hoàn thiện.

### 6.2 Xác thực tài khoản và khôi phục lỗi

- Thêm resend verification có cooldown/rate limit và response không làm lộ email tồn tại.
- Trang verify/reset phân biệt expired, invalid, already-used và network error; cung cấp hành động gửi lại/quay về đăng nhập.
- Password mới có checklist yêu cầu và lỗi liên kết trực tiếp với field.

### 6.3 Saved jobs

- Saved jobs có hành động bỏ lưu trực tiếp, xem/apply, trạng thái đã ứng tuyển và trạng thái job hết hạn/đóng.
- Sau khi bỏ lưu, cập nhật lạc quan có rollback khi API thất bại.
- Empty/error/loading state riêng, không tái sử dụng card theo cách che mất các hành động cần thiết.

### 6.4 Public company detail

- Thêm public endpoint đọc company đã được phép hiển thị, không lộ dữ liệu quản trị hoặc billing.
- Thêm route `/companies/:id` với thông tin công ty và danh sách job đang mở có phân trang.
- Job detail/card liên kết tới company detail khi có company hợp lệ.

### 6.5 Job alerts/preferences

- Thêm migration và model cho alert của Candidate: keyword, location, category, job type, work mode, salary range, frequency và trạng thái bật/tắt.
- CRUD endpoint chỉ thao tác alert thuộc Candidate hiện tại.
- UI cho phép tạo từ bộ lọc tìm kiếm hiện tại, sửa, bật/tắt và xóa có xác nhận.
- Scheduler định kỳ đối chiếu job mới với alert đang bật và tạo notification trong ứng dụng, có khóa chống tạo trùng theo cặp alert/job.
- `frequency` điều khiển cửa sổ chạy và thời điểm lần cuối; phạm vi không gửi email. UI nói rõ notification xuất hiện trong ứng dụng.

## 7. Đợt 4 — UX, accessibility, responsive và cấu trúc frontend

### 7.1 Form và phản hồi lỗi

- Tạo helper/hook chuẩn hóa API error thành lỗi field và lỗi form.
- Field lỗi có `aria-invalid`, `aria-describedby`; vùng lỗi tổng có `role="alert"`/`aria-live`; submit lỗi focus tới field sai đầu tiên.
- Các thao tác async không biến lỗi thành `[]`, `null` hoặc `{}`. Có retry cho lỗi tải dữ liệu; mutation giữ input để người dùng sửa/thử lại.
- Chuẩn hóa loading và disabled reason cho nút submit.

### 7.2 Dialog, menu và thông báo

- Dialog giữ focus, khôi phục focus, hỗ trợ Escape và khóa tương tác nền.
- Apply dialog theo dõi dirty state; đóng bằng backdrop/Escape khi có dữ liệu chưa gửi phải xác nhận.
- Popover/menu có semantic phù hợp, `aria-expanded`, `aria-controls`, đóng bằng Escape và điều hướng bàn phím tối thiểu.
- Trạng thái quan trọng được thông báo qua live region, không chỉ bằng màu hoặc toast.
- Emoji trang trí được ẩn khỏi accessibility tree hoặc thay bằng icon nhất quán.

### 7.3 Responsive

- Candidate navigation có hành vi mobile chủ đích.
- Bộ lọc job chuyển thành drawer/sheet trên màn hình hẹp; nút mở/đóng có accessible name và focus management.
- Kiểm tra các breakpoint chính 320, 375, 768, 1024 và desktop; không có horizontal overflow ngoài thành phần chủ đích.

### 7.4 Copy và cấu trúc route

- Sửa “Cover Letter của tôi” thành nhãn đúng với route quản lý CV.
- Chuẩn hóa thuật ngữ status/action bằng một nguồn mapping dùng chung.
- Tách dần Candidate route/page, modal và hook ra khỏi `App.tsx`; dùng `React.lazy` theo route cho page lớn.
- Không thực hiện big-bang rewrite. Mỗi lần tách phải giữ nguyên hành vi và có test/smoke check trước khi tách phần tiếp theo.

## 8. Data flow và tương thích

### 8.1 Application snapshot

```text
Candidate chọn đúng một CV
  -> backend xác thực ownership/type
  -> tạo snapshot trong cùng transaction
  -> lưu Application + snapshot
  -> API detail đọc snapshot
  -> fallback live CV chỉ cho record cũ
```

### 8.2 Danh sách có phân trang

```text
URL/filter state
  -> service gọi API với page/size/filter
  -> hủy request cũ
  -> backend validate và query page
  -> PageResponse
  -> UI render items + metadata + trạng thái
```

### 8.3 Error contract

Giữ envelope lỗi hiện tại và bổ sung mã lỗi ổn định, `fieldErrors` khi validation thất bại, `retryAfterSeconds` cho rate limit. Không đưa stack trace, credential, token hoặc response thô của nhà cung cấp ra client.

## 9. Kiểm thử và tiêu chí hoàn thành

### Backend

- Unit/integration test cho token invalidation và auth rate limit.
- Integration test cho application snapshot và CV ownership/xor contract.
- Validation test cho profile DTO, application và report.
- Repository/service test cho batch saved/applied flags và pagination boundary.
- Authorization test cho company public projection và job alert ownership.
- Test profile readiness, resend verification và saved-job states.
- Tắt scheduler không liên quan trong test hoặc cung cấp schema đúng để test không còn log lỗi nền giả.

### Frontend

- Test URL filter parsing, page clamping và stale-response protection.
- Test lỗi field, focus field đầu tiên và dirty dialog confirmation.
- Test saved jobs, profile checklist, job alerts và navigation context.
- Test keyboard cơ bản cho dialog/menu/drawer.
- Smoke test từng Candidate route sau khi lazy split.

### Quality gate

Đợt cuối chỉ hoàn thành khi:

```text
backend:  mvn test
frontend: npm run test -- --run
frontend: npm run build
frontend: npm run lint
encoding: check_encoding.py cho toàn bộ file đã sửa có tiếng Việt/emoji
```

Không chấp nhận test “pass” trong khi scheduler hoặc background task ghi lỗi database không liên quan.

## 10. Bản đồ CIB dự kiến đóng

- Đợt 1: CIB-001, 002, 003, 004, 005, 007, 008, 014, 026 và baseline build.
- Đợt 2: CIB-016, 017, 018, 019, 029, 031, 032.
- Đợt 3: CIB-011, 012, 015, 020, 021, 022, 024.
- Đợt 4: CIB-013, 023, 025, 027, 028, 030, 033.
- Các mục đã đạt trước đó như CIB-006, 009 và 010 được regression-test, không thiết kế lại.

## 11. Rủi ro và kiểm soát

- Thay đổi response list sang page có thể ảnh hưởng consumer: tìm toàn bộ call site và cập nhật trong cùng đợt.
- Snapshot có dữ liệu cũ: luôn có fallback và migration không phá dữ liệu.
- Tách `App.tsx` dễ gây regression: tách theo route nhỏ, build/test sau từng nhóm.
- Rate limiting theo tiến trình không đồng bộ đa node: interface và cấu hình cho phép nâng cấp store; ghi rõ giới hạn triển khai hiện tại.
- File CV từng commit có thể vẫn tồn tại trong Git history: bỏ khỏi index chỉ ngăn commit tiếp; lịch sử cần quy trình riêng và xác nhận trước khi rewrite.
- Job-alert scheduler có thể chạy đồng thời ở nhiều instance: unique constraint alert/job và transaction idempotent phải ngăn notification trùng.

## 12. Deliverable

- Migration, backend API/service/test cần thiết.
- Candidate frontend pages/components/hooks/tests đã tách hợp lý.
- Build, lint, test và UTF-8 check đạt.
- `CANDIDATE_IMPROVEMENT_BACKLOG.md` được cập nhật trạng thái, bằng chứng file/test và residual risk cho từng mục ngoài AI.
- Không có thay đổi trong các module AI ngoài việc sửa compile nếu một type dùng chung bắt buộc ảnh hưởng; trường hợp đó phải giữ nguyên hành vi và được nêu rõ.
