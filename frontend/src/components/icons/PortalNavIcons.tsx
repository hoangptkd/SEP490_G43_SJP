import type { ReactNode } from 'react';

type IconProps = {
  size?: number;
  className?: string;
};

function NavIcon({ size = 18, className, children }: IconProps & { children: ReactNode }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IconHome(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5.5 9.5V21h13V9.5" />
      <path d="M10 21v-6h4v6" />
    </NavIcon>
  );
}

export function IconDashboard(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M4 19V10" />
      <path d="M10 19V5" />
      <path d="M16 19v-8" />
      <path d="M22 19H2" />
    </NavIcon>
  );
}

export function IconProfile(props: IconProps) {
  return (
    <NavIcon {...props}>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20a7 7 0 0 1 14 0" />
    </NavIcon>
  );
}

export function IconLock(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </NavIcon>
  );
}

export function IconDocument(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M7 3h7l5 5v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" />
      <path d="M14 3v5h5" />
      <path d="M9 13h6M9 17h6" />
    </NavIcon>
  );
}

export function IconBookmark(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M7 4h10a1 1 0 0 1 1 1v16l-6-3.5L6 21V5a1 1 0 0 1 1-1z" />
    </NavIcon>
  );
}

export function IconClipboard(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="6" y="5" width="12" height="16" rx="2" />
      <path d="M9 5V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1" />
      <path d="M9 12h6M9 16h4" />
    </NavIcon>
  );
}

export function IconRobot(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="5" y="8" width="14" height="11" rx="3" />
      <path d="M12 4v4" />
      <circle cx="12" cy="3.5" r="1" fill="currentColor" stroke="none" />
      <circle cx="9.5" cy="13" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="14.5" cy="13" r="1.2" fill="currentColor" stroke="none" />
      <path d="M9 16.5h6" />
    </NavIcon>
  );
}

export function IconBell(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M7 10a5 5 0 0 1 10 0c0 4 1.5 5.5 1.5 5.5H5.5S7 14 7 10z" />
      <path d="M10.5 19a1.5 1.5 0 0 0 3 0" />
    </NavIcon>
  );
}

export function IconClock(props: IconProps) {
  return (
    <NavIcon {...props}>
      <circle cx="12" cy="13" r="7" />
      <path d="M12 10v4l2.5 1.5" />
      <path d="M9 3h6" />
    </NavIcon>
  );
}

export function IconGem(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M6 9 9.5 4h5L18 9l-6 11L6 9z" />
      <path d="M6 9h12" />
    </NavIcon>
  );
}

export function IconSearch(props: IconProps) {
  return (
    <NavIcon {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16.5 16.5 21 21" />
    </NavIcon>
  );
}

export function IconLogout(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" />
      <path d="M15 16l4-4-4-4" />
      <path d="M9 12h10" />
    </NavIcon>
  );
}

export function IconBriefcase(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="3" y="8" width="18" height="12" rx="2" />
      <path d="M9 8V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" />
      <path d="M3 13h18" />
    </NavIcon>
  );
}

export function IconUsers(props: IconProps) {
  return (
    <NavIcon {...props}>
      <circle cx="9" cy="9" r="3" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <circle cx="17" cy="9.5" r="2.5" />
      <path d="M15.2 19a4.8 4.8 0 0 1 5.3-3.8" />
    </NavIcon>
  );
}

export function IconBuilding(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16" />
      <path d="M15 10h4a1 1 0 0 1 1 1v10" />
      <path d="M8 8h2M8 12h2M8 16h2M4 21h16" />
    </NavIcon>
  );
}

export function IconSettings(props: IconProps) {
  return (
    <NavIcon {...props}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09A1.65 1.65 0 0 0 15 4.6a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9c.36.56.96.9 1.51.9H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </NavIcon>
  );
}

export function IconChevron(props: IconProps) {
  return (
    <NavIcon {...props} size={props.size ?? 14}>
      <path d="M6 9l6 6 6-6" />
    </NavIcon>
  );
}

export function IconCalendar(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M8 3v4M16 3v4M3 10h18" />
    </NavIcon>
  );
}

export function IconRefresh(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M21 12a9 9 0 1 1-2.6-6.3" />
      <path d="M21 4v6h-6" />
    </NavIcon>
  );
}

export function IconMail(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 7 9-7" />
    </NavIcon>
  );
}

export function IconInbox(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M4 8h16v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8z" />
      <path d="M4 13h4l1.5 2h5L16 13h4" />
      <path d="M9 4h6l1 4H8l1-4z" />
    </NavIcon>
  );
}

export function IconAlert(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M12 3 2.5 20h19L12 3z" />
      <path d="M12 10v4M12 17h.01" />
    </NavIcon>
  );
}

export function IconHandshake(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M8 13 4.5 9.5a2 2 0 0 1 0-2.8L7 4.2a2 2 0 0 1 2.8 0L12 6.4" />
      <path d="M16 13l3.5-3.5a2 2 0 0 0 0-2.8L17 4.2a2 2 0 0 0-2.8 0L12 6.4" />
      <path d="M8 13c1.5 2 4.5 2 6 0" />
      <path d="M7 17h10" />
    </NavIcon>
  );
}

export function IconGift(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="4" y="10" width="16" height="10" rx="1.5" />
      <path d="M12 10v10M4 14h16" />
      <path d="M12 10c-2.2 0-4-1.3-4-3s2-2.2 4-1c2-1.2 4-.1 4 1s-1.8 3-4 3z" />
    </NavIcon>
  );
}

/** Icon loại thông báo — đồng bộ menu (monochrome). */
export function getNotificationTypeIcon(type: string, size = 20) {
  switch (type) {
    case 'JOB_UPDATED':
      return <IconDocument size={size} />;
    case 'APPLICATION_STATUS_CHANGED':
      return <IconRefresh size={size} />;
    case 'JOB_OFFER_SENT':
      return <IconGift size={size} />;
    case 'INTERVIEW_SCHEDULED':
      return <IconCalendar size={size} />;
    case 'APPLICATION_RECEIVED':
      return <IconMail size={size} />;
    default:
      return <IconBell size={size} />;
  }
}

export function IconMapPin(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M12 21s-7-5.8-7-11a7 7 0 1 1 14 0c0 5.2-7 11-7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </NavIcon>
  );
}

export function IconWallet(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M3 10h18" />
      <path d="M16 14.5h2" />
    </NavIcon>
  );
}

export function IconSpark(props: IconProps) {
  return (
    <NavIcon {...props}>
      <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
      <circle cx="12" cy="12" r="3.2" />
    </NavIcon>
  );
}

export function IconMic(props: IconProps) {
  return (
    <NavIcon {...props}>
      <rect x="9" y="3" width="6" height="11" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0" />
      <path d="M12 18v3M9 21h6" />
    </NavIcon>
  );
}

export function IconBrandBriefcase(props: IconProps) {
  return (
    <svg
      className={props.className}
      width={props.size ?? 20}
      height={props.size ?? 20}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <rect x="2" y="7" width="20" height="14" rx="2" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 7V5.5A1.5 1.5 0 0 1 9.5 4h5A1.5 1.5 0 0 1 16 5.5V7" stroke="currentColor" strokeWidth="1.8" />
      <path d="M2 12h20" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}
