-- Allow PayOS as payment method
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;

ALTER TABLE payments
  ADD CONSTRAINT payments_payment_method_check
  CHECK (
    payment_method IS NULL
    OR payment_method IN ('bank_transfer', 'credit_card', 'momo', 'vnpay', 'paypal', 'cash', 'payos')
  );

UPDATE system_settings
SET setting_value = 'payos',
    updated_at = now()
WHERE setting_key = 'payment_gateway_provider';

INSERT INTO system_settings (setting_key, setting_value, description)
VALUES (
  'payment_gateway_provider',
  'payos',
  'Phương thức thanh toán mặc định khi checkout'
)
ON CONFLICT (setting_key) DO NOTHING;
