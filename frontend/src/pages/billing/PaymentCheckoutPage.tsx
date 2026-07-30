import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import { getStoredUser } from '../../utils/authStorage';
import type { BankTransferInfo, PlanCatalogItem } from '../../types/billing';
import '../../styles/admin.css';

function formatMoney(value?: number, currency = 'VND') {
  if (value == null) return '—';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value);
}

function formatDate(value?: string | null) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

async function copyText(value: string) {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
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
  const [method, setMethod] = useState<'bank_qr'>('bank_qr');
  const [bankInfo, setBankInfo] = useState<BankTransferInfo | null>(null);
  const [copied, setCopied] = useState('');
  const [now, setNow] = useState(Date.now());

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

  useEffect(() => {
    if (!bankInfo) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [bankInfo]);

  useEffect(() => {
    if (!bankInfo || bankInfo.status !== 'pending') return;
    const poll = window.setInterval(async () => {
      try {
        const status = await billingService.getPaymentStatus(bankInfo.paymentId);
        if (status.status === 'paid') {
          navigate(`/payment/result?paymentId=${bankInfo.paymentId}`);
        }
      } catch {
        // ignore
      }
    }, 5000);
    return () => window.clearInterval(poll);
  }, [bankInfo, navigate]);

  const remainLabel = (() => {
    if (!bankInfo?.expiresAt) return null;
    const ms = new Date(bankInfo.expiresAt).getTime() - now;
    if (ms <= 0) return '00:00';
    const totalSec = Math.floor(ms / 1000);
    const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
    const ss = String(totalSec % 60).padStart(2, '0');
    return `${mm}:${ss}`;
  })();

  async function handleCopy(label: string, value: string) {
    if (await copyText(value)) {
      setCopied(label);
      window.setTimeout(() => setCopied(''), 1500);
    }
  }

  async function handlePay() {
    if (!plan) return;
    if (method !== 'bank_qr') {
      setError('Vui lòng chọn phương thức thanh toán.');
      return;
    }
    setPaying(true);
    setError('');
    try {
      const result = await billingService.checkout(plan.id, 'bank_transfer');
      const detail = result.bankTransfer
        || await billingService.getBankTransfer(result.paymentId);
      setBankInfo(detail);
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
              <p className="muted" style={{ margin: '6px 0 0' }}>Kiểm tra thông tin gói và chọn phương thức thanh toán</p>
            </div>
            <span className={`admin-status-badge ${bankInfo ? 'status-pending' : 'status-unverified'}`}>
              {bankInfo ? 'Chờ thanh toán' : 'Chưa tạo đơn'}
            </span>
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
          <div style={{ textAlign: 'right', marginTop: 14, color: '#5b21b6', fontSize: '1.4rem', fontWeight: 700 }}>
            {formatMoney(plan.price, plan.currency)}
          </div>
        </article>

        {!bankInfo && (
          <article style={{ background: '#fff', borderRadius: 16, padding: 20, boxShadow: '0 8px 28px rgba(15,23,42,0.08)', marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 14, fontSize: '1rem' }}>Phương thức thanh toán</h2>

            <label
              style={{
                display: 'flex',
                gap: 12,
                alignItems: 'flex-start',
                padding: 14,
                borderRadius: 12,
                border: method === 'bank_qr' ? '2px solid #5b21b6' : '1px solid #e5e7eb',
                background: method === 'bank_qr' ? '#f5f3ff' : '#fff',
                cursor: 'pointer',
              }}
            >
              <input
                type="radio"
                name="paymentMethod"
                checked={method === 'bank_qr'}
                onChange={() => setMethod('bank_qr')}
                style={{ marginTop: 4 }}
              />
              <div>
                <strong>Chuyển khoản qua QR</strong>
                <p className="muted" style={{ margin: '4px 0 0' }}>
                  Quét VietQR bằng app ngân hàng. Admin sẽ xác nhận sau khi nhận tiền.
                </p>
              </div>
            </label>

            {error && <p className="error" style={{ marginTop: 12 }}>{error}</p>}

            <button
              type="button"
              style={{ marginTop: 18, width: '100%', padding: '12px 16px', fontSize: '1rem' }}
              disabled={paying}
              onClick={handlePay}
            >
              {paying ? 'Đang tạo yêu cầu...' : 'Thanh toán'}
            </button>
          </article>
        )}

        {bankInfo && (
          <article style={{ background: '#fff', borderRadius: 16, overflow: 'hidden', boxShadow: '0 8px 28px rgba(15,23,42,0.08)' }}>
            <div style={{ background: 'linear-gradient(90deg, #6d28d9, #7c3aed)', color: '#fff', padding: '14px 18px', fontWeight: 700 }}>
              Thanh toán chuyển khoản QR
            </div>

            <div style={{ padding: '12px 18px 0' }}>
              <p className="muted" style={{ margin: 0 }}>
                Mã đơn #{bankInfo.orderCode || bankInfo.paymentId.slice(0, 12).toUpperCase()} · {formatDate(bankInfo.createdAt)}
              </p>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                gap: 20,
                padding: 20,
              }}
            >
              <div style={{ textAlign: 'center' }}>
                {bankInfo.qrUrl ? (
                  <img
                    src={bankInfo.qrUrl}
                    alt="VietQR thanh toán"
                    style={{ width: '100%', maxWidth: 260, borderRadius: 12, border: '1px solid #e5e7eb', background: '#fff' }}
                  />
                ) : (
                  <div className="admin-placeholder-card">Không tạo được QR</div>
                )}
                <p className="muted" style={{ marginTop: 10, fontSize: '0.85rem' }}>Quét QR bằng app ngân hàng</p>
              </div>

              <div style={{ display: 'grid', gap: 12 }}>
                <InfoRow label="Ngân hàng" value={bankInfo.bankName} />
                <InfoRow label="Chủ TK" value={bankInfo.accountName} />
                <InfoRow
                  label="STK"
                  value={bankInfo.accountNumber}
                  onCopy={() => handleCopy('stk', bankInfo.accountNumber)}
                  copied={copied === 'stk'}
                />
                <InfoRow
                  label="Nội dung"
                  value={bankInfo.transferContent}
                  onCopy={() => handleCopy('content', bankInfo.transferContent)}
                  copied={copied === 'content'}
                  emphasize
                />
                <InfoRow label="Số tiền" value={formatMoney(bankInfo.amount, bankInfo.currency)} />
              </div>
            </div>

            {remainLabel && (
              <div style={{ background: '#dbeafe', color: '#1e3a8a', padding: '12px 18px' }}>
                Vui lòng thanh toán trong: <strong>{remainLabel}</strong>
                <span style={{ display: 'block', fontSize: '0.9rem', marginTop: 4 }}>
                  Sau thời gian này đơn sẽ hết hạn. Admin sẽ xác nhận khi nhận được chuyển khoản đúng nội dung.
                </span>
              </div>
            )}
          </article>
        )}
      </section>
    </div>
  );
}

function InfoRow({
  label,
  value,
  onCopy,
  copied,
  emphasize,
}: {
  label: string;
  value: string;
  onCopy?: () => void;
  copied?: boolean;
  emphasize?: boolean;
}) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '90px 1fr auto', gap: 8, alignItems: 'center' }}>
      <span className="muted">{label}</span>
      <strong style={{ color: emphasize ? '#5b21b6' : undefined, wordBreak: 'break-all' }}>{value || '—'}</strong>
      {onCopy && (
        <button type="button" className="outline" style={{ padding: '4px 10px', fontSize: '0.8rem' }} onClick={onCopy}>
          {copied ? 'Đã copy' : 'Copy'}
        </button>
      )}
    </div>
  );
}
