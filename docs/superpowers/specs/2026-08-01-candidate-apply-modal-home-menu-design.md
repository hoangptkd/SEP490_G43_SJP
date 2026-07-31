# Candidate Apply Modal And Home Candidate Menu Design

## Goal

Make the candidate job application flow production-ready by replacing the inline CV selector with an application modal inspired by the provided TopCV reference, and improve the logged-in candidate home navigation with notifications and an avatar dropdown menu.

## Scope

This change covers the candidate role only.

Payment flows are out of scope. Employer and admin dashboards are only touched where needed to display the new application data.

## User Decisions

- Use approach 1: store all submitted application data.
- Direct CV upload inside the application modal is saved into "My CVs" and can be reused later.
- Preferred working location is a free text/tag-style field, defaulted from the current job location.
- Home header changes apply only when a candidate is logged in.
- The candidate dashboard route remains available. The home dropdown is a quick navigation entry point, not a dashboard replacement.

## Backend Design

Add two fields to applications:

- `preferred_location`: nullable text.
- `cover_letter`: nullable text.

Update the application submit request and response:

- `ApplicationSubmitRequest` accepts `preferredLocation` and `coverLetter`.
- `ApplicationResponse` returns `preferredLocation` and `coverLetter`.
- `ApplicationService.submit` validates and persists the fields.
- `DtoMapper.toApplicationResponse` maps the fields for candidate and employer views.

Validation:

- `preferredLocation` is required by UI before submit, but backend accepts nullable/blank for compatibility with older clients.
- `coverLetter` is optional.
- Trim both fields before saving.
- Limit cover letter length in UI and backend to avoid oversized payloads. Recommended limit: 2000 characters.

Migration:

- Add a new Flyway migration after the current migration sequence.
- Do not modify existing migration files.

## Frontend Apply Modal Design

On job detail:

- Keep the right-side save button.
- Replace inline CV selector with a primary "Apply now" button.
- If candidate has already applied, button is disabled and says applied.
- If guest clicks apply, send them to login as current behavior requires.

Modal fields:

- Header with job title and company.
- CV selection area:
  - Show uploaded CVs and CV Builder versions as selectable rows.
  - Mark default or most recent CV.
  - Allow viewing an existing uploaded CV where supported.
- Upload area:
  - Click or drag/drop a file.
  - On submit, upload the file first through the existing CV upload endpoint.
  - The uploaded CV becomes available in "My CVs" and is used for this application.
- Preferred location:
  - Free text/tag-like input.
  - Default value is `job.location`.
  - Required in UI.
- Cover letter:
  - Optional textarea.
  - Character counter.
  - Optional quick action to reuse the latest cover letter only if previous cover letter data is available locally or from existing applications.
- Confirmation checkboxes:
  - Candidate confirms submitted information is accurate.
  - Candidate agrees to platform data use for recruitment matching.
- Submit button:
  - Disabled until a CV source is selected or a file is chosen, preferred location is filled, and required confirmations are checked.

Submit flow:

1. If a file is selected, call `candidateService.uploadCv(file)`.
2. Submit application with `jobId`, `cvId` or `cvVersionId`, `preferredLocation`, and `coverLetter`.
3. Close modal, refresh job detail state, and show success message.
4. On error, keep modal open and show an inline error.

## Home Candidate Header Design

When a logged-in candidate is on Home:

- Replace the candidate dashboard CTA in the home header with:
  - Notification icon button with unread badge.
  - Avatar button with candidate initials/photo fallback.
- Notification button links to `/candidate/notifications`.
- Avatar button opens a dropdown similar to the second reference image.

Dropdown content:

- Candidate identity block:
  - Full name or email fallback.
  - Profile completion/apply-ready status.
  - Email.
- Job management group:
  - Saved jobs.
  - Applied jobs.
  - Recommended jobs or matched jobs if route exists.
- CV and cover letter group:
  - My CVs.
  - Cover letter entry can route to CV page initially if there is no dedicated cover-letter page.
- Email and notifications group:
  - Notifications.
- Personal and security group:
  - Profile.
- Account plan group:
  - Subscription.
- Logout button.

The candidate dashboard `/candidate` remains available from direct navigation and can still be linked inside the dropdown if useful.

## UI Requirements

- Match the app's existing React and CSS patterns in `frontend/src/App.tsx` and `frontend/src/styles/global.css`.
- Avoid nested cards.
- Keep card radius at 8px or less.
- Use fixed button dimensions for icon buttons so badge/avatar changes do not shift the header.
- Mobile behavior:
  - Modal should fit small screens and scroll internally.
  - Header actions should not overlap with the brand or nav.
  - Dropdown should align within viewport.

## Error Handling

- No CV selected: show inline modal error.
- Invalid file type or oversized file: show inline modal error before upload where possible.
- Upload fails: keep modal open and show upload error.
- Application duplicate: close or keep modal with clear "already applied" message and refresh job state.
- Profile incomplete: show backend message and link to candidate profile.
- Backend unavailable: show network-safe error in modal.

## Testing Plan

Backend:

- Compile backend.
- Test application submit with existing CV and new fields.
- Test application submit without new fields for backward compatibility.
- Test response includes preferred location and cover letter.

Frontend:

- Build frontend.
- Login as candidate.
- Open job detail and apply using an existing uploaded CV.
- Apply using a CV Builder version.
- Apply using direct upload in modal.
- Verify preferred location and cover letter are shown in candidate application detail.
- Verify employer application detail shows preferred location and cover letter.
- Verify duplicate application flow.
- Verify Home header notification badge and avatar dropdown for candidate.
- Verify guest and employer home headers are unchanged except current expected behavior.
