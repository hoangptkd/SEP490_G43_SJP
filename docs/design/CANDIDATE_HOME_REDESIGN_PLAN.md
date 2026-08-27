# Candidate Home Redesign Plan

## Current Homepage Problems

- Hero quá marketing, chưa đặt job discovery làm trung tâm.
- Có CTA trùng nhau: nút tìm việc và search bar cùng dẫn tới job search.
- Có thống kê tĩnh không có nguồn dữ liệu thật.
- Candidate tools đặt trước job discovery nên người dùng phải cuộn mới thấy việc thật.
- Employer content chiếm trọng số quá lớn trên homepage dành cho Candidate.
- Homepage chưa hiển thị job thật, trong khi API `/jobs` đã hỗ trợ phân trang và filter.

## Proposed Information Architecture

1. Header compact
2. Job search hero
3. Quick category/search chips
4. Việc làm dành cho bạn hoặc fallback việc làm mới nhất
5. Việc làm mới nhất
6. Khám phá ngành nghề
7. Công ty đang tuyển, suy ra từ job thật
8. Công cụ quản lý tìm việc
9. Footer

## Sections to Remove

- Static stats không có dữ liệu thật.
- CTA banner cuối trang.
- Role cards Candidate/Employer dạng marketing.
- Feature card mô tả công nghệ chung hoặc Employer-heavy.

## Sections to Keep

- Header/topbar.
- Search form, nhưng nâng cấp thành search theo keyword và location.
- Footer, nhưng viết lại theo Candidate-first.
- Candidate avatar/notification menu khi đã đăng nhập.

## Sections to Add

- Job section dùng dữ liệu thật.
- Category discovery dùng `/categories`.
- Company discovery suy ra từ job response.
- Candidate tools thấp hơn job discovery.
- Loading/empty/error state cho từng section dữ liệu.

## Existing APIs Used

- `GET /jobs?page=0&size=6&sort=newest`
- `GET /jobs?search=&location=&category=`
- `GET /categories`
- `GET /candidate/recommendations/jobs` khi user là Candidate
- Candidate routes hiện có: `/candidate/cvs`, `/candidate/saved-jobs`, `/candidate/applications`, `/candidate/ai-interviews`

## Backend Gaps

- Public company list API chưa có.
- Category active job count chưa có.
- Homepage stats thật chưa có.
- Save/unsave job từ homepage chưa có tương tác riêng trong JobCard hiện tại; mở detail vẫn an toàn hơn.

## Existing Components Reused

- `JobCard`
- `CandidateHomeActions`
- `Shell` style tokens
- Existing `jobService`, `candidateService`

## Components to Create

- Không tách file mới trong lượt này vì `App.tsx` đang chứa homepage hiện tại.
- Tạo helper nhỏ trong `HomePage` cho company list, category chips, loading skeleton.
- Tái dùng `JobCard` thay vì tạo card giả.

## Desktop Layout

- Header sticky compact.
- Hero có search form lớn, hai input keyword/location và CTA tìm việc.
- Content max-width theo design token hiện có.
- Job sections dạng grid 2 cột/lưới responsive, khoảng cách dày hơn landing page cũ.
- Company/category/tools dùng card nhỏ, không dùng hero-scale typography.

## Mobile Layout

- Header wrap gọn, nav có thể cuộn ngang nếu thiếu chỗ.
- Search inputs stack một cột.
- Job cards một cột.
- Category và tools grid một cột hoặc hai cột nhỏ tùy viewport.
- Không có horizontal overflow.
