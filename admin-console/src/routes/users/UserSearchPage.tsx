import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { UsersTable } from './UsersTable';
import { useUserSearch } from './useUserSearch';

const KYC_STATUS_OPTIONS = [
  'PENDING',
  'SUBMITTED',
  'APPROVED',
  'REJECTED',
  'RESUBMISSION_REQUIRED',
];
const ACCOUNT_STATUS_OPTIONS = ['ACTIVE', 'SUSPENDED', 'CLOSED'];

export default function UserSearchPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [kycStatus, setKycStatus] = useState('');
  const [accountStatus, setAccountStatus] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useUserSearch({
    search: search || undefined,
    kycStatus: kycStatus || undefined,
    accountStatus: accountStatus || undefined,
    page,
  });

  return (
    <AdminShell title="Users">
      <div className="mb-4 flex gap-3">
        <Input
          placeholder="Search by email or phone"
          aria-label="Search by email or phone"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          className="max-w-sm"
        />

        <select
          aria-label="Filter by KYC status"
          className="h-8 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
          value={kycStatus}
          onChange={(e) => {
            setKycStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All KYC statuses</option>
          {KYC_STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>

        <select
          aria-label="Filter by account status"
          className="h-8 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
          value={accountStatus}
          onChange={(e) => {
            setAccountStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All account statuses</option>
          {ACCOUNT_STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      {isError && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn't load users.
          <button type="button" onClick={() => refetch()} className="ml-2 underline">
            Retry
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : (
        <UsersTable items={data?.items ?? []} onSelect={(id) => navigate(`/users/${id}`)} />
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
          <span>
            Page {data.page + 1} of {data.totalPages} ({data.totalElements} users)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={data.page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={data.page + 1 >= data.totalPages}
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
