import { useState, type FormEvent } from 'react';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { extractApiError } from '../../api/client';
import { useStaff } from './useStaff';
import type { AdminAccountType } from '../../api/staffAdmin';

const ACCOUNT_TYPES: AdminAccountType[] = ['SUPER', 'VICE_SUPER', 'TAB'];

export default function StaffPage() {
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [accountType, setAccountType] = useState<AdminAccountType>('TAB');
  const [roleName, setRoleName] = useState('');
  const [formError, setFormError] = useState('');

  const { listQuery, create, isCreating, deactivate, deactivatingId, isDeactivating } =
    useStaff(page);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setFormError('');
    create(
      {
        email: email.trim(),
        password,
        fullName: fullName.trim(),
        accountType,
        roleName: accountType === 'TAB' ? roleName.trim() : undefined,
      },
      {
        onSuccess: () => {
          setEmail('');
          setPassword('');
          setFullName('');
          setRoleName('');
          setShowForm(false);
        },
        onError: (err) => {
          const apiError = extractApiError(err);
          setFormError(apiError?.message ?? 'Could not create the admin account.');
        },
      },
    );
  };

  const staff = listQuery.data?.items ?? [];

  return (
    <AdminShell title="Staff">
      <div className="flex justify-end mb-4">
        <Button onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : 'Invite Admin'}
        </Button>
      </div>

      {showForm && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>New Admin Account</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-2">
                  <Label htmlFor="staff-email">Email</Label>
                  <Input
                    id="staff-email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="staff-full-name">Full name</Label>
                  <Input
                    id="staff-full-name"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    required
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="staff-password">Initial password</Label>
                  <Input
                    id="staff-password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    minLength={8}
                  />
                  <p className="text-xs text-slate-500">
                    Relay this to the new admin out-of-band — there's no invite-email flow.
                  </p>
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="staff-account-type">Account type</Label>
                  <select
                    id="staff-account-type"
                    className="h-9 rounded-lg border border-input bg-transparent px-2.5 text-sm"
                    value={accountType}
                    onChange={(e) => setAccountType(e.target.value as AdminAccountType)}
                  >
                    {ACCOUNT_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                {accountType === 'TAB' && (
                  <div className="flex flex-col gap-2 col-span-2">
                    <Label htmlFor="staff-role-name">
                      Role name (must match an admin resource, e.g. KYC, DISPUTES)
                    </Label>
                    <Input
                      id="staff-role-name"
                      value={roleName}
                      onChange={(e) => setRoleName(e.target.value)}
                      required
                    />
                  </div>
                )}
              </div>
              {formError ? <p className="text-sm text-red-600">{formError}</p> : null}
              <Button type="submit" disabled={isCreating}>
                {isCreating ? 'Creating…' : 'Create Admin Account'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      {listQuery.isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          Couldn't load the staff directory. Only SUPER admins can view this page.
        </div>
      )}

      {listQuery.isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : staff.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
          <p className="text-slate-400 text-lg">No admin accounts found.</p>
        </div>
      ) : (
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
                  Type
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Role
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Status
                </th>
                <th className="px-6 py-3" />
              </tr>
            </thead>
            <tbody>
              {staff.map((s) => (
                <tr key={s.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-6 py-4 font-medium text-slate-900">{s.full_name}</td>
                  <td className="px-6 py-4 text-slate-600">{s.email}</td>
                  <td className="px-6 py-4 text-slate-600">{s.account_type}</td>
                  <td className="px-6 py-4 text-slate-600">{s.role_name ?? '—'}</td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                        s.is_active ? 'bg-green-100 text-green-700' : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      {s.is_active ? 'Active' : 'Deactivated'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    {s.is_active && (
                      <Button
                        variant="destructive"
                        size="sm"
                        disabled={isDeactivating && deactivatingId === s.id}
                        onClick={() => deactivate(s.id)}
                      >
                        {isDeactivating && deactivatingId === s.id ? 'Deactivating…' : 'Deactivate'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {listQuery.data && listQuery.data.total_pages > 1 && (
        <div className="flex justify-between items-center mt-4 text-sm text-slate-500">
          <span>
            Page {listQuery.data.page + 1} of {listQuery.data.total_pages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= listQuery.data.total_pages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
