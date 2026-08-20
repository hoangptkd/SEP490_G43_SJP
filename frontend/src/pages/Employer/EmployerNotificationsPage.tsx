import { useEffect, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { employerService } from '../../services/employerService';
import type { NotificationItem } from '../../types/candidateDomain';
import { NotificationInbox } from '../../components/NotificationInbox';

const fadeUp = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
};
const EASE_OUT = [0.23, 1, 0.32, 1] as const;

export default function EmployerNotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  const load = useCallback(async (currentPage: number) => {
    setLoading(true);
    try {
      const data = await employerService.getNotifications(currentPage, 10);
      const itemsList = Array.isArray(data) ? data : (data?.items || []);
      const total = Array.isArray(data) ? 1 : (data?.totalPages || 1);
      setItems(itemsList);
      setTotalPages(total);
    } catch {
      setItems([]);
      setTotalPages(1);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(page);
  }, [load, page]);

  const unreadCount = items.filter((item) => !item.read).length;

  const getNotificationLink = (item: NotificationItem) => {
    if (item.relatedEntityType === 'JOB' && item.relatedEntityId) {
      return `/employer/jobs`;
    }
    if (item.relatedEntityType === 'APPLICATION' && item.relatedEntityId) {
      return `/employer/applications?appId=${item.relatedEntityId}`;
    }
    return null;
  };

  return (
    <motion.div variants={fadeUp} initial="initial" animate="animate"
      transition={{ duration: 0.25, ease: EASE_OUT }}>
      <div className="page-header">
        <h1>Thông báo</h1>
        <p>
          Cập nhật từ ứng viên và hệ thống
          {unreadCount > 0 ? ` · ${unreadCount} chưa đọc trên trang này` : ''}
        </p>
      </div>

      <NotificationInbox
        items={items}
        loading={loading}
        emptyTitle="Không có thông báo mới"
        emptyHint="Bạn sẽ nhận thông báo khi có hồ sơ ứng tuyển mới hoặc thông báo từ Admin."
        getLink={getNotificationLink}
        onMarkRead={(id) => { void employerService.markNotificationRead(id).then(() => load(page)); }}
        onMarkAllRead={() => { void employerService.markAllNotificationsRead().then(() => load(page)); }}
      />

      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 8, marginTop: 24 }}>
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page === 1}
            style={{
              padding: '6px 14px',
              borderRadius: 8,
              border: '1px solid #cbd5e1',
              background: page === 1 ? '#f8fafc' : '#fff',
              color: page === 1 ? '#94a3b8' : '#334155',
              cursor: page === 1 ? 'not-allowed' : 'pointer',
              fontSize: '0.85rem',
              fontWeight: 600,
            }}
          >
            ‹ Trước
          </button>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>
            Trang {page} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            style={{
              padding: '6px 14px',
              borderRadius: 8,
              border: '1px solid #cbd5e1',
              background: page === totalPages ? '#f8fafc' : '#fff',
              color: page === totalPages ? '#94a3b8' : '#334155',
              cursor: page === totalPages ? 'not-allowed' : 'pointer',
              fontSize: '0.85rem',
              fontWeight: 600,
            }}
          >
            Sau ›
          </button>
        </div>
      )}
    </motion.div>
  );
}
