import { ChangeEvent, FormEvent, lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import AdminLayout, { AdminProtected } from './pages/admin/AdminLayout';
import { authService } from './services/authService';
import { employerService } from './services/employerService';
import { aiInterviewService } from './services/aiInterviewService';
import { aiJobSearchService } from './services/aiJobSearchService';
import { useVoiceConversation, type VoicePhase } from './hooks/useVoiceConversation';
import { useCandidateRealtime } from './hooks/useCandidateRealtime';
import { clearAuthSession, getToken, setAuthSession, getStoredUser } from './utils/authStorage';
import { parseApiError } from './utils/planLimits';
import { filterAiJobSearchItems } from './utils/aiJobSearch';
import PlanLimitAlert from './components/PlanLimitAlert';
import { NotificationInbox } from './components/NotificationInbox';
import { DialogContainer } from './components/common/DialogContainer';
import ProvinceLocationSelect from './components/location/ProvinceLocationSelect';
import {
  IconBell,
  IconBookmark,
  IconBrandBriefcase,
  IconBriefcase,
  IconBuilding,
  IconCalendar,
  IconChevron,
  IconClipboard,
  IconClock,
  IconDashboard,
  IconDocument,
  IconGem,
  IconHome,
  IconLock,
  IconLogout,
  IconMapPin,
  IconMic,
  IconProfile,
  IconRefresh,
  IconRobot,
  IconSearch,
  IconSettings,
  IconSpark,
  IconUsers,
  IconWallet,
} from './components/icons/PortalNavIcons';
import { candidateService } from './services/candidateService';
import { jobService } from './services/jobService';
import { publicSettingsService, type PublicSettings } from './services/publicSettingsService';
import type {
  AiInterviewConfig,
  AiInterviewCvProfile,
  AiInterviewEligibleApplication,
  AiInterviewQuestion,
  AiInterviewSession,
} from './types/aiInterview';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  NotificationItem,
  JobAlert,
  JobAlertInput,
  SubmittedResume,
  SubscriptionView,
} from './types/candidateDomain';
import type { AccountView } from './types/auth';
import type { Category, Job, JobFilters, PublicCompany, Recommendation } from './types/job';
import type { AiJobSearchItem, AiJobSearchResult, AiJobSearchStatus } from './types/aiJobSearch';

const AdminAuditPage = lazy(() => import('./pages/admin/AdminAuditPage'));
const AdminBillingPage = lazy(() => import('./pages/admin/AdminBillingPage'));
const AdminCompanyDetailPage = lazy(() => import('./pages/admin/AdminCompanyDetailPage'));
const AdminCompanyReviewPage = lazy(() => import('./pages/admin/AdminCompanyReviewPage'));
const AdminDashboardPage = lazy(() => import('./pages/admin/AdminDashboardPage'));
const AdminJobDetailPage = lazy(() => import('./pages/admin/AdminJobDetailPage'));
const AdminJobsPage = lazy(() => import('./pages/admin/AdminJobsPage'));
const AdminLoginPage = lazy(() => import('./pages/admin/AdminLoginPage'));
const AdminPlanFormPage = lazy(() => import('./pages/admin/AdminPlanFormPage'));
const AdminProfilePage = lazy(() => import('./pages/admin/AdminProfilePage'));
const AdminSettingsPage = lazy(() => import('./pages/admin/AdminSettingsPage'));
const AdminStatisticsPage = lazy(() => import('./pages/admin/AdminStatisticsPage'));
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'));
const EmployerSubscriptionPage = lazy(() => import('./pages/billing/EmployerSubscriptionPage'));
const PaymentResultPage = lazy(() => import('./pages/billing/PaymentResultPage'));
const BankTransferCheckoutPage = lazy(() => import('./pages/billing/BankTransferCheckoutPage'));
const PaymentCheckoutPage = lazy(() => import('./pages/billing/PaymentCheckoutPage'));
const SubscriptionPlansPage = lazy(() => import('./pages/billing/SubscriptionPlansPage'));
const CompanyProfilePage = lazy(() => import('./pages/Employer/CompanyProfilePage'));
const CompanyLocationsPage = lazy(() => import('./pages/Employer/CompanyLocationsPage'));
const CandidateOnboardingPage = lazy(() => import('./pages/CandidateOnboardingPage'));
const CompanyVerificationPage = lazy(() => import('./pages/Employer/CompanyVerificationPage'));
const EmployerJobsPage = lazy(() => import('./pages/Employer/EmployerJobsPage'));
const EmployerApplicationsPage = lazy(() => import('./pages/Employer/EmployerApplicationsPage'));
const EmployerNotificationsPage = lazy(() => import('./pages/Employer/EmployerNotificationsPage'));
const EmployerSettingsPage = lazy(() => import('./pages/Employer/EmployerSettingsPage'));
const EmployerDashboardPage = lazy(() => import('./pages/Employer/EmployerDashboardPage'));
const EmployerInterviewsPage = lazy(() => import('./pages/Employer/EmployerInterviewsPage'));
const EmployerLandingPage = lazy(() => import('./pages/Employer/EmployerLandingPage'));

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

async function endAuthenticatedSession(afterLogout: () => void) {
  try {
    await authService.logout();
  } catch {
    // Local cleanup still matters when the token is already expired or revoked.
  }
  clearAuthSession();
  afterLogout();
}

// ─── Status labels ─────────────────────────────────────────────────────────
const statusLabels: Record<string, string> = {
  SUBMITTED: 'Đã nộp',
  UNDER_REVIEW: 'Đang xem xét',
  SHORTLISTED: 'Vào shortlist',
  INTERVIEW_SCHEDULED: 'Đã lên lịch phỏng vấn',
  INTERVIEWED: 'Đã phỏng vấn',
  EVALUATED: 'Đã đánh giá',
  ACCEPTED: 'Đã nhận Job Offer',
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
    <Suspense fallback={<div className="card" role="status" style={{ margin: 24 }}>Đang tải trang...</div>}>
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
      <Route path="/select-role" element={<SelectRolePage />} />
      <Route path="/employer-landing" element={<EmployerLandingPage />} />
      <Route path="/jobs" element={<JobsPage />} />
      <Route path="/jobs/:id" element={<JobDetailPage />} />
      <Route path="/companies/:id" element={<CompanyDetailPage />} />
      <Route path="/candidate/onboarding" element={<Protected role="CANDIDATE"><CandidateOnboardingPage /></Protected>} />
      <Route path="/candidate" element={<Protected role="CANDIDATE"><CandidateLayout /></Protected>}>
        <Route index element={<CandidateHome />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="cvs" element={<CvPage />} />
        <Route path="saved-jobs" element={<SavedJobsPage />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/:id" element={<ApplicationDetailPage />} />
        <Route path="ai-interviews" element={<AiInterviewPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="job-alerts" element={<JobAlertsPage />} />
        <Route path="subscription" element={<SubscriptionPage />} />
        <Route path="subscription/plans" element={<SubscriptionPlansPage backTo="/candidate/subscription" backLabel="Quay lại gói dịch vụ" />} />
      </Route>
      <Route path="/payment/result" element={<Protected><PaymentResultPage /></Protected>} />
      <Route path="/payment/checkout" element={<Protected><PaymentCheckoutPage /></Protected>} />
      <Route path="/payment/bank/:paymentId" element={<Protected><BankTransferCheckoutPage /></Protected>} />
      <Route path="/employer" element={<Protected role="EMPLOYER"><EmployerLayout /></Protected>}>
        <Route index element={<EmployerDashboardPage />} />
        <Route path="company-profile" element={<CompanyProfilePage />} />
        <Route path="locations" element={<CompanyLocationsPage />} />
        <Route path="verification" element={<CompanyVerificationPage />} />
        <Route path="jobs" element={<EmployerJobsPage />} />
        <Route path="applications" element={<EmployerApplicationsPage />} />
        <Route path="interviews" element={<EmployerInterviewsPage />} />
        <Route path="notifications" element={<EmployerNotificationsPage />} />
        <Route path="jobs/:jobId/applications" element={<EmployerApplicationsPage />} />
        <Route path="subscription" element={<EmployerSubscriptionPage />} />
        <Route path="subscription/plans" element={<SubscriptionPlansPage backTo="/employer/subscription" backLabel="Quay lại gói dịch vụ" title="Gói dành cho nhà tuyển dụng" />} />
        <Route path="settings" element={<EmployerSettingsPage />} />
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
    </Suspense>
  );
}

// ─── Utilities ─────────────────────────────────────────────────────────────
function formatMoney(value?: number) {
  if (!value) return 'Thỏa thuận';
  return new Intl.NumberFormat('vi-VN').format(value) + ' VND';
}

function formatDate(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('vi-VN');
}

function formatDateTime(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('vi-VN');
}

function formatJobMetaValue(value?: string) {
  if (!value) return '';
  return value
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

function companyInitials(name?: string) {
  const normalized = name?.trim();
  if (!normalized) return 'SJ';
  return normalized
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || normalized.slice(0, 2).toUpperCase();
}

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function focusFirstDialogControl(container: HTMLElement | null) {
  const first = container?.querySelector<HTMLElement>(focusableSelector);
  first?.focus();
}

function keepFocusInsideDialog(event: KeyboardEvent, container: HTMLElement | null) {
  if (event.key !== 'Tab' || !container) return;
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(focusableSelector))
    .filter((element) => element.offsetParent !== null || element === document.activeElement);
  if (focusable.length === 0) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function readError(error: unknown) {
  return parseApiError(error).message;
}

function safeInternalRedirect(value: string | null | undefined, fallback = '/candidate') {
  return value?.startsWith('/') && !value.startsWith('//') ? value : fallback;
}

async function candidateDestination(requestedRedirect?: string | null) {
  const returnTo = safeInternalRedirect(requestedRedirect);
  try {
    const onboarding = await candidateService.getOnboarding();
    return onboarding.status === 'PENDING'
      ? `/candidate/onboarding?returnTo=${encodeURIComponent(returnTo)}`
      : returnTo;
  } catch {
    return returnTo;
  }
}

function isPlanLimitError(error: unknown) {
  return parseApiError(error).isPlanLimit;
}

function readLoginError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { status?: number; data?: { code?: string; message?: string } } }).response;
    if (response?.data?.code === 'INVALID_CREDENTIALS' || response?.status === 401) {
      return 'Email hoặc mật khẩu không đúng.';
    }
    if (response?.data?.code === 'EMAIL_NOT_VERIFIED') {
      return 'Email chưa được xác minh. Vui lòng kiểm tra email trước khi đăng nhập.';
    }
  }
  return readError(error);
}

function isSuccessMessage(message: string) {
  return message.startsWith('✅')
    || message.startsWith('Đã')
    || message.startsWith('Da ')
    || message.includes('đã được')
    || message.includes('da duoc')
    || message.includes('thành công')
    || message.includes('thanh cong')
    || message.startsWith('Neu email')
    || message.startsWith('Nếu email')
    || message.startsWith('Mat khau')
    || message.startsWith('Mật khẩu')
    || message.includes('Upload CV');
}

function openBlobInNewTab(blob: Blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener,noreferrer');
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function ActionModal({
  title,
  description,
  confirmLabel,
  cancelLabel = 'Hủy',
  danger,
  busy,
  confirmDisabled,
  children,
  onConfirm,
  onClose,
}: {
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
  busy?: boolean;
  confirmDisabled?: boolean;
  children?: React.ReactNode;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const panelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    focusFirstDialogControl(panelRef.current);
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) {
        event.preventDefault();
        onClose();
      }
      keepFocusInsideDialog(event, panelRef.current);
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previousFocus?.focus();
    };
  }, [busy, onClose]);

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={() => !busy && onClose()}>
      <motion.div
        ref={panelRef}
        className="modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="action-modal-title"
        variants={scaleIn}
        initial="initial"
        animate="animate"
        exit="exit"
        transition={{ duration: 0.16, ease: EASE_OUT }}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 id="action-modal-title">{title}</h2>
        {description && <p className="muted">{description}</p>}
        {children}
        <div className="modal-actions">
          <button type="button" className="outline" disabled={busy} onClick={onClose}>
            {cancelLabel}
          </button>
          <button type="button" className={danger ? 'danger' : undefined} disabled={busy || confirmDisabled} onClick={onConfirm}>
            {busy ? 'Đang xử lý...' : confirmLabel}
          </button>
        </div>
      </motion.div>
    </div>
  );
}

type ApplyJobPayload = {
  resume: string;
  file?: File;
  preferredLocation: string;
  coverLetter: string;
};

function ApplyJobModal({
  job,
  cvs,
  defaultResume,
  busy,
  error,
  planLimitError,
  onClose,
  onSubmit,
  onOpenCv,
}: {
  job: Job;
  cvs: CvFile[];
  defaultResume: string;
  busy?: boolean;
  error?: string;
  planLimitError?: boolean;
  onClose: () => void;
  onSubmit: (payload: ApplyJobPayload) => void;
  onOpenCv: (id: string) => void;
}) {
  const modalRef = useRef<HTMLFormElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [selectedResume, setSelectedResume] = useState(defaultResume);
  const [file, setFile] = useState<File | undefined>();
  const [preferredLocation, setPreferredLocation] = useState(job.location || '');
  const [companyLocations, setCompanyLocations] = useState<CompanyLocation[]>(job.company?.locations || []);
  const [coverLetter, setCoverLetter] = useState('');
  const [fileError, setFileError] = useState('');

  useEffect(() => {
    if (job.company?.id) {
      jobService.getCompany(job.company.id)
        .then((comp) => {
          if (comp.locations && comp.locations.length > 0) {
            setCompanyLocations(comp.locations);
          }
        })
        .catch(() => {});
    }
  }, [job.company?.id]);

  const locationOptions = useMemo(() => {
    const list: { value: string; label: string }[] = [];
    const addedKeys = new Set<string>();

    list.push({ value: 'Remote', label: 'Làm việc từ xa (Remote)' });
    addedKeys.add('remote');
    addedKeys.add('làm việc từ xa');
    addedKeys.add('làm việc từ xa (remote)');

    const addLoc = (displayLoc?: string) => {
      const val = displayLoc?.trim();
      if (!val) return;
      const key = val.toLowerCase();
      if (addedKeys.has(key)) return;
      addedKeys.add(key);
      list.push({ value: val, label: val });
    };

    // 1. Thu thập tất cả các Chi nhánh Công ty chính thức
    const allBranches = [...(companyLocations || [])];
    if (job.companyLocation && !allBranches.some((b) => b.id === job.companyLocation?.id)) {
      allBranches.unshift(job.companyLocation);
    }

    const branchNamesSet = new Set<string>();

    allBranches.forEach((loc) => {
      const fullLoc = [loc.branchName, loc.address, loc.city].filter(Boolean).join(' - ');
      if (fullLoc) {
        addLoc(fullLoc);
      }
      if (loc.branchName) {
        branchNamesSet.add(loc.branchName.trim().toLowerCase());
      }
    });

    const isCoveredByBranches = (text?: string) => {
      if (!text) return true;
      const norm = text.trim().toLowerCase();
      if (!norm) return true;
      if (addedKeys.has(norm)) return true;
      for (const branchName of branchNamesSet) {
        if (branchName === norm || branchName.includes(norm) || norm.includes(branchName)) {
          return true;
        }
      }
      for (const key of addedKeys) {
        if (key.includes(norm) || norm.includes(key)) {
          return true;
        }
      }
      return false;
    };

    // 2. Chỉ thêm job.location hoặc company.location nếu chưa được bao gồm bởi chi nhánh chính thức
    if (job.location && !isCoveredByBranches(job.location)) {
      addLoc(job.location);
    }

    if (job.company?.location && !isCoveredByBranches(job.company.location)) {
      addLoc(job.company.location);
    }

    return list;
  }, [job, companyLocations]);

  useEffect(() => {
    if (!preferredLocation && locationOptions.length > 0) {
      const locLower = (job.location || '').trim().toLowerCase();
      const match = locationOptions.find((opt) => opt.value.toLowerCase() === locLower || opt.label.toLowerCase().includes(locLower)) || locationOptions[0];
      if (match) {
        setPreferredLocation(match.value);
      }
    }
  }, [locationOptions, job.location, preferredLocation]);

  const dirty = selectedResume !== defaultResume
    || Boolean(file)
    || preferredLocation !== (job.location || '')
    || Boolean(coverLetter.trim());

  const requestClose = useCallback(() => {
    if (busy) return;
    if (dirty && !window.confirm('Bạn có thông tin ứng tuyển chưa gửi. Bạn vẫn muốn đóng cửa sổ này?')) return;
    onClose();
  }, [busy, dirty, onClose]);

  useEffect(() => {
    setSelectedResume(defaultResume);
  }, [defaultResume]);

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    focusFirstDialogControl(modalRef.current);
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) {
        event.preventDefault();
        requestClose();
      }
      keepFocusInsideDialog(event, modalRef.current);
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previousFocus?.focus();
    };
  }, [busy, requestClose]);

  function validateFile(nextFile: File) {
    const name = nextFile.name.toLowerCase();
    if (!name.endsWith('.pdf')) {
      return 'Chỉ hỗ trợ file PDF.';
    }
    if (nextFile.size > 5 * 1024 * 1024) {
      return 'File CV không được vượt quá 5MB.';
    }
    return '';
  }

  function chooseFile(nextFile?: File) {
    if (!nextFile) return;
    const validationMessage = validateFile(nextFile);
    setFileError(validationMessage);
    if (validationMessage) {
      setFile(undefined);
      return;
    }
    setFile(nextFile);
    setSelectedResume('');
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!selectedResume && !file) {
      setFileError('Vui lòng chọn CV hoặc tải CV từ máy tính.');
      return;
    }
    if (!preferredLocation.trim()) {
      setFileError('Vui lòng nhập địa điểm làm việc mong muốn.');
      return;
    }
    onSubmit({
      resume: selectedResume,
      file,
      preferredLocation: preferredLocation.trim(),
      coverLetter: coverLetter.trim(),
    });
  }

  const latestCvId = cvs[0]?.id;
  const canSubmit = (Boolean(selectedResume) || Boolean(file))
    && Boolean(preferredLocation.trim())
    && !busy;

  return (
    <div className="modal-backdrop application-modal-backdrop" role="presentation" onMouseDown={requestClose}>
      <motion.form
        ref={modalRef}
        className="application-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="application-modal-title"
        variants={scaleIn}
        initial="initial"
        animate="animate"
        exit="exit"
        transition={{ duration: 0.16, ease: EASE_OUT }}
        onMouseDown={(event) => event.stopPropagation()}
        onSubmit={submit}
      >
        <div className="application-modal-header">
          <div>
            <h2 id="application-modal-title">Ứng tuyển</h2>
            <p>{job.title}</p>
          </div>
          <button type="button" className="icon-button" aria-label="Đóng" onClick={requestClose}>×</button>
        </div>

        <div className="application-modal-body">
          <section className="application-modal-section">
            <h3>
              <span aria-hidden="true">▣</span>
              Chọn CV để ứng tuyển
            </h3>
            <div className="resume-choice-list">
              {cvs.map((cv) => {
                const value = `uploaded:${cv.id}`;
                const checked = selectedResume === value;
                return (
                  <label key={cv.id} className={`resume-choice ${checked ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="resume"
                      value={value}
                      checked={checked}
                      onChange={() => {
                        setSelectedResume(value);
                        setFile(undefined);
                        setFileError('');
                      }}
                    />
                    <span className="resume-choice-main">
                      <strong>{cv.originalFileName}</strong>
                      <span>{Math.round(cv.fileSize / 1024)} KB</span>
                    </span>
                    {(cv.defaultCv || cv.id === latestCvId) && (
                      <span className="resume-choice-badge">{cv.defaultCv ? 'CV mặc định' : 'CV gần nhất'}</span>
                    )}
                    <button type="button" className="ghost sm" onClick={(event) => { event.preventDefault(); onOpenCv(cv.id); }}>
                      Xem
                    </button>
                  </label>
                );
              })}
            </div>

            <div
              className={`resume-upload-zone ${file ? 'has-file' : ''}`}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault();
                chooseFile(event.dataTransfer.files[0]);
              }}
              onClick={() => fileInputRef.current?.click()}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  fileInputRef.current?.click();
                }
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf,.pdf"
                hidden
                onChange={(event: ChangeEvent<HTMLInputElement>) => chooseFile(event.target.files?.[0])}
              />
              <span className="resume-upload-icon" aria-hidden="true">⇧</span>
              <strong>{file ? file.name : 'Tải lên CV từ máy tính, chọn hoặc kéo thả'}</strong>
              <span>Hỗ trợ PDF, kích thước dưới 5MB. CV sẽ được lưu vào CV của tôi.</span>
            </div>
          </section>

          <label className="application-field">
            <span className="application-field-row">
              Địa điểm làm việc mong muốn <span style={{ color: 'var(--danger)' }}>*</span>
            </span>
            <select
              className="application-select"
              value={preferredLocation}
              onChange={(event) => setPreferredLocation(event.target.value)}
              required
            >
              <option value="" disabled>-- Chọn địa điểm / chi nhánh làm việc --</option>
              {locationOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>

          <label className="application-field">
            <span className="application-field-row">
              Thư giới thiệu
              <small>{coverLetter.length}/2000</small>
            </span>
            <textarea
              value={coverLetter}
              onChange={(event) => setCoverLetter(event.target.value.slice(0, 2000))}
              maxLength={2000}
              placeholder="Viết ngắn gọn lý do bạn phù hợp với vị trí này..."
              rows={4}
            />
          </label>

          <div className="application-note">
            <strong>Lưu ý:</strong>
            <p>Hãy kiểm tra kỹ thông tin công ty và vị trí trước khi ứng tuyển. Không cung cấp giấy tờ nhạy cảm hoặc chuyển tiền ngoài nền tảng.</p>
          </div>

          {(fileError || error) && (
            planLimitError && error
              ? <PlanLimitAlert message={error} />
              : <div className="error-panel">{fileError || error}</div>
          )}
        </div>

        <div className="application-modal-footer">
          <button type="submit" disabled={!canSubmit} style={{ width: '100%', minHeight: 44 }}>
            {busy ? 'Đang nộp hồ sơ...' : 'Nộp hồ sơ ứng tuyển'}
          </button>
        </div>
      </motion.form>
    </div>
  );
}

function CandidateHomeActions() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [open, setOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationError, setNotificationError] = useState('');

  const loadCandidateHeader = useCallback(async () => {
    const [profileData, notificationData] = await Promise.all([
      candidateService.getProfile().catch(() => null),
      candidateService.getNotifications(0, 20).then((result) => result.items).catch(() => []),
    ]);
    setProfile(profileData);
    setNotifications(notificationData);
  }, []);

  useEffect(() => {
    void loadCandidateHeader();
  }, [loadCandidateHeader]);

  useCandidateRealtime((event) => {
    if (event.type === 'NOTIFICATION_UPDATED' || event.type === 'REALTIME_RECONNECTED') {
      void candidateService.getNotifications(0, 20)
        .then((result) => setNotifications(result.items))
        .catch(() => undefined);
    }
  });

  useEffect(() => {
    function closeOnOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false);
        setNotificationOpen(false);
      }
    }
    document.addEventListener('mousedown', closeOnOutside);
    return () => document.removeEventListener('mousedown', closeOnOutside);
  }, []);

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      setOpen(false);
      setNotificationOpen(false);
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, []);

  function logout() {
    void endAuthenticatedSession(() => navigate('/login'));
  }

  function notificationLink(item: NotificationItem) {
    if (item.relatedEntityType === 'APPLICATION' && item.relatedEntityId) {
      return `/candidate/applications/${item.relatedEntityId}`;
    }
    if (item.relatedEntityType === 'JOB' && item.relatedEntityId) {
      return `/jobs/${item.relatedEntityId}`;
    }
    return '/candidate/notifications';
  }

  async function openNotification(item: NotificationItem) {
    if (!item.read) {
      setNotifications((current) => current.map((entry) => entry.id === item.id ? { ...entry, read: true } : entry));
      try {
        await candidateService.markNotificationRead(item.id);
      } catch (err) {
        setNotifications((current) => current.map((entry) => entry.id === item.id ? { ...entry, read: false } : entry));
        setNotificationError(readError(err));
        return;
      }
    }
    setNotificationOpen(false);
    navigate(notificationLink(item));
  }

  async function markAllHomeNotificationsRead() {
    const previous = notifications;
    setNotifications((current) => current.map((entry) => ({ ...entry, read: true })));
    try {
      await candidateService.markAllNotificationsRead();
    } catch (err) {
      setNotifications(previous);
      setNotificationError(readError(err));
    }
  }

  const displayName = profile?.fullName || user?.email?.split('@')[0] || 'Candidate';
  const initials = displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'C';
  const unreadCount = notifications.filter((item) => !item.read).length;

  return (
    <div className="candidate-home-actions" ref={menuRef}>
      <button
        type="button"
        className="home-icon-action"
        aria-label="Thông báo"
        aria-expanded={notificationOpen}
        aria-controls="candidate-notification-menu"
        onClick={() => {
          setNotificationOpen((value) => !value);
          setOpen(false);
        }}
      >
        <span aria-hidden="true"><IconBell size={18} /></span>
        {unreadCount > 0 && <strong>{unreadCount > 99 ? '99+' : unreadCount}</strong>}
      </button>
      <button type="button" className="home-avatar-trigger" onClick={() => {
        setOpen((value) => !value);
        setNotificationOpen(false);
      }} aria-expanded={open} aria-controls="candidate-account-menu" aria-label="Mở menu tài khoản">
        <span className="home-avatar">{initials}</span>
      </button>

      <AnimatePresence>
        {notificationOpen && (
          <motion.div
            className="candidate-notification-menu"
            id="candidate-notification-menu"
            role="menu"
            variants={scaleIn}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={{ duration: 0.14, ease: EASE_OUT }}
          >
            <div className="notification-menu-header">
              <strong>Thông báo</strong>
              {unreadCount > 0 && (
                <button type="button" className="ghost sm" onClick={markAllHomeNotificationsRead}>
                  Đánh dấu đã đọc
                </button>
              )}
            </div>
            <div className="notification-menu-list">
              {notificationError && <div className="error-panel" role="alert">{notificationError}</div>}
              {notifications.length === 0 ? (
                <div className="notification-menu-empty">Chưa có thông báo mới.</div>
              ) : notifications.slice(0, 6).map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={`notification-menu-item ${item.read ? '' : 'unread'}`}
                  role="menuitem"
                  onClick={() => void openNotification(item)}
                >
                  <span>{item.title}</span>
                  <small>{item.message}</small>
                </button>
              ))}
            </div>
            <Link className="notification-menu-all" to="/candidate/notifications" onClick={() => setNotificationOpen(false)}>
              Xem tất cả thông báo
            </Link>
          </motion.div>
        )}
        {open && (
          <motion.div
            className="candidate-home-menu"
            id="candidate-account-menu"
            role="menu"
            variants={scaleIn}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={{ duration: 0.14, ease: EASE_OUT }}
          >
            <div className="candidate-menu-profile">
              <div className="candidate-menu-avatar">{initials}</div>
              <div>
                <strong>{displayName}</strong>
                <span>{profile?.applyReady ? 'Sẵn sàng ứng tuyển' : 'Cần hoàn thiện hồ sơ'}</span>
                <small>{user?.email}</small>
              </div>
            </div>

            <div className="candidate-menu-group">
              <strong>Quản lý tìm việc</strong>
              <Link to="/candidate/saved-jobs">Việc làm đã lưu</Link>
              <Link to="/candidate/applications">Việc làm đã ứng tuyển</Link>
              <Link to="/candidate">Việc làm phù hợp với bạn</Link>
            </div>
            <div className="candidate-menu-group">
              <strong>Quản lý CV</strong>
              <Link to="/candidate/cvs">CV của tôi</Link>
            </div>
            <div className="candidate-menu-group">
              <strong>Cài đặt email & thông báo</strong>
              <Link to="/candidate/notifications">Thông báo</Link>
            </div>
            <div className="candidate-menu-group">
              <strong>Cá nhân & Bảo mật</strong>
              <Link to="/candidate/profile">Hồ sơ cá nhân</Link>
              <Link to="/candidate/account">Tài khoản & Bảo mật</Link>
            </div>
            <div className="candidate-menu-group">
              <strong>Nâng cấp tài khoản</strong>
              <Link to="/candidate/subscription">Gói dịch vụ</Link>
            </div>

            <button type="button" className="candidate-menu-logout" onClick={logout}>
              Đăng xuất
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

type ProfileSectionKey = 'education' | 'workExperience' | 'projects' | 'certifications';

const profileSectionLabels: Record<ProfileSectionKey, string> = {
  education: 'Học vấn',
  workExperience: 'Kinh nghiệm làm việc',
  projects: 'Dự án',
  certifications: 'Chứng chỉ',
};

function asProfileItem(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function getItemText(item: Record<string, unknown>, key: string) {
  const value = item[key];
  return typeof value === 'string' ? value : '';
}

function numberParam(params: URLSearchParams, key: string) {
  const raw = params.get(key);
  if (!raw) return undefined;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function jobFiltersFromParams(params: URLSearchParams): JobFilters {
  return {
    search: params.get('search') || undefined,
    location: params.get('location') || undefined,
    skills: params.get('skills') || undefined,
    experienceLevel: params.get('experienceLevel') || undefined,
    category: params.get('category') || undefined,
    jobType: params.get('jobType') || undefined,
    workMode: params.get('workMode') || undefined,
    minSalary: numberParam(params, 'minSalary'),
    maxSalary: numberParam(params, 'maxSalary'),
    sort: params.get('sort') || 'newest',
  };
}

function jobPageFromParams(params: URLSearchParams) {
  const parsed = Number(params.get('page') || '0');
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 0;
}

function jobSizeFromParams(params: URLSearchParams) {
  const parsed = Number(params.get('size') || '12');
  return [12, 24, 48].includes(parsed) ? parsed : 12;
}

function toJobSearchParams(filters: JobFilters, page = 0, size = 12) {
  const next = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      next.set(key, String(value).trim());
    }
  });
  if (!next.get('sort')) next.set('sort', 'newest');
  if (page > 0) next.set('page', String(page));
  if (size !== 12) next.set('size', String(size));
  return next;
}

function PaginationControls({
  page,
  totalPages,
  onPageChange,
  label,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label: string;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pagination-bar" aria-label={label}>
      <button type="button" className="outline" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        Trước
      </button>
      <span className="muted">Trang {page + 1} / {totalPages}</span>
      <button type="button" className="outline" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Sau
      </button>
    </nav>
  );
}

type NavigationState = { from?: string; scrollY?: number; restoreScrollY?: number };

function internalOrigin(state: NavigationState | null | undefined, fallback: string) {
  return state?.from?.startsWith('/') && !state.from.startsWith('//') ? state.from : fallback;
}

function useRestoreScrollPosition() {
  const location = useLocation();
  useEffect(() => {
    const scrollY = (location.state as NavigationState | null)?.restoreScrollY;
    if (typeof scrollY !== 'number') return;
    const frame = window.requestAnimationFrame(() => window.scrollTo({ top: scrollY, behavior: 'auto' }));
    return () => window.cancelAnimationFrame(frame);
  }, [location.key, location.state]);
}

function Protected({ children, role }: { children: JSX.Element; role?: 'CANDIDATE' | 'EMPLOYER' | 'ADMIN' }) {
  const token = getToken();
  const currentRole = localStorage.getItem('role');
  const [validatedRole, setValidatedRole] = useState<string | null | undefined>(token ? undefined : null);

  useEffect(() => {
    let active = true;
    if (!token) {
      setValidatedRole(null);
      return () => { active = false; };
    }
    authService.getCurrentUser()
      .then((user) => { if (active) setValidatedRole(user.role); })
      .catch(() => {
        if (!active) return;
        clearAuthSession();
        setValidatedRole(null);
      });
    return () => { active = false; };
  }, [token]);

  if (!token || validatedRole === null) {
    return <Navigate to={`/login${role ? `?role=${role}` : ''}`} replace />;
  }
  if (validatedRole === undefined) return <div role="status" style={{ padding: 48, textAlign: 'center' }}>Đang xác thực phiên đăng nhập...</div>;
  const effectiveRole = validatedRole || currentRole;
  if (role && effectiveRole !== role) {
    const fallback = effectiveRole === 'CANDIDATE'
      ? '/candidate'
      : effectiveRole === 'EMPLOYER'
        ? '/employer'
        : effectiveRole === 'ADMIN'
          ? '/admin'
          : '/login';
    return <Navigate to={fallback} replace />;
  }
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
        <Link className="brand" to="/">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="7" width="20" height="14" rx="2" fill="var(--primary)" opacity="0.15"/>
            <rect x="8" y="3" width="8" height="6" rx="1.5" stroke="var(--primary)" strokeWidth="2" fill="none"/>
            <path d="M12 13v4M10 15h4" stroke="var(--primary)" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          Smart Recruitment
        </Link>

        <nav className="topbar-nav">
          <NavLink to="/" end className={({ isActive }) => `topbar-nav-item ${isActive ? 'active' : ''}`}>
            Home
          </NavLink>
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
              <Link to="/employer-landing" className="button-link outline" style={{ minHeight: 36, marginRight: 8, borderColor: 'transparent', color: 'var(--primary)', background: '#f0f9ff' }}>
                Đăng tuyển
              </Link>
              <Link to="/login" className="button-link outline" style={{ minHeight: 36 }}>
                Đăng nhập
              </Link>
              <Link to="/register" className="button-link" style={{ minHeight: 36 }}>
                Đăng ký
              </Link>
            </>
          )}
          {token && role === 'CANDIDATE' && <CandidateHomeActions />}
          {token && role !== 'CANDIDATE' && (
            <button
              className="outline sm"
              onClick={() => { void endAuthenticatedSession(() => { window.location.href = '/login'; }); }}
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
  const navigate = useNavigate();
  const [scrolled, setScrolled] = useState(false);
  const [homeSearch, setHomeSearch] = useState('');
  const [homeLocation, setHomeLocation] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [latestJobs, setLatestJobs] = useState<Job[]>([]);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [aiJobStatus, setAiJobStatus] = useState<AiJobSearchStatus | null>(null);
  const [homeLoading, setHomeLoading] = useState(true);
  const [homeError, setHomeError] = useState('');

  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 8);
    window.addEventListener('scroll', handler, { passive: true });
    return () => window.removeEventListener('scroll', handler);
  }, []);

  useEffect(() => {
    let mounted = true;
    async function loadHomeData() {
      setHomeLoading(true);
      setHomeError('');
      try {
        const [jobData, categoryData, recommendationData, aiStatusData] = await Promise.all([
          jobService.getAll({ sort: 'newest' }, 0, 6),
          jobService.getCategories().catch(() => []),
          token && role === 'CANDIDATE'
            ? jobService.recommendations().catch(() => [])
            : Promise.resolve([]),
          token && role === 'CANDIDATE'
            ? aiJobSearchService.status().catch(() => null)
            : Promise.resolve(null),
        ]);
        if (!mounted) return;
        setLatestJobs(jobData.content);
        setCategories(categoryData);
        setRecommendations(recommendationData);
        setAiJobStatus(aiStatusData);
      } catch (err) {
        if (mounted) setHomeError(readError(err));
      } finally {
        if (mounted) setHomeLoading(false);
      }
    }
    void loadHomeData();
    return () => { mounted = false; };
  }, [token, role]);

  function submitHomeSearch(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    const query = homeSearch.trim();
    const location = homeLocation.trim();
    if (query) params.set('search', query);
    if (location) params.set('location', location);
    params.set('sort', 'newest');
    const search = params.toString();
    navigate(search ? `/jobs?${search}` : '/jobs');
  }

  function openCategory(category: Category) {
    const params = new URLSearchParams();
    params.set('category', category.slug || category.id);
    params.set('sort', 'newest');
    navigate(`/jobs?${params.toString()}`);
  }

  function openAiJobSearch() {
    if (!token) {
      navigate(`/login?redirect=${encodeURIComponent('/jobs?mode=ai')}`);
      return;
    }
    navigate('/jobs?mode=ai');
  }

  const parentCategories = categories
    .filter((category) => !category.parentId)
    .slice(0, 8);
  const quickCategories = (parentCategories.length ? parentCategories : categories).slice(0, 8);
  const recommendationJobs = recommendations
    .slice(0, 4)
    .map((item) => ({ ...item.job, matchScore: undefined }));
  const hiringCompanies = Array.from(
    latestJobs.reduce((map, job) => {
      if (!map.has(job.company.id)) map.set(job.company.id, job.company);
      return map;
    }, new Map<string, Job['company']>()).values(),
  ).slice(0, 4);
  const isEmployer = Boolean(token && role === 'EMPLOYER');
  const isCandidate = Boolean(token && role === 'CANDIDATE');
  const showCandidateHome = !isEmployer;
  const candidateTools: Array<{ title: string; desc: string; to: string; icon: ReactNode }> = [
    { title: 'Quản lý CV', desc: 'Cập nhật CV đã upload trước khi ứng tuyển.', to: '/candidate/cvs', icon: <IconDocument size={22} /> },
    { title: 'Việc đã lưu', desc: 'Quay lại nhanh các công việc bạn đang cân nhắc.', to: '/candidate/saved-jobs', icon: <IconBookmark size={22} /> },
    { title: 'Theo dõi ứng tuyển', desc: 'Xem trạng thái hồ sơ, lịch phỏng vấn và job offer.', to: '/candidate/applications', icon: <IconClipboard size={22} /> },
    { title: 'Luyện phỏng vấn', desc: 'Chuẩn bị câu trả lời cho các vị trí đang ứng tuyển.', to: '/candidate/ai-interviews', icon: <IconMic size={22} /> },
  ];
  const employerTools: Array<{ title: string; desc: string; to: string; icon: ReactNode }> = [
    { title: 'Đăng tin tuyển dụng', desc: 'Tạo và quản lý tin đăng theo hạn mức gói dịch vụ.', to: '/employer/jobs', icon: <IconBriefcase size={22} /> },
    { title: 'Quản lý ứng viên', desc: 'Xem hồ sơ ứng tuyển, sắp xếp lịch và gửi offer.', to: '/employer/applications', icon: <IconUsers size={22} /> },
    { title: 'Hồ sơ công ty', desc: 'Cập nhật thông tin công ty và xác thực pháp lý.', to: '/employer/company-profile', icon: <IconBuilding size={22} /> },
    { title: 'Gói dịch vụ', desc: 'Nâng cấp hạn mức đăng tin và ưu tiên hiển thị.', to: '/employer/subscription', icon: <IconGem size={22} /> },
  ];

  return (
    <div className="home-shell">
      <header className={`home-topbar ${scrolled ? 'scrolled' : ''}`}>
        <Link className="brand" to="/">
          <IconBrandBriefcase size={22} />
          Smart Recruitment
        </Link>

        <nav className="home-topbar-nav">
          {!isEmployer && <Link to="/jobs" className="home-nav-link">Việc làm</Link>}
          {isCandidate && (
            <>
              <Link to="/candidate" className="home-nav-link">Dashboard</Link>
              <Link to="/candidate/saved-jobs" className="home-nav-link">Việc đã lưu</Link>
              <Link to="/candidate/applications" className="home-nav-link">Đã ứng tuyển</Link>
              <Link to="/candidate/cvs" className="home-nav-link">CV</Link>
            </>
          )}
          {isEmployer && (
            <>
              <Link to="/employer" className="home-nav-link">Dashboard</Link>
              <Link to="/employer/jobs" className="home-nav-link">Việc làm</Link>
              <Link to="/employer/applications" className="home-nav-link">Ứng viên</Link>
            </>
          )}
        </nav>

        <div className="home-topbar-actions">
          {!token ? (
            <>
              <Link to="/employer-landing" className="button-link outline" style={{ minHeight: 38, marginRight: 8, borderColor: 'transparent', color: 'var(--primary)', background: '#f0f9ff' }}>
                Đăng tuyển
              </Link>
              <Link to="/login" className="button-link outline" style={{ minHeight: 38 }}>
                Đăng nhập
              </Link>
              <Link to="/register" className="button-link" style={{ minHeight: 38 }}>
                Đăng ký
              </Link>
            </>
          ) : (
            <>
              {isCandidate && <CandidateHomeActions />}
              {isEmployer && (
                <Link to="/employer" className="button-link" style={{ minHeight: 38 }}>
                  Employer Portal
                </Link>
              )}
            </>
          )}
        </div>
      </header>

      <main>
        <section className="home-hero">
          <div className="home-hero-content">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: EASE_OUT }}
          >
            <span className="home-hero-eyebrow">
              {isEmployer
                ? 'Cổng nhà tuyển dụng Smart Recruitment'
                : 'Cập nhật việc làm thật từ hệ thống tuyển dụng'}
            </span>
            <h1 className="home-hero-title">
              {isEmployer
                ? 'Tuyển đúng người, quản lý hiệu quả'
                : 'Tìm công việc phù hợp với bạn'}
            </h1>
            <p className="home-hero-desc">
              {isEmployer
                ? 'Đăng tin tuyển dụng, theo dõi ứng viên và vận hành quy trình tuyển dụng trong Employer Portal.'
                : 'Tìm theo vị trí, kỹ năng, công ty hoặc địa điểm. Mở chi tiết công việc để lưu, đánh giá độ phù hợp và ứng tuyển bằng CV của bạn.'}
            </p>
          </motion.div>

          {isEmployer ? (
            <motion.div
              className="home-search-bar"
              style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, ease: EASE_OUT, delay: 0.2 }}
            >
              <Link to="/employer/jobs" className="button-link" style={{ minHeight: 44 }}>
                Quản lý việc làm
              </Link>
              <Link to="/employer/applications" className="button-link outline" style={{ minHeight: 44 }}>
                Quản lý ứng viên
              </Link>
              <Link to="/employer" className="button-link outline" style={{ minHeight: 44 }}>
                Vào Employer Portal
              </Link>
            </motion.div>
          ) : (
            <motion.form
              className="home-search-bar"
              onSubmit={submitHomeSearch}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, ease: EASE_OUT, delay: 0.2 }}
            >
              <label className="home-search-field">
                <span>Từ khóa</span>
                <input
                  placeholder="Vị trí, kỹ năng hoặc công ty"
                  value={homeSearch}
                  onChange={(event) => setHomeSearch(event.target.value)}
                />
              </label>
              <ProvinceLocationSelect
                className="home-search-field"
                label="Địa điểm"
                value={homeLocation}
                onChange={setHomeLocation}
                allowRemote
                placeholder="Tất cả địa điểm"
              />
              <button type="submit" className="home-search-btn">
                Tìm việc
              </button>
            </motion.form>
          )}

            {showCandidateHome && (
              <div className="home-ai-search">
                <button
                  type="button"
                  className="home-ai-search-button"
                  onClick={openAiJobSearch}
                  disabled={Boolean(token && aiJobStatus && !aiJobStatus.enabled)}
                  aria-describedby="home-ai-search-help"
                >
                  <span className="home-ai-search-icon" aria-hidden="true"><IconSpark size={16} /></span>
                  <span>Tìm việc phù hợp bằng AI</span>
                  <span aria-hidden="true">→</span>
                </button>
                <p id="home-ai-search-help">
                  {!token
                    ? 'Đăng nhập để AI phân tích Profile và CV mặc định của bạn.'
                    : aiJobStatus && !aiJobStatus.enabled
                      ? 'Tính năng AI hiện chưa sẵn sàng.'
                      : aiJobStatus
                        ? aiJobStatus.quota.limit < 0
                          ? 'Gói của bạn không giới hạn lượt tìm việc bằng AI.'
                          : `Còn ${aiJobStatus.quota.remaining}/${aiJobStatus.quota.limit} lượt trong tháng này.`
                        : 'AI phân tích Profile và CV mặc định để xếp hạng công việc.'}
                </p>
              </div>
            )}


          </div>
        </section>

        {homeError && (
          <section className="home-section compact">
            <div className="home-section-inner">
              <div className="error-panel">{homeError}</div>
            </div>
          </section>
        )}

        {showCandidateHome && recommendationJobs.length > 0 && (
          <section className="home-section compact">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Gợi ý cá nhân</p>
                  <h2 className="home-section-title">Việc làm dành cho bạn</h2>
                </div>
                <Link to="/jobs?sort=relevance" className="button-link outline sm">Xem thêm</Link>
              </div>
              <div className="home-job-grid">
                {recommendationJobs.map((job) => <JobCard key={job.id} job={job} />)}
              </div>
            </div>
          </section>
        )}

        {showCandidateHome && (
          <section className="home-section compact">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Cơ hội mới</p>
                  <h2 className="home-section-title">Việc làm mới nhất</h2>
                </div>
                <Link to="/jobs?sort=newest" className="button-link outline sm">Xem tất cả việc làm</Link>
              </div>

              {homeLoading ? (
                <div className="home-job-grid">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <div key={i} className="job-card-skeleton">
                      <div className="skeleton" style={{ height: 20, width: '70%' }} />
                      <div className="skeleton" style={{ height: 16, width: '48%' }} />
                      <div className="skeleton" style={{ height: 28, width: '80%' }} />
                    </div>
                  ))}
                </div>
              ) : latestJobs.length > 0 ? (
                <div className="home-job-grid">
                  {latestJobs.map((job) => <JobCard key={job.id} job={job} />)}
                </div>
              ) : (
                <div className="card home-empty-block">
                  <h3>Chưa có việc làm đang hiển thị</h3>
                  <p className="muted">Hãy quay lại sau hoặc thử tìm kiếm với từ khóa khác.</p>
                </div>
              )}
            </div>
          </section>
        )}

        {showCandidateHome && quickCategories.length > 0 && (
          <section className="home-section compact muted-band">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Ngành nghề</p>
                  <h2 className="home-section-title">Khám phá việc làm theo ngành</h2>
                </div>
              </div>
              <div className="home-category-grid">
                {quickCategories.map((category) => (
                  <button key={category.id} type="button" className="home-category-card" onClick={() => openCategory(category)}>
                    <strong>{category.name}</strong>
                    {category.description && <span>{category.description}</span>}
                  </button>
                ))}
              </div>
            </div>
          </section>
        )}

        {showCandidateHome && hiringCompanies.length > 0 && (
          <section className="home-section compact">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Nhà tuyển dụng</p>
                  <h2 className="home-section-title">Công ty đang có việc mới</h2>
                </div>
              </div>
              <div className="home-company-grid">
                {hiringCompanies.map((company) => (
                  <div key={company.id} className="home-company-card">
                    <div className="home-company-logo">
                      {company.logoUrl ? <img src={company.logoUrl} alt={company.name} loading="lazy" /> : <IconBuilding size={24} style={{ opacity: 0.6 }} />}
                    </div>
                    <div>
                      <strong>{company.name}</strong>
                      <span>{company.industry || company.location || 'Đang tuyển dụng'}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </section>
        )}

        {showCandidateHome && (
          <section className="home-section compact muted-band">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Quản lý tìm việc</p>
                  <h2 className="home-section-title">Công cụ cho ứng viên</h2>
                </div>
              </div>
              <div className="home-tools-grid">
                {candidateTools.map((tool) => (
                  <Link
                    key={tool.title}
                    className="home-tool-card"
                    to={isCandidate ? tool.to : '/login'}
                  >
                    <span className="home-tool-icon">{tool.icon}</span>
                    <strong>{tool.title}</strong>
                    <small>{tool.desc}</small>
                  </Link>
                ))}
              </div>
            </div>
          </section>
        )}

        {isEmployer && (
          <section className="home-section compact muted-band">
            <div className="home-section-inner">
              <div className="home-section-heading-row">
                <div>
                  <p className="eyebrow">Quản lý tuyển dụng</p>
                  <h2 className="home-section-title">Công cụ cho nhà tuyển dụng</h2>
                </div>
              </div>
              <div className="home-tools-grid">
                {employerTools.map((tool) => (
                  <Link key={tool.title} className="home-tool-card" to={tool.to}>
                    <span className="home-tool-icon">{tool.icon}</span>
                    <strong>{tool.title}</strong>
                    <small>{tool.desc}</small>
                  </Link>
                ))}
              </div>
            </div>
          </section>
        )}
      </main>

      <footer className="home-footer">
        <div className="home-section-inner">
          <div className="home-footer-grid">
            <div>
              <Link className="brand" to="/" style={{ marginBottom: 12, display: 'inline-flex' }}>
                <IconBrandBriefcase size={20} />
                Smart Recruitment
              </Link>
              <p style={{ color: 'var(--on-muted)', fontSize: '0.875rem', maxWidth: 280, margin: 0 }}>
                {isEmployer
                  ? 'Đăng tin, quản lý ứng viên và vận hành tuyển dụng trong một cổng dành cho nhà tuyển dụng.'
                  : 'Tìm việc, quản lý CV và theo dõi ứng tuyển trong một trải nghiệm dành cho ứng viên.'}
              </p>
            </div>
            {isEmployer ? (
              <div>
                <strong className="home-footer-heading">Nhà tuyển dụng</strong>
                <nav className="home-footer-nav">
                  <Link to="/employer">Dashboard</Link>
                  <Link to="/employer/jobs">Quản lý việc làm</Link>
                  <Link to="/employer/applications">Quản lý ứng viên</Link>
                  <Link to="/employer/subscription">Gói dịch vụ</Link>
                </nav>
              </div>
            ) : (
              <div>
                <strong className="home-footer-heading">Ứng viên</strong>
                <nav className="home-footer-nav">
                  <Link to="/jobs">Tìm việc làm</Link>
                  <Link to="/candidate/cvs">CV của tôi</Link>
                  <Link to="/candidate/saved-jobs">Việc đã lưu</Link>
                  <Link to="/candidate/applications">Hồ sơ ứng tuyển</Link>
                </nav>
              </div>
            )}
            <div>
              <strong className="home-footer-heading">Tài khoản</strong>
              <nav className="home-footer-nav">
                {token ? (
                  <Link to={isEmployer ? '/employer/settings' : isCandidate ? '/candidate/account' : '/login'}>
                    Cài đặt tài khoản
                  </Link>
                ) : (
                  <>
                    <Link to="/login">Đăng nhập</Link>
                    <Link to="/register">Đăng ký</Link>
                  </>
                )}
              </nav>
            </div>
            {!isEmployer && (
              <div>
                <strong className="home-footer-heading">Nhà tuyển dụng</strong>
                <nav className="home-footer-nav">
                  <Link to="/employer">Employer Portal</Link>
                  <Link to="/register">Đăng ký tuyển dụng</Link>
                </nav>
              </div>
            )}
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
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [googleOAuthEnabled, setGoogleOAuthEnabled] = useState(false);
  const oauthError = params.get('oauthError');
  const requestedRole = params.get('role');
  const requestedRedirect = params.get('redirect');
  const candidateRedirect = safeInternalRedirect(requestedRedirect);
  const oauthErrorMessage = oauthError === 'google_not_configured'
    ? 'Đăng nhập Google chưa được cấu hình trên môi trường này.'
    : oauthError === 'missing_profile'
      ? 'Google chưa trả về đủ thông tin email cho tài khoản này.'
      : oauthError === 'account_not_active'
        ? 'Tài khoản đang bị khoá hoặc chưa thể đăng nhập.'
        : '';

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
      const response = await authService.login({ email, password, portal: 'user' });
      if (response.user.role === 'ADMIN') {
        clearAuthSession();
        setError('Tài khoản quản trị vui lòng đăng nhập tại trang Admin.');
        return;
      }
      if (response.token) {
        setAuthSession(response.token, response.user);
      }
      if (response.user.role === 'EMPLOYER') navigate('/employer');
      else if (response.user.role === 'CANDIDATE') navigate(await candidateDestination(candidateRedirect));
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
          <h2>{requestedRole === 'EMPLOYER' ? 'Đăng Nhập Với Tài Khoản Nhà Tuyển Dụng' : 'Đăng nhập'}</h2>
          <p>Chào mừng trở lại! Vui lòng nhập thông tin tài khoản.</p>
        </div>

        {/* OAuth error */}
        <AnimatePresence>
          {oauthErrorMessage && (
            <motion.div
              className="error-panel"
              variants={scaleIn} initial="initial" animate="animate" exit="exit"
              transition={{ duration: 0.2, ease: EASE_OUT }}
              style={{ marginBottom: 16 }}
            >
              {oauthErrorMessage}
            </motion.div>
          )}
        </AnimatePresence>

        <form className="auth-form" onSubmit={submit} autoComplete="off">
          {/* Dummy hidden inputs to defeat Chrome/Edge aggressive autofill heuristics */}
          <input type="text" name="fake_username_login" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />
          <input type="password" name="fake_password_login" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />

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
                required
                autoComplete="off"
              />
            </div>
          </label>

          {/* Password */}
          <label>
            <div className="field-header">
              <span>Mật khẩu</span>
              <Link to="/forgot-password">Quên mật khẩu?</Link>
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
                required
                autoComplete="new-password"
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
            <Link to={requestedRole ? `/register?role=${requestedRole}` : "/register"}>Đăng ký ngay</Link>
          </p>


        </div>
      </motion.div>
    </div>
  );
}

// ─── REGISTER PAGE ──────────────────────────────────────────────────────────
function RegisterPage() {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const initialRole = (searchParams.get('role') as 'CANDIDATE' | 'EMPLOYER') || 'CANDIDATE';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [role, setRole] = useState<'CANDIDATE' | 'EMPLOYER'>(initialRole);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('');
  const [industries, setIndustries] = useState<{ categoryId: string, categoryName: string }[]>([]);
  const [isIndustryDropdownOpen, setIsIndustryDropdownOpen] = useState(false);
  const [categories, setCategories] = useState<any[]>([]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (role === 'EMPLOYER') {
      jobService.getCategories().then(data => {
        setCategories(data.filter((c: any) => !c.parentId));
      }).catch(() => {});
    }
  }, [role]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    if (password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.');
      return;
    }
    if (role === 'EMPLOYER') {
      const trimmedFullName = fullName.trim();
      if (!trimmedFullName) { setError('Vui lòng nhập họ và tên.'); return; }
      if (trimmedFullName.length < 2) { setError('Họ và tên quá ngắn (tối thiểu 2 ký tự).'); return; }
      if (trimmedFullName.length > 50) { setError('Họ và tên quá dài (tối đa 50 ký tự).'); return; }
      if (!/^[\p{L}\s]+$/u.test(trimmedFullName)) { setError('Họ và tên không hợp lệ (không được chứa số hoặc ký tự đặc biệt).'); return; }
      if (!phone.trim() || !/^(0|\+84)[3|5|7|8|9][0-9]{8}$/.test(phone.trim())) { setError('Số điện thoại không hợp lệ.'); return; }
      if (!gender) { setError('Vui lòng chọn giới tính.'); return; }
    }

    setLoading(true);
    try {
      await authService.register({
        email,
        password,
        role,
        ...(role === 'EMPLOYER' ? { fullName: fullName.trim(), phone: phone.trim(), gender: gender || undefined, industries } : {})
      });
      setMessage('Đăng ký thành công! Vui lòng kiểm tra email để xác minh tài khoản.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }

  const formContent = (
    <>
      <div className="auth-logo">
        <h1>{role === 'EMPLOYER' ? 'Đăng ký Nhà tuyển dụng' : 'Tạo tài khoản'}</h1>
        <p>{role === 'EMPLOYER' ? 'Tìm kiếm ứng viên tài năng cùng hệ thống của chúng tôi' : 'Tham gia Smart Recruitment Portal'}</p>
      </div>

      <form className="auth-form" onSubmit={submit} autoComplete="off">
        {/* Dummy hidden inputs to defeat Chrome/Edge aggressive autofill heuristics */}
        <input type="text" name="fake_username_reg" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />
        <input type="password" name="fake_password_reg" style={{ display: 'none' }} tabIndex={-1} autoComplete="off" />
        {role === 'EMPLOYER' && (
          <label>
            Họ và tên
            <div className="input-icon-wrap">
              <span className="input-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </span>
              <input
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                autoComplete="off"
              />
            </div>
          </label>
        )}

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
              required
              autoComplete="off"
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
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="new-password"
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

        <label>
          Nhập lại mật khẩu
          <div className="input-icon-wrap">
            <span className="input-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
            </span>
            <input
              type={showConfirmPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
              style={{ paddingRight: 44 }}
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword(!showConfirmPassword)}
              style={{
                position: 'absolute', right: 0, top: 0, bottom: 0,
                background: 'transparent', border: 'none', color: 'var(--outline)',
                cursor: 'pointer', minHeight: 'auto', padding: '0 12px', width: 'auto',
              }}
              tabIndex={-1}
            >
              {showConfirmPassword ? (
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


        {role === 'EMPLOYER' && (
          <>

            <label>
              Số điện thoại
              <div className="input-icon-wrap">
                <span className="input-icon">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                </span>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  required
                  autoComplete="off"
                />
              </div>
            </label>

            <label>
              Giới tính
              <select value={gender} onChange={(e) => setGender(e.target.value)} required>
                <option value="">-- Chọn giới tính --</option>
                <option value="male">Nam</option>
                <option value="female">Nữ</option>
                <option value="other">Khác</option>
                <option value="prefer_not_to_say">Không muốn tiết lộ</option>
              </select>
            </label>
          </>
        )}

        <AnimatePresence>
          {message && (
            <motion.div className={isSuccessMessage(message) ? 'success-panel' : 'error-panel'} variants={scaleIn} initial="initial" animate="animate" exit="exit"
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

        <>
          <div className="auth-divider">
            <span>Hoặc đăng ký bằng</span>
          </div>
          <a className="btn-google" href="#" onClick={(e) => {
            e.preventDefault();
            document.cookie = `oauth_preferred_role=${role}; path=/; max-age=3600`;
            window.location.href = "/api/oauth2/authorization/google";
          }}>
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
            </svg>
            Đăng ký bằng Google
          </a>
        </>
      </form>

      <div className="auth-footer">
        <p>Đã có tài khoản? <Link to={role === 'EMPLOYER' ? "/login?role=EMPLOYER" : "/login"}>Đăng nhập</Link></p>
      </div>
    </>
  );

  if (role === 'EMPLOYER') {
    return (
      <div className="min-h-screen flex bg-gray-50">
        <div className="flex-1 flex flex-col justify-center py-12 px-4 sm:px-6 lg:flex-none lg:px-20 xl:px-24">
          <motion.div
            className="mx-auto w-full max-w-sm lg:w-[480px] auth-card bg-white shadow-xl rounded-2xl"
            variants={scaleIn}
            initial="initial"
            animate="animate"
            transition={{ duration: 0.25, ease: EASE_OUT }}
          >
            {formContent}
          </motion.div>
        </div>
        <div className="hidden lg:block relative w-0 flex-1">
          <img
            className="absolute inset-0 h-full w-full object-cover"
            src="https://images.unsplash.com/photo-1556761175-4b46a572b786?ixlib=rb-4.0.3&auto=format&fit=crop&w=1920&q=80"
            alt="Office workspace"
          />
          <div className="absolute inset-0 bg-emerald-700 mix-blend-multiply opacity-60"></div>
          <div className="absolute inset-0 flex flex-col items-center justify-center text-white px-12 text-center">
            <h1 className="text-4xl font-bold mb-4">Tuyển Dụng Nhanh Chóng & Hiệu Quả</h1>
            <p className="text-lg text-emerald-50 max-w-lg">
              Tham gia mạng lưới tuyển dụng với hàng ngàn ứng viên tiềm năng. Xây dựng đội ngũ vững mạnh cho doanh nghiệp của bạn ngay hôm nay.
            </p>
          </div>
        </div>
      </div>
    );
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
        {formContent}
      </motion.div>
    </div>
  );
}

// ─── FORGOT PASSWORD ─────────────────────────────────────────────────────────
function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    setLoading(true);
    try {
      const response = await authService.forgotPassword({ email });
      setMessage(response.message || 'Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <motion.div className="auth-card" variants={scaleIn} initial="initial" animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}>
        <div className="auth-logo">
          <h1>Quên mật khẩu</h1>
          <p>Nhập email để nhận link đặt lại mật khẩu</p>
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
                onChange={(event) => setEmail(event.target.value)}
                required
                autoComplete="off"
              />
            </div>
          </label>

          <AnimatePresence>
            {message && (
              <motion.div className={isSuccessMessage(message) ? 'success-panel' : 'error-panel'}
                variants={scaleIn} initial="initial" animate="animate" exit="exit"
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
            {loading ? 'Đang gửi...' : 'Gửi link đặt lại mật khẩu'}
          </button>
        </form>

        <div className="auth-footer">
          <p><Link to="/login">Về trang đăng nhập</Link></p>
        </div>
      </motion.div>
    </div>
  );
}

// ─── RESET PASSWORD ──────────────────────────────────────────────────────────
function ResetPasswordPage() {
  const [params] = useSearchParams();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const token = params.get('token') || '';
  const passwordRules = {
    length: password.length >= 8,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    digit: /\d/.test(password),
  };
  const passwordValid = Object.values(passwordRules).every(Boolean);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    if (!token) {
      setError('Thiếu token đặt lại mật khẩu.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.');
      return;
    }
    if (!passwordValid) {
      setError('Mật khẩu chưa đáp ứng đầy đủ các yêu cầu bảo mật.');
      return;
    }
    setLoading(true);
    try {
      const response = await authService.resetPassword({ token, password });
      setMessage(response.message || 'Mật khẩu đã được cập nhật.');
      setPassword('');
      setConfirmPassword('');
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <motion.div className="auth-card" variants={scaleIn} initial="initial" animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}>
        <div className="auth-logo">
          <h1>Đặt lại mật khẩu</h1>
          <p>Mật khẩu cần ít nhất 8 ký tự, gồm chữ hoa, chữ thường và số</p>
        </div>

        {!token && (
          <div className="error-panel" role="alert">
            Link đặt lại mật khẩu không hợp lệ hoặc thiếu token.
            <div style={{ marginTop: 10 }}><Link to="/forgot-password">Yêu cầu link mới</Link></div>
          </div>
        )}

        <form className="auth-form" onSubmit={submit}>
          <label>
            Mật khẩu mới
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          <ul className="muted" aria-label="Yêu cầu mật khẩu" style={{ margin: 0, paddingLeft: 20 }}>
            <li>{passwordRules.length ? '✓' : '○'} Ít nhất 8 ký tự</li>
            <li>{passwordRules.upper ? '✓' : '○'} Có chữ hoa</li>
            <li>{passwordRules.lower ? '✓' : '○'} Có chữ thường</li>
            <li>{passwordRules.digit ? '✓' : '○'} Có chữ số</li>
          </ul>

          <label>
            Nhập lại mật khẩu
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          <AnimatePresence>
            {message && (
              <motion.div className={isSuccessMessage(message) ? 'success-panel' : 'error-panel'}
                variants={scaleIn} initial="initial" animate="animate" exit="exit"
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

          <button type="submit" disabled={loading || !token || !passwordValid || password !== confirmPassword} style={{ width: '100%', minHeight: 44 }}>
            {loading ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
          </button>
        </form>

        <div className="auth-footer">
          <p><Link to="/login">Về trang đăng nhập</Link></p>
        </div>
      </motion.div>
    </div>
  );
}

// ─── VERIFY EMAIL ───────────────────────────────────────────────────────────
function VerifyEmailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('Đang xác minh địa chỉ email của bạn...');
  const [secondsLeft, setSecondsLeft] = useState(5);

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setStatus('error');
      setMessage('Liên kết xác minh không hợp lệ hoặc đã thiếu token.');
      return;
    }
    authService.verifyEmail(token)
      .then(() => {
        setStatus('success');
        setMessage('Email đã được xác minh. Bạn có thể đăng nhập.');
      })
      .catch((err) => {
        setStatus('error');
        setMessage(readError(err));
      });
  }, [params]);

  useEffect(() => {
    if (status !== 'success') return;
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      const remaining = Math.max(0, 5 - Math.floor((Date.now() - startedAt) / 1000));
      setSecondsLeft(remaining);
      if (remaining === 0) {
        window.clearInterval(timer);
        navigate('/login', { replace: true });
      }
    }, 200);
    return () => window.clearInterval(timer);
  }, [navigate, status]);

  return (
    <div className="auth-shell verify-shell">
      <motion.div className={`auth-card verify-card verify-card--${status}`} variants={scaleIn} initial="initial" animate="animate"
        transition={{ duration: 0.25, ease: EASE_OUT }}>
        <div className="verify-icon" aria-hidden="true">
          {status === 'loading' ? <span className="verify-spinner" /> : status === 'success' ? '✓' : '!'}
        </div>
        <div className="auth-logo">
          <p className="verify-kicker">Smart Recruitment Portal</p>
          <h1>{status === 'loading' ? 'Đang xác minh email' : status === 'success' ? 'Xác minh thành công' : 'Không thể xác minh'}</h1>
        </div>
        <p className="verify-message" role="status">{message}</p>
        {status === 'success' && <>
          <div className="verify-countdown" aria-live="polite">
            <div className="verify-progress"><span key={status} /></div>
            <p>Tự động chuyển tới trang đăng nhập sau <strong>{secondsLeft} giây</strong></p>
          </div>
          <button type="button" className="verify-login-button" onClick={() => navigate('/login', { replace: true })}>Về trang đăng nhập</button>
        </>}
        {status === 'error' && <Link className="button-link outline verify-login-button" to="/login">Về trang đăng nhập</Link>}
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
          if (user.role === 'CANDIDATE') candidateDestination().then((destination) => navigate(destination));
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
      navigate(role === 'CANDIDATE' ? await candidateDestination() : '/employer');
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
  useRestoreScrollPosition();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const aiMode = params.get('mode') === 'ai';
  const [jobs, setJobs] = useState<Job[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const filters = useMemo(() => jobFiltersFromParams(params), [params]);
  const page = jobPageFromParams(params);
  const pageSize = jobSizeFromParams(params);
  const [draftFilters, setDraftFilters] = useState<JobFilters>(filters);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState('');
  const [filterError, setFilterError] = useState('');
  const [loading, setLoading] = useState(true);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const [aiStatus, setAiStatus] = useState<AiJobSearchStatus | null>(null);
  const [aiResult, setAiResult] = useState<AiJobSearchResult | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState('');
  const [showAiConsent, setShowAiConsent] = useState(false);
  const [consentBusy, setConsentBusy] = useState(false);
  const [revokeBusy, setRevokeBusy] = useState(false);
  const [aiPlanLimit, setAiPlanLimit] = useState(false);
  const filterRef = useRef<HTMLFormElement | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    if (aiMode) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await jobService.getAll(filters, page, pageSize, signal);
      if (signal?.aborted) return;
      setJobs(response.content);
      setTotalElements(response.totalElements);
      setTotalPages(response.totalPages);
      if (response.page !== page) {
        setParams(toJobSearchParams(filters, response.page, pageSize), { replace: true });
      }
    } catch (err) {
      if (!signal?.aborted) setError(readLoginError(err));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [aiMode, filters, page, pageSize, setParams]);

  const runAiSearch = useCallback(async (forceRefresh: boolean) => {
    setAiLoading(true);
    setAiError('');
    setAiPlanLimit(false);
    try {
      const result = await aiJobSearchService.search(forceRefresh);
      setAiResult(result);
      setAiStatus((current) => current ? {
        ...current,
        quota: result.quota,
        cache: {
          available: Boolean(result.runId),
          generatedAt: result.generatedAt,
          expiresAt: result.expiresAt,
          stale: false,
        },
      } : current);
    } catch (err) {
      const parsed = parseApiError(err);
      setAiError(parsed.message);
      setAiPlanLimit(parsed.isPlanLimit);
    } finally {
      setAiLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    if (!aiMode) return;
    const token = getToken();
    const role = localStorage.getItem('role');
    if (!token || role !== 'CANDIDATE') {
      navigate(`/login?redirect=${encodeURIComponent('/jobs?mode=ai')}`, { replace: true });
      return;
    }
    setAiLoading(true);
    let active = true;
    aiJobSearchService.status()
      .then((status) => {
        if (!active) return;
        setAiStatus(status);
        if (!status.enabled) {
          setAiError('Tìm việc bằng AI hiện chưa sẵn sàng. Vui lòng thử lại sau.');
          setAiLoading(false);
        } else if (status.consentRequired) {
          setShowAiConsent(true);
          setAiLoading(false);
        } else if (status.cache.available && !status.cache.stale) {
          void runAiSearch(false);
        } else {
          setAiResult(null);
          setAiLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setAiError(readError(err));
          setAiLoading(false);
        }
      });
    return () => { active = false; };
  }, [aiMode, navigate, runAiSearch]);
  useEffect(() => {
    if (aiMode) {
      setCategories([]);
      return;
    }
    let active = true;
    jobService.getCategories()
      .then((items) => { if (active) setCategories(items); })
      .catch(() => { if (active) setCategories([]); });
    return () => { active = false; };
  }, [aiMode]);
  useEffect(() => { setDraftFilters(filters); }, [filters]);

  function updateDraft<K extends keyof JobFilters>(key: K, value: JobFilters[K]) {
    setDraftFilters((current) => ({ ...current, [key]: value || undefined }));
  }

  function applyFilters(event?: FormEvent) {
    event?.preventDefault();
    if (draftFilters.minSalary !== undefined && draftFilters.maxSalary !== undefined
        && draftFilters.minSalary > draftFilters.maxSalary) {
      setFilterError('Mức lương từ không được lớn hơn mức lương đến.');
      return;
    }
    setFilterError('');
    const next = toJobSearchParams(draftFilters, 0, pageSize);
    if (aiMode) next.set('mode', 'ai');
    setParams(next);
  }

  function resetFilters() {
    setFilterError('');
    const reset: JobFilters = aiMode ? {} : { sort: 'newest' };
    setDraftFilters(reset);
    const next = toJobSearchParams(reset, 0, pageSize);
    if (aiMode) next.set('mode', 'ai');
    setParams(next);
  }

  function changePage(nextPage: number) {
    const bounded = Math.max(0, Math.min(nextPage, Math.max(totalPages - 1, 0)));
    setParams(toJobSearchParams(filters, bounded, pageSize));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const aiFilterKeys = new Set<keyof JobFilters>(['location', 'minSalary', 'maxSalary', 'jobType', 'workMode']);
  const activeFilterEntries = Object.entries(filters).filter(([key, value]) =>
    key !== 'sort'
      && (!aiMode || aiFilterKeys.has(key as keyof JobFilters))
      && value !== undefined
      && value !== null
      && String(value).trim() !== '',
  ) as [keyof JobFilters, string | number][];
  const hasActiveFilters = activeFilterEntries.length > 0;

  function clearFilter(key: keyof JobFilters) {
    const next = toJobSearchParams({ ...filters, [key]: undefined }, 0, pageSize);
    if (aiMode) next.set('mode', 'ai');
    setParams(next);
  }

  async function acceptAiConsent() {
    if (!aiStatus) return;
    setConsentBusy(true);
    setAiError('');
    try {
      const status = await aiJobSearchService.consent(aiStatus.policyVersion);
      setAiStatus(status);
      setShowAiConsent(false);
    } catch (err) {
      setAiError(readError(err));
    } finally {
      setConsentBusy(false);
    }
  }

  function refreshAiResults() {
    const remaining = aiResult?.quota.remaining ?? aiStatus?.quota.remaining;
    if (remaining === 0) {
      setAiPlanLimit(true);
      setAiError('Bạn đã hết lượt tìm việc bằng AI trong tháng này.');
      return;
    }
    const needsNewEvaluation = !aiStatus?.cache.available || aiStatus.cache.stale;
    if (!needsNewEvaluation || window.confirm('Đánh giá mới có thể sử dụng 1 lượt AI. Bạn muốn tiếp tục?')) {
      void runAiSearch(true);
    }
  }

  async function revokeAiConsent() {
    if (!window.confirm('Thu hồi quyền AI sẽ xóa kết quả gợi ý đang lưu. Bạn muốn tiếp tục?')) return;
    setRevokeBusy(true);
    setAiError('');
    try {
      await aiJobSearchService.revokeConsent();
      setAiResult(null);
      setAiStatus((current) => current ? {
        ...current,
        consentRequired: true,
        cache: { available: false, generatedAt: null, expiresAt: null, stale: false },
      } : current);
    } catch (err) {
      setAiError(readError(err));
    } finally {
      setRevokeBusy(false);
    }
  }

  const filteredAiItems = useMemo(() => {
    return filterAiJobSearchItems(aiResult?.items || [], filters);
  }, [aiResult, filters]);
  const isEmployer = localStorage.getItem('role') === 'EMPLOYER';
  const jobCards = aiMode
    ? filteredAiItems.map((item) => (
      <AiRecommendationCard key={item.job.id} item={item} />
    ))
    : jobs.map((job, i) => (
      <motion.div
        key={job.id}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.28, ease: EASE_OUT, delay: Math.min(i * 0.04, 0.3) }}
      >
        <JobCard job={job} />
      </motion.div>
    ));
  const jobSkeletons = Array.from({ length: isEmployer ? 8 : 6 }).map((_, i) => (
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
  ));
  const jobsEmpty = (
    <div className="empty-state card" style={{ padding: 48, textAlign: 'center' }}>
      <div className="empty-state-icon"><IconSearch size={36} /></div>
      <h3>{aiMode ? 'Chưa có công việc phù hợp' : 'Không tìm thấy việc làm'}</h3>
      <p className="muted">
        {aiMode && !aiResult
          ? 'Hãy thử lại hoặc cập nhật Profile và CV mặc định.'
          : 'Thử thay đổi bộ lọc để xem thêm kết quả.'}
      </p>
      {aiMode && !aiResult && <Link className="button-link outline" to="/candidate/profile">Cập nhật Profile</Link>}
    </div>
  );

  return (
    <Shell>
      <div className="jobs-shell">
        <button type="button" className="mobile-filter-toggle" aria-expanded={mobileFiltersOpen}
          aria-controls="job-search-filters" onClick={() => {
            setMobileFiltersOpen((value) => !value);
            window.requestAnimationFrame(() => filterRef.current?.querySelector<HTMLInputElement>('input')?.focus());
          }}>
          {mobileFiltersOpen ? 'Đóng bộ lọc' : aiMode ? 'Lọc top 10 AI' : 'Mở bộ lọc tìm việc'}
        </button>
        {aiMode && (
          <section className="ai-job-banner" aria-labelledby="ai-job-banner-title" aria-busy={aiLoading}>
            <div className="ai-job-banner-copy">
              <p className="eyebrow">AI Job Match</p>
              <h1 id="ai-job-banner-title">10 công việc phù hợp nhất</h1>
              <p>
                {aiResult?.lowConfidence || aiStatus?.readiness.lowConfidence
                  ? 'Đang xếp hạng từ Profile. Thêm CV mặc định để tăng độ tin cậy.'
                  : 'Xếp hạng từ Profile và CV mặc định của bạn.'}
              </p>
              <div className="ai-job-banner-meta" aria-live="polite">
                {(aiResult?.quota || aiStatus?.quota) && (
                  <span>
                    Lượt còn lại: {((aiResult?.quota || aiStatus?.quota)?.limit ?? 0) < 0
                      ? 'Không giới hạn'
                      : `${(aiResult?.quota || aiStatus?.quota)?.remaining}/${(aiResult?.quota || aiStatus?.quota)?.limit}`}
                  </span>
                )}
                {aiResult?.generatedAt && <span>Tạo lúc: {formatAiDate(aiResult.generatedAt)}</span>}
                {aiResult?.cached && <span>Kết quả đã lưu · đầu vào không thay đổi</span>}
              </div>
              {aiStatus?.cache.stale && !aiResult && (
                <div className="warning-panel" role="status" style={{ marginTop: 12 }}>
                  Hồ sơ hoặc thông tin việc làm đã được cập nhật. Hãy cập nhật kết quả AI để nhận đề xuất mới nhất.
                </div>
              )}
            </div>
            <div className="ai-job-banner-actions">
              {aiStatus?.consentRequired && !aiResult ? (
                <button type="button" onClick={() => setShowAiConsent(true)} disabled={aiLoading}>
                  Xem và đồng ý chính sách
                </button>
              ) : (
                <button type="button" onClick={refreshAiResults} disabled={aiLoading}>
                  {aiLoading
                    ? 'AI đang phân tích…'
                    : aiStatus?.cache.stale
                      ? 'Cập nhật kết quả AI'
                      : aiResult
                        ? 'Kiểm tra cập nhật kết quả'
                        : 'Tìm việc phù hợp với AI'}
                </button>
              )}
              <button type="button" className="outline" onClick={() => navigate('/jobs')}>
                Tìm kiếm thông thường
              </button>
              {!aiStatus?.consentRequired && (
                <button type="button" className="ai-consent-revoke" onClick={() => void revokeAiConsent()} disabled={aiLoading || revokeBusy}>
                  {revokeBusy ? 'Đang thu hồi…' : 'Thu hồi quyền AI'}
                </button>
              )}
            </div>
            {aiError && (
              <div className="ai-job-inline-error" role="alert">
                <span>{aiError}</span>
                <div>
                  {!aiPlanLimit && aiStatus?.enabled && (
                    <button type="button" className="outline sm" onClick={() => void runAiSearch(true)} disabled={aiLoading}>
                      Thử lại
                    </button>
                  )}
                  {aiPlanLimit && <Link className="button-link sm" to="/candidate/subscription/plans">Xem gói dịch vụ</Link>}
                </div>
              </div>
            )}
          </section>
        )}
        <div className="jobs-layout">
          {/* Filter Sidebar */}
          <form ref={filterRef} id="job-search-filters" className={`filter-panel ${mobileFiltersOpen ? 'mobile-open' : ''}`} onSubmit={applyFilters}>
            <h2>{aiMode ? 'Lọc kết quả AI' : 'Tìm việc làm'}</h2>

            {!aiMode && <div>
              <label className="filter-label">Từ khóa</label>
              <div className="input-icon-wrap">
                <span className="input-icon">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                  </svg>
                </span>
                <input
                  placeholder="Tên vị trí, kỹ năng..."
                  value={draftFilters.search || ''}
                  onChange={(e) => updateDraft('search', e.target.value)}
                />
              </div>
            </div>}

            <ProvinceLocationSelect
              label="Địa điểm"
              value={draftFilters.location || ''}
              onChange={(value) => updateDraft('location', value)}
              allowRemote
              placeholder="Tất cả địa điểm"
            />

            {!aiMode && <div>
              <label className="filter-label">Kỹ năng</label>
              <input
                placeholder="Java, React, Python..."
                value={draftFilters.skills || ''}
                onChange={(e) => updateDraft('skills', e.target.value)}
              />
            </div>}

            {!aiMode && <div>
              <label className="filter-label">Ngành / danh mục</label>
              <select value={draftFilters.category || ''} onChange={(e) => updateDraft('category', e.target.value)}>
                <option value="">Tất cả danh mục</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.slug || category.id}>{category.name}</option>
                ))}
              </select>
            </div>}

            <div className="filter-split">
              <div>
                <label className="filter-label">Lương từ</label>
                <input
                  type="number"
                  min="0"
                  inputMode="numeric"
                  placeholder="10,000,000"
                  value={draftFilters.minSalary || ''}
                  onChange={(e) => updateDraft('minSalary', e.target.value ? Number(e.target.value) : undefined)}
                />
              </div>
              <div>
                <label className="filter-label">Lương đến</label>
                <input
                  type="number"
                  min="0"
                  inputMode="numeric"
                  placeholder="30,000,000"
                  value={draftFilters.maxSalary || ''}
                  onChange={(e) => updateDraft('maxSalary', e.target.value ? Number(e.target.value) : undefined)}
                />
              </div>
            </div>

            {!aiMode && <div>
              <label className="filter-label">Kinh nghiệm</label>
              <select
                value={draftFilters.experienceLevel || ''}
                onChange={(e) => updateDraft('experienceLevel', e.target.value)}
              >
                <option value="">Tất cả cấp độ</option>
                <option value="INTERN">Thực tập sinh</option>
                <option value="FRESHER">Fresher</option>
                <option value="JUNIOR">Junior</option>
                <option value="MIDDLE">Middle</option>
                <option value="SENIOR">Senior</option>
              </select>
            </div>}

            <div className="filter-split">
              <div>
                <label className="filter-label" htmlFor="job-filter-job-type">Loại công việc</label>
                <select id="job-filter-job-type" value={draftFilters.jobType || ''} onChange={(e) => updateDraft('jobType', e.target.value)}>
                  <option value="">Tất cả</option>
                  <option value="full_time">Toàn thời gian</option>
                  <option value="part_time">Bán thời gian</option>
                  <option value="contract">Hợp đồng</option>
                  <option value="internship">Thực tập</option>
                  <option value="freelance">Tự do</option>
                </select>
              </div>
              <div>
                <label className="filter-label" htmlFor="job-filter-work-mode">Hình thức</label>
                <select id="job-filter-work-mode" value={draftFilters.workMode || ''} onChange={(e) => updateDraft('workMode', e.target.value)}>
                  <option value="">Tất cả</option>
                  <option value="onsite">Tại văn phòng</option>
                  <option value="remote">Từ xa</option>
                  <option value="hybrid">Kết hợp</option>
                </select>
              </div>
            </div>

            {!aiMode && <div>
              <label className="filter-label">Sắp xếp</label>
              <select
                value={draftFilters.sort || 'newest'}
                onChange={(e) => updateDraft('sort', e.target.value)}
              >
                <option value="newest">Mới nhất</option>
                <option value="salary">Lương cao nhất</option>
                <option value="deadline">Gần deadline</option>
              </select>
            </div>}

            <button type="submit" style={{ width: '100%' }}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
              </svg>
              {aiMode ? 'Lọc top 10' : 'Tìm kiếm'}
            </button>
            {hasActiveFilters && (
              <button type="button" className="outline" style={{ width: '100%' }} onClick={resetFilters}>
                Xóa bộ lọc
              </button>
            )}

            {filterError && <div className="error-panel" role="alert">{filterError}</div>}
            {!aiMode && error && <div className="error-panel" role="alert">{error}</div>}
          </form>

          {/* Job List */}
          <div>
            <div className="jobs-list-header">
              <h1>{aiMode ? 'Kết quả được AI xếp hạng' : 'Việc làm đang tuyển'}</h1>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {!aiMode && localStorage.getItem('role') === 'CANDIDATE' && (
                  <button type="button" className="outline sm"
                    onClick={() => navigate(`/candidate/job-alerts?${toJobSearchParams(filters, 0, pageSize)}`)}>
                    Lưu thành cảnh báo
                  </button>
                )}
                {aiMode
                  ? !aiLoading && <span className="chip neutral">{filteredAiItems.length}/{aiResult?.items.length || 0} kết quả</span>
                  : !loading && <span className="chip neutral">{totalElements} kết quả</span>}
                {!aiMode && <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  Hiển thị
                  <select
                    aria-label="Số việc làm mỗi trang"
                    value={pageSize}
                    onChange={(event) => setParams(toJobSearchParams(filters, 0, Number(event.target.value)))}
                  >
                    <option value={12}>12</option>
                    <option value={24}>24</option>
                    <option value={48}>48</option>
                  </select>
                </label>}
              </div>
            </div>

            {activeFilterEntries.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 14 }} aria-label="Bộ lọc đang áp dụng">
                {activeFilterEntries.map(([key, value]) => (
                  <button key={key} type="button" className="chip neutral" onClick={() => clearFilter(key)}>
                    {String(value)} ×
                  </button>
                ))}
              </div>
            )}

            {(aiMode ? aiLoading : loading) ? (
              isEmployer
                ? <SyncedJobSplitGrid items={jobSkeletons} />
                : <div className="job-grid">{jobSkeletons}</div>
            ) : (aiMode ? filteredAiItems.length === 0 : jobs.length === 0) ? (
              jobsEmpty
            ) : isEmployer ? (
              <SyncedJobSplitGrid items={jobCards} />
            ) : (
              <div className="job-grid">{jobCards}</div>
            )}
            {!aiMode && !loading && totalPages > 1 && (
              <nav className="pagination-bar" aria-label="Phân trang việc làm">
                <button type="button" className="outline" disabled={page === 0} onClick={() => changePage(page - 1)}>
                  Trước
                </button>
                {Array.from({ length: totalPages }, (_, index) => index)
                  .filter((index) => index === 0 || index === totalPages - 1 || Math.abs(index - page) <= 1)
                  .map((index) => (
                    <button
                      key={index}
                      type="button"
                      className={index === page ? '' : 'outline'}
                      aria-current={index === page ? 'page' : undefined}
                      onClick={() => changePage(index)}
                    >
                      {index + 1}
                    </button>
                  ))}
                <button type="button" className="outline" disabled={page >= totalPages - 1} onClick={() => changePage(page + 1)}>
                  Sau
                </button>
              </nav>
            )}
          </div>
        </div>
      </div>
      <AiConsentDialog
        open={showAiConsent}
        policyVersion={aiStatus?.policyVersion || ''}
        busy={consentBusy}
        error={showAiConsent ? aiError : ''}
        onAccept={() => void acceptAiConsent()}
        onClose={() => setShowAiConsent(false)}
      />
    </Shell>
  );
}

function formatAiDate(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(parsed);
}

function AiRecommendationCard({ item }: { item: AiJobSearchItem }) {
  return (
    <section className="ai-recommendation-card" aria-labelledby={`ai-job-${item.job.id}`}>
      <div className="ai-recommendation-summary">
        <div className="ai-rank-score" role="img" aria-label={`Xếp hạng ${item.rank}, phù hợp ${item.matchScore} phần trăm`}>
          <span>#{item.rank}</span>
          <strong>{item.matchScore}%</strong>
        </div>
        <div>
          <h2 id={`ai-job-${item.job.id}`}>Vì sao công việc này phù hợp?</h2>
          <p>{item.reason}</p>
        </div>
      </div>
      <div className="ai-skill-groups">
        <div>
          <span className="ai-skill-label">Kỹ năng khớp</span>
          <div className="ai-skill-list">
            {item.matchedSkills.length > 0
              ? item.matchedSkills.map((skill) => <span className="chip match" key={skill}>{skill}</span>)
              : <span className="muted">Chưa có kỹ năng trùng rõ ràng</span>}
          </div>
        </div>
        <div>
          <span className="ai-skill-label">Nên bổ sung</span>
          <div className="ai-skill-list">
            {item.missingSkills.length > 0
              ? item.missingSkills.map((skill) => <span className="chip warning" key={skill}>{skill}</span>)
              : <span className="muted">Không có khoảng trống kỹ năng nổi bật</span>}
          </div>
        </div>
      </div>
      <JobCard job={item.job} />
    </section>
  );
}

function AiConsentDialog({
  open,
  policyVersion,
  busy,
  error,
  onAccept,
  onClose,
}: {
  open: boolean;
  policyVersion: string;
  busy: boolean;
  error: string;
  onAccept: () => void;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      className="ai-consent-dialog"
      aria-labelledby="ai-consent-title"
      aria-describedby="ai-consent-description"
      onClose={onClose}
      onCancel={(event) => { if (busy) event.preventDefault(); }}
    >
      <div className="ai-consent-content">
        <p className="eyebrow">Quyền riêng tư · {policyVersion}</p>
        <h2 id="ai-consent-title">Cho phép AI phân tích dữ liệu nghề nghiệp?</h2>
        <p id="ai-consent-description">
          Hệ thống sử dụng kỹ năng, kinh nghiệm, học vấn, dự án và nội dung nghề nghiệp trong CV mặc định để xếp hạng công việc.
        </p>
        <ul>
          <li>Không gửi email, số điện thoại, ngày sinh, tên đầy đủ hoặc file CV gốc.</li>
          <li>Kết quả chỉ là gợi ý cho bạn, không phải quyết định tuyển dụng.</li>
          <li>Bạn có thể thu hồi đồng ý và xóa cache gợi ý bất cứ lúc nào.</li>
        </ul>
        {error && <div className="error-panel" role="alert">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="outline" onClick={() => dialogRef.current?.close()} disabled={busy}>
            Để sau
          </button>
          <button type="button" onClick={onAccept} disabled={busy} autoFocus>
            {busy ? 'Đang xác nhận…' : 'Đồng ý chính sách'}
          </button>
        </div>
      </div>
    </dialog>
  );
}

// ─── JOB CARD ───────────────────────────────────────────────────────────────
function SyncedJobSplitGrid({ items }: { items: ReactNode[] }) {
  const gridRef = useRef<HTMLDivElement>(null);
  const leftRef = useRef<HTMLDivElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);
  const syncing = useRef(false);
  const leftItems = items.filter((_, index) => index % 2 === 0);
  const rightItems = items.filter((_, index) => index % 2 === 1);

  function syncFrom(source: HTMLDivElement, target: HTMLDivElement) {
    if (syncing.current) return;
    syncing.current = true;
    const sourceMax = source.scrollHeight - source.clientHeight;
    const targetMax = target.scrollHeight - target.clientHeight;
    target.scrollTop = sourceMax > 0 ? (source.scrollTop / sourceMax) * Math.max(targetMax, 0) : 0;
    requestAnimationFrame(() => {
      syncing.current = false;
    });
  }

  useEffect(() => {
    const grid = gridRef.current;
    const left = leftRef.current;
    const right = rightRef.current;
    if (!grid || !left || !right) return;

    function onWheel(event: WheelEvent) {
      if (window.matchMedia('(max-width: 860px)').matches) return;
      event.preventDefault();
      syncing.current = true;
      left.scrollTop += event.deltaY;
      right.scrollTop += event.deltaY;
      requestAnimationFrame(() => {
        syncing.current = false;
      });
    }

    grid.addEventListener('wheel', onWheel, { passive: false });
    return () => grid.removeEventListener('wheel', onWheel);
  }, [items.length]);

  return (
    <div ref={gridRef} className="job-split-grid">
      <div
        ref={leftRef}
        className="job-split-pane"
        onScroll={(event) => rightRef.current && syncFrom(event.currentTarget, rightRef.current)}
      >
        {leftItems}
      </div>
      <div
        ref={rightRef}
        className="job-split-pane"
        onScroll={(event) => leftRef.current && syncFrom(event.currentTarget, leftRef.current)}
      >
        {rightItems}
      </div>
    </div>
  );
}

function JobCard({ job }: { job: Job }) {
  const location = useLocation();
  const initials = job.company.name.slice(0, 2).toUpperCase();
  const showFooter = Boolean(job.saved || job.applied);
  return (
    <Link
      to={`/jobs/${job.id}`}
      state={{ from: `${location.pathname}${location.search}`, scrollY: window.scrollY } satisfies NavigationState}
      className="job-card-link"
    >
      <article className={`job-card${job.featured ? ' is-featured' : ''}${showFooter ? ' has-footer' : ''}`}>
        <div className="job-card-header">
          <div className="job-company-logo">
            {job.company.logoUrl ? (
              <img src={job.company.logoUrl} alt={job.company.name} />
            ) : (
              <IconBuilding size={22} style={{ opacity: 0.6 }} />
            )}
          </div>
          <div className="job-card-info">
            <div className="job-card-title-row">
              <h3>{job.title}</h3>
              {job.featured && <span className="job-featured-badge">Nổi bật</span>}
            </div>
            <p className="job-card-company">{job.company.name}</p>
            <div className="job-card-meta">
              {job.location && (
                <span className="job-meta-badge" title={job.location}>
                  <IconMapPin size={12} />
                  <span className="job-meta-badge-text">{job.location}</span>
                </span>
              )}
              {job.experienceLevel && (
                <span className="job-meta-badge">
                  <IconBriefcase size={12} />
                  <span className="job-meta-badge-text">{job.experienceLevel}</span>
                </span>
              )}
              {(job.salaryMin || job.salaryMax) && (
                <span className="job-meta-badge salary">
                  <IconWallet size={12} />
                  <span className="job-meta-badge-text">{formatMoney(job.salaryMin)} – {formatMoney(job.salaryMax)}</span>
                </span>
              )}
            </div>
          </div>
        </div>

        {showFooter && (
          <div className="job-card-footer">
            {job.saved && <span className="chip"><IconBookmark size={12} /> Đã lưu</span>}
            {job.applied && <span className="chip neutral"><IconClipboard size={12} /> Đã nộp</span>}
          </div>
        )}
      </article>
    </Link>
  );
}

function CompanyDetailPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const page = jobPageFromParams(params);
  const [company, setCompany] = useState<PublicCompany | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const data = await jobService.getCompany(id, page, 10);
      setCompany(data);
      if (data.openJobs.totalPages > 0 && page >= data.openJobs.totalPages) {
        setParams({ page: String(data.openJobs.totalPages - 1) }, { replace: true });
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [id, page, setParams]);

  useEffect(() => { void load(); }, [load]);

  return (
    <Shell>
      <div className="jobs-shell" style={{ maxWidth: 1100, margin: '0 auto' }}>
        {loading ? (
          <div className="card" style={{ padding: 48, textAlign: 'center' }}>Đang tải thông tin công ty...</div>
        ) : error ? (
          <div className="error-panel" role="alert">{error} <button className="outline sm" onClick={() => void load()}>Thử lại</button></div>
        ) : company && (
          <>
            <section className="card" style={{ marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <div className="job-company-logo" style={{ width: 72, height: 72 }}>
                  {company.logoUrl ? <img src={company.logoUrl} alt={company.name} /> : <IconBuilding size={36} style={{ opacity: 0.6 }} />}
                </div>
                <div>
                  <h1 style={{ margin: 0 }}>{company.name}</h1>
                  <span className={`chip ${company.verified ? 'match' : 'neutral'}`}>
                    {company.verified ? '✓ Đã xác minh' : 'Chưa xác minh'}
                  </span>
                </div>
              </div>
              {company.description && <p style={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{company.description}</p>}
              <div className="job-card-meta" style={{ marginTop: 16 }}>
                {company.industry && <span className="job-meta-badge">Ngành: {company.industry}</span>}
                {company.companySize && <span className="job-meta-badge">Quy mô: {company.companySize} nhân sự</span>}
                {company.location && <span className="job-meta-badge">📍 {company.location}</span>}
                {company.website && <a href={company.website} target="_blank" rel="noopener noreferrer">Website công ty</a>}
              </div>
              {company.locations.length > 0 && (
                <div style={{ marginTop: 18 }}>
                  <strong>Địa điểm làm việc</strong>
                  <ul>{company.locations.map((item) => <li key={item.id}>{[item.branchName, item.address, item.city].filter(Boolean).join(' · ')}</li>)}</ul>
                </div>
              )}
            </section>
            <h2>Việc làm đang mở</h2>
            {company.openJobs.items.length === 0 ? (
              <div className="card empty-state">Công ty chưa có vị trí đang tuyển.</div>
            ) : (
              <div className="job-grid">{company.openJobs.items.map((job) => <JobCard key={job.id} job={job} />)}</div>
            )}
            <PaginationControls page={page} totalPages={company.openJobs.totalPages} label="Phân trang việc làm của công ty"
              onPageChange={(nextPage) => setParams(nextPage > 0 ? { page: String(nextPage) } : {})} />
          </>
        )}
      </div>
    </Shell>
  );
}

// ─── JOB DETAIL PAGE ────────────────────────────────────────────────────────
function JobDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigationState = location.state as NavigationState | null;
  const backTo = internalOrigin(navigationState, '/jobs');
  const [job, setJob] = useState<Job | null>(null);
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [selectedResume, setSelectedResume] = useState('');
  const [message, setMessage] = useState('');
  const [applying, setApplying] = useState(false);
  const [savingJob, setSavingJob] = useState(false);
  const [showApplyModal, setShowApplyModal] = useState(false);
  const [applyError, setApplyError] = useState('');
  const [applyPlanLimit, setApplyPlanLimit] = useState(false);
  const [messagePlanLimit, setMessagePlanLimit] = useState(false);
  const token = getToken();
  const role = localStorage.getItem('role');
  const isCandidate = Boolean(token && role === 'CANDIDATE');
  const [showReportForm, setShowReportForm] = useState(false);
  const [reportReason, setReportReason] = useState('misleading');
  const [reportDescription, setReportDescription] = useState('');
  const [reporting, setReporting] = useState(false);
  const [reporterProfile, setReporterProfile] = useState<CandidateProfile | null>(null);
  const [jobError, setJobError] = useState(false);
  const [saveBurst, setSaveBurst] = useState(false);
  const [applyBurst, setApplyBurst] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const jobData = await jobService.getById(id);
      setJob(jobData);
      if (isCandidate) {
        Promise.all([
          candidateService.getCvs(0, 100).then((result) => result.items).catch(() => []),
          candidateService.getProfile().catch(() => null),
        ]).then(([uploadedCvs, profile]) => {
          setCvs(uploadedCvs);
          setReporterProfile(profile);
          const defaultCv = uploadedCvs.find((item) => item.defaultCv) || uploadedCvs[0];
          setSelectedResume(defaultCv ? `uploaded:${defaultCv.id}` : '');
        });
      }
    } catch (err) {
      setJobError(true);
    }
  }, [id, isCandidate]);

  useEffect(() => { void load(); }, [load]);

  async function toggleSave() {
    if (!job) return;
    const previousJob = job;
    const nextSaved = !job.saved;
    setSavingJob(true);
    setMessage('');
    setJob({ ...job, saved: nextSaved });
    try {
      if (previousJob.saved) await candidateService.unsaveJob(previousJob.id);
      else await candidateService.saveJob(previousJob.id);
      setMessage(nextSaved ? 'Đã lưu việc làm.' : 'Đã bỏ lưu việc làm.');
      if (nextSaved) {
        setSaveBurst(true);
        window.setTimeout(() => setSaveBurst(false), 900);
      }
    } catch (err) {
      setJob(previousJob);
      setMessage(readError(err));
    } finally {
      setSavingJob(false);
    }
  }

  async function apply(payload?: ApplyJobPayload) {
    if (!job) return;
    if (payload) {
      setApplying(true);
      setApplyError('');
      setApplyPlanLimit(false);
      try {
        let resumeType = '';
        let resumeId = '';
        if (payload.file) {
          const uploaded = await candidateService.uploadCv(payload.file);
          resumeType = 'uploaded';
          resumeId = uploaded.id;
        } else {
          [resumeType, resumeId] = payload.resume.split(':');
        }
        await candidateService.apply(
          job.id,
          resumeType === 'uploaded' ? resumeId : undefined,
          resumeType === 'builder' ? resumeId : undefined,
          payload.preferredLocation,
          payload.coverLetter,
        );
        setMessage('✅ Đã nộp hồ sơ ứng tuyển thành công!');
        setShowApplyModal(false);
        setApplyBurst(true);
        window.setTimeout(() => setApplyBurst(false), 1400);
        await load();
      } catch (err) {
        setApplyError(readError(err));
        setApplyPlanLimit(isPlanLimitError(err));
      } finally {
        setApplying(false);
      }
      return;
    }
    const [resumeType, resumeId] = selectedResume.split(':');
    setApplying(true);
    setMessagePlanLimit(false);
    try {
      await candidateService.apply(
        job.id,
        resumeType === 'uploaded' ? resumeId : undefined,
        resumeType === 'builder' ? resumeId : undefined,
      );
      setMessage('✅ Đã nộp hồ sơ ứng tuyển thành công!');
      setApplyBurst(true);
      window.setTimeout(() => setApplyBurst(false), 1400);
      await load();
    } catch (err) {
      setMessage(readError(err));
      setMessagePlanLimit(isPlanLimitError(err));
    } finally {
      setApplying(false);
    }
  }

  async function openCv(id: string) {
    try {
      openBlobInNewTab(await candidateService.downloadCv(id));
    } catch (err) {
      setApplyError(readError(err));
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

  if (jobError) {
    return (
      <Shell>
        <div style={{ maxWidth: 1180, margin: '20px auto 0', padding: '0 24px' }}>
          <Link
            to={backTo}
            state={{ restoreScrollY: navigationState?.scrollY } satisfies NavigationState}
            style={{ color: 'var(--primary)', fontWeight: 600 }}
          >
            ← Quay lại
          </Link>
        </div>
        <div style={{ padding: '48px 24px', textAlign: 'center' }}>
          <div style={{ background: '#fee2e2', width: '64px', height: '64px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#b91c1c" strokeWidth="2.5">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
          </div>
          <h2 style={{ marginTop: 0, color: '#0f172a' }}>Tin này đã không còn hoạt động</h2>
          <p className="muted" style={{ marginBottom: 24 }}>Việc làm bạn đang tìm kiếm đã bị xóa hoặc không còn tồn tại.</p>
        </div>
      </Shell>
    );
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
  const jobFacts = [
    job.jobType && ['Loại hình', formatJobMetaValue(job.jobType)],
    job.workMode && ['Hình thức làm việc', formatJobMetaValue(job.workMode)],
    job.salaryType && ['Kiểu lương', formatJobMetaValue(job.salaryType)],
    job.deadline && ['Hạn ứng tuyển', formatDate(job.deadline)],
    job.workingTime && ['Thời gian làm việc', job.workingTime],
    job.vacancies !== undefined && job.vacancies !== null && ['Số lượng tuyển', String(job.vacancies)],
    job.companyLocation && ['Địa điểm công ty', [job.companyLocation.branchName, job.companyLocation.address, job.companyLocation.city].filter(Boolean).join(' - ')],
  ].filter(Boolean) as [string, string][];

  return (
    <Shell>
      <div>
        <div style={{ maxWidth: 1180, margin: '20px auto 0', padding: '0 24px' }}>
          <Link
            to={backTo}
            state={{ restoreScrollY: navigationState?.scrollY } satisfies NavigationState}
            style={{ color: 'var(--primary)', fontWeight: 600 }}
          >
            ← Quay lại danh sách
          </Link>
        </div>
        <div className="job-detail-layout">
        {/* Left: Job details */}
        <div className="job-detail-main">
          {/* Header card */}
          <motion.div className="job-detail-header" variants={fadeUp} initial="initial" animate="animate"
            transition={{ duration: 0.28, ease: EASE_OUT }}>
            <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
              <div className="job-company-logo" style={{ width: 64, height: 64, borderRadius: 12 }}>
                {job.company.logoUrl ? (
                  <img src={job.company.logoUrl} alt={job.company.name} />
                ) : (
                  <IconBuilding size={32} style={{ opacity: 0.6 }} />
                )}
              </div>
              <div style={{ flex: 1 }}>
                <Link className="eyebrow" to={`/companies/${job.company.id}`}>{job.company.name}</Link>
                <h1 className="job-detail-title" style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  {job.title}
                  {job.featured && (
                    <span
                      style={{
                        display: 'inline-flex',
                        padding: '4px 10px',
                        borderRadius: 999,
                        background: '#fef3c7',
                        color: '#92400e',
                        fontSize: '0.8rem',
                        fontWeight: 700,
                      }}
                    >
                      Nổi bật
                    </span>
                  )}
                </h1>
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

          {job.skills?.length > 0 && (
            <motion.div className="job-detail-section" variants={fadeUp} initial="initial" animate="animate"
              transition={{ duration: 0.28, ease: EASE_OUT, delay: 0.12 }}>
              <h2>Kỹ năng</h2>
              <div className="chip-row">
                {job.skills.map((skill) => (
                  <span key={skill} className="chip match">{skill}</span>
                ))}
              </div>
            </motion.div>
          )}

          {jobFacts.length > 0 && (
            <motion.div className="job-detail-section" variants={fadeUp} initial="initial" animate="animate"
              transition={{ duration: 0.28, ease: EASE_OUT, delay: 0.14 }}>
              <h2>Thông tin công việc</h2>
              <div className="data-table" style={{ gap: 0 }}>
                {jobFacts.map(([label, value]) => (
                  <div key={label} className="data-row" style={{ gridTemplateColumns: '180px 1fr' }}>
                    <span className="muted">{label}</span>
                    <strong>{value}</strong>
                  </div>
                ))}
              </div>
            </motion.div>
          )}

          {job.benefits && (
            <motion.div className="job-detail-section" variants={fadeUp} initial="initial" animate="animate"
              transition={{ duration: 0.28, ease: EASE_OUT, delay: 0.16 }}>
              <h2>Phúc lợi</h2>
              <p style={{ color: 'var(--on-muted)', lineHeight: 1.7, whiteSpace: 'pre-line' }}>{job.benefits}</p>
            </motion.div>
          )}
        </div>

        {/* Right: Apply panel */}
        <aside className="apply-panel">
          {isCandidate ? (
            <>
              <button
                className={`outline saved-job-button ${job.saved ? 'is-saved' : ''}${saveBurst ? ' just-saved' : ''}`}
                onClick={toggleSave}
                disabled={savingJob}
                style={{ width: '100%' }}
              >
                {job.saved ? <><IconBookmark size={14} /> Bỏ lưu</> : <><IconBookmark size={14} /> Lưu việc làm</>}
              </button>

              {false && cvs.length > 0 && (
                <div>
                  <label className="filter-label" style={{ marginBottom: 8 }}>Chọn CV</label>
                  <select value={selectedResume} onChange={(e) => setSelectedResume(e.target.value)}>
                    <option value="">Chọn CV của bạn</option>
                    {cvs.map((cv) => (
                      <option key={cv.id} value={`uploaded:${cv.id}`}>{cv.originalFileName}</option>
                    ))}
                  </select>
                </div>
              )}

              <button
                className={`apply-now-button${job.applied || applyBurst ? ' is-applied' : ''}${applyBurst ? ' just-applied' : ''}`}
                onClick={() => {
                  setApplyError('');
                  setApplyPlanLimit(false);
                  setShowApplyModal(true);
                }}
                disabled={job.applied || applying}
                style={{ width: '100%', minHeight: 44 }}
              >
                {applying ? 'Đang gửi...' : job.applied ? '✓ Đã ứng tuyển' : 'Ứng tuyển ngay'}
              </button>

              <AnimatePresence>
                {message && (
                  messagePlanLimit ? (
                    <PlanLimitAlert message={message} />
                  ) : (
                  <motion.div
                    className={`${isSuccessMessage(message) ? 'success-panel' : 'error-panel'}${applyBurst && isSuccessMessage(message) ? ' just-applied' : ''}${saveBurst && isSuccessMessage(message) ? ' just-saved' : ''}`}
                    variants={scaleIn} initial="initial" animate="animate" exit="exit"
                    transition={{ duration: 0.22, ease: EASE_OUT }}
                  >
                    {message}
                  </motion.div>
                  )
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
                    <span style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                      Mô tả thêm
                      <small>{reportDescription.length}/2000</small>
                    </span>
                    <textarea
                      value={reportDescription}
                      onChange={(e) => setReportDescription(e.target.value.slice(0, 2000))}
                      placeholder="Mô tả ngắn vấn đề bạn gặp phải..."
                      maxLength={2000}
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
          ) : token ? (
            <Link className="button-link outline" to={role === 'EMPLOYER' ? '/employer' : '/'} style={{ width: '100%', textAlign: 'center' }}>
              Vao dashboard cua ban
            </Link>
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
      <AnimatePresence>
        {showApplyModal && (
          <ApplyJobModal
            job={job}
            cvs={cvs}
            defaultResume={selectedResume}
            busy={applying}
            error={applyError}
            planLimitError={applyPlanLimit}
            onClose={() => !applying && setShowApplyModal(false)}
            onSubmit={apply}
            onOpenCv={openCv}
          />
        )}
      </AnimatePresence>
      </div>
    </Shell>
  );
}

// ─── CANDIDATE LAYOUT ────────────────────────────────────────────────────────
function CandidateLayout() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const loadUnreadCount = useCallback(() => {
    candidateService.getNotifications(0, 100).then(data => {
      setUnreadCount(data.items.filter(n => !n.read).length);
    }).catch(() => {});
  }, []);

  useEffect(() => { loadUnreadCount(); }, [loadUnreadCount]);
  useEffect(() => {
    document.body.classList.toggle('candidate-nav-locked', mobileNavOpen);
    return () => document.body.classList.remove('candidate-nav-locked');
  }, [mobileNavOpen]);
  useCandidateRealtime((event) => {
    if (event.type === 'NOTIFICATION_UPDATED' || event.type === 'REALTIME_RECONNECTED') {
      loadUnreadCount();
    }
  });

  function logout() { void endAuthenticatedSession(() => navigate('/login')); }

  const navItems = [
    { to: '/', end: true, icon: <IconHome />, label: 'Trang chủ' },
    { to: '/candidate', end: true, icon: <IconDashboard />, label: 'Dashboard' },
    { to: '/candidate/profile', icon: <IconProfile />, label: 'Hồ sơ' },
    { to: '/candidate/account', icon: <IconLock />, label: 'Tài khoản' },
    { to: '/candidate/cvs', icon: <IconDocument />, label: 'CV của tôi' },
    { to: '/candidate/saved-jobs', icon: <IconBookmark />, label: 'Việc đã lưu' },
    { to: '/candidate/applications', icon: <IconClipboard />, label: 'Ứng tuyển' },
    { to: '/candidate/ai-interviews', icon: <IconRobot />, label: 'AI Interview' },
    { to: '/candidate/notifications', icon: <IconBell />, label: 'Thông báo' },
    { to: '/candidate/job-alerts', icon: <IconClock />, label: 'Cảnh báo việc làm' },
    { to: '/candidate/subscription', icon: <IconGem />, label: 'Gói dịch vụ' },
  ];

  return (
    <div className="candidate-shell">
      <button type="button" className="candidate-mobile-nav-toggle" aria-expanded={mobileNavOpen}
        aria-controls="candidate-navigation" onClick={() => setMobileNavOpen((value) => !value)}>
        {mobileNavOpen ? 'Đóng menu' : 'Menu ứng viên'}
      </button>
      {mobileNavOpen && (
        <button
          type="button"
          className="candidate-nav-backdrop"
          aria-label="Đóng menu"
          onClick={() => setMobileNavOpen(false)}
        />
      )}
      <aside id="candidate-navigation" className={`candidate-nav portal-nav ${mobileNavOpen ? 'mobile-open' : ''}`}>
        <Link className="brand portal-nav-brand" to="/">
          <span className="portal-nav-brand-mark"><IconBrandBriefcase /></span>
          <span className="portal-nav-brand-text">
            <strong>SRP Candidate</strong>
            <span>Cổng ứng viên</span>
          </span>
        </Link>

        <nav className="portal-nav-section" aria-label="Menu ứng viên">
          {navItems.map(({ to, end, icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={() => setMobileNavOpen(false)}
              className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            >
              <span className="sidebar-link-icon">{icon}</span>
              <span className="sidebar-link-label">{label}</span>
              {to === '/candidate/notifications' && unreadCount > 0 && (
                <span className="portal-nav-badge">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="portal-nav-footer">
          <NavLink to="/jobs" className="sidebar-link" onClick={() => setMobileNavOpen(false)}>
            <span className="sidebar-link-icon"><IconSearch /></span>
            <span className="sidebar-link-label">Tìm việc làm</span>
          </NavLink>
          <button type="button" className="sidebar-link portal-nav-logout" onClick={logout}>
            <span className="sidebar-link-icon"><IconLogout /></span>
            <span className="sidebar-link-label">Đăng xuất</span>
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
  const [onboardingStatus, setOnboardingStatus] = useState<'PENDING' | 'COMPLETED' | 'SKIPPED'>('COMPLETED');
  const [metrics, setMetrics] = useState({
    savedJobs: 0,
    applications: 0,
    aiSessions: 0,
    unreadNotifications: 0,
  });
  const [loading, setLoading] = useState(true);
  const userName = localStorage.getItem('email')?.split('@')[0] || 'bạn';

  useEffect(() => {
    async function loadHome() {
      try {
        const [recommendedJobs, savedJobs, applications, aiSessions, notifications, onboarding] = await Promise.all([
          jobService.recommendations().catch(() => []),
          candidateService.getSavedJobs(0, 1).catch(() => null),
          candidateService.getApplications(0, 1).catch(() => null),
          aiInterviewService.sessions().catch(() => []),
          candidateService.getNotifications(0, 100).catch(() => null),
          candidateService.getOnboarding().catch(() => null),
        ]);
        setRecommendations(recommendedJobs);
        if (onboarding?.status) setOnboardingStatus(onboarding.status);
        setMetrics({
          savedJobs: savedJobs?.totalItems || 0,
          applications: applications?.totalItems || 0,
          aiSessions: aiSessions.length,
          unreadNotifications: notifications?.items.filter((item) => !item.read).length || 0,
        });
      } finally {
        setLoading(false);
      }
    }
    void loadHome();
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

      {onboardingStatus === 'SKIPPED' && (
        <div className="notice-panel" style={{ marginTop: 18, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
          <div><strong>Hoàn thiện mong muốn nghề nghiệp</strong><p className="muted" style={{ margin: '4px 0 0' }}>Bổ sung vị trí, mức lương và địa điểm để nhận gợi ý sát hơn.</p></div>
          <Link className="button-link outline sm" to="/candidate/onboarding">Hoàn thiện ngay</Link>
        </div>
      )}

      <div className="metric-grid" style={{ marginTop: 24 }}>
        {[
          { label: 'Việc đã lưu', value: metrics.savedJobs, icon: <IconBookmark size={22} />, to: '/candidate/saved-jobs' },
          { label: 'Đang ứng tuyển', value: metrics.applications, icon: <IconClipboard size={22} />, to: '/candidate/applications' },
          { label: 'Phỏng vấn AI', value: metrics.aiSessions, icon: <IconRobot size={22} />, to: '/candidate/ai-interviews' },
          { label: 'Thông báo mới', value: metrics.unreadNotifications, icon: <IconBell size={22} />, to: '/candidate/notifications' },
        ].map(({ label, value, icon, to }, i) => (
          <motion.div
            key={label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.06 }}
          >
            <Link to={to} style={{ display: 'block', textDecoration: 'none' }}>
              <div className="metric-card metric-card-link">
                <div className="metric-card-icon">{icon}</div>
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
                <div className="empty-state-icon"><IconBriefcase size={36} /></div>
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
            { icon: <IconProfile size={22} />, title: 'Cập nhật hồ sơ', desc: 'Tăng cơ hội được tuyển dụng', to: '/candidate/profile' },
            { icon: <IconDocument size={22} />, title: 'Quản lý CV', desc: 'Tải lên hoặc tạo CV mới', to: '/candidate/cvs' },
            { icon: <IconRobot size={22} />, title: 'Luyện phỏng vấn AI', desc: 'Chuẩn bị cho buổi phỏng vấn thật', to: '/candidate/ai-interviews' },
            { icon: <IconGem size={22} />, title: 'Gói dịch vụ', desc: 'Xem quyền lợi của bạn', to: '/candidate/subscription' },
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
  const [skillDraft, setSkillDraft] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const savedSnapshotRef = useRef('');

  useEffect(() => {
    candidateService.getProfile().then((data) => {
      setProfile(data);
      savedSnapshotRef.current = JSON.stringify(data);
    });
  }, []);

  const dirty = Boolean(profile && (JSON.stringify(profile) !== savedSnapshotRef.current || skillDraft.trim()));

  useEffect(() => {
    function warnUnsaved(event: BeforeUnloadEvent) {
      if (!dirty) return;
      event.preventDefault();
    }
    window.addEventListener('beforeunload', warnUnsaved);
    return () => window.removeEventListener('beforeunload', warnUnsaved);
  }, [dirty]);

  async function save() {
    if (!profile) return;
    setSaving(true);
    try {
      const saved = await candidateService.updateProfile(profile);
      setProfile(saved);
      savedSnapshotRef.current = JSON.stringify(saved);
      setMessage('Đã lưu hồ sơ thành công!');
      setTimeout(() => setMessage(''), 3000);
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setSaving(false);
    }
  }

  function addSkill(value = skillDraft) {
    if (!profile) return;
    const normalized = value.trim();
    if (!normalized) return;
    if (!profile.skills.some((skill) => skill.toLocaleLowerCase() === normalized.toLocaleLowerCase())) {
      setProfile({ ...profile, skills: [...profile.skills, normalized] });
    }
    setSkillDraft('');
  }

  function removeSkill(value: string) {
    if (!profile) return;
    setProfile({ ...profile, skills: profile.skills.filter((skill) => skill !== value) });
  }

  function updateSectionItem(section: ProfileSectionKey, index: number, field: string, value: string) {
    if (!profile) return;
    const items = [...(profile[section] || [])].map(asProfileItem);
    items[index] = { ...items[index], [field]: value };
    setProfile({ ...profile, [section]: items });
  }

  function addSectionItem(section: ProfileSectionKey) {
    if (!profile) return;
    setProfile({
      ...profile,
      [section]: [
        ...(profile[section] || []),
        { title: '', organization: '', time: '', description: '' },
      ],
    });
  }

  function removeSectionItem(section: ProfileSectionKey, index: number) {
    if (!profile) return;
    setProfile({ ...profile, [section]: (profile[section] || []).filter((_, itemIndex) => itemIndex !== index) });
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
        <Link className="button-link outline sm" to="/candidate/onboarding">Mong muốn nghề nghiệp</Link>
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

        {!profile.applyReady && (
          <div className="notice-panel" style={{ marginBottom: 20 }} role="status">
            <strong>Hoàn thiện hồ sơ để ứng tuyển</strong>
            <ul style={{ margin: '10px 0 0', paddingLeft: 20 }}>
              {profile.missingReadinessItems.map((code) => (
                <li key={code}>{({
                  FULL_NAME: 'Thêm họ tên',
                  PHONE: 'Thêm số điện thoại hợp lệ',
                  LOCATION: 'Thêm địa điểm hiện tại',
                  SKILLS: 'Thêm ít nhất một kỹ năng',
                  CV: 'Tải lên ít nhất một CV PDF',
                } as Record<string, string>)[code] || code}</li>
              ))}
            </ul>
            {profile.missingReadinessItems.includes('CV') && (
              <Link to="/candidate/cvs" className="button-link outline sm" style={{ marginTop: 10 }}>Đi đến quản lý CV</Link>
            )}
          </div>
        )}

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
          <ProvinceLocationSelect
            label="Địa điểm"
            value={profile.location || ''}
            onChange={(value) => setProfile({ ...profile, location: value })}
            allowRemote
          />
          <label>
            Tiêu đề nghề nghiệp
            <input maxLength={160} value={profile.headline || ''}
              onChange={(e) => setProfile({ ...profile, headline: e.target.value })}
              placeholder="Ví dụ: Backend Developer" />
          </label>
          <label>
            Số năm kinh nghiệm
            <input type="number" min={0} max={80} value={profile.experienceYears ?? 0}
              onChange={(e) => setProfile({ ...profile, experienceYears: Number(e.target.value) })} />
          </label>
          <label>
            Cấp độ kinh nghiệm
            <select value={profile.experienceLevel || ''}
              onChange={(e) => setProfile({ ...profile, experienceLevel: e.target.value || undefined })}>
              <option value="">Chưa chọn</option>
              <option value="intern">Thực tập</option>
              <option value="fresher">Fresher</option>
              <option value="junior">Junior</option>
              <option value="middle">Middle</option>
              <option value="senior">Senior</option>
              <option value="lead">Lead</option>
            </select>
          </label>
          <label>
            LinkedIn
            <input type="url" value={profile.linkedinUrl || ''}
              onChange={(e) => setProfile({ ...profile, linkedinUrl: e.target.value })}
              placeholder="https://linkedin.com/in/..." />
          </label>
          <label>
            Portfolio
            <input type="url" value={profile.portfolioUrl || ''}
              onChange={(e) => setProfile({ ...profile, portfolioUrl: e.target.value })}
              placeholder="https://..." />
          </label>
          <label className="wide">
            Kỹ năng
            <div style={{ display: 'flex', gap: 8 }}>
              <input value={skillDraft} onChange={(e) => setSkillDraft(e.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ',') {
                    event.preventDefault();
                    addSkill();
                  }
                }}
                placeholder="Nhập kỹ năng rồi nhấn Enter" />
              <button type="button" className="outline" onClick={() => addSkill()}>Thêm</button>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 8 }}>
              {profile.skills.map((skill) => (
                <button key={skill} type="button" className="chip neutral" onClick={() => removeSkill(skill)}>
                  {skill} ×
                </button>
              ))}
            </div>
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

        <div style={{ display: 'grid', gap: 16, marginTop: 24 }}>
          {(Object.keys(profileSectionLabels) as ProfileSectionKey[]).map((section) => (
            <div key={section} style={{
              border: '1px solid var(--outline-variant)',
              borderRadius: 'var(--radius-control)',
              padding: 16,
              background: 'var(--surface-container)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
                <h3 style={{ margin: 0, fontSize: '1rem' }}>{profileSectionLabels[section]}</h3>
                <button type="button" className="outline sm" onClick={() => addSectionItem(section)}>
                  + Thêm
                </button>
              </div>

              {(profile[section] || []).length === 0 ? (
                <p className="muted" style={{ margin: 0 }}>Chưa có thông tin.</p>
              ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                  {(profile[section] || []).map((rawItem, index) => {
                    const item = asProfileItem(rawItem);
                    return (
                      <div key={index} className="form-grid two" style={{ paddingTop: 12, borderTop: '1px solid var(--outline-variant)' }}>
                        <label>
                          Tiêu đề
                          <input
                            value={getItemText(item, 'title')}
                            onChange={(e) => updateSectionItem(section, index, 'title', e.target.value)}
                            placeholder="Ví dụ: Software Engineer"
                          />
                        </label>
                        <label>
                          Đơn vị
                          <input
                            value={getItemText(item, 'organization')}
                            onChange={(e) => updateSectionItem(section, index, 'organization', e.target.value)}
                            placeholder="Công ty, trường học, tổ chức"
                          />
                        </label>
                        <label>
                          Thời gian
                          <input
                            value={getItemText(item, 'time')}
                            onChange={(e) => updateSectionItem(section, index, 'time', e.target.value)}
                            placeholder="2023 - nay"
                          />
                        </label>
                        <label className="wide">
                          Mô tả
                          <textarea
                            value={getItemText(item, 'description')}
                            onChange={(e) => updateSectionItem(section, index, 'description', e.target.value)}
                            placeholder="Kết quả, trách nhiệm hoặc thành tựu nổi bật"
                          />
                        </label>
                        <div className="wide" style={{ display: 'flex', justifyContent: 'flex-end' }}>
                          <button type="button" className="danger sm" onClick={() => removeSectionItem(section, index)}>
                            Xóa mục
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 20, paddingTop: 16,
          borderTop: '1px solid var(--outline-variant)' }}>
          <button onClick={save} disabled={saving || !dirty}>
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

// ─── ACCOUNT PAGE ───────────────────────────────────────────────────────────
function AccountPage() {
  const navigate = useNavigate();
  const [account, setAccount] = useState<AccountView | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [deactivatePassword, setDeactivatePassword] = useState('');
  const [showDeactivateConfirm, setShowDeactivateConfirm] = useState(false);
  const [busy, setBusy] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    authService.getAccount()
      .then((data) => {
        setAccount(data);
      })
      .catch((err) => setError(readError(err)));
  }, []);

  useEffect(() => {
    if (!avatarFile) {
      setAvatarPreview('');
      return;
    }
    const previewUrl = URL.createObjectURL(avatarFile);
    setAvatarPreview(previewUrl);
    return () => URL.revokeObjectURL(previewUrl);
  }, [avatarFile]);

  function resetNotice() {
    setMessage('');
    setError('');
  }

  function syncStoredUser(updated: AccountView) {
    const token = getToken();
    if (!token) return;
    setAuthSession(token, {
      id: updated.id,
      email: updated.email,
      role: updated.role,
      status: updated.status,
      emailVerified: updated.emailVerified,
    });
  }

  async function saveAvatar(event: FormEvent) {
    event.preventDefault();
    resetNotice();
    if (!avatarFile) {
      setError('Vui lòng chọn ảnh đại diện.');
      return;
    }
    setBusy('avatar');
    try {
      const updated = await authService.updateAvatar(avatarFile);
      setAccount(updated);
      setAvatarFile(null);
      syncStoredUser(updated);
      setMessage('Đã cập nhật avatar.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault();
    resetNotice();
    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.');
      return;
    }
    setBusy('password');
    try {
      await authService.changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setMessage('Đã đổi mật khẩu.');
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  async function deactivate(event: FormEvent) {
    event.preventDefault();
    resetNotice();
    setShowDeactivateConfirm(true);
  }

  async function confirmDeactivate() {
    setBusy('deactivate');
    try {
      await authService.deactivateAccount(deactivatePassword);
      clearAuthSession();
      setShowDeactivateConfirm(false);
      navigate('/login');
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  if (!account) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
          style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto 12px' }}>
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <p className="muted">Đang tải tài khoản...</p>
      </div>
    );
  }

  const displayName = account.fullName || account.email.split('@')[0];
  const initials = displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'C';

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Tài khoản & Bảo mật</h1>
        <p>Quản lý đăng nhập, email và ảnh đại diện của bạn</p>
      </div>

      {(message || error) && (
        <div className={error ? 'error-panel' : 'success-panel'} style={{ marginBottom: 16 }}>
          {error || message}
        </div>
      )}

      <section className="card" style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <div style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            overflow: 'hidden',
            background: 'var(--primary-soft)',
            border: '2px solid var(--primary-soft)',
            display: 'grid',
            placeItems: 'center',
            color: 'var(--primary)',
            fontWeight: 800,
            fontSize: '1.35rem',
          }}>
            {avatarPreview || account.avatarUrl ? (
              <img src={avatarPreview || account.avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : initials}
          </div>
          <div style={{ flex: '1 1 240px' }}>
            <h2 style={{ margin: 0 }}>{displayName}</h2>
            <p className="muted" style={{ margin: '4px 0 10px' }}>{account.email}</p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <span className={`chip ${account.emailVerified ? 'match' : 'warning'}`}>
                {account.emailVerified ? 'Email đã xác minh' : 'Chờ xác minh email'}
              </span>
              <span className={`chip ${account.status === 'ACTIVE' ? 'match' : 'danger'}`}>
                {account.status === 'ACTIVE' ? 'Đang hoạt động' : account.status}
              </span>
              <span className="chip neutral">{account.role}</span>
            </div>
          </div>
        </div>
      </section>

      <div style={{ display: 'grid', gap: 20 }}>
        <form className="card" onSubmit={saveAvatar}>
          <h2 style={{ marginBottom: 16 }}>Ảnh đại diện</h2>
          <div className="form-grid">
            <label>
              Chọn ảnh từ máy tính
              <input
                type="file"
                accept="image/png,image/jpeg"
                onChange={(event) => setAvatarFile(event.target.files?.[0] || null)}
              />
            </label>
          </div>
          <p className="muted" style={{ margin: '10px 0 0', fontSize: '0.85rem' }}>
            Hỗ trợ JPG hoặc PNG, tối đa 2MB, kích thước tối đa 4096x4096.
          </p>
          <button type="submit" disabled={busy === 'avatar'} style={{ marginTop: 16 }}>
            {busy === 'avatar' ? 'Đang tải...' : 'Tải ảnh đại diện'}
          </button>
        </form>

        {!account.passwordLoginEnabled && (
          <div className="notice-panel">
            Tài khoản này đang đăng nhập bằng Google/OAuth và chưa có mật khẩu nội bộ.
          </div>
        )}

        <form className="card" onSubmit={savePassword}>
          <h2 style={{ marginBottom: 16 }}>Đổi mật khẩu</h2>
          <div className="form-grid two">
            <label>
              Mật khẩu hiện tại
              <input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                disabled={!account.passwordLoginEnabled}
                required
              />
            </label>
            <label>
              Mật khẩu mới
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={!account.passwordLoginEnabled}
                required
              />
            </label>
            <label>
              Nhập lại mật khẩu mới
              <input
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={!account.passwordLoginEnabled}
                required
              />
            </label>
          </div>
          <button type="submit" disabled={!account.passwordLoginEnabled || busy === 'password'} style={{ marginTop: 16 }}>
            {busy === 'password' ? 'Đang đổi...' : 'Đổi mật khẩu'}
          </button>
        </form>

        <form className="card" onSubmit={deactivate} style={{ borderColor: 'rgba(220, 38, 38, 0.35)' }}>
          <h2 style={{ marginBottom: 16, color: 'var(--danger)' }}>Vô hiệu hoá tài khoản</h2>
          <div className="form-grid">
            <label>
              Mật khẩu hiện tại
              <input
                type="password"
                value={deactivatePassword}
                onChange={(event) => setDeactivatePassword(event.target.value)}
                disabled={!account.passwordLoginEnabled}
                required
              />
            </label>
          </div>
          <button type="submit" className="danger" disabled={!account.passwordLoginEnabled || busy === 'deactivate'} style={{ marginTop: 16 }}>
            {busy === 'deactivate' ? 'Đang xử lý...' : 'Vô hiệu hoá tài khoản'}
          </button>
        </form>
      </div>
      <AnimatePresence>
        {showDeactivateConfirm && (
          <ActionModal
            title="Vô hiệu hóa tài khoản?"
            description="Bạn sẽ bị đăng xuất ngay. Hồ sơ không còn truy cập được và bạn cần liên hệ hỗ trợ nếu muốn khôi phục tài khoản."
            confirmLabel="Vô hiệu hóa tài khoản"
            danger
            busy={busy === 'deactivate'}
            onClose={() => setShowDeactivateConfirm(false)}
            onConfirm={() => void confirmDeactivate()}
          />
        )}
      </AnimatePresence>
    </motion.div>
  );
}

// ─── CV PAGE ────────────────────────────────────────────────────────────────
function CvPage() {
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [cvPage, setCvPage] = useState(0);
  const [cvTotalPages, setCvTotalPages] = useState(0);
  const [message, setMessage] = useState('');
  const [planLimitReached, setPlanLimitReached] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [pendingDeleteCv, setPendingDeleteCv] = useState<CvFile | null>(null);
  const [actionBusy, setActionBusy] = useState(false);

  const load = useCallback(async () => {
    const cvResult = await candidateService.getCvs(cvPage, 10);
    setCvs(cvResult.items);
    setCvTotalPages(cvResult.totalPages);
  }, [cvPage]);

  useEffect(() => { void load(); }, [load]);

  async function upload(file?: File) {
    if (!file) return;
    setUploading(true);
    setPlanLimitReached(false);
    try {
      await candidateService.uploadCv(file);
      setMessage('✅ Upload CV thành công!');
      await load();
    } catch (err) {
      setMessage(readError(err));
      setPlanLimitReached(isPlanLimitError(err));
    } finally {
      setUploading(false);
    }
  }

  async function setDefaultCv(id: string) {
    setActionBusy(true);
    try {
      await candidateService.setDefaultCv(id);
      setMessage('Đã đặt CV mặc định.');
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function deleteUploadedCv(id: string) {
    setActionBusy(true);
    try {
      await candidateService.deleteCv(id);
      setMessage('Đã xóa CV.');
      setPendingDeleteCv(null);
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function openCv(id: string) {
    try {
      openBlobInNewTab(await candidateService.downloadCv(id));
    } catch (err) {
      setMessage(readError(err));
    }
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
          <div className="empty-state-icon"><IconDocument size={36} /></div>
          <p className="muted" style={{ marginBottom: 12 }}>Chọn file PDF để tải lên</p>
          <label style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            background: 'var(--primary)', color: '#fff', borderRadius: 'var(--radius-control)',
            padding: '8px 18px', cursor: 'pointer', fontWeight: 600, fontSize: '0.875rem',
            transition: 'background-color 150ms var(--ease-out), transform 100ms var(--ease-out)',
          }}>
            {uploading ? 'Đang tải...' : 'Chọn file'}
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
            planLimitReached ? (
              <div style={{ marginTop: 12 }}>
                <PlanLimitAlert message={message} />
              </div>
            ) : (
            <motion.div
              className={isSuccessMessage(message) ? 'success-panel' : 'error-panel'}
              variants={scaleIn} initial="initial" animate="animate" exit="exit"
              transition={{ duration: 0.18, ease: EASE_OUT }}
              style={{ marginTop: 12 }}
            >
              {message}
            </motion.div>
            )
          )}
        </AnimatePresence>
      </div>

      {/* CV list */}
      {cvs.length > 0 && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h2 style={{ marginBottom: 16 }}>CV đã tải lên</h2>
          <div className="data-table cv-data-table">
            <div className="data-table-header">
              <span>Tên file</span>
              <span>Kích thước</span>
              <span>Trạng thái</span>
              <span></span>
              <span></span>
              <span></span>
            </div>
            {cvs.map((cv) => (
              <div className="data-row" key={cv.id}>
                <strong style={{ fontSize: '0.925rem', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <IconDocument size={14} /> {cv.originalFileName}
                </strong>
                <span className="muted">{Math.round(cv.fileSize / 1024)} KB</span>
                <span>
                  {cv.defaultCv
                    ? <span className="chip match">Mặc định</span>
                    : <span className="chip neutral">PDF</span>
                  }
                </span>
                <button className="outline sm" onClick={() => openCv(cv.id)}>
                  Xem
                </button>
                <button className="outline sm"
                  disabled={actionBusy || cv.defaultCv}
                  onClick={() => setDefaultCv(cv.id)}>
                  Đặt mặc định
                </button>
                <button className="danger sm"
                  disabled={actionBusy}
                  onClick={() => setPendingDeleteCv(cv)}>
                  Xóa
                </button>
              </div>
            ))}
          </div>
          <PaginationControls
            page={cvPage}
            totalPages={cvTotalPages}
            label="Phân trang CV đã tải lên"
            onPageChange={setCvPage}
          />
        </div>
      )}
      <AnimatePresence>
        {pendingDeleteCv && (
          <ActionModal
            title="Xóa CV đã tải lên?"
            description={`CV "${pendingDeleteCv.originalFileName}" sẽ không còn được dùng cho lần ứng tuyển mới.`}
            confirmLabel="Xóa CV"
            danger
            busy={actionBusy}
            onClose={() => setPendingDeleteCv(null)}
            onConfirm={() => deleteUploadedCv(pendingDeleteCv.id)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  );
}

// ─── SAVED JOBS PAGE ─────────────────────────────────────────────────────────
function SavedJobsPage() {
  useRestoreScrollPosition();
  const [params, setParams] = useSearchParams();
  const page = jobPageFromParams(params);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError('');
    candidateService.getSavedJobs(page, 12)
      .then((result) => {
        setJobs(result.items);
        setTotalPages(result.totalPages);
        if (result.totalPages > 0 && page >= result.totalPages) {
          setParams({ page: String(result.totalPages - 1) }, { replace: true });
        }
      })
      .catch((err) => setError(readError(err)))
      .finally(() => setLoading(false));
  }, [page, reloadKey, setParams]);

  async function removeSavedJob(job: Job) {
    setError('');
    setJobs((current) => current.filter((item) => item.id !== job.id));
    try {
      await candidateService.unsaveJob(job.id);
    } catch (err) {
      setJobs((current) => [job, ...current]);
      setError(readError(err));
    }
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Việc làm đã lưu</h1>
        <p>Các vị trí bạn đang quan tâm</p>
      </div>
      {error && (
        <div className="error-panel" role="alert" style={{ marginBottom: 16 }}>
          {error}
          <button type="button" className="outline sm" onClick={() => setReloadKey((value) => value + 1)} style={{ marginLeft: 10 }}>
            Thử lại
          </button>
        </div>
      )}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : jobs.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div className="empty-state-icon"><IconBookmark size={36} /></div>
          <h3>Chưa có việc làm nào được lưu</h3>
          <p className="muted">Tìm kiếm và lưu các vị trí bạn yêu thích!</p>
          <Link to="/jobs" className="button-link" style={{ marginTop: 16, display: 'inline-flex' }}>
            Tìm việc làm
          </Link>
        </div>
      ) : (
        <>
          <div className="job-grid">
            {jobs.map((job, i) => (
              <motion.div key={job.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25, ease: EASE_OUT, delay: i * 0.05 }}>
                <JobCard job={job} />
                <div className="card" style={{ marginTop: -8, padding: '12px 16px', display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <span className={`chip ${job.applied ? 'match' : 'neutral'}`}>
                    {job.applied ? 'Đã ứng tuyển' : (job.status === 'CLOSED' || (job.deadline && new Date(job.deadline) < new Date())) ? 'Đã đóng / hết hạn' : 'Đang tuyển'}
                  </span>
                  <button type="button" className="danger sm" onClick={() => void removeSavedJob(job)}>Bỏ lưu</button>
                </div>
              </motion.div>
            ))}
          </div>
          <PaginationControls
            page={page}
            totalPages={totalPages}
            label="Phân trang việc làm đã lưu"
            onPageChange={(nextPage) => setParams(nextPage > 0 ? { page: String(nextPage) } : {})}
          />
        </>
      )}
    </motion.div>
  );
}

// ─── APPLICATIONS PAGE ───────────────────────────────────────────────────────
function ApplicationsPage() {
  useRestoreScrollPosition();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const page = jobPageFromParams(params);
  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [message, setMessage] = useState('');
  const [pageError, setPageError] = useState('');

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setPageError('');
    try {
      const result = await candidateService.getApplications(page, 10);
      setApplications(result.items);
      setTotalPages(result.totalPages);
      if (result.totalPages > 0 && page >= result.totalPages) {
        setParams({ page: String(result.totalPages - 1) }, { replace: true });
      }
    } catch (err) {
      setPageError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [page, setParams]);

  useEffect(() => { void loadApplications(); }, [loadApplications]);
  useCandidateRealtime((event) => {
    if (event.type === 'APPLICATION_UPDATED' || event.type === 'REALTIME_RECONNECTED') {
      void loadApplications();
    }
  });

  const statusOptions = [
    { value: 'ALL', label: 'Tất cả', count: applications.length },
    ...Array.from(new Set(applications.map((application) => application.status))).map((status) => ({
      value: status,
      label: statusLabels[status] || status,
      count: applications.filter((application) => application.status === status).length,
    })),
  ];
  const filteredApplications = statusFilter === 'ALL'
    ? applications
    : applications.filter((application) => application.status === statusFilter);

  async function openSubmittedCv(application: CandidateApplication) {
    if (!application.submittedResume?.downloadAvailable && !application.cv?.id) return;
    setMessage('');
    try {
      openBlobInNewTab(await candidateService.downloadSubmittedResume(application.id));
    } catch (err) {
      setMessage(readError(err));
    }
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Hồ sơ ứng tuyển</h1>
        <p>Theo dõi trạng thái từng công việc bạn đã nộp hồ sơ.</p>
      </div>
      {pageError && <div className="error-panel" role="alert" style={{ marginBottom: 16 }}>{pageError} <button className="outline sm" onClick={() => void loadApplications()}>Thử lại</button></div>}

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : applications.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div className="empty-state-icon"><IconClipboard size={36} /></div>
          <h3>Chưa có đơn ứng tuyển nào</h3>
          <p className="muted">Bắt đầu ứng tuyển vào các vị trí phù hợp!</p>
          <Link to="/jobs" className="button-link" style={{ marginTop: 16, display: 'inline-flex' }}>
            Tìm việc làm
          </Link>
        </div>
      ) : (
        <div className="application-list-shell">
          <div className="application-list-toolbar" aria-label="Lọc hồ sơ ứng tuyển">
            {statusOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                className={statusFilter === option.value ? 'active' : ''}
                onClick={() => setStatusFilter(option.value)}
              >
                <span>{option.label}</span>
                <strong>{option.count}</strong>
              </button>
            ))}
          </div>

          {message && <div className="error-panel">{message}</div>}

          {filteredApplications.length === 0 ? (
            <div className="card application-empty-state">
              <h3>Không có hồ sơ ở trạng thái này</h3>
              <p className="muted">Chọn trạng thái khác để xem các hồ sơ ứng tuyển còn lại.</p>
            </div>
          ) : (
            <div className="application-card-list">
              {filteredApplications.map((application, i) => {
                const company = application.job.company;
                const logoUrl = company.logoUrl;
                const cvLabel = application.submittedResume?.originalFileName
                  || application.submittedResume?.title
                  || application.cv?.originalFileName
                  || application.cvVersion?.title
                  || 'Không có CV';
                const latestTimeline = application.timeline?.[application.timeline.length - 1];
                return (
                  <motion.article
                    key={application.id}
                    className="application-history-card"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.035 }}
                  >
                    <div className="application-company-mark" aria-hidden="true">
                      {logoUrl ? (
                        <img src={logoUrl} alt="" />
                      ) : (
                        <IconBuilding size={24} style={{ opacity: 0.6 }} />
                      )}
                    </div>

                    <div className="application-history-main">
                      <div className="application-history-title-row">
                        <div>
                          <Link
                            className="application-job-title"
                            to={`/candidate/applications/${application.id}`}
                            state={{ from: `${location.pathname}${location.search}`, scrollY: window.scrollY } satisfies NavigationState}
                          >
                            {application.job.title}
                          </Link>
                          <p className="application-company-name">{company.name}</p>
                        </div>
                        <span className={`chip ${statusColors[application.status] || 'neutral'}`}>
                          {statusLabels[application.status] || application.status}
                        </span>
                      </div>
                      {['archived', 'removed', 'closed'].includes(application.job?.status?.toLowerCase() || '') && (
                        <div style={{ marginTop: '8px', color: '#b91c1c', fontSize: '0.85rem', fontWeight: 600 }}>
                          ⚠️ Tin không còn hoạt động
                        </div>
                      )}

                      <div className="application-history-meta">
                        <span className="meta-with-icon"><IconCalendar size={12} /> Ứng tuyển: {formatDateTime(application.submittedAt)}</span>
                        <span className="meta-with-icon"><IconSearch size={12} /> {application.preferredLocation || application.job.location || 'Chưa có địa điểm'}</span>
                        {(application.submittedResume?.downloadAvailable || application.cv?.id) ? (
                          <button type="button" className="application-cv-link meta-with-icon" onClick={() => void openSubmittedCv(application)}>
                            <IconDocument size={12} /> CV ứng tuyển
                          </button>
                        ) : (
                          <span className="meta-with-icon"><IconDocument size={12} /> {cvLabel}</span>
                        )}
                      </div>

                      <div className="application-history-note">
                        <span>{latestTimeline?.publicNote || `Trạng thái hiện tại: ${statusLabels[application.status] || application.status}`}</span>
                        <small>Cập nhật: {formatDate(application.updatedAt || application.submittedAt)}</small>
                      </div>
                    </div>

                    <div className="application-history-actions">
                      <Link
                        className="button-link outline sm"
                        to={`/candidate/applications/${application.id}`}
                        state={{ from: `${location.pathname}${location.search}`, scrollY: window.scrollY } satisfies NavigationState}
                      >
                        Xem chi tiết
                      </Link>
                    </div>
                  </motion.article>
                );
              })}
            </div>
          )}
          <PaginationControls
            page={page}
            totalPages={totalPages}
            label="Phân trang hồ sơ ứng tuyển"
            onPageChange={(nextPage) => setParams(nextPage > 0 ? { page: String(nextPage) } : {})}
          />
        </div>
      )}
    </motion.div>
  );
}

function SubmittedResumeSnapshotView({ resume }: { resume: SubmittedResume }) {
  const snapshot = resume.builderSnapshot || {};
  const primaryFields = [
    ['fullName', 'Họ và tên'],
    ['headline', 'Tiêu đề nghề nghiệp'],
    ['phone', 'Số điện thoại'],
    ['email', 'Email'],
    ['location', 'Địa điểm'],
    ['bio', 'Giới thiệu'],
  ] as const;
  const sections = [
    ['education', 'Học vấn'],
    ['workExperience', 'Kinh nghiệm làm việc'],
    ['projects', 'Dự án'],
    ['certifications', 'Chứng chỉ'],
  ] as const;

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <dl style={{ display: 'grid', gap: 8, margin: 0 }}>
        {primaryFields.map(([key, label]) => {
          const value = snapshot[key];
          if (typeof value !== 'string' || !value.trim()) return null;
          return (
            <div key={key}>
              <dt className="muted" style={{ fontSize: '0.8rem' }}>{label}</dt>
              <dd style={{ margin: '2px 0 0', whiteSpace: 'pre-wrap' }}>{value}</dd>
            </div>
          );
        })}
      </dl>
      {Array.isArray(snapshot.skills) && snapshot.skills.length > 0 && (
        <div>
          <strong>Kỹ năng</strong>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
            {snapshot.skills.map((skill, index) => (
              <span className="chip neutral" key={`${String(skill)}-${index}`}>{String(skill)}</span>
            ))}
          </div>
        </div>
      )}
      {sections.map(([key, label]) => {
        const items = snapshot[key];
        if (!Array.isArray(items) || items.length === 0) return null;
        return (
          <section key={key}>
            <strong>{label}</strong>
            <div style={{ display: 'grid', gap: 8, marginTop: 8 }}>
              {items.map((item, index) => (
                <div className="notice-panel" key={index}>
                  {item && typeof item === 'object'
                    ? Object.values(item as Record<string, unknown>).filter((value) => typeof value === 'string' && value.trim()).map(String).join(' · ')
                    : String(item)}
                </div>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

// ─── APPLICATION DETAIL PAGE ─────────────────────────────────────────────────
function ApplicationDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigationState = location.state as NavigationState | null;
  const backTo = internalOrigin(navigationState, '/candidate/applications');
  const [application, setApplication] = useState<CandidateApplication | null>(null);
  const [message, setMessage] = useState('');
  const [actionBusy, setActionBusy] = useState(false);
  const [rescheduleInterviewId, setRescheduleInterviewId] = useState('');
  const [declineInterviewId, setDeclineInterviewId] = useState('');
  const [rescheduleNote, setRescheduleNote] = useState('');
  const [rejectOfferId, setRejectOfferId] = useState('');
  const [offerNote, setOfferNote] = useState('');
  const [withdrawOfferId, setWithdrawOfferId] = useState('');

  const loadApplication = useCallback(async () => {
    if (!id) return;
    const app = await candidateService.getApplication(id);
    setApplication(app);

    // Auto-mark unviewed interviews as viewed
    if (app.interviews && Array.isArray(app.interviews)) {
      app.interviews.forEach(interview => {
        if (!interview.viewedAt) {
          candidateService.viewInterview(interview.id).catch(console.error);
        }
      });
    }
  }, [id]);

  useEffect(() => {
    void loadApplication().catch((err) => setMessage(readError(err)));
  }, [loadApplication]);
  useCandidateRealtime((event) => {
    if (event.type === 'REALTIME_RECONNECTED'
        || (event.type === 'APPLICATION_UPDATED' && event.entityId === id)) {
      void loadApplication().catch((err) => setMessage(readError(err)));
    }
  });

  async function openSubmittedResume(applicationId: string) {
    setMessage('');
    try {
      openBlobInNewTab(await candidateService.downloadSubmittedResume(applicationId));
    } catch (err) {
      setMessage(readError(err));
    }
  }

  async function respondToInterview(interviewId: string, responseStatus: string, note?: string) {
    setActionBusy(true);
    setMessage('');
    try {
      await candidateService.respondToInterview(interviewId, responseStatus, note);
      setMessage('Đã cập nhật phản hồi phỏng vấn.');
      setRescheduleInterviewId('');
      setDeclineInterviewId('');
      setRescheduleNote('');
      await loadApplication();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function respondToOffer(offerId: string, accepted: boolean, note?: string) {
    setActionBusy(true);
    setMessage('');
    try {
      await candidateService.respondToOffer(offerId, accepted, note);
      setMessage(accepted ? 'Đã chấp nhận job offer.' : 'Đã gửi phản hồi job offer.');
      setRejectOfferId('');
      setOfferNote('');
      await loadApplication();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setActionBusy(false);
    }
  }

  async function finalRespondToOffer(offerId: string, accepted: boolean) {
    setActionBusy(true);
    setMessage('');
    try {
      await candidateService.finalRespondToOffer(offerId, accepted);
      setMessage(accepted ? 'Đã chấp nhận offer cũ.' : 'Đã hủy bỏ job offer.');
      setWithdrawOfferId('');
      await loadApplication();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setActionBusy(false);
    }
  }

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
        <Link
          to={backTo}
          state={{ restoreScrollY: navigationState?.scrollY } satisfies NavigationState}
          style={{ color: 'var(--primary)', fontSize: '0.875rem', fontWeight: 600 }}
        >
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
            CV: {application.submittedResume?.originalFileName
              || application.submittedResume?.title
              || application.cv?.originalFileName
              || application.cvVersion?.title
              || 'Không có'}
          </span>
        </div>
        {['archived', 'removed', 'closed'].includes(application.job?.status?.toLowerCase() || '') && (
          <div style={{ marginTop: '16px', padding: '12px', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px', color: '#b91c1c', fontSize: '0.9rem', fontWeight: 600 }}>
            ⚠️ Tin tuyển dụng này đã không còn hoạt động. Bạn không thể thực hiện thêm thao tác.
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <h2 style={{ marginBottom: 12 }}>CV đã nộp</h2>
        {application.submittedResume?.sourceType === 'builder' ? (
          <div>
            <strong>{application.submittedResume.title || 'CV Builder'}</strong>
            <p className="muted" style={{ margin: '4px 0 16px' }}>
              Bản sao tại thời điểm ứng tuyển
              {application.submittedResume.sourceUpdatedAt ? ` · Cập nhật ${formatDate(application.submittedResume.sourceUpdatedAt)}` : ''}
            </p>
            <SubmittedResumeSnapshotView resume={application.submittedResume} />
          </div>
        ) : application.submittedResume?.sourceType === 'uploaded' ? (
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
            <div>
              <strong>{application.submittedResume.originalFileName || application.submittedResume.title || 'cv.pdf'}</strong>
              <p className="muted" style={{ margin: '4px 0 0' }}>File bản sao tại thời điểm ứng tuyển</p>
            </div>
            {application.submittedResume.downloadAvailable && (
              <button type="button" className="outline sm" onClick={() => openSubmittedResume(application.id)}>
                Mở CV
              </button>
            )}
          </div>
        ) : application.cvVersion ? (
          <div>
            <strong>{application.cvVersion.title}</strong>
            <p className="muted" style={{ margin: '4px 0 0' }}>CV Builder - cập nhật {formatDate(application.cvVersion.updatedAt)}</p>
          </div>
        ) : application.cv ? (
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
            <div>
              <strong>{application.cv.originalFileName}</strong>
              <p className="muted" style={{ margin: '4px 0 0' }}>{formatDate(application.cv.createdAt)}</p>
            </div>
            <button type="button" className="outline sm" onClick={() => openSubmittedResume(application.id)}>
              Mở CV
            </button>
          </div>
        ) : (
          <p className="muted" style={{ margin: 0 }}>Không có thông tin CV đã nộp.</p>
        )}
      </div>

      {(application.preferredLocation || application.coverLetter) && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h2 style={{ marginBottom: 12 }}>Thông tin đã nộp</h2>
          {application.preferredLocation && (
            <p style={{ margin: '4px 0' }}>
              <strong>Địa điểm làm việc mong muốn:</strong> {application.preferredLocation}
            </p>
          )}
          {application.coverLetter && (
            <div style={{ marginTop: 12 }}>
              <strong>Thư giới thiệu:</strong>
              <p style={{ margin: '6px 0 0', whiteSpace: 'pre-wrap', color: 'var(--on-muted)', lineHeight: 1.6 }}>
                {application.coverLetter}
              </p>
            </div>
          )}
        </div>
      )}

      <AnimatePresence>
        {message && (
          <motion.div
            className={isSuccessMessage(message) ? 'success-panel' : 'error-panel'}
            variants={scaleIn}
            initial="initial"
            animate="animate"
            exit="exit"
            transition={{ duration: 0.18, ease: EASE_OUT }}
            style={{ marginBottom: 20 }}
            role={isSuccessMessage(message) ? 'status' : 'alert'}
          >
            {message}
          </motion.div>
        )}
      </AnimatePresence>

      {application.interviews && application.interviews.length > 0 && (
        <div className="card" style={{ marginBottom: 20, borderLeft: '4px solid #b45309' }}>
          <h2 style={{ marginBottom: 12 }}>Lịch Phỏng Vấn</h2>
          {application.interviews.map(interview => {
            const isUnrespondedPast = interview.status === 'NO_RESPONSE' ||
              ((interview.status === 'SCHEDULED' || interview.status === 'PENDING_RESPONSE') &&
               ((interview.scheduledAt && new Date(interview.scheduledAt).getTime() <= Date.now()) ||
                (interview.responseDeadline && new Date(interview.responseDeadline).getTime() <= Date.now())));

            return (
              <div
                key={interview.id}
                style={{
                  marginBottom: 16,
                  padding: 14,
                  background: isUnrespondedPast ? '#f1f5f9' : '#f8fafc',
                  borderRadius: 8,
                  border: isUnrespondedPast ? '1px solid #cbd5e1' : '1px solid #e2e8f0',
                  opacity: isUnrespondedPast ? 0.75 : 1,
                }}
              >
                <p style={{ margin: '4px 0', color: isUnrespondedPast ? '#64748b' : undefined }}>
                  <strong>Thời gian:</strong> {formatDateTime(interview.scheduledAt)}
                </p>
                <p style={{ margin: '4px 0', color: isUnrespondedPast ? '#64748b' : undefined }}>
                  <strong>Địa điểm:</strong> {interview.location || 'Chưa cập nhật'}
                </p>
                {interview.meetingLink && (
                  <p style={{ margin: '4px 0', color: isUnrespondedPast ? '#64748b' : undefined }}>
                    <strong>Link họp:</strong> <a href={interview.meetingLink} target="_blank" rel="noreferrer" style={{ color: isUnrespondedPast ? '#64748b' : '#2563eb' }}>{interview.meetingLink}</a>
                  </p>
                )}
                {interview.note && <p style={{ margin: '4px 0', color: isUnrespondedPast ? '#64748b' : undefined }}><strong>Ghi chú:</strong> {interview.note}</p>}

                {interview.employerRescheduleResponse === 'reject_reschedule' && interview.status === 'PENDING_RESPONSE' && !isUnrespondedPast && (
                  <div style={{ padding: 12, background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 6, marginBottom: 12, marginTop: 12 }}>
                    <p style={{ margin: '0 0 4px 0', color: '#991b1b', fontWeight: 600 }}>⚠️ Nhà tuyển dụng từ chối yêu cầu đổi lịch</p>
                    <p style={{ margin: 0, color: '#7f1d1d', fontSize: '0.9rem' }}><strong>Lý do:</strong> {interview.employerRescheduleNote}</p>
                    <p style={{ margin: '8px 0 0 0', color: '#991b1b', fontSize: '0.85rem' }}>Vui lòng xác nhận bạn có thể tham gia theo lịch cũ hay không.</p>
                  </div>
                )}

                <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid #e2e8f0' }}>
                  <p style={{ margin: '4px 0', fontSize: '0.9rem' }}>
                    <strong>Phản hồi của bạn:</strong>{' '}
                    {isUnrespondedPast ? (
                      <span style={{ color: '#64748b', fontWeight: 600, fontStyle: 'italic' }}>🔴 Lịch phỏng vấn đã hết hạn phản hồi (Quá giờ)</span>
                    ) : ['SCHEDULED', 'PENDING_RESPONSE'].includes(interview.status) ? (
                      <span style={{ color: '#9a3412', fontWeight: 700 }}>Đang chờ bạn xác nhận lịch phỏng vấn</span>
                    ) : interview.status === 'ACCEPTED' ? (
                      <span style={{ color: '#047857' }}>Đã xác nhận tham gia</span>
                    ) : interview.status === 'RESCHEDULE_REQUESTED' ? (
                      <span style={{ color: '#b45309' }}>Đã yêu cầu đổi lịch</span>
                    ) : interview.status === 'DECLINED' ? (
                      <span style={{ color: '#b91c1c' }}>Từ chối tham gia</span>
                    ) : interview.status === 'COMPLETED' ? (
                      <span style={{ color: '#4338ca' }}>Đã phỏng vấn xong</span>
                    ) : interview.status === 'NO_SHOW' ? (
                      <span style={{ color: '#b91c1c' }}>Không tham gia</span>
                    ) : interview.status === 'NO_RESPONSE' ? (
                      <span style={{ color: '#64748b', fontWeight: 600, fontStyle: 'italic' }}>🔴 Đã hết hạn phản hồi</span>
                    ) : (
                      interview.status
                    )}
                  </p>

                  {isUnrespondedPast ? (
                    <div style={{ marginTop: 10, padding: '6px 12px', background: '#e2e8f0', color: '#64748b', borderRadius: 6, fontSize: '0.85rem', fontStyle: 'italic', display: 'inline-block' }}>
                      🔒 Đã quá hạn phản hồi phỏng vấn
                    </div>
                  ) : ['SCHEDULED', 'PENDING_RESPONSE'].includes(interview.status) && (
                    <div className="button-row" style={{ marginTop: 12 }}>
                      <button type="button" className="success sm" disabled={actionBusy || ['archived', 'removed', 'closed'].includes(application.job?.status?.toLowerCase() || '')} onClick={() => respondToInterview(interview.id, 'confirmed')}>Xác nhận</button>
                      {interview.employerRescheduleResponse !== 'reject_reschedule' && (
                        <button className="outline sm" disabled={actionBusy || ['archived', 'removed', 'closed'].includes(application.job?.status?.toLowerCase() || '')} onClick={() => setRescheduleInterviewId(interview.id)}>Xin đổi lịch</button>
                      )}
                      <button className="danger sm" disabled={actionBusy || ['archived', 'removed', 'closed'].includes(application.job?.status?.toLowerCase() || '')} onClick={() => setDeclineInterviewId(interview.id)}>Từ chối tham gia</button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
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
              <p style={{ margin: '4px 0', fontSize: '0.9rem', color: '#475569' }}>
                Nếu bạn có bất kỳ thắc mắc hoặc cần trao đổi thêm về Job Offer này, vui lòng liên hệ trực tiếp với nhà tuyển dụng qua thông tin liên hệ của công ty.
              </p>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <h2 style={{ marginBottom: 0 }}>Lịch sử trạng thái</h2>
        <div className="timeline">
          {application.timeline.map((item, i) => {
            let displayNote = item.publicNote;
            if (displayNote) {
              const rescheduleMatch = displayNote.match(/(?:Nhà tuyển dụng phản hồi đổi lịch:\s*)?Chấp nhận đổi lịch phỏng vấn mới\s*\(Lịch cũ:\s*([^\-]+?)\s*->\s*Lịch mới:\s*([^)]+)\)/i);
              if (rescheduleMatch) {
                displayNote = `Đã đổi lịch phỏng vấn từ ${rescheduleMatch[1].trim()} thành ${rescheduleMatch[2].trim()}`;
              } else if (displayNote.toLowerCase().includes('yêu cầu đổi lịch phỏng vấn')) {
                if (!displayNote.includes('Lý do:') && application.interviews && application.interviews.length > 0) {
                  const latestIv = application.interviews[application.interviews.length - 1];
                  if (latestIv && latestIv.candidateRescheduleNote) {
                    displayNote = `Ứng viên đã yêu cầu đổi lịch phỏng vấn (Lý do: ${latestIv.candidateRescheduleNote})`;
                  } else {
                    displayNote = 'Ứng viên đã yêu cầu đổi lịch phỏng vấn';
                  }
                }
              } else if (displayNote.toLowerCase().includes('từ chối đổi lịch phỏng vấn')) {
                if (!displayNote.includes('Lý do:') && application.interviews && application.interviews.length > 0) {
                  const latestIv = application.interviews[application.interviews.length - 1];
                  if (latestIv && latestIv.employerRescheduleNote) {
                    displayNote = `Nhà tuyển dụng phản hồi đổi lịch: Từ chối đổi lịch phỏng vấn (Lý do: ${latestIv.employerRescheduleNote})`;
                  }
                }
              } else if (
                displayNote === 'Đã lên lịch phỏng vấn' &&
                application.interviews && application.interviews.length > 0
              ) {
                const latestIv = application.interviews[application.interviews.length - 1];
                if (latestIv && latestIv.scheduledAt) {
                  displayNote += ` (Thời gian: ${formatDateTime(latestIv.scheduledAt)})`;
                }
              }
            }

            return (
              <motion.div key={item.id} className="timeline-item"
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.06 }}>

                {i > 0 && application.timeline[i - 1].toStatus === item.toStatus ? (
                  <span style={{ display: 'inline-block', marginBottom: 8, fontSize: '1.2rem', color: 'var(--text-muted)', opacity: 0.6 }}>↳</span>
                ) : (
                  <strong className={`chip ${statusColors[item.toStatus] || ''}`} style={{ display: 'inline-flex', marginBottom: 8 }}>
                    {statusLabels[item.toStatus] || item.toStatus}
                  </strong>
                )}
                <span className="timeline-item-date">
                  {formatDateTime(item.createdAt)}
                </span>
                {displayNote && (
                  <p style={{ margin: '6px 0 0', color: 'var(--on-muted)', fontSize: '0.875rem' }}>
                    {displayNote}
                  </p>
                )}
              </motion.div>
            );
          })}
        </div>
      </div>
      <AnimatePresence>
        {rescheduleInterviewId && (
          <ActionModal
            title="Xin đổi lịch phỏng vấn"
            description="Nhập lý do và thời gian đề xuất để nhà tuyển dụng xem xét."
            confirmLabel="Gửi yêu cầu"
            busy={actionBusy}
            confirmDisabled={!rescheduleNote.trim()}
            onClose={() => { setRescheduleInterviewId(''); setRescheduleNote(''); }}
            onConfirm={() => respondToInterview(rescheduleInterviewId, 'request_reschedule', rescheduleNote.trim())}
          >
            <label className="modal-field">
              Lý do và thời gian đề xuất
              <textarea
                value={rescheduleNote}
                onChange={(event) => setRescheduleNote(event.target.value)}
                placeholder="Ví dụ: Tôi bận lịch học lúc 9h, mong được đổi sang 14h cùng ngày."
                autoFocus
              />
            </label>
          </ActionModal>
        )}
        {declineInterviewId && (
          <ActionModal
            title="Từ chối tham gia phỏng vấn?"
            description="Nhà tuyển dụng sẽ nhận được thông báo rằng bạn không tham gia lịch phỏng vấn này."
            confirmLabel="Xác nhận từ chối"
            danger
            busy={actionBusy}
            onClose={() => setDeclineInterviewId('')}
            onConfirm={() => respondToInterview(declineInterviewId, 'declined')}
          />
        )}
        {rejectOfferId && (
          <ActionModal
            title="Phản hồi job offer"
            description="Bạn có thể nêu lý do từ chối hoặc đề xuất điều chỉnh offer."
            confirmLabel="Gửi phản hồi"
            danger
            busy={actionBusy}
            onClose={() => { setRejectOfferId(''); setOfferNote(''); }}
            onConfirm={() => respondToOffer(rejectOfferId, false, offerNote.trim())}
          >
            <label className="modal-field">
              Ghi chú gửi nhà tuyển dụng
              <textarea
                value={offerNote}
                onChange={(event) => setOfferNote(event.target.value)}
                placeholder="Ví dụ: Tôi mong muốn điều chỉnh mức lương hoặc ngày bắt đầu..."
                autoFocus
              />
            </label>
          </ActionModal>
        )}
        {withdrawOfferId && (
          <ActionModal
            title="Hủy bỏ job offer?"
            description="Thao tác này sẽ gửi phản hồi cuối cùng rằng bạn không tiếp tục với offer này."
            confirmLabel="Hủy bỏ offer"
            danger
            busy={actionBusy}
            onClose={() => setWithdrawOfferId('')}
            onConfirm={() => finalRespondToOffer(withdrawOfferId, false)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  );
}

function JobAlertsPage() {
  const [params, setParams] = useSearchParams();
  const page = jobPageFromParams(params);
  const sourceFilters = jobFiltersFromParams(params);
  const [items, setItems] = useState<JobAlert[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState('');
  const [deleteId, setDeleteId] = useState('');
  const [draft, setDraft] = useState<JobAlertInput>(() => ({
    name: sourceFilters.search ? `Việc làm: ${sourceFilters.search}` : 'Cảnh báo việc làm của tôi',
    keyword: sourceFilters.search,
    location: sourceFilters.location,
    category: sourceFilters.category,
    jobType: sourceFilters.jobType,
    workMode: sourceFilters.workMode,
    minSalary: sourceFilters.minSalary,
    maxSalary: sourceFilters.maxSalary,
    frequency: 'DAILY',
    enabled: true,
  }));

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await candidateService.getJobAlerts(page, 10);
      setItems(result.items);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { void load(); }, [load]);

  function inputFromAlert(alert: JobAlert): JobAlertInput {
    return {
      name: alert.name, keyword: alert.keyword, location: alert.location, category: alert.category,
      jobType: alert.jobType, workMode: alert.workMode, minSalary: alert.minSalary, maxSalary: alert.maxSalary,
      frequency: alert.frequency, enabled: alert.enabled,
    };
  }

  async function saveAlert(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      if (editingId) await candidateService.updateJobAlert(editingId, draft);
      else await candidateService.createJobAlert(draft);
      setEditingId('');
      setDraft({ name: 'Cảnh báo việc làm của tôi', frequency: 'DAILY', enabled: true });
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  async function toggleAlert(alert: JobAlert) {
    const previous = items;
    setItems((current) => current.map((item) => item.id === alert.id ? { ...item, enabled: !item.enabled } : item));
    try {
      await candidateService.updateJobAlert(alert.id, { ...inputFromAlert(alert), enabled: !alert.enabled });
    } catch (err) {
      setItems(previous);
      setError(readError(err));
    }
  }

  async function removeAlert() {
    if (!deleteId) return;
    try {
      await candidateService.deleteJobAlert(deleteId);
      setDeleteId('');
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate" transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Cảnh báo việc làm</h1>
        <p>Hệ thống kiểm tra theo lịch và gửi thông báo trong ứng dụng khi có việc mới phù hợp.</p>
      </div>
      <form className="card form-grid two" onSubmit={saveAlert} style={{ marginBottom: 20 }}>
        <h2 className="wide">{editingId ? 'Sửa cảnh báo' : 'Tạo cảnh báo mới'}</h2>
        <label>Tên cảnh báo<input required maxLength={120} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></label>
        <label>Từ khóa<input value={draft.keyword || ''} onChange={(e) => setDraft({ ...draft, keyword: e.target.value })} /></label>
        <ProvinceLocationSelect label="Địa điểm" value={draft.location || ''} onChange={(value) => setDraft({ ...draft, location: value })} allowRemote placeholder="Tất cả địa điểm" />
        <label>Ngành / danh mục<input value={draft.category || ''} onChange={(e) => setDraft({ ...draft, category: e.target.value })} /></label>
        <label>Loại công việc<select value={draft.jobType || ''} onChange={(e) => setDraft({ ...draft, jobType: e.target.value })}>
          <option value="">Tất cả</option><option value="full_time">Toàn thời gian</option><option value="part_time">Bán thời gian</option>
          <option value="contract">Hợp đồng</option><option value="internship">Thực tập</option><option value="freelance">Tự do</option>
        </select></label>
        <label>Hình thức<select value={draft.workMode || ''} onChange={(e) => setDraft({ ...draft, workMode: e.target.value })}>
          <option value="">Tất cả</option><option value="onsite">Văn phòng</option><option value="remote">Từ xa</option><option value="hybrid">Kết hợp</option>
        </select></label>
        <label>Lương tối thiểu<input type="number" min={0} value={draft.minSalary || ''} onChange={(e) => setDraft({ ...draft, minSalary: e.target.value ? Number(e.target.value) : undefined })} /></label>
        <label>Lương tối đa<input type="number" min={0} value={draft.maxSalary || ''} onChange={(e) => setDraft({ ...draft, maxSalary: e.target.value ? Number(e.target.value) : undefined })} /></label>
        <label>Tần suất<select value={draft.frequency} onChange={(e) => setDraft({ ...draft, frequency: e.target.value as 'DAILY' | 'WEEKLY' })}>
          <option value="DAILY">Hàng ngày</option><option value="WEEKLY">Hàng tuần</option>
        </select></label>
        <div className="wide" style={{ display: 'flex', gap: 8 }}>
          <button type="submit">{editingId ? 'Lưu thay đổi' : 'Tạo cảnh báo'}</button>
          {editingId && <button type="button" className="outline" onClick={() => setEditingId('')}>Hủy</button>}
        </div>
      </form>
      {error && <div className="error-panel" role="alert" style={{ marginBottom: 16 }}>{error} <button className="outline sm" onClick={() => void load()}>Thử lại</button></div>}
      {loading ? <div className="card">Đang tải cảnh báo...</div> : items.length === 0 ? (
        <div className="card empty-state">Bạn chưa có cảnh báo việc làm nào.</div>
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          {items.map((alert) => (
            <div className="card" key={alert.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center' }}>
              <div><strong>{alert.name}</strong><p className="muted" style={{ margin: '6px 0 0' }}>
                {[alert.keyword, alert.location, alert.jobType, alert.workMode].filter(Boolean).join(' · ') || 'Tất cả việc làm'} · {alert.frequency === 'DAILY' ? 'Hàng ngày' : 'Hàng tuần'}
              </p></div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button type="button" className={alert.enabled ? '' : 'outline'} onClick={() => void toggleAlert(alert)}>{alert.enabled ? 'Đang bật' : 'Đã tắt'}</button>
                <button type="button" className="outline" onClick={() => { setEditingId(alert.id); setDraft(inputFromAlert(alert)); }}>Sửa</button>
                <button type="button" className="danger" onClick={() => setDeleteId(alert.id)}>Xóa</button>
              </div>
            </div>
          ))}
        </div>
      )}
      <PaginationControls page={page} totalPages={totalPages} label="Phân trang cảnh báo việc làm"
        onPageChange={(nextPage) => setParams(nextPage > 0 ? { page: String(nextPage) } : {})} />
      <AnimatePresence>{deleteId && <ActionModal title="Xóa cảnh báo việc làm?" description="Cảnh báo sẽ ngừng tạo thông báo mới."
        confirmLabel="Xóa cảnh báo" danger onClose={() => setDeleteId('')} onConfirm={removeAlert} />}</AnimatePresence>
    </motion.div>
  );
}

// ─── NOTIFICATIONS PAGE ──────────────────────────────────────────────────────
function NotificationsPage() {
  const [params, setParams] = useSearchParams();
  const page = jobPageFromParams(params);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await candidateService.getNotifications(page, 20);
      setItems(data.items);
      setTotalPages(data.totalPages);
      if (data.totalPages > 0 && page >= data.totalPages) {
        setParams({ page: String(data.totalPages - 1) }, { replace: true });
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setLoading(false);
    }
  }, [page, setParams]);

  useEffect(() => { void load(); }, [load]);
  useCandidateRealtime((event) => {
    if (event.type === 'NOTIFICATION_UPDATED' || event.type === 'REALTIME_RECONNECTED') {
      void load();
    }
  });

  const unreadCount = items.filter((item) => !item.read).length;

  async function markAllRead() {
    try {
      await candidateService.markAllNotificationsRead();
      await load();
    } catch (err) {
      setError(readError(err));
    }
  }

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
        <p>
          Cập nhật từ nhà tuyển dụng và hệ thống
          {unreadCount > 0 ? ` · ${unreadCount} chưa đọc` : ''}
        </p>
      </div>

      {error && <div className="error-panel" role="alert" style={{ marginBottom: 16 }}>{error} <button className="outline sm" onClick={() => void load()}>Thử lại</button></div>}

      <NotificationInbox
        items={items}
        loading={loading}
        emptyTitle="Không có thông báo mới"
        emptyHint="Bạn sẽ nhận thông báo khi có cập nhật từ nhà tuyển dụng."
        getLink={getNotificationLink}
        onMarkRead={(id) => {
          candidateService.markNotificationRead(id).then(load).catch((err) => setError(readError(err)));
        }}
        onMarkAllRead={() => { void markAllRead(); }}
      />
      {!loading && items.length > 0 && (
        <PaginationControls
          page={page}
          totalPages={totalPages}
          label="Phân trang thông báo"
          onPageChange={(nextPage) => setParams(nextPage > 0 ? { page: String(nextPage) } : {})}
        />
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
            <strong>{formatDateTime(subscription.startedAt)}</strong>
          </div>
          <div>
            <div className="muted" style={{ fontSize: '0.85rem' }}>Ngày hết hạn</div>
            <strong>{formatDateTime(subscription.expiresAt)}</strong>
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

        {(subscription.usages?.length || 0) > 0 && (
          <div style={{ marginTop: 18 }}>
            <div className="muted" style={{ marginBottom: 8, fontSize: '0.85rem' }}>Hạn mức theo gói</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
              {subscription.usages!.map((item) => {
                const remaining = Math.max(0, item.limit - item.used);
                const over = item.used >= item.limit;
                return (
                  <div
                    key={item.featureKey}
                    style={{
                      border: `1px solid ${over ? '#fecaca' : '#e5e7eb'}`,
                      background: over ? '#fef2f2' : '#f9fafb',
                      borderRadius: 10,
                      padding: '12px 14px',
                    }}
                  >
                    <div className="muted" style={{ fontSize: '0.8rem' }}>
                      {item.label}{item.daily ? ' / ngày' : ''}
                    </div>
                    <strong style={{ fontSize: '1.15rem' }}>{item.used}/{item.limit}</strong>
                    <div style={{ fontSize: '0.8rem', color: over ? '#b91c1c' : '#166534', marginTop: 4 }}>
                      {over ? 'Đã hết hạn mức' : `Còn ${remaining}`}
                    </div>
                  </div>
                );
              })}
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
          { label: 'CV đã tải', value: subscription.cvCount, icon: <IconDocument size={22} /> },
          { label: 'Việc đã lưu', value: subscription.savedJobsCount, icon: <IconBookmark size={22} /> },
          { label: 'Thông báo chưa đọc', value: subscription.unreadNotificationsCount, icon: <IconBell size={22} /> },
        ].map(({ label, value, icon }, i) => (
          <motion.div key={label} className="metric-card"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.22, ease: EASE_OUT, delay: i * 0.06 }}>
            <div className="metric-card-icon">{icon}</div>
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
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    employerService.getNotifications(1, 20).then(data => {
      const list = Array.isArray(data) ? data : (data?.items || []);
      setUnreadCount(list.filter(n => !n.read).length);
    }).catch(() => {});
  }, []);

  function logout() { void endAuthenticatedSession(() => navigate('/login')); }

  return (
    <div className="employer-shell">
      <DialogContainer />
      <aside className="employer-nav portal-nav">
        <Link className="brand portal-nav-brand" to="/">
          <span className="portal-nav-brand-mark"><IconHome /></span>
          <span className="portal-nav-brand-text">
            <strong>SRP</strong>
            <span>Cổng nhà tuyển dụng</span>
          </span>
        </Link>

        <nav className="portal-nav-section" aria-label="Menu nhà tuyển dụng">
          <NavLink to="/employer" end className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconDashboard /></span>
            <span className="sidebar-link-label">Dashboard</span>
          </NavLink>

          <NavLink to="/employer/jobs" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconBriefcase /></span>
            <span className="sidebar-link-label">Quản lý Việc làm</span>
          </NavLink>

          <NavLink to="/employer/applications" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconUsers /></span>
            <span className="sidebar-link-label">Quản lý Ứng viên</span>
          </NavLink>

          <NavLink to="/employer/interviews" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconClipboard /></span>
            <span className="sidebar-link-label">Lịch phỏng vấn</span>
          </NavLink>

          <NavLink
            to="/employer/notifications"
            className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            onClick={() => {
              employerService.markAllNotificationsRead().then(() => setUnreadCount(0));
            }}
          >
            <span className="sidebar-link-icon"><IconBell /></span>
            <span className="sidebar-link-label">Thông báo</span>
            {unreadCount > 0 && (
              <span className="portal-nav-badge">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </NavLink>

          <NavLink to="/employer/subscription" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconGem /></span>
            <span className="sidebar-link-label">Gói dịch vụ</span>
          </NavLink>

          <div className="nav-dropdown">
            <button
              type="button"
              className={`nav-dropdown-trigger${companyOpen ? ' open' : ''}`}
              onClick={() => setCompanyOpen(!companyOpen)}
              aria-expanded={companyOpen}
            >
              <span className="sidebar-link-icon"><IconBuilding /></span>
              <span className="sidebar-link-label">Công ty</span>
              <span className={`arrow ${companyOpen ? 'open' : ''}`}><IconChevron /></span>
            </button>

            <div className={`nav-dropdown-items${companyOpen ? ' is-open' : ''}`}>
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
            </div>
          </div>
        </nav>

        <div className="portal-nav-footer">
          <NavLink to="/employer/settings" className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
            <span className="sidebar-link-icon"><IconSettings /></span>
            <span className="sidebar-link-label">Cài đặt tài khoản</span>
          </NavLink>
          <NavLink to="/jobs" className="sidebar-link">
            <span className="sidebar-link-icon"><IconSearch /></span>
            <span className="sidebar-link-label">Xem tin tuyển dụng</span>
          </NavLink>
          <button type="button" className="sidebar-link portal-nav-logout" onClick={logout}>
            <span className="sidebar-link-icon"><IconLogout /></span>
            <span className="sidebar-link-label">Đăng xuất</span>
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

// ─── AI INTERVIEW PAGE ───────────────────────────────────────────────────────
function AiInterviewPage() {
  const [config, setConfig] = useState<AiInterviewConfig | null>(null);
  const [applications, setApplications] = useState<AiInterviewEligibleApplication[]>([]);
  const [sessions, setSessions] = useState<AiInterviewSession[]>([]);
  const [practiceCvs, setPracticeCvs] = useState<CvFile[]>([]);
  const [practiceCvVersions, setPracticeCvVersions] = useState<CvVersion[]>([]);
  const [activeTab, setActiveTab] = useState<'application' | 'practice'>('application');
  const [selectedCvId, setSelectedCvId] = useState('');
  const [cvProfile, setCvProfile] = useState<AiInterviewCvProfile | null>(null);
  const [targetRole, setTargetRole] = useState('');
  const [seniority, setSeniority] = useState<AiInterviewCvProfile['experienceLevel']>('fresher');
  const [focusSkills, setFocusSkills] = useState<string[]>([]);
  const [analyzingCv, setAnalyzingCv] = useState(false);
  const [selectedSession, setSelectedSession] = useState<AiInterviewSession | null>(null);
  const [pendingDeleteSession, setPendingDeleteSession] = useState<AiInterviewSession | null>(null);
  const [message, setMessage] = useState('');
  const [planLimitReached, setPlanLimitReached] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sessionPage, setSessionPage] = useState(0);
  const SESSION_PAGE_SIZE = 5;

  const practiceCvOptions = useMemo(() => [
    ...practiceCvs.map((cv) => ({
      id: cv.id,
      label: cv.originalFileName,
      source: 'CV tải lên',
      defaultCv: cv.defaultCv,
    })),
    ...practiceCvVersions.map((cv) => ({
      id: cv.id,
      label: cv.title,
      source: 'CV Builder',
      defaultCv: false,
    })),
  ], [practiceCvs, practiceCvVersions]);

  async function load() {
    const configData = await aiInterviewService.configStatus();
    setConfig(configData);
    if (!configData.enabled) {
      setApplications([]);
      setSessions([]);
      setPracticeCvs([]);
      setPracticeCvVersions([]);
      return;
    }
    const [sessionData, applicationData, cvData, cvVersionData] = await Promise.all([
      aiInterviewService.sessions(),
      aiInterviewService.eligibleApplications(),
      candidateService.getCvs(0, 100),
      candidateService.getCvVersions(0, 100),
    ]);
    setSessions(sessionData);
    setSessionPage(0);
    setApplications(applicationData);
    setPracticeCvs(cvData.items);
    setPracticeCvVersions(cvVersionData.items);
    const availableIds = new Set([...cvData.items, ...cvVersionData.items].map((cv) => cv.id));
    const preferredCvId = cvData.items.find((cv) => cv.defaultCv)?.id
      || cvData.items[0]?.id
      || cvVersionData.items[0]?.id
      || '';
    setSelectedCvId((current) => availableIds.has(current) ? current : preferredCvId);
  }

  useEffect(() => {
    load().catch((err) => setMessage(readError(err)));
  }, []);

  async function createFromApplication(applicationId: string) {
    setLoading(true);
    setMessage('');
    setPlanLimitReached(false);
    try {
      const session = await aiInterviewService.createApplicationSession(applicationId);
      setSelectedSession(session);
      await load();
    } catch (err) {
      setMessage(readError(err));
      setPlanLimitReached(isPlanLimitError(err));
    } finally {
      setLoading(false);
    }
  }

  async function createPractice(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setMessage('');
    setPlanLimitReached(false);
    try {
      if (!selectedCvId || !cvProfile || cvProfile.cvId !== selectedCvId) {
        setMessage('Vui lòng chọn và phân tích một CV trước khi bắt đầu.');
        return;
      }
      const session = await aiInterviewService.createPracticeSession({
        cvId: selectedCvId,
        targetRole,
        seniority,
        focusSkills,
      });
      setSelectedSession(session);
      await load();
    } catch (err) {
      setMessage(readError(err));
      setPlanLimitReached(isPlanLimitError(err));
    } finally {
      setLoading(false);
    }
  }

  async function analyzeSelectedCv() {
    if (!selectedCvId) {
      setMessage('Bạn chưa có CV để luyện phỏng vấn.');
      return;
    }
    setAnalyzingCv(true);
    setMessage('');
    try {
      const profile = await aiInterviewService.analyzePracticeCv(selectedCvId);
      setCvProfile(profile);
      setTargetRole(profile.suggestedRoles[0]?.title || '');
      setSeniority(profile.experienceLevel);
      setFocusSkills([]);
    } catch (err) {
      setCvProfile(null);
      setMessage(readError(err));
    } finally {
      setAnalyzingCv(false);
    }
  }

  function toggleFocusSkill(skill: string) {
    setFocusSkills((current) => {
      if (current.includes(skill)) return current.filter((item) => item !== skill);
      if (current.length >= 3) return current;
      return [...current, skill];
    });
  }

  async function openSession(id: string) {
    setSelectedSession(await aiInterviewService.getSession(id));
  }

  async function deleteSession(id: string) {
    setLoading(true);
    try {
      await aiInterviewService.deleteSession(id);
      if (selectedSession?.id === id) setSelectedSession(null);
      setPendingDeleteSession(null);
      await load();
    } catch (err) {
      setMessage(readError(err));
    } finally {
      setLoading(false);
    }
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
          <p className="eyebrow">Luyện tập</p>
          <h1>AI Interview</h1>
          <p className="muted">Nhận xét AI chỉ dùng để luyện tập, không phải quyết định tuyển dụng.</p>
        </div>
        <span className="chip">{config.questionCount} câu / phiên</span>
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
                <span className="meta-with-icon"><IconClipboard size={14} /> Theo hồ sơ ứng tuyển</span>
              </button>
              <button
                type="button"
                className={activeTab === 'practice' ? 'active' : ''}
                onClick={() => setActiveTab('practice')}
              >
                🎯 Luyện theo CV
              </button>
            </div>

            <AnimatePresence mode="wait">
              {activeTab === 'application' ? (
                <motion.div key="application" className="table-list ai-table"
                  variants={fadeUp} initial="initial" animate="animate" exit="exit"
                  transition={{ duration: 0.18, ease: EASE_OUT }}>
                  {applications.length === 0 && (
                    <div className="empty-state">Chưa có hồ sơ ứng tuyển hợp lệ để luyện phỏng vấn.</div>
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
                  <label>
                    Chọn CV để luyện tập
                    <select
                      required
                      value={selectedCvId}
                      onChange={(event) => {
                        setSelectedCvId(event.target.value);
                        setCvProfile(null);
                        setTargetRole('');
                        setFocusSkills([]);
                      }}
                    >
                      {practiceCvOptions.length === 0 ? <option value="">Bạn chưa có CV</option> : null}
                      {practiceCvOptions.map((cv) => (
                        <option value={cv.id} key={cv.id}>
                          {cv.label} — {cv.source}{cv.defaultCv ? ' (mặc định)' : ''}
                        </option>
                      ))}
                    </select>
                  </label>

                  <button type="button" className="outline" disabled={!selectedCvId || analyzingCv}
                    onClick={analyzeSelectedCv}>
                    {analyzingCv ? 'Đang phân tích CV...' : cvProfile ? 'Phân tích lại CV' : 'Phân tích CV và gợi ý vị trí'}
                  </button>

                  {cvProfile ? (
                    <>
                      <div className="notice-panel" role="status">
                        <strong>Hồ sơ phỏng vấn từ CV</strong>
                        <p style={{ margin: '4px 0 0' }}>{cvProfile.summary}</p>
                      </div>

                      <label>
                        Vị trí mục tiêu
                        <input required list="ai-interview-role-suggestions" value={targetRole}
                          onChange={(event) => setTargetRole(event.target.value)}
                          placeholder="Chọn gợi ý hoặc nhập vị trí khác" />
                        <datalist id="ai-interview-role-suggestions">
                          {cvProfile.suggestedRoles.map((role) => (
                            <option value={role.title} key={role.title}>{role.reason}</option>
                          ))}
                        </datalist>
                      </label>

                      <label>
                        Cấp độ phỏng vấn
                        <select value={seniority}
                          onChange={(event) => setSeniority(event.target.value as AiInterviewCvProfile['experienceLevel'])}>
                          <option value="intern">Intern</option>
                          <option value="fresher">Fresher</option>
                          <option value="junior">Junior</option>
                          <option value="middle">Middle</option>
                          <option value="senior">Senior</option>
                        </select>
                      </label>

                      <fieldset className="practice-skill-fieldset">
                        <legend>Kỹ năng muốn luyện sâu — chọn tối đa 3</legend>
                        <div className="practice-skill-grid">
                          {cvProfile.skills.map((skill) => {
                            const selected = focusSkills.includes(skill);
                            return (
                              <label key={skill} className={`practice-skill-option${selected ? ' selected' : ''}`}>
                                <input type="checkbox" checked={selected}
                                  disabled={!selected && focusSkills.length >= 3}
                                  onChange={() => toggleFocusSkill(skill)} />
                                <span>{skill}</span>
                              </label>
                            );
                          })}
                        </div>
                      </fieldset>

                      <button type="submit" disabled={loading || analyzingCv || !targetRole.trim()}>
                        {loading ? 'Đang tạo...' : '🚀 Bắt đầu luyện theo CV'}
                      </button>
                    </>
                  ) : null}
                </motion.form>
              )}
            </AnimatePresence>

            <AnimatePresence>
              {message && (
                planLimitReached ? (
                  <div style={{ marginTop: 12 }}>
                    <PlanLimitAlert message={message} />
                  </div>
                ) : (
                <motion.div className="error-panel" style={{ marginTop: 12 }}
                  variants={scaleIn} initial="initial" animate="animate" exit="exit"
                  transition={{ duration: 0.18, ease: EASE_OUT }} role="alert">
                  {message}
                </motion.div>
                )
              )}
            </AnimatePresence>
          </div>

          <div className="card">
            <h2>Lịch sử gần đây</h2>
            <div className="session-list">
              {sessions.length === 0 && (
                <div className="empty-state">Chưa có phiên phỏng vấn nào.</div>
              )}
              {sessions
                .slice(sessionPage * SESSION_PAGE_SIZE, (sessionPage + 1) * SESSION_PAGE_SIZE)
                .map((session) => (
                <article className="session-row" key={session.id}>
                  <div>
                    <strong>{session.title}</strong>
                    <p>
                      {session.contextType === 'application' ? 'Theo hồ sơ ứng tuyển' : 'Luyện tập tự do'}
                      {' - '}{session.status}
                    </p>
                  </div>
                  <div className="button-row">
                    <button type="button" className="outline" onClick={() => openSession(session.id)}>
                      {session.status === 'completed' ? 'Xem lại' : 'Tiếp tục'}
                    </button>
                    <button type="button" className="danger" onClick={() => setPendingDeleteSession(session)}>Ẩn</button>
                  </div>
                </article>
              ))}
            </div>
            {sessions.length > SESSION_PAGE_SIZE && (
              <div className="pagination-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12, gap: 8 }}>
                <button
                  type="button"
                  className="outline"
                  disabled={sessionPage === 0}
                  onClick={() => setSessionPage((p) => p - 1)}
                >
                  ← Trước
                </button>
                <span className="muted" style={{ fontSize: '0.875rem' }}>
                  Trang {sessionPage + 1} / {Math.ceil(sessions.length / SESSION_PAGE_SIZE)}
                </span>
                <button
                  type="button"
                  className="outline"
                  disabled={(sessionPage + 1) * SESSION_PAGE_SIZE >= sessions.length}
                  onClick={() => setSessionPage((p) => p + 1)}
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        </div>
      )}
      <AnimatePresence>
        {pendingDeleteSession && (
          <ActionModal
            title="Ẩn phiên phỏng vấn?"
            description={`Phiên "${pendingDeleteSession.title}" sẽ không còn hiển thị trong lịch sử gần đây.`}
            confirmLabel="Ẩn phiên"
            danger
            busy={loading}
            onClose={() => setPendingDeleteSession(null)}
            onConfirm={() => deleteSession(pendingDeleteSession.id)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  );
}

// ─── AI INTERVIEW ROOM ───────────────────────────────────────────────────────
function AiInterviewRoom(props: {
  config: AiInterviewConfig;
  session: AiInterviewSession;
  onSessionChange: (session: AiInterviewSession) => void;
  onBack: () => void;
}) {
  return props.session.conversation
    ? <AiConversationRoom {...props} />
    : <LegacyAiInterviewRoom {...props} />;
}

function AiConversationRoom({
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
  const conversation = session.conversation!;
  const currentTurn = conversation.timeline.find((turn) => turn.id === conversation.currentTurnId);
  const [transcript, setTranscript] = useState(
    currentTurn?.finalTranscript || currentTurn?.rawTranscript || '',
  );
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const commandIdsRef = useRef(new Map<string, string>());
  const latestSnapshotRef = useRef({
    sessionId: session.id,
    version: conversation.version,
  });

  useEffect(() => {
    if (latestSnapshotRef.current.sessionId !== session.id) {
      latestSnapshotRef.current = { sessionId: session.id, version: conversation.version };
      return;
    }
    latestSnapshotRef.current.version = Math.max(
      latestSnapshotRef.current.version,
      conversation.version,
    );
  }, [conversation.version, session.id]);

  const applySnapshot = useCallback((next: AiInterviewSession) => {
    if (!next.conversation) {
      onSessionChange(next);
      return;
    }
    const nextVersion = next.conversation.version || 0;
    const latest = latestSnapshotRef.current;
    if (next.id === latest.sessionId && nextVersion < latest.version) return;
    latestSnapshotRef.current = { sessionId: next.id, version: nextVersion };
    onSessionChange(next);
  }, [onSessionChange]);

  const refreshSession = useCallback(async () => {
    const refreshed = await aiInterviewService.getSession(session.id);
    applySnapshot(refreshed);
    return refreshed;
  }, [applySnapshot, session.id]);

  useEffect(() => {
    setTranscript(currentTurn?.finalTranscript || currentTurn?.rawTranscript || '');
    setError('');
    setBusy('');
  }, [currentTurn?.id]);

  useEffect(() => {
    const waitingForServer = !conversation.errorCode
      && session.status !== 'completed'
      && (currentTurn?.answerStatus === 'PROCESSING'
        || ['ANALYZE_ANSWER', 'ACK_TRANSITION', 'CLOSING'].includes(conversation.dialogueState));
    if (!waitingForServer) return undefined;
    const timer = window.setTimeout(() => {
      void refreshSession().catch(() => undefined);
    }, 1400);
    return () => window.clearTimeout(timer);
  }, [conversation.dialogueState, conversation.errorCode, conversation.version,
    currentTurn?.answerStatus, refreshSession, session.status]);

  const commandId = useCallback((kind: string, turnId: string) => {
    const key = `${kind}:${turnId}`;
    const existing = commandIdsRef.current.get(key);
    if (existing) return existing;
    const created = window.crypto.randomUUID();
    commandIdsRef.current.set(key, created);
    return created;
  }, []);

  const createSpeechUrl = useCallback((text: string) => (
    aiInterviewService.createSpeechTicket(session.id, text)
  ), [session.id]);

  const finalizeCapture = useCallback(async (
    segments: Array<{ sequence: number; file: File; durationSeconds: number }>,
    captureId: string,
    captureVersion: number,
    browserTranscript: string,
    transcriptionProvider: 'web_speech' | 'speechmatics_realtime',
  ) => {
    if (!currentTurn || !conversation.expectsAnswer) {
      throw new Error('Lượt hội thoại hiện tại không còn nhận câu trả lời.');
    }
    return aiInterviewService.finalizeHandsFreeTurnCapture(
      session.id,
      currentTurn.id,
      captureId,
      captureVersion,
      segments,
      browserTranscript,
      transcriptionProvider,
    );
  }, [conversation.expectsAnswer, currentTurn, session.id]);

  const confirmAnswer = useCallback(async (finalTranscript: string, rawTranscript?: string) => {
    if (!currentTurn || !finalTranscript.trim()) return;
    setBusy('AI đang phân tích bằng chứng trong câu trả lời...');
    setError('');
    try {
      const next = await aiInterviewService.confirmTurn(
        session.id,
        currentTurn.id,
        commandId('confirm', currentTurn.id),
        { rawTranscript, finalTranscript: finalTranscript.trim() },
        conversation.version,
      );
      applySnapshot(next);
    } catch (confirmError) {
      const message = readError(confirmError);
      setError(message);
      await refreshSession().catch(() => undefined);
      throw new Error(message);
    } finally {
      setBusy('');
    }
  }, [applySnapshot, commandId, conversation.version, currentTurn, refreshSession, session.id]);

  const replayTurn = useCallback(async () => {
    if (!currentTurn) throw new Error('Lượt hội thoại hiện tại không còn hợp lệ.');
    setBusy('Đang ghi nhận lần đọc lại...');
    try {
      applySnapshot(await aiInterviewService.replayTurn(
        session.id,
        currentTurn.id,
        conversation.version,
      ));
    } catch (replayError) {
      throw new Error(readError(replayError));
    } finally {
      setBusy('');
    }
  }, [applySnapshot, conversation.version, currentTurn, session.id]);

  const voice = useVoiceConversation({
    questionId: currentTurn?.id,
    questionText: conversation.speechText || currentTurn?.interviewerText,
    initialTranscript: currentTurn?.finalTranscript || currentTurn?.rawTranscript || '',
    initialRawTranscript: currentTurn?.rawTranscript,
    initialConversationState: turnVoiceState(currentTurn?.answerStatus),
    confirmationPromptDelayMs: config.voiceConfirmationPromptDelayMs,
    confirmationAutoFinalizeMs: config.voiceConfirmationAutoFinalizeMs,
    recognitionRestartDelayMs: config.voiceRecognitionRestartDelayMs,
    voiceLoadWaitMs: config.voiceLoadWaitMs,
    nextQuestionDelayMs: config.voiceNextQuestionDelayMs,
    answerTranscriptionProvider: config.answerTranscriptionProvider,
    onCreateTranscriptionTicket: config.answerTranscriptionProvider === 'speechmatics_realtime'
      ? () => aiInterviewService.createTranscriptionTicket(session.id)
      : undefined,
    // UI actions are disabled separately while a command is pending. Keeping the
    // voice lifecycle enabled here lets the newly published CORE question speak
    // as soon as the monotonic backend snapshot arrives.
    disabled: !conversation.expectsAnswer,
    onTranscript: setTranscript,
    onFinalizeCapture: finalizeCapture,
    onConfirm: confirmAnswer,
    onReplayQuestion: replayTurn,
    onSpeechUrl: config.voiceStreamingEnabled ? createSpeechUrl : undefined,
  });

  async function skipCurrentTurn() {
    if (!currentTurn) return;
    voice.stop();
    setBusy('Đang chuyển sang nội dung tiếp theo...');
    setError('');
    try {
      applySnapshot(await aiInterviewService.skipTurn(
        session.id,
        currentTurn.id,
        commandId('skip', currentTurn.id),
        conversation.version,
      ));
    } catch (skipError) {
      setError(readError(skipError));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function retryConversation() {
    setBusy('Đang thử lại đúng bước bị lỗi...');
    setError('');
    try {
      applySnapshot(await aiInterviewService.retryConversation(session.id));
    } catch (retryError) {
      setError(readError(retryError));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  useEffect(() => () => voice.stop(), [voice.stop]);

  if (session.status === 'completed') {
    return (
      <div className="interview-room conversation-room">
        <button type="button" className="outline" onClick={onBack}>← Quay lại danh sách</button>
        <div className="result-panel conversation-result">
          <p className="eyebrow">Kết quả luyện tập tham khảo</p>
          <h2>{session.title}</h2>
          <strong className="score-display">
            {Math.round(Number(session.summary?.overallScore || session.overallScore || 0))}%
          </strong>
          {session.summary ? (
            <>
              <div className="chip-row result-score-breakdown">
                <span className="chip">Nội dung: {Math.round(Number(session.summary.contentScore || 0))}%</span>
                {session.summary.voiceDeliveryScore != null
                  ? <span className="chip">Giọng nói: {Math.round(Number(session.summary.voiceDeliveryScore))}%</span>
                  : <span className="chip warning">Chưa đủ dữ liệu giọng nói</span>}
                {session.summary.replayCount > 0 ? (
                  <span className="chip neutral">
                    Đọc lại {session.summary.replayCount} lần · trừ {Number(session.summary.replayPenalty || 0).toFixed(0)} điểm giao tiếp
                  </span>
                ) : null}
              </div>
              <p className="conversation-summary-copy">{session.summary.summary}</p>
              <FeedbackList title="Điểm mạnh" items={session.summary.strengths || []} />
              <FeedbackList title="Điểm cần cải thiện" items={session.summary.weaknesses || []} />
              <FeedbackList title="Kế hoạch cải thiện" items={session.summary.improvementPlan || []} />
            </>
          ) : null}
          <p className="muted conversation-reference-note">
            Kết quả chỉ phục vụ luyện tập; hệ thống không dùng bằng cấp hoặc thông tin ngoài phần phỏng vấn để chấm.
          </p>
        </div>
        <ConversationTimeline turns={conversation.timeline} />
      </div>
    );
  }

  return (
    <div className="interview-room conversation-room">
      <button type="button" className="outline" onClick={onBack}>← Quay lại danh sách</button>
      <section className="conversation-stage" aria-labelledby="conversation-title">
        <header className="conversation-stage-header">
          <div>
            <p className="eyebrow">Phỏng vấn mô phỏng</p>
            <h2 id="conversation-title">{session.title}</h2>
            <p className="muted">Một cuộc trao đổi liền mạch, gồm đúng 5 nội dung CORE được chấm.</p>
          </div>
          <div className="conversation-progress" aria-label={`${conversation.completedCoreQuestions} trên ${conversation.totalCoreQuestions} nội dung CORE đã hoàn tất`}>
            <strong>{conversation.completedCoreQuestions}/{conversation.totalCoreQuestions}</strong>
            <span>CORE hoàn tất</span>
          </div>
        </header>

        <div className="conversation-progress-track" aria-hidden="true">
          <span style={{ width: `${Math.min(100, (conversation.completedCoreQuestions / conversation.totalCoreQuestions) * 100)}%` }} />
        </div>

        <ConversationTimeline turns={conversation.timeline} compact />

        {conversation.errorCode ? (
          <div className="conversation-retry-panel" role="alert">
            <div>
              <strong>Thao tác AI tạm thời chưa hoàn tất</strong>
              <p>{conversation.errorMessage || 'Câu trả lời đã được giữ an toàn. Bạn chỉ cần thử lại bước đang lỗi.'}</p>
            </div>
            <button type="button" disabled={Boolean(busy)} onClick={() => void retryConversation()}>
              Thử lại bước này
            </button>
          </div>
        ) : null}

        {conversation.expectsAnswer && currentTurn ? (
          <section className="conversation-answer-dock" aria-labelledby="answer-dock-title">
            <div className="conversation-answer-heading">
              <div>
                <h3 id="answer-dock-title">Câu trả lời của bạn</h3>
                <p className="muted">
                  Im lặng khoảng {Math.round(config.voiceConfirmationPromptDelayMs / 1000)} giây, hệ thống sẽ hỏi xác nhận;
                  {' '}bạn vẫn có thể nói tiếp hoặc tự bấm hoàn tất.
                </p>
              </div>
              <span className="conversation-live-status" aria-live="polite">
                {voicePhaseLabel(voice.phase)}
              </span>
            </div>

            <div className="voice-controls conversation-controls">
              <button
                type="button"
                className={voice.active ? 'outline' : ''}
                disabled={Boolean(busy) || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED'].includes(voice.phase)}
                onClick={() => { if (voice.active) voice.stop(); else voice.start(); }}
              >
                {voice.active ? 'Dừng chế độ thoại' : 'Bắt đầu trả lời bằng giọng nói'}
              </button>
              <button
                type="button"
                disabled={!voice.active || Boolean(busy)
                  || !['LISTENING', 'WAITING_FOR_CONTINUATION'].includes(voice.phase)}
                onClick={voice.done}
              >
                Tôi đã trả lời xong
              </button>
              <button
                type="button"
                className="outline"
                disabled={Boolean(busy) || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'AI_SPEAKING'].includes(voice.phase)}
                onClick={() => void voice.replayQuestion()}
              >
                <IconRefresh size={16} /> Đọc lại câu hỏi
              </button>
              <button
                type="button"
                className="outline"
                disabled={Boolean(busy) || voice.phase === 'PROCESSING_AUDIO'}
                onClick={voice.manualFallback ? voice.retryVoice : voice.useManualFallback}
              >
                {voice.manualFallback ? <><IconMic size={16} /> Thử microphone lại</> : 'Nhập transcript thủ công'}
              </button>
            </div>

            <p className="voice-replay-note">
              Mỗi lần đọc lại trừ 2 điểm giao tiếp; tổng mức trừ tối đa là 10 điểm cho cả phiên.
            </p>
            <p className="voice-status" role="status">
              Nguồn transcript: Web Speech
            </p>
            {voice.transcriptNotice ? <p className="voice-status" role="status">{voice.transcriptNotice}</p> : null}
            {voice.interimTranscript ? <p className="voice-interim">Đang nghe: {voice.interimTranscript}</p> : null}

            <label className="transcript-editor">
              {['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || voice.manualFallback
                ? 'Kiểm tra và chỉnh sửa transcript trước khi xác nhận'
                : 'Transcript draft trong lúc bạn nói'}
              <textarea
                value={transcript}
                readOnly={!['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) && !voice.manualFallback}
                onChange={['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || voice.manualFallback
                  ? (event) => setTranscript(event.target.value) : undefined}
                placeholder={voice.manualFallback
                  ? 'Nhập câu trả lời của bạn tại đây...'
                  : 'Nội dung sẽ xuất hiện khi bạn nói...'}
              />
            </label>
            <p className="muted conversation-transcript-note">
              Nội dung đã sửa không bị trừ điểm. Điểm nói vẫn lấy từ audio/VAD gốc, điểm nội dung chỉ dùng transcript bạn xác nhận.
            </p>

            {voice.error ? <div className="error-panel" role="alert">{voice.error}</div> : null}
            {error ? <div className="error-panel" role="alert">{error}</div> : null}
            {['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || voice.manualFallback ? (
              <div className="manual-fallback-actions">
                {voice.phase === 'REVIEWING_TRANSCRIPT' && !voice.manualFallback ? (
                  <button type="button" className="outline" disabled={Boolean(busy)}
                    onClick={() => voice.continueAnswer(transcript)}>
                    Tiếp tục trả lời
                  </button>
                ) : null}
                <button type="button"
                  disabled={!transcript.trim() || Boolean(busy)
                    || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'NEXT_QUESTION'].includes(voice.phase)}
                  onClick={() => voice.confirmTranscript(transcript)}>
                  Xác nhận câu trả lời
                </button>
                <button type="button" className="outline" disabled={Boolean(busy)}
                  onClick={() => void skipCurrentTurn()}>
                  Bỏ qua nội dung này
                </button>
              </div>
            ) : null}
          </section>
        ) : null}

        {busy ? (
          <p className="conversation-processing" role="status">
            <span className="conversation-spinner" aria-hidden="true" /> {busy}
          </p>
        ) : null}
      </section>
    </div>
  );
}

function ConversationTimeline({
  turns,
  compact = false,
}: {
  turns: NonNullable<AiInterviewSession['conversation']>['timeline'];
  compact?: boolean;
}) {
  return (
    <section className={`conversation-timeline${compact ? ' compact' : ''}`} aria-label="Nội dung hội thoại đã diễn ra">
      {turns.map((turn) => {
        const candidateText = turn.finalTranscript || turn.rawTranscript;
        return (
          <div className={`conversation-exchange${turn.current ? ' current' : ''}`} key={turn.id}>
            <article className="conversation-bubble interviewer">
              <span className="conversation-speaker">Nhà phỏng vấn AI</span>
              <p>{turn.interviewerText}</p>
            </article>
            {candidateText || turn.answerStatus === 'SKIPPED' ? (
              <article className="conversation-bubble candidate">
                <span className="conversation-speaker">Bạn</span>
                <p>{turn.answerStatus === 'SKIPPED' ? 'Đã bỏ qua nội dung này.' : candidateText}</p>
                {turn.transcriptEdited ? <small>Transcript đã được bạn chỉnh sửa</small> : null}
              </article>
            ) : null}
          </div>
        );
      })}
    </section>
  );
}

function turnVoiceState(status?: NonNullable<AiInterviewSession['conversation']>['timeline'][number]['answerStatus']) {
  if (status === 'PROCESSING') return 'PROCESSING_AUDIO' as const;
  if (status === 'REVIEWING') return 'REVIEWING_TRANSCRIPT' as const;
  return undefined;
}

function LegacyAiInterviewRoom({
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
    setTranscript(currentQuestion?.answer?.answeredAt
      ? ''
      : currentQuestion?.answer?.finalTranscript
        || currentQuestion?.answer?.transcript
        || currentQuestion?.answer?.rawTranscript
        || '');
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
    setBusy('Đang chuyển giọng nói thành bản ghi...');
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

  async function confirmVoiceAnswer(finalTranscript: string, rawTranscript?: string) {
    if (!currentQuestion || !finalTranscript.trim()) return;
    setError('');
    try {
      onSessionChange(await aiInterviewService.confirmAnswer(
        session.id,
        currentQuestion.id,
        { rawTranscript, finalTranscript: finalTranscript.trim() },
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

  async function retryQuestionGeneration() {
    setBusy('Đang thử tạo lại câu hỏi thích ứng...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.retryQuestionGeneration(session.id));
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
    } finally {
      setBusy('');
    }
  }

  async function retryFeedback(question: AiInterviewQuestion) {
    setBusy('Đang thử lại nhận xét...');
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

  const createSpeechUrl = useCallback(async (text: string) => {
    if (config.voiceProvider !== 'shopaikey_gemini_stream') return undefined;
    return aiInterviewService.createSpeechTicket(session.id, text);
  }, [config.voiceProvider, session.id]);

  const finalizeHandsFreeCapture = useCallback(async (
    segments: Array<{ sequence: number; file: File; durationSeconds: number }>,
    captureId: string,
    captureVersion: number,
    browserTranscript: string,
    transcriptionProvider: 'web_speech' | 'speechmatics_realtime',
  ) => {
    if (!currentQuestion) throw new Error('Câu hỏi hiện tại không còn hợp lệ.');
    return aiInterviewService.finalizeHandsFreeCapture(
      session.id,
      currentQuestion.id,
      captureId,
      captureVersion,
      segments,
      browserTranscript,
      transcriptionProvider,
    );
  }, [currentQuestion, session.id]);

  const recordQuestionReplay = useCallback(async () => {
    if (!currentQuestion) throw new Error('Câu hỏi hiện tại không còn hợp lệ.');
    try {
      onSessionChange(await aiInterviewService.recordQuestionReplay(session.id, currentQuestion.id));
    } catch (replayError) {
      throw new Error(readError(replayError));
    }
  }, [currentQuestion, onSessionChange, session.id]);

  const voice = useVoiceConversation({
    questionId: currentQuestion?.id,
    questionText: currentQuestion?.content,
    initialTranscript: currentQuestion?.answer?.finalTranscript
      || currentQuestion?.answer?.transcript
      || currentQuestion?.answer?.rawTranscript
      || '',
    initialRawTranscript: currentQuestion?.answer?.rawTranscript,
    initialConversationState: currentQuestion?.answer?.conversationState,
    confirmationPromptDelayMs: config.voiceConfirmationPromptDelayMs,
    confirmationAutoFinalizeMs: config.voiceConfirmationAutoFinalizeMs,
    recognitionRestartDelayMs: config.voiceRecognitionRestartDelayMs,
    voiceLoadWaitMs: config.voiceLoadWaitMs,
    nextQuestionDelayMs: config.voiceNextQuestionDelayMs,
    answerTranscriptionProvider: config.answerTranscriptionProvider,
    onCreateTranscriptionTicket: config.answerTranscriptionProvider === 'speechmatics_realtime'
      ? () => aiInterviewService.createTranscriptionTicket(session.id)
      : undefined,
    disabled: Boolean(busy),
    onTranscript: setTranscript,
    onFinalizeCapture: finalizeHandsFreeCapture,
    onConfirm: confirmVoiceAnswer,
    onReplayQuestion: recordQuestionReplay,
    onSpeechUrl: config.voiceProvider === 'shopaikey_gemini_stream' ? createSpeechUrl : undefined,
  });

  const manualFallback = voice.manualFallback;

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
          <p><strong>Điểm luyện tập tham khảo</strong></p>
          {session.summary ? (
            <>
              <div className="chip-row result-score-breakdown">
                <span className="chip">Nội dung: {Math.round(Number(session.summary.contentScore || 0))}%</span>
                {session.summary.voiceDeliveryScore != null ? (
                  <span className="chip">Giọng nói: {Math.round(Number(session.summary.voiceDeliveryScore))}%</span>
                ) : (
                  <span className="chip warning">Chưa đủ dữ liệu giọng nói</span>
                )}
                <span className="chip">Trọng số giọng nói áp dụng: {Math.round(Number(session.summary.voiceWeight || 0) * 100)}%</span>
                {session.summary.replayCount > 0 ? (
                  <span className="chip neutral">
                    Đọc lại {session.summary.replayCount} lần · trừ {Number(session.summary.replayPenalty || 0).toFixed(0)} điểm giao tiếp
                  </span>
                ) : null}
              </div>
              {session.summary.voiceEvidenceQuestionCount === 0 ? (
                <p className="notice-panel" role="status">
                  Không đủ dữ liệu để chấm tốc độ nói và khoảng nghỉ. Điểm tổng hiện chỉ dựa trên nội dung câu trả lời.
                </p>
              ) : session.summary.manualFallbackQuestionCount > 0 ? (
                <p className="muted">
                  Điểm giọng nói được tính từ {session.summary.voiceEvidenceQuestionCount} câu có audio;
                  {' '}{session.summary.manualFallbackQuestionCount} câu nhập thủ công chỉ được chấm nội dung.
                </p>
              ) : null}
            </>
          ) : null}
          <p style={{ color: 'var(--on-muted)' }}>{session.summary?.summary}</p>
          <FeedbackList title="Điểm mạnh" items={session.summary?.strengths || []} />
          <FeedbackList title="Điểm cần cải thiện" items={session.summary?.weaknesses || []} />
          <FeedbackList title="Kế hoạch cải thiện" items={session.summary?.improvementPlan || []} />
          <p className="muted" style={{ marginTop: 16, fontSize: '0.8rem' }}>
            Nhận xét AI chỉ phục vụ luyện tập, không phải quyết định tuyển dụng.
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
          Nhận xét AI chỉ phục vụ luyện tập, không phải quyết định tuyển dụng.
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
                    Sau khoảng {Math.round(config.voiceConfirmationPromptDelayMs / 1000)} giây im lặng,
                    {' '}hệ thống sẽ hỏi bạn đã trả lời xong chưa. Nếu bạn không phản hồi thêm trong
                    {' '}{Math.round(config.voiceConfirmationAutoFinalizeMs / 1000)} giây, câu trả lời sẽ được tự hoàn tất.
                  </p>
                </div>
                <div className="voice-controls">
                  <button
                    type="button"
                    className={voice.active ? 'outline' : ''}
                    aria-pressed={voice.active}
                    disabled={Boolean(busy) || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED'].includes(voice.phase)}
                    onClick={() => {
                      if (voice.active) voice.stop();
                      else voice.start();
                    }}
                  >
                    {voice.active ? 'Dừng chế độ thoại' : 'Bắt đầu phỏng vấn'}
                  </button>
                  <button
                    type="button"
                    disabled={!voice.active || Boolean(busy)
                      || !['LISTENING', 'WAITING_FOR_CONTINUATION'].includes(voice.phase)}
                    onClick={voice.done}
                  >
                    Tôi đã trả lời xong
                  </button>
                  <button
                    type="button"
                    className="outline"
                    disabled={!voice.active || Boolean(busy)
                      || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'AI_SPEAKING'].includes(voice.phase)}
                    onClick={() => void voice.replayQuestion()}
                  >
                    <IconRefresh size={16} /> Đọc lại câu hỏi
                  </button>
                  {manualFallback ? (
                    <button type="button" className="outline"
                      disabled={Boolean(busy) || voice.phase === 'PROCESSING_AUDIO'} onClick={voice.retryVoice}>
                      <IconMic size={16} /> Thử microphone lại
                    </button>
                  ) : (
                    <button type="button" className="outline"
                      disabled={Boolean(busy) || voice.phase === 'PROCESSING_AUDIO'} onClick={voice.useManualFallback}>
                      Nhập transcript thủ công
                    </button>
                  )}
                </div>
                <p className="voice-replay-note">
                  Đã đọc lại {currentQuestion.replayCount || 0} lần. Mỗi lần đọc lại trừ 2 điểm giao tiếp,
                  tối đa 10 điểm giao tiếp cho cả phiên.
                </p>
                <p className="voice-status" role="status" aria-live="polite">
                  Trạng thái: {voicePhaseLabel(voice.phase)}
                </p>
                <p className="voice-status" role="status">
                  Nguồn transcript: Web Speech
                </p>
                {voice.transcriptNotice ? (
                  <p className="voice-status" role="status" aria-live="polite">{voice.transcriptNotice}</p>
                ) : null}
                {voice.interimTranscript ? (
                  <p className="voice-interim" aria-live="polite">Đang nghe: {voice.interimTranscript}</p>
                ) : null}
                <label className="transcript-editor">
                  {['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || manualFallback
                    ? 'Kiểm tra và chỉnh sửa transcript trước khi xác nhận'
                    : 'Nội dung hệ thống đang nghe'}
                  <textarea
                    value={transcript}
                    readOnly={!['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) && !manualFallback}
                    onChange={['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || manualFallback
                      ? (event) => setTranscript(event.target.value) : undefined}
                    placeholder={manualFallback
                      ? 'Nhập câu trả lời của bạn tại đây...'
                      : 'Câu trả lời sẽ xuất hiện trực tiếp khi bạn nói...'}
                    aria-describedby="hands-free-transcript-help"
                  />
                </label>
                <p id="hands-free-transcript-help" className="muted">
                  {manualFallback
                    ? 'Câu nhập thủ công vẫn được chấm nội dung nhưng không dùng để tính tốc độ nói và khoảng nghỉ.'
                    : 'Nội dung đã sửa không bị trừ điểm; chỉ transcript đã xác nhận mới dùng để chấm nội dung.'}
                </p>
                {voice.error ? <div className="error-panel" role="alert">{voice.error}</div> : null}
                {['REVIEWING_TRANSCRIPT', 'ERROR_RECOVERABLE'].includes(voice.phase) || manualFallback ? (
                  <div className="manual-fallback-actions">
                    {voice.phase === 'REVIEWING_TRANSCRIPT' && !manualFallback ? (
                      <button type="button" className="outline" disabled={Boolean(busy)}
                        onClick={() => voice.continueAnswer(transcript)}>
                        Tiếp tục trả lời
                      </button>
                    ) : null}
                    <button type="button"
                      disabled={!transcript.trim() || Boolean(busy)
                        || ['PROCESSING_AUDIO', 'ANSWER_CONFIRMED', 'NEXT_QUESTION'].includes(voice.phase)}
                      onClick={() => voice.confirmTranscript(transcript)}>
                      Xác nhận câu trả lời
                    </button>
                    <button type="button" className="outline" disabled={Boolean(busy)}
                      onClick={() => void skipQuestion()}>
                      Bỏ qua câu này
                    </button>
                  </div>
                ) : null}
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
                    📝 Tạo bản ghi lời nói
                  </button>
                  <button type="button" className="outline" disabled={!!busy} onClick={skipQuestion}>
                    ⏭ Bỏ qua câu này
                  </button>
                  {audioFile && <span className="muted">{Math.round(audioFile.size / 1024)} KB đã ghi</span>}
                </div>
                <label className="transcript-editor">
                  Bản ghi lời nói có thể sửa
                  <textarea
                    value={transcript}
                    onChange={(event) => setTranscript(event.target.value)}
                    placeholder="Bản ghi sẽ hiện trực tiếp khi bạn nói hoặc sau khi xử lý audio..."
                    aria-describedby="transcript-help"
                  />
                </label>
                <p id="transcript-help" className="muted">
                  Kiểm tra bản ghi lời nói trước khi lưu câu trả lời.
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
        ) : session.totalQuestions < config.questionCount ? (
          <div className="notice-panel">
            <p>Câu trả lời đã được lưu, nhưng AI chưa tạo được phần câu hỏi thích ứng. Bạn có thể thử lại mà không cần trả lời lại câu trước.</p>
            <button type="button" disabled={Boolean(busy)} onClick={() => void retryQuestionGeneration()} style={{ marginTop: 8 }}>
              Thử tạo lại câu hỏi
            </button>
          </div>
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
    IDLE: 'Sẵn sàng',
    AI_SPEAKING: 'AI đang nói',
    LISTENING: 'Đang lắng nghe...',
    WAITING_FOR_CONTINUATION: 'Đang nghe xác nhận hoặc phần trả lời tiếp theo',
    PROCESSING_AUDIO: 'Đang xử lý audio và tạo transcript draft',
    REVIEWING_TRANSCRIPT: 'Đang chờ bạn kiểm tra transcript',
    ANSWER_CONFIRMED: 'Câu trả lời đã được xác nhận',
    NEXT_QUESTION: 'Đang chuẩn bị câu hỏi tiếp theo',
    ERROR_RECOVERABLE: 'Có lỗi tạm thời, câu trả lời vẫn được giữ',
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
                  {question.answer.skipped ? 'Đã bỏ qua câu này.' : question.answer.transcript}
                </p>
                {question.answer.feedback && (
                  <div className="feedback-box">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className="chip match">{Math.round(Number(question.answer.feedback.questionScore))}%</span>
                      {question.answer.feedback.evaluationStatus === 'NOT_ANSWERED' ? (
                        <span className="chip neutral">
                          Không chấm BARS · {question.answer.feedback.scoreReason === 'SKIPPED' ? 'Đã bỏ qua' : 'Chưa trả lời'}
                        </span>
                      ) : null}
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
