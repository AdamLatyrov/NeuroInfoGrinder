#!/usr/bin/env python3
"""Parameter sweep for the offline temporal pool graph algorithm."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from compare_message_lab_algorithms import algorithm_metrics, run_temporal_pool_graph
from message_lab_core import classify_text, load_messages, utc_now_iso, write_json, markdown_table


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sweep temporal pool graph parameters on exported messages")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--thresholds", default="0.25,0.27,0.29,0.31,0.33,0.35")
    parser.add_argument("--windows", default="45,90,150")
    parser.add_argument("--output-json", type=Path, default=None)
    parser.add_argument("--output-md", type=Path, default=None)
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    records = load_messages(args.input)
    if args.limit and args.limit > 0:
        records = records[: args.limit]
    classifications = {record.id: classify_text(record.text, chat=record.chat, topic=record.topic) for record in records}
    thresholds = [float(value.strip()) for value in args.thresholds.split(",") if value.strip()]
    windows = [int(value.strip()) for value in args.windows.split(",") if value.strip()]

    rows = []
    best_clusters = []
    best_score = -999.0
    for threshold in thresholds:
        for window in windows:
            run_args = argparse.Namespace(window_threshold=threshold, window_size=window)
            clusters = run_temporal_pool_graph(records, classifications, run_args)
            metrics = algorithm_metrics(f"temporal_t{threshold}_w{window}", records, classifications, clusters)
            metrics["threshold"] = threshold
            metrics["windowSize"] = window
            rows.append(metrics)
            if metrics["recommendationScore"] > best_score:
                best_score = metrics["recommendationScore"]
                best_clusters = clusters[:30]

    rows = sorted(rows, key=lambda item: (-item["recommendationScore"], item["singletonReviewClusters"]))
    payload = {
        "generatedUtc": utc_now_iso(),
        "mode": "offline-temporal-pool-graph-parameter-sweep",
        "input": str(args.input),
        "count": len(records),
        "results": rows,
        "best": rows[0] if rows else None,
        "bestTopClusters": best_clusters,
    }
    print(json.dumps({k: v for k, v in payload.items() if k != "bestTopClusters"}, ensure_ascii=False, indent=2))
    if args.output_json:
        write_json(args.output_json, payload)
    if args.output_md:
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        args.output_md.write_text(build_markdown(payload), encoding="utf-8")
    return 0


def build_markdown(payload: dict) -> str:
    lines = ["# Temporal Pool Graph Parameter Sweep", ""]
    lines.append(f"- Generated UTC: `{payload['generatedUtc']}`")
    lines.append(f"- Input: `{payload['input']}`")
    lines.append(f"- Messages: `{payload['count']}`")
    if payload["best"]:
        best = payload["best"]
        lines.append(f"- Best: threshold `{best['threshold']}`, window `{best['windowSize']}`, score `{best['recommendationScore']}`")
    lines.append("")
    lines.append("## Results")
    lines.append("")
    lines.append(
        markdown_table(
            [
                [
                    item["threshold"],
                    item["windowSize"],
                    item["recommendationScore"],
                    item["reviewClusterCount"],
                    item["multiMessageReviewClusters"],
                    item["singletonReviewClusters"],
                    item["usefulGroupingRate"],
                    item["reviewGroupingRate"],
                    item["reviewPurityRate"],
                    item["blockedReviewLeaks"],
                    item["oversizedReviewClusters"],
                ]
                for item in payload["results"]
            ],
            [
                "Threshold",
                "Window",
                "Score",
                "Review",
                "Multi Review",
                "Singleton Review",
                "Useful Grouping",
                "Review Grouping",
                "Purity",
                "Blocked Leaks",
                "Oversized",
            ],
        )
    )
    lines.append("")
    lines.append("## Best Top Clusters")
    lines.append("")
    lines.append(
        markdown_table(
            [
                [
                    cluster["id"],
                    cluster["size"],
                    cluster["decision"],
                    cluster["qualityScore"],
                    ", ".join(cluster["classes"].keys()),
                    ", ".join(cluster["sourceTopics"].keys()),
                    ", ".join(cluster["sampleMessageIds"][:8]),
                ]
                for cluster in payload["bestTopClusters"][:20]
            ],
            ["Cluster", "Size", "Decision", "Score", "Classes", "Topics", "Sample IDs"],
        )
    )
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    raise SystemExit(main())
