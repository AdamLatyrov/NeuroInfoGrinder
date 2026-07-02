#!/usr/bin/env python3
"""Classify one message or a small live batch using the offline lab classifier."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from message_lab_core import (
    MessageRecord,
    add_common_args,
    classify_text,
    load_messages,
    markdown_table,
    text_preview,
    utc_now_iso,
    write_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Offline live message classifier for NeuroInfoGrinder lab calibration")
    source = parser.add_mutually_exclusive_group(required=False)
    source.add_argument("--text", help="Single message text")
    source.add_argument("--file", type=Path, help="JSON/JSONL/CSV/TXT file with messages")
    source.add_argument("--stdin", action="store_true", help="Read one message or JSON/JSONL batch from stdin")
    parser.add_argument("--chat", default="", help="Optional chat title for --text")
    parser.add_argument("--topic", default="", help="Optional topic title for --text")
    add_common_args(parser)
    return parser.parse_args()


def read_stdin_records() -> list[MessageRecord]:
    payload = sys.stdin.read().strip()
    if not payload:
        return []
    if payload.startswith("{") or payload.startswith("["):
        temp = Path("__stdin__.json")
        temp.write_text(payload, encoding="utf-8")
        try:
            return load_messages(temp)
        finally:
            temp.unlink(missing_ok=True)
    return [MessageRecord("stdin-1", payload)]


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    if args.file:
        records = load_messages(args.file)
    elif args.stdin:
        records = read_stdin_records()
    else:
        text = args.text if args.text is not None else input("Message: ")
        records = [MessageRecord("live-1", text, args.chat, args.topic)]

    results = []
    for record in records:
        classification = classify_text(record.text, chat=record.chat, topic=record.topic)
        results.append(
            {
                "id": record.id,
                "chat": record.chat,
                "topic": record.topic,
                "preview": text_preview(record.text),
                "classification": classification.to_dict(),
            }
        )

    payload = {
        "generatedUtc": utc_now_iso(),
        "mode": "offline-live-classification",
        "count": len(results),
        "items": results,
    }

    print(json.dumps(payload, ensure_ascii=False, indent=2))

    if args.output_json:
        write_json(args.output_json, payload)
    if args.output_md:
        rows = [
            [
                item["id"],
                item["classification"]["primaryClass"],
                item["classification"]["materialRoute"],
                item["classification"]["materialType"],
                item["classification"]["action"],
                item["classification"]["confidence"],
                item["preview"],
            ]
            for item in results
        ]
        md = "# Live Message Classification\n\n"
        md += f"- Generated UTC: `{payload['generatedUtc']}`\n"
        md += f"- Count: `{payload['count']}`\n\n"
        md += markdown_table(rows, ["ID", "Class", "Route", "Type", "Action", "Confidence", "Preview"])
        md += "\n"
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        args.output_md.write_text(md, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
