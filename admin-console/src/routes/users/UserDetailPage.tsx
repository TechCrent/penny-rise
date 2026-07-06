import { useState, type ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { SuspendUserModal } from './SuspendUserModal';
import { ConfirmActionModal } from './ConfirmActionModal';
import { useUserDetail } from './useUserDetail';

function pesewasToCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

type ModalKind = 'SUSPEND' | 'RESTORE' | 'FORCE_LOGOUT' | null;

export default function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const { detailQuery, suspend, isSuspending, restore, isRestoring, forceLogout, isForcingLogout } =
    useUserDetail(userId ?? '');
  const [modal, setModal] = useState<ModalKind>(null);

  if (detailQuery.isLoading) {
    return (
      <AdminShell title="User">
        <div className="text-center py-12 text-slate-400">Loading…</div>
      </AdminShell>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AdminShell title="User">
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
          Couldn't load this user.
        </div>
      </AdminShell>
    );
  }

  const {
    user,
    vaults,
    activeSusuMemberships,
    recentTransactions,
    kycSubmissionHistory,
    sessions,
  } = detailQuery.data;
  const canSuspend = user.accountStatus !== 'SUSPENDED';
  const canRestore = user.accountStatus === 'SUSPENDED';

  return (
    <AdminShell title={user.displayName}>
      <div className="flex justify-between items-start mb-6">
        <div>
          <p className="text-slate-500">
            {user.email} · {user.phone}
          </p>
          <div className="flex gap-2 mt-2">
            <Pill>{user.kycStatus}</Pill>
            <Pill tone={user.accountStatus === 'SUSPENDED' ? 'red' : 'default'}>
              {user.accountStatus}
            </Pill>
            <Pill>{user.subscriptionTier}</Pill>
          </div>
        </div>

        <div className="flex gap-2" data-testid="action-bar">
          {canSuspend && (
            <Button variant="destructive" onClick={() => setModal('SUSPEND')}>
              Suspend
            </Button>
          )}
          {canRestore && (
            <Button variant="default" onClick={() => setModal('RESTORE')}>
              Restore
            </Button>
          )}
          <Button variant="outline" onClick={() => setModal('FORCE_LOGOUT')}>
            Force Logout
          </Button>
        </div>
      </div>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Identity</CardTitle>
        </CardHeader>
        <CardContent>
          <p data-testid="ghana-card-masked">
            Ghana Card: {user.maskedGhanaCard ?? '— not on file —'}
          </p>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>KYC Submission History</CardTitle>
        </CardHeader>
        <CardContent>
          {kycSubmissionHistory.length === 0 && (
            <p className="text-slate-400">No KYC submissions.</p>
          )}
          {kycSubmissionHistory.map((submission) => (
            <div
              key={submission.submissionId}
              className="border-b border-slate-100 py-3 last:border-none"
            >
              <div className="flex justify-between">
                <span className="font-medium text-slate-800">{submission.status}</span>
                <span className="text-sm text-slate-400">
                  {new Date(submission.submittedAt).toLocaleDateString()}
                </span>
              </div>
              {submission.decisionReason && (
                <p className="text-sm text-slate-500">{submission.decisionReason}</p>
              )}

              {submission.documents && submission.documents.length > 0 ? (
                <div className="grid grid-cols-3 gap-3 mt-2" data-testid="kyc-documents">
                  {submission.documents.map((doc) => (
                    <div key={doc.documentType}>
                      <p className="text-xs font-medium text-slate-500 mb-1">{doc.documentType}</p>
                      <a
                        href={doc.signedUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        title="Click to view full size"
                        className="block cursor-zoom-in"
                      >
                        <img
                          src={doc.signedUrl}
                          alt={doc.documentType}
                          className="w-full rounded-lg border border-slate-200 object-cover max-h-48 hover:opacity-90 transition-opacity"
                          onError={(e) => {
                            const img = e.target as HTMLImageElement;
                            img.style.display = 'none';
                            img.dataset.failed = 'true';
                          }}
                        />
                      </a>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-slate-400 mt-1">
                  No document images available for this submission.
                </p>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      <div className="grid grid-cols-2 gap-4 mb-4">
        <Card>
          <CardHeader>
            <CardTitle>Vaults</CardTitle>
          </CardHeader>
          <CardContent>
            {vaults.length === 0 && <p className="text-slate-400">No vaults.</p>}
            {vaults.map((v) => (
              <div key={v.id} className="flex justify-between py-1 text-sm">
                <span>
                  {v.name} <span className="text-slate-400">({v.vaultType})</span>
                </span>
                <span>GHS {pesewasToCedis(v.balancePesewas)}</span>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Active Susu Memberships</CardTitle>
          </CardHeader>
          <CardContent>
            {activeSusuMemberships.length === 0 && (
              <p className="text-slate-400">No active susu memberships.</p>
            )}
            {activeSusuMemberships.map((m) => (
              <div key={m.susuGroupId} className="flex justify-between py-1 text-sm">
                <span>{m.susuGroupName}</span>
                <span className="text-slate-400">Position {m.rotationPosition}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Recent Transactions</CardTitle>
          </CardHeader>
          <CardContent>
            {recentTransactions.length === 0 && (
              <p className="text-slate-400">No recent transactions.</p>
            )}
            {recentTransactions.map((t) => (
              <div key={t.reference} className="flex justify-between py-1 text-sm">
                <span>{t.type}</span>
                <span>GHS {pesewasToCedis(t.amountPesewas)}</span>
                <span className="text-slate-400">{t.status}</span>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Active Sessions</CardTitle>
          </CardHeader>
          <CardContent>
            {sessions === undefined && (
              <p className="text-xs text-slate-400">Session data isn't available yet.</p>
            )}
            {sessions?.length === 0 && <p className="text-slate-400">No active sessions.</p>}
            {sessions?.map((s) => (
              <div key={s.id} className="flex justify-between py-1 text-sm">
                <span>{s.deviceLabel ?? s.ipAddress}</span>
                <span className="text-slate-400">
                  Last used {new Date(s.lastUsedAt).toLocaleString()}
                </span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      {modal === 'SUSPEND' && (
        <SuspendUserModal
          isSubmitting={isSuspending}
          onConfirm={(reason) => suspend(reason, { onSuccess: () => setModal(null) })}
          onCancel={() => setModal(null)}
        />
      )}

      {modal === 'RESTORE' && (
        <ConfirmActionModal
          title="Restore this user?"
          description="The user will regain full access immediately and be able to log in again."
          confirmLabel="Restore User"
          isSubmitting={isRestoring}
          onConfirm={() => restore(undefined, { onSuccess: () => setModal(null) })}
          onCancel={() => setModal(null)}
        />
      )}

      {modal === 'FORCE_LOGOUT' && (
        <ConfirmActionModal
          title="Force logout this user?"
          description="All of this user's active sessions will be revoked. They can log in again immediately — this does not change their account status."
          confirmLabel="Confirm Force Logout"
          isSubmitting={isForcingLogout}
          onConfirm={() => forceLogout(undefined, { onSuccess: () => setModal(null) })}
          onCancel={() => setModal(null)}
        />
      )}
    </AdminShell>
  );
}

function Pill({ children, tone = 'default' }: { children: ReactNode; tone?: 'default' | 'red' }) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
        tone === 'red' ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-700'
      }`}
    >
      {children}
    </span>
  );
}
