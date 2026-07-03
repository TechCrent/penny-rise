import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAdminAuth } from '../../auth/AdminAuthContext';

interface AdminShellProps {
  children: ReactNode;
  title?: string;
}

export function AdminShell({ children, title }: AdminShellProps) {
  const { logout } = useAdminAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-8">
            <span className="font-bold text-lg text-slate-900">Stash Admin</span>
            <nav className="flex gap-6">
              <Link
                to="/kyc-queue"
                className="text-sm font-medium text-slate-600 hover:text-slate-900"
              >
                KYC Queue
              </Link>
              <Link to="/users" className="text-sm font-medium text-slate-600 hover:text-slate-900">
                Users
              </Link>
            </nav>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="text-sm text-slate-500 hover:text-slate-900"
          >
            Sign out
          </button>
        </div>
      </header>
      <main className="max-w-7xl mx-auto px-6 py-8">
        {title ? <h1 className="text-2xl font-bold text-slate-900 mb-6">{title}</h1> : null}
        {children}
      </main>
    </div>
  );
}
