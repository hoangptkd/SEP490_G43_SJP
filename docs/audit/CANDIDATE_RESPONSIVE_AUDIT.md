# Candidate Responsive Audit

Static CSS review only. Browser/device verification is still required at mobile, tablet, laptop, and desktop widths.

| ID | Area | Current behavior | Problem | Evidence | Recommended behavior | Priority | Acceptance criteria |
|---|---|---|---|---|---|---|
| RESP-001 | Candidate sidebar/nav | Candidate shell collapses to one column under 860px and nav becomes horizontal scroll. | Horizontal nav can hide important routes and needs manual touch/overflow verification. | `frontend/src/styles/global.css:1833-1837`, `3137-3159`. | Provide deliberate mobile navigation such as drawer/bottom nav or clear scroll affordance. | P2 | At 360px all candidate routes are reachable without confusing horizontal overflow. |
| RESP-002 | Job search | Jobs layout collapses under 980px. | Filter panel can be long on mobile and lacks sticky apply/reset affordance. | `frontend/src/styles/global.css:967-984`, `3125-3135`. | Add mobile filter drawer or compact summary with sticky apply/clear. | P2 | Filters usable one-handed at 360px without losing context. |
| RESP-003 | Data tables | Data tables convert to card rows under 720px. | Saved/applications row content needs manual check for long titles/company names/status chips. | `frontend/src/styles/global.css:3177-3216`, `frontend/src/App.tsx:3709-3731`. | Ensure long text wraps/truncates with labels per row. | P2 | No clipped title/status on 360px and 768px. |
| RESP-004 | Apply modal | Modal maxes to full viewport on small screens and body scrolls. | Good mobile containment, but focus and safe-area behavior need verification. | `frontend/src/styles/global.css:2697-2743`, `3228-3248`. | Add safe-area padding and focus trap. | P1 | Modal content/actions remain reachable on 360px height-constrained viewport. |
| RESP-005 | Resume choices | Resume-choice grid collapses to two columns on mobile. | Long file names rely on truncation; "view" actions may shift below. | `frontend/src/styles/global.css:2769-2796`, `3242-3248`. | Keep stable action placement and accessible full filename tooltip/title. | P3 | Long filenames do not overlap controls. |
| RESP-006 | Candidate profile form | Form grids collapse to one column under 720px. | Generic section item cards may become very tall; no section navigation. | `frontend/src/styles/global.css:3217-3227`, `frontend/src/App.tsx:2986-3038`. | Add accordions or anchors for long profile sections. | P3 | Large profile remains navigable on mobile. |
| RESP-007 | AI interview config | AI grid uses main/sidebar layout. | Needs mobile verification for practice controls and history list. | `frontend/src/styles/global.css:1384-1404`, `frontend/src/App.tsx:4561-4739`. | Collapse history below main controls with clear active session state. | P2 | At 360px practice creation and session history are usable without horizontal overflow. |
| RESP-008 | AI interview room | Interview room max width 900px; recorder controls are flex-like. | Manual recording buttons and transcript textarea may crowd on mobile; button labels are long. | `frontend/src/styles/global.css:1444-1478`, `frontend/src/App.tsx:5054-5089`. | Stack controls with fixed touch targets and shorten labels. | P2 | Recording/transcript/finish controls are reachable and not overlapping on 360px. |
| RESP-009 | Notification menu | Floating notification menu is shifted on mobile. | Hard-coded `right: -58px` can still overflow depending trigger position. | `frontend/src/styles/global.css:3253-3258`. | Use viewport-aware popover positioning. | P2 | Notification popover never extends beyond viewport at 360px/768px. |
| RESP-010 | Touch targets | Buttons generally min-height 40px; checkboxes 18px. | Some small buttons use `button.sm` 32px height, below common 44px touch target guidance. | `frontend/src/styles/global.css:244-249`, `frontend/src/styles/global.css:300`. | Use >=44px for mobile or increase hit area. | P3 | All mobile interactive controls have 44px effective target. |

## Needs Manual Responsive Verification

- 360x640, 375x812, 768x1024, 1024x768, 1366x768, 1440x900.
- Candidate sidebar route reachability.
- Application detail with long offer/interview data.
- AI interview room during recording, STT loading, provider timeout, and completed feedback.
- Browser Back/refresh on job search filters and AI interview state.
