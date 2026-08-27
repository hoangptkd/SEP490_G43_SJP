# User Roles and Permissions

## Sources Reviewed
- Frontend route guards in `frontend/src/App.tsx`.
- Admin route guard in `frontend/src/pages/admin/AdminLayout.tsx`.
- Axios auth interceptor in `frontend/src/services/api.ts`.
- Backend security in `backend/src/main/java/com/sjp/recruitment/config/SecurityConfig.java`.
- Backend controllers under `backend/src/main/java/com/sjp/recruitment/controller`.

## Guest
Allowed:
- Browse `/jobs` and `/jobs/:id`.
- Use `/login`, `/register`, `/verify-email`, `/oauth/callback`, `/select-role`.
- Call public backend endpoints: auth/OAuth/health plus GET `/jobs`, `/jobs/**`, `/categories`, `/categories/**`.

Restricted:
- Cannot access `/candidate/*`, `/employer/*`, or `/admin/*` without auth.
- Cannot save jobs or apply from job detail unless authenticated.

## Candidate
Allowed in current frontend:
- Candidate dashboard and recommendations.
- Candidate profile view/update.
- CV upload, set default CV, delete CV, create CV versions.
- Saved jobs view, save, unsave.
- Apply to jobs and view own applications/application detail.
- Notifications and mark notification read.
- Subscription summary.
- AI interview config, eligible applications, sessions, practice sessions, audio transcription, answer submission, skip, retry feedback, retry summary, delete/hide session.

Restrictions and verification notes:
- `AiInterviewController` has `@PreAuthorize("hasRole('CANDIDATE')")`.
- Candidate/employer `Protected` wrapper in `App.tsx` checks only token, not role.
- Other candidate endpoint role checks may exist in services. Needs verification.

## Employer
Allowed in current frontend:
- Employer dashboard placeholder.
- Company profile view/update and logo upload.
- Company location CRUD.
- Legal verification document upload/view/download/delete.
- Company job list.
- If company is verified, create/edit/delete jobs, save draft, submit for review, resubmit rejected jobs.

Restrictions and verification notes:
- Job creation UI is blocked unless `company.verified` or `verificationStatus` is `verified`.
- Headquarter location delete is blocked in UI.
- Employer controller endpoints are authenticated, but explicit controller-level `@PreAuthorize` was not found. Service-level role checks need verification.

## Admin
Allowed:
- Admin-only login through `/admin/login`; frontend rejects non-admin login responses.
- Admin layout protected by token plus stored user role `ADMIN`.
- Company review: filter, select, inspect documents, approve, reject with reason.
- Job review: filter, select, inspect detail, approve, reject with reason.
- Read own admin profile.

Restrictions and verification notes:
- Non-admin users redirect to `/admin/login`.
- Backend admin endpoints are authenticated by global security; explicit controller role annotations were not found. Service-level role checks need verification.

## Important Permission Gaps
- Candidate and employer frontend protected routes only require a token.
- Admin frontend protection is stronger because it checks stored user role.
- AI interview is explicitly candidate-only at backend controller level.
