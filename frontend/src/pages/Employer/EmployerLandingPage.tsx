import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { FiCheckCircle, FiTrendingUp, FiUsers, FiSearch } from '../../components/Icons';

export default function EmployerLandingPage() {
  return (
    <div className="min-h-screen bg-slate-50 font-sans">
      {/* Header */}
      <header className="bg-white border-b border-gray-100 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-8">
            <Link to="/" className="text-xl font-bold text-emerald-600 flex items-center gap-2">
              <span className="bg-emerald-600 text-white p-1.5 rounded-lg">SRP</span>
              Smart Recruitment
            </Link>
          </div>
          
          <div className="flex items-center gap-3">
            <Link 
              to="/login?role=EMPLOYER" 
              className="px-4 py-2 text-sm font-semibold text-slate-700 hover:text-emerald-600 transition-colors"
            >
              Đăng nhập
            </Link>
            <Link 
              to="/register?role=EMPLOYER" 
              className="px-4 py-2 text-sm font-semibold bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors shadow-sm"
            >
              Đăng ký Nhà Tuyển Dụng
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="relative pt-20 pb-32 overflow-hidden bg-white">
        <div className="absolute top-0 right-0 w-1/2 h-full bg-emerald-50 rounded-bl-[100px] -z-10 opacity-50"></div>
        <div className="absolute -top-24 -right-24 w-96 h-96 bg-emerald-400 opacity-20 rounded-full blur-3xl -z-10"></div>
        <div className="absolute bottom-10 left-10 w-72 h-72 bg-blue-400 opacity-10 rounded-full blur-3xl -z-10"></div>

        <div className="max-w-7xl mx-auto px-4 relative z-10 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1 className="text-4xl md:text-6xl font-bold text-slate-800 mb-6 tracking-tight leading-tight">
              Đăng tin tuyển dụng miễn phí <br />
              <span className="text-emerald-600">Tiếp cận hàng triệu ứng viên</span>
            </h1>
            <p className="text-lg md:text-xl text-slate-500 mb-10 max-w-2xl mx-auto">
              Nền tảng tuyển dụng thông minh ứng dụng AI giúp bạn tìm kiếm, đánh giá và kết nối với những ứng viên phù hợp nhất một cách nhanh chóng.
            </p>
            
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link 
                to="/register?role=EMPLOYER" 
                className="px-8 py-4 bg-emerald-600 text-white text-lg font-bold rounded-full hover:bg-emerald-700 transition-all hover:shadow-lg hover:-translate-y-0.5 w-full sm:w-auto"
              >
                Trải nghiệm ngay
              </Link>
              <button className="px-8 py-4 bg-slate-800 text-white text-lg font-bold rounded-full hover:bg-slate-900 transition-all hover:shadow-lg hover:-translate-y-0.5 w-full sm:w-auto">
                Tư vấn tuyển dụng
              </button>
            </div>
          </motion.div>

          {/* Stats or Features mini-cards */}
          <motion.div 
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="mt-20 grid grid-cols-1 md:grid-cols-3 gap-6 max-w-4xl mx-auto"
          >
            <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 flex flex-col items-center text-center">
              <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mb-4">
                <FiUsers className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-slate-800 mb-2">Hồ sơ chất lượng</h3>
              <p className="text-slate-500 text-sm">Hệ thống phân tích CV AI giúp bạn tìm ra ứng viên phù hợp nhất.</p>
            </div>
            
            <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 flex flex-col items-center text-center">
              <div className="w-12 h-12 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center mb-4">
                <FiTrendingUp className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-slate-800 mb-2">Tuyển dụng nhanh chóng</h3>
              <p className="text-slate-500 text-sm">Giảm thiểu thời gian lọc hồ sơ nhờ hệ thống tự động hóa thông minh.</p>
            </div>

            <div className="bg-white p-6 rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 flex flex-col items-center text-center">
              <div className="w-12 h-12 bg-purple-100 text-purple-600 rounded-full flex items-center justify-center mb-4">
                <FiSearch className="w-6 h-6" />
              </div>
              <h3 className="text-xl font-bold text-slate-800 mb-2">Tiếp cận thụ động</h3>
              <p className="text-slate-500 text-sm">Gợi ý việc làm của bạn tới hàng triệu ứng viên tiềm năng mỗi ngày.</p>
            </div>
          </motion.div>
        </div>
      </section>
    </div>
  );
}
