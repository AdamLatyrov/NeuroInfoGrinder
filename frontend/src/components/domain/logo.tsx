/**
 * NeuroInfoGrinder brand mark — same visual language as Cerebrum:
 * dark tile, "N" lettermark with accent arc, center data point.
 * Uses currentColor + CSS accent variable for theme adaptability.
 */

interface LogoProps {
  markOnly?: boolean;
  className?: string;
  size?: number;
}

export function Logo({ markOnly = false, className, size = 32 }: LogoProps) {
  return (
    <div className={className} style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <svg
        viewBox="0 0 32 32"
        width={size}
        height={size}
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* Dark rounded tile */}
        <rect width="32" height="32" rx="7" fill="#0a0a0a" />
        {/* Left vertical */}
        <path d="M9 24V8" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" />
        {/* Diagonal */}
        <path d="M9 8L23 24" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" />
        {/* Right vertical — accent (purple) */}
        <path d="M23 24V8" stroke="#a78bfa" strokeWidth="2.8" strokeLinecap="round" />
        {/* Center data point */}
        <circle cx="16" cy="16" r="2" fill="#a78bfa" />
      </svg>

      {!markOnly && (
        <span
          style={{
            fontFamily: "'Geist', 'Inter', system-ui, sans-serif",
            fontWeight: 600,
            fontSize: size * 0.45,
            letterSpacing: "-0.02em",
            color: "var(--color-text-strong, #fafafa)",
          }}
        >
          NeuroInfoGrinder
        </span>
      )}
    </div>
  );
}
