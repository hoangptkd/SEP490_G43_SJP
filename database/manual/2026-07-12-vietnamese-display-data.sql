-- Normalize demo/display data to Vietnamese with accents.
-- Scope: user-facing seed/demo text only. Technical fields such as email, slug,
-- role, status, URL, provider id, model name, and file path are intentionally left unchanged.

SET client_encoding TO 'UTF8';

BEGIN;

-- Companies
UPDATE companies
SET description = 'Phòng thí nghiệm sản phẩm cho các quy trình tuyển dụng thông minh.',
    industry = 'Công nghệ nhân sự',
    location = 'Thành phố Hồ Chí Minh'
WHERE id = 'b41ee9ee-ed8d-4728-8fad-005801cc6bf2';

UPDATE companies
SET description = 'Công ty demo về gia công phần mềm và phát triển sản phẩm.',
    industry = 'Phát triển phần mềm',
    location = 'Hà Nội'
WHERE id = '0954d976-40f2-46b1-ad79-c73f5eff6ae0';

UPDATE companies
SET description = 'Công ty chuyên cung cấp phần mềm.',
    industry = 'Bán lẻ',
    location = 'Bắc Ninh'
WHERE id = 'df9676ba-2b4b-4a26-a468-3cf3d9ff2839';

UPDATE companies
SET description = 'Công ty phần mềm.',
    industry = 'Công nghệ thông tin',
    location = 'Hồ Chí Minh'
WHERE id = '2021ca7b-90bc-404d-99c5-68514d6cb67d';

-- Locations
UPDATE company_locations
SET branch_name = CASE branch_name
        WHEN 'Ho Chi Minh City' THEN 'Thành phố Hồ Chí Minh'
        WHEN 'Ha Noi' THEN 'Hà Nội'
        WHEN 'TIên DU' THEN 'Tiên Du'
        WHEN 'hm' THEN 'Hồ Chí Minh'
        ELSE branch_name
    END,
    city = CASE city
        WHEN 'Ho Chi Minh City' THEN 'Thành phố Hồ Chí Minh'
        WHEN 'Ha Noi' THEN 'Hà Nội'
        WHEN 'Hcm' THEN 'Hồ Chí Minh'
        WHEN 'hm' THEN 'Hồ Chí Minh'
        WHEN 'hn' THEN 'Hà Nội'
        ELSE city
    END,
    district = CASE district
        WHEN 'cg' THEN 'Cầu Giấy'
        WHEN 'cầu giấy' THEN 'Cầu Giấy'
        WHEN 'quận ba đình' THEN 'Quận Ba Đình'
        ELSE district
    END,
    country = CASE country
        WHEN 'Vietnam' THEN 'Việt Nam'
        ELSE country
    END;

-- Jobs
UPDATE jobs
SET title = 'Thực tập sinh DevOps Backend',
    description = 'Hỗ trợ CI/CD, giám sát hệ thống và vận hành nền tảng backend.',
    requirements = E'Docker\nAWS\nLinux\nKiến thức cơ bản về Spring Boot',
    location = 'Hà Nội'
WHERE id = 'cc5b1000-9e9b-4fed-8542-ba08fc1d2ac3';

UPDATE jobs
SET title = 'Lập trình viên Java Backend',
    description = 'Xây dựng REST API và các dịch vụ nghiệp vụ cho sản phẩm tuyển dụng.',
    requirements = E'Java 17\nSpring Boot\nPostgreSQL\nThiết kế REST API',
    location = 'Hà Nội'
WHERE id = 'b6026358-d81e-479e-a6d1-e93ff1616e8c';

UPDATE jobs
SET title = 'Kỹ sư nền tảng Java cao cấp',
    description = 'Dẫn dắt kiến trúc backend cho các dịch vụ tuyển dụng có lưu lượng cao.',
    requirements = E'Java\nSpring Boot\nPostgreSQL\nRedis\nThiết kế hệ thống',
    location = 'Thành phố Hồ Chí Minh'
WHERE id = 'bccd37d3-f14a-4e17-bfab-b9be0a59205f';

UPDATE jobs
SET title = 'Kỹ sư Frontend React',
    description = 'Xây dựng giao diện cho ứng viên và nhà tuyển dụng bằng React.',
    requirements = E'React\nTypeScript\nThiết kế UI component\nTích hợp API',
    location = 'Đà Nẵng'
WHERE id = '53b3cb9a-118f-4192-b563-c21204cd763e';

UPDATE jobs
SET title = 'Kỹ sư nền tảng Cloud',
    description = 'Vận hành hạ tầng cloud cho các sản phẩm tuyển dụng.',
    requirements = 'AWS, Docker, Kubernetes, CI/CD, giám sát hệ thống.',
    location = 'Đà Nẵng'
WHERE id = 'a69fa2bb-181d-4bec-a453-b8664f8f9672';

UPDATE jobs
SET title = 'Lập trình viên Fullstack React Spring',
    description = 'Phát triển các tính năng dành cho ứng viên trên React và Spring Boot.',
    requirements = 'React, TypeScript, Java, Spring Boot, PostgreSQL.',
    location = 'Thành phố Hồ Chí Minh'
WHERE id = '8314b4c4-7da1-4ab0-9750-4fcf51ff358c';

UPDATE jobs
SET title = 'Kỹ sư tích hợp dữ liệu',
    description = 'Tích hợp nguồn dữ liệu việc làm và xây dựng pipeline phân tích.',
    requirements = 'PostgreSQL, mô hình hóa dữ liệu, tích hợp API, tối ưu SQL.',
    location = 'Hà Nội'
WHERE id = '7c1f8dd9-09a3-4c72-82fe-77de5a7ed4bd';

UPDATE jobs
SET title = 'Kỹ sư API Backend từ xa',
    description = 'Thiết kế API cho các luồng matching, thông báo và gói dịch vụ.',
    requirements = 'Spring Boot, PostgreSQL, Redis, thiết kế REST rõ ràng.',
    location = 'Từ xa'
WHERE id = '7c5d945e-d540-4ed0-8ff2-32b327fd38e9';

UPDATE jobs
SET title = 'Nhân viên marketing',
    description = 'Điều phối hoạt động marketing và hỗ trợ triển khai chiến dịch.',
    location = 'Hồ Chí Minh'
WHERE id = '8093a1e4-75a9-4ea7-b11a-b7cf8c74e03e';

UPDATE jobs
SET title = 'Lập trình viên Java',
    description = 'Phát triển và bảo trì các tính năng backend bằng Java.',
    requirements = 'Nắm vững Java, tư duy logic và kỹ năng làm việc nhóm.',
    location = 'Hồ Chí Minh'
WHERE id = '1d66e626-c234-4c49-ad1b-e1ea60ec705f';

UPDATE jobs
SET title = 'Lập trình viên Backend',
    description = 'Phát triển dịch vụ backend ổn định và dễ mở rộng.',
    requirements = 'Tư duy logic tốt, hiểu REST API và cơ sở dữ liệu.',
    location = 'Hồ Chí Minh'
WHERE id = '561dc167-f01e-46e2-bbdf-fadab198e312';

UPDATE jobs
SET title = 'Chuyên gia marketing',
    description = 'Xây dựng chiến dịch marketing đa kênh và tối ưu hiệu quả chuyển đổi.',
    requirements = 'Tốt nghiệp chuyên ngành liên quan, có tư duy phân tích và sáng tạo.',
    location = 'Hồ Chí Minh'
WHERE id = 'c9a105b4-096d-4c01-b6c5-1fd68d2cace4';

UPDATE jobs
SET description = 'Xây dựng và triển khai các chiến dịch marketing có trách nhiệm.',
    requirements = 'Tư duy logic tốt và khả năng lập kế hoạch rõ ràng.',
    location = 'Hồ Chí Minh'
WHERE id = '9728d009-aa93-484d-a5dd-4df59f595411';

UPDATE jobs
SET title = 'Chuyên viên an toàn thông tin',
    description = 'Kiểm thử bảo mật và hỗ trợ phát hiện rủi ro trong hệ thống.',
    requirements = 'Hiểu kiến thức bảo mật cơ bản và có tinh thần học hỏi.',
    location = 'Hồ Chí Minh'
WHERE id = 'a88b8b29-9b40-4d1d-bf25-ee8836caea53';

UPDATE jobs
SET location = CASE location
        WHEN 'Ha Noi' THEN 'Hà Nội'
        WHEN 'Ho Chi Minh City' THEN 'Thành phố Hồ Chí Minh'
        WHEN 'Da Nang' THEN 'Đà Nẵng'
        WHEN 'Remote' THEN 'Từ xa'
        WHEN 'hm' THEN 'Hồ Chí Minh'
        ELSE location
    END;

-- Candidate/user/profile demo text
UPDATE job_seekers
SET headline = 'Lập trình viên Java/Spring Boot',
    summary = 'Lập trình viên backend có kinh nghiệm với Spring Boot, PostgreSQL và tích hợp React.',
    location = 'Hà Nội'
WHERE id = '0f24f767-7845-4bda-927c-ff0d61b0d5cb';

UPDATE users
SET full_name = CASE full_name
        WHEN 'Nguyen Van Candidate' THEN 'Nguyễn Văn Candidate'
        WHEN 'Tran Thi Employer' THEN 'Trần Thị Employer'
        WHEN 'Admin Demo' THEN 'Quản trị viên Demo'
        WHEN 'Le Minh Recruiter' THEN 'Lê Minh Recruiter'
        WHEN 'System Admin' THEN 'Quản trị hệ thống'
        ELSE full_name
    END
WHERE full_name IN ('Nguyen Van Candidate', 'Tran Thi Employer', 'Admin Demo', 'Le Minh Recruiter', 'System Admin');

-- Plans and categories
UPDATE plans
SET name = CASE name
        WHEN 'FREE' THEN 'Miễn phí'
        WHEN 'Free Candidate' THEN 'Gói ứng viên miễn phí'
        WHEN 'PRO_CANDIDATE' THEN 'Gói ứng viên Pro'
        WHEN 'Candidate Pro' THEN 'Ứng viên Pro'
        WHEN 'Employer Starter' THEN 'Nhà tuyển dụng khởi đầu'
        ELSE name
    END,
    description = CASE description
        WHEN 'Basic candidate plan' THEN 'Gói cơ bản dành cho ứng viên.'
        WHEN 'Basic candidate plan for profile, CV and applications.' THEN 'Gói cơ bản cho hồ sơ, CV và ứng tuyển.'
        WHEN 'Candidate pro demo plan' THEN 'Gói ứng viên Pro dùng cho demo.'
        WHEN 'Priority candidate plan for full testing.' THEN 'Gói ưu tiên cho ứng viên dùng để kiểm thử đầy đủ.'
        WHEN 'Starter employer plan for demo job posting.' THEN 'Gói khởi đầu cho nhà tuyển dụng đăng tin demo.'
        ELSE description
    END;

UPDATE categories
SET name = CASE name
        WHEN 'Frontend-Developer' THEN 'Lập trình viên Frontend'
        WHEN 'Backend-Developer' THEN 'Lập trình viên Backend'
        ELSE name
    END,
    description = CASE description
        WHEN 'Software development roles' THEN 'Các vị trí phát triển phần mềm'
        WHEN 'Data, analytics, and AI roles' THEN 'Các vị trí dữ liệu, phân tích và AI'
        WHEN 'DevOps and platform roles' THEN 'Các vị trí DevOps và nền tảng'
        WHEN 'Backend engineering roles' THEN 'Các vị trí kỹ sư backend'
        WHEN 'Data platform roles' THEN 'Các vị trí nền tảng dữ liệu'
        WHEN 'Frontend engineering roles' THEN 'Các vị trí kỹ sư frontend'
        WHEN 'Fullstack product engineering roles' THEN 'Các vị trí kỹ sư fullstack sản phẩm'
        WHEN 'ReactJS Development' THEN 'Phát triển ReactJS'
        WHEN 'Backend' THEN 'Phát triển Backend'
        ELSE description
    END;

-- Notifications and application history
UPDATE notifications
SET title = CASE title
        WHEN 'Da gui ho so ung tuyen' THEN 'Đã gửi hồ sơ ứng tuyển'
        WHEN 'Saved job reminder' THEN 'Nhắc nhở việc làm đã lưu'
        WHEN 'Chao mung den Smart Recruitment' THEN 'Chào mừng đến Smart Recruitment'
        WHEN 'Application reviewed' THEN 'Hồ sơ ứng tuyển đã được xem xét'
        ELSE title
    END,
    message = CASE
        WHEN message = 'Tai khoan demo da san sang de test full flow.' THEN 'Tài khoản demo đã sẵn sàng để kiểm thử toàn bộ luồng.'
        WHEN message = 'Fullstack React Spring Developer is still accepting applications.' THEN 'Vị trí Lập trình viên Fullstack React Spring vẫn đang nhận hồ sơ.'
        WHEN message = 'Your demo application for Java Backend Developer has been reviewed.' THEN 'Hồ sơ demo của bạn cho vị trí Lập trình viên Java Backend đã được xem xét.'
        WHEN message LIKE 'Ban da ung tuyen thanh cong vao vi tri %.' THEN replace(replace(message, 'Ban da ung tuyen thanh cong vao vi tri ', 'Bạn đã ứng tuyển thành công vào vị trí '), 'marketing.', 'Nhân viên marketing.')
        ELSE message
    END;

UPDATE notifications
SET message = replace(message, 'Remote Backend API Engineer', 'Kỹ sư API Backend từ xa')
WHERE message LIKE '%Remote Backend API Engineer%';

UPDATE application_status_history
SET note = CASE note
        WHEN 'Ho so ung tuyen da duoc gui thanh cong.' THEN 'Hồ sơ ứng tuyển đã được gửi thành công.'
        WHEN 'Ho so ung tuyen demo da duoc gui thanh cong.' THEN 'Hồ sơ ứng tuyển demo đã được gửi thành công.'
        WHEN 'Employer reviewed the demo application.' THEN 'Nhà tuyển dụng đã xem xét hồ sơ demo.'
        ELSE note
    END;

-- AI interview display text
UPDATE interview_sessions
SET title = replace(title, 'Luyen phong van', 'Luyện phỏng vấn'),
    ai_summary = CASE ai_summary
        WHEN 'Tong ket tam thoi: ban da hoan thanh buoi luyen tap. Hay tiep tuc cai thien do cu the, cau truc cau tra loi va ket qua do luong duoc.'
        THEN 'Tổng kết tạm thời: bạn đã hoàn thành buổi luyện tập. Hãy tiếp tục cải thiện độ cụ thể, cấu trúc câu trả lời và kết quả đo lường được.'
        ELSE ai_summary
    END
WHERE title LIKE 'Luyen phong van:%'
   OR ai_summary = 'Tong ket tam thoi: ban da hoan thanh buoi luyen tap. Hay tiep tuc cai thien do cu the, cau truc cau tra loi va ket qua do luong duoc.';

UPDATE interview_questions
SET content = CASE content
        WHEN 'Hay gioi thieu ngan gon ve kinh nghiem cua ban va ly do ban phu hop voi vi tri nay.'
            THEN 'Hãy giới thiệu ngắn gọn về kinh nghiệm của bạn và lý do bạn phù hợp với vị trí này.'
        WHEN 'Hay mo ta mot du an gan day ma ban tu hao nhat. Ban da dong gop gi va ket qua ra sao?'
            THEN 'Hãy mô tả một dự án gần đây mà bạn tự hào nhất. Bạn đã đóng góp gì và kết quả ra sao?'
        WHEN 'Khi gap mot yeu cau kho hoac thay doi gap, ban se phan tich va xu ly nhu the nao?'
            THEN 'Khi gặp một yêu cầu khó hoặc thay đổi gấp, bạn sẽ phân tích và xử lý như thế nào?'
        WHEN 'Khi thiet ke API quan ly don hang bang Spring Boot, ban se xu ly transaction va tranh cap nhat dong thoi sai du lieu nhu the nao?'
            THEN 'Khi thiết kế API quản lý đơn hàng bằng Spring Boot, bạn sẽ xử lý transaction và tránh cập nhật đồng thời sai dữ liệu như thế nào?'
        WHEN 'Neu duoc nhan vao vai tro nay, 30 ngay dau tien ban se uu tien nhung viec gi?'
            THEN 'Nếu được nhận vào vai trò này, 30 ngày đầu tiên bạn sẽ ưu tiên những việc gì?'
        WHEN 'Hay chia se mot lan ban phai hoc cong nghe moi trong thoi gian ngan. Ban da hoc va ap dung ra sao?'
            THEN 'Hãy chia sẻ một lần bạn phải học công nghệ mới trong thời gian ngắn. Bạn đã học và áp dụng ra sao?'
        ELSE content
    END;

UPDATE ai_answer_feedbacks
SET feedback = CASE feedback
        WHEN 'Tra loi theo cau truc: boi canh, hanh dong, ket qua.' || E'\n' || 'Bo sung vi du cu the va so lieu neu co.'
            THEN 'Trả lời theo cấu trúc: bối cảnh, hành động, kết quả.' || E'\n' || 'Bổ sung ví dụ cụ thể và số liệu nếu có.'
        WHEN 'Feedback tam thoi: cau tra loi da co y chinh, nhung can them boi canh, hanh dong cu the va ket qua do luong duoc.'
            THEN 'Feedback tạm thời: câu trả lời đã có ý chính, nhưng cần thêm bối cảnh, hành động cụ thể và kết quả đo lường được.'
        ELSE feedback
    END,
    strengths = CASE strengths
        WHEN 'Cau tra loi co du thong tin de bat dau danh gia.' THEN 'Câu trả lời có đủ thông tin để bắt đầu đánh giá.'
        ELSE strengths
    END,
    weaknesses = CASE weaknesses
        WHEN 'Nen lam ro hon tac dong va ket qua co the do luong.' THEN 'Nên làm rõ hơn tác động và kết quả có thể đo lường.'
        WHEN 'Cau tra loi con ngan, can them boi canh va ket qua cu the.' THEN 'Câu trả lời còn ngắn, cần thêm bối cảnh và kết quả cụ thể.'
        ELSE weaknesses
    END,
    suggestions = CASE suggestions
        WHEN 'Tra loi theo cau truc: boi canh, hanh dong, ket qua.' || E'\n' || 'Bo sung vi du cu the va so lieu neu co.'
            THEN 'Trả lời theo cấu trúc: bối cảnh, hành động, kết quả.' || E'\n' || 'Bổ sung ví dụ cụ thể và số liệu nếu có.'
        ELSE suggestions
    END;

UPDATE ai_session_feedbacks
SET ai_summary = CASE ai_summary
        WHEN 'Tong ket tam thoi: ban da hoan thanh buoi luyen tap. Hay tiep tuc cai thien do cu the, cau truc cau tra loi va ket qua do luong duoc.'
            THEN 'Tổng kết tạm thời: bạn đã hoàn thành buổi luyện tập. Hãy tiếp tục cải thiện độ cụ thể, cấu trúc câu trả lời và kết quả đo lường được.'
        ELSE ai_summary
    END,
    strengths = CASE strengths
        WHEN 'Ban da hoan thanh day du cac cau hoi luyen tap.' THEN 'Bạn đã hoàn thành đầy đủ các câu hỏi luyện tập.'
        ELSE strengths
    END,
    weaknesses = CASE weaknesses
        WHEN 'Can tiep tuc bo sung vi du cu the va ket qua do luong duoc trong cau tra loi.'
            THEN 'Cần tiếp tục bổ sung ví dụ cụ thể và kết quả đo lường được trong câu trả lời.'
        ELSE weaknesses
    END,
    suggestions = CASE suggestions
        WHEN 'On lai transcript tung cau.' || E'\n' || 'Chuan bi cau tra loi theo cau truc boi canh, hanh dong, ket qua.' || E'\n' || 'Thu luyen lai voi cac cau hoi kho hon.'
            THEN 'Ôn lại transcript từng câu.' || E'\n' || 'Chuẩn bị câu trả lời theo cấu trúc bối cảnh, hành động, kết quả.' || E'\n' || 'Thử luyện lại với các câu hỏi khó hơn.'
        ELSE suggestions
    END;

COMMIT;
