import { FormEvent, useEffect, useState } from 'react';
import { employerService } from '../../services/employerService';
import type { CompanyLocation } from '../../types/job';
import { FiPlus, FiEdit2, FiTrash2, FiMapPin, FiCheckCircle, FiAlertCircle } from '../../components/Icons';

function CompanyLocationsPage() {
  const [locations, setLocations] = useState<CompanyLocation[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<Partial<CompanyLocation>>({
    branchName: '',
    address: '',
    city: '',
    district: '',
    country: 'Vietnam',
    headquarter: false,
  });
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadLocations();
  }, []);

  async function loadLocations() {
    setLoading(true);
    try {
      const data = await employerService.getLocations();
      setLocations(data);
    } catch (err) {
      setError('Không thể tải danh sách địa điểm làm việc.');
    } finally {
      setLoading(false);
    }
  }

  function handleOpenAdd() {
    setEditingId(null);
    setFormData({
      branchName: '',
      address: '',
      city: '',
      district: '',
      country: 'Vietnam',
      headquarter: locations.length === 0, // Nếu chưa có địa điểm nào thì tự đặt làm trụ sở
    });
    setShowForm(true);
    setMessage('');
    setError('');
  }

  function handleOpenEdit(loc: CompanyLocation) {
    setEditingId(loc.id);
    setFormData({
      branchName: loc.branchName,
      address: loc.address || '',
      city: loc.city || '',
      district: loc.district || '',
      country: loc.country || 'Vietnam',
      headquarter: loc.headquarter,
    });
    setShowForm(true);
    setMessage('');
    setError('');
    setFieldErrors({});
  }

  function validateForm() {
    const errors: Record<string, string> = {};
    if (!formData.branchName?.trim()) {
      errors.branchName = 'Vui lòng nhập tên chi nhánh / văn phòng.';
    } else if (formData.branchName.length > 100) {
      errors.branchName = 'Tên chi nhánh không được vượt quá 100 ký tự.';
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validateForm()) {
      setError('Vui lòng kiểm tra lại thông tin không hợp lệ.');
      return;
    }
    setSaving(true);
    setMessage('');
    setError('');
    try {
      if (editingId) {
        await employerService.updateLocation(editingId, formData);
        setMessage('Cập nhật địa điểm thành công.');
      } else {
        await employerService.createLocation(formData);
        setMessage('Thêm địa điểm mới thành công.');
      }
      setShowForm(false);
      await loadLocations();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Có lỗi xảy ra khi lưu địa điểm làm việc.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string, isHq: boolean) {
    if (isHq) {
      alert('Không thể xóa Trụ sở chính. Vui lòng đặt chi nhánh khác làm Trụ sở chính trước.');
      return;
    }
    if (!confirm('Bạn có chắc chắn muốn xóa địa điểm làm việc này?')) return;
    try {
      await employerService.deleteLocation(id);
      setMessage('Xóa địa điểm thành công.');
      await loadLocations();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi xóa địa điểm.');
    }
  }

  if (loading) return (
    <div className="flex justify-center items-center h-64">
      <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-emerald-600"></div>
    </div>
  );

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-2xl p-8 text-white shadow-lg relative overflow-hidden flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="absolute top-0 right-0 w-64 h-64 bg-white opacity-5 rounded-full -translate-y-1/2 translate-x-1/3 blur-2xl pointer-events-none"></div>
        <div className="absolute bottom-0 left-0 w-64 h-64 bg-emerald-500 opacity-10 rounded-full translate-y-1/3 -translate-x-1/3 blur-2xl pointer-events-none"></div>
        
        <div className="relative z-10 text-center md:text-left">
          <h1 className="text-2xl font-bold mb-2">Quản lý chi nhánh & Văn phòng</h1>
          <p className="text-slate-300 text-sm">
            Danh sách các địa điểm hoạt động và văn phòng làm việc của doanh nghiệp.
          </p>
        </div>
        <button
          type="button"
          onClick={handleOpenAdd}
          className="relative z-10 inline-flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2.5 rounded-xl font-semibold shadow-sm transition-all"
        >
          <FiPlus className="w-5 h-5" /> Thêm địa điểm mới
        </button>
      </div>

      {message && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 px-4 py-3 rounded-xl flex items-center gap-3">
          <FiCheckCircle className="w-5 h-5 shrink-0" /> {message}
        </div>
      )}
      
      {error && !showForm && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl flex items-center gap-3">
          <FiAlertCircle className="w-5 h-5 shrink-0" /> {error}
        </div>
      )}

      {showForm && (
        <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 bg-gray-50/50">
            <h3 className="text-lg font-bold text-slate-800">
              {editingId ? 'Chỉnh sửa thông tin chi nhánh' : 'Thêm chi nhánh văn phòng mới'}
            </h3>
          </div>
          
          <form onSubmit={handleSubmit} className="p-6 space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2 md:col-span-2">
                <label className="block text-sm font-medium text-gray-700">Tên chi nhánh / Văn phòng <span className="text-red-500">*</span></label>
                <input
                  required
                  value={formData.branchName || ''}
                  onChange={(e) => setFormData({ ...formData, branchName: e.target.value })}
                  placeholder="Ví dụ: Trụ sở Hà Nội, Chi nhánh HCM..."
                  className={`w-full px-4 py-2.5 rounded-xl border focus:ring-2 outline-none transition-all ${fieldErrors.branchName ? 'border-red-500 focus:border-red-500 focus:ring-red-200' : 'border-gray-200 focus:border-emerald-500 focus:ring-emerald-200'}`}
                />
                {fieldErrors.branchName && <p className="text-red-500 text-xs mt-1 font-medium">{fieldErrors.branchName}</p>}
              </div>

              <div className="space-y-2 md:col-span-2">
                <label className="block text-sm font-medium text-gray-700">Địa chỉ chi tiết</label>
                <input
                  value={formData.address || ''}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  placeholder="Số nhà, đường/phố, phường/xã..."
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 outline-none transition-all"
                />
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Quận / Huyện</label>
                <input
                  value={formData.district || ''}
                  onChange={(e) => setFormData({ ...formData, district: e.target.value })}
                  placeholder="Ví dụ: Cầu Giấy, Quận 1..."
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 outline-none transition-all"
                />
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Tỉnh / Thành phố</label>
                <input
                  value={formData.city || ''}
                  onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                  placeholder="Ví dụ: Hà Nội, TP. Hồ Chí Minh..."
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 outline-none transition-all"
                />
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Quốc gia</label>
                <input
                  value={formData.country || 'Vietnam'}
                  onChange={(e) => setFormData({ ...formData, country: e.target.value })}
                  placeholder="Vietnam"
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200 outline-none transition-all"
                />
              </div>
            </div>

            <label className="flex items-center gap-3 p-4 bg-slate-50 border border-slate-200 rounded-xl cursor-pointer hover:bg-slate-100 transition-colors">
              <input
                type="checkbox"
                checked={formData.headquarter || false}
                onChange={(e) => setFormData({ ...formData, headquarter: e.target.checked })}
                className="w-5 h-5 rounded text-emerald-600 focus:ring-emerald-500 border-gray-300"
              />
              <span className="font-semibold text-slate-800">Đặt làm Trụ sở chính (Head Office)</span>
            </label>

            {error && <p className="text-red-500 text-sm">{error}</p>}

            <div className="flex flex-col sm:flex-row gap-3 pt-6 border-t border-gray-100">
              <button
                type="submit"
                disabled={saving}
                className={`flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold text-white transition-all shadow-sm ${
                  saving ? 'bg-emerald-400 cursor-wait' : 'bg-emerald-600 hover:bg-emerald-700 hover:shadow-md'
                }`}
              >
                {saving ? 'Đang xử lý...' : (editingId ? 'Lưu thay đổi' : 'Thêm mới')}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="flex-1 sm:flex-none px-6 py-2.5 rounded-xl text-sm font-semibold bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 transition-all shadow-sm"
              >
                Hủy
              </button>
            </div>
          </form>
        </div>
      )}

      <div>
        {locations.length === 0 ? (
          <div className="text-center py-16 px-6 bg-slate-50 rounded-2xl border-2 border-dashed border-slate-200">
            <FiMapPin className="w-12 h-12 text-slate-300 mx-auto mb-4" />
            <h3 className="text-lg font-semibold text-slate-800 mb-2">Chưa có địa điểm làm việc</h3>
            <p className="text-slate-500 max-w-sm mx-auto">
              Chưa có địa điểm làm việc nào được cấu hình trên hệ thống. Hãy thêm mới để ứng viên biết nơi làm việc.
            </p>
          </div>
        ) : (
          <div className="grid gap-4">
            {locations.map((loc) => (
              <div key={loc.id} className={`flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl border transition-all ${
                loc.headquarter ? 'bg-emerald-50/30 border-emerald-200 shadow-sm' : 'bg-white border-gray-200 hover:border-emerald-300 shadow-sm hover:shadow'
              }`}>
                <div>
                  <div className="flex items-center gap-3 mb-2 flex-wrap">
                    <strong className="text-lg text-slate-800 font-bold">{loc.branchName}</strong>
                    {loc.headquarter && (
                      <span className="bg-emerald-600 text-white text-[10px] uppercase tracking-wide font-bold px-2.5 py-0.5 rounded-full">
                        Trụ sở chính
                      </span>
                    )}
                  </div>
                  <p className="text-slate-500 text-sm flex items-start gap-1.5">
                    <FiMapPin className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>
                      {loc.address ? `${loc.address}, ` : ''}
                      {loc.district ? `${loc.district}, ` : ''}
                      {loc.city || ''}
                      {loc.country ? ` (${loc.country})` : ''}
                    </span>
                  </p>
                </div>
                
                <div className="flex gap-2 items-center">
                  <button
                    type="button"
                    onClick={() => handleOpenEdit(loc)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold bg-white border border-gray-200 text-slate-700 hover:bg-slate-50 hover:text-emerald-600 transition-colors"
                  >
                    <FiEdit2 className="w-4 h-4" /> Chỉnh sửa
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(loc.id, loc.headquarter)}
                    disabled={loc.headquarter}
                    title={loc.headquarter ? 'Không thể xóa trụ sở chính' : 'Xóa chi nhánh'}
                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold border transition-colors ${
                      loc.headquarter 
                        ? 'bg-gray-50 border-gray-200 text-gray-400 cursor-not-allowed' 
                        : 'bg-white border-red-200 text-red-600 hover:bg-red-50'
                    }`}
                  >
                    <FiTrash2 className="w-4 h-4" /> Xóa
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default CompanyLocationsPage;
