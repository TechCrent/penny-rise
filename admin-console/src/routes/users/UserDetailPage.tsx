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
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      </AdminShell>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AdminShell title="User">
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
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
      <div className="mb-6 flex items-start justify-between">
        <div>
          <p className="text-muted-foreground">
            {user.email} · {user.phone}
          </p>
          <div className="mt-2 flex gap-2">
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
          <p className="text-foreground" data-testid="ghana-card-masked">
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
            <p className="text-disabled-foreground">No KYC submissions.</p>
          )}
          {kycSubmissionHistory.map((submission) => (
            <div key={submission.submissionId} className="border-b border-border py-3 last:border-none">
              <div className="flex justify-between">
                <span className="font-medium text-foreground">{submission.status}</span>
                <span className="text-sm text-disabled-foreground">
                  {new Date(submission.submittedAt).toLocaleDateString()}
                </span>
              </div>
              {submission.decisionReason && (
                <p className="text-sm text-muted-foreground">{submission.decisionReason}</p>
              )}

              {submission.documents && submission.documents.length > 0 ? (
                <div className="mt-2 grid grid-cols-3 gap-3" data-testid="kyc-documents">
                  {submission.documents.map((doc) => (
                    <div key={doc.documentType}>
                      <p className="mb-1 text-xs font-medium text-muted-foreground">
                        {doc.documentType}
                      </p>
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
                          className="max-h-48 w-full rounded-lg border border-border object-cover transition-opacity hover:opacity-90"
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
                <p className="mt-1 text-xs text-disabled-foreground">
                  No document images available for this submission.
                </p>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      <div className="mb-4 grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Vaults</CardTitle>
          </CardHeader>
          <CardContent>
            {vaults.length === 0 && <p className="text-disabled-foreground">No vaults.</p>}
            {vaults.map((v) => (
              <div key={v.id} className="flex justify-between py-1 text-sm text-foreground">
                <span>
                  {v.name} <span className="text-disabled-foreground">({v.vaultType})</span>
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
              <p className="text-disabled-foreground">No active susu memberships.</p>
            )}
            {activeSusuMemberships.map((m) => (
              <div key={m.susuGroupId} className="flex justify-between py-1 text-sm text-foreground">
                <span>{m.susuGroupName}</span>
                <span className="text-disabled-foreground">Position {m.rotationPosition}</span>
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
              <p className="text-disabled-foreground">No recent transactions.</p>
            )}
            {recentTransactions.map((t) => (
              <div key={t.reference} className="flex justify-between py-1 text-sm text-foreground">
                <span>{t.type}</span>
                <span>GHS {pesewasToCedis(t.amountPesewas)}</span>
                <span className="text-disabled-foreground">{t.status}</span>
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
              <p className="text-xs text-disabled-foreground">Session data isn't available yet.</p>
            )}
            {sessions?.length === 0 && <p className="text-disabled-foreground">No active sessions.</p>}
            {sessions?.map((s) => (
              <div key={s.id} className="flex justify-between py-1 text-sm text-foreground">
                <span>{s.deviceLabel ?? s.ipAddress}</span>
                <span className="text-disabled-foreground">
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
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        tone === 'red'
          ? 'border border-destructive/30 bg-destructive/15 text-destructive'
          : 'border border-border bg-card-secondary text-muted-foreground'
      }`}
    >
      {children}
    </span>
  );
}
