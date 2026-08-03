import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import { getStoredUser } from '../../utils/authStorage';
import type { PlanCatalogItem } from '../../types/billing';
import '../../styles/admin.css';

function formatMoney(value?: number, currency = 'VND') {
  if (value == null) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

export default function PaymentCheckoutPage() {
  const [params] = useSearchParams();
  const planId = params.get('planId');
  const navigate = useNavigate();
  const user = getStoredUser();
  const backTo = user?.role === 'EMPLOYER' ? '/employer/subscription/plans' : '/candidate/subscription/plans';

  const [plan, setPlan] = useState<PlanCatalogItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState('');

  const loadPlan = useCallback(async () => {
    if (!planId) {
      setError('Thiếu mã gói dịch vụ.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const plans = await billingService.listPlans();
      const found = plans.find((item) => item.id === planId) || null;
      if (!found) {
        setError('Không tìm thấy gói hoặc gói không còn bán.');
        setPlan(null);
      } else {
        setPlan(found);
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [planId]);

  useEffect(() => {
    loadPlan();
  }, [loadPlan]);

  async function handlePay() {
    if (!plan) return;
    setPaying(true);
    setError('');
    try {
      const result = await billingService.checkout(plan.id, 'payos');
      if (result.status === 'paid') {
        navigate(`/payment/result?paymentId=${result.paymentId}`);
        return;
      }
      if (result.payUrl) {
        window.location.href = result.payUrl;
        return;
      }
      setError(result.message || 'Không nhận được đường dẫn thanh toán PayOS.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setPaying(false);
    }
  }

  if (loading) {
    return <p className="loading" style={{ padding: 40 }}>Đang tải trang thanh toán...</p>;
  }

  if (!plan) {
    return (
      <section className="admin-page" style={{ maxWidth: 720, margin: '40px auto' }}>
        <p className="error">{error || 'Không tìm thấy gói'}</p>
        <Link to={backTo}>Quay lại danh sách gói</Link>
      </section>
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: 'linear-gradient(180deg, #f4f7ff 0%, #eef2f8 100%)', padding: '28px 16px 48px' }}>
      <section style={{ maxWidth: 760, margin: '0 auto' }}>
        <Link to={backTo} className="button-link outline" style={{ marginBottom: 16, display: 'inline-block' }}>
          ← Quay lại danh sách gói
        </Link>

        <article style={{ background: '#fff', borderRadius: 16, padding: 20, boxShadow: '0 8px 28px rgba(15,23,42,0.08)', marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
            <div>
              <h1 style={{ margin: 0, fontSize: '1.2rem' }}>Thanh toán gói dịch vụ</h1>
              <p className="muted" style={{ margin: '6px 0 0' }}>Thanh toán tự động qua PayOS — gói kích hoạt ngay khi thành công</p>
            </div>
            <span className="admin-status-badge status-unverified">Chưa tạo đơn</span>
          </div>

          <h2 style={{ marginTop: 20, marginBottom: 10, fontSize: '1rem' }}>Chi tiết sản phẩm</h2>
          <div style={{ background: '#f3f4f6', borderRadius: 12, padding: 14 }}>
            <strong>{plan.name}</strong>
            {plan.description && <p className="muted" style={{ margin: '6px 0 0' }}>{plan.description}</p>}
            <p className="muted" style={{ margin: '6px 0 0' }}>Thời hạn: {plan.durationDays} ngày · Số lượng: 1</p>
            {plan.benefits.length > 0 && (
              <ul style={{ margin: '10px 0 0', paddingLeft: 18 }}>
                {plan.benefits.map((b) => <li key={b}>{b}</li>)}
              </ul>
            )}
          </div>
          <div style={{ textAlign: 'right', marginTop: 14, color: '#00507d', fontSize: '1.4rem', fontWeight: 700 }}>
            {formatMoney(plan.price, plan.currency)}
          </div>
        </article>

        <article style={{ background: '#fff', borderRadius: 16, padding: 20, boxShadow: '0 8px 28px rgba(15,23,42,0.08)' }}>
          <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: '1rem' }}>Phương thức thanh toán</h2>
          <div
            style={{
              padding: 14,
              borderRadius: 12,
              border: '2px solid #00507d',
              background: '#f0f7fc',
            }}
          >
            <strong>PayOS</strong>
            <p className="muted" style={{ margin: '6px 0 0' }}>
              Chuyển đến trang PayOS để quét VietQR / chuyển khoản. Hệ thống tự kích hoạt gói sau khi thanh toán thành công.
            </p>
          </div>

          {error && <p className="error" style={{ marginTop: 12 }}>{error}</p>}

          <button
            type="button"
            style={{ marginTop: 18, width: '100%', padding: '12px 16px', fontSize: '1rem' }}
            disabled={paying}
            onClick={handlePay}
          >
            {paying ? 'Đang chuyển đến PayOS...' : 'Thanh toán bằng PayOS'}
          </button>
        </article>
      </section>
    </div>
  );
}
