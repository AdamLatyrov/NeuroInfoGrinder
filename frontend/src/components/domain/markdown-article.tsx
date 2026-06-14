import { Fragment, type ReactNode } from "react";

const INLINE_CODE_PATTERN = /`([^`\n]+)`/;
const STRONG_PATTERN = /\*\*([^*]+)\*\*/;

function renderInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const tokens = text.split(/(`[^`\n]+`|\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^\s)]+\)|https?:\/\/[^\s]+)/g);

  tokens.forEach((token, index) => {
    if (!token) {
      return;
    }

    const match = /^\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)$|^(https?:\/\/[^\s]+)$/i.exec(token);
    if (match) {
      const label = match[1] ?? match[3] ?? token;
      const url = match[2] ?? match[3] ?? token;
      nodes.push(
        <a
          key={index}
          href={url}
          target="_blank"
          rel="noreferrer"
          className="text-brand-blue underline decoration-brand-blue/40 underline-offset-2 hover:decoration-brand-blue"
        >
          {label}
        </a>,
      );
      return;
    }

    const codeMatch = token.match(INLINE_CODE_PATTERN);
    if (codeMatch && codeMatch[0] === token) {
      nodes.push(
        <code
          key={index}
          className="rounded bg-bg-app px-1.5 py-0.5 font-mono-value text-[0.95em] text-text-strong"
        >
          {token.slice(1, -1)}
        </code>,
      );
      return;
    }

    const strongMatch = token.match(STRONG_PATTERN);
    if (strongMatch && strongMatch[0] === token) {
      nodes.push(
        <strong key={index} className="font-semibold text-text-strong">
          {token.slice(2, -2)}
        </strong>,
      );
      return;
    }

    nodes.push(<Fragment key={index}>{token}</Fragment>);
  });

  return nodes;
}

interface MarkdownArticleProps {
  markdown: string;
  emptyFallback?: string;
  className?: string;
}

export function MarkdownArticle({
  markdown,
  emptyFallback = "Пока нет содержимого",
  className = "",
}: MarkdownArticleProps) {
  const source = markdown?.trim();
  if (!source) {
    return <div className="text-sm italic text-text-weak">{emptyFallback}</div>;
  }

  const elements: ReactNode[] = [];
  const lines = source.split(/\r?\n/);
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (trimmed.startsWith("```")) {
      const codeLines: string[] = [];
      const info = trimmed.slice(3).trim();
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith("```")) {
        codeLines.push(lines[index]);
        index += 1;
      }
      if (index < lines.length) {
        index += 1;
      }
      elements.push(
        <div key={`code-${elements.length}`} className="overflow-hidden rounded-2xl border border-border-subtle bg-bg-app">
          {info && (
            <div className="border-b border-border-subtle px-4 py-2 text-xs uppercase tracking-wide text-text-muted">
              {info}
            </div>
          )}
          <pre className="overflow-x-auto px-4 py-3 font-mono-value text-xs leading-6 text-text-default">
            <code>{codeLines.join("\n")}</code>
          </pre>
        </div>,
      );
      continue;
    }

    if (trimmed.startsWith("# ")) {
      elements.push(
        <h1 key={`h1-${elements.length}`} className="text-2xl font-semibold text-text-strong">
          {renderInline(trimmed.slice(2))}
        </h1>,
      );
      index += 1;
      continue;
    }

    if (trimmed.startsWith("## ")) {
      elements.push(
        <h2 key={`h2-${elements.length}`} className="mt-2 text-xl font-semibold text-text-strong">
          {renderInline(trimmed.slice(3))}
        </h2>,
      );
      index += 1;
      continue;
    }

    if (trimmed.startsWith("### ")) {
      elements.push(
        <h3 key={`h3-${elements.length}`} className="mt-2 text-lg font-semibold text-text-strong">
          {renderInline(trimmed.slice(4))}
        </h3>,
      );
      index += 1;
      continue;
    }

    if (trimmed.startsWith(">")) {
      const quoteLines: string[] = [];
      while (index < lines.length && lines[index].trim().startsWith(">")) {
        quoteLines.push(lines[index].trim().replace(/^>\s?/, ""));
        index += 1;
      }
      elements.push(
        <blockquote
          key={`quote-${elements.length}`}
          className="border-l-4 border-brand-blue/40 bg-brand-blue-soft/30 px-4 py-3 text-sm leading-7 text-text-default"
        >
          {quoteLines.map((quoteLine, quoteIndex) => (
            <p key={quoteIndex}>{renderInline(quoteLine)}</p>
          ))}
        </blockquote>,
      );
      continue;
    }

    if (/^[-*]\s+/.test(trimmed)) {
      const items: string[] = [];
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ""));
        index += 1;
      }
      elements.push(
        <ul key={`ul-${elements.length}`} className="list-disc space-y-2 pl-5 text-sm leading-7 text-text-default">
          {items.map((item, itemIndex) => (
            <li key={itemIndex}>{renderInline(item)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    if (/^\d+\.\s+/.test(trimmed)) {
      const items: string[] = [];
      while (index < lines.length && /^\d+\.\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^\d+\.\s+/, ""));
        index += 1;
      }
      elements.push(
        <ol key={`ol-${elements.length}`} className="list-decimal space-y-2 pl-5 text-sm leading-7 text-text-default">
          {items.map((item, itemIndex) => (
            <li key={itemIndex}>{renderInline(item)}</li>
          ))}
        </ol>,
      );
      continue;
    }

    const paragraphLines: string[] = [];
    while (index < lines.length && lines[index].trim()) {
      const current = lines[index].trim();
      if (
        current.startsWith("#") ||
        current.startsWith(">") ||
        current.startsWith("```") ||
        /^[-*]\s+/.test(current) ||
        /^\d+\.\s+/.test(current)
      ) {
        break;
      }
      paragraphLines.push(current);
      index += 1;
    }

    elements.push(
      <p key={`p-${elements.length}`} className="text-sm leading-7 text-text-default">
        {renderInline(paragraphLines.join(" "))}
      </p>,
    );
  }

  return <div className={`space-y-4 ${className}`.trim()}>{elements}</div>;
}
