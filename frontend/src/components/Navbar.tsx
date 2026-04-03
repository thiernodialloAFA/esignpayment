import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Navbar.css';

const Navbar: React.FC = () => {
  const { user, isAuthenticated, login, logout } = useAuth();
  const location = useLocation();

  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <Link to="/" className="brand-link">
          <span className="brand-icon">✍️</span>
          <span className="brand-name">ESignPay</span>
        </Link>
      </div>

      {isAuthenticated && (
        <div className="navbar-menu">
          <Link
            to="/dashboard"
            className={`nav-link ${isActive('/dashboard') ? 'active' : ''}`}
          >
            Dashboard
          </Link>
          <Link
            to="/documents"
            className={`nav-link ${isActive('/documents') ? 'active' : ''}`}
          >
            Documents
          </Link>
          <Link
            to="/accounts"
            className={`nav-link ${location.pathname.startsWith('/accounts') ? 'active' : ''}`}
          >
            Comptes
          </Link>
          <Link
            to="/payments"
            className={`nav-link ${isActive('/payments') ? 'active' : ''}`}
          >
            Payments
          </Link>
        </div>
      )}

      <div className="navbar-actions">
        {isAuthenticated ? (
          <div className="user-menu">
            <span className="user-name">
              {user?.firstName} {user?.lastName}
            </span>
            <button onClick={logout} className="btn btn-outline btn-sm">
              Logout
            </button>
          </div>
        ) : (
          <div className="auth-links">
            <button onClick={login} className="btn btn-outline btn-sm">
              Login
            </button>
          </div>
        )}
      </div>
    </nav>
  );
};

export default Navbar;
