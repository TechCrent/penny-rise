import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Users, Scale, CircleDollarSign } from 'lucide-react';
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { AdminShell } from '../../components/layout/AdminShell';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { fetchDashboardSummary } from '../../api/dashboardAdmin';

interface SummaryTile {
  label: string;
  value: number;
  path: string;
  tone: 'default' | 'warning';
  icon: typeof ShieldCheck;
  chartColor: string;
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
      icon: ShieldCheck,
      chartColor: 'var(--chart-1)',
    },
    {
      label: 'Flagged Accounts',
      value: data?.flagged_accounts_count ?? 0,
      path: '/kyc-queue',
      tone: 'warning',
      icon: Users,
      chartColor: 'var(--chart-2)',
    },
    {
      label: 'Open Disputes',
      value: data?.open_disputes_count ?? 0,
      path: '/disputes',
      tone: 'warning',
      icon: Scale,
      chartColor: 'var(--chart-4)',
    },
    {
      label: 'Flagged Susu Groups',
      value: data?.flagged_susu_groups_count ?? 0,
      path: '/susu-groups',
      tone: 'warning',
      icon: CircleDollarSign,
      chartColor: 'var(--chart-5)',
    },
  ];

  const chartData = tiles.map((tile) => ({
    label: tile.label.replace('Pending ', '').replace('Flagged ', ''),
    value: tile.value,
    color: tile.chartColor,
  }));

  return (
    <AdminShell title="Dashboard">
      {isError ? (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn't load the dashboard summary.
        </div>
      ) : null}

      {isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {tiles.map((tile) => (
              <Card
                key={tile.label}
                className="cursor-pointer transition-colors hover:border-primary/40"
                onClick={() => navigate(tile.path)}
                data-testid={`dashboard-tile-${tile.label}`}
              >
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-sm font-medium text-muted-foreground">
                      {tile.label}
                    </CardTitle>
                    <tile.icon className="size-4 text-muted-foreground" />
                  </div>
                </CardHeader>
                <CardContent>
                  <p
                    className={`text-3xl font-bold ${
                      tile.tone === 'warning' && tile.value > 0 ? 'text-warning' : 'text-foreground'
                    }`}
                  >
                    {tile.value}
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Review queues at a glance</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} layout="vertical" margin={{ left: 24, right: 24 }}>
                    <XAxis type="number" allowDecimals={false} stroke="var(--muted-foreground)" fontSize={12} />
                    <YAxis
                      type="category"
                      dataKey="label"
                      width={140}
                      stroke="var(--muted-foreground)"
                      fontSize={12}
                      tickLine={false}
                      axisLine={false}
                    />
                    <Tooltip
                      cursor={{ fill: 'var(--table-hover)' }}
                      contentStyle={{
                        background: 'var(--popover)',
                        border: '1px solid var(--border)',
                        borderRadius: 8,
                        color: 'var(--foreground)',
                      }}
                    />
                    <Bar dataKey="value" radius={[0, 6, 6, 0]} barSize={22}>
                      {chartData.map((entry) => (
                        <Cell key={entry.label} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </AdminShell>
  );
}
