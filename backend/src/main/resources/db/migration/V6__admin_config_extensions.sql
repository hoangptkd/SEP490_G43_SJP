CREATE TABLE IF NOT EXISTS system_settings (
  setting_key text PRIMARY KEY,
  setting_value text NOT NULL,
  description text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO system_settings (setting_key, setting_value, description)
VALUES
  ('ai_system_prompt', 'Bạn là trợ lý phỏng vấn chuyên nghiệp. Đặt câu hỏi rõ ràng, ngắn gọn và đưa phản hồi mang tính xây dựng.', 'Prompt hệ thống cho phỏng vấn AI'),
  ('ai_feedback_prompt', 'Đánh giá câu trả lời theo tiêu chí: nội dung, cấu trúc, mức độ phù hợp công việc. Trả về điểm và nhận xét ngắn.', 'Prompt phản hồi sau mỗi câu trả lời'),
  ('payment_gateway_provider', 'vnpay', 'Nhà cung cấp cổng thanh toán mặc định'),
  ('payment_gateway_merchant_id', '', 'Merchant ID / Terminal ID của cổng thanh toán'),
  ('payment_gateway_sandbox', 'true', 'Chế độ sandbox cho cổng thanh toán'),
  ('theme_mode', 'light', 'Giao diện mặc định: light hoặc dark'),
  ('theme_primary_color', '#00507d', 'Màu chủ đạo của hệ thống'),
  ('max_ai_sessions_per_day', '5', 'Giới hạn số phiên phỏng vấn AI mỗi ứng viên mỗi ngày'),
  ('max_applications_per_day', '20', 'Giới hạn số lần ứng tuyển mỗi ứng viên mỗi ngày')
ON CONFLICT (setting_key) DO NOTHING;
