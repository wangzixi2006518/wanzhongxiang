"""Validate a completed run without issuing new embedding or chat requests."""
import argparse
import json
from pathlib import Path

import evaluate

parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
parser.add_argument("--base", default="http://127.0.0.1:8080")
args = parser.parse_args()
evaluate.HERE = args.output.resolve()
cfg = evaluate.read_json("config.json")
corpus = evaluate.read_json("corpus.json")
dataset = evaluate.read_json("dataset.json")
retrieval = evaluate.read_json("retrieval.json")
raw = [json.loads(line) for line in (evaluate.HERE / "raw-results.jsonl").read_text(encoding="utf-8").splitlines()]
runtime = evaluate.Runtime(args.base)
current = runtime.milvus_call("entities/query", {"filter": cfg["retrieval"]["active_filter"],
    "outputFields": ["doc_id", "content", "metadata"], "limit": 1000})
current.sort(key=lambda row: row["metadata"]["chunk_id"])
by_id = {c["metadata"]["chunk_id"]: c for c in corpus["chunks"]}
source_edges = 0
source_valid = True
in_replay = True
for row in raw:
    candidates = {h["metadata"]["chunk_id"] for h in retrieval[row["id"]]["candidates"]}
    for source in (row.get("body", {}).get("data") or {}).get("sources", []):
        source_edges += 1
        chunk = by_id.get(source["chunkId"])
        source_valid &= chunk is not None and source["text"] == chunk["content"] and source["version"] == chunk["metadata"]["version"]
        in_replay &= source["chunkId"] in candidates
checks = {
    "dataset_hash_unchanged": evaluate.sha((evaluate.HERE / "dataset.json").read_bytes()) == cfg["dataset_sha256"],
    "raw_hash_unchanged": evaluate.sha((evaluate.HERE / "raw-results.jsonl").read_bytes()) == cfg["raw_results_sha256"],
    "question_count": len(dataset), "response_count": len(raw), "unique_response_ids": len({r["id"] for r in raw}),
    "question_ids_match": {q["id"] for q in dataset} == {r["id"] for r in raw},
    "all_http_200": all(r["http_status"] == 200 for r in raw),
    "all_business_code_1": all(r.get("body", {}).get("code") == 1 for r in raw),
    "citation_edges": source_edges, "all_returned_sources_match_snapshot": source_valid,
    "all_returned_citations_in_independent_replay": in_replay,
    "corpus_unchanged_after_run": current == corpus["chunks"],
    "catalog_unchanged_after_run": runtime.catalog() == corpus["catalog"],
}
evaluate.write_json("integrity-checks.json", checks)
sources = ["platform-server/src/main/java/com/wanzhongxiang/rag/RagRetrievalService.java",
    "platform-server/src/main/java/com/wanzhongxiang/rag/RagAnswerService.java",
    "platform-common/src/main/java/com/wanzhongxiang/constant/AiPromptConstant.java"]
hash_file = evaluate.HERE / "source-hashes.json"
if not hash_file.exists():
    evaluate.write_json("source-hashes.json", {f: evaluate.sha((evaluate.ROOT / f).read_bytes()) for f in sources})
assert len(dataset) == len(raw) == len({r["id"] for r in raw}) == 50
assert all(checks[k] for k in checks if isinstance(checks[k], bool)), "Integrity check failed; inspect report"
print(json.dumps(checks, ensure_ascii=False, indent=2))
