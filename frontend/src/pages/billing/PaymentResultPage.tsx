import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import { getStoredUser } from '../../utils/authStorage';
import type { PaymentStatus } from '../../types/billing';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function formatMoney(value?: number, currency = 'VND') {
  if (value == null) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
}

export default function PaymentResultPage() {
  const [params] = useSearchParams();
  const paymentId = params.get('paymentId');
  const [status, setStatus] = useState<PaymentStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const user = getStoredUser();
  const backTo = user?.role === 'EMPLOYER' ? '/employer/subscription' : '/candidate/subscription';

  useEffect(() => {
    if (!paymentId) {
      setError('Thiếu mã giao dịch.');
      setLoading(false);
      return;
    }

    let attempts = 0;
    const maxAttempts = 8;

    async function poll() {
      if (!paymentId) return;
      try {
        const data = await billingService.getPaymentStatus(paymentId);
        setStatus(data);
        if (data.status === 'pending' && attempts < maxAttempts) {
          attempts += 1;
          window.setTimeout(poll, 2000);
        }
      } catch (err) {
        setError(readError(err));
      } finally {
        setLoading(false);
      }
    }

    poll();
  }, [paymentId]);

  const isPaid = status?.status === 'paid';
  const isFailed = status?.status === 'failed' || status?.status === 'cancelled';

  return (
    <div className="admin-auth-shell">
      <section className="admin-auth-panel" style={{ maxWidth: 520 }}>
        <p className="admin-auth-eyebrow">Smart Recruitment Portal</p>
        <h1>{loading ? 'Đang xác nhận thanh toán...' : isPaid ? 'Thanh toán thành công' : isFailed ? 'Thanh toán thất bại' : 'Đang chờ xác nhận'}</h1>

        {error && <p className="error">{error}</p>}

        {isPaid && (
          <p className="muted">Gói đã được kích hoạt. Bạn có thể xem chi tiết gói đang dùng tại trang gói dịch vụ.</p>
        )}
        {!loading && !isPaid && !isFailed && (
          <p className="muted">Đơn đang chờ admin xác nhận chuyển khoản. Sau khi xác nhận, gói sẽ hiện ở trang gói dịch vụ.</p>
        )}

        {status && (
          <div className="admin-profile-card">
            <div><span>Gói</span><strong>{status.planName || '—'}</strong></div>
            <div><span>Số tiền</span><strong>{formatMoney(status.amount, status.currency)}</strong></div>
            <div><span>Thanh toán</span><strong>{isPaid ? 'Đã thanh toán' : isFailed ? 'Thất bại' : 'Chờ xử lý'}</strong></div>
            <div><span>Đăng ký</span><strong>{status.subscriptionStatus === 'active' ? 'Đang sử dụng' : (status.subscriptionStatus || '—')}</strong></div>
            {status.failureReason && <div><span>Lý do</span><strong>{status.failureReason}</strong></div>}
          </div>
        )}

        <p className="admin-auth-footer" style={{ marginTop: 20 }}>
          <Link to={backTo}>{isPaid ? 'Xem gói đang dùng' : 'Quay lại trang gói dịch vụ'}</Link>
        </p>
      </section>
    </div>
  );
}
