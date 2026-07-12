import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { authService } from './services/authService';
import { aiInterviewService } from './services/aiInterviewService';
import { useVoiceConversation } from './hooks/useVoiceConversation';
import { clearAuthSession, getToken, setAuthSession } from './utils/authStorage';
import { candidateService } from './services/candidateService';
import { jobService } from './services/jobService';
import CompanyProfilePage from './pages/Employer/CompanyProfilePage';
import CompanyLocationsPage from './pages/Employer/CompanyLocationsPage';
import CompanyVerificationPage from './pages/Employer/CompanyVerificationPage';
import EmployerJobsPage from './pages/employer/EmployerJobsPage';
import type {
  AiInterviewConfig,
  AiInterviewEligibleApplication,
  AiInterviewQuestion,
  AiInterviewSession,
} from './types/aiInterview';
import type {
  CandidateApplication,
  CandidateProfile,
  CvFile,
  CvVersion,
  NotificationItem,
  SubscriptionView,
} from './types/candidateDomain';
import type { Job, JobFilters, Recommendation } from './types/job';

const statusLabels: Record<string, string> = {
  SUBMITTED: 'Da nop',
  UNDER_REVIEW: 'Dang xem xet',
  SHORTLISTED: 'Vao shortlist',
  INTERVIEW_SCHEDULED: 'Hen phong van',
  INTERVIEWED: 'Da phong van',
  EVALUATED: 'Da danh gia',
  ACCEPTED: 'Chap nhan',
  REJECTED: 'Tu choi',
  HIRED: 'Da tuyen',
};

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/jobs" replace />} />
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
      </Route>
      <Route path="/employer" element={<Protected><EmployerLayout /></Protected>}>
        <Route index element={<EmployerDashboard />} />
        <Route path="company-profile" element={<CompanyProfilePage />} />
        <Route path="locations" element={<CompanyLocationsPage />} />
        <Route path="verification" element={<CompanyVerificationPage />} />
        <Route path="jobs" element={<EmployerJobsPage />} />
      </Route>
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route path="/admin" element={<AdminProtected><AdminLayout /></AdminProtected>}>
        <Route index element={<AdminDashboardPage />} />
        <Route path="companies" element={<AdminCompanyReviewPage />} />
        <Route path="users" element={<AdminUsersPage />} />
        <Route path="jobs" element={<AdminJobsPage />} />
        <Route path="statistics" element={<AdminStatisticsPage />} />
        <Route path="settings" element={<AdminSettingsPage />} />
        <Route path="profile" element={<AdminProfilePage />} />
      </Route>
    </Routes>
  );
}

function formatMoney(value?: number) {
  if (!value) return 'Thoa thuan';
  return new Intl.NumberFormat('vi-VN').format(value) + ' VND';
}

function readError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || 'Co loi xay ra';
  }
  return 'Co loi xay ra';
}

function Protected({ children }: { children: JSX.Element }) {
  if (!getToken()) return <Navigate to="/login" replace />;
  return children;
}

function Shell({ children }: { children: React.ReactNode }) {
  const token = getToken();
  const role = localStorage.getItem('role');
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/jobs">Smart Recruitment</Link>
        <nav>
          <NavLink to="/jobs">Viec lam</NavLink>
          {token && role === 'CANDIDATE' && <NavLink to="/candidate">Candidate</NavLink>}
          {token && role === 'EMPLOYER' && <NavLink to="/employer">Employer</NavLink>}
          {!token && <NavLink to="/login">Dang nhap</NavLink>}
        </nav>
      </header>
      <main>{children}</main>
    </div>
  );
}

function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('candidate.demo@sjp.local');
  const [password, setPassword] = useState('Password123!');
  const [error, setError] = useState('');
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
    try {
      const response = await authService.login({ email, password });
      if (response.token) {
        setAuthSession(response.token, response.user);
      }
      if (response.user.role === 'ADMIN') {
        navigate('/admin');
      } else if (response.user.role === 'EMPLOYER') {
        navigate('/employer');
      } else if (response.user.role === 'CANDIDATE') {
        navigate('/candidate');
      } else {
        navigate('/jobs');
      }
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <Shell>
      <section className="auth-panel">
        <h1>Dang nhap</h1>
        <form onSubmit={submit} className="form-grid">
          <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          <label>Mat khau<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          {oauthError === 'google_not_configured' && <p className="error">Dang nhap Google chua duoc cau hinh tren moi truong nay.</p>}
          {error && <p className="error">{error}</p>}
          <button type="submit">Dang nhap</button>
        </form>
        {googleOAuthEnabled
          ? <a className="secondary-action" href="/api/oauth2/authorization/google">Dang nhap voi Google</a>
          : <p className="muted">Dang nhap Google chua duoc cau hinh tren moi truong nay.</p>}
        <p className="muted">Demo: candidate.demo@sjp.local / Password123!</p>
      </section>
    </Shell>
  );
}

function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'CANDIDATE' | 'EMPLOYER'>('CANDIDATE');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    setError('');
    try {
      await authService.register({ email, password, role });
      setMessage('Dang ky thanh cong. Vui long kiem tra email de xac minh tai khoan.');
    } catch (err) {
      setError(readError(err));
    }
  }

  return (
    <Shell>
      <section className="auth-panel">
        <h1>Dang ky</h1>
        <form onSubmit={submit} className="form-grid">
          <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          <label>Mat khau<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
          <label>Vai tro
            <select value={role} onChange={(e) => setRole(e.target.value as 'CANDIDATE' | 'EMPLOYER')}>
              <option value="CANDIDATE">Candidate</option>
              <option value="EMPLOYER">Employer</option>
            </select>
          </label>
          {message && <p className="success">{message}</p>}
          {error && <p className="error">{error}</p>}
          <button type="submit">Tao tai khoan</button>
        </form>
      </section>
    </Shell>
  );
}

function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [message, setMessage] = useState('Dang xac minh...');
  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setMessage('Thieu token xac minh.');
      return;
    }
    authService.verifyEmail(token)
      .then(() => setMessage('Email da duoc xac minh. Ban co the dang nhap.'))
      .catch((err) => setMessage(readError(err)));
  }, [params]);
  return <Shell><section className="auth-panel"><h1>Xac minh email</h1><p>{message}</p><Link to="/login">Ve trang dang nhap</Link></section></Shell>;
}

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
          if (user.role === 'CANDIDATE') {
            navigate('/candidate');
          } else if (user.role === 'EMPLOYER') {
            navigate('/employer');
          } else {
            navigate('/jobs');
          }
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
    return <Shell><section className="auth-panel"><h1>Loi dang nhap</h1><p className="error">{error}</p></section></Shell>;
  }

  return <Shell><section className="auth-panel"><p>Dang hoan tat dang nhap Google...</p></section></Shell>;
}

function SelectRolePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  async function select(role: 'CANDIDATE' | 'EMPLOYER') {
    const token = params.get('token');
    if (!token) return setError('Thieu token chon vai tro.');
    try {
      const response = await authService.completeOauthRole(token, role);
      if (response.token) {
        setAuthSession(response.token, response.user);
      }
      navigate(role === 'CANDIDATE' ? '/candidate' : '/employer');
    } catch (err) {
      setError(readError(err));
    }
  }
  return (
    <Shell>
      <section className="auth-panel">
        <h1>Chon vai tro</h1>
        <div className="button-row">
          <button onClick={() => select('CANDIDATE')}>Candidate</button>
          <button className="outline" onClick={() => select('EMPLOYER')}>Employer</button>
        </div>
        {error && <p className="error">{error}</p>}
      </section>
    </Shell>
  );
}

function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [filters, setFilters] = useState<JobFilters>({ sort: 'newest' });
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const response = await jobService.getAll(filters, 0, 12);
      setJobs(response.content);
    } catch (err) {
      setError(readError(err));
    }
  }, [filters]);

  useEffect(() => { void load(); }, [load]);

  return (
    <Shell>
      <section className="page-grid">
        <aside className="filter-panel">
          <h2>Tim viec</h2>
          <input placeholder="Tu khoa" value={filters.search || ''} onChange={(e) => setFilters({ ...filters, search: e.target.value })} />
          <input placeholder="Dia diem" value={filters.location || ''} onChange={(e) => setFilters({ ...filters, location: e.target.value })} />
          <input placeholder="Ky nang: Java, React" value={filters.skills || ''} onChange={(e) => setFilters({ ...filters, skills: e.target.value })} />
          <select value={filters.experienceLevel || ''} onChange={(e) => setFilters({ ...filters, experienceLevel: e.target.value })}>
            <option value="">Kinh nghiem</option>
            <option value="INTERN">Intern</option>
            <option value="FRESHER">Fresher</option>
            <option value="JUNIOR">Junior</option>
          </select>
          <select value={filters.sort || 'newest'} onChange={(e) => setFilters({ ...filters, sort: e.target.value })}>
            <option value="newest">Moi nhat</option>
            <option value="relevance">Phu hop</option>
            <option value="salary">Luong cao</option>
            <option value="deadline">Gan deadline</option>
          </select>
          <button onClick={load}>Ap dung</button>
          {error && <p className="error">{error}</p>}
        </aside>
        <section className="list-panel">
          <h1>Viec lam dang tuyen</h1>
          <div className="job-list">{jobs.map((job) => <JobCard key={job.id} job={job} />)}</div>
        </section>
      </section>
    </Shell>
  );
}

function JobCard({ job }: { job: Job }) {
  return (
    <article className="job-card">
      <div>
        <Link to={`/jobs/${job.id}`}><h3>{job.title}</h3></Link>
        <p>{job.company.name} · {job.location} · {job.experienceLevel}</p>
        <p>{formatMoney(job.salaryMin)} - {formatMoney(job.salaryMax)}</p>
      </div>
      <div className="chip-row">
        {job.matchScore !== undefined && <span className="chip strong">{job.matchScore}% match</span>}
        {job.saved && <span className="chip">Da luu</span>}
        {job.applied && <span className="chip">Da ung tuyen</span>}
      </div>
    </article>
  );
}

function JobDetailPage() {
  const { id } = useParams();
  const [job, setJob] = useState<Job | null>(null);
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [cvId, setCvId] = useState<string | undefined>();
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setJob(await jobService.getById(id));
    if (getToken()) {
      candidateService.getCvs().then((items) => {
        setCvs(items);
        setCvId(items.find((item) => item.defaultCv)?.id || items[0]?.id);
      }).catch(() => setCvs([]));
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
    try {
      await candidateService.apply(job.id, cvId);
      setMessage('Da nop ho so ung tuyen.');
      await load();
    } catch (err) {
      setMessage(readError(err));
    }
  }

  if (!job) return <Shell><p className="loading">Dang tai...</p></Shell>;
  return (
    <Shell>
      <section className="detail-page">
        <div className="detail-main">
          <p className="eyebrow">{job.company.name}</p>
          <h1>{job.title}</h1>
          <p>{job.location} · {job.experienceLevel} · {formatMoney(job.salaryMin)} - {formatMoney(job.salaryMax)}</p>
          <h2>Mo ta</h2>
          <p>{job.description}</p>
          <h2>Yeu cau</h2>
          <div className="chip-row">{job.requirements.map((item) => <span className="chip" key={item}>{item}</span>)}</div>
        </div>
        <aside className="apply-panel">
          {job.matchScore !== undefined && <strong>{job.matchScore}% phu hop</strong>}
          {getToken() ? (
            <>
              <button onClick={toggleSave}>{job.saved ? 'Bo luu' : 'Luu viec'}</button>
              <select value={cvId || ''} onChange={(e) => setCvId(e.target.value || undefined)}>
                <option value="">Chon CV</option>
                {cvs.map((cv) => <option key={cv.id} value={cv.id}>{cv.originalFileName}</option>)}
              </select>
              <button onClick={apply} disabled={job.applied}>{job.applied ? 'Da ung tuyen' : 'Ung tuyen'}</button>
            </>
          ) : <Link className="button-link" to="/login">Dang nhap de ung tuyen</Link>}
          {message && <p className={message.includes('Da nop') ? 'success' : 'error'}>{message}</p>}
        </aside>
      </section>
    </Shell>
  );
}

function CandidateLayout() {
  const navigate = useNavigate();
  function logout() {
    clearAuthSession();
    navigate('/login');
  }
  return (
    <div className="candidate-shell">
      <aside className="candidate-nav">
        <Link className="brand" to="/candidate">Candidate</Link>
        <NavLink to="/candidate/profile">Ho so</NavLink>
        <NavLink to="/candidate/cvs">CV</NavLink>
        <NavLink to="/candidate/saved-jobs">Viec da luu</NavLink>
        <NavLink to="/candidate/applications">Ung tuyen</NavLink>
        <NavLink to="/candidate/ai-interviews">AI Interview</NavLink>
        <NavLink to="/candidate/notifications">Thong bao</NavLink>
        <NavLink to="/candidate/subscription">Goi dich vu</NavLink>
        <NavLink to="/jobs">Tim viec</NavLink>
        <button onClick={logout}>Dang xuat</button>
      </aside>
      <main className="candidate-main"><Outlet /></main>
    </div>
  );
}

function CandidateHome() {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  useEffect(() => { jobService.recommendations().then(setRecommendations).catch(() => setRecommendations([])); }, []);
  return <section><h1>Dashboard ung vien</h1><div className="job-list">{recommendations.slice(0, 4).map((item) => <JobCard key={item.job.id} job={item.job} />)}</div></section>;
}

function ProfilePage() {
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [skills, setSkills] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { candidateService.getProfile().then((data) => { setProfile(data); setSkills(data.skills.join(', ')); }); }, []);
  async function save() {
    if (!profile) return;
    const saved = await candidateService.updateProfile({ ...profile, skills: skills.split(',').map((s) => s.trim()).filter(Boolean) });
    setProfile(saved);
    setMessage('Da luu ho so.');
  }
  if (!profile) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>Ho so Candidate</h1>
      <div className="form-grid two">
        <label>Ho ten<input value={profile.fullName || ''} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} /></label>
        <label>Dien thoai<input value={profile.phone || ''} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} /></label>
        <label>Dia diem<input value={profile.location || ''} onChange={(e) => setProfile({ ...profile, location: e.target.value })} /></label>
        <label>Ky nang<input value={skills} onChange={(e) => setSkills(e.target.value)} /></label>
        <label className="wide">Gioi thieu<textarea value={profile.bio || ''} onChange={(e) => setProfile({ ...profile, bio: e.target.value })} /></label>
      </div>
      <button onClick={save}>Luu ho so</button>
      <span className={profile.applyReady ? 'success' : 'muted'}>{profile.applyReady ? 'San sang ung tuyen' : 'Can hoan thien ho so va CV'}</span>
      {message && <p className="success">{message}</p>}
    </section>
  );
}

function CvPage() {
  const [cvs, setCvs] = useState<CvFile[]>([]);
  const [versions, setVersions] = useState<CvVersion[]>([]);
  const [message, setMessage] = useState('');
  async function load() {
    setCvs(await candidateService.getCvs());
    setVersions(await candidateService.getCvVersions());
  }
  useEffect(() => { load(); }, []);
  async function upload(file?: File) {
    if (!file) return;
    try {
      await candidateService.uploadCv(file);
      setMessage('Upload CV thanh cong.');
      await load();
    } catch (err) {
      setMessage(readError(err));
    }
  }
  async function createVersion() {
    await candidateService.createCvVersion(`CV Builder ${versions.length + 1}`, {});
    await load();
  }
  return (
    <section className="content-card">
      <h1>CV cua toi</h1>
      <input type="file" accept="application/pdf" onChange={(e) => upload(e.target.files?.[0])} />
      {message && <p className={message.includes('thanh cong') ? 'success' : 'error'}>{message}</p>}
      <div className="table-list">
        {cvs.map((cv) => (
          <div className="table-row" key={cv.id}>
            <strong>{cv.originalFileName}</strong>
            <span>{Math.round(cv.fileSize / 1024)} KB</span>
            <span>{cv.defaultCv ? 'Mac dinh' : 'PDF'}</span>
            <button onClick={() => candidateService.setDefaultCv(cv.id).then(load)}>Dat mac dinh</button>
            <button className="danger" onClick={() => candidateService.deleteCv(cv.id).then(load)}>Xoa</button>
          </div>
        ))}
      </div>
      <h2>CV Builder</h2>
      <button onClick={createVersion}>Tao ban CV tu ho so</button>
      <div className="table-list">{versions.map((version) => <div className="table-row" key={version.id}><strong>{version.title}</strong><span>{version.templateKey}</span><span>{new Date(version.updatedAt).toLocaleDateString('vi-VN')}</span></div>)}</div>
    </section>
  );
}

function SavedJobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  useEffect(() => { candidateService.getSavedJobs().then(setJobs); }, []);
  return <section><h1>Viec da luu</h1><div className="job-list">{jobs.map((job) => <JobCard key={job.id} job={job} />)}</div></section>;
}

function ApplicationsPage() {
  const [applications, setApplications] = useState<CandidateApplication[]>([]);
  useEffect(() => { candidateService.getApplications().then(setApplications); }, []);
  return (
    <section className="content-card">
      <h1>Ho so ung tuyen</h1>
      <div className="table-list">
        {applications.map((application) => (
          <Link className="table-row clickable" to={`/candidate/applications/${application.id}`} key={application.id}>
            <strong>{application.job.title}</strong>
            <span>{application.job.company.name}</span>
            <span className="status-pill">{statusLabels[application.status] || application.status}</span>
            <span>{new Date(application.submittedAt).toLocaleDateString('vi-VN')}</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

function ApplicationDetailPage() {
  const { id } = useParams();
  const [application, setApplication] = useState<CandidateApplication | null>(null);
  useEffect(() => { if (id) candidateService.getApplication(id).then(setApplication); }, [id]);
  if (!application) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>{application.job.title}</h1>
      <p>{application.job.company.name} · {statusLabels[application.status] || application.status}</p>
      <p>CV da nop: {application.cv?.originalFileName || application.cvVersion?.title || 'Khong co'}</p>
      <div className="timeline">
        {application.timeline.map((item) => (
          <div className="timeline-item" key={item.id}>
            <strong>{statusLabels[item.toStatus] || item.toStatus}</strong>
            <span>{new Date(item.createdAt).toLocaleString('vi-VN')}</span>
            <p>{item.publicNote}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  async function load() { setItems(await candidateService.getNotifications()); }
  useEffect(() => { load(); }, []);
  return (
    <section className="content-card">
      <h1>Thong bao</h1>
      <div className="table-list">
        {items.map((item) => <div className="table-row" key={item.id}><strong>{item.title}</strong><span>{item.message}</span><button onClick={() => candidateService.markNotificationRead(item.id).then(load)}>{item.read ? 'Da doc' : 'Danh dau doc'}</button></div>)}
      </div>
    </section>
  );
}

function SubscriptionPage() {
  const [subscription, setSubscription] = useState<SubscriptionView | null>(null);
  useEffect(() => { candidateService.getSubscription().then(setSubscription); }, []);
  if (!subscription) return <p className="loading">Dang tai...</p>;
  return (
    <section className="content-card">
      <h1>Goi dich vu</h1>
      <div className="metric-grid">
        <div><span>Goi hien tai</span><strong>{subscription.planName}</strong></div>
        <div><span>CV</span><strong>{subscription.cvCount}</strong></div>
        <div><span>Viec da luu</span><strong>{subscription.savedJobsCount}</strong></div>
        <div><span>Thong bao chua doc</span><strong>{subscription.unreadNotificationsCount}</strong></div>
      </div>
      <h2>Quyen loi</h2>
      <div className="chip-row">{subscription.benefits.map((benefit) => <span className="chip" key={benefit}>{benefit}</span>)}</div>
    </section>
  );
}

function EmployerLayout() {
  const navigate = useNavigate();
  const [companyOpen, setCompanyOpen] = useState(false);

  function logout() {
    clearAuthSession();
    navigate('/login');
  }

  return (
    <div className="employer-shell">
      <aside className="employer-nav">
        <Link className="brand" to="/employer">Employer Portal</Link>
        <NavLink to="/employer" end>Dashboard</NavLink>
        <NavLink to="/employer/jobs">Quan ly Viec lam</NavLink>

        <div className="nav-dropdown">
          <button
            type="button"
            className="nav-dropdown-trigger"
            onClick={() => setCompanyOpen(!companyOpen)}
          >
            <span>Cong ty</span>
            <span className={`arrow ${companyOpen ? 'open' : ''}`}>▼</span>
          </button>
          {companyOpen && (
            <div className="nav-dropdown-items">
              <NavLink to="/employer/company-profile" className="sub-nav-item">Ho so Cong ty</NavLink>
              <NavLink to="/employer/locations" className="sub-nav-item">Dia diem lam viec</NavLink>
              <NavLink to="/employer/verification" className="sub-nav-item">Xac thuc phap ly</NavLink>
            </div>
          )}
        </div>

        <button onClick={logout} style={{ marginTop: 'auto' }}>Dang xuat</button>
      </aside>
      <main className="employer-main"><Outlet /></main>
    </div>
  );
}

function EmployerDashboard() {
  return (
    <section className="content-card">
      <div style={{ textAlign: 'center', padding: '30px 20px', marginBottom: '20px' }}>
        <h1 style={{ color: '#245d43', marginBottom: '12px' }}>Employer Dashboard</h1>
        <p style={{ color: '#4b5b52', fontSize: '1.1rem', maxWidth: '600px', margin: '0 auto' }}>
          Chào mừng Nhà tuyển dụng đến với Smart Recruitment Portal. Quản lý hồ sơ công ty và tin tuyển dụng của bạn.
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px', padding: '0 10px' }}>
        <div style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '24px', background: '#f8fafc', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.25rem' }}>📢 Quản lý & Đăng tin tuyển dụng</h3>
            <p style={{ color: '#64748b', fontSize: '0.95rem', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Tạo mới các vị trí tuyển dụng, thiết lập mức lương, quyền lợi và theo dõi trạng thái các tin đăng. (Yêu cầu công ty đã xác thực)
            </p>
          </div>
          <Link to="/employer/jobs" style={{ background: '#245d43', color: '#fff', padding: '10px 16px', borderRadius: '6px', textAlign: 'center', textDecoration: 'none', fontWeight: 600 }}>
            Quản lý việc làm →
          </Link>
        </div>

        <div style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '24px', background: '#f8fafc', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.25rem' }}>🏢 Hồ sơ công ty & Logo</h3>
            <p style={{ color: '#64748b', fontSize: '0.95rem', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Cập nhật thông tin giới thiệu, địa điểm trụ sở và tải lên logo chính thức của doanh nghiệp.
            </p>
          </div>
          <Link to="/employer/company-profile" style={{ background: '#334155', color: '#fff', padding: '10px 16px', borderRadius: '6px', textAlign: 'center', textDecoration: 'none', fontWeight: 600 }}>
            Hồ sơ công ty →
          </Link>
        </div>

        <div style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '24px', background: '#f8fafc', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ margin: '0 0 10px 0', color: '#0f172a', fontSize: '1.25rem' }}>⚖️ Xác thực pháp lý</h3>
            <p style={{ color: '#64748b', fontSize: '0.95rem', lineHeight: 1.5, margin: '0 0 20px 0' }}>
              Tải lên giấy phép kinh doanh và các tài liệu minh chứng để được Admin phê duyệt tài khoản hợp lệ.
            </p>
          </div>
          <Link to="/employer/verification" style={{ background: '#3b82f6', color: '#fff', padding: '10px 16px', borderRadius: '6px', textAlign: 'center', textDecoration: 'none', fontWeight: 600 }}>
            Xác thực ngay →
          </Link>
        </div>
      </div>
    </section>
  );
}

function EmployerPlaceholder({ title }: { title: string }) {
  return (
    <section className="content-card">
      <div style={{ textAlign: 'center', padding: '40px 20px' }}>
        <h1 style={{ color: '#245d43', marginBottom: '16px' }}>{title}</h1>
        <p style={{ color: '#4b5b52', fontSize: '1.1rem' }}>Giao dien dang duoc phat trien.</p>
      </div>
    </section>
  );
}

function AiInterviewPage() {
  const [config, setConfig] = useState<AiInterviewConfig | null>(null);
  const [applications, setApplications] = useState<AiInterviewEligibleApplication[]>([]);
  const [sessions, setSessions] = useState<AiInterviewSession[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [activeTab, setActiveTab] = useState<'application' | 'practice'>('application');
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
      setSessions([]);
      setJobs([]);
      return;
    }
    const sessionData = await aiInterviewService.sessions();
    setSessions(sessionData);
    if (configData.enabled) {
      const [applicationData, jobsData] = await Promise.all([
        aiInterviewService.eligibleApplications(),
        jobService.getAll({ sort: 'newest' }, 0, 20).then((result) => result.content),
      ]);
      setApplications(applicationData);
      setJobs(jobsData);
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
      const session = await aiInterviewService.createPracticeSession(targetRole, skillList, jobId || undefined);
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

  if (!config) return <p className="loading">Dang tai AI Interview...</p>;

  return (
    <section className="ai-page">
      <div className="ai-heading">
        <div>
          <p className="eyebrow">Candidate practice</p>
          <h1>AI Interview</h1>
          <p className="muted">AI feedback chi dung de luyen tap, khong phai quyet dinh tuyen dung.</p>
        </div>
        <span className="status-pill">{config.questionCount} cau / session</span>
      </div>

      {!config.enabled && (
        <div className="notice-panel" role="status">
          <strong>AI Interview chua san sang</strong>
          <p>{config.message || 'AI Interview chua duoc cau hinh.'}</p>
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
          <div className="content-card">
            <div className="segmented" role="tablist" aria-label="Che do tao phong van AI">
              <button type="button" className={activeTab === 'application' ? 'active' : 'outline'} onClick={() => setActiveTab('application')}>Theo application</button>
              <button type="button" className={activeTab === 'practice' ? 'active' : 'outline'} onClick={() => setActiveTab('practice')}>Practice tu do</button>
            </div>

            {activeTab === 'application' ? (
              <div className="table-list ai-table">
                {applications.length === 0 && <p className="empty-state">Chua co application hop le de luyen phong van.</p>}
                {applications.map((application) => (
                  <div className="table-row" key={application.id}>
                    <strong>{application.job.title}</strong>
                    <span>{application.job.company.name}</span>
                    <span className="status-pill">{statusLabels[application.status] || application.status}</span>
                    <button type="button" disabled={loading} onClick={() => createFromApplication(application.id)}>Bat dau</button>
                  </div>
                ))}
              </div>
            ) : (
              <form className="form-grid" onSubmit={createPractice}>
                <label>Target role
                  <input required value={targetRole} onChange={(event) => setTargetRole(event.target.value)} />
                </label>
                <label>Skills
                  <input required value={skills} onChange={(event) => setSkills(event.target.value)} placeholder="Spring Boot, PostgreSQL" />
                </label>
                <label>Optional active job
                  <select value={jobId} onChange={(event) => setJobId(event.target.value)}>
                    <option value="">Khong chon job</option>
                    {jobs.map((job) => <option value={job.id} key={job.id}>{job.title}</option>)}
                  </select>
                </label>
                <button type="submit" disabled={loading}>Tao practice session</button>
              </form>
            )}

            {message && <p className="error" role="alert">{message}</p>}
          </div>

          <div className="content-card">
            <h2>Lich su gan day</h2>
            <div className="session-list">
              {sessions.length === 0 && <p className="empty-state">Chua co phien phong van nao.</p>}
              {sessions.map((session) => (
                <article className="session-row" key={session.id}>
                  <div>
                    <strong>{session.title}</strong>
                    <p>{session.contextType === 'application' ? 'Theo application' : 'Practice tu do'} - {session.status}</p>
                  </div>
                  <div className="button-row">
                    <button type="button" className="outline" onClick={() => openSession(session.id)}>
                      {session.status === 'completed' ? 'Xem lai' : 'Resume'}
                    </button>
                    <button type="button" className="danger" onClick={() => deleteSession(session.id)}>An</button>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

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
  const currentAnswerLocked = Boolean(currentQuestion?.answer?.answeredAt);

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
      setError('Khong the truy cap microphone. Vui long kiem tra quyen trinh duyet.');
    }
  }

  function stopRecording() {
    const recorder = mediaRecorderRef.current;
    if (recorder && recorder.state !== 'inactive') {
      recorder.stop();
    }
    setIsRecording(false);
  }

  async function transcribe() {
    if (!audioFile || !currentQuestion) return;
    setBusy('Dang chuyen giong noi thanh transcript...');
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

  async function submitAnswer(answerText = transcript, propagateError = false) {
    if (!currentQuestion || !answerText.trim()) return;
    setBusy('Dang cham feedback...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.submitAnswer(session.id, currentQuestion.id, answerText.trim()));
    } catch (err) {
      setError(readError(err));
      await refreshSession().catch(() => undefined);
      if (propagateError) throw new Error(readError(err));
    } finally {
      setBusy('');
    }
  }

  async function skipQuestion() {
    if (!currentQuestion) return;
    setBusy('Dang bo qua cau hoi...');
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
    setBusy('Dang thu lai feedback...');
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
    setBusy('Dang tao lai tong ket...');
    setError('');
    try {
      onSessionChange(await aiInterviewService.retrySummary(session.id));
    } catch (err) {
      setError(readError(err));
    } finally {
      setBusy('');
    }
  }

  const voice = useVoiceConversation({
    questionId: currentQuestion?.id,
    questionText: currentQuestion?.content,
    initialTranscript: currentQuestion?.answer?.transcript || '',
    silenceMs: config.voiceSilenceMs || 4000,
    disabled: Boolean(busy) || currentAnswerLocked || !currentQuestion,
    onTranscript: setTranscript,
    onSubmit: (value) => submitAnswer(value, true),
  });

  useEffect(() => () => {
    window.clearTimeout(timerRef.current);
    if (mediaRecorderRef.current?.state !== 'inactive') mediaRecorderRef.current?.stop();
    mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
  }, []);

  if (session.status === 'completed') {
    return (
      <div className="interview-room">
        <button type="button" className="outline" onClick={onBack}>Quay lai danh sach</button>
        <div className="result-panel">
          <p className="eyebrow">Ket qua luyen tap</p>
          <h2>{session.title}</h2>
          <strong className="score-display">{Math.round(Number(session.summary?.overallScore || session.overallScore || 0))}%</strong>
          <p>{session.summary?.summary}</p>
          {session.summary?.fallback ? (
            <div className="notice-panel" role="status">
              <p>Đây là tổng kết dự phòng vì dịch vụ AI tạm thời chưa phản hồi.</p>
              <button type="button" disabled={Boolean(busy)} onClick={retrySummary}>Thử tạo lại tổng kết AI</button>
            </div>
          ) : null}
          <FeedbackList title="Diem manh" items={session.summary?.strengths || []} />
          <FeedbackList title="Diem can cai thien" items={session.summary?.weaknesses || []} />
          <FeedbackList title="Ke hoach cai thien" items={session.summary?.improvementPlan || []} />
          <p className="muted">AI feedback chi phuc vu luyen tap, khong phai quyet dinh tuyen dung.</p>
        </div>
        <QuestionHistory questions={session.questions} onRetryFeedback={retryFeedback} />
      </div>
    );
  }

  return (
    <div className="interview-room">
      <button type="button" className="outline" onClick={onBack}>Quay lai danh sach</button>
      <div className="question-panel">
        <div className="interview-progress">
          <span>Cau {currentQuestion?.orderIndex || session.totalQuestions}/{config.questionCount}</span>
          <span>{session.title}</span>
        </div>
        <p className="muted">AI feedback chi phuc vu luyen tap, khong phai quyet dinh tuyen dung.</p>
        {currentQuestion ? (
          <>
            <h2>{currentQuestion.content}</h2>
            <div className="chip-row">
              <span className="chip">{currentQuestion.questionType}</span>
              {currentQuestion.difficulty && <span className="chip">{currentQuestion.difficulty}</span>}
              {currentQuestion.skillTag && <span className="chip">{currentQuestion.skillTag}</span>}
            </div>
            {currentQuestion.answer?.errorMessage && (
              <div className="notice-panel" role="alert">
                <p>{currentQuestion.answer.errorMessage || 'He thong chua xu ly duoc cau tra loi nay, vui long thu lai.'}</p>
                {currentAnswerLocked && currentQuestion.answer.feedbackStatus === 'failed' && (
                  <button type="button" onClick={() => retryFeedback(currentQuestion)}>Retry feedback</button>
                )}
              </div>
            )}
            {currentAnswerLocked ? (
              <div className="locked-answer">
                <strong>Cau tra loi da duoc chot</strong>
                <p>{currentQuestion.answer?.skipped ? 'Da skip cau nay.' : currentQuestion.answer?.transcript}</p>
                {currentQuestion.answer?.feedbackStatus === 'failed' && !currentQuestion.answer?.errorMessage && (
                  <button type="button" onClick={() => retryFeedback(currentQuestion)}>Retry feedback</button>
                )}
              </div>
            ) : (
              <>
                {config.voiceStreamingEnabled ? (
                  <section className="voice-conversation-panel" aria-labelledby="voice-conversation-title">
                    <div>
                      <h3 id="voice-conversation-title">Hội thoại giọng nói liên tục</h3>
                      <p className="muted">AI sẽ đọc câu hỏi, nghe câu trả lời và tự gửi sau khoảng lặng.</p>
                    </div>
                    {voice.supported ? (
                      <>
                        <div className="voice-controls">
                          <button
                            type="button"
                            className={voice.active ? 'outline' : ''}
                            aria-pressed={voice.active}
                            disabled={Boolean(busy)}
                            onClick={voice.active ? voice.pause : voice.start}
                          >
                            {voice.active ? 'Tạm dừng hội thoại' : 'Bắt đầu hội thoại bằng mic'}
                          </button>
                          <label className="voice-auto-submit">
                            <input
                              type="checkbox"
                              checked={voice.autoSubmit}
                              onChange={(event) => voice.setAutoSubmit(event.target.checked)}
                            />
                            Tự gửi sau {Math.round((config.voiceSilenceMs || 4000) / 1000)} giây im lặng
                          </label>
                        </div>
                        <p className="voice-status" role="status" aria-live="polite">
                          Trạng thái: {voicePhaseLabel(voice.phase)}
                        </p>
                        {voice.interimTranscript ? (
                          <p className="voice-interim" aria-live="polite">Đang nghe: {voice.interimTranscript}</p>
                        ) : null}
                        {voice.error ? <p className="error" role="alert">{voice.error}</p> : null}
                      </>
                    ) : (
                      <p className="notice-panel" role="status">
                        Trình duyệt này chưa hỗ trợ nhận dạng giọng nói liên tục. Bạn vẫn có thể ghi âm thủ công bên dưới.
                      </p>
                    )}
                  </section>
                ) : null}
                <div className="recorder-panel">
                  <button type="button" disabled={voice.active} onClick={isRecording ? stopRecording : startRecording}>
                    {isRecording ? 'Dung ghi am' : 'Bat dau ghi am'}
                  </button>
                  <button type="button" className="outline" disabled={!audioFile || isRecording || !!busy} onClick={transcribe}>
                    Tao transcript
                  </button>
                  <button type="button" className="outline" disabled={!!busy} onClick={skipQuestion}>Skip cau nay</button>
                  {audioFile && <span className="muted">{Math.round(audioFile.size / 1024)} KB da ghi</span>}
                </div>
                <label className="transcript-editor">Transcript có thể sửa
                  <textarea
                    value={transcript}
                    onChange={(event) => setTranscript(event.target.value)}
                    placeholder="Transcript sẽ hiện trực tiếp khi bạn nói hoặc sau khi xử lý audio..."
                    aria-describedby="transcript-help"
                  />
                </label>
                <p id="transcript-help" className="muted">Kiểm tra transcript trước khi gửi nếu chế độ tự gửi đang tắt.</p>
                <button type="button" disabled={!transcript.trim() || !!busy} onClick={() => void submitAnswer()}>Gửi câu trả lời để nhận feedback</button>
              </>
            )}
          </>
        ) : (
          <div className="notice-panel">
            <p>Da tra loi du cau. Neu tong ket chua hien thi, hay thu lai.</p>
            <button type="button" onClick={retrySummary}>Retry tong ket</button>
          </div>
        )}
        {busy && <p className="muted" role="status">{busy}</p>}
        {error && <p className="error" role="alert">{error}</p>}
      </div>
      <QuestionHistory questions={session.questions} onRetryFeedback={retryFeedback} />
    </div>
  );
}

function findCurrentQuestion(session: AiInterviewSession) {
  return session.questions.find((question) => !question.answer?.answeredAt || question.answer.feedbackStatus === 'failed');
}

function voicePhaseLabel(phase: 'idle' | 'speaking' | 'listening' | 'processing' | 'paused' | 'error') {
  const labels = {
    idle: 'Sẵn sàng',
    speaking: 'AI đang đọc câu hỏi',
    listening: 'Microphone đang nghe',
    processing: 'Đang gửi và chấm câu trả lời',
    paused: 'Đã tạm dừng',
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
    <div className="content-card">
      <h2>Cau hoi da xu ly</h2>
      <div className="question-history">
        {questions.map((question) => (
          <article className="history-item" key={question.id}>
            <strong>Cau {question.orderIndex}: {question.content}</strong>
            {question.answer?.answeredAt && (
              <>
                <p>{question.answer.skipped ? 'Da skip cau nay.' : question.answer.transcript}</p>
                {question.answer.feedback && (
                  <div className="feedback-box">
                    <span className="status-pill">{Math.round(Number(question.answer.feedback.score))}%</span>
                    {question.answer.feedback.fallback ? (
                      <>
                        <span className="fallback-pill">Đánh giá dự phòng</span>
                        {onRetryFeedback ? (
                          <button type="button" className="outline" onClick={() => void onRetryFeedback(question)}>
                            Thử chấm lại bằng AI
                          </button>
                        ) : null}
                      </>
                    ) : null}
                    <p>{question.answer.feedback.feedback}</p>
                    <FeedbackList title="Diem manh" items={question.answer.feedback.strengths} />
                    <FeedbackList title="Can cai thien" items={question.answer.feedback.weaknesses} />
                    <FeedbackList title="Goi y" items={question.answer.feedback.suggestions} />
                  </div>
                )}
              </>
            )}
          </article>
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
