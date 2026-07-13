import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  ShieldCheck,
  Users,
  Scale,
  ScrollText,
  CircleDollarSign,
  UserCog,
  LogOut,
  Bell,
  Search,
} from 'lucide-react';
import { useAdminAuth } from '../../auth/AdminAuthContext';
import { cn } from '@/lib/utils';

interface AdminShellProps {
  children: ReactNode;
  title?: string;
}

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/kyc-queue', label: 'KYC Queue', icon: ShieldCheck },
  { to: '/users', label: 'Users', icon: Users },
  { to: '/disputes', label: 'Disputes', icon: Scale },
  { to: '/audit-log', label: 'Audit Log', icon: ScrollText },
  { to: '/susu-groups', label: 'Susu Groups', icon: CircleDollarSign },
];

export function AdminShell({ children, title }: AdminShellProps) {
  const { logout, accountType } = useAdminAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const navItems =
    accountType === 'SUPER'
      ? [...NAV_ITEMS, { to: '/staff', label: 'Staff', icon: UserCog }]
      : NAV_ITEMS;

  return (
    <div className="flex min-h-screen bg-background text-foreground">
      <aside className="flex w-60 shrink-0 flex-col border-r border-sidebar-border bg-sidebar">
        <div className="flex items-center gap-2 px-5 py-5">
          <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
            P
          </span>
          <span className="text-base font-semibold tracking-tight text-foreground">
            PennyRise
          </span>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-sidebar-accent text-sidebar-primary'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    className={cn('size-4', isActive ? 'text-sidebar-primary' : 'text-sidebar-foreground')}
                  />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-4 border-b border-border bg-navbar px-6 py-3">
          <div className="flex items-center gap-2 rounded-lg border border-input bg-card-secondary px-3 py-1.5 text-sm text-muted-foreground">
            <Search className="size-4" />
            <span>Search…</span>
          </div>
          <div className="flex items-center gap-4">
            <button
              type="button"
              className="relative flex size-8 items-center justify-center rounded-lg text-muted-foreground hover:bg-card-secondary hover:text-foreground"
              aria-label="Notifications"
            >
              <Bell className="size-4" />
              <span className="absolute right-1.5 top-1.5 size-1.5 rounded-full bg-primary" />
            </button>
            <div className="flex items-center gap-2 rounded-lg bg-card-secondary py-1.5 pl-1.5 pr-3">
              <span className="flex size-6 items-center justify-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">
                {accountType ? accountType.charAt(0) : '?'}
              </span>
              <span className="text-xs font-medium text-foreground">{accountType ?? 'Staff'}</span>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
            >
              <LogOut className="size-4" />
              Sign out
            </button>
          </div>
        </header>
        <main className="flex-1 px-8 py-8">
          {title ? <h1 className="mb-6 text-2xl font-semibold text-foreground">{title}</h1> : null}
          {children}
        </main>
      </div>
    </div>
  );
}
