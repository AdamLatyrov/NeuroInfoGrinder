import { Fragment, type ReactNode } from "react";

const LINK_PATTERN =
  /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s]+)/gi;

export interface RichMessageTextEntity {
  type: string | null;
  offset: number | null;
  length: number | null;
  url: string | null;
  text: string | null;
}

interface RichMessageTextProps {
  text: string;
  entities?: RichMessageTextEntity[] | null;
  emptyFallback?: string;
}

interface LinkEntityRange {
  start: number;
  end: number;
  href: string;
}

function normalizeExternalHref(url: string): string | null {
  const value = url.trim();
  if (!value) {
    return null;
  }
  if (/^https?:\/\//i.test(value)) {
    return value;
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) {
    return null;
  }
  return `https://${value}`;
}

function entityUrl(entity: RichMessageTextEntity, label: string): string | null {
  if (entity.url?.trim()) {
    return normalizeExternalHref(entity.url);
  }
  if (entity.type?.toLowerCase() === "url") {
    return normalizeExternalHref(label);
  }
  return null;
}

function linkEntityRanges(
  text: string,
  entities: RichMessageTextEntity[] | null | undefined
): LinkEntityRange[] {
  if (!entities?.length) {
    return [];
  }

  const ranges = entities
    .map((entity) => {
      if (!Number.isInteger(entity.offset) || !Number.isInteger(entity.length)) {
        return null;
      }

      const start = Math.max(0, Math.min(entity.offset ?? 0, text.length));
      const end = Math.max(start, Math.min(start + (entity.length ?? 0), text.length));
      if (start >= end) {
        return null;
      }

      const label = text.slice(start, end);
      if (entity.text && entity.text !== label) {
        return null;
      }

      const href = entityUrl(entity, label);
      if (!href) {
        return null;
      }

      return { start, end, href };
    })
    .filter((range): range is LinkEntityRange => Boolean(range))
    .sort((left, right) => left.start - right.start || right.end - left.end);

  const nonOverlappingRanges: LinkEntityRange[] = [];
  let lastEnd = 0;
  for (const range of ranges) {
    if (range.start < lastEnd) {
      continue;
    }
    nonOverlappingRanges.push(range);
    lastEnd = range.end;
  }

  return nonOverlappingRanges;
}

function renderLink(key: string, href: string, label: string): ReactNode {
  return (
    <a
      key={key}
      href={href}
      target="_blank"
      rel="noreferrer"
      className="text-brand-blue underline decoration-brand-blue/40 underline-offset-2 hover:decoration-brand-blue"
    >
      {label}
    </a>
  );
}

function renderAutoLinks(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let lastIndex = 0;

  for (const match of text.matchAll(LINK_PATTERN)) {
    const fullMatch = match[0];
    const index = match.index ?? 0;

    if (index > lastIndex) {
      nodes.push(
        <Fragment key={`${keyPrefix}-text-${lastIndex}`}>
          {text.slice(lastIndex, index)}
        </Fragment>
      );
    }

    const markdownLabel = match[1];
    const markdownUrl = match[2];
    const plainUrl = match[3];
    const url = markdownUrl ?? plainUrl ?? fullMatch;
    const label = markdownLabel ?? plainUrl ?? url;

    nodes.push(renderLink(`${keyPrefix}-link-${index}`, url, label));

    lastIndex = index + fullMatch.length;
  }

  if (lastIndex < text.length) {
    nodes.push(
      <Fragment key={`${keyPrefix}-tail-${lastIndex}`}>
        {text.slice(lastIndex)}
      </Fragment>
    );
  }

  return nodes;
}

export function RichMessageText({
  text,
  entities,
  emptyFallback = "Пустое сообщение",
}: RichMessageTextProps) {
  if (!text) {
    return <span className="italic text-text-weak">{emptyFallback}</span>;
  }

  const ranges = linkEntityRanges(text, entities);
  if (ranges.length === 0) {
    return <>{renderAutoLinks(text, "auto")}</>;
  }

  const nodes: ReactNode[] = [];
  let lastIndex = 0;

  for (const range of ranges) {
    if (range.start > lastIndex) {
      nodes.push(...renderAutoLinks(text.slice(lastIndex, range.start), `text-${lastIndex}`));
    }

    nodes.push(
      renderLink(
        `entity-link-${range.start}`,
        range.href,
        text.slice(range.start, range.end)
      )
    );
    lastIndex = range.end;
  }

  if (lastIndex < text.length) {
    nodes.push(...renderAutoLinks(text.slice(lastIndex), `tail-${lastIndex}`));
  }

  return <>{nodes}</>;
}
