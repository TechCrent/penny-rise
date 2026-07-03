import { format } from 'date-fns';
import type { AdminUserListItem } from '../../api/usersAdmin';

interface UsersTableProps {
  items: AdminUserListItem[];
  onSelect: (id: string) => void;
}

export function UsersTable({ items, onSelect }: UsersTableProps) {
  if (items.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
        <p className="text-slate-400 text-lg">No users match this search.</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Name
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Email
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              KYC Status
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Account Status
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Tier
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Joined
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((user, index) => (
            <tr
              key={user.id}
              className={`border-b border-slate-100 hover:bg-slate-50 transition-colors cursor-pointer ${
                index === items.length - 1 ? 'border-0' : ''
              }`}
              onClick={() => onSelect(user.id)}
              data-testid={`user-row-${user.id}`}
            >
              <td className="px-6 py-4 font-medium text-slate-900">{user.displayName}</td>
              <td className="px-6 py-4 text-slate-600">{user.email}</td>
              <td className="px-6 py-4">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
                  {user.kycStatus}
                </span>
              </td>
              <td className="px-6 py-4">
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                    user.accountStatus === 'SUSPENDED'
                      ? 'bg-red-100 text-red-700'
                      : 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {user.accountStatus}
                </span>
              </td>
              <td className="px-6 py-4 text-slate-600">{user.subscriptionTier}</td>
              <td className="px-6 py-4 text-slate-600">
                {format(new Date(user.createdAt), 'dd MMM yyyy')}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
