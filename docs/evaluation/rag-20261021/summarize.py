"""Aggregate reviewed judgments; never infer answer correctness from HTTP or citation IDs."""
import argparse
import csv
import datetime as dt
import hashlib
import json
from pathlib import Path
import statistics

parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent)
args = parser.parse_args()
P = args.output.resolve()
load = lambda name: json.loads((P / name).read_text(encoding="utf-8"))
cfg, corpus, dataset, retrieval, review = [load(n) for n in
    ("config.json", "corpus.json", "dataset.json", "retrieval.json", "review.json")]
raw = {r["id"]: r for r in map(json.loads, (P / "raw-results.jsonl").read_text(encoding="utf-8").splitlines())}
judgments = {r["id"]: r for r in review["records"]}
assert len(raw) == len(dataset) == len(judgments) == 50
assert {q["id"] for q in dataset} == set(raw) == set(judgments)
assert hashlib.sha256((P / "dataset.json").read_bytes()).hexdigest() == cfg["dataset_sha256"]
assert hashlib.sha256((P / "raw-results.jsonl").read_bytes()).hexdigest() == cfg["raw_results_sha256"]
assert all(load("integrity-checks.json")[k] for k in (
    "dataset_hash_unchanged", "raw_hash_unchanged", "all_returned_sources_match_snapshot",
    "all_returned_citations_in_independent_replay", "corpus_unchanged_after_run"))


def fraction(n, d):
    return {"numerator": n, "denominator": d, "percent": round(100*n/d, 2) if d else None}


rows = []
for q in dataset:
    r, v, j = raw[q["id"]], retrieval[q["id"]], judgments[q["id"]]
    data = r.get("body", {}).get("data") or {}
    ids = {h["metadata"]["chunk_id"] for h in v["candidates"]}
    hits = sum(bool(ids.intersection(group)) for group in q["gold_evidence_groups"])
    total = len(q["gold_evidence_groups"])
    rows.append({**q, **j, "http_status": r["http_status"], "business_code": r.get("body", {}).get("code"),
        "answer": data.get("answer", ""), "sources": data.get("sources", []),
        "latency_ms": r["latency_ms"], "candidate_count": len(ids),
        "retrieved_chunk_ids": sorted(ids), "evidence_groups_hit": hits, "evidence_groups_total": total,
        "retrieval_hit": hits > 0 if total else None,
        "retrieval_complete": hits == total if total else None,
        "recall_at_2": hits / total if total else None,
        "refused": data.get("answer", "").strip() == "当前检索资料不足以回答该问题。"})

answerable = [r for r in rows if r["answerable"]]
unanswerable = [r for r in rows if not r["answerable"]]
latencies = sorted(r["latency_ms"] for r in rows)
category_metrics = {}
for category in dict.fromkeys(r["category"] for r in rows):
    group = [r for r in rows if r["category"] == category]
    category_metrics[category] = {"count": len(group), "correct": fraction(sum(r["correct"] for r in group), len(group)),
        "strict_complete": fraction(sum(r["strict_complete"] for r in group),len(group))}

summary = {
    "generated_at": dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
    "sample_size": len(rows), "answerable_count": len(answerable), "unanswerable_count": len(unanswerable),
    "http_success": fraction(sum(r["http_status"] == 200 for r in rows),len(rows)),
    "business_success": fraction(sum(r["business_code"] == 1 for r in rows),len(rows)),
    "hit_at_2_any_gold": fraction(sum(r["retrieval_hit"] for r in answerable),len(answerable)),
    "complete_evidence_hit_at_2": fraction(sum(r["retrieval_complete"] for r in answerable),len(answerable)),
    "macro_recall_at_2": {"mean": round(statistics.mean(r["recall_at_2"] for r in answerable),6),
        "percent": round(100*statistics.mean(r["recall_at_2"] for r in answerable),2),"question_count": len(answerable)},
    "micro_evidence_recall_at_2": fraction(sum(r["evidence_groups_hit"] for r in answerable),sum(r["evidence_groups_total"] for r in answerable)),
    "answer_correctness": fraction(sum(r["correct"] for r in answerable),len(answerable)),
    "strict_expected_point_completeness": fraction(sum(r["strict_complete"] for r in answerable),len(answerable)),
    "unanswerable_refusal": fraction(sum(r["refused"] and r["correct"] for r in unanswerable),len(unanswerable)),
    "false_refusal_on_answerable": fraction(sum(r["refused"] for r in answerable),len(answerable)),
    "overall_question_pass": fraction(sum(r["correct"] for r in rows),len(rows)),
    "citation_id_validity": fraction(sum(bool(r["sources"]) for r in rows),sum(not r["refused"] for r in rows)),
    "citation_edges": load("integrity-checks.json")["citation_edges"],
    "latency_ms": {"mean":round(statistics.mean(latencies),2), "p50":round(statistics.median(latencies),2),
        "p95_nearest_rank":latencies[47], "min":latencies[0], "max":latencies[-1]},
    "category_metrics": category_metrics,
    "incorrect_question_ids": [r["id"] for r in answerable if not r["correct"]],
    "strict_incomplete_ids": [r["id"] for r in answerable if not r["strict_complete"]],
    "reviewer":review["reviewer"], "annotation_issue":review["annotation_issue"],
    "scope_limits": [
        "Single locally authored 50-question set over 4 documents/18 chunks; not a general or production benchmark.",
        "Retrieval candidates independently replayed with same model/dimension/index/filter/threshold; not instrumented inside the live JVM.",
        "All 52 returned source edges match the corpus and independently retrieved candidates, but this is not semantic support correctness.",
        "Question-level semantic correctness reviewed by Codex, without independent human review; strict completeness is reported separately.",
        "Standalone RAG endpoint only; no SSE tool-routing or multi-turn memory evaluation.",
        "One configuration/run, no before/after optimization claim; latency measured with concurrency 2, not a load test.",
        "Rules are source-derived snapshot versions from 2026-10-09; statistics boundary questions refer to those rules, not later business-comparison changes."
    ]
}
(P / "summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
(P / "scored-results.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
with (P / "results.csv").open("w",encoding="utf-8-sig",newline="") as f:
    fields = ["id","category","question","answerable","retrieval_complete","evidence_groups_hit","evidence_groups_total",
        "correct","strict_complete","refused","latency_ms","answer","reason"]
    w=csv.DictWriter(f,fieldnames=fields,extrasaction="ignore");w.writeheader();w.writerows(rows)

cases = ["# RAG 50题逐题复核", "", "评分依据为冻结规则，评审者为 Codex，未经过独立人工复核。原始响应见 raw-results.jsonl。", ""]
for r in rows:
    cases.extend([f'## {r["id"]} {r["question"]}',"",
        f'- 类型：{r["category"]}；问题级正确：{r["correct"]}；严格要点完整：{r["strict_complete"]}',
        f'- 必要证据覆盖：{r["evidence_groups_hit"]}/{r["evidence_groups_total"]}；耗时：{r["latency_ms"]} ms',
        '- 预设要点：'+'；'.join(r['expected_points']),
        '- Top-2 候选：'+'；'.join(h['metadata']['section_title']+f' ({h["distance"]:.4f})' for h in retrieval[r['id']]['candidates']),
        '', '**实际回答：**', '',r['answer'],'','**复核：** '+r['reason'],''])
(P/'cases.md').write_text('\n'.join(cases),encoding='utf-8')

fmt=lambda name:f'{summary[name]["numerator"]}/{summary[name]["denominator"]}（{summary[name]["percent"]}%）'
report=f'''# 万众享 RAG 质量评测报告

评测时间：{cfg['started_at']} 至 {cfg['finished_at']}。

## 结论

本轮固定题集共 50 题，其中 40 题资料内可回答、10 题资料不足。可回答题的问题级回答正确率为 **{fmt('answer_correctness')}**；Top-2 完整必要依据命中率为 **{fmt('complete_evidence_hit_at_2')}**；无答案题正确拒答为 **{fmt('unanswerable_refusal')}**。

这些数字是本地小型固定集的单次结果，由 Codex 逐题进行语义复核，未经过独立人工复核，不代表生产环境或通用准确率，也不是优化前后的提升比例。

## 条件与样本

- 调用本机已启动的 `POST /admin/ai/rag/answer`；服务未重启，业务实现未修改，索引和目录未改写。
- 规则范围：4 份源码说明，18 个生效片段。资料适用版本、完整文本、元数据见 `corpus.json`；文件校验值见 `config.json`。
- 20 题直接问题、10 题同义改写、10 题多片段问题、10 题无答案问题。题目、标准依据与预设要点在首次答题前写入 `dataset.json`，运行后校验哈希未变。
- Embedding：`{cfg['retrieval']['embedding_model']}`、{cfg['retrieval']['dimensions']} 维；Milvus：COSINE、FLAT、Top-2、阈值 0.5，按 `(document_id, version)` 生效组合过滤。
- 生成：运行中服务的 DeepSeek ChatClient，温度 0.0；调用独立规则接口，不评估 SSE 工具选择、多轮记忆或实时经营查询。
- 评测客户端：{cfg['client_os']}，逻辑处理器 {cfg['client_cpu_logical']}；Python {cfg['client_python']}；HTTP 并发 2，单次超时 180 秒，无自动重试。
- 耗时属于本轮端到端观测，含网络及云模型开销，不是压测容量或首字延迟。

## 指标定义与结果

| 指标 | 定义 | 结果 |
| --- | --- | --- |
| 任一依据命中 Hit@2 | 可回答问题中，前2个候选至少含一组标准依据 | {fmt('hit_at_2_any_gold')} |
| 完整依据命中@2 | 可回答问题中，前2个候选覆盖全部必要依据组 | {fmt('complete_evidence_hit_at_2')} |
| Recall@2（逐题宏平均） | 逐题计算已找回必要依据组数/所需依据组数，再对40题平均 | {summary['macro_recall_at_2']['percent']}% |
| 必要依据微平均召回 | 所有题找回的必要依据组总数/必要依据组总数 | {fmt('micro_evidence_recall_at_2')} |
| 回答正确率 | 对40道可回答题按题意与冻结规则复核，误答/误拒答均不通过 | {fmt('answer_correctness')} |
| 预设要点严格完整率 | 要求覆盖题集预写的全部要点，含标注中超出题干的要求 | {fmt('strict_expected_point_completeness')} |
| 无答案正确拒答率 | 10道无依据题明确拒答且不编造具体事实/规则 | {fmt('unanswerable_refusal')} |
| 可回答题误拒答率 | 有标准依据的题被返回资料不足 | {fmt('false_refusal_on_answerable')} |
| 总体问题通过率 | 答对可回答题或正确拒答无答案题/全部50题 | {fmt('overall_question_pass')} |
| HTTP 成功率 | HTTP 200/全部请求 | {fmt('http_success')} |
| 业务返回成功率 | 返回 code=1/全部请求，不能据此认定回答正确 | {fmt('business_success')} |

### 分组结果

| 分组 | 题数 | 问题级正确 | 严格要点完整 |
| --- | --- | --- | --- |
'''
labels={'direct':'直接问题','paraphrase':'同义改写','multi_evidence':'多片段问题','unanswerable':'无答案问题'}
for category,m in category_metrics.items():
    report+=f'| {labels[category]} | {m["count"]} | {m["correct"]["numerator"]}/{m["count"]}（{m["correct"]["percent"]}%） | {m["strict_complete"]["numerator"]}/{m["count"]}（{m["strict_complete"]["percent"]}%） |\n'
report+=f'''
### 延迟与来源检查

- 平均响应：{summary['latency_ms']['mean']} ms；P50：{summary['latency_ms']['p50']} ms；P95（最近秩法）：{summary['latency_ms']['p95_nearest_rank']} ms。
- 39 条非拒答响应共返回 52 条引用边，全部能映射到冻结生效片段，正文和版本一致，也都属于独立重放检索的 Top-2 候选。
- “引用编号合法”不等于语义正确：Q31、Q39 引用了合法编号，但结论仍然错误。
- 10 道无答案题中，2 题没有阈值内候选，由服务直接拒答；其余 8 题有候选但不足以回答，由模型拒答。不能把10题都说成模型拒答能力。

## 三个实际失败案例

1. **Q31 用户取消与管理端拒单比较**：Top-2 漏掉用户取消章节。回答错误声称二者都仅允许待接单，后面又称用户规则无法确认。正确区别是用户可取消待付款或待接单，管理端拒单要求待接单。
2. **Q39 商品停售与套餐联动**：Top-2 返回状态值和直接修改套餐的章节，漏掉商品停售联动章节。模型混淆两条业务流程，错误否认已有的联动停售。
3. **Q40 接单与派送校验比较**：Top-2 没有接单章节，模型返回资料不足。规则库里存在答案，因此属于漏检导致的误拒答。

## 一项标注问题

Q32 问的是两个方法各自要求的原状态，回答正确给出“已接单/派送中”。但预写标注还要求完成后的状态及实际送达时间，题干未询问这些内容。因此问题级正确计通过、预设要点严格完整计不通过。原始题集保留不改，同时报告两个口径，不能把90%的严格完整率说成92.5%。后续独立评测应在答题前让题干与评分要点一一对齐。

## 解释边界

- 检索候选没有通过新增生产接口取得，而是以相同输入、Embedding 模型/维度、Milvus 集合/过滤/阈值独立重放。全部52条真实返回引用均包含在重放候选中，但不能将重放称为应用内部埋点结果。
- Hit@2 的100%只要求找到任意一组依据，会掩盖多片段题漏检。简历优先用完整依据命中率92.5%，并保留题集规模。
- 规则资料是2026-10-09源码快照。统计边界题按资料中的旧实现说明评分，不替代后来经营区间对比接口的左闭右开口径。未提供的自动取消时长，即使其他业务代码存在，也不能当作本接口本轮资料已有的答案。
- 本轮没有新增混合检索、重排、多智能体或优化前后对照，不填写任何“提升到”的数字。

## 可用于简历的评测表述

基于50道固定问题评估规则问答，覆盖直接提问、同义改写、多片段问题及无答案场景；本地测试中，Top-2完整依据命中率为92.5%，可回答问题正确率为92.5%，10道无答案问题均正确拒答，并保留原始响应与逐题评分记录。

如只使用完全按预写要点判分的口径，可写“预设答案要点完整通过率90%”，并保留Q32标注说明。语义正确率由Codex复核，正式投递前建议本人复核 `cases.md`，尤其三个失败例和Q32。

## 文件与复现

- `dataset.json`：固定题集、预设要点、必要依据组。
- `corpus.json`、`config.json`：生效资料快照、模型与检索参数、题集/结果哈希。
- `retrieval.json`：每题原始Top-2候选、阈值过滤后的候选及相似度。
- `raw-results.jsonl`：50条真实HTTP响应及耗时，不含JWT/密钥。
- `review.json`：逐题语义判断、严格完整性判断及原因。
- `scored-results.json`、`results.csv`、`cases.md`：逐题汇总与可读复核记录。
- `summary.json`：机器可读指标；`check_integrity.py` / `integrity-checks.json`：目录/正文未变、来源及哈希一致性检查。
- `evaluate.py`：只读评测脚本；`summarize.py`：仅聚合已复核判分，不以关键词或HTTP成功自动判定答案正确。

从仓库根目录运行，Python需安装 `PyYAML` 和 `PyMySQL`，终端需有 `DASHSCOPE_API_KEY`；数据库和JWT配置从本机配置读取，只在内存使用。`--base` 默认本机8080。复测新建目录，保留本轮文件：

```powershell
$py = 'C:/Users/ROG/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$suite = 'docs/evaluation/rag-20261021'
$next = 'docs/evaluation/rag-next-run'
& $py -X utf8 "$suite/evaluate.py" prepare --output $next
Copy-Item -LiteralPath "$suite/dataset.json" -Destination "$next/dataset.json"
& $py -X utf8 "$suite/evaluate.py" run --output $next
```

完成逐题复核并把新判分写入新目录的 `review.json` 后，检查和汇总：

```powershell
& $py -X utf8 "$suite/check_integrity.py" --output $next
& $py -X utf8 "$suite/summarize.py" --output $next
```

复测必须重新逐题复核答案，不能复制本轮判分冒充新判分。若资料或标准依据已变，应先于请求模型修订并冻结题集，再执行。脚本拒绝覆盖已有原始响应，不自动重试或筛掉失败题。
'''
(P/'REPORT.md').write_text(report,encoding='utf-8')
print(json.dumps({k:summary[k] for k in ['sample_size','complete_evidence_hit_at_2','macro_recall_at_2','answer_correctness','strict_expected_point_completeness','unanswerable_refusal','overall_question_pass','latency_ms']},ensure_ascii=False,indent=2))
