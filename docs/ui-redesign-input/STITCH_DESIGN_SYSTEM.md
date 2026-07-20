# Stitch Design System: Smart Recruitment Portal

## Selected Direction
Use **Trust Blue Operations**: a professional recruitment SaaS visual system with trust blue, slate neutrals, green success states, restrained cards, compact forms, and dense admin-friendly tables.

## Design Mood
Trustworthy, modern, polished, calm, professional, and suitable for long work sessions. It should support both public job discovery and data-dense candidate/employer/admin dashboards.

## Brand Palette
- Primary: `#0369A1`
- Primary hover: `#075985`
- Secondary: `#0EA5E9`
- CTA/success action: `#16A34A`
- Soft brand background: `#F0F9FF`
- App background: `#F8FAFC`
- Surface: `#FFFFFF`
- Text strong: `#0F172A`
- Text body: `#334155`
- Text muted: `#64748B`
- Border: `#E2E8F0`

## Semantic Colors
- Success/approved/verified: `#15803D` on `#F0FDF4`
- Info/active/published: `#0369A1` on `#F0F9FF`
- Warning/pending/review: `#B45309` on `#FFFBEB`
- Danger/rejected/error: `#B91C1C` on `#FEF2F2`
- Neutral/draft/unverified: `#475569` on `#F1F5F9`

## Typography
- Use `Inter` or `Source Sans 3` for most UI.
- Use `Lexend` only if a more branded heading style is needed.
- Use monospace only for IDs, timestamps, or technical values.
- Scale: display 40, h1 32, h2 24, h3 20, body 16, small body 14, label 13, caption 12.
- Keep dashboard headings compact.

## Shape, Spacing, and Elevation
- Base spacing: 4px.
- Common gaps: 8, 12, 16, 20, 24, 32, 48.
- Controls radius: 6px.
- Cards/tables/modals radius: 8px.
- Avoid large rounded cards.
- Use borders by default. Use shadows only for menus, modals, toasts, and sticky overlays.

## Layout
- Public container: max 1180px.
- Dashboard container: max 1280px, full width for data tables.
- Admin review container: max 1440px.
- Auth panel: max 440px.
- Breakpoints: 375, 640, 768, 1024, 1280, 1440.
- Public/job pages use 12-column layout on desktop and one column on mobile.
- Admin review uses list pane plus detail pane on desktop, stacked on mobile.
- Forms use two columns desktop, one column mobile.

## Components
### Buttons
Primary blue, secondary bordered, danger red, ghost neutral, link button. Height 40px dense and 44px public/auth. Clear disabled and loading states.

### Forms
Labels above inputs. Inputs/selects/textareas use white background, slate border, 6px radius, clear focus ring, inline helper/error text. Textareas minimum 112px.

### Tabs
Segmented tabs for small mode switches such as AI application/practice. Underline tabs for dashboard sections.

### Cards
White surface, 1px border, 8px radius, 16-20px padding. Job cards can hover with blue border. Avoid nested cards.

### Tables
Compact, readable, sticky headers when long. Row hover, selected state, visible action buttons. On mobile, convert rows into stacked cards when possible.

### Status Badges
Text labels required. Use success for verified/approved, warning for pending, danger for rejected, neutral for draft/unverified, info for active/published.

### Alerts and Toasts
Inline alerts for form/API states. Toasts for successful save/delete/submit. Error states should remain visible near the affected workflow.

### Modals and Drawers
Use modals for confirmations currently represented by browser confirm/alert. Use drawers for mobile navigation or optional details. Radius 8px, strong focus management.

### Tooltips
Use for unfamiliar icons and truncated labels only. Do not hide critical data only in tooltips.

### Pagination
Desktop: previous/next, pages, item count. Mobile: previous/next plus current count.

### Skeletons
Use skeletons for job cards, table rows, profile forms, and AI interview loading. Preserve layout size.

### Empty States
Always include helpful message and next action. Do not leave blank screens.

### Charts
Use simple line, bar, horizontal bar, and funnel charts. No decorative chart backgrounds. Always include labels, legends, and values. Admin statistics are placeholder until real APIs exist.

## Navigation
- Public header: compact, 64px, white, bottom border.
- Candidate/employer sidebars: light, clear active item, text labels.
- Admin sidebar: dark slate/navy, compact, strong active state.
- Mobile: sidebars become drawer or stacked top navigation with 44px touch targets.

## Module Fit
- Candidate: more welcoming cards and guided empty states.
- Employer: clear forms, verification status, draft/review workflow.
- Admin: dense list-detail layouts, sticky filters, compact tables, strong status badges.

## Avoid
No heavy glassmorphism, excessive gradients, neon colors, brutalism, playful themes, large decorative animations, crypto-dashboard visuals, excessive rounding, or usability-sacrificing effects.
