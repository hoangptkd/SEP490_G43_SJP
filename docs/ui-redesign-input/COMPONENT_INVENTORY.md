# Component Inventory

## Layout Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `Shell` | `frontend/src/App.tsx` | Public/auth wrapper with topbar | public/auth/job screens | `children` | Inline; reusable only inside `App.tsx`. |
| `CandidateLayout` | `frontend/src/App.tsx` | Candidate side nav and outlet | `/candidate/*` | none | Inline route layout. |
| `EmployerLayout` | `frontend/src/App.tsx` | Employer side nav with company dropdown | `/employer/*` | none | Inline route layout. |
| `AdminLayout` | `frontend/src/pages/admin/AdminLayout.tsx` | Admin side nav, user display, logout, outlet | `/admin/*` | none | Dedicated admin shell. |
| `Layout` | `frontend/src/components/layout/Layout.tsx` | Header/main/footer wrapper | Not found in active route tree | `children` | Appears unused. Needs verification. |

## Navigation Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `Header` | `frontend/src/components/layout/Header.tsx` | Public nav | `Layout` only | none | Links to `/interviews` and `/assessments`, but routes are not declared. |
| `Footer` | `frontend/src/components/layout/Footer.tsx` | Footer | `Layout` only | Needs verification | Appears unused by current route tree. |
| Admin menu array | `frontend/src/pages/admin/AdminLayout.tsx` | Admin nav definition | `AdminLayout` | `to`, `label`, `end` | Good source for admin nav structure. |
| Candidate nav links | `frontend/src/App.tsx` | Candidate nav | `CandidateLayout` | hardcoded | Inline. |
| Employer nav/dropdown | `frontend/src/App.tsx` | Employer nav | `EmployerLayout` | hardcoded, dropdown state | Inline. |

## Form Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `Input` | `frontend/src/components/common/Input.tsx` | Tailwind input with label/error | Not found in active screens | label, type, value, onChange, error, disabled, required | Reusable but underused. |
| Native controlled forms | Many page files | Current form implementation | Most screens | local state | Screen-specific. |
| Admin reject forms | Admin company/jobs pages | Capture rejection reason | Admin review screens | textarea state | Similar pattern duplicated. |
| Upload controls | Candidate CV, employer logo/docs, AI audio | File/audio upload | Multiple screens | file/audio state | Needs consistent design pattern. |

## Data Display Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `Card` | `frontend/src/components/common/Card.tsx` | Tailwind card wrapper | Not found in active screens | children, className, onClick | Reusable but underused. |
| `JobCard` | `frontend/src/App.tsx` | Job summary card | jobs list, candidate dashboard, saved jobs | `job` | Useful reusable component. |
| `FeedbackList` | `frontend/src/App.tsx` | Titled AI feedback list | AI result/history | title, items | AI-specific inline component. |
| `QuestionHistory` | `frontend/src/App.tsx` | AI question/feedback history | AI room | questions, onRetryFeedback | Complex AI-specific inline component. |
| `DocumentRow` | `frontend/src/pages/admin/AdminCompanyReviewPage.tsx` | Legal document row | Admin company review | `doc` | Could align with employer document rows visually. |
| Status badges/pills | CSS/helpers in pages | Status display | Many screens | status string | Mapping is duplicated across pages. |

## Job Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `JobsPage` | `frontend/src/App.tsx` | Public job search/list | `/jobs` | none | Page-level. |
| `JobDetailPage` | `frontend/src/App.tsx` | Job detail/apply | `/jobs/:id` | route param | Page-level. |
| `EmployerJobsPage` | `frontend/src/pages/Employer/EmployerJobsPage.tsx` | Employer job management | `/employer/jobs` | none | Large page with inline styling. |
| `AdminJobsPage` | `frontend/src/pages/admin/AdminJobsPage.tsx` | Admin job review | `/admin/jobs` | none | List-detail workflow. |

## Candidate Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `CandidateHome` | `frontend/src/App.tsx` | Recommendations | `/candidate` | none | Inline page. |
| `ProfilePage` | `frontend/src/App.tsx` | Candidate profile | `/candidate/profile` | none | Inline page. |
| `CvPage` | `frontend/src/App.tsx` | CV management | `/candidate/cvs` | none | Inline page. |
| `SavedJobsPage` | `frontend/src/App.tsx` | Saved jobs | `/candidate/saved-jobs` | none | Inline page. |
| `ApplicationsPage` | `frontend/src/App.tsx` | Application list | `/candidate/applications` | none | Inline page. |
| `ApplicationDetailPage` | `frontend/src/App.tsx` | Application timeline | `/candidate/applications/:id` | route param | Inline page. |
| `NotificationsPage` | `frontend/src/App.tsx` | Notifications | `/candidate/notifications` | none | Inline page. |
| `SubscriptionPage` | `frontend/src/App.tsx` | Plan/usage | `/candidate/subscription` | none | Inline page. |
| `AiInterviewPage` | `frontend/src/App.tsx` | AI interview dashboard | `/candidate/ai-interviews` | none | Large inline page. |
| `AiInterviewRoom` | `frontend/src/App.tsx` | Active/completed AI room | AI page | config, session, onSessionChange, onBack | Complex interaction component. |

## Employer Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `EmployerDashboard` | `frontend/src/App.tsx` | Employer placeholder | `/employer` | none | Placeholder/minimal. |
| `CompanyProfilePage` | `frontend/src/pages/Employer/CompanyProfilePage.tsx` | Company profile/logo | `/employer/company-profile` | none | Many inline styles. |
| `CompanyLocationsPage` | `frontend/src/pages/Employer/CompanyLocationsPage.tsx` | Branch CRUD | `/employer/locations` | none | Add/edit panel. |
| `CompanyVerificationPage` | `frontend/src/pages/Employer/CompanyVerificationPage.tsx` | Legal docs/status | `/employer/verification` | none | File constraints important. |
| `EmployerJobsPage` | `frontend/src/pages/Employer/EmployerJobsPage.tsx` | Job CRUD/review | `/employer/jobs` | none | Verification gating important. |

## Admin Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `AdminProtected` | `frontend/src/pages/admin/AdminLayout.tsx` | Admin guard | admin route element | children | Checks token and stored role. |
| `AdminLoginPage` | `frontend/src/pages/admin/AdminLoginPage.tsx` | Admin login | `/admin/login` | none | Separate from normal login. |
| `AdminDashboardPage` | `frontend/src/pages/admin/AdminDashboardPage.tsx` | Dashboard placeholder | `/admin` | none | Metrics placeholders. |
| `AdminCompanyReviewPage` | `frontend/src/pages/admin/AdminCompanyReviewPage.tsx` | Company review | `/admin/companies` | none | Implemented list-detail. |
| `AdminJobsPage` | `frontend/src/pages/admin/AdminJobsPage.tsx` | Job review | `/admin/jobs` | none | Implemented list-detail. |
| `AdminUsersPage` | `frontend/src/pages/admin/AdminUsersPage.tsx` | Users placeholder | `/admin/users` | none | Not implemented. |
| `AdminStatisticsPage` | `frontend/src/pages/admin/AdminStatisticsPage.tsx` | Statistics placeholder | `/admin/statistics` | none | No charts/API. |
| `AdminSettingsPage` | `frontend/src/pages/admin/AdminSettingsPage.tsx` | Settings placeholder | `/admin/settings` | none | No forms/API. |
| `AdminProfilePage` | `frontend/src/pages/admin/AdminProfilePage.tsx` | Admin account details | `/admin/profile` | none | Read-only. |

## Feedback Components
| Component | File path | Purpose | Used by | Main props | Reuse/refactor note |
|---|---|---|---|---|---|
| `Modal` | `frontend/src/components/common/Modal.tsx` | Generic overlay | Not found in active screens | isOpen, onClose, title, children | Current screens use inline forms and browser alerts/confirms. |
| CSS feedback classes | `frontend/src/styles/global.css` | `.error`, `.success`, `.loading`, `.empty-state`, `.notice-panel` | Many screens | class names | Important design-system states. |
| Browser alert/confirm | Employer/Admin pages | Confirm destructive/review actions | locations, docs, jobs, admin review | native API | Can be redesigned as presentation if semantics remain. |
