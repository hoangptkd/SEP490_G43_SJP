import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-gray-800 text-white py-8">
      <div className="container mx-auto px-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div>
            <h3 className="text-lg font-bold mb-4">Smart Recruitment Portal</h3>
            <p className="text-gray-400">
              Nền tảng kết nối việc làm và hỗ trợ phỏng vấn thông minh
            </p>
          </div>

          <div>
            <h4 className="font-semibold mb-4">Quick Links</h4>
            <ul className="space-y-2 text-gray-400">
              <li><a href="/jobs" className="hover:text-white">Find Jobs</a></li>
              <li><a href="/candidates" className="hover:text-white">Find Candidates</a></li>
              <li><a href="/about" className="hover:text-white">About Us</a></li>
            </ul>
          </div>

          <div>
            <h4 className="font-semibold mb-4">Contact</h4>
            <ul className="space-y-2 text-gray-400">
              <li>Email: info@sjp.vn</li>
              <li>Phone: +84 123 456 789</li>
            </ul>
          </div>
        </div>

        <div className="border-t border-gray-700 mt-8 pt-8 text-center text-gray-400">
          <p>&copy; 2026 Smart Recruitment Portal. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
};