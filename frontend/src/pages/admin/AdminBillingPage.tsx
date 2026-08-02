import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminService } from '../../services/adminService';
import type { AdminPayment, AdminPlan, AdminRevenueSummary, AdminSubscription } from '../../types/admin';

type BillingTab = 'overview' | 'plans' | 'transactions';
type TxFilter = 'payments' | 'pending' | 'paid' | 'expired' | 'cancelled';

const PAGE_SIZE = 8;

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function matchesQuery(haystack: Array<string | number | undefined | null>, query: string) {
  if (!query) return true;
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return haystack.some((value) => String(value ?? '').toLowerCase().includes(q));
}

function paymentMatches(item: AdminPayment, query: string) {
  return matchesQuery(
    [
      item.userEmail,
      item.planName,
      item.transferContent,
      item.transactionId,
      item.paymentMethod,
      methodLabel(item.paymentMethod),
      item.status,
      statusLabel(item.status),
      item.amount,
      item.gateway,
    ],
    query,
  );
}

function subscriptionMatches(item: AdminSubscription, query: string) {
  return matchesQuery(
    [item.userEmail, item.userName, item.planName, item.status, statusLabel(item.status), item.cancelledReason],
    query,
  );
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
    active: 'Đang dùng',
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

function resolveTab(tabParam: string | null): BillingTab {
  if (tabParam === 'plans' || tabParam === 'overview' || tabParam === 'transactions') return tabParam;
  if (tabParam === 'subscriptions' || tabParam === 'payments') return 'transactions';
  return 'overview';
}

function methodLabel(method?: string) {
  const map: Record<string, string> = {
    bank_transfer: 'Chuyển khoản QR',
    momo: 'MoMo',
    vnpay: 'VNPay',
  };
  return method ? (map[method] || method) : '—';
}

export default function AdminBillingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const [tab, setTab] = useState<BillingTab>(() => resolveTab(tabParam));
  const [plans, setPlans] = useState<AdminPlan[]>([]);
  const [subscriptions, setSubscriptions] = useState<AdminSubscription[]>([]);
  const [payments, setPayments] = useState<AdminPayment[]>([]);
  const [revenue, setRevenue] = useState<AdminRevenueSummary | null>(null);
  const [planFilter, setPlanFilter] = useState('all');
  const [txFilter, setTxFilter] = useState<TxFilter>('pending');
  const [txSearch, setTxSearch] = useState('');
  const [txPage, setTxPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    setTab(resolveTab(tabParam));
  }, [tabParam]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [planData, subData, paymentData, revenueData] = await Promise.all([
        adminService.listPlans(planFilter),
        adminService.listSubscriptions('all'),
        adminService.listPayments('all'),
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
  }, [planFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const pendingPayments = useMemo(
    () => payments.filter((p) => p.status?.toLowerCase() === 'pending'),
    [payments],
  );

  const paidActiveSubs = useMemo(
    () => subscriptions.filter((s) => s.status?.toLowerCase() === 'active'),
    [subscriptions],
  );

  const expiredSubs = useMemo(
    () => subscriptions.filter((s) => s.status?.toLowerCase() === 'expired'),
    [subscriptions],
  );

  const cancelledSubs = useMemo(
    () => subscriptions.filter((s) => s.status?.toLowerCase() === 'cancelled'),
    [subscriptions],
  );

  const paymentBySubId = useMemo(() => {
    const map = new Map<string, AdminPayment>();
    payments
      .filter((p) => p.subscriptionId && p.status?.toLowerCase() === 'paid')
      .forEach((p) => {
        if (p.subscriptionId && !map.has(p.subscriptionId)) {
          map.set(p.subscriptionId, p);
        }
      });
    return map;
  }, [payments]);

  /** Payment gắn subscription (ưu tiên có ND CK) — dùng khi xem đăng ký đã hủy/hết hạn. */
  const anyPaymentBySubId = useMemo(() => {
    const map = new Map<string, AdminPayment>();
    const ranked = [...payments].sort((a, b) => {
      const score = (p: AdminPayment) => {
        let s = 0;
        if (p.transferContent) s += 4;
        const st = p.status?.toLowerCase() || '';
        if (st === 'paid') s += 3;
        else if (st === 'cancelled' || st === 'failed') s += 2;
        else if (st === 'pending') s += 1;
        return s;
      };
      return score(b) - score(a);
    });
    ranked.forEach((p) => {
      if (p.subscriptionId && !map.has(p.subscriptionId)) {
        map.set(p.subscriptionId, p);
      }
    });
    return map;
  }, [payments]);

  /** Gói hủy do chưa/không thanh toán — không cho khôi phục. Chỉ khôi phục khi đã từng thanh toán (admin hủy). */
  function canRestoreCancelled(item: AdminSubscription) {
    if (paymentBySubId.has(item.id)) return true;
    const reason = (item.cancelledReason || '').toLowerCase();
    if (
      /hết hạn thanh toán|thay thế bởi đơn|thất bại|chưa thanh toán|không thanh toán/.test(reason)
    ) {
      return false;
    }
    // Chưa kích hoạt (pending → cancelled) coi như chưa thanh toán
    return Boolean(item.startDate);
  }

  const counts = {
    payments: payments.length,
    pending: pendingPayments.length,
    paid: paidActiveSubs.length,
    expired: expiredSubs.length,
    cancelled: cancelledSubs.length,
  };

  const filteredPayments = useMemo(
    () => payments.filter((item) => paymentMatches(item, txSearch)),
    [payments, txSearch],
  );

  const filteredPending = useMemo(
    () => pendingPayments.filter((item) => paymentMatches(item, txSearch)),
    [pendingPayments, txSearch],
  );

  const filteredPaid = useMemo(
    () => paidActiveSubs.filter((item) => {
      if (subscriptionMatches(item, txSearch)) return true;
      const payment = anyPaymentBySubId.get(item.id);
      return payment ? paymentMatches(payment, txSearch) : false;
    }),
    [paidActiveSubs, txSearch, anyPaymentBySubId],
  );

  const filteredExpired = useMemo(
    () => expiredSubs.filter((item) => {
      if (subscriptionMatches(item, txSearch)) return true;
      const payment = anyPaymentBySubId.get(item.id);
      return payment ? paymentMatches(payment, txSearch) : false;
    }),
    [expiredSubs, txSearch, anyPaymentBySubId],
  );

  const filteredCancelled = useMemo(
    () => cancelledSubs.filter((item) => {
      if (subscriptionMatches(item, txSearch)) return true;
      const payment = anyPaymentBySubId.get(item.id);
      return payment ? paymentMatches(payment, txSearch) : false;
    }),
    [cancelledSubs, txSearch, anyPaymentBySubId],
  );

  const filteredCountByTab: Record<TxFilter, number> = {
    payments: filteredPayments.length,
    pending: filteredPending.length,
    paid: filteredPaid.length,
    expired: filteredExpired.length,
    cancelled: filteredCancelled.length,
  };

  const filteredTotal = filteredCountByTab[txFilter];
  const txTotalPages = Math.max(1, Math.ceil(filteredTotal / PAGE_SIZE));
  const txPageStart = (txPage - 1) * PAGE_SIZE;

  const pagedPayments = filteredPayments.slice(txPageStart, txPageStart + PAGE_SIZE);
  const pagedPending = filteredPending.slice(txPageStart, txPageStart + PAGE_SIZE);
  const pagedPaid = filteredPaid.slice(txPageStart, txPageStart + PAGE_SIZE);
  const pagedExpired = filteredExpired.slice(txPageStart, txPageStart + PAGE_SIZE);
  const pagedCancelled = filteredCancelled.slice(txPageStart, txPageStart + PAGE_SIZE);

  useEffect(() => {
    if (txPage > txTotalPages) setTxPage(txTotalPages);
  }, [txPage, txTotalPages]);

  function changeTxFilter(next: TxFilter) {
    setTxFilter(next);
    setTxPage(1);
  }

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

  async function cancelSubscription(item: AdminSubscription) {
    const reason = window.prompt(
      `Hủy gói "${item.planName}" của ${item.userEmail}?\nNhập lý do:`,
      'Admin hủy đăng ký',
    );
    if (reason == null) return;
    try {
      await adminService.cancelSubscription(item.id, reason.trim() || 'Admin hủy đăng ký');
      setSuccess('Đã hủy gói. Có thể khôi phục ở tab Đã hủy.');
      setTxFilter('cancelled');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  async function restoreSubscription(item: AdminSubscription) {
    if (!canRestoreCancelled(item)) {
      setError('Không thể khôi phục gói đã hủy do chưa thanh toán.');
      return;
    }
    if (!window.confirm(`Khôi phục gói "${item.planName}" cho ${item.userEmail}?`)) return;
    try {
      await adminService.activateSubscription(item.id);
      setSuccess('Đã khôi phục gói cho người dùng.');
      setTxFilter('paid');
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
      setTxFilter('paid');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <section className="admin-page">
      <header className="admin-page-intro">
        <div className="admin-page-intro-copy">
          <p className="admin-page-intro-eyebrow">Doanh thu & gói dịch vụ</p>
          <h1>Gói dịch vụ & Thanh toán</h1>
          <p>Quản lý gói, giao dịch và đăng ký của người dùng trên nền tảng.</p>
        </div>
        <div className="admin-page-intro-aside">
          <div className="admin-page-intro-stat">
            <span>Tab hiện tại</span>
            <strong>
              {tab === 'overview' ? 'Tổng quan' : tab === 'plans' ? 'Gói dịch vụ' : 'Đăng ký & TT'}
            </strong>
          </div>
          <div className="admin-page-intro-stat">
            <span>Gói đang mở</span>
            <strong>{revenue?.activePlans ?? plans.filter((p) => p.status === 'active').length}</strong>
          </div>
        </div>
      </header>

      {error && <p className="error admin-inline-message">{error}</p>}
      {success && <p className="success admin-inline-message">{success}</p>}

      <div className="admin-toolbar">
        <div className="admin-toolbar-group" role="tablist" aria-label="Tab billing">
          {[
            { value: 'overview', label: 'Tổng quan doanh thu' },
            { value: 'plans', label: 'Gói dịch vụ' },
            { value: 'transactions', label: 'Đăng ký & Thanh toán' },
          ].map((item) => (
            <button
              key={item.value}
              type="button"
              role="tab"
              aria-selected={tab === item.value}
              className={tab === item.value ? 'active' : 'outline'}
              onClick={() => changeTab(item.value as BillingTab)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <button type="button" className="outline admin-toolbar-refresh" onClick={load} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
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
          <div className="admin-toolbar">
            <div className="admin-toolbar-group" role="tablist" aria-label="Lọc gói">
              {['all', 'active', 'inactive'].map((item) => (
                <button
                  key={item}
                  type="button"
                  role="tab"
                  aria-selected={planFilter === item}
                  className={planFilter === item ? 'active' : 'outline'}
                  onClick={() => setPlanFilter(item)}
                >
                  {statusLabel(item)}
                </button>
              ))}
            </div>
            <Link className="button-link admin-toolbar-refresh" to="/admin/billing/plans/new">
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

      {tab === 'transactions' && (
        <>
          <div className="admin-toolbar">
            <div className="admin-toolbar-group" role="tablist" aria-label="Lọc giao dịch">
              {[
                { value: 'payments', label: `Giao dịch (${counts.payments})` },
                { value: 'pending', label: `Chờ xử lý (${counts.pending})` },
                { value: 'paid', label: `Đã thanh toán (${counts.paid})` },
                { value: 'expired', label: `Hết hạn (${counts.expired})` },
                { value: 'cancelled', label: `Đã hủy (${counts.cancelled})` },
              ].map((item) => (
                <button
                  key={item.value}
                  type="button"
                  role="tab"
                  aria-selected={txFilter === item.value}
                  className={txFilter === item.value ? 'active' : 'outline'}
                  onClick={() => changeTxFilter(item.value as TxFilter)}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <div className="admin-company-list-header" style={{ marginBottom: 12, gap: 12, flexWrap: 'wrap' }}>
            <input
              type="search"
              className="admin-search-input"
              value={txSearch}
              onChange={(e) => {
                setTxSearch(e.target.value);
                setTxPage(1);
              }}
              placeholder="Tìm theo email, tên, gói, nội dung CK, mã giao dịch..."
              style={{ flex: 1, minWidth: 240, maxWidth: 480 }}
              aria-label="Tìm kiếm đăng ký và thanh toán"
            />
            <span className="muted">
              {txSearch.trim()
                ? `${filteredTotal}/${counts[txFilter]} kết quả`
                : `${counts[txFilter]} mục`}
            </span>
          </div>

          {txFilter === 'payments' && (
            <section className="admin-company-list-panel">
              <div className="admin-company-list-header">
                <div>
                  <h2>Tất cả giao dịch</h2>
                  <p className="muted" style={{ margin: '4px 0 0' }}>Lịch sử thanh toán trên hệ thống</p>
                </div>
                <span>{filteredPayments.length} giao dịch</span>
              </div>
              <div className="admin-review-list">
                {pagedPayments.map((item) => {
                  const badge = statusBadge(item.status);
                  return (
                    <article key={item.id} className="admin-review-row">
                      <div className="admin-review-main">
                        <div className="admin-review-title-line">
                          <strong>{item.planName || 'Gói dịch vụ'} — {money(item.amount, item.currency)}</strong>
                          <span className={`admin-status-badge ${badge.className}`}>{badge.text}</span>
                        </div>
                        <div className="admin-review-meta">
                          <span>{item.userEmail}</span>
                          <span>{methodLabel(item.paymentMethod)}</span>
                          {item.transferContent && (
                            <span>ND CK: <strong>{item.transferContent}</strong></span>
                          )}
                          <span>{formatDate(item.paidAt || item.createdAt)}</span>
                        </div>
                      </div>
                    </article>
                  );
                })}
                {!loading && filteredPayments.length === 0 && (
                  <div className="admin-placeholder-card">
                    <p>{txSearch.trim() ? 'Không tìm thấy giao dịch phù hợp.' : 'Chưa có giao dịch.'}</p>
                  </div>
                )}
              </div>
              {filteredPayments.length > PAGE_SIZE && (
                <div className="admin-pagination">
                  <button type="button" className="outline" disabled={txPage === 1} onClick={() => setTxPage((p) => Math.max(1, p - 1))}>
                    Trước
                  </button>
                  <span>
                    Trang {txPage}/{txTotalPages}
                  </span>
                  <button type="button" className="outline" disabled={txPage === txTotalPages} onClick={() => setTxPage((p) => Math.min(txTotalPages, p + 1))}>
                    Sau
                  </button>
                </div>
              )}
            </section>
          )}

          {txFilter === 'pending' && (
            <section className="admin-company-list-panel">
              <div className="admin-company-list-header">
                <div>
                  <h2>Chờ xử lý</h2>
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    Xác nhận chuyển khoản. Bill hết hạn QR vẫn giữ tại đây 1 ngày; bill bị thay thế bởi đơn mới sẽ bị loại.
                  </p>
                </div>
                <span>{filteredPending.length} giao dịch</span>
              </div>
              <div className="admin-review-list">
                {pagedPending.map((item) => {
                  const isBank = item.paymentMethod === 'bank_transfer';
                  const qrExpired = Boolean(
                    item.expiresAt && new Date(item.expiresAt).getTime() < Date.now(),
                  );
                  return (
                    <article
                      key={item.id}
                      className="admin-review-row"
                      style={isBank ? { borderColor: '#f59e0b', background: '#fffbeb' } : undefined}
                    >
                      <div className="admin-review-main">
                        <div className="admin-review-title-line">
                          <strong>{item.planName || 'Gói dịch vụ'} — {money(item.amount, item.currency)}</strong>
                          <span className={`admin-status-badge ${qrExpired ? 'status-rejected' : 'status-pending'}`}>
                            {isBank
                              ? qrExpired
                                ? 'Hết hạn QR — còn xác nhận trong 24h'
                                : 'Chờ xác nhận CK'
                              : 'Chờ xử lý'}
                          </span>
                        </div>
                        <div className="admin-review-meta">
                          <span>{item.userEmail}</span>
                          <span>{methodLabel(item.paymentMethod)}</span>
                          {item.transferContent && <span>ND CK: <strong>{item.transferContent}</strong></span>}
                          <span>{formatDate(item.createdAt)}</span>
                        </div>
                        {isBank && (
                          <p className="muted">
                            {qrExpired
                              ? 'QR đã hết hạn phía user nhưng vẫn có thể xác nhận trong 1 ngày nếu đã nhận tiền. Bill bị thay thế bởi đơn mới sẽ không còn ở đây.'
                              : 'Đối chiếu sao kê với đúng nội dung CK rồi xác nhận.'}
                          </p>
                        )}
                      </div>
                      {isBank && (
                        <button type="button" onClick={() => confirmPayment(item)}>
                          Xác nhận đã nhận tiền
                        </button>
                      )}
                    </article>
                  );
                })}
                {!loading && filteredPending.length === 0 && (
                  <div className="admin-placeholder-card">
                    <p>{txSearch.trim() ? 'Không tìm thấy giao dịch phù hợp.' : 'Không có giao dịch chờ xử lý.'}</p>
                  </div>
                )}
              </div>
              {filteredPending.length > PAGE_SIZE && (
                <div className="admin-pagination">
                  <button type="button" className="outline" disabled={txPage === 1} onClick={() => setTxPage((p) => Math.max(1, p - 1))}>
                    Trước
                  </button>
                  <span>
                    Trang {txPage}/{txTotalPages}
                  </span>
                  <button type="button" className="outline" disabled={txPage === txTotalPages} onClick={() => setTxPage((p) => Math.min(txTotalPages, p + 1))}>
                    Sau
                  </button>
                </div>
              )}
            </section>
          )}

          {txFilter === 'paid' && (
            <section className="admin-company-list-panel">
              <div className="admin-company-list-header">
                <div>
                  <h2>Đã thanh toán — gói đang dùng</h2>
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    User đang dùng gói nào. Có thể hủy gói (chuyển sang Đã hủy).
                  </p>
                </div>
                <span>{filteredPaid.length} đăng ký</span>
              </div>
              <div className="admin-review-list">
                {pagedPaid.map((item) => {
                  const payment = paymentBySubId.get(item.id) || anyPaymentBySubId.get(item.id);
                  return (
                    <article key={item.id} className="admin-review-row">
                      <div className="admin-review-main">
                        <div className="admin-review-title-line">
                          <strong>{item.userEmail}</strong>
                          <span className="admin-status-badge status-verified">Đang dùng</span>
                        </div>
                        <div className="admin-review-meta">
                          <span>Gói: <strong>{item.planName}</strong></span>
                          <span>{item.userName || '—'}</span>
                          {payment && <span>{money(payment.amount, payment.currency)}</span>}
                          {payment?.transferContent && (
                            <span>ND CK: <strong>{payment.transferContent}</strong></span>
                          )}
                          <span>{formatDate(item.startDate)} → {formatDate(item.endDate)}</span>
                        </div>
                      </div>
                      <button type="button" className="danger" onClick={() => cancelSubscription(item)}>
                        Hủy gói
                      </button>
                    </article>
                  );
                })}
                {!loading && filteredPaid.length === 0 && (
                  <div className="admin-placeholder-card">
                    <p>{txSearch.trim() ? 'Không tìm thấy đăng ký phù hợp.' : 'Chưa có user nào đang dùng gói.'}</p>
                  </div>
                )}
              </div>
              {filteredPaid.length > PAGE_SIZE && (
                <div className="admin-pagination">
                  <button type="button" className="outline" disabled={txPage === 1} onClick={() => setTxPage((p) => Math.max(1, p - 1))}>
                    Trước
                  </button>
                  <span>
                    Trang {txPage}/{txTotalPages}
                  </span>
                  <button type="button" className="outline" disabled={txPage === txTotalPages} onClick={() => setTxPage((p) => Math.min(txTotalPages, p + 1))}>
                    Sau
                  </button>
                </div>
              )}
            </section>
          )}

          {txFilter === 'expired' && (
            <section className="admin-company-list-panel">
              <div className="admin-company-list-header">
                <div>
                  <h2>Hết hạn</h2>
                  <p className="muted" style={{ margin: '4px 0 0' }}>Các tài khoản đã hết hạn gói</p>
                </div>
                <span>{filteredExpired.length} đăng ký</span>
              </div>
              <div className="admin-review-list">
                {pagedExpired.map((item) => {
                  const payment = anyPaymentBySubId.get(item.id);
                  return (
                  <article key={item.id} className="admin-review-row">
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{item.userEmail}</strong>
                        <span className="admin-status-badge status-rejected">Hết hạn</span>
                      </div>
                      <div className="admin-review-meta">
                        <span>Gói: <strong>{item.planName}</strong></span>
                        <span>{item.userName || '—'}</span>
                        {payment?.transferContent && (
                          <span>ND CK: <strong>{payment.transferContent}</strong></span>
                        )}
                        <span>{formatDate(item.startDate)} → {formatDate(item.endDate)}</span>
                      </div>
                    </div>
                    <button type="button" className="outline" onClick={() => restoreSubscription(item)}>
                      Gia hạn / Khôi phục
                    </button>
                  </article>
                  );
                })}
                {!loading && filteredExpired.length === 0 && (
                  <div className="admin-placeholder-card">
                    <p>{txSearch.trim() ? 'Không tìm thấy đăng ký phù hợp.' : 'Không có gói hết hạn.'}</p>
                  </div>
                )}
              </div>
              {filteredExpired.length > PAGE_SIZE && (
                <div className="admin-pagination">
                  <button type="button" className="outline" disabled={txPage === 1} onClick={() => setTxPage((p) => Math.max(1, p - 1))}>
                    Trước
                  </button>
                  <span>
                    Trang {txPage}/{txTotalPages}
                  </span>
                  <button type="button" className="outline" disabled={txPage === txTotalPages} onClick={() => setTxPage((p) => Math.min(txTotalPages, p + 1))}>
                    Sau
                  </button>
                </div>
              )}
            </section>
          )}

          {txFilter === 'cancelled' && (
            <section className="admin-company-list-panel">
              <div className="admin-company-list-header">
                <div>
                  <h2>Đã hủy</h2>
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    Chỉ khôi phục gói đã từng thanh toán (hủy bởi admin). Bill hủy do chưa thanh toán không khôi phục.
                  </p>
                </div>
                <span>{filteredCancelled.length} đăng ký</span>
              </div>
              <div className="admin-review-list">
                {pagedCancelled.map((item) => {
                  const restorable = canRestoreCancelled(item);
                  const payment = anyPaymentBySubId.get(item.id);
                  return (
                  <article key={item.id} className="admin-review-row">
                    <div className="admin-review-main">
                      <div className="admin-review-title-line">
                        <strong>{item.userEmail}</strong>
                        <span className="admin-status-badge status-rejected">Đã hủy</span>
                        {!restorable && (
                          <span className="admin-status-badge status-unverified">Chưa thanh toán</span>
                        )}
                      </div>
                      <div className="admin-review-meta">
                        <span>Gói: <strong>{item.planName}</strong></span>
                        <span>{item.userName || '—'}</span>
                        {payment?.transferContent && (
                          <span>ND CK: <strong>{payment.transferContent}</strong></span>
                        )}
                        <span>{formatDate(item.startDate)} → {formatDate(item.endDate)}</span>
                      </div>
                      {item.cancelledReason && (
                        <p className="muted">Lý do: {item.cancelledReason}</p>
                      )}
                    </div>
                    {restorable && (
                      <button type="button" onClick={() => restoreSubscription(item)}>
                        Khôi phục gói
                      </button>
                    )}
                  </article>
                  );
                })}
                {!loading && filteredCancelled.length === 0 && (
                  <div className="admin-placeholder-card">
                    <p>{txSearch.trim() ? 'Không tìm thấy đăng ký phù hợp.' : 'Không có gói đã hủy.'}</p>
                  </div>
                )}
              </div>
              {filteredCancelled.length > PAGE_SIZE && (
                <div className="admin-pagination">
                  <button type="button" className="outline" disabled={txPage === 1} onClick={() => setTxPage((p) => Math.max(1, p - 1))}>
                    Trước
                  </button>
                  <span>
                    Trang {txPage}/{txTotalPages}
                  </span>
                  <button type="button" className="outline" disabled={txPage === txTotalPages} onClick={() => setTxPage((p) => Math.min(txTotalPages, p + 1))}>
                    Sau
                  </button>
                </div>
              )}
            </section>
          )}
        </>
      )}
    </section>
  );
}
