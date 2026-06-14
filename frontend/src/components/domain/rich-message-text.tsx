import { Fragment, type ReactNode } from "react";

const LINK_PATTERN =
  /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s]+)/gi;

interface RichMessageTextProps {
  text: string;
  emptyFallback?: string;
}

export function RichMessageText({
  text,
  emptyFallback = "Пустое сообщение",
}: RichMessageTextProps) {
  if (!text) {
    return <span className="italic text-text-weak">{emptyFallback}</span>;
  }

  const nodes: ReactNode[] = [];
  let lastIndex = 0;

  for (const match of text.matchAll(LINK_PATTERN)) {
    const fullMatch = match[0];
    const index = match.index ?? 0;

    if (index > lastIndex) {
      nodes.push(
        <Fragment key={`text-${lastIndex}`}>
          {text.slice(lastIndex, index)}
        </Fragment>
      );
    }

    const markdownLabel = match[1];
    const markdownUrl = match[2];
    const plainUrl = match[3];
    const url = markdownUrl ?? plainUrl ?? fullMatch;
    const label = markdownLabel ?? plainUrl ?? url;

    nodes.push(
      <a
        key={`link-${index}`}
        href={url}
        target="_blank"
        rel="noreferrer"
        className="text-brand-blue underline decoration-brand-blue/40 underline-offset-2 hover:decoration-brand-blue"
      >
        {label}
      </a>
    );

    lastIndex = index + fullMatch.length;
  }

  if (lastIndex < text.length) {
    nodes.push(
      <Fragment key={`tail-${lastIndex}`}>
        {text.slice(lastIndex)}
      </Fragment>
    );
  }

  return <>{nodes}</>;
}
