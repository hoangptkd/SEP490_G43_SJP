-- Số tin đăng miễn phí mặc định cho employer (chưa mua gói)
UPDATE system_settings
SET setting_value = '15',
    updated_at = now()
WHERE setting_key = 'max_free_job_posts';
