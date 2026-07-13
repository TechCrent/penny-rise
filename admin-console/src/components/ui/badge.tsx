import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex w-fit shrink-0 items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-medium',
  {
    variants: {
      variant: {
        neutral: 'border-border bg-card-secondary text-muted-foreground',
        gold: 'border-primary/30 bg-primary/15 text-primary',
        success: 'border-success/30 bg-success/15 text-success',
        warning: 'border-warning/30 bg-warning/15 text-warning',
        destructive: 'border-destructive/30 bg-destructive/15 text-destructive',
        info: 'border-info/30 bg-info/15 text-info',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
);

function Badge({
  className,
  variant,
  ...props
}: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return (
    <span data-slot="badge" className={cn(badgeVariants({ variant, className }))} {...props} />
  );
}

export { Badge, badgeVariants };
