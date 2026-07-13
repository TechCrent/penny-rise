import { format } from 'date-fns';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import type { AdminUserListItem } from '../../api/usersAdmin';

interface UsersTableProps {
  items: AdminUserListItem[];
  onSelect: (id: string) => void;
}

export function UsersTable({ items, onSelect }: UsersTableProps) {
  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-12 text-center">
        <p className="text-lg text-muted-foreground">No users match this search.</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-table-header">
          <TableHead>Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>KYC Status</TableHead>
          <TableHead>Account Status</TableHead>
          <TableHead>Tier</TableHead>
          <TableHead>Joined</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((user) => (
          <TableRow
            key={user.id}
            className="cursor-pointer"
            onClick={() => onSelect(user.id)}
            data-testid={`user-row-${user.id}`}
          >
            <TableCell className="font-medium text-foreground">{user.displayName}</TableCell>
            <TableCell className="text-muted-foreground">{user.email}</TableCell>
            <TableCell>
              <Badge variant="neutral">{user.kycStatus}</Badge>
            </TableCell>
            <TableCell>
              <Badge variant={user.accountStatus === 'SUSPENDED' ? 'destructive' : 'neutral'}>
                {user.accountStatus}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">{user.subscriptionTier}</TableCell>
            <TableCell className="text-muted-foreground">
              {format(new Date(user.createdAt), 'dd MMM yyyy')}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
