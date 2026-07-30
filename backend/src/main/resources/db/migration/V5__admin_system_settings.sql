CREATE TABLE IF NOT EXISTS system_settings (
  setting_key text PRIMARY KEY,
  setting_value text NOT NULL,
  description text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO system_settings (setting_key, setting_value, description)
VALUES
  ('site_name', 'Smart Recruitment Portal', 'Tên hiển thị của hệ thống'),
  ('support_email', 'support@sjp.local', 'Email hỗ trợ người dùng'),
  ('maintenance_mode', 'false', 'Bật chế độ bảo trì toàn hệ thống'),
  ('ai_interview_enabled', 'true', 'Cho phép ứng viên dùng phỏng vấn AI'),
  ('payment_gateway_enabled', 'true', 'Cho phép thanh toán gói dịch vụ'),
  ('max_free_job_posts', '3', 'Số tin tuyển dụng miễn phí tối đa mỗi nhà tuyển dụng'),
  ('company_review_required', 'true', 'Bắt buộc duyệt hồ sơ công ty trước khi đăng tin')
ON CONFLICT (setting_key) DO NOTHING;
