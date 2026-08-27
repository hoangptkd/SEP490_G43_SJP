# Frontend Tech Stack

## Framework and Versions
- React `^18.2.0`
- React DOM `^18.2.0`
- Vite `^5.1.0`
- TypeScript `^5.3.3`

## Language
- TypeScript with `.tsx` React components.

## CSS Framework
- Tailwind CSS `^3.4.1` is installed and imported in `frontend/src/styles/global.css`.
- Current screens mostly use custom global CSS classes and inline styles, especially Employer/Admin pages.

## Component Library
- No third-party UI component library found.
- Local primitives exist: `Button`, `Input`, `Card`, `Modal` in `frontend/src/components/common`.
- Current route screens mostly use native HTML elements and custom CSS rather than these primitives.

## State Management
- Redux Toolkit `^2.2.1` and React Redux `^9.1.0` are installed.
- Store and slices exist under `frontend/src/store`.
- Current screens reviewed mainly use local React state and direct service calls. Redux usage in active UI needs verification.

## Form and Validation Libraries
- `react-hook-form ^7.50.0`, `@hookform/resolvers ^3.3.4`, and `zod ^3.22.4` are installed.
- Reviewed forms are controlled React forms with native validation and backend error handling, not react-hook-form/zod.

## Chart Library
- No chart library found in `package.json`.
- Admin statistics is a placeholder.

## Icon Library
- No icon library found in `package.json`.
- Current UI uses text labels and occasional text symbols.

## Routing
- `react-router-dom ^6.22.0`.
- Route tree is declared in `frontend/src/App.tsx`.
- Browser routing is mounted in `frontend/src/main.tsx` with `BrowserRouter`.

## API Client
- Axios `^1.6.7`.
- `frontend/src/services/api.ts` creates a shared Axios instance.
- Base URL: `import.meta.env.VITE_API_URL || '/api'`.
- Request interceptor adds `Authorization: Bearer <token>` from `localStorage.token`.
- Response interceptor handles 401 by clearing auth storage and redirecting to `/admin/login` for admin paths, otherwise `/login`.

## Authentication Method
- JWT token stored in `localStorage` as `token`.
- User stored as JSON in `localStorage.user`.
- Role also stored in `localStorage.role`.
- Helpers: `getToken`, `getStoredUser`, `setAuthSession`, `clearAuthSession` in `frontend/src/utils/authStorage.ts`.
- Google OAuth entry link: `/api/oauth2/authorization/google`, shown only if `/auth/config` enables it.

## UI-Related Dependencies
- `clsx ^2.1.0`
- `tailwind-merge ^2.2.1`
- `date-fns ^3.3.1` is installed, though reviewed screens mostly use native date formatting.
