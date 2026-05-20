import React from 'react';
import { Link } from 'react-router-dom';

export const Header: React.FC = () => {
  return (
    <header className="bg-white shadow">
      <nav className="container mx-auto px-4 py-4 flex justify-between items-center">
        <Link to="/" className="text-xl font-bold text-blue-600">
          Smart Recruitment Portal
        </Link>

        <div className="flex items-center space-x-6">
          <Link to="/jobs" className="text-gray-600 hover:text-blue-600">
            Jobs
          </Link>
          <Link to="/interviews" className="text-gray-600 hover:text-blue-600">
            Interviews
          </Link>
          <Link to="/assessments" className="text-gray-600 hover:text-blue-600">
            Assessments
          </Link>
          <Link to="/login" className="text-gray-600 hover:text-blue-600">
            Login
          </Link>
          <Link
            to="/register"
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
          >
            Sign Up
          </Link>
        </div>
      </nav>
    </header>
  );
};