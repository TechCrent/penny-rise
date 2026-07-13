import { useState, type FormEvent } from 'react';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
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
      <div className="mb-4 flex justify-end">
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
                  <p className="text-xs text-muted-foreground">
                    Relay this to the new admin out-of-band — there's no invite-email flow.
                  </p>
                </div>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="staff-account-type">Account type</Label>
                  <select
                    id="staff-account-type"
                    className="h-9 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
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
                  <div className="col-span-2 flex flex-col gap-2">
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
              {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
              <Button type="submit" disabled={isCreating}>
                {isCreating ? 'Creating…' : 'Create Admin Account'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      {listQuery.isError && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn't load the staff directory. Only SUPER admins can view this page.
        </div>
      )}

      {listQuery.isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : staff.length === 0 ? (
        <div className="rounded-xl border border-border bg-card p-12 text-center">
          <p className="text-lg text-muted-foreground">No admin accounts found.</p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-table-header">
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.map((s) => (
              <TableRow key={s.id}>
                <TableCell className="font-medium text-foreground">{s.full_name}</TableCell>
                <TableCell className="text-muted-foreground">{s.email}</TableCell>
                <TableCell className="text-muted-foreground">{s.account_type}</TableCell>
                <TableCell className="text-muted-foreground">{s.role_name ?? '—'}</TableCell>
                <TableCell>
                  <Badge variant={s.is_active ? 'success' : 'neutral'}>
                    {s.is_active ? 'Active' : 'Deactivated'}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
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
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {listQuery.data && listQuery.data.total_pages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
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
