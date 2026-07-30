import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { billingService } from '../../services/billingService';
import { getStoredUser } from '../../utils/authStorage';
import type { BankTransferInfo } from '../../types/billing';
import '../../styles/admin.css';

function formatMoney(value?: number, currency = 'VND') {
  if (value == null) return '—';
  return new Intl.NumberFormat('vi-VN').format(value) + ' ' + currency;
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

export default function BankTransferCheckoutPage() {
  const { paymentId } = useParams();
  const navigate = useNavigate();
  const user = getStoredUser();
  const backTo = user?.role === 'EMPLOYER' ? '/employer/subscription' : '/candidate/subscription';

  const [info, setInfo] = useState<BankTransferInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState('');
  const [now, setNow] = useState(Date.now());

  const load = useCallback(async () => {
    if (!paymentId) return;
    setLoading(true);
    setError('');
    try {
      const data = await billingService.getBankTransfer(paymentId);
      setInfo(data);
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [paymentId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!info || info.status !== 'pending') return;
    const poll = window.setInterval(async () => {
      try {
        const status = await billingService.getPaymentStatus(info.paymentId);
        if (status.status === 'paid') {
          navigate(`/payment/result?paymentId=${info.paymentId}`);
        }
      } catch {
        // ignore poll errors
      }
    }, 5000);
    return () => window.clearInterval(poll);
  }, [info, navigate]);

  const remainLabel = useMemo(() => {
    if (!info?.expiresAt) return null;
    const ms = new Date(info.expiresAt).getTime() - now;
    if (ms <= 0) return '00:00';
    const totalSec = Math.floor(ms / 1000);
    const mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
    const ss = String(totalSec % 60).padStart(2, '0');
    return `${mm}:${ss}`;
  }, [info, now]);

  async function handleCopy(label: string, value: string) {
    const ok = await copyText(value);
    if (ok) {
      setCopied(label);
      window.setTimeout(() => setCopied(''), 1500);
    }
  }

  if (loading) {
    return <p className="loading" style={{ padding: 40 }}>Đang tải thông tin thanh toán...</p>;
  }

  if (error || !info) {
    return (
      <section className="admin-page" style={{ maxWidth: 720, margin: '40px auto' }}>
        <p className="error">{error || 'Không tìm thấy đơn thanh toán'}</p>
        <Link to={backTo}>Quay lại gói dịch vụ</Link>
      </section>
    );
  }

  const isPending = info.status === 'pending';
  const isPaid = info.status === 'paid';

  return (
    <div style={{ minHeight: '100vh', background: 'linear-gradient(180deg, #f4f7ff 0%, #eef2f8 100%)', padding: '28px 16px 48px' }}>
      <section style={{ maxWidth: 720, margin: '0 auto' }}>
        <Link to={backTo} className="button-link outline" style={{ marginBottom: 16, display: 'inline-block' }}>
          ← Quay lại
        </Link>

        <article style={{ background: '#fff', borderRadius: 16, padding: 20, boxShadow: '0 8px 28px rgba(15,23,42,0.08)', marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
            <div>
              <h1 style={{ margin: 0, fontSize: '1.15rem' }}>Mã đơn #{info.orderCode || info.paymentId.slice(0, 12).toUpperCase()}</h1>
              <p className="muted" style={{ margin: '6px 0 0' }}>{formatDate(info.createdAt)}</p>
            </div>
            <span className={`admin-status-badge ${isPaid ? 'status-verified' : isPending ? 'status-pending' : 'status-rejected'}`}>
              {isPaid ? 'Đã thanh toán' : isPending ? 'Chờ xử lý' : info.status === 'expired' ? 'Hết hạn' : info.status}
            </span>
          </div>

          <h2 style={{ marginTop: 20, marginBottom: 10, fontSize: '1rem' }}>Chi tiết sản phẩm</h2>
          <div style={{ background: '#f3f4f6', borderRadius: 12, padding: 14 }}>
            <strong>{info.planName || 'Gói dịch vụ'}</strong>
            <p className="muted" style={{ margin: '6px 0 0' }}>Số lượng: 1</p>
          </div>
          <div style={{ textAlign: 'right', marginTop: 14, color: '#5b21b6', fontSize: '1.4rem', fontWeight: 700 }}>
            {formatMoney(info.amount, info.currency)}
          </div>
        </article>

        <article style={{ background: '#fff', borderRadius: 16, overflow: 'hidden', boxShadow: '0 8px 28px rgba(15,23,42,0.08)' }}>
          <div style={{ background: 'linear-gradient(90deg, #6d28d9, #7c3aed)', color: '#fff', padding: '14px 18px', fontWeight: 700 }}>
            Thanh toán tự động
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
              {info.qrUrl ? (
                <img
                  src={info.qrUrl}
                  alt="VietQR thanh toán"
                  style={{ width: '100%', maxWidth: 260, borderRadius: 12, border: '1px solid #e5e7eb', background: '#fff' }}
                />
              ) : (
                <div className="admin-placeholder-card">Không tạo được QR</div>
              )}
              <p className="muted" style={{ marginTop: 10, fontSize: '0.85rem' }}>Quét QR bằng app ngân hàng</p>
            </div>

            <div style={{ display: 'grid', gap: 12 }}>
              <InfoRow label="Ngân hàng" value={info.bankName} />
              <InfoRow label="Chủ TK" value={info.accountName} />
              <InfoRow
                label="STK"
                value={info.accountNumber}
                onCopy={() => handleCopy('stk', info.accountNumber)}
                copied={copied === 'stk'}
              />
              <InfoRow
                label="Nội dung"
                value={info.transferContent}
                onCopy={() => handleCopy('content', info.transferContent)}
                copied={copied === 'content'}
                emphasize
              />
              <InfoRow label="Số tiền" value={formatMoney(info.amount, info.currency)} />
            </div>
          </div>

          {isPending && remainLabel && (
            <div style={{ background: '#dbeafe', color: '#1e3a8a', padding: '12px 18px', display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
              <span>Vui lòng thanh toán trong: <strong>{remainLabel}</strong></span>
              <span style={{ fontSize: '0.9rem' }}>Sau thời gian này đơn sẽ hết hạn và không thể xác nhận.</span>
            </div>
          )}

          {!isPending && (
            <div style={{ padding: 16 }}>
              <p className="muted" style={{ margin: 0 }}>
                {isPaid
                  ? 'Thanh toán đã được xác nhận. Gói của bạn đã được kích hoạt.'
                  : 'Đơn thanh toán đã hết hạn hoặc thất bại. Vui lòng tạo đơn mới.'}
              </p>
            </div>
          )}
        </article>

        <p className="muted" style={{ marginTop: 16, textAlign: 'center' }}>
          Sau khi chuyển khoản, vui lòng chờ admin xác nhận (thường trong vài phút đến vài giờ).
        </p>
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
