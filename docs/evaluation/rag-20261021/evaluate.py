"""Read-only evaluation of the running RAG API and its configured retrieval.

Dependencies: PyYAML, PyMySQL. Credentials stay in memory and are never exported.
Run prepare first, then run, then score after reviewing raw answers.
"""
from __future__ import annotations

import argparse
import base64
import concurrent.futures
import datetime as dt
import hashlib
import hmac
import json
import os
from pathlib import Path
import platform
import re
import time
import urllib.error
import urllib.request

import pymysql
import yaml

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
REFUSAL = "当前检索资料不足以回答该问题。"


def write_json(name, data):
    (HERE / name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def read_json(name):
    return json.loads((HERE / name).read_text(encoding="utf-8"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


class Runtime:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.config = {}
        def merge(x, y):
            for k, v in y.items():
                if isinstance(v, dict) and isinstance(x.get(k), dict):
                    merge(x[k], v)
                else:
                    x[k] = v
        resources = ROOT / "platform-server/src/main/resources"
        default_profile = "local" if (resources / "application-local.yml").is_file() else "demo"
        profile = os.environ.get("WZX_EVAL_PROFILE", default_profile)
        if profile not in ("local", "demo"):
            raise ValueError("WZX_EVAL_PROFILE must be local or demo")
        for name in ("application.yml", f"application-{profile}.yml"):
            merge(self.config, yaml.safe_load((ROOT / "platform-server/src/main/resources" / name).read_text(encoding="utf-8")))
        self.milvus = "http://%s:%s" % (
            self.get("spring.ai.vectorstore.milvus.client.host"),
            self.get("spring.ai.vectorstore.milvus.client.port"))
        self.collection = self.get("spring.ai.vectorstore.milvus.collection-name")
        self.database = self.get("spring.ai.vectorstore.milvus.database-name", "default")
        self.embedding_key = self.get("spring.ai.openai.embedding.api-key")
        self.embedding_model = self.get("spring.ai.openai.embedding.options.model")
        self.dimensions = self.get("spring.ai.openai.embedding.options.dimensions")
        self.embedding_url = self.get("spring.ai.openai.embedding.base-url").rstrip("/") + self.get("spring.ai.openai.embedding.embeddings-path")
        self.token = None

    def resolve(self, v):
        if not isinstance(v, str):
            return v
        def sub(m):
            key, _, default = m.group(1).partition(":")
            return os.environ.get(key, str(self.get(key, default)))
        for _ in range(15):
            nv = re.sub(r"\$\{([^{}]+)\}", sub, v)
            if nv == v:
                return nv
            v = nv
        raise ValueError("Unresolved config")

    def get(self, path, default=None):
        v = self.config
        try:
            for key in path.split("."):
                v = v[key]
        except (KeyError, TypeError):
            return default
        return self.resolve(v)

    def connect(self):
        return pymysql.connect(host=self.get("wzx.datasource.host"),
            port=int(self.get("wzx.datasource.port")), user=self.get("wzx.datasource.username"),
            password=self.get("wzx.datasource.password"), database=self.get("wzx.datasource.database"),
            cursorclass=pymysql.cursors.DictCursor, connect_timeout=5)

    def catalog(self):
        with self.connect() as conn, conn.cursor() as c:
            c.execute("SELECT d.document_id,d.document_title,d.active_version,v.source_path,v.chunk_count "
                "FROM ai_rule_document d JOIN ai_rule_document_version v "
                "ON d.document_id=v.document_id AND d.active_version=v.version "
                "WHERE d.enabled=1 AND v.status='READY' ORDER BY d.document_id")
            return c.fetchall()

    def authenticate(self):
        # Same short-lived local test credential mechanism as RagRuntimeEvidenceSupport.
        with self.connect() as conn, conn.cursor() as c:
            c.execute("SELECT id FROM employee WHERE status=1 ORDER BY id LIMIT 1")
            employee_id = c.fetchone()["id"]
        b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
        msg = b".".join([b64(b'{"alg":"HS256"}'),
            b64(json.dumps({"empId": employee_id, "exp": int(time.time()) + 7200}).encode())])
        secret = self.get("wzx.jwt.admin-secret-key")
        self.token = (msg + b"." + b64(hmac.new(hashlib.sha256(secret.encode()).digest(), msg, hashlib.sha256).digest())).decode()
        response = self.http(self.base + "/admin/ai/rag/ready", auth=True)
        if response["http_status"] != 200 or response["body"].get("data") is not True:
            raise RuntimeError("Running service authentication or RAG readiness check failed")

    def http(self, url, body=None, auth=False, bearer=None, timeout=180):
        headers = {"Content-Type": "application/json"}
        if auth:
            headers[self.get("wzx.jwt.admin-token-name")] = self.token
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        req = urllib.request.Request(url, data=None if body is None else json.dumps(body, ensure_ascii=False).encode(), headers=headers)
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                result = {"http_status": r.status, "body": json.load(r)}
        except urllib.error.HTTPError as e:
            # Do not export server error bodies: they may contain local connection details.
            result = {"http_status": e.code, "error_type": type(e).__name__}
        except Exception as e:
            result = {"http_status": None, "error_type": type(e).__name__}
        result["latency_ms"] = round((time.perf_counter() - start) * 1000, 2)
        return result

    def milvus_call(self, endpoint, body):
        response = self.http(self.milvus + "/v2/vectordb/" + endpoint,
            {"collectionName": self.collection, "dbName": self.database, **body})
        if response.get("body", {}).get("code") != 0:
            raise RuntimeError("Milvus evaluation request failed: " + endpoint)
        data = response["body"]["data"]
        if isinstance(data, list):
            for row in data:
                if isinstance(row, dict) and isinstance(row.get("metadata"), str):
                    row["metadata"] = json.loads(row["metadata"])
        return data


def active_filter(catalog):
    return " or ".join('(metadata["document_id"] == %s and metadata["version"] == %s)' %
        (json.dumps(row["document_id"]), json.dumps(row["active_version"])) for row in catalog)


def prepare(runtime):
    catalog = runtime.catalog()
    corpus = runtime.milvus_call("entities/query", {"filter": active_filter(catalog),
        "outputFields": ["doc_id", "content", "metadata"], "limit": 1000})
    corpus.sort(key=lambda d: d["metadata"]["chunk_id"])
    expected = sum(r["chunk_count"] for r in catalog)
    if len(corpus) != expected:
        raise RuntimeError("Catalog chunk count differs from active Milvus snapshot")
    write_json("corpus.json", {"catalog": catalog, "chunks": corpus})
    schema = runtime.milvus_call("collections/describe", {})
    config = {
        "prepared_at": dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
        "application_url": runtime.base, "endpoint": "/admin/ai/rag/answer",
        "evaluation_scope": "standalone RAG endpoint; not SSE tools or conversation memory",
        "retrieval": {"embedding_model": runtime.embedding_model, "dimensions": runtime.dimensions,
            "top_k": 2, "similarity_threshold": 0.5, "metric": "COSINE", "index": "FLAT",
            "active_filter": active_filter(catalog),
            "method": "independent replay via same embedding model and Milvus REST; API citations are checked against replay candidates"},
        "generation": {"model": "deepseek-chat", "temperature": 0.0, "pipeline": "running Spring RagAnswerService"},
        "corpus_file_sha256": sha((HERE / "corpus.json").read_bytes()),
        "corpus_count": len(corpus), "document_count": len(catalog),
        "milvus_schema": schema, "client_os": platform.platform(), "client_cpu_logical": os.cpu_count(),
        "client_python": platform.python_version(), "concurrency": 2, "timeout_seconds": 180,
        "automatic_retries": 0, "dataset_origin": "locally authored from frozen active rules; not an external benchmark",
        "grading": "prewritten expected points; semantic review by Codex, no independent human review",
    }
    write_json("config.json", config)
    runtime.authenticate()
    print(json.dumps({"prepared": True, "documents": len(catalog), "chunks": len(corpus)}, ensure_ascii=False), flush=True)


def run(runtime):
    config = read_json("config.json")
    dataset = read_json("dataset.json")
    if runtime.catalog() != read_json("corpus.json")["catalog"]:
        raise RuntimeError("Active catalog changed after freezing dataset")
    dataset_hash = sha((HERE / "dataset.json").read_bytes())
    if (HERE / "raw-results.jsonl").exists():
        raise RuntimeError("Existing raw results must be preserved; choose a new run directory")
    config["dataset_sha256"] = dataset_hash
    config["started_at"] = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
    write_json("config.json", config)
    runtime.authenticate()
    # Batch query embeddings, no document re-embedding or index changes.
    vectors, batches = [], []
    for offset in range(0, len(dataset), 10):
        response = runtime.http(runtime.embedding_url,
            {"model": runtime.embedding_model, "input": [q["question"] for q in dataset[offset:offset+10]],
             "dimensions": runtime.dimensions, "encoding_format": "float"}, bearer=runtime.embedding_key)
        if response.get("http_status") != 200:
            raise RuntimeError("Query embedding batch failed")
        vectors.extend(sorted(response["body"]["data"], key=lambda d: d["index"]))
        batches.append({"count": len(response["body"]["data"]), "latency_ms": response["latency_ms"],
            "usage": response["body"].get("usage")})
    if len(vectors) != len(dataset) or any(len(v["embedding"]) != runtime.dimensions for v in vectors):
        raise RuntimeError("Unexpected embedding count or dimension")
    config["embedding_batches"] = batches
    write_json("config.json", config)
    retrieval = {}
    for q, vector in zip(dataset, vectors):
        start = time.perf_counter()
        hits = runtime.milvus_call("entities/search", {
            "data": [vector["embedding"]], "annsField": "embedding", "limit": 2,
            "filter": config["retrieval"]["active_filter"], "outputFields": ["doc_id", "content", "metadata"],
            "searchParams": {"metricType": "COSINE", "params": {}}})
        retrieval[q["id"]] = {"raw_top_k": hits,
            "candidates": [h for h in hits if h["distance"] >= config["retrieval"]["similarity_threshold"]],
            "milvus_latency_ms": round((time.perf_counter() - start) * 1000, 2)}
    write_json("retrieval.json", retrieval)
    print("Query embeddings and all retrieval candidates saved; starting 50 live answer requests.", flush=True)

    def answer(q):
        result = runtime.http(runtime.base + config["endpoint"], {"message": q["question"]}, auth=True)
        return {"id": q["id"], "question": q["question"], **result}

    start = time.perf_counter()
    with (HERE / "raw-results.jsonl").open("a", encoding="utf-8") as out:
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = {pool.submit(answer, q): q for q in dataset}
            for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
                result = future.result()
                out.write(json.dumps(result, ensure_ascii=False) + "\n")
                out.flush()
                print(f'{i:02d}/{len(dataset)} {result["id"]} HTTP={result["http_status"]} ms={result["latency_ms"]}', flush=True)
    config["finished_at"] = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
    config["answer_phase_wall_seconds"] = round(time.perf_counter() - start, 2)
    config["catalog_unchanged_after_run"] = runtime.catalog() == read_json("corpus.json")["catalog"]
    if sha((HERE / "dataset.json").read_bytes()) != dataset_hash:
        raise RuntimeError("Dataset changed during run")
    config["raw_results_sha256"] = sha((HERE / "raw-results.jsonl").read_bytes())
    write_json("config.json", config)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["prepare", "run"])
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--output", type=Path, help="New run directory; copy the fixed dataset there before run")
    args = parser.parse_args()
    if args.output:
        HERE = args.output.resolve()
        HERE.mkdir(parents=True, exist_ok=True)
    runtime = Runtime(args.base)
    {"prepare": prepare, "run": run}[args.phase](runtime)
