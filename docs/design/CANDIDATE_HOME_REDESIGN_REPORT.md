# Candidate Home Redesign Report

## What Changed

- Homepage được đổi từ landing page marketing sang trang khám phá việc làm cho Candidate.
- Hero mới tập trung vào job search với hai input thật: từ khóa và địa điểm.
- Bỏ số liệu tĩnh không có nguồn dữ liệu.
- Bỏ CTA marketing trùng lặp và role cards Candidate/Employer cũ.
- Thêm section việc làm mới nhất dùng dữ liệu thật từ `/jobs`.
- Thêm section việc làm dành cho bạn khi Candidate đăng nhập và recommendation API trả dữ liệu.
- Thêm quick category và category discovery dùng `/categories`.
- Thêm company discovery nhẹ, suy ra từ các job thật đang tải.
- Đưa Candidate tools xuống thấp hơn job discovery.
- Header Candidate expose nhanh các route thật: việc đã lưu, đã ứng tuyển, CV.

## Files Changed

- `frontend/src/App.tsx`
- `frontend/src/styles/global.css`
- `docs/audit/CANDIDATE_HOME_REDESIGN_ANALYSIS.md`
- `docs/design/CANDIDATE_HOME_REDESIGN_PLAN.md`
- `docs/design/CANDIDATE_HOME_REDESIGN_REPORT.md`

## Sections Removed

- Static statistics: 10,000+ jobs, 5,000+ companies, 50,000+ candidates, 98% satisfaction.
- Generic feature grid đặt cao trên trang.
- Candidate/Employer role cards có trọng số ngang nhau.
- Final marketing CTA banner.

## Sections Added

- Job search hero.
- Quick category chips.
- Personalized jobs, only when supported by real recommendation data.
- Latest jobs.
- Category discovery.
- Companies hiring, derived from real job data.
- Candidate tools.

## APIs Used

- `GET /jobs?page=0&size=6&sort=newest`
- `GET /categories`
- `GET /candidate/recommendations/jobs` for Candidate users

## Known Limitations

- Chưa có public company listing API nên section công ty chỉ suy ra từ job mới nhất.
- Category chưa có active job count.
- Homepage chưa có thống kê thật nên không hiển thị metrics.
- Save/unsave trực tiếp trên homepage chưa thêm để tránh thay đổi flow; Candidate mở Job Detail để thao tác.

## Verification

- UTF-8 encoding check: passed.
- `src/App.tsx` TypeScript check: no errors reported.
- `npm run build`: blocked by existing Employer TypeScript errors outside this redesign.

## Manual Tests Required

- Desktop: mở `/`, tìm theo keyword/location, click category, mở job detail.
- Candidate logged-in: kiểm tra avatar menu, notification menu, saved/applications/CV nav.
- Candidate logged-in with recommendations: kiểm tra section "Việc làm dành cho bạn".
- Guest: kiểm tra homepage không gọi candidate-only UI giả.
- Mobile: kiểm tra search stack, job card một cột, nav không gây overflow.
