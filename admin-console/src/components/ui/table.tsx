import * as React from 'react';

import { cn } from '@/lib/utils';

function Table({ className, ...props }: React.ComponentProps<'table'>) {
  return (
    <div className="w-full overflow-hidden rounded-xl border border-table-border bg-table-row">
      <div className="w-full overflow-x-auto">
        <table data-slot="table" className={cn('w-full text-sm', className)} {...props} />
      </div>
    </div>
  );
}

function TableHeader({ className, ...props }: React.ComponentProps<'thead'>) {
  return (
    <thead
      data-slot="table-header"
      className={cn('bg-table-header [&_tr]:border-b [&_tr]:border-table-border', className)}
      {...props}
    />
  );
}

function TableBody({ className, ...props }: React.ComponentProps<'tbody'>) {
  return (
    <tbody
      data-slot="table-body"
      className={cn('[&_tr:last-child]:border-0', className)}
      {...props}
    />
  );
}

function TableRow({ className, ...props }: React.ComponentProps<'tr'>) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        'border-b border-table-border/60 bg-table-row transition-colors hover:bg-table-hover',
        className,
      )}
      {...props}
    />
  );
}

function TableHead({ className, ...props }: React.ComponentProps<'th'>) {
  return (
    <th
      data-slot="table-head"
      className={cn(
        'px-6 py-3 text-left text-xs font-semibold tracking-wide text-muted-foreground uppercase',
        className,
      )}
      {...props}
    />
  );
}

function TableCell({ className, ...props }: React.ComponentProps<'td'>) {
  return (
    <td data-slot="table-cell" className={cn('px-6 py-4 text-foreground', className)} {...props} />
  );
}

export { Table, TableHeader, TableBody, TableRow, TableHead, TableCell };
