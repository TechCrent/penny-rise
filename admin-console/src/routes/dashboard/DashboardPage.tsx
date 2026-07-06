import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { fetchDashboardSummary } from '../../api/dashboardAdmin';

interface SummaryTile {
  label: string;
  value: number;
  path: string;
  tone: 'default' | 'warning';
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['adminDashboard'],
    queryFn: fetchDashboardSummary,
  });

  const tiles: SummaryTile[] = [
    {
      label: 'Pending KYC Reviews',
      value: data?.pending_kyc_count ?? 0,
      path: '/kyc-queue',
      tone: 'default',
    },
    {
      label: 'Flagged Accounts',
      value: data?.flagged_accounts_count ?? 0,
      path: '/kyc-queue',
      tone: 'warning',
    },
    {
      label: 'Open Disputes',
      value: data?.open_disputes_count ?? 0,
      path: '/disputes',
      tone: 'warning',
    },
    {
      label: 'Flagged Susu Groups',
      value: data?.flagged_susu_groups_count ?? 0,
      path: '/susu-groups',
      tone: 'warning',
    },
  ];

  return (
    <AdminShell title="Dashboard">
      {isError ? (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          Couldn't load the dashboard summary.
        </div>
      ) : null}

      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {tiles.map((tile) => (
            <Card
              key={tile.label}
              className="cursor-pointer hover:border-slate-400 transition-colors"
              onClick={() => navigate(tile.path)}
              data-testid={`dashboard-tile-${tile.label}`}
            >
              <CardHeader>
                <CardTitle className="text-sm font-medium text-slate-500">{tile.label}</CardTitle>
              </CardHeader>
              <CardContent>
                <p
                  className={`text-3xl font-bold ${
                    tile.tone === 'warning' && tile.value > 0 ? 'text-amber-600' : 'text-slate-900'
                  }`}
                >
                  {tile.value}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
