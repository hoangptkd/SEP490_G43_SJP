# Checklist test UI/UX và thay đổi dữ liệu

Ngày tạo: 2026-08-04  
Dự án: Smart Job Portal  
Mục tiêu: giúp tester kiểm tra nhanh chức năng trên giao diện, trạng thái UX và dữ liệu thay đổi trong database sau mỗi thao tác.

## Cách dùng checklist

- Mỗi dòng là một nhóm test case thủ công.
- Test UI/UX trước, sau đó kiểm tra database theo cột "Bảng DB cần kiểm tra".
- Với chức năng chỉ đọc, tester chỉ cần kiểm tra dữ liệu hiển thị đúng, không có bản ghi mới hoặc cập nhật ngoài ý muốn.
- Sau thao tác tạo/sửa/xóa, nên refresh trang hoặc đăng nhập bằng vai trò liên quan để xác nhận dữ liệu đã phản ánh đúng.

Ký hiệu:

- `[ ]`: chưa test
- `[OK]`: pass
- `[BUG]`: có lỗi, cần ghi lại bước tái hiện, tài khoản test, ảnh màn hình và dữ liệu DB liên quan

## Tài khoản và dữ liệu cần chuẩn bị

| Nhóm dữ liệu | Cần có để test |
|---|---|
| Guest | Không đăng nhập, truy cập `/jobs`, `/jobs/:id`, `/login`, `/register` |
| Candidate | Tài khoản role candidate đã xác minh email, có ít nhất 1 CV PDF, có hồ sơ cá nhân |
| Employer | Tài khoản role employer, có công ty, có địa điểm làm việc, có hồ sơ pháp lý |
| Admin | Tài khoản role admin để duyệt công ty, duyệt job, quản lý user, gói và thanh toán |
| Job | Có job `draft`, `pending_review`, `published`, `rejected`, `closed`, `expired` |
| Application | Có hồ sơ ứng tuyển ở nhiều trạng thái: `applied`, `reviewed`, `shortlisted`, `interview_scheduled`, `accepted`, `rejected` |
| Payment | Có gói miễn phí, gói trả phí, payment `pending`, `paid`, `failed`, `cancelled` |

## Checklist chung UI/UX

| ID | Mục cần test | Kỳ vọng |
|---|---|---|
| UX-01 | Responsive desktop/mobile | Không vỡ layout, không overlap text, modal vừa màn hình, bảng/list dễ cuộn |
| UX-02 | Loading state | Khi gọi API có trạng thái loading, không cho bấm lặp gây duplicate |
| UX-03 | Empty state | Danh sách rỗng hiển thị thông báo rõ, không crash |
| UX-04 | Error state | Lỗi API/validation hiển thị dễ hiểu, không mất dữ liệu form đã nhập |
| UX-05 | Permission | Guest không vào được trang protected; sai role bị redirect đúng |
| UX-06 | Form validation | Required field, email, password, file PDF, số tiền, ngày deadline được chặn đúng |
| UX-07 | Confirm destructive action | Xóa CV, xóa location, xóa document, xóa job, hủy subscription phải có xác nhận |
| UX-08 | Refresh sau thao tác | Sau create/update/delete, list/detail cập nhật đúng mà không cần thao tác phức tạp |
| UX-09 | Tiếng Việt | Text hiển thị không bị lỗi font, không bị mojibake hoặc ký tự thay thế lỗi |
| UX-10 | Auth token hết hạn | Khi API trả 401, session bị clear và redirect về login phù hợp |

## Auth và tài khoản

| ID | Route | Vai trò | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|---|
| AUTH-01 | `/login` | Guest | Đăng nhập candidate/employer | Báo lỗi sai mật khẩu, login đúng chuyển route theo role | `POST /auth/login` | `users` | `last_login_at` cập nhật nếu backend có ghi; không đổi role/status |
| AUTH-02 | `/admin/login` | Guest/Admin | Đăng nhập admin | Non-admin không vào admin; admin vào dashboard | `POST /auth/login` | `users` | Chỉ user role admin được vào `/admin` |
| AUTH-03 | `/register` | Guest | Đăng ký candidate | Validate email/password, hiển thị thông báo xác minh email | `POST /auth/register` | `users`, `job_seekers`, `email_verification_tokens` | Tạo user role `job_seeker`, tạo profile `job_seekers`, token verify còn hạn |
| AUTH-04 | `/register` | Guest | Đăng ký employer | Form tạo tài khoản employer ổn định | `POST /auth/register` | `users`, `companies`, `employers`, `email_verification_tokens` | Tạo user role `employer`, công ty/employer liên kết đúng |
| AUTH-05 | `/verify-email` | Guest | Xác minh email | Token hợp lệ báo thành công, token sai/hết hạn báo lỗi | `POST /auth/verify-email` | `users`, `email_verification_tokens` | `users.email_verified_at` có giá trị, token có `used_at` |
| AUTH-06 | `/forgot-password` | Guest | Gửi email quên mật khẩu | Không lộ email có tồn tại hay không, thông báo rõ | `POST /auth/forgot-password` | `password_reset_tokens` | Tạo token mới cho user hợp lệ, token cũ xử lý theo logic backend |
| AUTH-07 | `/reset-password` | Guest | Đặt lại mật khẩu | Password mới validate đúng, token dùng lại bị chặn | `POST /auth/reset-password` | `users`, `password_reset_tokens` | `password_hash` đổi, token có `used_at` |
| AUTH-08 | `/oauth/callback`, `/select-role` | Guest | OAuth chọn role | Chọn candidate/employer xong vào đúng hệ thống | `POST /auth/oauth/complete-role`, `GET /auth/me` | `users`, `oauth_accounts`, `job_seekers`, `employers`, `companies` | User liên kết OAuth đúng provider, role và profile được tạo đúng |
| AUTH-09 | Header/layout | All | Đăng xuất | Token local bị xóa, quay lại login, back browser không vào được trang protected | `POST /auth/logout` | Không bắt buộc | Không tạo/sửa dữ liệu nghiệp vụ |

## Public job

| ID | Route | Vai trò | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|---|
| PUB-01 | `/jobs` | Guest/All | Xem danh sách job | Card đủ title, công ty, lương, địa điểm; phân trang/sort không vỡ | `GET /jobs` | `jobs`, `companies`, `company_locations`, `categories` | Chỉ hiển thị job public hợp lệ, không đổi DB |
| PUB-02 | `/jobs` | Guest/All | Search/filter job | Filter keyword/location/salary/skill/status reset được | `GET /jobs` | `jobs`, `job_skills`, `skills`, `companies` | Kết quả đúng điều kiện, không đổi DB |
| PUB-03 | `/jobs/:id` | Guest/All | Xem chi tiết job | Mô tả, yêu cầu, benefit, deadline, công ty hiển thị đủ | `GET /jobs/{id}` | `jobs` | Với job `published`, `views_count` tăng 1 khi xem detail |
| PUB-04 | `/jobs/:id` | Candidate | Lưu job | Nút lưu/đã lưu đổi trạng thái ngay, không tạo duplicate khi bấm lại | `POST /candidate/saved-jobs/{jobId}` | `saved_jobs` | Có 1 bản ghi theo `job_seeker_id`, `job_id` |
| PUB-05 | `/jobs/:id`, `/candidate/saved-jobs` | Candidate | Bỏ lưu job | UI bỏ badge đã lưu, list saved jobs mất item | `DELETE /candidate/saved-jobs/{jobId}` | `saved_jobs` | Bản ghi bị xóa |
| PUB-06 | `/jobs/:id` | Candidate | Báo cáo job | Form reason/description validate, báo cáo trùng pending bị chặn | `POST /jobs/{id}/reports` | `job_reports` | Tạo report `pending`, không tạo duplicate pending cùng user/job |

## Candidate

| ID | Route | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|
| CAN-01 | `/candidate` | Dashboard gợi ý việc làm | Card recommendation hiển thị điểm match/lý do nếu có | `GET /candidate/recommendations/jobs` | `ai_job_recommendations`, `jobs`, `companies` | Không đổi DB, dữ liệu gợi ý khớp candidate |
| CAN-02 | `/candidate/profile` | Xem hồ sơ | Thông tin load đúng, empty state rõ nếu chưa nhập | `GET /candidate/profile` | `job_seekers`, `candidate_skills`, `skills` | Không đổi DB |
| CAN-03 | `/candidate/profile` | Cập nhật hồ sơ | Lưu thành công, reload vẫn giữ dữ liệu, validate field bắt buộc | `PUT /candidate/profile` | `job_seekers`, `candidate_skills`, `skills` | `headline/summary/location`, `education_json`, `work_experience_json`, `projects_json`, `certifications_json` cập nhật đúng |
| CAN-04 | `/candidate/cvs` | Upload CV PDF | Chặn file sai định dạng/quá dung lượng; hiển thị tiến trình/lỗi | `POST /candidate/cvs` | `resumes` | Tạo resume `source_type='uploaded'`, `file_url/file_name/file_size`, `is_primary` đúng |
| CAN-05 | `/candidate/cvs` | Đặt CV mặc định | Badge CV mặc định đổi đúng, chỉ 1 CV mặc định | `PATCH /candidate/cvs/{id}/default` | `resumes` | Chỉ một bản ghi cùng `job_seeker_id` có `is_primary=true` |
| CAN-06 | `/candidate/cvs` | Xóa CV upload | Có confirm, xóa xong list cập nhật | `DELETE /candidate/cvs/{id}` | `resumes`, `applications` | CV bị xóa hoặc soft delete theo logic; application cũ không bị hỏng dữ liệu snapshot |
| CAN-07 | `/candidate/cvs` | Tạo CV Builder version | Form title/template/snapshot lưu đúng | `POST /candidate/cv-versions` | `resumes` | Tạo resume `source_type='builder'`, `content_json`, `template_key` đúng |
| CAN-08 | `/candidate/cvs` | Sửa/xóa CV Builder version | Dữ liệu reload đúng, xóa có confirm | `PUT/DELETE /candidate/cv-versions/{id}` | `resumes` | `content_json/template_key/title` cập nhật hoặc bản ghi bị xóa/soft delete |
| CAN-09 | `/jobs/:id` | Ứng tuyển bằng CV có sẵn | Modal chọn CV, cover letter, location; không submit thiếu CV/location | `POST /applications` | `applications`, `application_status_history`, `notifications`, `ai_ranking_results` | Tạo application, snapshot CV/job có dữ liệu, trạng thái ban đầu đúng, employer nhận notification nếu có |
| CAN-10 | `/jobs/:id` | Chặn ứng tuyển trùng | UI hiển thị đã ứng tuyển, không tạo bản ghi mới | `POST /applications` | `applications` | Unique theo `job_id`, `job_seeker_id` được giữ |
| CAN-11 | `/candidate/applications` | Xem danh sách đơn ứng tuyển | Filter/status/card đúng, trạng thái tiếng Việt đúng | `GET /applications/me` | `applications`, `jobs`, `companies` | Không đổi DB |
| CAN-12 | `/candidate/applications/:id` | Xem chi tiết đơn | Timeline, CV snapshot, job snapshot, trạng thái đúng | `GET /applications/me/{id}` | `applications`, `application_status_history`, `interview_schedules`, `job_offers` | Không đổi DB |
| CAN-13 | `/candidate/applications/:id` | Phản hồi lịch phỏng vấn | Confirm/decline/request reschedule rõ ràng | `PUT /v1/interviews/{id}/candidate-response` | `interview_schedules`, `notifications`, `application_status_history` | `candidate_response`, `candidate_response_at`, note cập nhật; employer nhận notification |
| CAN-14 | `/candidate/applications/:id` | Phản hồi offer | Accept/reject/final accept hiển thị đúng điều kiện | `PUT /v1/offers/{id}/response`, `/candidate-final-response` | `job_offers`, `applications`, `notifications`, `application_status_history` | Offer/application chuyển trạng thái đúng, lưu note ứng viên |
| CAN-15 | `/candidate/saved-jobs` | Xem job đã lưu | List đúng job đã lưu, click vào detail được | `GET /candidate/saved-jobs` | `saved_jobs`, `jobs` | Không đổi DB |
| CAN-16 | `/candidate/notifications` | Đọc notification | Badge unread giảm, item chuyển đã đọc | `PATCH /candidate/notifications/{id}/read` | `notifications` | `is_read=true`, `read_at` có giá trị nếu backend ghi |
| CAN-17 | `/candidate/notifications` | Đọc tất cả | Không còn unread badge | `PATCH /candidate/notifications/read-all` | `notifications` | Tất cả notification của user chuyển `is_read=true` |
| CAN-18 | `/candidate/subscription` | Xem gói hiện tại | Hiển thị plan, hạn dùng, usage rõ ràng | `GET /candidate/subscription`, `GET /billing/me` | `subscriptions`, `plans`, `subscription_usages` | Không đổi DB |
| CAN-19 | `/candidate/subscription/plans` | Chọn/mua gói | Plan đúng role, nút checkout rõ, không mua trùng gây pending rác | `GET /billing/plans`, `POST /billing/checkout` | `subscriptions`, `payments` | Tạo subscription/payment `pending`; hủy pending cũ nếu backend thay thế |

## Candidate AI interview

| ID | Route | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|
| AI-01 | `/candidate/ai-interviews` | Xem config AI | Nếu AI tắt phải hiển thị message rõ, không cho tạo session | `GET /candidate/ai-interviews/config-status` | `system_settings` | Không đổi DB |
| AI-02 | `/candidate/ai-interviews` | Tạo practice session | Validate target role/skill, tạo xong vào phòng phỏng vấn | `POST /candidate/ai-interviews/sessions/practice` | `interview_sessions`, `interview_questions`, `ai_question_sets`, `ai_question_bank` | Session `practice`, status `created/in_progress`, có đủ câu hỏi |
| AI-03 | `/candidate/ai-interviews` | Tạo session theo application | Chỉ hiện application đủ điều kiện, không tạo trùng sai application | `POST /candidate/ai-interviews/sessions/application` | `interview_sessions`, `interview_questions`, `applications` | Session link đúng `application_id`, `job_id` |
| AI-04 | `/candidate/ai-interviews` | Ghi âm và tạo transcript | Xin quyền microphone, lỗi permission rõ, audio max time được chặn | `POST /questions/current/audio` | `interview_answers` | Lưu `audio_url`, `duration_seconds`, `transcript_text`, `transcript_status` |
| AI-05 | `/candidate/ai-interviews` | Lưu câu trả lời text | Nút disabled khi transcript rỗng, tự chuyển câu tiếp theo | `POST /questions/{questionId}/answer` | `interview_answers`, `ai_answer_feedbacks` | Answer có `answered_at`, feedback có score/nhận xét hoặc fallback |
| AI-06 | `/candidate/ai-interviews` | Skip câu hỏi | Có trạng thái skipped rõ, không yêu cầu transcript | `POST /questions/{questionId}/skip` | `interview_answers` | `is_skipped=true`, `evaluation_source='skipped'` |
| AI-07 | `/candidate/ai-interviews` | Kết thúc phỏng vấn | Loading chấm điểm, result hiển thị score/strength/weakness | `POST /sessions/{sessionId}/finish` | `interview_sessions`, `ai_session_feedbacks` | Session `completed`, `completed_at`, `overall_score`, summary được lưu |
| AI-08 | `/candidate/ai-interviews` | Retry feedback/summary | Chỉ hiện khi fallback/lỗi, retry không tạo duplicate sai | `POST /feedback/retry`, `POST /summary/retry` | `ai_answer_feedbacks`, `ai_session_feedbacks` | Feedback/summary được cập nhật cùng answer/session |
| AI-09 | `/candidate/ai-interviews` | Xóa/hide session | Confirm rõ, list không còn session | `DELETE /candidate/ai-interviews/sessions/{id}` | `interview_sessions` | `deleted_at` có giá trị hoặc session bị xóa theo logic |

## Employer

| ID | Route | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|
| EMP-01 | `/employer` | Dashboard employer | Card số liệu/link điều hướng đúng | API dashboard nếu có | `jobs`, `applications`, `notifications`, `subscriptions` | Không đổi DB |
| EMP-02 | `/employer/company-profile` | Xem/sửa hồ sơ công ty | Validate tên/website/tax code, reload giữ dữ liệu | `GET/PUT /employer/company` | `companies`, `employers`, `company_industries`, `categories` | Công ty cập nhật đúng; nếu đã verified cần kiểm tra rule yêu cầu review lại |
| EMP-03 | `/employer/company-profile` | Upload logo | Preview đúng, chặn file lỗi, reload vẫn thấy logo | `POST /employer/company/logo` | `companies` | `logo_url` cập nhật |
| EMP-04 | `/employer/locations` | Thêm location | Form rõ ràng, tạo xong list cập nhật | `POST /employer/company/locations` | `company_locations`, `companies` | Tạo location đúng `company_id`, `is_headquarter` đúng |
| EMP-05 | `/employer/locations` | Sửa/xóa location | Không cho xóa headquarter nếu UI có rule; confirm xóa | `PUT/DELETE /employer/company/locations/{id}` | `company_locations`, `jobs` | Location cập nhật/xóa; job đang link location không bị lỗi |
| EMP-06 | `/employer/verification` | Upload document pháp lý | Chặn file sai, trạng thái pending rõ | `POST /employer/company/documents` | `company_documents`, `companies` | Tạo document `pending`; company có thể chuyển trạng thái chờ duyệt |
| EMP-07 | `/employer/verification` | Replace/delete document | Có confirm, replace giữ đúng document id hoặc tạo bản ghi theo logic | `POST /documents/{id}/replace`, `DELETE /documents/{id}` | `company_documents`, `companies` | File/status/reject_reason cập nhật đúng; xóa không còn hiện UI |
| EMP-08 | `/employer/jobs` | Tạo job draft | Form đầy đủ field, salary/deadline/required validate đúng | `POST /employer/jobs` | `jobs`, `job_skills`, `skills` | Tạo job `draft`, đúng company/employer/location/category |
| EMP-09 | `/employer/jobs` | Sửa job | Update giữ dữ liệu, không mất field phụ như benefit/vacancies/working_time | `PUT /employer/jobs/{id}` | `jobs`, `job_skills` | `updated_at` đổi, field cập nhật đúng |
| EMP-10 | `/employer/jobs` | Submit job để admin duyệt | Nút chỉ hiện khi hợp lệ, status đổi ngay | `POST /employer/jobs/{id}/submit-review` | `jobs`, `job_edit_history`, `notifications` | Job chuyển `pending_review`, có lịch sử chỉnh sửa nếu backend ghi |
| EMP-11 | `/employer/jobs` | Đóng/mở lại job | Confirm đóng, reopen yêu cầu deadline mới nếu cần | `POST /close`, `POST /reopen` | `jobs`, `applications`, `notifications` | `status`, `closed_at`, `deadline` cập nhật; ứng viên liên quan nhận notification nếu có |
| EMP-12 | `/employer/jobs` | Xóa job | Confirm, không xóa job published nếu rule chặn | `DELETE /employer/jobs/{id}` | `jobs`, `applications`, `saved_jobs` | Job bị xóa hoặc đổi trạng thái theo logic; dữ liệu liên quan không orphan |
| EMP-13 | `/employer/applications`, `/employer/jobs/:jobId/applications` | Xem ứng viên | Filter job/status/search đúng, download CV được | `GET /employer/applications`, `GET /employer/applications/{id}/cv` | `applications`, `resumes`, `jobs`, `job_seekers` | Không đổi DB khi chỉ xem/download |
| EMP-14 | `/employer/applications` | Cập nhật trạng thái đơn | Timeline/status đổi đúng, note lưu đúng | `PUT /employer/applications/{id}/status` | `applications`, `application_status_history`, `notifications` | `applications.status`, `status_updated_by`, history và notification cập nhật |
| EMP-15 | `/employer/applications` | Từ chối ứng viên | Confirm/reason rõ, candidate nhận thông báo | `POST /v1/applications/{id}/reject` | `applications`, `application_status_history`, `notifications` | Application `rejected`, có note |
| EMP-16 | `/employer/applications` | Tạo lịch phỏng vấn | Validate ngày tương lai/link/location, hiện trong detail candidate | `POST /v1/applications/{id}/interviews` | `interview_schedules`, `applications`, `notifications` | Tạo interview, application chuyển `interview_scheduled` nếu backend xử lý |
| EMP-17 | `/employer/applications` | Cập nhật kết quả phỏng vấn | Pass/fail/no_show rõ, note lưu | `PUT /v1/interviews/{id}/result` | `interview_schedules`, `applications`, `notifications` | `interview_result`, `result_updated_by`, `result_updated_at` cập nhật |
| EMP-18 | `/employer/applications` | Tạo offer | Validate salary/start date/expire date, candidate thấy offer | `POST /v1/applications/{id}/offers` | `job_offers`, `applications`, `notifications` | Offer `sent`, `sent_at`, chỉ 1 offer/application |
| EMP-19 | `/employer/applications` | Phản hồi khi candidate reject offer | Update offer hoặc từ chối thương lượng đúng luồng | `PUT /v1/offers/{id}/employer-response` | `job_offers`, `applications`, `notifications` | Status offer đúng: `sent`, `employer_declined_negotiation`, hoặc trạng thái liên quan |
| EMP-20 | `/employer/notifications` | Đọc notification | Badge/list cập nhật như candidate | `PATCH /employer/notifications/{id}/read`, `/read-all` | `notifications` | Notification của employer chuyển `is_read=true` |
| EMP-21 | `/employer/subscription`, `/employer/subscription/plans` | Xem/mua gói employer | Chỉ hiện plan phù hợp employer, checkout đúng | `GET /billing/me`, `POST /billing/checkout` | `plans`, `subscriptions`, `payments`, `subscription_usages` | Tạo/cập nhật subscription/payment đúng user employer |

## Admin

| ID | Route | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|
| ADM-01 | `/admin` | Dashboard | Metric khớp dữ liệu thật, loading/empty rõ | `GET /admin/dashboard` | `users`, `companies`, `jobs`, `applications`, `payments`, `subscriptions` | Không đổi DB |
| ADM-02 | `/admin/companies` | List/filter công ty | Filter pending/verified/rejected, detail load đúng | `GET /admin/companies` | `companies`, `employers`, `company_documents`, `users` | Không đổi DB |
| ADM-03 | `/admin/companies/:id` | Duyệt công ty | Confirm approve, status đổi ở list và employer | `POST /admin/companies/{id}/approve` | `companies`, `employers`, `company_documents`, `notifications`, `admin_audit_logs` | Company verified/active, document approved nếu rule áp dụng, employer nhận notification |
| ADM-04 | `/admin/companies/:id` | Từ chối công ty | Bắt nhập reason, employer thấy lý do | `POST /admin/companies/{id}/reject` | `companies`, `employers`, `company_documents`, `notifications`, `admin_audit_logs` | Status rejected, lưu reason nếu có trường tương ứng |
| ADM-05 | `/admin/companies/:id` | Duyệt/từ chối document | Nút đúng theo trạng thái document | `POST /documents/{documentId}/approve/reject` | `company_documents`, `companies`, `notifications`, `admin_audit_logs` | Document `approved/rejected`, `reviewed_by`, `reviewed_at`, `reject_reason` đúng |
| ADM-06 | `/admin/jobs` | List/filter job | Filter pending/published/rejected/reports đúng | `GET /admin/jobs`, `GET /admin/jobs/reports` | `jobs`, `companies`, `job_reports` | Không đổi DB |
| ADM-07 | `/admin/jobs/:id` | Duyệt job | Job xuất hiện ở public sau khi duyệt | `POST /admin/jobs/{id}/approve` | `jobs`, `job_review_history`, `notifications`, `admin_audit_logs` | Job `published`, `reviewed_by_user_id`, `published_at`, history `APPROVED` |
| ADM-08 | `/admin/jobs/:id` | Từ chối job | Bắt nhập reason, employer thấy lý do | `POST /admin/jobs/{id}/reject` | `jobs`, `job_review_history`, `notifications`, `admin_audit_logs` | Job `rejected`, `rejection_reason`, history `REJECTED` |
| ADM-09 | `/admin/jobs/:id` | Đóng/mở lại job | Nút hiển thị đúng trạng thái, public list cập nhật | `POST /admin/jobs/{id}/close`, `/reopen` | `jobs`, `notifications`, `admin_audit_logs` | Job `closed/published`, `closed_at/deadline` đúng |
| ADM-10 | `/admin/jobs?status=reports` | Xử lý report job | Dismiss/notify/resolve rõ ràng, tránh double submit | `POST /reports/{id}/dismiss`, `/notify-company`, `/resolve` | `job_reports`, `jobs`, `notifications`, `admin_audit_logs` | Report đổi `dismissed/resolved`; nếu vi phạm job có thể `removed/closed` theo logic |
| ADM-11 | `/admin/users` | Quản lý user | Filter role/status, suspend/activate confirm | `GET /admin/users`, `POST /suspend`, `POST /activate` | `users`, `admin_audit_logs` | User `suspended/active`, không tự đổi role |
| ADM-12 | `/admin/billing` | Xem plan/subscription/payment/revenue | Tab, filter, số tiền định dạng VND đúng | `GET /admin/billing/*` | `plans`, `subscriptions`, `payments`, `subscription_usages` | Không đổi DB khi chỉ xem |
| ADM-13 | `/admin/billing/plans/new`, `/edit` | Tạo/sửa plan | Validate price/duration/features/status, quay lại list thấy plan | `POST/PUT /admin/billing/plans` | `plans`, `admin_audit_logs` | Plan tạo/cập nhật đúng `target_role`, `features`, `status` |
| ADM-14 | `/admin/billing` | Xóa/ẩn plan | Không xóa plan đang dùng nếu backend chặn; UI báo rõ | `DELETE /admin/billing/plans/{id}` | `plans`, `subscriptions`, `admin_audit_logs` | Plan bị xóa hoặc chuyển inactive/archived theo logic |
| ADM-15 | `/admin/billing` | Cancel/activate subscription | Confirm, reason lưu, user thấy gói đổi | `POST /subscriptions/{id}/cancel`, `/activate` | `subscriptions`, `subscription_usages`, `admin_audit_logs` | `status`, `cancelled_at`, `cancelled_reason`, `start_date/end_date` đúng |
| ADM-16 | `/admin/billing` | Confirm bank payment | Sau confirm user có gói active | `POST /admin/billing/payments/{id}/confirm` | `payments`, `subscriptions`, `subscription_usages`, `admin_audit_logs` | Payment `paid`, `paid_at`, `transaction_id`; subscription `active` |
| ADM-17 | `/admin/settings` | Cập nhật settings | Save rõ, reload giữ cấu hình; màu/theme/public settings đổi đúng | `GET/PUT /admin/settings` | `system_settings`, `admin_audit_logs` | `setting_value`, `updated_at`, `updated_by` cập nhật |
| ADM-18 | `/admin/settings?tab=categories` | Tạo/bật/tắt category | Slug unique, inactive không hiện ở public nếu rule áp dụng | `GET/POST/PUT /admin/categories` | `categories`, `admin_audit_logs` | Category tạo/cập nhật `status`, `slug` đúng |
| ADM-19 | `/admin/statistics` | Thống kê | Date range/filter đúng, số liệu khớp dashboard | `GET /admin/dashboard/statistics` | `users`, `companies`, `jobs`, `applications`, `payments`, `subscriptions` | Không đổi DB |
| ADM-20 | `/admin/audit-logs` | Xem audit log | Filter target/limit đúng, action mới nhất lên đầu | `GET /admin/audit-logs` | `admin_audit_logs`, `users` | Không đổi DB |
| ADM-21 | `/admin/profile` | Hồ sơ admin | Email/role/status đúng, link điều hướng đúng | `GET /auth/me` | `users` | Không đổi DB |

## Billing và thanh toán cho user

| ID | Route | Chức năng cần test | UI/UX cần kiểm tra | API chính | Bảng DB cần kiểm tra | Dữ liệu kỳ vọng |
|---|---|---|---|---|---|---|
| PAY-01 | `/candidate/subscription/plans`, `/employer/subscription/plans` | Chọn gói miễn phí | Không redirect payment nếu price 0 | `POST /billing/checkout` | `subscriptions`, `payments`, `subscription_usages` | Payment `paid` với transaction free, subscription `active` |
| PAY-02 | `/payment/checkout` | Checkout PayOS/VNPay/Momo | Redirect đúng gateway, back/cancel xử lý rõ | `POST /billing/checkout` | `payments`, `subscriptions` | Payment/subscription ban đầu `pending` |
| PAY-03 | `/payment/bank/:paymentId` | Thanh toán chuyển khoản | Hiển thị số tiền/nội dung chuyển khoản/copy được | `GET /billing/payments/{id}/bank-transfer` | `payments`, `subscriptions` | Không đổi DB khi chỉ xem; sau admin confirm chuyển paid/active |
| PAY-04 | `/payment/result` | Kết quả thanh toán | Success/fail/pending rõ, có nút quay lại dashboard | `GET /billing/payments/{id}` | `payments`, `subscriptions`, `payment_webhook_events` | Payment reflect đúng gateway; nếu paid thì subscription active |
| PAY-05 | Callback gateway | Webhook/IPN | Không duplicate khi webhook gọi lại nhiều lần | `/payments/payos/webhook`, `/payments/momo/ipn`, `/payments/vnpay/ipn` | `payments`, `subscriptions`, `payment_webhook_events`, `subscription_usages` | Idempotent, không tạo nhiều active subscription sai |

## Bảng database liên quan theo module

| Module | Bảng chính | Bảng phụ/liên quan | Ghi chú kiểm tra |
|---|---|---|---|
| Auth | `users` | `email_verification_tokens`, `password_reset_tokens`, `oauth_accounts` | Kiểm tra role/status/email_verified/token |
| Candidate profile | `job_seekers` | `candidate_skills`, `skills` | Hồ sơ section JSON nằm trong `job_seekers` |
| CV | `resumes` | `applications`, `ai_ranking_results` | Cùng bảng cho CV upload và CV builder, phân biệt bằng `source_type` |
| Public job | `jobs` | `companies`, `company_locations`, `categories`, `job_skills`, `skills`, `saved_jobs`, `job_reports` | Xem detail job published tăng `views_count` |
| Application | `applications` | `application_status_history`, `resumes`, `jobs`, `job_seekers`, `notifications` | Cần kiểm tra snapshot CV/job sau apply |
| Interview workflow | `interview_schedules` | `applications`, `job_offers`, `notifications`, `application_status_history` | Lịch phỏng vấn thủ công khác với AI interview |
| Offer | `job_offers` | `applications`, `notifications`, `application_status_history` | Có unique offer theo application |
| AI interview | `interview_sessions` | `interview_questions`, `interview_answers`, `ai_answer_feedbacks`, `ai_session_feedbacks`, `ai_question_sets`, `ai_question_bank`, `system_settings` | Kiểm tra status, score, fallback, deleted_at |
| Employer company | `companies` | `employers`, `company_locations`, `company_documents`, `company_industries`, `categories`, `notifications` | Duyệt/từ chối ảnh hưởng khả năng đăng job |
| Employer job | `jobs` | `job_skills`, `job_edit_history`, `job_review_history`, `notifications`, `applications` | Status job quyết định hiển thị public |
| Admin | `admin_audit_logs` | Tất cả bảng nghiệp vụ | Hành động admin quan trọng nên có audit |
| Billing | `plans` | `subscriptions`, `payments`, `subscription_usages`, `payment_webhook_events`, `system_settings` | Kiểm tra trạng thái payment/subscription đồng bộ |
| Notification | `notifications` | `users`, các bảng ref theo `ref_type/ref_id` | Mark read phải chỉ ảnh hưởng notification của user hiện tại |

## SQL mẫu kiểm tra nhanh

Thay các giá trị trong `:param` bằng dữ liệu thật khi test.

```sql
-- User theo email
select id, email, role, status, email_verified_at, last_login_at, created_at, updated_at
from users
where email = :email;

-- Candidate profile và kỹ năng
select js.*
from job_seekers js
join users u on u.id = js.user_id
where u.email = :candidate_email;

select cs.*, s.name
from candidate_skills cs
join skills s on s.id = cs.skill_id
join job_seekers js on js.id = cs.job_seeker_id
join users u on u.id = js.user_id
where u.email = :candidate_email;

-- CV của candidate
select r.id, r.title, r.source_type, r.template_key, r.file_name, r.file_size,
       r.is_primary, r.deleted_at, r.created_at, r.updated_at
from resumes r
join job_seekers js on js.id = r.job_seeker_id
join users u on u.id = js.user_id
where u.email = :candidate_email
order by r.created_at desc;

-- Job và số lượt xem
select id, title, status, views_count, rejection_reason, published_at, closed_at, deadline, updated_at
from jobs
where id = :job_id;

-- Saved job
select sj.*
from saved_jobs sj
join job_seekers js on js.id = sj.job_seeker_id
join users u on u.id = js.user_id
where u.email = :candidate_email and sj.job_id = :job_id;

-- Application và timeline
select a.id, a.job_id, a.job_seeker_id, a.resume_id, a.status,
       a.preferred_location, a.cover_letter, a.ai_match_score,
       a.job_snapshot_json, a.resume_snapshot_json, a.applied_at, a.updated_at
from applications a
where a.id = :application_id;

select *
from application_status_history
where application_id = :application_id
order by created_at desc;

-- Interview schedule
select *
from interview_schedules
where application_id = :application_id
order by created_at desc;

-- Offer
select *
from job_offers
where application_id = :application_id;

-- Company, locations, documents
select c.*
from companies c
where c.id = :company_id;

select *
from company_locations
where company_id = :company_id
order by is_headquarter desc, created_at desc;

select *
from company_documents
where company_id = :company_id
order by uploaded_at desc;

-- Job review history
select *
from job_review_history
where job_id = :job_id
order by reviewed_at desc;

-- Job reports
select *
from job_reports
where job_id = :job_id
order by created_at desc;

-- Notification của user
select n.*
from notifications n
join users u on u.id = n.user_id
where u.email = :email
order by n.created_at desc;

-- AI interview session
select *
from interview_sessions
where id = :session_id;

select *
from interview_questions
where session_id = :session_id
order by order_index;

select ia.*, af.overall_score, af.feedback
from interview_answers ia
left join ai_answer_feedbacks af on af.answer_id = ia.id
where ia.session_id = :session_id
order by ia.answered_at;

select *
from ai_session_feedbacks
where session_id = :session_id;

-- Billing
select *
from subscriptions
where user_id = :user_id
order by created_at desc;

select *
from payments
where user_id = :user_id
order by created_at desc;

select *
from subscription_usages
where subscription_id = :subscription_id;

-- Admin audit
select *
from admin_audit_logs
where target_id = :target_id
order by created_at desc;
```

## Lưu ý nghiệp vụ cần soi kỹ

- Candidate và employer route trên frontend cần test sai role thật kỹ: candidate thử vào `/employer`, employer thử vào `/candidate`, non-admin thử vào `/admin`.
- Job chỉ nên xuất hiện public khi đã được duyệt/published và chưa hết hạn/đóng.
- Employer chỉ nên tạo/submit job khi công ty đủ điều kiện xác thực theo rule hiện tại.
- Application không được tạo trùng cho cùng candidate và job.
- CV/application/job snapshot phải giữ dữ liệu cũ để sau này xem lại không bị thay đổi khi CV/job gốc bị sửa.
- Các hành động admin như duyệt/từ chối công ty, duyệt/từ chối job, confirm payment, suspend user nên có audit log.
- Các thao tác notification phải chỉ cập nhật notification thuộc user hiện tại.
- Payment webhook/IPN cần test gọi lại nhiều lần để chắc chắn không tạo nhiều subscription active hoặc đổi trạng thái sai.
