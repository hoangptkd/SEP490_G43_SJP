# Thiết kế tìm việc phù hợp bằng AI

Ngày: 2026-08-11

Trạng thái: Đã duyệt thiết kế trong brainstorming, chờ lập kế hoạch triển khai

Phạm vi: Candidate tìm top 10 công việc phù hợp bằng AI từ Profile và CV mặc định

## 1. Bối cảnh

Hệ thống hiện có:

- tìm kiếm job theo keyword, category, kỹ năng, lương, kinh nghiệm, job type và work mode;
- gợi ý Candidate theo rule dựa trên skill overlap và location;
- ShopAIKey cho AI Interview và AI Ranking ứng viên của Employer;
- bảng `ai_job_recommendations` đã có trong schema nhưng chưa có luồng Candidate AI Job Search hoàn chỉnh;
- Profile có kỹ năng, headline, bio, kinh nghiệm, học vấn, dự án và chứng chỉ; CV mặc định có thể là CV tải lên hoặc CV tạo bằng trình dựng.

Tính năng mới phải tách khỏi AI Interview và AI Ranking của Employer, không làm thay đổi hành vi hai luồng đó.

## 2. Quyết định sản phẩm đã chốt

- Candidate không nhập prompt tìm việc; AI tự phân tích Profile và CV mặc định.
- Candidate chủ động bấm nút “Tìm việc phù hợp bằng AI”.
- Nút nằm tại khu vực tìm kiếm của Home.
- Sau khi bấm, điều hướng tới `/jobs?mode=ai`.
- Kết quả gồm top 10 job phù hợp nhất.
- Mỗi kết quả có điểm, kỹ năng khớp, kỹ năng còn thiếu và lý do ngắn.
- Candidate có thể lọc tiếp top 10 theo location, salary, job type và work mode.
- Kết quả cache trong 24 giờ và mất hiệu lực khi Profile hoặc CV mặc định thay đổi.
- Mọi Candidate được dùng tính năng với quota theo gói, reset hàng tháng.
- UI hiển thị số lượt còn lại và thời điểm cache hết hạn.
- Candidate xác nhận consent một lần theo `policyVersion` và có thể thu hồi.
- Provider lỗi thì hiển thị lỗi và cho thử lại; không fallback sang rule-based matching.

## 3. Mục tiêu

1. Giúp Candidate nhận danh sách job có thứ tự phù hợp dựa trên dữ liệu nghề nghiệp thật.
2. Giải thích được vì sao một job phù hợp và Candidate còn thiếu gì.
3. Kiểm soát chi phí provider bằng prefilter, cache, quota và chống request trùng.
4. Không gửi PII không cần thiết cho provider và có consent audit rõ ràng.
5. Có benchmark để đánh giá chất lượng thay vì chỉ dựa vào demo chủ quan.

## 4. Ngoài phạm vi

- Không xây vector database hoặc embedding pipeline trong phiên bản này.
- Không tự động chạy AI khi mở Home hoặc Jobs.
- Không gửi email, phone, date of birth, tên thật hoặc file CV gốc cho provider.
- Không dùng kết quả này để Employer tự động loại ứng viên.
- Không thay thế tìm kiếm job thường.
- Không thay đổi prompt, scoring hoặc lifecycle của AI Interview trong cùng đợt triển khai.
- Không học từ click/save/apply trong phiên bản đầu.

## 5. Kiến trúc

```text
Home AI button
  -> /jobs?mode=ai
  -> GET status/cache/readiness/consent/quota
  -> consent gate nếu cần
  -> POST generate hoặc lấy cache
  -> CandidateContext từ Profile + CV mặc định
  -> deterministic prefilter
  -> ShopAIKey rerank
  -> strict validation
  -> transactional persist
  -> top 10 tren JobsPage
```

### 5.1 Thành phần backend

#### `AiJobSearchController`

- Chỉ cho phép role Candidate.
- Cung cấp API status, consent, generate/cache và revoke consent.
- Không trả provider response thô.

#### `AiJobSearchService`

- Điều phối readiness, consent, cache, quota và concurrency.
- Tạo `PROCESSING` run trước khi gọi provider.
- Chỉ persist recommendations và consume quota sau khi response hợp lệ.
- Không ghi đè cache thành công cũ khi refresh thất bại.

#### `AiJobSearchCandidateContextBuilder`

- Lấy Profile của Candidate hiện tại.
- Phân giải CV mặc định bất kể loại: CV tải lên hoặc CV tạo bằng trình dựng; nếu không có thì chỉ dùng Profile và gắn `lowConfidence=true`.
- Với CV tải lên chưa có `parsedText`, trích xuất bằng Tika từ private storage và lưu text đã chuẩn hóa.
- Với CV tạo bằng trình dựng, chuẩn hóa `contentJson` thành văn bản nghề nghiệp có cấu trúc; không đưa JSON thô hoặc trường trình bày vào prompt.
- Loại PII và giới hạn CV text tối đa 12.000 ký tự trước khi tạo prompt.

#### `AiJobSearchCandidateSelector`

- Chỉ lấy job `published`, chưa hết hạn và company không bị xóa/khóa.
- Tính prefilter score theo skill overlap, experience, location/work mode và freshness.
- Lấy tối đa 30 candidate job, sau đó compact tối đa 20 job tốt nhất cho prompt.
- Không dùng `listingPriority` làm tín hiệu match AI.

#### `ShopAiKeyJobSearchClient`

- Client riêng cho AI Job Search.
- Dùng cùng biến môi trường `SHOPAIKEY_API_KEY`, `SHOPAIKEY_BASE_URL`, `SHOPAIKEY_MODEL` nhưng có properties và prompt version riêng.
- Gửi strict JSON schema, temperature thấp và timeout cấu hình được.
- Tự retry tối đa một lần khi response không parse/validate được; không retry vô hạn.

#### `AiJobSearchResultValidator`

- Job ID phải thuộc candidate pool.
- Không chấp nhận job ID trùng.
- `matchScore` phải nằm trong 0–100.
- `matchedSkills` và `missingSkills` phải có giới hạn số lượng/độ dài.
- `reason` bắt buộc, tiếng Việt, tối đa 500 ký tự.
- Kết quả cuối cùng tối đa 10 job.

### 5.2 Thành phần frontend

- Home thêm nút AI ngay dưới form tìm kiếm thường.
- Guest bấm nút được chuyển tới login với return URL `/jobs?mode=ai`.
- Candidate bấm nút được chuyển ngay tới `/jobs?mode=ai`.
- JobsPage nhận `mode=ai` và hiển thị AI banner, consent/readiness/loading/error/result state.
- AI mode có nút quay về search thường và nút “Tìm lại”.
- Filter trong AI mode chỉ lọc client-side trên top 10, không gọi provider và không thay đổi rank gốc.

## 6. Data model

### 6.1 `ai_job_search_runs`

Thêm bảng:

```text
id uuid PK
job_seeker_id uuid FK -> job_seekers
status varchar: PROCESSING | SUCCEEDED | FAILED
input_hash varchar(64)
profile_updated_at timestamptz nullable
cv_id uuid nullable
cv_type varchar nullable: UPLOADED | BUILDER
cv_updated_at timestamptz nullable
model_used varchar nullable
prompt_version varchar not null
result_count integer not null default 0
quota_consumed boolean not null default false
failure_code varchar nullable
started_at timestamptz not null
completed_at timestamptz nullable
expires_at timestamptz nullable
created_at timestamptz not null
```

Ràng buộc/index:

- partial unique index trên `job_seeker_id` khi `status='PROCESSING'`;
- index `(job_seeker_id, created_at desc)`;
- index cho cleanup theo `created_at`;
- `input_hash` không chứa raw Profile/CV.

### 6.2 `ai_job_recommendations`

Tận dụng bảng hiện có và bổ sung:

```text
run_id uuid nullable FK -> ai_job_search_runs
rank_position integer nullable
```

`reason_json` lưu:

```json
{
  "matchedSkills": ["Java", "Spring Boot"],
  "missingSkills": ["AWS"],
  "reason": "Kinh nghiệm backend phù hợp với vị trí.",
  "lowConfidence": false
}
```

Mỗi lần thành công sẽ thay thế transactional bộ recommendations hiện tại của Candidate. Run metadata cũ được giữ tạm thời, không giữ raw prompt/response.

### 6.3 `candidate_ai_consents`

```text
id uuid PK
job_seeker_id uuid FK -> job_seekers
purpose varchar not null = AI_JOB_SEARCH
policy_version varchar not null
granted_at timestamptz not null
revoked_at timestamptz nullable
created_at timestamptz not null
```

Chỉ consent chưa revoke và đúng policy version hiện tại mới hợp lệ. Khi policy version thay đổi, Candidate phải consent lại.

### 6.4 Quota

- Feature key: `ai_job_searches`.
- Plan feature key: `maxAiJobSearchesPerMonth`.
- Free system setting: `max_ai_job_searches_per_month`.
- Free default: 3 lượt/tháng.
- Reset theo tháng lịch trong timezone `Asia/Ho_Chi_Minh`.
- Cache hit, validation failure và provider failure không consume quota.
- Force refresh thành công consume một lượt.
- `limit < 0` được hiểu là không giới hạn, phù hợp convention hiện có.

## 7. API contract

Base path: `/candidate/ai-job-search`

### 7.1 `GET /status`

Không gọi provider. Response:

```json
{
  "enabled": true,
  "consentRequired": false,
  "policyVersion": "ai-job-search-v1",
  "readiness": {
    "profileAvailable": true,
    "defaultCvAvailable": true,
    "lowConfidence": false,
    "missingItems": []
  },
  "quota": {
    "used": 1,
    "limit": 3,
    "remaining": 2,
    "resetAt": "2026-09-01T00:00:00+07:00"
  },
  "cache": {
    "available": true,
    "generatedAt": "2026-08-11T10:00:00+07:00",
    "expiresAt": "2026-08-12T10:00:00+07:00",
    "stale": false
  }
}
```

### 7.2 `POST /consent`

Request:

```json
{
  "accepted": true,
  "policyVersion": "ai-job-search-v1"
}
```

Backend không chấp nhận policy version cũ/không hợp lệ.

### 7.3 `DELETE /consent`

- Revoke consent hiện tại.
- Xóa recommendations/cache hiện tại của Candidate.
- Không xóa metadata quota/run không chứa nội dung CV.

### 7.4 `POST /search`

Request:

```json
{
  "forceRefresh": false
}
```

Response:

```json
{
  "source": "AI",
  "cached": false,
  "lowConfidence": false,
  "generatedAt": "2026-08-11T10:00:00+07:00",
  "expiresAt": "2026-08-12T10:00:00+07:00",
  "quota": {
    "used": 2,
    "limit": 3,
    "remaining": 1,
    "resetAt": "2026-09-01T00:00:00+07:00"
  },
  "items": [
    {
      "rank": 1,
      "job": {},
      "matchScore": 92,
      "matchedSkills": ["Java", "Spring Boot"],
      "missingSkills": ["AWS"],
      "reason": "Kinh nghiệm backend và kỹ năng Spring Boot phù hợp tốt với vị trí."
    }
  ]
}
```

## 8. Matching pipeline

### 8.1 Input hash và cache

Input hash SHA-256 được tạo từ canonical representation của:

- các Profile field nghề nghiệp;
- skill list đã trim/sort/deduplicate;
- typed education/work/project/certification data;
- default CV ID, loại CV, `updatedAt` và hash của nội dung nghề nghiệp đã chuẩn hóa;
- prompt version.

Cache hợp lệ khi:

- run gần nhất `SUCCEEDED`;
- `expiresAt > now()`;
- input hash khớp;
- consent còn hợp lệ;
- recommendations tham chiếu job vẫn public và chưa hết hạn.

Job mới không tự động phá cache 24 giờ. Candidate có thể chủ động “Tìm lại” để lấy job mới và consume quota.

### 8.2 Prefilter

Prefilter chỉ là candidate generation, không được hiển thị là điểm AI. Tín hiệu dự kiến:

- skill overlap: 45%;
- experience/seniority: 20%;
- location và work mode: 15%;
- title/category relevance: 10%;
- freshness: 10%.

Nếu Profile thiếu tín hiệu, selector bổ sung job mới theo category phổ biến để tránh pool rỗng, nhưng kết quả phải gắn `lowConfidence=true`.

### 8.3 Prompt và output

Prompt phải nêu rõ:

- đây là recommendation cho Candidate, không phải quyết định tuyển dụng;
- chỉ được chọn ID trong danh sách;
- score theo thang 0–100;
- matched/missing skill phải dựa trên context;
- reason bằng tiếng Việt có dấu;
- chỉ trả JSON, không markdown.

Prompt version ban đầu: `ai-job-search-v1`. Model và prompt version được ghi trong run để benchmark và audit.

## 9. UI/UX

### 9.1 Home

- Giữ nguyên form search thường.
- Thêm nút full-width phía dưới: “Tìm việc phù hợp bằng AI”.
- Candidate đã login thấy quota còn lại bên dưới nút.
- Guest thấy copy “Đăng nhập để AI phân tích Profile và CV”.
- Employer/Admin không nhìn thấy Candidate AI CTA.

### 9.2 Jobs AI mode

AI banner hiển thị:

- tiêu đề “10 công việc phù hợp nhất”;
- nguồn dữ liệu Profile + CV mặc định hoặc Profile-only;
- quota used/remaining/reset time;
- generated time và cache expiry;
- nút “Tìm lại” và “Quay về tìm thường”.

Mỗi card hiển thị:

- rank và match score;
- job/company/location/salary/work mode;
- matched skills;
- missing skills;
- reason;
- save/apply/open detail actions theo logic hiện có.

### 9.3 Consent dialog

Dialog nêu rõ:

- dữ liệu nghề nghiệp nào được xử lý;
- PII nào không được gửi;
- nhà cung cấp AI;
- mục đích và policy version;
- cách thu hồi consent.

Dialog dùng accessible primitive hiện có: focus trap, Escape policy, restore focus và `aria-describedby`.

### 9.4 Filter trong AI mode

- Location: text/select phù hợp UI hiện có.
- Salary min/max: validate min <= max.
- Job type và work mode: select.
- Filter chỉ ẩn/hiện item trong top 10.
- Rank và match score không tính lại khi filter.
- URL giữ `mode=ai` cùng filter để refresh/back không mất ngữ cảnh.

## 10. Trạng thái và lỗi

| Trạng thái | Hành vi |
|---|---|
| Guest | Redirect login, giữ return URL AI mode. |
| Consent missing/outdated | Hiện consent dialog trước khi gọi AI. |
| Profile unavailable | Chặn run và link tới Profile. |
| Default CV unavailable | Cho phép Profile-only, hiển thị low-confidence warning. |
| Cache valid | Trả ngay, không consume quota. |
| PROCESSING conflict | Trả `409 AI_JOB_SEARCH_IN_PROGRESS`, UI khóa action. |
| Quota exhausted | Trả `402 PLAN_LIMIT_REACHED`, kèm usage/reset và upgrade link. |
| AI disabled/misconfigured | Trả `503 AI_JOB_SEARCH_UNAVAILABLE`. |
| Provider timeout/failure | Trả lỗi đã làm sạch, run `FAILED`, không consume quota. |
| Invalid provider JSON | Retry một lần; vẫn sai thì `FAILED`. |
| Empty candidate pool | Trả empty state, không gọi provider và không consume quota. |
| Refresh failure with old cache | Giữ cache cũ trong DB; UI hiển thị lỗi refresh và cho xem lại cache AI cũ có nhãn stale. |

Không trả stack trace, provider body, raw prompt hoặc raw CV text cho frontend.

## 11. Consent, privacy và retention

- Consent purpose cố định: `AI_JOB_SEARCH`.
- Policy version cấu hình được, mặc định `ai-job-search-v1`.
- Context provider chỉ gồm headline, bio nghề nghiệp, location, experience, skills, typed profile sections và nội dung nghề nghiệp từ CV đã chuẩn hóa, giới hạn.
- Không gửi user ID nội bộ; job dùng opaque ID cần thiết để validate response.
- Không persist raw provider request/response.
- Không ghi PII, CV text, prompt hoặc API key vào log.
- Revoke consent xóa recommendations hiện tại và invalid cache.
- Run metadata không chứa CV content được giữ 90 ngày để audit quota, sau đó scheduler xóa.
- Consent audit được giữ theo vòng đời tài khoản và chính sách hệ thống.

## 12. Observability

Metrics không chứa PII:

- request count theo status: cache hit, success, failed, quota denied;
- provider latency;
- schema retry count;
- pool size và result count;
- low-confidence rate;
- average match score distribution;
- cache hit rate;
- monthly usage theo plan ID, không theo email/user name.

Structured log chỉ dùng run ID, status, model, prompt version, duration và error code đã làm sạch.

## 13. Testing

### 13.1 Backend unit/service tests

- Consent bắt buộc và policy version đúng.
- Candidate không đọc run/recommendation của user khác.
- Cache hit không gọi provider và không consume quota.
- Profile/CV thay đổi làm input hash thay đổi.
- Context builder xử lý đúng cả CV tải lên và CV tạo bằng trình dựng.
- Chỉ một run `PROCESSING` trên Candidate.
- Failure không consume quota; success consume đúng một lượt.
- Quota reset theo tháng `Asia/Ho_Chi_Minh`.
- Client timeout, malformed JSON và retry một lần.
- Validator chặn ID ngoài pool, duplicate ID, score ngoài range và text quá dài.
- Context builder không chứa email, phone, DOB hoặc full name.
- Transactional replace không làm mất cache cũ khi run mới fail.
- Cleanup scheduler chỉ xóa run metadata quá 90 ngày.

### 13.2 Integration tests

- Migration trên PostgreSQL cho runs, consent, recommendation columns và partial unique index.
- API authorization, validation, response envelope và error codes.
- Private CV parsing từ storage và chuẩn hóa `contentJson` của CV tạo bằng trình dựng.
- Plan/free quota và usage response.
- Job hết hạn/closed không xuất hiện trong response dù có recommendation cũ.

### 13.3 Frontend tests

- Home AI CTA điều hướng đúng.
- Guest login return URL.
- Consent dialog keyboard/focus/accessibility.
- Loading, error, quota, cache, stale, low-confidence và empty states.
- Force refresh confirmation.
- Filter client-side không thay rank.
- Switch AI/regular mode không gọi nhầm API.
- URL refresh/back giữ AI mode và filters.

### 13.4 AI benchmark

Tạo fixture Candidate/Profile/CV và job được con người gắn nhãn relevance. Lưu model, prompt version và thời điểm benchmark.

Ngưỡng chấp nhận ban đầu:

- `Recall@10 >= 0.80`;
- `NDCG@10 >= 0.70`;
- unsupported-job rate sau backend validation bằng 0;
- tối thiểu 90% explanation claim có grounding trong Profile/CV/job input;
- schema-valid rate được theo dõi trước và sau một lần retry.

CI dùng fake provider deterministic. Provider integration và benchmark thật chỉ chạy thủ công khi có API key, không bắt buộc trong CI thường.

## 14. Migration và tương thích

- Tạo migration mới sau `V34`.
- Không sửa migration cũ.
- `run_id`/`rank_position` của `ai_job_recommendations` nullable để tương thích record cũ.
- Endpoint recommendations rule-based hiện tại tiếp tục hoạt động cho Home/Candidate dashboard đến khi frontend AI mode dùng endpoint mới.
- FeatureLimitService được mở rộng cho chu kỳ monthly mà không đổi hành vi daily quota hiện có.
- Admin plan form và plan response thêm `maxAiJobSearchesPerMonth`.
- AI Job Search có cấu hình enable/disable riêng; provider thiếu key không làm backend fail startup nếu tính năng tắt.

## 15. Tiêu chí hoàn thành

Tính năng chỉ được coi là hoàn thành khi:

1. Candidate có thể bấm nút AI ở Home và nhận top 10 tại `/jobs?mode=ai`.
2. Consent, quota tháng, cache 24 giờ, invalidation và concurrency guard hoạt động.
3. Kết quả có rank, score, matched skills, missing skills và reason.
4. Filter trong AI mode hoạt động mà không gọi lại AI.
5. Provider lỗi không fallback rule-based, không consume quota và không làm mất cache AI cũ.
6. Không có PII/CV text/raw provider body trong API response hoặc log.
7. Backend/frontend tests, build, lint, PostgreSQL migration và UTF-8 check đều pass.
8. Benchmark được version hóa và đạt ngưỡng đã định trước khi tuyên bố chất lượng production.

## 16. Rủi ro còn lại

- Prefilter heuristic có thể loại job phù hợp trước khi AI nhìn thấy; benchmark phải đo cả prefilter recall.
- CV parsed text có thể thiếu layout/ngữ cảnh; UI phải nói rõ đây là gợi ý.
- Provider model/prompt drift có thể làm thay đổi rank; model và prompt version phải được ghi lại.
- In-process provider calls không thay thế job queue bền vững; phiên bản đầu dùng synchronous request có timeout cấu hình. Nếu latency/thông lượng tăng, có thể nâng cấp sang async queue mà giữ nguyên result model và UI state.
- Quota theo tháng lịch có thể khác billing anniversary; phiên bản đầu dùng ranh giới tháng `Asia/Ho_Chi_Minh` để quy tắc minh bạch và nhất quán.
