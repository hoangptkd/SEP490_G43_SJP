import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import type { NotificationItem } from '../types/candidateDomain';
import { IconInbox, getNotificationTypeIcon } from './icons/PortalNavIcons';

const EASE_OUT = [0.23, 1, 0.32, 1] as const;

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
}

function groupLabel(iso: string) {
  const date = new Date(iso);
  const diff = Math.round((startOfDay(new Date()) - startOfDay(date)) / 86400000);
  if (diff === 0) return 'Hôm nay';
  if (diff === 1) return 'Hôm qua';
  return date.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' });
}

function relativeTime(iso: string) {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'Vừa xong';
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return new Date(iso).toLocaleDateString('vi-VN');
}

function toneFor(type: string) {
  switch (type) {
    case 'APPLICATION_RECEIVED':
      return 'blue';
    case 'INTERVIEW_SCHEDULED':
      return 'amber';
    case 'JOB_OFFER_SENT':
      return 'green';
    case 'APPLICATION_STATUS_CHANGED':
      return 'indigo';
    case 'JOB_UPDATED':
      return 'slate';
    default:
      return 'teal';
  }
}

export function NotificationInbox({
  items,
  loading,
  emptyTitle,
  emptyHint,
  getLink,
  onMarkRead,
  onMarkAllRead,
}: {
  items: NotificationItem[];
  loading: boolean;
  emptyTitle: string;
  emptyHint: string;
  getLink: (item: NotificationItem) => string | null;
  onMarkRead: (id: string) => void;
  onMarkAllRead?: () => void;
}) {
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const safeItems = Array.isArray(items) ? items : [];
  const unreadCount = safeItems.filter((item) => !item.read).length;
  const visible = filter === 'unread' ? safeItems.filter((item) => !item.read) : safeItems;
  const groups = useMemo(() => {
    const map = new Map<string, NotificationItem[]>();
    visible.forEach((item) => {
      const label = groupLabel(item.createdAt);
      const bucket = map.get(label) || [];
      bucket.push(item);
      map.set(label, bucket);
    });
    return Array.from(map.entries());
  }, [visible]);

  if (loading) {
    return (
      <div className="notif-inbox">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className="notif-card is-skeleton">
            <div className="skeleton" style={{ width: 44, height: 44, borderRadius: 12 }} />
            <div style={{ display: 'grid', gap: 8, flex: 1 }}>
              <div className="skeleton" style={{ height: 16, width: '46%' }} />
              <div className="skeleton" style={{ height: 14, width: '78%' }} />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="notif-inbox">
      <div className="notif-toolbar">
        <div className="notif-filters" role="tablist" aria-label="Lọc thông báo">
          <button type="button" className={filter === 'all' ? 'is-active' : ''} onClick={() => setFilter('all')}>
            Tất cả
          </button>
          <button type="button" className={filter === 'unread' ? 'is-active' : ''} onClick={() => setFilter('unread')}>
            Chưa đọc{unreadCount > 0 ? ` (${unreadCount})` : ''}
          </button>
        </div>
        {unreadCount > 0 && onMarkAllRead && (
          <button type="button" className="outline sm" onClick={onMarkAllRead}>
            Đánh dấu đã đọc tất cả
          </button>
        )}
      </div>

      {visible.length === 0 ? (
        <div className="notif-empty">
          <div className="empty-state-icon"><IconInbox size={36} /></div>
          <h3>{filter === 'unread' ? 'Không còn thông báo chưa đọc' : emptyTitle}</h3>
          <p className="muted">{filter === 'unread' ? 'Bạn đã xem hết các cập nhật mới.' : emptyHint}</p>
        </div>
      ) : (
        groups.map(([label, groupItems]) => (
          <section key={label} className="notif-group">
            <h2 className="notif-group-label">{label}</h2>
            {groupItems.map((item, index) => {
              const link = getLink(item);
              return (
                <motion.article
                  key={item.id}
                  className={`notif-card${item.read ? '' : ' is-unread'}`}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.22, ease: EASE_OUT, delay: Math.min(index * 0.03, 0.18) }}
                >
                  <div className={`notif-icon tone-${toneFor(item.type)}`}>
                    {getNotificationTypeIcon(item.type, 20)}
                  </div>
                  <div className="notif-body">
                    <div className="notif-title-row">
                      {!item.read && <span className="notif-dot" aria-hidden="true" />}
                      <strong>{item.title}</strong>
                      {!item.read && <span className="notif-badge">Mới</span>}
                    </div>
                    <p>{item.message}</p>
                    <time dateTime={item.createdAt} title={new Date(item.createdAt).toLocaleString('vi-VN')}>
                      {relativeTime(item.createdAt)}
                    </time>
                  </div>
                  <div className="notif-actions">
                    {!item.read && (
                      <button type="button" className="ghost sm" onClick={() => onMarkRead(item.id)}>
                        Đã đọc
                      </button>
                    )}
                    {link && (
                      <Link
                        to={link}
                        className="button-link sm"
                        onClick={() => { if (!item.read) onMarkRead(item.id); }}
                      >
                        Xem chi tiết
                      </Link>
                    )}
                  </div>
                </motion.article>
              );
            })}
          </section>
        ))
      )}
    </div>
  );
}
