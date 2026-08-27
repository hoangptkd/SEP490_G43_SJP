# Candidate Home Redesign Analysis

## Mục tiêu

Homepage hiện tại cần chuyển từ landing page marketing sang trang khám phá việc làm cho ứng viên. Màn hình đầu tiên phải trả lời câu hỏi: "Tôi có thể tìm và ứng tuyển công việc phù hợp nào?"

## Đánh giá section hiện tại

| Section | Quyết định | Lý do |
|---|---|---|
| Header | MODIFY | Header có link việc làm và tài khoản, nhưng còn thiếu các shortcut Candidate quan trọng khi đã đăng nhập như việc đã lưu, hồ sơ ứng tuyển, CV. Link Employer chỉ nên là phụ. |
| Hero marketing | MODIFY | Headline hiện nói chung về nền tảng và chia đều Candidate/Employer. Cần đổi trọng tâm sang tìm việc. |
| CTA "Tìm việc làm ngay" | REMOVE | Bị trùng với search bar. Search phải là CTA chính. |
| Search bar | KEEP/MODIFY | Đây là phần có giá trị cao nhất. Nên thêm input địa điểm vì API `/jobs` đã hỗ trợ `location`. |
| Static stats | REMOVE | Các số 10,000+, 5,000+, 50,000+, 98% không được chứng minh từ database hoặc API hiện có. Không nên hiển thị social proof giả. |
| Features grid | MOVE/MODIFY | Các công cụ Candidate có giá trị nhưng nên đặt sau job discovery. Bỏ nội dung công nghệ chung, viết lại theo outcome của ứng viên. |
| Candidate/Employer role cards | REMOVE/MODIFY | Homepage Candidate không nên dành trọng số ngang nhau cho Employer. Chỉ giữ Employer như link phụ ở footer/header. |
| CTA banner cuối trang | REMOVE | Trùng với search/register và vẫn mang tính marketing. |
| Footer | MODIFY | Giữ footer nhưng ưu tiên Candidate links, Employer chỉ là nhóm phụ. |

## Nội dung cần thêm

| Section | Nguồn dữ liệu thật | Ghi chú |
|---|---|---|
| Việc làm dành cho bạn | `jobService.recommendations()` nếu user là Candidate | Nếu lỗi/không có dữ liệu, không fake. |
| Việc làm mới nhất | `jobService.getAll({ sort: 'newest' }, 0, 6)` | Dùng API sẵn có, không tải toàn bộ dataset. |
| Khám phá theo ngành nghề | `jobService.getCategories()` | Hiển thị category thật; không hiển thị số lượng nếu backend chưa trả count. |
| Công ty đang tuyển | Suy ra từ danh sách job mới nhất | Không có public company API riêng, nên chỉ hiển thị công ty xuất hiện trong job thật. |
| Công cụ Candidate | Route hiện có: CV, saved jobs, applications, AI interviews | Đặt thấp hơn job discovery. |

## Backend gaps

- Chưa có public company listing API.
- Category API chưa trả số lượng active jobs theo ngành.
- Job recommendation endpoint nằm dưới Candidate auth, cần fallback khi chưa đăng nhập hoặc không có dữ liệu.
- Chưa có API thống kê thật cho homepage.
