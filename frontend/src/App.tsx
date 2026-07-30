import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import AdminAuditPage from './pages/admin/AdminAuditPage';
import AdminBillingPage from './pages/admin/AdminBillingPage';
import AdminCompanyDetailPage from './pages/admin/AdminCompanyDetailPage';
import AdminCompanyReviewPage from './pages/admin/AdminCompanyReviewPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminJobDetailPage from './pages/admin/AdminJobDetailPage';
import AdminJobsPage from './pages/admin/AdminJobsPage';
import AdminLayout, { AdminProtected } from './pages/admin/AdminLayout';
import AdminLoginPage from './pages/admin/AdminLoginPage';
import AdminPlanFormPage from './pages/admin/AdminPlanFormPage';
import AdminProfilePage from './pages/admin/AdminProfilePage';
import AdminSettingsPage from './pages/admin/AdminSettingsPage';
import AdminStatisticsPage from './pages/admin/AdminStatisticsPage';
import AdminUsersPage from './pages/admin/AdminUsersPage';
import EmployerSubscriptionPage from './pages/billing/EmployerSubscriptionPage';
import PaymentResultPage from './pages/billing/PaymentResultPage';
import BankTransferCheckoutPage from './pages/billing/BankTransferCheckoutPage';
import PaymentCheckoutPage from './pages/billing/PaymentCheckoutPage';
import SubscriptionPlansPage from './pages/billing/SubscriptionPlansPage';
import { authService } from './services/authService';
import { aiInterviewService } from './services/aiInterviewService';
import { useVoiceConversation, type VoicePhase } from './hooks/useVoiceConversation';
import { clearAuthSession, getToken, setAuthSession, getStoredUser } from './utils/authStorage';
import { candidateService } from './services/candidateService';
import { jobService } from './services/jobService';
import { publicSettingsService, type PublicSettings } from './services/publicSettingsService';
import CompanyProfilePage from './pages/Employer/CompanyProfilePage';
import CompanyLocationsPage from './pages/Employer/CompanyLocationsPage';
import CompanyVerificationPage from './pages/Employer/CompanyVerificationPage';
import EmployerJobsPage from './pages/Employer/EmployerJobsPage';
import type {
  AiInterviewConfig,
  AiInterviewEligibleApplication,
  AiInterviewQuestion,
  AiInterviewQuestionSet,
  AiInterviewSession,
} from './types/aiInterview';
import EmployerApplicationsPage from './pages/Employer/EmployerApplicationsPage';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  CvVersion,
  NotificationItem,
  SubscriptionView,
} from './types/candidateDomain';
import type { Job, JobFilters, Recommendation } from './types/job';

// ─── Framer Motion variants ────────────────────────────────────────────────
const fadeUp = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
  exit:    { opacity: 0, y: -8 },
};

const scaleIn = {
  initial: { opacity: 0, scale: 0.95 },
  animate: { opacity: 1, scale: 1 },
  exit:    { opacity: 0, scale: 0.97 },
};

const EASE_OUT = [0.23, 1, 0.32, 1] as const;

// ─── Status labels ─────────────────────────────────────────────────────────
const statusLabels: Record<string, string> = {
  SUBMITTED: 'Đã nộp',
  UNDER_REVIEW: 'Đang xem xét',
  SHORTLISTED: 'Vào shortlist',
  INTERVIEW_SCHEDULED: 'Hẹn phỏng vấn',
  INTERVIEWED: 'Đã phỏng vấn',
  EVALUATED: 'Đã đánh giá',
  ACCEPTED: 'Chấp nhận',
  REJECTED: 'Từ chối',
  HIRED: 'Đã tuyển',
};

const statusColors: Record<string, string> = {
  SUBMITTED: 'neutral',
  UNDER_REVIEW: '',
  SHORTLISTED: 'match',
  INTERVIEW_SCHEDULED: 'match',
  INTERVIEWED: 'match',
  EVALUATED: 'warning',
  ACCEPTED: 'match',
  REJECTED: 'danger',
  HIRED: 'match',
};

// ─── App routes ────────────────────────────────────────────────────────────
function App() {
  const [publicSettings, setPublicSettings] = useState<PublicSettings>(publicSettingsService.defaults);

  useEffect(() => {
    publicSettingsService.get()
      .then((settings) => {
        setPublicSettings(settings);
        document.documentElement.style.setProperty('--primary', settings.themePrimaryColor || '#00507d');
        document.title = settings.siteName || 'Smart Recruitment Portal';
      })
      .catch(() => {
        // keep defaults
      });
  }, []);

  const user = getStoredUser();
  const isAdmin = user?.role === 'ADMIN';
  const showMaintenance = publicSettings.maintenanceMode && !isAdmin && !window.location.pathname.startsWith('/admin');

  if (showMaintenance) {
    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: '#f8fafc' }}>
        <section style={{ maxWidth: 480, textAlign: 'center', background: '#fff', padding: 28, borderRadius: 16, boxShadow: '0 8px 30px rgba(15,23,42,0.08)' }}>
          <h1 style={{ marginTop: 0 }}>{publicSettings.siteName}</h1>
          <p>Hệ thống đang bảo trì. Vui lòng quay lại sau.</p>
          <p className="muted">Hỗ trợ: {publicSettings.supportEmail}</p>
          <Link to="/admin/login" className="button-link outline">Đăng nhập Admin</Link>
        </section>
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
      <Route path="/select-role" element={<SelectRolePage />} />
      <Route path="/jobs" element={<JobsPage />} />
      <Route path="/jobs/:id" element={<JobDetailPage />} />
      <Route path="/candidate" element={<Protected><CandidateLayout /></Protected>}>
        <Route index element={<CandidateHome />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="cvs" element={<CvPage />} />
        <Route path="saved-jobs" element={<SavedJobsPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="ai-interviews" element={<AiInterviewPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="subscription" element={<SubscriptionPage />} />
        <Route path="subscription/plans" element={<SubscriptionPlansPage backTo="/candidate/subscription" backLabel="Quay lại gói dịch vụ" />} />
      </Route>
      <Route path="/payment/result" element={<Protected><PaymentResultPage /></Protected>} />
      <Route path="/payment/checkout" element={<Protected><PaymentCheckoutPage /></Protected>} />
      <Route path="/payment/bank/:paymentId" element={<Protected><BankTransferCheckoutPage /></Protected>} />
      <Route path="/employer" element={<Protected><EmployerLayout /></Protected>}>
        <Route index element={<EmployerDashboard />} />
        <Route path="company-profile" element={<CompanyProfilePage />} />
        <Route path="locations" element={<CompanyLocationsPage />} />
        <Route path="verification" element={<CompanyVerificationPage />} />
        <Route path="jobs" element={<EmployerJobsPage />} />
        <Route path="applications" element={<EmployerApplicationsPage />} />
        <Route path="jobs/:jobId/applications" element={<EmployerApplicationsPage />} />
        <Route path="subscription" element={<EmployerSubscriptionPage />} />
        <Route path="subscription/plans" element={<SubscriptionPlansPage backTo="/employer/subscription" backLabel="Quay lại gói dịch vụ" title="Gói dành cho nhà tuyển dụng" />} />
      </Route>
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route path="/admin" element={<AdminProtected><AdminLayout /></AdminProtected>}>
        <Route index element={<AdminDashboardPage />} />
        <Route path="companies" element={<AdminCompanyReviewPage />} />
        <Route path="companies/:id" element={<AdminCompanyDetailPage />} />
        <Route path="users" element={<AdminUsersPage />} />
        <Route path="jobs" element={<AdminJobsPage />} />
        <Route path="jobs/:id" element={<AdminJobDetailPage />} />
        <Route path="billing" element={<AdminBillingPage />} />
        <Route path="billing/plans/new" element={<AdminPlanFormPage />} />
        <Route path="billing/plans/:id/edit" element={<AdminPlanFormPage />} />
        <Route path="categories" element={<Navigate to="/admin/settings?tab=categories" replace />} />
        <Route path="statistics" element={<AdminStatisticsPage />} />
        <Route path="audit-logs" element={<AdminAuditPage />} />
        <Route path="settings" element={<AdminSettingsPage />} />
        <Route path="profile" element={<AdminProfilePage />} />
      </Route>
    </Routes>
  );
}

// ─── Utilities ─────────────────────────────────────────────────────────────
function formatMoney(value?: number) {
  if (!value) return 'Thỏa thuận';
  return new Intl.NumberFormat('vi-VN').format(value) + ' VND';
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Có lỗi xảy ra';
  }
  return 'Có lỗi xảy ra';
}

function Protected({ children }: { children: JSX.Element }) {
  if (!getToken()) return <Navigate to="/login" replace />;
  return children;
}

// ─── Public Shell (with topbar) ────────────────────────────────────────────
function Shell({ children }: { children: React.ReactNode }) {
  const token = getToken();
  const role = localStorage.getItem('role');
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 8);
    window.addEventListener('scroll', handler, { passive: true });
    return () => window.removeEventListener('scroll', handler);
  }, []);

  return (
    <div className="app-shell">
      <header className={`topbar ${scrolled ? 'scrolled' : ''}`}>
        <Link className="brand" to="/jobs">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.15"/>
            <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
            <path d="M12 13v4M10 15h4" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          Smart Recruitment
        </Link>

        <nav className="topbar-nav">
          <NavLink to="/jobs" className={({ isActive }) => `topbar-nav-item ${isActive ? 'active' : ''}`}>
            Việc làm
          </NavLink>
          {token && role === 'CANDIDATE' && (
            <NavLink to="/candidate" className={({ isActive }) => `topbar-nav-item ${isActive ? 'active' : ''}`}>
              Dashboard
            </NavLink>
          )}
          {token && role === 'EMPLOYER' && (
            <NavLink to="/employer" className={({ isActive }) => `topbar-nav-item ${isActive ? 'active' : ''}`}>
              Nhà tuyển dụng
            </NavLink>
          )}
        </nav>

        <div className="topbar-actions">
          {!token && (
            <>
              <Link to="/login" className="button-link outline" style={{ minHeight: 36 }}>
                Đăng nhập
              </Link>
              <Link to="/register" className="button-link" style={{ minHeight: 36 }}>
                Đăng ký
              </Link>
            </>
          )}
          {token && (
            <button
              className="outline sm"
              onClick={() => { clearAuthSession(); window.location.href = '/login'; }}
            >
              Đăng xuất
            </button>
          )}
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}

// ─── HOME PAGE ──────────────────────────────────────────────────────────────
function HomePage() {
  const token = getToken();
  const role = localStorage.getItem('role');
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 8);
    window.addEventListener('scroll', handler, { passive: true });
    return () => window.removeEventListener('scroll', handler);
  }, []);

  const stats = [
    { value: '10,000+', label: 'Việc làm đang tuyển' },
    { value: '5,000+', label: 'Công ty đối tác' },
    { value: '50,000+', label: 'Ứng viên thành công' },
    { value: '98%', label: 'Tỷ lệ hài lòng' },
  ];

  const features = [
    {
      icon: '🤖',
      title: 'AI Interview Luyện tập',
      desc: 'Chuẩn bị phỏng vấn với AI thông minh, nhận phản hồi chi tiết để cải thiện kỹ năng.',
      color: '#3b82f6',
      bg: '#eff6ff',
    },
    {
      icon: '⚡',
      title: 'Gợi ý Việc làm Thông minh',
      desc: 'Thuật toán AI phân tích hồ sơ và đề xuất việc làm phù hợp nhất với bạn.',
      color: '#f59e0b',
      bg: '#fffbeb',
    },
    {
      icon: '🎯',
      title: 'Ứng tuyển Một chạm',
      desc: 'Nộp hồ sơ nhanh chóng với CV đã lưu sẵn. Theo dõi trạng thái ứng tuyển realtime.',
      color: '#10b981',
      bg: '#f0fdf4',
    },
    {
      icon: '🏢',
      title: 'Hệ thống Tuyển dụng Toàn diện',
      desc: 'Nhà tuyển dụng quản lý tin đăng, duyệt hồ sơ, lên lịch phỏng vấn trên một nền tảng.',
      color: '#8b5cf6',
      bg: '#f5f3ff',
    },
  ];

  return (
    <div className="home-shell">
      {/* Navbar */}
      <header className={`home-topbar ${scrolled ? 'scrolled' : ''}`}>
        <Link className="brand" to="/">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.15"/>
            <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
            <path d="M12 13v4M10 15h4" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          Smart Recruitment
        </Link>

        <nav className="home-topbar-nav">
          <Link to="/jobs" className="home-nav-link">Việc làm</Link>
          {token && role === 'CANDIDATE' && (
            <Link to="/candidate" className="home-nav-link">Dashboard</Link>
          )}
          {token && role === 'EMPLOYER' && (
            <Link to="/employer" className="home-nav-link">Nhà tuyển dụng</Link>
          )}
        </nav>

        <div className="home-topbar-actions">
          {!token ? (
            <>
              <Link to="/login" className="button-link outline" style={{ minHeight: 38 }}>
                Đăng nhập
              </Link>
              <Link to="/register" className="button-link" style={{ minHeight: 38 }}>
                Đăng ký miễn phí
              </Link>
            </>
          ) : (
            <>
              {role === 'CANDIDATE' && (
                <Link to="/candidate" className="button-link" style={{ minHeight: 38 }}>
                  Vào Dashboard →
                </Link>
              )}
              {role === 'EMPLOYER' && (
                <Link to="/employer" className="button-link" style={{ minHeight: 38 }}>
                  Employer Portal →
                </Link>
              )}
            </>
          )}
        </div>
      </header>

      {/* Hero Section */}
      <section className="home-hero">
        <div className="home-hero-bg" aria-hidden="true" />
        <div className="home-hero-content">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: EASE_OUT }}
          >
            <span className="home-hero-eyebrow">
              🚀 Nền tảng tuyển dụng thông minh hàng đầu Việt Nam
            </span>
            <h1 className="home-hero-title">
              Kết nối
              <span className="home-hero-accent"> Tài năng</span>
              {' '}với
              <br />Cơ hội Nghề nghiệp
            </h1>
            <p className="home-hero-desc">
              Smart Recruitment Portal giúp ứng viên tìm việc phù hợp với AI thông minh,
              đồng thời hỗ trợ nhà tuyển dụng tìm kiếm nhân tài hiệu quả nhất.
            </p>
          </motion.div>

          <motion.div
            className="home-hero-actions"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: EASE_OUT, delay: 0.12 }}
          >
            <Link to="/jobs" className="button-link home-hero-btn-primary">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
              </svg>
              Tìm việc làm ngay
            </Link>
            {!token && (
              <Link to="/register" className="button-link outline home-hero-btn-secondary">
                Đăng ký miễn phí →
              </Link>
            )}
          </motion.div>

          {/* Search bar */}
          <motion.div
            className="home-search-bar"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: EASE_OUT, delay: 0.2 }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--outline)" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            <input
              placeholder="Tìm kiếm vị trí, kỹ năng, công ty..."
              readOnly
              onClick={() => window.location.href = '/jobs'}
              style={{ cursor: 'pointer' }}
            />
            <Link to="/jobs" className="button-link home-search-btn">Tìm kiếm</Link>
          </motion.div>
        </div>
      </section>

      {/* Stats */}
      <section className="home-stats">
        {stats.map(({ value, label }, i) => (
          <motion.div
            key={label}
            className="home-stat-item"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: EASE_OUT, delay: 0.25 + i * 0.07 }}
          >
            <strong className="home-stat-value">{value}</strong>
            <span className="home-stat-label">{label}</span>
          </motion.div>
        ))}
      </section>

      {/* Features */}
      <section className="home-section">
        <div className="home-section-inner">
          <div className="home-section-header">
            <p className="eyebrow" style={{ textAlign: 'center', marginBottom: 8 }}>Tính năng nổi bật</p>
            <h2 className="home-section-title">Tất cả những gì bạn cần</h2>
            <p className="home-section-desc">
              Từ AI luyện phỏng vấn đến quản lý tuyển dụng — mọi thứ trên một nền tảng duy nhất.
            </p>
          </div>

          <div className="home-features-grid">
            {features.map(({ icon, title, desc, color, bg }, i) => (
              <motion.div
                key={title}
                className="home-feature-card"
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: EASE_OUT, delay: 0.1 + i * 0.08 }}
              >
                <div className="home-feature-icon" style={{ background: bg, color }}>
                  {icon}
                </div>
                <h3>{title}</h3>
                <p>{desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* For Candidates & Employers */}
      <section className="home-roles-section">
        <div className="home-section-inner">
          <div className="home-roles-grid">
            {/* For Candidates */}
            <motion.div
              className="home-role-card home-role-candidate"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.5, ease: EASE_OUT }}
            >
              <div className="home-role-icon">👨‍💼</div>
              <h3>Dành cho Ứng viên</h3>
              <ul className="home-role-list">
                <li>✅ Tìm việc làm phù hợp với AI</li>
                <li>✅ Luyện phỏng vấn với AI thông minh</li>
                <li>✅ Tạo và quản lý CV chuyên nghiệp</li>
                <li>✅ Theo dõi trạng thái ứng tuyển</li>
                <li>✅ Nhận thông báo realtime</li>
              </ul>
              <Link
                to={token && role === 'CANDIDATE' ? '/candidate' : '/register'}
                className="button-link home-role-btn"
              >
                {token && role === 'CANDIDATE' ? 'Vào Dashboard →' : 'Bắt đầu tìm việc →'}
              </Link>
            </motion.div>

            {/* For Employers */}
            <motion.div
              className="home-role-card home-role-employer"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.5, ease: EASE_OUT, delay: 0.1 }}
            >
              <div className="home-role-icon">🏢</div>
              <h3>Dành cho Nhà tuyển dụng</h3>
              <ul className="home-role-list">
                <li>✅ Đăng tin tuyển dụng dễ dàng</li>
                <li>✅ Quản lý hồ sơ ứng viên</li>
                <li>✅ Xem xét và phê duyệt nhanh</li>
                <li>✅ Báo cáo và thống kê chi tiết</li>
                <li>✅ Xác thực pháp lý doanh nghiệp</li>
              </ul>
              <Link
                to={token && role === 'EMPLOYER' ? '/employer' : '/register'}
                className="button-link outline home-role-btn"
              >
                {token && role === 'EMPLOYER' ? 'Vào Employer Portal →' : 'Đăng ký tuyển dụng →'}
              </Link>
            </motion.div>
          </div>
        </div>
      </section>

      {/* CTA Banner */}
      {!token && (
        <section className="home-cta-section">
          <div className="home-section-inner" style={{ textAlign: 'center' }}>
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, ease: EASE_OUT }}
            >
              <h2 className="home-cta-title">Sẵn sàng bắt đầu hành trình của bạn?</h2>
              <p className="home-cta-desc">
                Tham gia cùng hàng nghìn người đã tìm được công việc mơ ước qua Smart Recruitment Portal.
              </p>
              <div className="home-cta-actions">
                <Link to="/register" className="button-link home-cta-btn">
                  Tạo tài khoản miễn phí
                </Link>
                <Link to="/jobs" className="button-link outline home-cta-btn">
                  Xem việc làm →
                </Link>
              </div>
            </motion.div>
          </div>
        </section>
      )}

      {/* Footer */}
      <footer className="home-footer">
        <div className="home-section-inner">
          <div className="home-footer-grid">
            <div>
              <Link className="brand" to="/" style={{ marginBottom: 12, display: 'inline-flex' }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.2"/>
                  <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
                  <path d="M12 13v4M10 15h4" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round"/>
                </svg>
                Smart Recruitment
              </Link>
              <p style={{ color: 'var(--on-muted)', fontSize: '0.875rem', maxWidth: 280, margin: 0 }}>
                Nền tảng tuyển dụng thông minh, kết nối tài năng với cơ hội nghề nghiệp tốt nhất.
              </p>
            </div>
            <div>
              <strong className="home-footer-heading">Ứng viên</strong>
              <nav className="home-footer-nav">
                <Link to="/jobs">Tìm việc làm</Link>
                <Link to="/register">Đăng ký</Link>
                <Link to="/login">Đăng nhập</Link>
              </nav>
            </div>
            <div>
              <strong className="home-footer-heading">Nhà tuyển dụng</strong>
              <nav className="home-footer-nav">
                <Link to="/register">Đăng ký tuyển dụng</Link>
                <Link to="/login">Đăng nhập</Link>
              </nav>
            </div>
            <div>
              <strong className="home-footer-heading">Hệ thống</strong>
              <nav className="home-footer-nav">
                <Link to="/admin/login">Admin</Link>
              </nav>
            </div>
          </div>
          <div className="home-footer-bottom">
            <p>© {new Date().getFullYear()} Smart Recruitment Portal. All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>
  );
}

// ─── LOGIN PAGE ─────────────────────────────────────────────────────────────
function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('candidate.demo@sjp.local');
  const [password, setPassword] = useState('Password123!');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [googleOAuthEnabled, setGoogleOAuthEnabled] = useState(false);
  const oauthError = params.get('oauthError');

  useEffect(() => {
    authService.getConfig()
      .then((config) => setGoogleOAuthEnabled(config.googleOAuthEnabled))
      .catch(() => setGoogleOAuthEnabled(false));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const response = await authService.login({ email, password });
      if (response.token) {
        setAuthSession(response.token, response.user);
      }
      if (response.user.role === 'ADMIN') navigate('/admin');
      else if (response.user.role === 'EMPLOYER') navigate('/employer');
      else if (response.user.role === 'CANDIDATE') navigate('/candidate');
      else navigate('/jobs');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <motion.div
        className="auth-card"
        variants={scaleIn}
        initial="initial"
        animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}
      >
        {/* Logo */}
        <div className="auth-logo">
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 12 }}>
            <div style={{
              width: 52, height: 52, borderRadius: 12,
              background: 'var(--primary-softer)',
              border: '2px solid var(--primary-soft)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.2"/>
                <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
                <path d="M12 13v4M10 15h4" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
          </div>
          <h1>Smart Recruitment</h1>
          <p>Nền tảng tuyển dụng thông minh</p>
        </div>

        {/* Heading */}
        <div className="auth-heading">
          <h2>Đăng nhập</h2>
          <p>Chào mừng trở lại! Vui lòng nhập thông tin tài khoản.</p>
        </div>

        {/* OAuth error */}
        <AnimatePresence>
          {oauthError === 'google_not_configured' && (
            <motion.div
              className="error-panel"
              variants={scaleIn} initial="initial" animate="animate" exit="exit"
              transition={{ duration: 0.2, ease: EASE_OUT }}
              style={{ marginBottom: 16 }}
            >
              Đăng nhập Google chưa được cấu hình trên môi trường này.
            </motion.div>
          )}
        </AnimatePresence>

        <form className="auth-form" onSubmit={submit}>
          {/* Email */}
          <label>
            Email
            <div className="input-icon-wrap">
              <span className="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="2" y="4" width="20" height="16" rx="2"/>
                  <path d="m2 7 10 7 10-7"/>
                </svg>
              </span>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ten@congty.com"
                required
                autoComplete="email"
              />
            </div>
          </label>

          {/* Password */}
          <label>
            <div className="field-header">
              <span>Mật khẩu</span>
              <a href="#">Quên mật khẩu?</a>
            </div>
            <div className="input-icon-wrap">
              <span className="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </span>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                autoComplete="current-password"
                style={{ paddingRight: 44 }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute', right: 0, top: 0, bottom: 0,
                  background: 'transparent', border: 'none', color: 'var(--outline)',
                  cursor: 'pointer', minHeight: 'auto', padding: '0 12px', width: 'auto',
                }}
                tabIndex={-1}
              >
                {showPassword ? (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                ) : (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                )}
              </button>
            </div>
          </label>

          {/* Error */}
          <AnimatePresence>
            {error && (
              <motion.div
                className="error-panel"
                variants={scaleIn} initial="initial" animate="animate" exit="exit"
                transition={{ duration: 0.18, ease: EASE_OUT }}
              >
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Submit */}
          <button type="submit" disabled={loading} style={{ width: '100%', minHeight: 44 }}>
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
                  style={{ animation: 'spin 0.8s linear infinite' }}>
                  <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
                </svg>
                Đang đăng nhập...
              </span>
            ) : 'Đăng nhập'}
          </button>

          {/* Divider */}
          {googleOAuthEnabled && (
            <>
              <div className="auth-divider">
                <span>Hoặc tiếp tục với</span>
              </div>
              <a className="btn-google" href="/api/oauth2/authorization/google">
                <svg width="18" height="18" viewBox="0 0 24 24">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
                Google
              </a>
            </>
          )}
        </form>

        {/* Footer */}
        <div className="auth-footer">
          <p>
            Chưa có tài khoản?{' '}
            <Link to="/register">Đăng ký ngay</Link>
          </p>
          <p style={{ marginTop: 8, fontSize: '0.78rem', color: 'var(--outline)', lineHeight: 1.5 }}>
            Demo — Ứng viên: candidate.demo@sjp.local / Password123!
            <br />
            Nhà tuyển dụng: employer.demo@sjp.local / Password123!
            <br />
            Admin: admin@sjp.local / Admin@123
          </p>
        </div>
      </motion.div>
    </div>
  );
}

// ─── REGISTER PAGE ──────────────────────────────────────────────────────────
function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'CANDIDATE' | 'EMPLOYER'>('CANDIDATE');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    setLoading(true);
    try {
      await authService.register({ email, password, role });
      setMessage('Đăng ký thành công! Vui lòng kiểm tra email để xác minh tài khoản.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <motion.div
        className="auth-card"
        variants={scaleIn}
        initial="initial"
        animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}
      >
        <div className="auth-logo">
          <h1>Tạo tài khoản</h1>
          <p>Tham gia Smart Recruitment Portal</p>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <label>
            Email
            <div className="input-icon-wrap">
              <span className="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="2" y="4" width="20" height="16" rx="2"/>
                  <path d="m2 7 10 7 10-7"/>
                </svg>
              </span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ten@congty.com"
                required
              />
            </div>
          </label>

          <label>
            Mật khẩu
            <div className="input-icon-wrap">
              <span className="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Tối thiểu 8 ký tự"
                required
              />
            </div>
          </label>

          <label>
            Vai trò
            <select value={role} onChange={(e) => setRole(e.target.value as 'CANDIDATE' | 'EMPLOYER')}>
              <option value="CANDIDATE">Ứng viên</option>
              <option value="EMPLOYER">Nhà tuyển dụng</option>
            </select>
          </label>

          <AnimatePresence>
            {message && (
              <motion.div className="success-panel" variants={scaleIn} initial="initial" animate="animate" exit="exit"
                transition={{ duration: 0.18, ease: EASE_OUT }}>
                {message}
              </motion.div>
            )}
            {error && (
              <motion.div className="error-panel" variants={scaleIn} initial="initial" animate="animate" exit="exit"
                transition={{ duration: 0.18, ease: EASE_OUT }}>
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          <button type="submit" disabled={loading} style={{ width: '100%', minHeight: 44 }}>
            {loading ? 'Đang xử lý...' : 'Tạo tài khoản'}
          </button>
        </form>

        <div className="auth-footer">
          <p>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
        </div>
      </motion.div>
    </div>
  );
}

// ─── VERIFY EMAIL ───────────────────────────────────────────────────────────
function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState('Đang xác minh...');

  useEffect(() => {
    const token = params.get('token');
    if (!token) { setMessage('Thiếu token xác minh.'); return; }
    authService.verifyEmail(token)
      .then(() => setMessage('Email đã được xác minh. Bạn có thể đăng nhập.'))
      .catch((err) => setMessage(readError(err)));
  }, [params]);

  return (
    <div className="auth-shell">
      <motion.div className="auth-card" variants={scaleIn} initial="initial" animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}>
        <div className="auth-logo"><h1>Xác minh Email</h1></div>
        <p style={{ color: 'var(--on-muted)', textAlign: 'center' }}>{message}</p>
        <div className="auth-footer"><Link to="/login">Về trang đăng nhập</Link></div>
      </motion.div>
    </div>
  );
}

// ─── OAUTH CALLBACK ─────────────────────────────────────────────────────────
function OAuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  useEffect(() => {
    const token = params.get('token');
    if (token) {
      localStorage.setItem('token', token);
      authService.getCurrentUser()
        .then((user) => {
          setAuthSession(token, user);
          if (user.role === 'CANDIDATE') navigate('/candidate');
          else if (user.role === 'EMPLOYER') navigate('/employer');
          else navigate('/jobs');
        })
        .catch((err) => {
          setError(readError(err));
          localStorage.removeItem('token');
          setTimeout(() => navigate('/login'), 2000);
        });
    } else {
      navigate('/login');
    }
  }, [navigate, params]);

  if (error) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <div className="auth-logo"><h1>Lỗi đăng nhập</h1></div>
          <div className="error-panel">{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-shell">
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto 12px' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <p style={{ color: 'var(--on-muted)' }}>Đang hoàn tất đăng nhập Google...</p>
      </div>
    </div>
  );
}

// ─── SELECT ROLE ─────────────────────────────────────────────────────────────
function SelectRolePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  async function select(role: 'CANDIDATE' | 'EMPLOYER') {
    const token = params.get('token');
    if (!token) return setError('Thiếu token chọn vai trò.');
    try {
      const response = await authService.completeOauthRole(token, role);
      if (response.token) { setAuthSession(response.token, response.user); }
      navigate(role === 'CANDIDATE' ? '/candidate' : '/employer');
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <div className="auth-shell">
      <motion.div className="auth-card" variants={scaleIn} initial="initial" animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}>
        <div className="auth-logo">
          <h1>Chọn vai trò</h1>
          <p>Bạn muốn sử dụng hệ thống với tư cách nào?</p>
        </div>
        <div style={{ display: 'grid', gap: 12 }}>
          <button onClick={() => select('CANDIDATE')} style={{ minHeight: 52, fontSize: '1rem' }}>
            🎓 Ứng viên
          </button>
          <button className="outline" onClick={() => select('EMPLOYER')} style={{ minHeight: 52, fontSize: '1rem' }}>
            🏢 Nhà tuyển dụng
          </button>
        </div>
        {error && <div className="error-panel" style={{ marginTop: 12 }}>{error}</div>}
      </motion.div>
    </div>
  );
}

// ─── JOB SEARCH PAGE ────────────────────────────────────────────────────────
function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [filters, setFilters] = useState<JobFilters>({ sort: 'newest' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await jobService.getAll(filters, 0, 12);
      setJobs(response.content);
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { void load(); }, [load]);

  return (
    <Shell>
      <div className="jobs-shell">
        <div className="jobs-layout">
          {/* Filter Sidebar */}
          <aside className="filter-panel">
            <h2>Tìm việc làm</h2>

            <div>
              <label className="filter-label">Từ khóa</label>
              <div className="input-icon-wrap">
                <span className="input-icon">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                  </svg>
                </span>
                <input
                  placeholder="Tên vị trí, kỹ năng..."
                  value={filters.search || ''}
                  onChange={(e) => setFilters({ ...filters, search: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="filter-label">Địa điểm</label>
              <div className="input-icon-wrap">
                <span className="input-icon">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                </span>
                <input
                  placeholder="TP.HCM, Hà Nội..."
                  value={filters.location || ''}
                  onChange={(e) => setFilters({ ...filters, location: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="filter-label">Kỹ năng</label>
              <input
                placeholder="Java, React, Python..."
                value={filters.skills || ''}
                onChange={(e) => setFilters({ ...filters, skills: e.target.value })}
              />
            </div>

            <div>
              <label className="filter-label">Kinh nghiệm</label>
              <select
                value={filters.experienceLevel || ''}
                onChange={(e) => setFilters({ ...filters, experienceLevel: e.target.value })}
              >
                <option value="">Tất cả cấp độ</option>
                <option value="INTERN">Thực tập sinh</option>
                <option value="FRESHER">Fresher</option>
                <option value="JUNIOR">Junior</option>
                <option value="MIDDLE">Middle</option>
                <option value="SENIOR">Senior</option>
              </select>
            </div>

            <div>
              <label className="filter-label">Sắp xếp</label>
              <select
                value={filters.sort || 'newest'}
                onChange={(e) => setFilters({ ...filters, sort: e.target.value })}
              >
                <option value="newest">Mới nhất</option>
                <option value="relevance">Phù hợp nhất</option>
                <option value="salary">Lương cao nhất</option>
                <option value="deadline">Gần deadline</option>
              </select>
            </div>

            <button onClick={load} style={{ width: '100%' }}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
              </svg>
              Tìm kiếm
            </button>

            {error && <div className="error-panel">{error}</div>}
          </aside>

          {/* Job List */}
          <div>
            <div className="jobs-list-header">
              <h1>Việc làm đang tuyển</h1>
              {!loading && (
                <span className="chip neutral">{jobs.length} kết quả</span>
              )}
            </div>

            {loading ? (
              <div className="job-grid">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="job-card-skeleton">
                    <div style={{ display: 'flex', gap: 14 }}>
                      <div className="skeleton" style={{ width: 48, height: 48, borderRadius: 8, flexShrink: 0 }} />
                      <div style={{ flex: 1, display: 'grid', gap: 8 }}>
                        <div className="skeleton" style={{ height: 18, width: '70%' }} />
                        <div className="skeleton" style={{ height: 14, width: '50%' }} />
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <div className="skeleton" style={{ height: 24, width: 80 }} />
                      <div className="skeleton" style={{ height: 24, width: 100 }} />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="job-grid">
                {jobs.map((job, i) => (
                  <motion.div
                    key={job.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.28, ease: EASE_OUT, delay: Math.min(i * 0.04, 0.3) }}
                  >
                    <JobCard job={job} />
                  </motion.div>
                ))}
                {jobs.length === 0 && (
                  <div className="empty-state card" style={{ padding: 48, textAlign: 'center' }}>
                    <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>🔍</div>
                    <h3>Không tìm thấy việc làm</h3>
                    <p className="muted">Thử thay đổi bộ lọc để xem thêm kết quả.</p>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </Shell>
  );
}

// ─── JOB CARD ───────────────────────────────────────────────────────────────
function JobCard({ job }: { job: Job }) {
  const initials = job.company.name.slice(0, 2).toUpperCase();
  return (
    <Link to={`/jobs/${job.id}`} style={{ display: 'block' }}>
      <article className="job-card">
        <div className="job-card-header">
          <div className="job-company-logo">{initials}</div>
          <div className="job-card-info" style={{ flex: 1 }}>
            <h3>{job.title}</h3>
            <p className="job-card-company">{job.company.name}</p>
            <div className="job-card-meta">
              {job.location && (
                <span className="job-meta-badge">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  {job.location}
                </span>
              )}
              {job.experienceLevel && (
                <span className="job-meta-badge">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="2" y="7" width="20" height="14" rx="2"/>
                    <path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/>
                  </svg>
                  {job.experienceLevel}
                </span>
              )}
              {(job.salaryMin || job.salaryMax) && (
                <span className="job-meta-badge salary">
                  💰 {formatMoney(job.salaryMin)} – {formatMoney(job.salaryMax)}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="job-card-footer">
          {job.matchScore !== undefined && (
            <span className="chip match">⚡ {job.matchScore}% phù hợp</span>
          )}
          {job.saved && <span className="chip">🔖 Đã lưu</span>}
          {job.applied && <span className="chip neutral">✓ Đã nộp</span>}
        </div>
      </article>
    </Link>
  );
}

// ─── JOB DETAIL PAGE ────────────────────────────────────────────────────────
function JobDetailPage() {
  const { id } = useParams();
  const [job, setJob] = useState<Job | null>(null);
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [cvId, setCvId] = useState<string | undefined>();
  const [message, setMessage] = useState('');
  const [applying, setApplying] = useState(false);
  const [showReportForm, setShowReportForm] = useState(false);
  const [reportReason, setReportReason] = useState('misleading');
  const [reportDescription, setReportDescription] = useState('');
  const [reporting, setReporting] = useState(false);
  const [reporterProfile, setReporterProfile] = useState<CandidateProfile | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setJob(await jobService.getById(id));
    if (getToken()) {
      candidateService.getCvs().then((items) => {
        setCvs(items);
        setCvId(items.find((item) => item.defaultCv)?.id || items[0]?.id);
      }).catch(() => setCvs([]));
      candidateService.getProfile().then(setReporterProfile).catch(() => setReporterProfile(null));
    }
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  async function toggleSave() {
    if (!job) return;
    if (job.saved) await candidateService.unsaveJob(job.id);
    else await candidateService.saveJob(job.id);
    await load();
  }

  async function apply() {
    if (!job) return;
    setApplying(true);
    try {
      await candidateService.apply(job.id, cvId);
      setMessage('✅ Đã nộp hồ sơ ứng tuyển thành công!');
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setApplying(false);
    }
  }

  async function submitReport(event: FormEvent) {
    event.preventDefault();
    if (!job) return;
    if (!getToken()) {
      setMessage('Vui lòng đăng nhập để gửi báo cáo.');
      return;
    }
    setReporting(true);
    setMessage('');
    try {
      await candidateService.reportJob(job.id, reportReason, reportDescription.trim());
      setMessage('✅ Đã gửi báo cáo. Admin sẽ kiểm tra tin tuyển dụng này.');
      setShowReportForm(false);
      setReportDescription('');
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setReporting(false);
    }
  }

  if (!job) {
    return (
      <Shell>
        <div style={{ padding: '48px 24px', textAlign: 'center' }}>
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto 12px' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          <p className="muted">Đang tải thông tin việc làm...</p>
        </div>
      </Shell>
    );
  }

  const initials = job.company.name.slice(0, 2).toUpperCase();

  return (
    <Shell>
      <div className="job-detail-layout">
        {/* Left: Job details */}
        <div className="job-detail-main">
          {/* Header card */}
          <motion.div className="job-detail-header" variants={fadeUp} initial="initial" animate="animate"
            transition={{ duration: 0.28, ease: EASE_OUT }}>
            <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
              <div className="job-company-logo" style={{ width: 64, height: 64, fontSize: '1.4rem', borderRadius: 12 }}>
                {initials}
              </div>
              <div style={{ flex: 1 }}>
                <p className="eyebrow">{job.company.name}</p>
                <h1 className="job-detail-title">{job.title}</h1>
                <div className="chip-row" style={{ marginTop: 12 }}>
                  {job.location && (
                    <span className="job-meta-badge">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                        <circle cx="12" cy="10" r="3"/>
                      </svg>
                      {job.location}
                    </span>
                  )}
                  {job.experienceLevel && (
                    <span className="job-meta-badge">{job.experienceLevel}</span>
                  )}
                  {(job.salaryMin || job.salaryMax) && (
                    <span className="job-meta-badge salary">
                      💰 {formatMoney(job.salaryMin)} – {formatMoney(job.salaryMax)}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </motion.div>

          {/* Description */}
          {job.description && (
            <motion.div className="job-detail-section" variants={fadeUp} initial="initial" animate="animate"
              transition={{ duration: 0.28, ease: EASE_OUT, delay: 0.06 }}>
              <h2>Mô tả công việc</h2>
              <p style={{ color: 'var(--on-muted)', lineHeight: 1.7, whiteSpace: 'pre-line' }}>{job.description}</p>
            </motion.div>
          )}

          {/* Requirements */}
          {job.requirements?.length > 0 && (
            <motion.div className="job-detail-section" variants={fadeUp} initial="initial" animate="animate"
              transition={{ duration: 0.28, ease: EASE_OUT, delay: 0.1 }}>
              <h2>Yêu cầu</h2>
              <div className="chip-row">
                {job.requirements.map((item, i) => (
                  <motion.span
                    key={item}
                    className="chip neutral"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.2, ease: EASE_OUT, delay: 0.12 + i * 0.03 }}
                  >
                    {item}
                  </motion.span>
                ))}
              </div>
            </motion.div>
          )}
        </div>

        {/* Right: Apply panel */}
        <aside className="apply-panel">
          {job.matchScore !== undefined && (
            <div className="match-score-ring">
              {job.matchScore}%
              <span>Phù hợp</span>
            </div>
          )}

          {getToken() ? (
            <>
              <button
                className="outline"
                onClick={toggleSave}
                style={{ width: '100%' }}
              >
                {job.saved ? '🔖 Bỏ lưu' : '🔖 Lưu việc làm'}
              </button>

              {cvs.length > 0 && (
                <div>
                  <label className="filter-label" style={{ marginBottom: 8 }}>Chọn CV</label>
                  <select value={cvId || ''} onChange={(e) => setCvId(e.target.value || undefined)}>
                    <option value="">Chọn CV của bạn</option>
                    {cvs.map((cv) => (
                      <option key={cv.id} value={cv.id}>{cv.originalFileName}</option>
                    ))}
                  </select>
                </div>
              )}

              <button
                onClick={apply}
                disabled={job.applied || applying}
                style={{ width: '100%', minHeight: 44 }}
              >
                {applying ? 'Đang gửi...' : job.applied ? '✓ Đã ứng tuyển' : 'Ứng tuyển ngay'}
              </button>

              <AnimatePresence>
                {message && (
                  <motion.div
                    className={message.startsWith('✅') ? 'success-panel' : 'error-panel'}
                    variants={scaleIn} initial="initial" animate="animate" exit="exit"
                    transition={{ duration: 0.18, ease: EASE_OUT }}
                  >
                    {message}
                  </motion.div>
                )}
              </AnimatePresence>

              <button
                type="button"
                className="outline"
                onClick={() => setShowReportForm((value) => !value)}
                style={{ width: '100%', marginTop: 4 }}
              >
                Báo cáo tin tuyển dụng
              </button>

              {showReportForm && (
                <form onSubmit={submitReport} style={{ display: 'grid', gap: 10, marginTop: 8 }}>
                  <div style={{
                    background: 'var(--surface-container, #f8fafc)',
                    border: '1px solid var(--outline-variant, #e2e8f0)',
                    borderRadius: 8,
                    padding: 12,
                    fontSize: '0.85rem',
                    lineHeight: 1.5,
                  }}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>Thông tin người báo cáo</div>
                    <div>Họ tên: <strong>{reporterProfile?.fullName || '—'}</strong></div>
                    <div>Tuổi: <strong>{reporterProfile?.age != null ? reporterProfile.age : '—'}</strong></div>
                    <div>SĐT: <strong>{reporterProfile?.phone || '—'}</strong></div>
                    {(!reporterProfile?.fullName || !reporterProfile?.phone) && (
                      <p className="muted" style={{ margin: '8px 0 0', fontSize: '0.8rem' }}>
                        Vui lòng cập nhật họ tên và số điện thoại trong hồ sơ trước khi gửi báo cáo.
                      </p>
                    )}
                  </div>
                  <label className="filter-label">
                    Lý do báo cáo
                    <select value={reportReason} onChange={(e) => setReportReason(e.target.value)} required>
                      <option value="misleading">Thông tin sai lệch</option>
                      <option value="scam">Lừa đảo / nghi ngờ</option>
                      <option value="spam">Spam / tin rác</option>
                      <option value="offensive">Nội dung phản cảm</option>
                      <option value="discrimination">Phân biệt đối xử</option>
                      <option value="other">Khác</option>
                    </select>
                  </label>
                  <label className="filter-label">
                    Mô tả thêm
                    <textarea
                      value={reportDescription}
                      onChange={(e) => setReportDescription(e.target.value)}
                      placeholder="Mô tả ngắn vấn đề bạn gặp phải..."
                      rows={3}
                    />
                  </label>
                  <button
                    type="submit"
                    className="danger"
                    disabled={reporting || !reporterProfile?.fullName || !reporterProfile?.phone}
                    style={{ width: '100%' }}
                  >
                    {reporting ? 'Đang gửi...' : 'Gửi báo cáo'}
                  </button>
                </form>
              )}
            </>
          ) : (
            <Link className="button-link" to="/login" style={{ width: '100%', textAlign: 'center' }}>
              Đăng nhập để ứng tuyển
            </Link>
          )}

          <div style={{ borderTop: '1px solid var(--outline-variant)', paddingTop: 14 }}>
            <p className="muted" style={{ fontSize: '0.8rem' }}>
              Hồ sơ của bạn sẽ được gửi trực tiếp đến nhà tuyển dụng.
            </p>
          </div>
        </aside>
      </div>
    </Shell>
  );
}

// ─── CANDIDATE LAYOUT ────────────────────────────────────────────────────────
function CandidateLayout() {
  const navigate = useNavigate();
  function logout() { clearAuthSession(); navigate('/login'); }

  const navItems = [
    { to: '/candidate', end: true, icon: '📊', label: 'Dashboard' },
    { to: '/candidate/profile', icon: '👤', label: 'Hồ sơ' },
    { to: '/candidate/cvs', icon: '📄', label: 'CV của tôi' },
    { to: '/candidate/saved-jobs', icon: '🔖', label: 'Việc đã lưu' },
    { to: '/candidate/applications', icon: '📋', label: 'Ứng tuyển' },
    { to: '/candidate/ai-interviews', icon: '🤖', label: 'AI Interview' },
    { to: '/candidate/notifications', icon: '🔔', label: 'Thông báo' },
    { to: '/candidate/subscription', icon: '💎', label: 'Gói dịch vụ' },
  ];

  return (
    <div className="candidate-shell">
      <aside className="candidate-nav">
        <Link className="brand" to="/candidate">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.2"/>
            <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
          </svg>
          SJP Candidate
        </Link>

        <div style={{ borderBottom: '1px solid var(--outline-variant)', marginBottom: 8, paddingBottom: 8 }}>
          <span style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--outline)',
            letterSpacing: '0.07em', textTransform: 'uppercase', padding: '0 12px', display: 'block' }}>
            Cổng ứng viên
          </span>
        </div>

        {navItems.map(({ to, end, icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
          >
            <span className="sidebar-link-icon">{icon}</span>
            {label}
          </NavLink>
        ))}

        <div style={{ marginTop: 12, borderTop: '1px solid var(--outline-variant)', paddingTop: 12 }}>
          <NavLink to="/jobs" className="sidebar-link">
            <span className="sidebar-link-icon">🔍</span>
            Tìm việc làm
          </NavLink>
          <button
            className="ghost"
            onClick={logout}
            style={{ width: '100%', justifyContent: 'flex-start', color: 'var(--danger)',
              minHeight: 40, padding: '10px 12px', gap: 10 }}
          >
            <span>🚪</span> Đăng xuất
          </button>
        </div>
      </aside>

      <main className="candidate-main">
        <Outlet />
      </main>
    </div>
  );
}

// ─── CANDIDATE HOME ──────────────────────────────────────────────────────────
function CandidateHome() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);
  const userName = localStorage.getItem('email')?.split('@')[0] || 'bạn';

  useEffect(() => {
    jobService.recommendations()
      .then(setRecommendations)
      .catch(() => setRecommendations([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      {/* Welcome Banner */}
      <motion.div
        className="candidate-welcome-banner"
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: EASE_OUT }}
      >
        <div className="candidate-welcome-text">
          <p className="eyebrow">Bảng điều khiển</p>
          <h1>Xin chào, {userName} 👋</h1>
          <p>Hôm nay là ngày tốt để tìm việc mơ ước của bạn!</p>
        </div>
        <Link to="/jobs" className="button-link candidate-welcome-cta">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          Tìm việc ngay
        </Link>
      </motion.div>

      <div className="metric-grid" style={{ marginTop: 24 }}>
        {[
          { label: 'Việc đã lưu', value: '—', icon: '🔖', to: '/candidate/saved-jobs' },
          { label: 'Đang ứng tuyển', value: '—', icon: '📋', to: '/candidate/applications' },
          { label: 'Phỏng vấn AI', value: '—', icon: '🤖', to: '/candidate/ai-interviews' },
          { label: 'Thông báo mới', value: '—', icon: '🔔', to: '/candidate/notifications' },
        ].map(({ label, value, icon, to }, i) => (
          <motion.div
            key={label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.06 }}
          >
            <Link to={to} style={{ display: 'block', textDecoration: 'none' }}>
              <div className="metric-card metric-card-link">
                <div style={{ fontSize: '1.6rem', marginBottom: 8 }}>{icon}</div>
                <div className="metric-card-label">{label}</div>
                <div className="metric-card-value">{value}</div>
              </div>
            </Link>
          </motion.div>
        ))}
      </div>

      <div style={{ marginTop: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h2 style={{ margin: 0 }}>Việc làm được gợi ý</h2>
          <Link to="/jobs" className="button-link outline" style={{ minHeight: 34, fontSize: '0.82rem' }}>
            Xem tất cả →
          </Link>
        </div>
        {loading ? (
          <div className="job-grid">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="job-card-skeleton">
                <div style={{ display: 'flex', gap: 14 }}>
                  <div className="skeleton" style={{ width: 48, height: 48, borderRadius: 8 }} />
                  <div style={{ flex: 1, display: 'grid', gap: 8 }}>
                    <div className="skeleton" style={{ height: 18, width: '65%' }} />
                    <div className="skeleton" style={{ height: 14, width: '45%' }} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="job-grid">
            {recommendations.slice(0, 4).map((item, i) => (
              <motion.div
                key={item.job.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.05 }}
              >
                <JobCard job={item.job} />
              </motion.div>
            ))}
            {recommendations.length === 0 && (
              <div className="card" style={{ padding: 32, textAlign: 'center' }}>
                <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>💡</div>
                <h3>Chưa có gợi ý việc làm</h3>
                <p className="muted">Hoàn thiện hồ sơ để nhận gợi ý việc làm phù hợp với bạn.</p>
                <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 16 }}>
                  <Link to="/candidate/profile" className="button-link">
                    Cập nhật hồ sơ
                  </Link>
                  <Link to="/jobs" className="button-link outline">
                    Tìm việc thủ công
                  </Link>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Quick Actions */}
      <div style={{ marginTop: 32 }}>
        <h2 style={{ marginBottom: 16 }}>Hành động nhanh</h2>
        <div className="quick-actions-grid">
          {[
            { icon: '👤', title: 'Cập nhật hồ sơ', desc: 'Tăng cơ hội được tuyển dụng', to: '/candidate/profile' },
            { icon: '📄', title: 'Quản lý CV', desc: 'Tải lên hoặc tạo CV mới', to: '/candidate/cvs' },
            { icon: '🤖', title: 'Luyện phỏng vấn AI', desc: 'Chuẩn bị cho buổi phỏng vấn thật', to: '/candidate/ai-interviews' },
            { icon: '💎', title: 'Gói dịch vụ', desc: 'Xem quyền lợi của bạn', to: '/candidate/subscription' },
          ].map(({ icon, title, desc, to }, i) => (
            <motion.div key={to}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.22, ease: EASE_OUT, delay: 0.15 + i * 0.05 }}
            >
              <Link to={to} style={{ display: 'block', textDecoration: 'none' }}>
                <div className="quick-action-card">
                  <span className="quick-action-icon">{icon}</span>
                  <div>
                    <strong>{title}</strong>
                    <p className="muted" style={{ margin: '2px 0 0', fontSize: '0.8rem' }}>{desc}</p>
                  </div>
                </div>
              </Link>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── PROFILE PAGE ────────────────────────────────────────────────────────────
function ProfilePage() {
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [skills, setSkills] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    candidateService.getProfile().then((data) => {
      setProfile(data);
      setSkills(data.skills.join(', '));
    });
  }, []);

  async function save() {
    if (!profile) return;
    const saved = await candidateService.updateProfile({ ...profile, skills: skills.split(',').map((s) => s.trim()).filter(Boolean) });
    setProfile(saved);
    setMessage('Đã lưu hồ sơ thành công!');
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  }

  if (!profile) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto 12px' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <p className="muted">Đang tải hồ sơ...</p>
      </div>
    );
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Hồ sơ Ứng viên</h1>
        <p>Cập nhật thông tin cá nhân để tăng cơ hội được tuyển</p>
      </div>

      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24,
          padding: '0 0 20px', borderBottom: '1px solid var(--outline-variant)' }}>
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'linear-gradient(135deg, var(--primary-soft), var(--primary-softer))',
            border: '2px solid var(--primary-soft)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '1.5rem', fontWeight: 700, color: 'var(--primary)',
            fontFamily: 'Lexend, sans-serif',
          }}>
            {profile.fullName?.slice(0, 1) || '?'}
          </div>
          <div>
            <h3 style={{ margin: 0 }}>{profile.fullName || 'Chưa cập nhật tên'}</h3>
            <span className={`chip ${profile.applyReady ? 'match' : 'warning'}`} style={{ marginTop: 6 }}>
              {profile.applyReady ? '✓ Sẵn sàng ứng tuyển' : '⚠ Cần hoàn thiện hồ sơ'}
            </span>
          </div>
        </div>

        <div className="form-grid two">
          <label>
            Họ tên
            <input
              value={profile.fullName || ''}
              onChange={(e) => setProfile({ ...profile, fullName: e.target.value })}
              placeholder="Nguyễn Văn A"
            />
          </label>
          <label>
            Điện thoại
            <input
              value={profile.phone || ''}
              onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
              placeholder="0901 234 567"
            />
          </label>
          <label>
            Ngày sinh
            <input
              type="date"
              value={profile.dateOfBirth ? profile.dateOfBirth.slice(0, 10) : ''}
              onChange={(e) => setProfile({ ...profile, dateOfBirth: e.target.value || undefined })}
            />
          </label>
          <label>
            Địa điểm
            <input
              value={profile.location || ''}
              onChange={(e) => setProfile({ ...profile, location: e.target.value })}
              placeholder="TP.HCM, Hà Nội..."
            />
          </label>
          <label>
            Kỹ năng
            <input
              value={skills}
              onChange={(e) => setSkills(e.target.value)}
              placeholder="Java, React, SQL (phân cách bằng dấu phẩy)"
            />
          </label>
          <label className="wide">
            Giới thiệu bản thân
            <textarea
              value={profile.bio || ''}
              onChange={(e) => setProfile({ ...profile, bio: e.target.value })}
              placeholder="Mô tả ngắn về kinh nghiệm, mục tiêu nghề nghiệp..."
            />
          </label>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 20, paddingTop: 16,
          borderTop: '1px solid var(--outline-variant)' }}>
          <button onClick={save} disabled={saving}>
            {saving ? 'Đang lưu...' : '💾 Lưu hồ sơ'}
          </button>
          <AnimatePresence>
            {message && (
              <motion.div className="success-panel" variants={scaleIn} initial="initial" animate="animate" exit="exit"
                transition={{ duration: 0.18, ease: EASE_OUT }} style={{ flex: 1 }}>
                {message}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  );
}

// ─── CV PAGE ────────────────────────────────────────────────────────────────
function CvPage() {
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [versions, setVersions] = useState<CvVersion[]>([]);
  const [message, setMessage] = useState('');
  const [uploading, setUploading] = useState(false);

  async function load() {
    setCvs(await candidateService.getCvs());
    setVersions(await candidateService.getCvVersions());
  }

  useEffect(() => { load(); }, []);

  async function upload(file?: File) {
    if (!file) return;
    setUploading(true);
    try {
      await candidateService.uploadCv(file);
      setMessage('✅ Upload CV thành công!');
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setUploading(false);
    }
  }

  async function createVersion() {
    await candidateService.createCvVersion(`CV Builder ${versions.length + 1}`, {});
    await load();
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>CV của tôi</h1>
        <p>Quản lý và tải lên CV để ứng tuyển</p>
      </div>

      {/* Upload area */}
      <div className="card" style={{ marginBottom: 20 }}>
        <h2 style={{ marginBottom: 16 }}>Tải lên CV (PDF)</h2>
        <div style={{
          border: '2px dashed var(--outline-variant)',
          borderRadius: 'var(--radius-card)',
          padding: '32px 24px',
          textAlign: 'center',
          background: 'var(--surface-container)',
          transition: 'border-color 150ms var(--ease-out)',
        }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 8 }}>📁</div>
          <p className="muted" style={{ marginBottom: 12 }}>Chọn file PDF để tải lên</p>
          <label style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            background: 'var(--primary)', color: '#fff', borderRadius: 'var(--radius-control)',
            padding: '8px 18px', cursor: 'pointer', fontWeight: 600, fontSize: '0.875rem',
            transition: 'background-color 150ms var(--ease-out), transform 100ms var(--ease-out)',
          }}>
            {uploading ? 'Đang tải...' : '📎 Chọn file'}
            <input
              type="file"
              accept="application/pdf"
              style={{ display: 'none' }}
              onChange={(e) => upload(e.target.files?.[0])}
              disabled={uploading}
            />
          </label>
        </div>
        <AnimatePresence>
          {message && (
            <motion.div
              className={message.startsWith('✅') ? 'success-panel' : 'error-panel'}
              variants={scaleIn} initial="initial" animate="animate" exit="exit"
              transition={{ duration: 0.18, ease: EASE_OUT }}
              style={{ marginTop: 12 }}
            >
              {message}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* CV list */}
      {cvs.length > 0 && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h2 style={{ marginBottom: 16 }}>CV đã tải lên</h2>
          <div className="data-table">
            <div className="data-table-header" style={{
              gridTemplateColumns: '1fr auto auto auto auto'
            }}>
              <span>Tên file</span>
              <span>Kích thước</span>
              <span>Trạng thái</span>
              <span></span>
              <span></span>
            </div>
            {cvs.map((cv) => (
              <div className="data-row" key={cv.id}
                style={{ gridTemplateColumns: '1fr auto auto auto auto' }}>
                <strong style={{ fontSize: '0.925rem' }}>📄 {cv.originalFileName}</strong>
                <span className="muted">{Math.round(cv.fileSize / 1024)} KB</span>
                <span>
                  {cv.defaultCv
                    ? <span className="chip match">Mặc định</span>
                    : <span className="chip neutral">PDF</span>
                  }
                </span>
                <button className="outline sm"
                  onClick={() => candidateService.setDefaultCv(cv.id).then(load)}>
                  Đặt mặc định
                </button>
                <button className="danger sm"
                  onClick={() => candidateService.deleteCv(cv.id).then(load)}>
                  Xóa
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* CV Builder */}
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <h2 style={{ margin: 0 }}>CV Builder</h2>
          <button className="outline" onClick={createVersion}>+ Tạo CV mới</button>
        </div>
        {versions.length === 0 ? (
          <div className="empty-state">Chưa có CV Builder nào. Tạo CV từ hồ sơ của bạn!</div>
        ) : (
          <div className="data-table">
            {versions.map((version) => (
              <div className="data-row" key={version.id}
                style={{ gridTemplateColumns: '1fr auto auto' }}>
                <strong>📝 {version.title}</strong>
                <span className="chip neutral">{version.templateKey}</span>
                <span className="muted">{new Date(version.updatedAt).toLocaleDateString('vi-VN')}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
}

// ─── SAVED JOBS PAGE ─────────────────────────────────────────────────────────
function SavedJobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    candidateService.getSavedJobs()
      .then(setJobs)
      .finally(() => setLoading(false));
  }, []);

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Việc làm đã lưu</h1>
        <p>Các vị trí bạn đang quan tâm</p>
      </div>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : jobs.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>🔖</div>
          <h3>Chưa có việc làm nào được lưu</h3>
          <p className="muted">Tìm kiếm và lưu các vị trí bạn yêu thích!</p>
          <Link to="/jobs" className="button-link" style={{ marginTop: 16, display: 'inline-flex' }}>
            Tìm việc làm
          </Link>
        </div>
      ) : (
        <div className="job-grid">
          {jobs.map((job, i) => (
            <motion.div key={job.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.05 }}>
              <JobCard job={job} />
            </motion.div>
          ))}
        </div>
      )}
    </motion.div>
  );
}

// ─── APPLICATIONS PAGE ───────────────────────────────────────────────────────
function ApplicationsPage() {
  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    candidateService.getApplications()
      .then(setApplications)
      .finally(() => setLoading(false));
  }, []);

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Hồ sơ ứng tuyển</h1>
        <p>Theo dõi trạng thái các đơn ứng tuyển của bạn</p>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : applications.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>📋</div>
          <h3>Chưa có đơn ứng tuyển nào</h3>
          <p className="muted">Bắt đầu ứng tuyển vào các vị trí phù hợp!</p>
          <Link to="/jobs" className="button-link" style={{ marginTop: 16, display: 'inline-flex' }}>
            Tìm việc làm
          </Link>
        </div>
      ) : (
        <div className="data-table">
          <div className="data-table-header"
            style={{ gridTemplateColumns: '1fr auto auto auto' }}>
            <span>Vị trí</span>
            <span>Công ty</span>
            <span>Trạng thái</span>
            <span>Ngày nộp</span>
          </div>
          {applications.map((application, i) => (
            <motion.div key={application.id}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.04 }}>
              <Link
                className="data-row clickable"
                to={`/candidate/applications/${application.id}`}
                style={{ gridTemplateColumns: '1fr auto auto auto', display: 'grid',
                  textDecoration: 'none', color: 'inherit' }}
              >
                <strong>{application.job.title}</strong>
                <span className="muted">{application.job.company.name}</span>
                <span className={`chip ${statusColors[application.status] || ''}`}>
                  {statusLabels[application.status] || application.status}
                </span>
                <span className="muted">
                  {new Date(application.submittedAt).toLocaleDateString('vi-VN')}
                </span>
              </Link>
            </motion.div>
          ))}
        </div>
      )}
    </motion.div>
  );
}

// ─── APPLICATION DETAIL PAGE ─────────────────────────────────────────────────
function ApplicationDetailPage() {
  const { id } = useParams();
  const [application, setApplication] = useState<CandidateApplication | null>(null);

  useEffect(() => {
    if (id) candidateService.getApplication(id).then(setApplication);
  }, [id]);

  if (!application) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
      </div>
    );
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div style={{ marginBottom: 20 }}>
        <Link to="/candidate/applications" style={{ color: 'var(--primary)', fontSize: '0.875rem', fontWeight: 600 }}>
          ← Quay lại danh sách
        </Link>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <p className="eyebrow">{application.job.company.name}</p>
        <h1 style={{ fontSize: '1.5rem', margin: '8px 0 12px' }}>{application.job.title}</h1>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <span className={`chip ${statusColors[application.status] || ''}`}>
            {statusLabels[application.status] || application.status}
          </span>
          <span className="chip neutral">
            CV: {application.cv?.originalFileName || application.cvVersion?.title || 'Không có'}
          </span>
        </div>
      </div>

      {application.interviews && application.interviews.length > 0 && (
        <div className="card" style={{ marginBottom: 20, borderLeft: '4px solid #b45309' }}>
          <h2 style={{ marginBottom: 12 }}>Lịch Phỏng Vấn</h2>
          {application.interviews.map(interview => (
            <div key={interview.id} style={{ marginBottom: 16, padding: 12, background: '#f8fafc', borderRadius: 8 }}>
              <p style={{ margin: '4px 0' }}><strong>Thời gian:</strong> {new Date(interview.scheduledAt).toLocaleString('vi-VN')}</p>
              <p style={{ margin: '4px 0' }}><strong>Địa điểm:</strong> {interview.location || 'Chưa cập nhật'}</p>
              {interview.meetingLink && <p style={{ margin: '4px 0' }}><strong>Link họp:</strong> <a href={interview.meetingLink} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>{interview.meetingLink}</a></p>}
              {interview.note && <p style={{ margin: '4px 0' }}><strong>Ghi chú:</strong> {interview.note}</p>}
              
              <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #e2e8f0' }}>
                <p style={{ margin: '4px 0', fontSize: '0.9rem' }}>
                  <strong>Phản hồi của bạn:</strong>{' '}
                  {interview.candidateResponse === 'confirmed' ? <span style={{ color: '#047857' }}>Đã xác nhận tham gia</span>
                   : interview.candidateResponse === 'request_reschedule' ? <span style={{ color: '#b45309' }}>Đã yêu cầu đổi lịch</span>
                   : interview.candidateResponse === 'declined' ? <span style={{ color: '#b91c1c' }}>Từ chối tham gia</span>
                   : 'Chưa phản hồi'}
                </p>

                {interview.candidateResponse === 'request_reschedule' && interview.employerRescheduleResponse && (
                  <p style={{ margin: '4px 0', fontSize: '0.9rem', color: '#4338ca' }}>
                    <strong>Phản hồi từ Nhà tuyển dụng:</strong> {interview.employerRescheduleResponse === 'accept_reschedule' ? 'Đã đồng ý đổi lịch' : 'Không đồng ý đổi lịch'}. 
                    {interview.employerRescheduleNote && ` Lời nhắn: ${interview.employerRescheduleNote}`}
                  </p>
                )}

                {interview.candidateResponse === 'request_reschedule' && interview.employerRescheduleResponse === 'reject_reschedule' && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                    <button onClick={() => candidateService.respondToInterview(interview.id, 'confirmed').then(() => window.location.reload())} style={{ background: '#047857', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Đồng ý lịch cũ</button>
                    <button onClick={() => candidateService.respondToInterview(interview.id, 'declined').then(() => window.location.reload())} style={{ background: '#b91c1c', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Hủy phỏng vấn</button>
                  </div>
                )}

                {(!interview.candidateResponse || interview.candidateResponse === 'pending') && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                    <button onClick={() => candidateService.respondToInterview(interview.id, 'confirmed').then(() => window.location.reload())} style={{ background: '#047857', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Đồng ý tham gia</button>
                    <button onClick={() => {
                      const note = prompt('Nhập lý do đổi lịch và thời gian đề xuất:');
                      if (note) candidateService.respondToInterview(interview.id, 'request_reschedule', note).then(() => window.location.reload());
                    }} style={{ background: '#f59e0b', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Xin đổi lịch</button>
                    <button onClick={() => candidateService.respondToInterview(interview.id, 'declined').then(() => window.location.reload())} style={{ background: '#b91c1c', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Từ chối</button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {application.jobOffer && (
        <div className="card" style={{ marginBottom: 20, borderLeft: '4px solid #047857' }}>
          <h2 style={{ marginBottom: 12 }}>🎉 Đề xuất công việc (Job Offer)</h2>
          <div style={{ padding: 12, background: '#f8fafc', borderRadius: 8 }}>
            <p style={{ margin: '4px 0' }}><strong>Chức danh:</strong> {application.jobOffer.positionTitle}</p>
            <p style={{ margin: '4px 0' }}><strong>Mức lương:</strong> {application.jobOffer.salary ? `${application.jobOffer.salary.toLocaleString()} ${application.jobOffer.salaryCurrency} (${application.jobOffer.salaryType})` : 'Thỏa thuận'}</p>
            {application.jobOffer.startDate && <p style={{ margin: '4px 0' }}><strong>Ngày bắt đầu:</strong> {application.jobOffer.startDate}</p>}
            {application.jobOffer.workingLocation && <p style={{ margin: '4px 0' }}><strong>Nơi làm việc:</strong> {application.jobOffer.workingLocation}</p>}
            {application.jobOffer.benefits && <p style={{ margin: '4px 0', whiteSpace: 'pre-wrap' }}><strong>Phúc lợi:</strong> {application.jobOffer.benefits}</p>}
            {application.jobOffer.offerLetterUrl && <p style={{ margin: '4px 0' }}><strong>Link Offer Letter:</strong> <a href={application.jobOffer.offerLetterUrl} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>Xem chi tiết đính kèm</a></p>}
            {application.jobOffer.employerNote && <p style={{ margin: '4px 0' }}><strong>Lời nhắn từ Nhà tuyển dụng:</strong> {application.jobOffer.employerNote}</p>}
            
            <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #e2e8f0' }}>
              <p style={{ margin: '4px 0', fontSize: '0.9rem' }}>
                <strong>Phản hồi của bạn:</strong>{' '}
                {application.jobOffer.status === 'sent' ? 'Chưa phản hồi' : 
                 application.jobOffer.status === 'accepted' ? <span style={{ color: '#047857' }}>Đã chấp nhận Offer</span> : 
                 application.jobOffer.status === 'rejected' ? <span style={{ color: '#b45309' }}>Đã từ chối Offer (Đang chờ phản hồi từ NTD)</span> : 
                 application.jobOffer.status === 'employer_declined_negotiation' ? <span style={{ color: '#b91c1c' }}>NTD từ chối thay đổi Offer</span> :
                 application.jobOffer.status === 'withdrawn_by_candidate' ? <span style={{ color: '#b91c1c' }}>Bạn đã hủy bỏ Offer</span> :
                 application.jobOffer.status}
              </p>
              {application.jobOffer.status === 'sent' && (
                <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                  <button onClick={() => candidateService.respondToOffer(application.jobOffer!.id, true).then(() => window.location.reload())} style={{ background: '#047857', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Chấp nhận Offer</button>
                  <button onClick={() => {
                    const note = prompt('Nhập lý do từ chối và đề xuất thay đổi (Ví dụ: Tôi muốn lương 20tr):');
                    if (note !== null) {
                        candidateService.respondToOffer(application.jobOffer!.id, false, note || '').then(() => window.location.reload());
                    }
                  }} style={{ background: '#b91c1c', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Từ chối Offer / Đề xuất sửa đổi</button>
                </div>
              )}
              {application.jobOffer.status === 'employer_declined_negotiation' && (
                <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                  <button onClick={() => candidateService.finalRespondToOffer(application.jobOffer!.id, true).then(() => window.location.reload())} style={{ background: '#047857', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Chấp nhận Offer cũ</button>
                  <button onClick={() => {
                    if (window.confirm('Bạn có chắc chắn muốn hủy bỏ toàn bộ Job Offer này không?')) {
                        candidateService.finalRespondToOffer(application.jobOffer!.id, false).then(() => window.location.reload());
                    }
                  }} style={{ background: '#b91c1c', color: '#fff', padding: '6px 12px', borderRadius: 6, border: 'none', cursor: 'pointer', fontWeight: 600 }}>Hủy bỏ hoàn toàn</button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <h2 style={{ marginBottom: 0 }}>Lịch sử trạng thái</h2>
        <div className="timeline">
          {application.timeline.map((item, i) => (
            <motion.div key={item.id} className="timeline-item"
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.06 }}>
              <strong className={`chip ${statusColors[item.toStatus] || ''}`} style={{ display: 'inline-flex', marginBottom: 8 }}>
                {statusLabels[item.toStatus] || item.toStatus}
              </strong>
              <span className="timeline-item-date">
                {new Date(item.createdAt).toLocaleString('vi-VN')}
              </span>
              {item.publicNote && (
                <p style={{ margin: '6px 0 0', color: 'var(--on-muted)', fontSize: '0.875rem' }}>
                  {item.publicNote}
                </p>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </motion.div>
  );
}

// ─── NOTIFICATIONS PAGE ──────────────────────────────────────────────────────
function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    const data = await candidateService.getNotifications();
    setItems(data);
    setLoading(false);
  }

  useEffect(() => { load(); }, []);

  const getIcon = (type: string) => {
    switch (type) {
      case 'JOB_UPDATED': return '📝';
      case 'APPLICATION_STATUS_CHANGED': return '🔄';
      case 'JOB_OFFER_SENT': return '🎉';
      case 'INTERVIEW_SCHEDULED': return '📅';
      default: return '🔔';
    }
  };

  const getNotificationLink = (item: NotificationItem) => {
    if (item.relatedEntityType === 'JOB' && item.relatedEntityId) {
      return `/jobs/${item.relatedEntityId}`;
    }
    if (item.relatedEntityType === 'APPLICATION' && item.relatedEntityId) {
      return `/candidate/applications/${item.relatedEntityId}`;
    }
    return null;
  };

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Thông báo</h1>
        <p>Cập nhật từ nhà tuyển dụng và hệ thống</p>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : items.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>📭</div>
          <h3>Không có thông báo mới</h3>
          <p className="muted">Bạn sẽ nhận thông báo khi có cập nhật từ nhà tuyển dụng.</p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {items.map((item, i) => {
            const link = getNotificationLink(item);
            return (
              <motion.div key={item.id} 
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2, ease: EASE_OUT, delay: i * 0.04 }}
                style={{ 
                  display: 'flex', 
                  padding: '16px 20px', 
                  borderBottom: i < items.length - 1 ? '1px solid var(--outline-variant)' : 'none',
                  background: item.read ? 'transparent' : 'var(--primary-softer)',
                  alignItems: 'center',
                  gap: 16
                }}>
                <div style={{ fontSize: '1.5rem', minWidth: 40, textAlign: 'center' }}>
                  {getIcon(item.type)}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <strong style={{ display: 'block', color: 'var(--on-surface)' }}>{item.title}</strong>
                    {!item.read && <span className="chip primary sm">Mới</span>}
                  </div>
                  <p style={{ margin: 0, color: 'var(--on-muted)', fontSize: '0.9rem', lineHeight: 1.4 }}>
                    {item.message}
                  </p>
                  <span style={{ fontSize: '0.75rem', color: 'var(--outline)', marginTop: 8, display: 'block' }}>
                    {new Date(item.createdAt).toLocaleString('vi-VN')}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8, flexDirection: 'column', alignItems: 'flex-end' }}>
                  {!item.read && (
                    <button className="outline sm"
                      onClick={() => candidateService.markNotificationRead(item.id).then(load)}>
                      Đánh dấu đọc
                    </button>
                  )}
                  {link && (
                    <Link to={link} className="button-link sm">
                      Xem chi tiết
                    </Link>
                  )}
                </div>
              </motion.div>
            );
          })}
        </div>
      )}
    </motion.div>
  );
}

// ─── SUBSCRIPTION PAGE ───────────────────────────────────────────────────────
function SubscriptionPage() {
  const [subscription, setSubscription] = useState<SubscriptionView | null>(null);

  useEffect(() => {
    candidateService.getSubscription().then(setSubscription);
  }, []);

  if (!subscription) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
      </div>
    );
  }

  const statusValue = (subscription.status || '').toLowerCase();
  const isActivePaid = statusValue === 'active' && subscription.planCode !== 'FREE' && subscription.planName?.toLowerCase() !== 'free';
  const statusLabel =
    statusValue === 'active' ? (isActivePaid ? 'Đang sử dụng' : 'Gói miễn phí')
      : statusValue === 'pending' ? 'Chờ kích hoạt'
        : statusValue === 'expired' ? 'Hết hạn'
          : statusValue === 'cancelled' ? 'Đã hủy'
            : subscription.status || '—';
  const statusStyle =
    statusValue === 'active'
      ? { background: isActivePaid ? '#dcfce7' : '#f3f4f6', color: isActivePaid ? '#166534' : '#374151' }
      : statusValue === 'pending'
        ? { background: '#fef3c7', color: '#92400e' }
        : ['expired', 'cancelled'].includes(statusValue)
          ? { background: '#fee2e2', color: '#991b1b' }
          : { background: '#f3f4f6', color: '#374151' };

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Gói dịch vụ</h1>
        <p>Xem gói bạn đang dùng và nâng cấp khi cần</p>
      </div>

      <article
        className="card"
        style={{
          marginBottom: 20,
          border: isActivePaid ? '2px solid #16a34a' : undefined,
          background: isActivePaid ? 'linear-gradient(180deg, #f0fdf4 0%, #fff 55%)' : undefined,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <div>
            <p className="muted" style={{ margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em', fontSize: '0.8rem' }}>
              Gói đang dùng
            </p>
            <h2 style={{ margin: '6px 0 0', fontSize: '1.6rem' }}>{subscription.planName}</h2>
          </div>
          <span
            style={{
              display: 'inline-block',
              padding: '6px 12px',
              borderRadius: 999,
              fontWeight: 600,
              fontSize: '0.85rem',
              ...statusStyle,
            }}
          >
            {statusLabel}
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 14, marginTop: 18 }}>
          <div>
            <div className="muted" style={{ fontSize: '0.85rem' }}>Giá gói</div>
            <strong>
              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(subscription.price || 0)}
            </strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: '0.85rem' }}>Ngày bắt đầu</div>
            <strong>{subscription.startedAt ? new Date(subscription.startedAt).toLocaleString('vi-VN') : '—'}</strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: '0.85rem' }}>Ngày hết hạn</div>
            <strong>{subscription.expiresAt ? new Date(subscription.expiresAt).toLocaleString('vi-VN') : '—'}</strong>
          </div>
        </div>

        {subscription.benefits.length > 0 && (
          <div style={{ marginTop: 18 }}>
            <div className="muted" style={{ marginBottom: 8, fontSize: '0.85rem' }}>Quyền lợi đang có</div>
            <div className="chip-row">
              {subscription.benefits.map((benefit) => (
                <span key={benefit} className="chip match">✓ {benefit}</span>
              ))}
            </div>
          </div>
        )}

        <div style={{ marginTop: 20 }}>
          <Link to="/candidate/subscription/plans" className="button-link">
            {isActivePaid ? 'Đổi / gia hạn gói' : 'Mua gói'}
          </Link>
        </div>
      </article>

      <div className="metric-grid">
        {[
          { label: 'CV đã tải', value: subscription.cvCount, icon: '📄' },
          { label: 'Việc đã lưu', value: subscription.savedJobsCount, icon: '🔖' },
          { label: 'Thông báo chưa đọc', value: subscription.unreadNotificationsCount, icon: '🔔' },
        ].map(({ label, value, icon }, i) => (
          <motion.div key={label} className="metric-card"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.06 }}>
            <div style={{ fontSize: '1.5rem', marginBottom: 6 }}>{icon}</div>
            <div className="metric-card-label">{label}</div>
            <div className="metric-card-value" style={{ fontSize: '1.4rem' }}>{value}</div>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}

// ─── EMPLOYER LAYOUT ──────────────────────────────────────────────────────────
function EmployerLayout() {
  const navigate = useNavigate();
  const [companyOpen, setCompanyOpen] = useState(false);

  function logout() { clearAuthSession(); navigate('/login'); }

  return (
    <div className="employer-shell">
      <aside className="employer-nav">
        <Link className="brand" to="/employer">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="var(--primary)" strokeWidth="2" fill="var(--primary-softer)"/>
            <polyline points="9 22 9 12 15 12 15 22" stroke="var(--primary)" strokeWidth="2" fill="none"/>
          </svg>
          SJP Employer
        </Link>

        <div style={{ borderBottom: '1px solid var(--outline-variant)', marginBottom: 8, paddingBottom: 8 }}>
          <span style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--outline)',
            letterSpacing: '0.07em', textTransform: 'uppercase', padding: '0 12px', display: 'block' }}>
            Cổng nhà tuyển dụng
          </span>
        </div>

        <NavLink to="/employer" end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <span className="sidebar-link-icon">📊</span>
          Dashboard
        </NavLink>

        <NavLink to="/employer/jobs" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <span className="sidebar-link-icon">💼</span>
          Quản lý Việc làm
        </NavLink>

        <NavLink to="/employer/applications" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <span className="sidebar-link-icon">👥</span>
          Quản lý Ứng viên
        </NavLink>

        <NavLink to="/employer/subscription" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
          <span className="sidebar-link-icon">💎</span>
          Gói dịch vụ
        </NavLink>

        {/* Company dropdown */}
        <div className="nav-dropdown">
          <button
            type="button"
            className="nav-dropdown-trigger"
            onClick={() => setCompanyOpen(!companyOpen)}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span className="sidebar-link-icon">🏢</span>
              Công ty
            </span>
            <span className={`arrow ${companyOpen ? 'open' : ''}`}>▾</span>
          </button>

          <AnimatePresence>
            {companyOpen && (
              <motion.div
                className="nav-dropdown-items"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.2, ease: EASE_OUT }}
              >
                <NavLink to="/employer/company-profile"
                  className={({ isActive }) => `sub-nav-item ${isActive ? 'active' : ''}`}>
                  Hồ sơ Công ty
                </NavLink>
                <NavLink to="/employer/locations"
                  className={({ isActive }) => `sub-nav-item ${isActive ? 'active' : ''}`}>
                  Địa điểm làm việc
                </NavLink>
                <NavLink to="/employer/verification"
                  className={({ isActive }) => `sub-nav-item ${isActive ? 'active' : ''}`}>
                  Xác thực pháp lý
                </NavLink>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        <div style={{ marginTop: 'auto', borderTop: '1px solid var(--outline-variant)', paddingTop: 12 }}>
          <NavLink to="/jobs" className="sidebar-link">
            <span className="sidebar-link-icon">🔍</span>
            Xem tin tuyển dụng
          </NavLink>
          <button
            className="ghost"
            onClick={logout}
            style={{ width: '100%', justifyContent: 'flex-start', color: 'var(--danger)',
              minHeight: 40, padding: '10px 12px', gap: 10 }}
          >
            <span>🚪</span> Đăng xuất
          </button>
        </div>
      </aside>

      <main className="employer-main">
        <Outlet />
      </main>
    </div>
  );
}

// ─── EMPLOYER DASHBOARD ──────────────────────────────────────────────────────
function EmployerDashboard() {
  const features = [
    {
      icon: '📢',
      title: 'Quản lý & Đăng tin tuyển dụng',
      desc: 'Tạo mới các vị trí tuyển dụng, thiết lập mức lương, quyền lợi và theo dõi trạng thái các tin đăng.',
      to: '/employer/jobs',
      label: 'Quản lý việc làm →',
      note: 'Yêu cầu công ty đã xác thực',
    },
    {
      icon: '🏢',
      title: 'Hồ sơ Công ty & Logo',
      desc: 'Cập nhật thông tin giới thiệu, địa điểm trụ sở và tải lên logo chính thức của doanh nghiệp.',
      to: '/employer/company-profile',
      label: 'Hồ sơ công ty →',
      variant: 'outline',
    },
    {
      icon: '⚖️',
      title: 'Xác thực pháp lý',
      desc: 'Tải lên giấy phép kinh doanh và các tài liệu minh chứng để được Admin phê duyệt tài khoản hợp lệ.',
      to: '/employer/verification',
      label: 'Xác thực ngay →',
    },
  ];

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Employer Dashboard</h1>
        <p>Chào mừng đến với Smart Recruitment Portal. Quản lý hồ sơ công ty và tin tuyển dụng.</p>
      </div>

      <div className="employer-cards">
        {features.map(({ icon, title, desc, to, label, variant, note }, i) => (
          <motion.div
            key={to}
            className="employer-feature-card"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.07 }}
          >
            <div>
              <div className="employer-feature-icon">{icon}</div>
              <h3>{title}</h3>
              <p>{desc}</p>
              {note && <p style={{ color: 'var(--outline)', fontSize: '0.8rem', marginTop: 4 }}>* {note}</p>}
            </div>
            <Link
              to={to}
              className={`button-link ${variant || ''}`}
              style={{ width: '100%', marginTop: 12 }}
            >
              {label}
            </Link>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}

// ─── AI INTERVIEW PAGE ───────────────────────────────────────────────────────
function AiInterviewPage() {
  const [config, setConfig] = useState<AiInterviewConfig | null>(null);
  const [applications, setApplications] = useState<AiInterviewEligibleApplication[]>([]);
  const [questionSets, setQuestionSets] = useState<AiInterviewQuestionSet[]>([]);
  const [sessions, setSessions] = useState<AiInterviewSession[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [activeTab, setActiveTab] = useState<'application' | 'practice'>('application');
  const [questionMode, setQuestionMode] = useState<'ai_generated' | 'fixed'>('ai_generated');
  const [selectedQuestionSetId, setSelectedQuestionSetId] = useState('');
  const [targetRole, setTargetRole] = useState('Java Backend Developer');
  const [skills, setSkills] = useState('Spring Boot, PostgreSQL');
  const [jobId, setJobId] = useState('');
  const [selectedSession, setSelectedSession] = useState<AiInterviewSession | null>(null);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    const configData = await aiInterviewService.configStatus();
    setConfig(configData);
    if (!configData.enabled) {
      setApplications([]);
      setQuestionSets([]);
      setSessions([]);
      setJobs([]);
      return;
    }
    const sessionData = await aiInterviewService.sessions();
    setSessions(sessionData);
    if (configData.enabled) {
      const [applicationData, jobsData, questionSetData] = await Promise.all([
        aiInterviewService.eligibleApplications(),
        jobService.getAll({ sort: 'newest' }, 0, 20).then((result) => result.content),
        aiInterviewService.questionSets(),
      ]);
      setApplications(applicationData);
      setJobs(jobsData);
      setQuestionSets(questionSetData);
      setSelectedQuestionSetId((current) => current || questionSetData[0]?.id || '');
    }
  }

  useEffect(() => {
    load().catch((err) => setMessage(readError(err)));
  }, []);

  async function createFromApplication(applicationId: string) {
    setLoading(true);
    setMessage('');
    try {
      const session = await aiInterviewService.createApplicationSession(applicationId);
      setSelectedSession(session);
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setLoading(false);
    }
  }

  async function createPractice(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    try {
      const skillList = skills.split(',').map((item) => item.trim()).filter(Boolean);
      if (questionMode === 'fixed' && !selectedQuestionSetId) {
        setMessage('Hay chon mot bo cau hoi co san truoc khi tao practice session.');
        setLoading(false);
        return;
      }
      const session = await aiInterviewService.createPracticeSession(
        targetRole,
        skillList,
        jobId || undefined,
        questionMode === 'fixed' ? selectedQuestionSetId : undefined,
      );
      setSelectedSession(session);
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setLoading(false);
    }
  }

  async function openSession(id: string) {
    setSelectedSession(await aiInterviewService.getSession(id));
  }

  async function deleteSession(id: string) {
    await aiInterviewService.deleteSession(id);
    if (selectedSession?.id === id) setSelectedSession(null);
    await load();
  }

  if (!config) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <p className="muted" style={{ marginTop: 12 }}>Đang tải AI Interview...</p>
      </div>
    );
  }

  return (
    <motion.div className="ai-page" variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="ai-heading">
        <div>
          <p className="eyebrow">Candidate Practice</p>
          <h1>AI Interview</h1>
          <p className="muted">AI feedback chỉ dùng để luyện tập, không phải quyết định tuyển dụng.</p>
        </div>
        <span className="chip">{config.questionCount} câu / session</span>
      </div>

      {!config.enabled && (
        <div className="notice-panel" role="status">
          <strong>AI Interview chưa sẵn sàng</strong>
          <p style={{ margin: '4px 0 0' }}>{config.message || 'AI Interview chưa được cấu hình.'}</p>
        </div>
      )}

      {config.enabled && selectedSession && (
        <AiInterviewRoom
          config={config}
          session={selectedSession}
          onSessionChange={setSelectedSession}
          onBack={async () => { setSelectedSession(null); await load(); }}
        />
      )}

      {config.enabled && !selectedSession && (
        <div className="ai-layout">
          <div className="card">
            <div className="segmented" role="tablist" aria-label="Chế độ tạo phỏng vấn AI">
              <button
                type="button"
                className={activeTab === 'application' ? 'active' : ''}
                onClick={() => setActiveTab('application')}
              >
                📋 Theo application
              </button>
              <button
                type="button"
                className={activeTab === 'practice' ? 'active' : ''}
                onClick={() => setActiveTab('practice')}
              >
                🎯 Practice tự do
              </button>
            </div>

            <AnimatePresence mode="wait">
              {activeTab === 'application' ? (
                <motion.div key="application" className="table-list ai-table"
                  variants={fadeUp} initial="initial" animate="animate" exit="exit"
                  transition={{ duration: 0.18, ease: EASE_OUT }}>
                  {applications.length === 0 && (
                    <div className="empty-state">Chưa có application hợp lệ để luyện phỏng vấn.</div>
                  )}
                  {applications.map((application) => (
                    <div className="table-row" key={application.id}
                      style={{ gridTemplateColumns: 'minmax(200px, 2fr) minmax(150px, 1.5fr) 120px auto' }}>
                      <strong>{application.job.title}</strong>
                      <span className="muted">{application.job.company.name}</span>
                      <span className={`chip ${statusColors[application.status] || ''}`}>
                        {statusLabels[application.status] || application.status}
                      </span>
                      <button type="button" disabled={loading} onClick={() => createFromApplication(application.id)}>
                        Bắt đầu
                      </button>
                    </div>
                  ))}
                </motion.div>
              ) : (
                <motion.form key="practice" className="form-grid" onSubmit={createPractice}
                  variants={fadeUp} initial="initial" animate="animate" exit="exit"
                  transition={{ duration: 0.18, ease: EASE_OUT }}>
                  <div className="segmented" role="tablist" aria-label="Che do cau hoi practice">
                    <button
                      type="button"
                      className={questionMode === 'ai_generated' ? 'active' : ''}
                      onClick={() => setQuestionMode('ai_generated')}
                    >
                      AI tu tao cau hoi
                    </button>
                    <button
                      type="button"
                      className={questionMode === 'fixed' ? 'active' : ''}
                      onClick={() => setQuestionMode('fixed')}
                    >
                      Bo cau hoi co san
                    </button>
                  </div>
                  {questionMode === 'fixed' ? (
                    <label>
                      Bo cau hoi test
                      <select
                        required
                        value={selectedQuestionSetId}
                        onChange={(event) => setSelectedQuestionSetId(event.target.value)}
                      >
                        {questionSets.length === 0 ? (
                          <option value="">Chua co bo cau hoi active</option>
                        ) : null}
                        {questionSets.map((questionSet) => (
                          <option value={questionSet.id} key={questionSet.id}>
                            {questionSet.title} ({questionSet.questionCount} cau)
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : null}
                  <label>
                    Target role
                    <input required value={targetRole} onChange={(event) => setTargetRole(event.target.value)} />
                  </label>
                  <label>
                    Skills
                    <input required value={skills} onChange={(event) => setSkills(event.target.value)}
                      placeholder="Spring Boot, PostgreSQL" />
                  </label>
                  <label>
                    Chọn job (tùy chọn)
                    <select value={jobId} onChange={(event) => setJobId(event.target.value)}>
                      <option value="">Không chọn job</option>
                      {jobs.map((job) => <option value={job.id} key={job.id}>{job.title}</option>)}
                    </select>
                  </label>
                  <button type="submit" disabled={loading}>
                    {loading ? 'Đang tạo...' : '🚀 Tạo practice session'}
                  </button>
                </motion.form>
              )}
            </AnimatePresence>

            <AnimatePresence>
              {message && (
                <motion.div className="error-panel" style={{ marginTop: 12 }}
                  variants={scaleIn} initial="initial" animate="animate" exit="exit"
                  transition={{ duration: 0.18, ease: EASE_OUT }} role="alert">
                  {message}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <div className="card">
            <h2>Lịch sử gần đây</h2>
            <div className="session-list">
              {sessions.length === 0 && (
                <div className="empty-state">Chưa có phiên phỏng vấn nào.</div>
              )}
              {sessions.map((session) => (
                <article className="session-row" key={session.id}>
                  <div>
                    <strong>{session.title}</strong>
                    <p>
                      {session.contextType === 'application' ? 'Theo application' : 'Practice tự do'}
                      {' - '}{session.status}
                    </p>
                  </div>
                  <div className="button-row">
                    <button type="button" className="outline" onClick={() => openSession(session.id)}>
                      {session.status === 'completed' ? 'Xem lại' : 'Resume'}
                    </button>
                    <button type="button" className="danger" onClick={() => deleteSession(session.id)}>Ẩn</button>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </div>
      )}
    </motion.div>
  );
}

// ─── AI INTERVIEW ROOM ───────────────────────────────────────────────────────
function AiInterviewRoom({
  config,
  session,
  onSessionChange,
  onBack,
}: {
  config: AiInterviewConfig;
  session: AiInterviewSession;
  onSessionChange: (session: AiInterviewSession) => void;
  onBack: () => void;
}) {
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | undefined>();
  const recordingStartedAtRef = useRef(0);
  const [isRecording, setIsRecording] = useState(false);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [audioDurationSeconds, setAudioDurationSeconds] = useState(0);
  const [transcript, setTranscript] = useState('');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const currentQuestion = findCurrentQuestion(session);

  useEffect(() => {
    setAudioFile(null);
    setAudioDurationSeconds(0);
    setTranscript(currentQuestion?.answer?.answeredAt ? '' : currentQuestion?.answer?.transcript || '');
    setError('');
    setBusy('');
  }, [currentQuestion?.answer?.answeredAt, currentQuestion?.answer?.transcript, currentQuestion?.id]);

  async function refreshSession() {
    onSessionChange(await aiInterviewService.getSession(session.id));
  }

  async function startRecording() {
    setError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;
      chunksRef.current = [];
      const recorder = new MediaRecorder(stream);
      mediaRecorderRef.current = recorder;
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' });
        setAudioFile(new File([blob], `answer-${Date.now()}.webm`, { type: blob.type || 'audio/webm' }));
        setAudioDurationSeconds(Math.max(1, (Date.now() - recordingStartedAtRef.current) / 1000));
        stream.getTracks().forEach((track) => track.stop());
        mediaStreamRef.current = null;
        window.clearTimeout(timerRef.current);
      };
      recorder.start();
      recordingStartedAtRef.current = Date.now();
      setIsRecording(true);
      timerRef.current = window.setTimeout(() => stopRecording(), config.audioMaxSeconds * 1000);
    } catch {
      setError('Không thể truy cập microphone. Vui lòng kiểm tra quyền trình duyệt.');
    }
  }

  function stopRecording() {
    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    setIsRecording(false);
  }

  async function transcribe() {
    if (!audioFile || !currentQuestion) return;
    setBusy('Đang chuyển giọng nói thành transcript...');
    setError('');
    try {
      const result = await aiInterviewService.uploadAudio(session.id, audioFile, audioDurationSeconds);
      setTranscript(result.transcript);
      await refreshSession();
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function submitAnswer(answerText = transcript) {
    if (!currentQuestion || !answerText.trim()) return;
    setBusy('Đang lưu câu trả lời...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.submitAnswer(session.id, currentQuestion.id, answerText.trim()));
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function confirmVoiceAnswer(answerText: string) {
    if (!currentQuestion || !answerText.trim()) return;
    setError('');
    try {
      onSessionChange(await aiInterviewService.confirmAnswer(
        session.id,
        currentQuestion.id,
        answerText.trim(),
      ));
    } catch (err) {
      const message = readError(err);
      setError(message);
      await refreshSession().catch(() => undefined);
      throw new Error(message);
    }
  }

  async function finishInterview() {
    voice.stop();
    if (isRecording) stopRecording();
    setBusy('Đang chấm điểm và tạo nhận xét cho toàn bộ buổi phỏng vấn...');
    setError('');
    try {
      const currentAnswer = currentQuestion && transcript.trim()
        ? { questionId: currentQuestion.id, transcript: transcript.trim() }
        : undefined;
      onSessionChange(await aiInterviewService.finishInterview(session.id, currentAnswer));
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function skipQuestion() {
    if (!currentQuestion) return;
    setBusy('Đang bỏ qua câu hỏi...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.skipQuestion(session.id, currentQuestion.id));
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  async function retryFeedback(question: AiInterviewQuestion) {
    setBusy('Đang thử lại feedback...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.retryFeedback(session.id, question.id));
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function retrySummary() {
    setBusy('Đang tạo lại tổng kết...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.retrySummary(session.id));
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  const createSpeechUrl = useCallback(async (text: string) => {
    if (config.voiceProvider !== 'shopaikey_tts') return undefined;
    const ticket = await aiInterviewService.createSpeechTicket(session.id, text);
    return ticket.streamUrl;
  }, [config.voiceProvider, session.id]);

  const voice = useVoiceConversation({
    questionId: currentQuestion?.id,
    questionText: currentQuestion?.content,
    initialTranscript: currentQuestion?.answer?.transcript || '',
    silenceMs: config.voiceSilenceMs || 3000,
    confirmationSilenceMs: config.voiceConfirmationSilenceMs || 3000,
    unclearConfirmationDelayMs: config.voiceUnclearConfirmationDelayMs || 1200,
    disabled: Boolean(busy),
    onTranscript: setTranscript,
    onConfirm: confirmVoiceAnswer,
    onSpeechUrl: config.voiceProvider === 'shopaikey_tts' ? createSpeechUrl : undefined,
  });

  useEffect(() => () => {
    window.clearTimeout(timerRef.current);
    if (mediaRecorderRef.current?.state !== 'inactive') mediaRecorderRef.current?.stop();
    mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
  }, []);

  if (session.status === 'completed') {
    return (
      <div className="interview-room">
        <button type="button" className="outline" onClick={onBack}>← Quay lại danh sách</button>
        <div className="result-panel">
          <p className="eyebrow">Kết quả luyện tập</p>
          <h2>{session.title}</h2>
          <strong className="score-display">
            {Math.round(Number(session.summary?.overallScore || session.overallScore || 0))}%
          </strong>
          <p style={{ color: 'var(--on-muted)' }}>{session.summary?.summary}</p>
          {session.summary?.fallback ? (
            <div className="notice-panel" role="status" style={{ margin: '12px 0' }}>
              <p style={{ margin: 0 }}>Đây là tổng kết dự phòng vì dịch vụ AI tạm thời chưa phản hồi.</p>
              <button type="button" disabled={Boolean(busy)} onClick={retrySummary} style={{ marginTop: 8 }}>
                Thử tạo lại tổng kết AI
              </button>
            </div>
          ) : null}
          <FeedbackList title="Điểm mạnh" items={session.summary?.strengths || []} />
          <FeedbackList title="Điểm cần cải thiện" items={session.summary?.weaknesses || []} />
          <FeedbackList title="Kế hoạch cải thiện" items={session.summary?.improvementPlan || []} />
          <p className="muted" style={{ marginTop: 16, fontSize: '0.8rem' }}>
            AI feedback chỉ phục vụ luyện tập, không phải quyết định tuyển dụng.
          </p>
        </div>
        <QuestionHistory questions={session.questions} onRetryFeedback={retryFeedback} />
      </div>
    );
  }

  return (
    <div className="interview-room">
      <button type="button" className="outline" onClick={onBack}>← Quay lại danh sách</button>
      <div className="question-panel">
        <div className="interview-progress">
          <span>Câu {currentQuestion?.orderIndex || session.totalQuestions}/{session.totalQuestions || config.questionCount}</span>
          <span className="chip">{session.title}</span>
        </div>
        <p className="muted" style={{ fontSize: '0.8rem' }}>
          AI feedback chỉ phục vụ luyện tập, không phải quyết định tuyển dụng.
        </p>
        {currentQuestion ? (
          <>
            <h2>{currentQuestion.content}</h2>
            <div className="chip-row">
              <span className="chip">{currentQuestion.questionType}</span>
              {currentQuestion.difficulty && <span className="chip warning">{currentQuestion.difficulty}</span>}
              {currentQuestion.skillTag && <span className="chip neutral">{currentQuestion.skillTag}</span>}
            </div>
            {currentQuestion.answer?.errorMessage && (
              <div className="error-panel" role="alert">
                <p style={{ margin: 0 }}>
                  {currentQuestion.answer.errorMessage || 'Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại.'}
                </p>
              </div>
            )}
            {config.voiceStreamingEnabled && voice.supported ? (
              <section className="voice-conversation-panel" aria-labelledby="voice-conversation-title">
                <div>
                  <h3 id="voice-conversation-title">Phỏng vấn rảnh tay</h3>
                  <p className="muted">
                    Sau {Math.round((config.voiceSilenceMs || 3000) / 1000)} giây im lặng, AI sẽ hỏi bạn đã trả lời xong chưa.
                    Hãy nói “đã xong” hoặc “chưa xong”.
                  </p>
                </div>
                <div className="voice-controls">
                  <button
                    type="button"
                    className={voice.active ? 'danger' : ''}
                    aria-pressed={voice.active}
                    disabled={Boolean(busy) || voice.phase === 'saving-answer'}
                    onClick={() => {
                      if (voice.active) void finishInterview();
                      else voice.start();
                    }}
                  >
                    {voice.active ? 'Kết thúc phỏng vấn' : 'Bắt đầu phỏng vấn'}
                  </button>
                </div>
                <p className="voice-status" role="status" aria-live="polite">
                  Trạng thái: {voicePhaseLabel(voice.phase)}
                </p>
                {voice.interimTranscript ? (
                  <p className="voice-interim" aria-live="polite">Đang nghe: {voice.interimTranscript}</p>
                ) : null}
                <label className="transcript-editor">
                  Nội dung hệ thống đang nghe
                  <textarea
                    value={transcript}
                    readOnly
                    placeholder="Câu trả lời sẽ xuất hiện trực tiếp khi bạn nói..."
                    aria-describedby="hands-free-transcript-help"
                  />
                </label>
                <p id="hands-free-transcript-help" className="muted">
                  Câu xác nhận “đã xong/chưa xong” được xử lý riêng và không được thêm vào câu trả lời.
                </p>
                {voice.error ? <div className="error-panel" role="alert">{voice.error}</div> : null}
              </section>
            ) : (
              <>
                {config.voiceStreamingEnabled ? (
                  <p className="notice-panel" role="status">
                    Trình duyệt chưa hỗ trợ hội thoại liên tục. Bạn vẫn có thể dùng chế độ ghi âm thủ công bên dưới.
                  </p>
                ) : null}
                <div className="recorder-panel">
                  <button type="button" onClick={isRecording ? stopRecording : startRecording}>
                    {isRecording ? '⏹ Dừng ghi âm' : '🎙 Bắt đầu ghi âm'}
                  </button>
                  <button type="button" className="outline" disabled={!audioFile || isRecording || !!busy}
                    onClick={transcribe}>
                    📝 Tạo transcript
                  </button>
                  <button type="button" className="outline" disabled={!!busy} onClick={skipQuestion}>
                    ⏭ Skip câu này
                  </button>
                  {audioFile && <span className="muted">{Math.round(audioFile.size / 1024)} KB đã ghi</span>}
                </div>
                <label className="transcript-editor">
                  Transcript có thể sửa
                  <textarea
                    value={transcript}
                    onChange={(event) => setTranscript(event.target.value)}
                    placeholder="Transcript sẽ hiện trực tiếp khi bạn nói hoặc sau khi xử lý audio..."
                    aria-describedby="transcript-help"
                  />
                </label>
                <p id="transcript-help" className="muted">
                  Kiểm tra transcript trước khi lưu câu trả lời.
                </p>
                <div className="voice-controls">
                  <button
                    type="button"
                    disabled={!transcript.trim() || !!busy}
                    onClick={() => void submitAnswer()}
                  >
                    Lưu câu trả lời và sang câu tiếp theo
                  </button>
                  <button type="button" className="danger" disabled={Boolean(busy)} onClick={() => void finishInterview()}>
                    Kết thúc phỏng vấn và chấm điểm
                  </button>
                </div>
              </>
            )}
          </>
        ) : (
          <div className="notice-panel">
            <p>Đã hoàn thành phần câu hỏi. Điểm và nhận xét chỉ được tạo sau khi bạn kết thúc phỏng vấn.</p>
            <button type="button" className="danger" disabled={Boolean(busy)} onClick={() => void finishInterview()} style={{ marginTop: 8 }}>
              Kết thúc phỏng vấn và nhận kết quả
            </button>
          </div>
        )}
        {busy && <p className="muted" role="status" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', flexShrink: 0 }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          {busy}
        </p>}
        {error && <div className="error-panel" role="alert">{error}</div>}
      </div>
      <QuestionHistory questions={session.questions} onRetryFeedback={retryFeedback} />
    </div>
  );
}

function findCurrentQuestion(session: AiInterviewSession) {
  return session.questions.find((question) => !question.answer?.answeredAt);
}

function voicePhaseLabel(phase: VoicePhase) {
  const labels: Record<VoicePhase, string> = {
    idle: 'Sẵn sàng',
    'speaking-question': 'AI đang nói',
    'listening-answer': 'Microphone đang nghe câu trả lời',
    'asking-confirmation': 'AI đang hỏi xác nhận',
    'listening-confirmation': 'Đang chờ bạn nói đã xong hoặc chưa xong',
    'saving-answer': 'Đang lưu câu trả lời và chuẩn bị câu tiếp theo',
    'awaiting-end': 'Đã hết câu hỏi, đang chờ kết thúc phỏng vấn',
    error: 'Cần kiểm tra microphone',
  };
  return labels[phase];
}

function QuestionHistory({
  questions,
  onRetryFeedback,
}: {
  questions: AiInterviewQuestion[];
  onRetryFeedback?: (question: AiInterviewQuestion) => Promise<void>;
}) {
  return (
    <div className="card">
      <h2>Câu hỏi đã xử lý</h2>
      <div className="question-history">
        {questions.map((question, i) => (
          <motion.article className="history-item" key={question.id}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.05 }}>
            <strong>Câu {question.orderIndex}: {question.content}</strong>
            {question.answer?.answeredAt && (
              <>
                <p style={{ color: 'var(--on-muted)', marginTop: 8, fontSize: '0.875rem' }}>
                  {question.answer.skipped ? 'Đã skip câu này.' : question.answer.transcript}
                </p>
                {question.answer.feedback && (
                  <div className="feedback-box">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className="chip match">{Math.round(Number(question.answer.feedback.score))}%</span>
                      {question.answer.feedback.fallback ? (
                        <>
                          <span className="fallback-pill">Đánh giá dự phòng</span>
                          {onRetryFeedback ? (
                            <button type="button" className="outline sm"
                              onClick={() => void onRetryFeedback(question)}>
                              Thử chấm lại bằng AI
                            </button>
                          ) : null}
                        </>
                      ) : null}
                    </div>
                    <p style={{ color: 'var(--on-muted)', fontSize: '0.875rem', margin: 0 }}>
                      {question.answer.feedback.feedback}
                    </p>
                    <FeedbackList title="Điểm mạnh" items={question.answer.feedback.strengths} />
                    <FeedbackList title="Cần cải thiện" items={question.answer.feedback.weaknesses} />
                    <FeedbackList title="Gợi ý" items={question.answer.feedback.suggestions} />
                  </div>
                )}
              </>
            )}
          </motion.article>
        ))}
      </div>
    </div>
  );
}

function FeedbackList({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null;
  return (
    <div className="feedback-list">
      <strong>{title}</strong>
      <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
    </div>
  );
}

export default App;
