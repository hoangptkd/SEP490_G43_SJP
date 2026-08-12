import React, { useState, useEffect } from 'react';
import { getStoredUser } from '../../utils/authStorage';
import { employerService } from '../../services/employerService';
import { authService } from '../../services/authService';
import { customAlert, customConfirm } from '../../utils/dialog';

const EmployerSettingsPage: React.FC = () => {
  const user = getStoredUser();
  
  const [activeTab, setActiveTab] = useState<'personal' | 'security'>('personal');
  
  // Personal Info Form State
  const [avatarPreview, setAvatarPreview] = useState<string>('');
  const [avatarLoading, setAvatarLoading] = useState(false);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [position, setPosition] = useState('');

  useEffect(() => {
    employerService.getPersonalProfile().then(data => {
      setFullName(data.fullName || '');
      setPhone(data.phone || '');
      setPosition(data.position || '');
    }).catch(console.error);
    authService.getAccount()
      .then((account) => setAvatarPreview(account.avatarUrl || ''))
      .catch(console.error);
  }, []);
  const [personalLoading, setPersonalLoading] = useState(false);
  const [personalMsg, setPersonalMsg] = useState({ type: '', text: '' });

  // Security Form State
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [securityLoading, setSecurityLoading] = useState(false);
  const [securityMsg, setSecurityMsg] = useState({ type: '', text: '' });

  // Deactivate Account State
  const [deactivateLoading, setDeactivateLoading] = useState(false);
  const [deactivatePassword, setDeactivatePassword] = useState('');


  const handleUpdatePersonal = async (e: React.FormEvent) => {
    e.preventDefault();
    setPersonalLoading(true);
    setPersonalMsg({ type: '', text: '' });
    try {
      await employerService.updatePersonalProfile({ fullName, phone, position });
      setPersonalMsg({ type: 'success', text: 'Cập nhật thông tin cá nhân thành công' });
      // Note: In a real app, you might want to fetch user context again to update sidebar
    } catch (err: any) {
      setPersonalMsg({ type: 'error', text: err.response?.data?.message || 'Có lỗi xảy ra khi cập nhật thông tin' });
    } finally {
      setPersonalLoading(false);
    }
  };

  const handleUpdatePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      setSecurityMsg({ type: 'error', text: 'Mật khẩu xác nhận không khớp' });
      return;
    }
    setSecurityLoading(true);
    setSecurityMsg({ type: '', text: '' });
    try {
      await authService.changePassword({ currentPassword, newPassword });
      setSecurityMsg({ type: 'success', text: 'Đổi mật khẩu thành công' });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setSecurityMsg({ type: 'error', text: err.response?.data?.message || 'Có lỗi xảy ra khi đổi mật khẩu' });
    } finally {
      setSecurityLoading(false);
    }
  };

  const handleDeactivate = async () => {
    if (!(await customConfirm("Bạn có chắc chắn muốn yêu cầu vô hiệu hóa tài khoản không? Hành động này sẽ được Admin xem xét."))) return;
    setDeactivateLoading(true);
    setSecurityMsg({ type: '', text: '' });
    try {
      await authService.deactivateAccount(deactivatePassword);
      setSecurityMsg({ type: 'success', text: 'Tài khoản đã được vô hiệu hóa.' });
      setDeactivatePassword('');
    } catch (err: any) {
      setSecurityMsg({ type: 'error', text: err.response?.data?.message || 'Có lỗi xảy ra khi gửi yêu cầu' });
    } finally {
      setDeactivateLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>Cài đặt tài khoản</h1>
      </div>

      <div style={{ background: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        {/* Tabs */}
        <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', background: '#f8fafc' }}>
          <button
            onClick={() => setActiveTab('personal')}
            style={{
              padding: '16px 24px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'personal' ? '2px solid #2563eb' : '2px solid transparent',
              color: activeTab === 'personal' ? '#2563eb' : '#64748b',
              fontWeight: activeTab === 'personal' ? 600 : 500,
              fontSize: '1rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            Thông tin cá nhân
          </button>
          <button
            onClick={() => setActiveTab('security')}
            style={{
              padding: '16px 24px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'security' ? '2px solid #2563eb' : '2px solid transparent',
              color: activeTab === 'security' ? '#2563eb' : '#64748b',
              fontWeight: activeTab === 'security' ? 600 : 500,
              fontSize: '1rem',
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            Đăng nhập & Bảo mật
          </button>
        </div>

        {/* Content */}
        <div style={{ padding: '32px' }}>
          {activeTab === 'personal' && (
            <div style={{ maxWidth: '600px' }}>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: '#1e293b', marginBottom: '20px' }}>Thông tin liên hệ cá nhân</h2>
              <p style={{ color: '#64748b', marginBottom: '24px', fontSize: '0.95rem' }}>
                Thông tin này dùng để hệ thống hoặc Admin liên hệ với bạn. Sẽ không hiển thị công khai trên tin tuyển dụng.
              </p>
              
              {personalMsg.text && (
                <div style={{ 
                  padding: '12px 16px', 
                  borderRadius: '6px', 
                  marginBottom: '20px', 
                  background: personalMsg.type === 'success' ? '#dcfce7' : '#fee2e2',
                  color: personalMsg.type === 'success' ? '#166534' : '#991b1b',
                  fontSize: '0.9rem'
                }}>
                  {personalMsg.text}
                </div>
              )}

              {/* Avatar Section */}
              <div style={{ marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '20px' }}>
                <div style={{
                  width: '80px', height: '80px', borderRadius: '50%', background: '#e2e8f0', 
                  backgroundImage: avatarPreview ? `url(${avatarPreview})` : 'none',
                  backgroundSize: 'cover', backgroundPosition: 'center',
                  border: '1px solid #cbd5e1',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: '#94a3b8', fontSize: '2rem'
                }}>
                  {!avatarPreview && <span>👤</span>}
                </div>
                <div>
                  <input 
                    type="file" 
                    accept="image/*" 
                    id="avatar-upload"
                    style={{ display: 'none' }}
                    onChange={async (e) => {
                      if (e.target.files && e.target.files[0]) {
                        const file = e.target.files[0];
                        setAvatarPreview(URL.createObjectURL(file));
                        setAvatarLoading(true);
                        setPersonalMsg({ type: '', text: '' });
                        try {
                          const updatedAccount = await authService.updateAvatar(file);
                          if (user) {
                             const updatedUser = { ...user, avatarUrl: updatedAccount.avatarUrl };
                             localStorage.setItem('user', JSON.stringify(updatedUser));
                          }
                          setPersonalMsg({ type: 'success', text: 'Cập nhật ảnh đại diện thành công' });
                        } catch (err: any) {
                          setPersonalMsg({ type: 'error', text: err.response?.data?.message || 'Lỗi khi upload ảnh' });
                        } finally {
                          setAvatarLoading(false);
                        }
                      }
                    }}
                  />
                  <label htmlFor="avatar-upload" style={{
                    display: 'inline-block', padding: '8px 16px', background: '#f1f5f9', 
                    color: '#334155', borderRadius: '6px', cursor: avatarLoading ? 'not-allowed' : 'pointer', fontSize: '0.9rem',
                    fontWeight: 500, border: '1px solid #cbd5e1',
                    pointerEvents: avatarLoading ? 'none' : 'auto'
                  }}>
                    {avatarLoading ? 'Đang tải lên...' : 'Thay đổi ảnh đại diện'}
                  </label>
                </div>
              </div>

              <form onSubmit={handleUpdatePersonal} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Họ và tên</label>
                  <input
                    type="text"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    required
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Số điện thoại</label>
                  <input
                    type="text"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    required
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Chức vụ tại công ty</label>
                  <input
                    type="text"
                    value={position}
                    onChange={(e) => setPosition(e.target.value)}
                    required
                    placeholder="VD: Trưởng phòng Nhân sự, HR Executive..."
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <button
                    type="submit"
                    disabled={personalLoading}
                    style={{
                      background: personalLoading ? '#94a3b8' : '#2563eb',
                      color: '#fff',
                      padding: '10px 24px',
                      borderRadius: '6px',
                      border: 'none',
                      fontWeight: 600,
                      cursor: personalLoading ? 'not-allowed' : 'pointer',
                      marginTop: '8px'
                    }}
                  >
                    {personalLoading ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {activeTab === 'security' && (
            <div style={{ maxWidth: '600px' }}>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: '#1e293b', marginBottom: '20px' }}>Đổi mật khẩu</h2>
              
              {securityMsg.text && (
                <div style={{ 
                  padding: '12px 16px', 
                  borderRadius: '6px', 
                  marginBottom: '20px', 
                  background: securityMsg.type === 'success' ? '#dcfce7' : '#fee2e2',
                  color: securityMsg.type === 'success' ? '#166534' : '#991b1b',
                  fontSize: '0.9rem'
                }}>
                  {securityMsg.text}
                </div>
              )}

              <form onSubmit={handleUpdatePassword} style={{ display: 'flex', flexDirection: 'column', gap: '20px', paddingBottom: '32px', borderBottom: '1px solid #e2e8f0', marginBottom: '32px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Mật khẩu hiện tại</label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Mật khẩu mới</label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Xác nhận mật khẩu mới</label>
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box' }}
                  />
                </div>
                <div>
                  <button
                    type="submit"
                    disabled={securityLoading}
                    style={{
                      background: securityLoading ? '#94a3b8' : '#2563eb',
                      color: '#fff',
                      padding: '10px 24px',
                      borderRadius: '6px',
                      border: 'none',
                      fontWeight: 600,
                      cursor: securityLoading ? 'not-allowed' : 'pointer',
                      marginTop: '8px'
                    }}
                  >
                    {securityLoading ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
                  </button>
                </div>
              </form>

              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: '#b91c1c', marginBottom: '16px' }}>Vô hiệu hóa tài khoản</h2>
                <p style={{ color: '#64748b', marginBottom: '20px', fontSize: '0.95rem', lineHeight: 1.5 }}>
                  Nếu bạn không còn nhu cầu sử dụng dịch vụ hoặc muốn đóng tài khoản công ty, bạn có thể gửi yêu cầu vô hiệu hóa. 
                  Lưu ý: Quá trình này sẽ cần Admin phê duyệt. Khi tài khoản bị vô hiệu hóa, mọi tin tuyển dụng của công ty sẽ bị ẩn.
                </p>
                <div style={{ marginBottom: '16px' }}>
                  <label htmlFor="employer-deactivate-password" style={{ display: 'block', fontSize: '0.9rem', fontWeight: 500, color: '#334155', marginBottom: '8px' }}>Mật khẩu hiện tại</label>
                  <input
                    id="employer-deactivate-password"
                    type="password"
                    value={deactivatePassword}
                    onChange={(e) => setDeactivatePassword(e.target.value)}
                    autoComplete="current-password"
                    required
                    placeholder="Nhập mật khẩu để xác nhận"
                    style={{ width: '100%', padding: '10px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '0.95rem', boxSizing: 'border-box', fontFamily: 'inherit' }}
                  />
                </div>
                <button
                  type="button"
                  onClick={handleDeactivate}
                  disabled={deactivateLoading || !deactivatePassword}
                  style={{
                    background: '#fff',
                    color: '#dc2626',
                    border: '1px solid #dc2626',
                    padding: '10px 20px',
                    borderRadius: '6px',
                    fontWeight: 600,
                    cursor: deactivateLoading ? 'not-allowed' : 'pointer',
                    transition: 'all 0.2s'
                  }}
                  onMouseOver={(e) => { e.currentTarget.style.background = '#fef2f2' }}
                  onMouseOut={(e) => { e.currentTarget.style.background = '#fff' }}
                >
                  {deactivateLoading ? 'Đang gửi...' : 'Yêu cầu vô hiệu hóa'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EmployerSettingsPage;
