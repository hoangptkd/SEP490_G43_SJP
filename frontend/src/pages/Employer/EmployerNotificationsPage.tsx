import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { employerService } from '../../services/employerService';
import type { NotificationItem } from '../../types/candidateDomain';

const fadeUp = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
  exit:    { opacity: 0, y: -8 },
};
const EASE_OUT = [0.23, 1, 0.32, 1] as const;

export default function EmployerNotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    const data = await employerService.getNotifications();
    setItems(data);
    setLoading(false);
  }

  useEffect(() => { load(); }, []);

  const getIcon = (type: string) => {
    switch (type) {
      case 'JOB_UPDATED': return '📝';
      case 'APPLICATION_STATUS_CHANGED': return '🔄';
      case 'JOB_OFFER_SENT': return '🎉';
      case 'INTERVIEW_SCHEDULED': return '📅';
      case 'APPLICATION_RECEIVED': return '📩';
      default: return '🔔';
    }
  };

  const getNotificationLink = (item: NotificationItem) => {
    if (item.relatedEntityType === 'JOB' && item.relatedEntityId) {
      return `/employer/jobs`;
    }
    if (item.relatedEntityType === 'APPLICATION' && item.relatedEntityId) {
      return `/employer/applications`;
    }
    return null;
  };

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1>Thông báo</h1>
          <p>Cập nhật từ ứng viên và hệ thống</p>
        </div>
        {items.length > 0 && items.some(i => !i.read) && (
          <button
            className="outline"
            onClick={() => employerService.markAllNotificationsRead().then(load)}
            style={{ marginTop: '8px' }}
          >
            Đánh dấu đọc tất cả
          </button>
        )}
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.5"
            style={{ animation: 'spin 0.8s linear infinite', margin: '0 auto' }}>
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
        </div>
      ) : items.length === 0 ? (
        <div className="card" style={{ padding: 48, textAlign: 'center' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>📭</div>
          <h3>Không có thông báo mới</h3>
          <p className="muted">Bạn sẽ nhận thông báo khi có hồ sơ ứng tuyển mới hoặc thông báo từ Admin.</p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {items.map((item, i) => {
            const link = getNotificationLink(item);
            return (
              <motion.div key={item.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2, ease: EASE_OUT, delay: i * 0.04 }}
                style={{
                  display: 'flex',
                  padding: '16px 20px',
                  borderBottom: i < items.length - 1 ? '1px solid var(--outline-variant)' : 'none',
                  background: item.read ? 'transparent' : 'var(--primary-softer)',
                  alignItems: 'center',
                  gap: 16
                }}>
                <div style={{ fontSize: '1.5rem', minWidth: 40, textAlign: 'center' }}>
                  {getIcon(item.type)}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <strong style={{ display: 'block', color: 'var(--on-surface)' }}>{item.title}</strong>
                    {!item.read && <span className="chip primary sm">Mới</span>}
                  </div>
                  <p style={{ margin: 0, color: 'var(--on-muted)', fontSize: '0.9rem', lineHeight: 1.4 }}>
                    {item.message}
                  </p>
                  <span style={{ fontSize: '0.75rem', color: 'var(--outline)', marginTop: 8, display: 'block' }}>
                    {new Date(item.createdAt).toLocaleString('vi-VN')}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8, flexDirection: 'column', alignItems: 'flex-end' }}>
                  {!item.read && (
                    <button className="outline sm"
                      onClick={() => employerService.markNotificationRead(item.id).then(load)}>
                      Đánh dấu đọc
                    </button>
                  )}
                  {link && (
                    <Link to={link} className="button-link sm">
                      Xem chi tiết
                    </Link>
                  )}
                </div>
              </motion.div>
            );
          })}
        </div>
      )}
    </motion.div>
  );
}
