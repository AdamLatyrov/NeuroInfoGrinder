#!/usr/bin/env python3
"""Offline batch classification and clustering for NeuroInfoGrinder lab calibration."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

from message_lab_core import (
    add_common_args,
    classify_text,
    cluster_messages,
    load_messages,
    markdown_table,
    text_preview,
    utc_now_iso,
    write_json,
)
from compare_message_lab_algorithms import algorithm_specs


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Offline batch classifier and clusterer for NeuroInfoGrinder lab calibration")
    parser.add_argument("--input", required=True, type=Path, help="Input JSON/JSONL/CSV/TXT file")
    parser.add_argument("--limit", type=int, default=0, help="Limit records after loading; 0 means all")
    parser.add_argument("--algorithm", default="pool_graph", choices=["baseline_graph", "pool_graph", "temporal_pool_graph", "token_dbscan", "canopy_signatures"], help="Clustering algorithm; pool_graph is the calibrated default")
    parser.add_argument("--threshold", type=float, default=0.34, help="Similarity threshold for dependency-free graph clustering")
    parser.add_argument("--pool-threshold", type=float, default=0.25, help="Similarity threshold for semantic-pool graph clustering")
    parser.add_argument("--window-threshold", type=float, default=0.25, help="Similarity threshold for temporal pool graph clustering")
    parser.add_argument("--dbscan-eps", type=float, default=0.27, help="Token DBSCAN-like similarity threshold")
    parser.add_argument("--dbscan-min-samples", type=int, default=2, help="Token DBSCAN-like min samples including the point")
    parser.add_argument("--window-size", type=int, default=90, help="Max previous rows checked per temporal stream bucket")
    parser.add_argument("--max-pairwise", type=int, default=600, help="Max rows per bucket for pairwise similarity")
    add_common_args(parser)
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    records = load_messages(args.input)
    if args.limit and args.limit > 0:
        records = records[: args.limit]

    classifications = {record.id: classify_text(record.text, chat=record.chat, topic=record.topic) for record in records}
    if args.algorithm == "baseline_graph":
        clusters = cluster_messages(records, classifications, threshold=args.threshold, max_pairwise=args.max_pairwise)
    else:
        clusters = algorithm_specs()[args.algorithm].runner(records, classifications, args)

    class_counts = Counter(classification.primary_class for classification in classifications.values())
    route_counts = Counter(classification.material_route for classification in classifications.values())
    action_counts = Counter(classification.action for classification in classifications.values())
    review_clusters = [cluster for cluster in clusters if cluster["decision"] in {"REVIEW_CLUSTER_FOR_MATERIAL", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST"}]

    payload = {
        "generatedUtc": utc_now_iso(),
        "mode": "offline-batch-classification-clustering",
        "input": str(args.input),
        "count": len(records),
        "algorithm": args.algorithm,
        "threshold": args.threshold,
        "poolThreshold": args.pool_threshold,
        "windowThreshold": args.window_threshold,
        "dbscanEps": args.dbscan_eps,
        "classCounts": dict(class_counts.most_common()),
        "routeCounts": dict(route_counts.most_common()),
        "actionCounts": dict(action_counts.most_common()),
        "clusterCount": len(clusters),
        "reviewClusterCount": len(review_clusters),
        "items": [
            {
                "id": record.id,
                "chat": record.chat,
                "topic": record.topic,
                "createdAt": record.created_at,
                "preview": text_preview(record.text),
                "classification": classifications[record.id].to_dict(),
            }
            for record in records
        ],
        "clusters": clusters,
    }

    print(json.dumps({k: v for k, v in payload.items() if k not in {"items", "clusters"}}, ensure_ascii=False, indent=2))

    if args.output_json:
        write_json(args.output_json, payload)
    if args.output_md:
        md = build_markdown(payload)
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        args.output_md.write_text(md, encoding="utf-8")
    return 0


def build_markdown(payload: dict) -> str:
    lines: list[str] = []
    lines.append("# Offline Message Lab Report")
    lines.append("")
    lines.append(f"- Generated UTC: `{payload['generatedUtc']}`")
    lines.append(f"- Input: `{payload['input']}`")
    lines.append(f"- Messages: `{payload['count']}`")
    lines.append(f"- Algorithm: `{payload.get('algorithm', 'baseline_graph')}`")
    lines.append(f"- Clusters: `{payload['clusterCount']}`")
    lines.append(f"- Review clusters: `{payload['reviewClusterCount']}`")
    lines.append("")
    lines.append("## Class Counts")
    lines.append("")
    lines.append(markdown_table([[key, value] for key, value in payload["classCounts"].items()], ["Class", "Count"]))
    lines.append("")
    lines.append("## Route Counts")
    lines.append("")
    lines.append(markdown_table([[key, value] for key, value in payload["routeCounts"].items()], ["Route", "Count"]))
    lines.append("")
    lines.append("## Top Clusters")
    lines.append("")
    cluster_rows = []
    for cluster in payload["clusters"][:40]:
        cluster_rows.append(
            [
                cluster["id"],
                cluster["size"],
                cluster["decision"],
                cluster["qualityScore"],
                ", ".join(cluster["classes"].keys()),
                ", ".join(cluster["sourceChats"].keys()),
                ", ".join(cluster["sampleMessageIds"][:8]),
            ]
        )
    lines.append(markdown_table(cluster_rows, ["Cluster", "Size", "Decision", "Score", "Classes", "Chats", "Sample IDs"]))
    lines.append("")
    lines.append("## Review Cluster Samples")
    lines.append("")
    review_clusters = [cluster for cluster in payload["clusters"] if cluster["decision"] != "NO_MATERIAL"]
    if not review_clusters:
        lines.append("No review clusters found.")
        lines.append("")
    for cluster in review_clusters[:30]:
        lines.append(f"### {cluster['id']} · {cluster['decision']} · score {cluster['qualityScore']}")
        lines.append("")
        rows = [
            [sample["id"], sample["chat"], sample["topic"], sample["class"], sample["route"], sample["preview"]]
            for sample in cluster["samples"][:20]
        ]
        lines.append(markdown_table(rows, ["ID", "Chat", "Topic", "Class", "Route", "Preview"]))
        lines.append("")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    raise SystemExit(main())
