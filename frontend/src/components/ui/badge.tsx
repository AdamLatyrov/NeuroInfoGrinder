import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex min-h-6 items-center justify-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold leading-tight transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "bg-brand-blue-soft text-brand-blue",
        success: "bg-success-soft text-success",
        warning: "bg-warning-soft text-warning",
        danger: "bg-danger-soft text-danger",
        outline: "border border-border-subtle text-text-muted bg-transparent",
        secondary: "bg-bg-app text-text-muted",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {
  dot?: boolean;
}

function Badge({ className, variant, dot, children, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ variant }), className)} {...props}>
      {dot && (
        <span
          className={cn("h-1.5 w-1.5 rounded-full", {
            "bg-brand-blue": variant === "default",
            "bg-success": variant === "success",
            "bg-warning": variant === "warning",
            "bg-danger": variant === "danger",
            "bg-text-muted": variant === "outline" || variant === "secondary",
          })}
        />
      )}
      {children}
    </span>
  );
}

export { Badge, badgeVariants };
