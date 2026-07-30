import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPayment, AdminPlan, AdminRevenueSummary, AdminSubscription } from '../../types/admin';

type BillingTab = 'overview' | 'plans' | 'subscriptions' | 'payments';

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function money(value?: number, currency = 'VND') {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value ?? 0);
}

function statusBadge(status: string) {
  const value = status?.toLowerCase() || '';
  if (['active', 'paid', 'verified'].includes(value)) return { text: statusLabel(value), className: 'status-verified' };
  if (['pending'].includes(value)) return { text: statusLabel(value), className: 'status-pending' };
  if (['cancelled', 'failed', 'refunded', 'inactive', 'expired', 'archived'].includes(value)) {
    return { text: statusLabel(value), className: 'status-rejected' };
  }
  return { text: statusLabel(value), className: 'status-unverified' };
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    active: 'Đang hoạt động',
    inactive: 'Tạm tắt',
    archived: 'Tạm tắt',
    pending: 'Chờ xử lý',
    paid: 'Đã thanh toán',
    failed: 'Thất bại',
    cancelled: 'Đã hủy',
    refunded: 'Hoàn tiền',
    expired: 'Hết hạn',
    job_seeker: 'Ứng viên',
    employer: 'Nhà tuyển dụng',
    all: 'Tất cả',
  };
  return map[status] || status;
}

function formatDate(value?: string) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

export default function AdminBillingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const initialTab: BillingTab =
    tabParam === 'plans' || tabParam === 'subscriptions' || tabParam === 'payments' || tabParam === 'overview'
      ? tabParam
      : 'overview';
  const [tab, setTab] = useState<BillingTab>(initialTab);
  const [plans, setPlans] = useState<AdminPlan[]>([]);
  const [subscriptions, setSubscriptions] = useState<AdminSubscription[]>([]);
  const [payments, setPayments] = useState<AdminPayment[]>([]);
  const [revenue, setRevenue] = useState<AdminRevenueSummary | null>(null);
  const [planFilter, setPlanFilter] = useState('all');
  const [subFilter, setSubFilter] = useState('all');
  const [paymentFilter, setPaymentFilter] = useState('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (tabParam === 'plans' || tabParam === 'subscriptions' || tabParam === 'payments' || tabParam === 'overview') {
      setTab(tabParam);
    }
  }, [tabParam]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [planData, subData, paymentData, revenueData] = await Promise.all([
        adminService.listPlans(planFilter),
        adminService.listSubscriptions(subFilter),
        adminService.listPayments(paymentFilter),
        adminService.getRevenueSummary(),
      ]);
      setPlans(planData);
      setSubscriptions(subData);
      setPayments(paymentData);
      setRevenue(revenueData);
      setError('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [planFilter, subFilter, paymentFilter]);

  useEffect(() => {
    load();
  }, [load]);

  function changeTab(next: BillingTab) {
    setTab(next);
    setSearchParams(next === 'overview' ? {} : { tab: next });
  }

  async function deletePlan(plan: AdminPlan) {
    if (!window.confirm(`Xóa gói "${plan.name}"? Thao tác này không thể hoàn tác.`)) return;
    try {
      await adminService.deletePlan(plan.id);
      setError('');
      setSuccess(`Đã xóa gói "${plan.name}".`);
      await load();
    } catch (err) {
      setSuccess('');
      setError(readError(err));
    }
  }

  async function toggleSubscription(item: AdminSubscription) {
    try {
      if (item.status.toLowerCase() === 'active') {
        const reason = window.prompt('Nhập lý do hủy đăng ký', 'Admin hủy đăng ký') || '';
        await adminService.cancelSubscription(item.id, reason);
        setSuccess('Đã hủy đăng ký.');
      } else {
        await adminService.activateSubscription(item.id);
        setSuccess('Đã kích hoạt đăng ký.');
      }
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  async function confirmPayment(item: AdminPayment) {
    if (!window.confirm(`Xác nhận đã nhận chuyển khoản ${money(item.amount, item.currency)} từ ${item.userEmail}?`)) return;
    try {
      await adminService.confirmPayment(item.id);
      setSuccess('Đã xác nhận thanh toán và kích hoạt gói.');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1>Gói dịch vụ & Thanh toán</h1>
          <p className="muted">Quản lý plans, subscriptions, giao dịch và doanh thu nền tảng.</p>
        </div>
        <button type="button" className="outline" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-filter-tabs">
        {[
          { value: 'overview', label: 'Tổng quan doanh thu' },
          { value: 'plans', label: 'Gói dịch vụ' },
          { value: 'subscriptions', label: 'Đăng ký' },
          { value: 'payments', label: 'Thanh toán' },
        ].map((item) => (
          <button
            key={item.value}
            type="button"
            className={tab === item.value ? 'active' : 'outline'}
            onClick={() => changeTab(item.value as BillingTab)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && revenue && (
        <div className="admin-dashboard-grid">
          <article className="admin-dashboard-card green">
            <span>Doanh thu đã thu</span>
            <strong>{money(revenue.totalPaid)}</strong>
            <p>{revenue.paidCount} giao dịch thành công</p>
          </article>
          <article className="admin-dashboard-card amber">
            <span>Đang chờ thanh toán</span>
            <strong>{money(revenue.totalPending)}</strong>
            <p>{revenue.pendingCount} giao dịch pending</p>
          </article>
          <article className="admin-dashboard-card blue">
            <span>Đăng ký đang active</span>
            <strong>{revenue.activeSubscriptions}</strong>
            <p>{revenue.activePlans} gói đang mở bán</p>
          </article>
          <article className="admin-dashboard-card primary">
            <span>Hoàn tiền</span>
            <strong>{money(revenue.totalRefunded)}</strong>
            <p>{revenue.failedCount} giao dịch thất bại</p>
          </article>
        </div>
      )}

      {tab === 'plans' && (
        <>
          <div className="admin-filter-tabs">
            {['all', 'active', 'inactive'].map((item) => (
              <button
                key={item}
                type="button"
                className={planFilter === item ? 'active' : 'outline'}
                onClick={() => setPlanFilter(item)}
              >
                {statusLabel(item)}
              </button>
            ))}
            <Link className="button-link" to="/admin/billing/plans/new">
              + Tạo gói mới
            </Link>
          </div>
          <section className="admin-company-list-panel">
            <div className="admin-company-list-header">
              <h2>Danh sách gói</h2>
              <span>{plans.length} gói</span>
            </div>
            <div className="admin-review-list">
              {plans.map((plan) => {
                const badge = statusBadge(plan.status);
                return (
                  <article key={plan.id} className="admin-review-row">
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{plan.name}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                      </div>
                      <div className="admin-review-meta">
                        <span>{statusLabel(plan.targetRole)}</span>
                        <span>{money(plan.price, plan.currency)}</span>
                        <span>{plan.durationDays} ngày</span>
                      </div>
                      {plan.description && <p className="muted">{plan.description}</p>}
                    </div>
                    <div className="admin-company-actions">
                      <Link className="button-link outline" to={`/admin/billing/plans/${plan.id}/edit`}>
                        Chỉnh sửa
                      </Link>
                      <button type="button" className="danger" onClick={() => deletePlan(plan)}>
                        Xóa
                      </button>
                    </div>
                  </article>
                );
              })}
              {!loading && plans.length === 0 && <div className="admin-placeholder-card"><p>Chưa có gói dịch vụ.</p></div>}
            </div>
          </section>
        </>
      )}

      {tab === 'subscriptions' && (
        <>
          <div className="admin-filter-tabs">
            {['all', 'active', 'pending', 'cancelled', 'expired'].map((item) => (
              <button
                key={item}
                type="button"
                className={subFilter === item ? 'active' : 'outline'}
                onClick={() => setSubFilter(item)}
              >
                {statusLabel(item)}
              </button>
            ))}
          </div>
          <section className="admin-company-list-panel">
            <div className="admin-company-list-header">
              <h2>Đăng ký gói</h2>
              <span>{subscriptions.length} đăng ký</span>
            </div>
            <div className="admin-review-list">
              {subscriptions.map((item) => {
                const badge = statusBadge(item.status);
                return (
                  <article key={item.id} className="admin-review-row">
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{item.userEmail}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                      </div>
                      <div className="admin-review-meta">
                        <span>{item.planName}</span>
                        <span>{item.userName || '—'}</span>
                        <span>{formatDate(item.startDate)} → {formatDate(item.endDate)}</span>
                      </div>
                      {item.cancelledReason && <p className="muted">Lý do hủy: {item.cancelledReason}</p>}
                    </div>
                    <button type="button" className="outline" onClick={() => toggleSubscription(item)}>
                      {item.status.toLowerCase() === 'active' ? 'Hủy đăng ký' : 'Kích hoạt'}
                    </button>
                  </article>
                );
              })}
              {!loading && subscriptions.length === 0 && <div className="admin-placeholder-card"><p>Chưa có đăng ký nào.</p></div>}
            </div>
          </section>
        </>
      )}

      {tab === 'payments' && (
        <>
          <div className="admin-filter-tabs">
            {['all', 'paid', 'pending', 'failed', 'refunded', 'cancelled'].map((item) => (
              <button
                key={item}
                type="button"
                className={paymentFilter === item ? 'active' : 'outline'}
                onClick={() => setPaymentFilter(item)}
              >
                {statusLabel(item)}
              </button>
            ))}
          </div>

          <section className="admin-company-list-panel">
            <div className="admin-company-list-header">
              <h2>Giao dịch thanh toán</h2>
              <span>{payments.length} giao dịch</span>
            </div>
            <div className="admin-review-list">
              {payments.map((item) => {
                const badge = statusBadge(item.status);
                const needsBankConfirm =
                  item.status?.toLowerCase() === 'pending' && item.paymentMethod === 'bank_transfer';
                return (
                  <article
                    key={item.id}
                    className="admin-review-row"
                    style={needsBankConfirm ? { borderColor: '#f59e0b', background: '#fffbeb' } : undefined}
                  >
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{item.planName || 'Gói dịch vụ'} — {money(item.amount, item.currency)}</strong>
                        <span className={`admin-status-badge ${badge.className}`}>
                          {needsBankConfirm ? 'Chờ xác nhận CK' : badge.text}
                        </span>
                      </div>
                      <div className="admin-review-meta">
                        <span>{item.userEmail}</span>
                        <span>{item.paymentMethod || '—'}</span>
                        {item.transferContent && <span>Nội dung: <strong>{item.transferContent}</strong></span>}
                        {!item.transferContent && item.gateway && <span>{item.gateway}</span>}
                        <span>{formatDate(item.paidAt || item.createdAt)}</span>
                      </div>
                      {needsBankConfirm && (
                        <p className="muted">Đối chiếu sao kê ngân hàng với đúng nội dung CK rồi bấm xác nhận.</p>
                      )}
                      {item.failureReason && <p className="muted">{item.failureReason}</p>}
                    </div>
                    {needsBankConfirm && (
                      <button type="button" onClick={() => confirmPayment(item)}>
                        Xác nhận đã nhận tiền
                      </button>
                    )}
                  </article>
                );
              })}
              {!loading && payments.length === 0 && <div className="admin-placeholder-card"><p>Chưa có giao dịch thanh toán.</p></div>}
            </div>
          </section>
        </>
      )}
    </section>
  );
}
