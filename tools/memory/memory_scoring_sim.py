#!/usr/bin/env python3
"""
Memory scoring reproduction and simulation tool.

This script reproduces the current memory candidate scoring logic:
- keyword match score
- reverse containment score
- semantic score
- graph propagation score
- min-score filtering and top-k truncation

It also provides:
- Monte Carlo simulation for keyword-count fairness analysis
- multi-dimensional plotting for kw+rev threshold behavior
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
import re
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


DEFAULT_RRF_K = 60.0
DEFAULT_MIN_SCORE_THRESHOLD = 0.025
DEFAULT_GRAPH_BASE_COEF = 0.03
DEFAULT_GRAPH_SEED_TOP_N = 10
DEFAULT_MAX_LEXICAL_FRAGMENTS = 32
DEFAULT_KEYWORD_COVERAGE_BOOST = 0.6
DEFAULT_ISOLATED_TITLE = "技术操作：激活web包以执行发专栏任务"
DEFAULT_ISOLATED_CONTENT = (
    "用户要求激活web包来执行发专栏的网页操作。AI已响应并开始执行激活操作。"
)

try:
    import jieba  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    jieba = None


class ScoreMode(str, Enum):
    BALANCED = "BALANCED"
    KEYWORD_FIRST = "KEYWORD_FIRST"
    SEMANTIC_FIRST = "SEMANTIC_FIRST"


MODE_MULTIPLIERS: Dict[ScoreMode, Tuple[float, float, float]] = {
    ScoreMode.BALANCED: (1.0, 1.0, 1.0),
    ScoreMode.KEYWORD_FIRST: (1.3, 0.8, 0.9),
    ScoreMode.SEMANTIC_FIRST: (0.8, 1.3, 1.1),
}


@dataclass
class MemoryRecord:
    id: int
    title: str
    content: str
    importance: float = 0.5
    embedding: Optional[List[float]] = None


@dataclass
class LinkRecord:
    source_id: int
    target_id: int
    weight: float = 1.0


@dataclass
class ScoringConfig:
    semantic_threshold: float = 0.4
    score_mode: ScoreMode = ScoreMode.BALANCED
    keyword_weight: float = 10.0
    semantic_weight: float = 0.5
    edge_weight: float = 0.4
    rrf_k: float = DEFAULT_RRF_K
    min_score_threshold: float = DEFAULT_MIN_SCORE_THRESHOLD
    graph_base_coef: float = DEFAULT_GRAPH_BASE_COEF
    graph_seed_top_n: int = DEFAULT_GRAPH_SEED_TOP_N
    semantic_sqrt_norm: bool = True

    def normalized(self) -> "ScoringConfig":
        threshold = max(0.0, min(1.0, float(self.semantic_threshold)))
        keyword_weight = max(0.0, float(self.keyword_weight))
        semantic_weight = max(0.0, float(self.semantic_weight))
        edge_weight = max(0.0, float(self.edge_weight))
        rrf_k = max(1.0, float(self.rrf_k))
        min_score_threshold = max(0.0, float(self.min_score_threshold))
        graph_base_coef = max(0.0, float(self.graph_base_coef))
        graph_seed_top_n = max(1, int(self.graph_seed_top_n))
        return ScoringConfig(
            semantic_threshold=threshold,
            score_mode=self.score_mode,
            keyword_weight=keyword_weight,
            semantic_weight=semantic_weight,
            edge_weight=edge_weight,
            rrf_k=rrf_k,
            min_score_threshold=min_score_threshold,
            graph_base_coef=graph_base_coef,
            graph_seed_top_n=graph_seed_top_n,
            semantic_sqrt_norm=self.semantic_sqrt_norm,
        )


@dataclass
class ScoreBreakdown:
    keyword: float = 0.0
    reverse: float = 0.0
    semantic: float = 0.0
    graph: float = 0.0

    @property
    def total(self) -> float:
        return self.keyword + self.reverse + self.semantic + self.graph


def _contains_ignore_case(haystack: str, needle: str) -> bool:
    return needle.lower() in haystack.lower()


def tokenize_query(query: str) -> List[str]:
    query = query.strip()
    if not query:
        return []
    if "|" in query:
        return [part.strip() for part in query.split("|") if part.strip()]
    return [part.strip() for part in re.split(r"\s+", query) if part.strip()]


def extract_core_question_text(raw_query: str) -> str:
    compact = raw_query.replace("\r\n", "\n")

    cn_match = re.search(
        r"(?s)问题\s*[：:]\s*(.+?)(?:\n\s*解决方案\s*[：:]|\Z)",
        compact,
    )
    en_match = re.search(
        r"(?s)Question\s*:\s*(.+?)(?:\n\s*Solution\s*:|\Z)",
        compact,
    )
    selected = ""
    if cn_match and cn_match.group(1).strip():
        selected = cn_match.group(1).strip()
    elif en_match and en_match.group(1).strip():
        selected = en_match.group(1).strip()
    else:
        selected = compact

    lines = []
    for line in selected.splitlines():
        stripped = line.lstrip()
        if stripped.startswith("历史记录:") or stripped.startswith("History:"):
            continue
        lines.append(line)

    cleaned = "\n".join(lines)
    cleaned = re.sub(r"(?is)<tool.*?>.*?</tool>", " ", cleaned)
    cleaned = re.sub(r"(?is)<tool_result.*?</tool_result>", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned[:500]


def build_candidate_search_query(query: str, solution: str) -> str:
    core_question = extract_core_question_text(query)
    fallback_question = re.sub(
        r"\s+",
        " ",
        re.sub(r"(?is)<tool_result.*?</tool_result>", " ", query),
    ).strip()[:800]

    selected_question = core_question if core_question else fallback_question
    if not selected_question:
        return solution[:300]

    concise_solution = re.sub(
        r"\s+",
        " ",
        re.sub(r"(?is)<tool_result.*?</tool_result>", " ", solution),
    ).strip()[:180]

    if concise_solution:
        return f"{selected_question}\n{concise_solution}"
    return selected_question


def expand_keyword_token(token: str) -> List[str]:
    normalized = token.strip().lower()
    if not normalized:
        return []
    merged: List[str] = [normalized]
    if jieba is not None:
        merged.extend(
            t.strip().lower()
            for t in jieba.cut(normalized)
            if t and len(t.strip()) >= 2
        )
    deduped: List[str] = []
    seen = set()
    for t in merged:
        if len(t) < 2:
            continue
        if t in seen:
            continue
        seen.add(t)
        deduped.append(t)
    return deduped


def build_lexical_fragments(query: str, keywords: List[str]) -> List[str]:
    merged: List[str] = []
    merged.extend(expand_keyword_token(query))
    for kw in keywords:
        merged.extend(expand_keyword_token(kw))
    deduped: List[str] = []
    seen = set()
    for token in merged:
        if token in seen:
            continue
        seen.add(token)
        deduped.append(token)
    deduped.sort(key=len, reverse=True)
    return deduped[:DEFAULT_MAX_LEXICAL_FRAGMENTS]


def cosine_similarity(left: List[float], right: List[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    dot = 0.0
    left_norm = 0.0
    right_norm = 0.0
    for l_value, r_value in zip(left, right):
        dot += l_value * r_value
        left_norm += l_value * l_value
        right_norm += r_value * r_value
    if left_norm <= 0.0 or right_norm <= 0.0:
        return 0.0
    return dot / (math.sqrt(left_norm) * math.sqrt(right_norm))


def _lookup_semantic_similarity(
    keyword: str,
    memory: MemoryRecord,
    semantic_similarity: Dict[str, Dict[str, float]],
    query_embeddings: Dict[str, List[float]],
) -> Optional[float]:
    exact_map = semantic_similarity.get(keyword)
    if exact_map is None:
        exact_map = semantic_similarity.get(keyword.lower())
    if exact_map is not None:
        value = exact_map.get(str(memory.id))
        if value is None:
            value = exact_map.get(memory.id)  # type: ignore[arg-type]
        if value is not None:
            return float(value)

    q_emb = query_embeddings.get(keyword)
    if q_emb is None:
        q_emb = query_embeddings.get(keyword.lower())
    if q_emb is not None and memory.embedding is not None:
        return cosine_similarity(q_emb, memory.embedding)
    return None


def score_memories(
    memories: List[MemoryRecord],
    links: List[LinkRecord],
    query: str,
    config: ScoringConfig,
    semantic_similarity: Optional[Dict[str, Dict[str, float]]] = None,
    query_embeddings: Optional[Dict[str, List[float]]] = None,
) -> Dict[str, Any]:
    cfg = config.normalized()
    semantic_similarity = semantic_similarity or {}
    query_embeddings = query_embeddings or {}

    keywords = tokenize_query(query)
    if not keywords:
        return {
            "query": query,
            "keywords": [],
            "lexical_fragments": [],
            "results": [],
            "filtered_count": 0,
            "raw_count": 0,
        }
    lexical_fragments = build_lexical_fragments(query, keywords)

    mode_kw_mul, mode_sem_mul, mode_edge_mul = MODE_MULTIPLIERS[cfg.score_mode]
    effective_kw_weight = cfg.keyword_weight * mode_kw_mul
    effective_sem_weight = cfg.semantic_weight * mode_sem_mul
    effective_edge_weight = cfg.edge_weight * mode_edge_mul
    semantic_keyword_norm_factor = (
        1.0 / math.sqrt(len(keywords))
        if cfg.semantic_sqrt_norm and len(keywords) > 0
        else 1.0
    )

    scores: Dict[int, float] = {}
    breakdown: Dict[int, ScoreBreakdown] = {}

    def add_score(memory_id: int, component: str, value: float) -> None:
        if memory_id not in breakdown:
            breakdown[memory_id] = ScoreBreakdown()
        current = breakdown[memory_id]
        if component == "keyword":
            current.keyword += value
        elif component == "reverse":
            current.reverse += value
        elif component == "semantic":
            current.semantic += value
        elif component == "graph":
            current.graph += value
        scores[memory_id] = scores.get(memory_id, 0.0) + value

    # 1) Keyword match (title contains lexical fragments)
    matched_fragments: Dict[int, set] = {}
    for memory in memories:
        hits = {
            frag
            for frag in lexical_fragments
            if _contains_ignore_case(memory.title, frag)
        }
        if hits:
            matched_fragments[memory.id] = hits

    keyword_results = sorted(
        (memory for memory in memories if memory.id in matched_fragments),
        key=lambda m: (
            len(matched_fragments.get(m.id, set())),
            m.importance,
        ),
        reverse=True,
    )
    total_fragments = max(1, len(lexical_fragments))
    for index, memory in enumerate(keyword_results, start=1):
        base_score = 1.0 / (cfg.rrf_k + index)
        coverage_ratio = len(matched_fragments.get(memory.id, set())) / total_fragments
        coverage_multiplier = 1.0 + DEFAULT_KEYWORD_COVERAGE_BOOST * coverage_ratio
        weighted = (
            base_score
            * memory.importance
            * effective_kw_weight
            * coverage_multiplier
        )
        add_score(memory.id, "keyword", weighted)

    # 2) Reverse containment: query contains memory title
    reverse_results = [
        memory for memory in memories if _contains_ignore_case(query, memory.title)
    ]
    for index, memory in enumerate(reverse_results, start=1):
        base_score = 1.0 / (cfg.rrf_k + index)
        weighted = base_score * memory.importance * effective_kw_weight
        add_score(memory.id, "reverse", weighted)

    # 3) Semantic score
    if effective_sem_weight > 0.0:
        for keyword in keywords:
            semantic_hits: List[Tuple[MemoryRecord, float]] = []
            for memory in memories:
                similarity = _lookup_semantic_similarity(
                    keyword=keyword,
                    memory=memory,
                    semantic_similarity=semantic_similarity,
                    query_embeddings=query_embeddings,
                )
                if similarity is None:
                    continue
                if similarity >= cfg.semantic_threshold:
                    semantic_hits.append((memory, similarity))

            semantic_hits.sort(key=lambda item: item[1], reverse=True)
            for index, (memory, similarity) in enumerate(semantic_hits, start=1):
                rank_score = 1.0 / (cfg.rrf_k + index)
                similarity_score = similarity * effective_sem_weight
                weighted = (
                    (rank_score * math.sqrt(memory.importance)) + similarity_score
                ) * semantic_keyword_norm_factor
                add_score(memory.id, "semantic", weighted)

    # 4) Graph propagation from top seed nodes
    if effective_edge_weight > 0.0 and scores:
        top_seed_ids = [
            memory_id
            for memory_id, _ in sorted(
                scores.items(), key=lambda item: item[1], reverse=True
            )[: cfg.graph_seed_top_n]
        ]
        outgoing: Dict[int, List[LinkRecord]] = {}
        incoming: Dict[int, List[LinkRecord]] = {}
        for link in links:
            outgoing.setdefault(link.source_id, []).append(link)
            incoming.setdefault(link.target_id, []).append(link)

        base_propagation = cfg.graph_base_coef * effective_edge_weight
        for source_id in top_seed_ids:
            source_score = scores.get(source_id, 0.0)
            if source_score <= 0.0:
                continue

            for link in outgoing.get(source_id, []):
                propagated = (
                    source_score * link.weight * effective_edge_weight
                    + base_propagation
                )
                add_score(link.target_id, "graph", propagated)

            for link in incoming.get(source_id, []):
                propagated = (
                    source_score * link.weight * effective_edge_weight
                    + base_propagation
                )
                add_score(link.source_id, "graph", propagated)

    memory_map = {memory.id: memory for memory in memories}
    filtered = [
        (memory_id, total)
        for memory_id, total in scores.items()
        if total >= cfg.min_score_threshold
    ]
    filtered.sort(key=lambda item: item[1], reverse=True)

    rows: List[Dict[str, Any]] = []
    for rank, (memory_id, total) in enumerate(filtered, start=1):
        memory = memory_map.get(memory_id)
        if memory is None:
            continue
        comp = breakdown.get(memory_id, ScoreBreakdown())
        rows.append(
            {
                "rank": rank,
                "memory_id": memory_id,
                "title": memory.title,
                "importance": memory.importance,
                "score_total": total,
                "score_keyword": comp.keyword,
                "score_reverse": comp.reverse,
                "score_semantic": comp.semantic,
                "score_graph": comp.graph,
            }
        )

    return {
        "query": query,
        "keywords": keywords,
        "lexical_fragments": lexical_fragments,
        "effective_weights": {
            "keyword_weight": effective_kw_weight,
            "semantic_weight": effective_sem_weight,
            "semantic_keyword_norm_factor": semantic_keyword_norm_factor,
            "semantic_sqrt_norm": cfg.semantic_sqrt_norm,
            "edge_weight": effective_edge_weight,
            "mode": cfg.score_mode.value,
        },
        "thresholds": {
            "semantic_threshold": cfg.semantic_threshold,
            "min_score_threshold": cfg.min_score_threshold,
        },
        "results": rows,
        "filtered_count": len(rows),
        "raw_count": len(scores),
    }


def load_dataset(path: Path) -> Tuple[List[MemoryRecord], List[LinkRecord], Dict[str, Dict[str, float]], Dict[str, List[float]]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    memories_raw = raw.get("memories", [])
    links_raw = raw.get("links", [])
    semantic_similarity = raw.get("semantic_similarity", {})
    query_embeddings = raw.get("query_embeddings", {})

    memories: List[MemoryRecord] = []
    for item in memories_raw:
        memories.append(
            MemoryRecord(
                id=int(item["id"]),
                title=str(item.get("title", "")),
                content=str(item.get("content", "")),
                importance=float(item.get("importance", 0.5)),
                embedding=item.get("embedding"),
            )
        )

    links: List[LinkRecord] = []
    for item in links_raw:
        links.append(
            LinkRecord(
                source_id=int(item["source_id"]),
                target_id=int(item["target_id"]),
                weight=float(item.get("weight", 1.0)),
            )
        )
    return memories, links, semantic_similarity, query_embeddings


def ensure_isolated_demo_memory(memories: List[MemoryRecord]) -> int:
    for memory in memories:
        if memory.title == DEFAULT_ISOLATED_TITLE:
            return memory.id
    next_id = (max((m.id for m in memories), default=0) + 1) if memories else 1
    memories.append(
        MemoryRecord(
            id=next_id,
            title=DEFAULT_ISOLATED_TITLE,
            content=DEFAULT_ISOLATED_CONTENT,
            importance=0.72,
            embedding=None,
        )
    )
    return next_id


def analytical_rank_bound(
    threshold: float,
    keyword_weight: float,
    importance: float,
    rrf_k: float,
    hit_count: int,
) -> float:
    if threshold <= 0.0 or keyword_weight <= 0.0 or importance <= 0.0 or hit_count <= 0:
        return float("inf")
    return (hit_count * keyword_weight * importance) / threshold - rrf_k


def percentile(values: List[float], q: float) -> float:
    if not values:
        return 0.0
    if q <= 0:
        return min(values)
    if q >= 1:
        return max(values)
    ordered = sorted(values)
    pos = (len(ordered) - 1) * q
    left = math.floor(pos)
    right = math.ceil(pos)
    if left == right:
        return ordered[left]
    weight = pos - left
    return ordered[left] * (1.0 - weight) + ordered[right] * weight


def run_fairness_simulation(
    keyword_counts: List[int],
    trials: int,
    seed: int,
    rrf_k: float,
    importance: float,
    semantic_weight: float,
    semantic_threshold: float,
    rank_max: int,
    decision_threshold: float,
) -> List[Dict[str, Any]]:
    rng = random.Random(seed)
    rows: List[Dict[str, Any]] = []

    for k_count in keyword_counts:
        raw_values: List[float] = []
        sqrt_values: List[float] = []
        linear_values: List[float] = []

        for _ in range(trials):
            total = 0.0
            for _ in range(k_count):
                rank = rng.randint(1, rank_max)
                sim = rng.uniform(semantic_threshold, 1.0)
                delta = (1.0 / (rrf_k + rank)) * math.sqrt(importance) + sim * semantic_weight
                total += delta
            raw_values.append(total)
            sqrt_values.append(total / math.sqrt(k_count))
            linear_values.append(total / k_count)

        rows.append(
            {
                "keyword_count": k_count,
                "raw_mean": sum(raw_values) / len(raw_values),
                "sqrt_mean": sum(sqrt_values) / len(sqrt_values),
                "linear_mean": sum(linear_values) / len(linear_values),
                "raw_std": math.sqrt(
                    sum((x - (sum(raw_values) / len(raw_values))) ** 2 for x in raw_values)
                    / len(raw_values)
                ),
                "sqrt_std": math.sqrt(
                    sum((x - (sum(sqrt_values) / len(sqrt_values))) ** 2 for x in sqrt_values)
                    / len(sqrt_values)
                ),
                "linear_std": math.sqrt(
                    sum(
                        (x - (sum(linear_values) / len(linear_values))) ** 2
                        for x in linear_values
                    )
                    / len(linear_values)
                ),
                "raw_p95": percentile(raw_values, 0.95),
                "sqrt_p95": percentile(sqrt_values, 0.95),
                "linear_p95": percentile(linear_values, 0.95),
                "raw_pass_rate": sum(1 for x in raw_values if x >= decision_threshold)
                / len(raw_values),
                "sqrt_pass_rate": sum(1 for x in sqrt_values if x >= decision_threshold)
                / len(sqrt_values),
                "linear_pass_rate": sum(
                    1 for x in linear_values if x >= decision_threshold
                )
                / len(linear_values),
            }
        )
    return rows


def _require_matplotlib() -> Any:
    import matplotlib.pyplot as plt  # type: ignore
    return plt


def plot_fairness(
    rows: List[Dict[str, Any]],
    output_path: Path,
    decision_threshold: float,
) -> None:
    plt = _require_matplotlib()

    k_values = [row["keyword_count"] for row in rows]
    raw_mean = [row["raw_mean"] for row in rows]
    sqrt_mean = [row["sqrt_mean"] for row in rows]
    linear_mean = [row["linear_mean"] for row in rows]

    raw_pass = [row["raw_pass_rate"] for row in rows]
    sqrt_pass = [row["sqrt_pass_rate"] for row in rows]
    linear_pass = [row["linear_pass_rate"] for row in rows]

    fig, axes = plt.subplots(1, 2, figsize=(12, 4.8), dpi=140)

    axes[0].plot(k_values, raw_mean, marker="o", label="raw sum")
    axes[0].plot(k_values, sqrt_mean, marker="o", label="sqrt(K) normalized")
    axes[0].plot(k_values, linear_mean, marker="o", label="K normalized")
    axes[0].set_xlabel("keyword count K")
    axes[0].set_ylabel("semantic score")
    axes[0].set_title("Score scaling by K")
    axes[0].grid(alpha=0.25)
    axes[0].legend()

    axes[1].plot(k_values, raw_pass, marker="o", label="raw sum")
    axes[1].plot(k_values, sqrt_pass, marker="o", label="sqrt(K) normalized")
    axes[1].plot(k_values, linear_pass, marker="o", label="K normalized")
    axes[1].set_xlabel("keyword count K")
    axes[1].set_ylabel(f"pass rate @ threshold={decision_threshold}")
    axes[1].set_ylim(0.0, 1.02)
    axes[1].set_title("Decision fairness by K")
    axes[1].grid(alpha=0.25)
    axes[1].legend()

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path)
    plt.close(fig)


def plot_kw_rev_heatmaps(
    output_path: Path,
    importance_values: List[float],
    rank_max: int,
    threshold: float,
    keyword_weight: float,
    rrf_k: float,
) -> None:
    plt = _require_matplotlib()

    n_cols = len(importance_values)
    fig, axes = plt.subplots(1, n_cols, figsize=(4.3 * n_cols, 4.2), dpi=140)
    if n_cols == 1:
        axes = [axes]

    rank_values = list(range(1, rank_max + 1))
    for col, importance in enumerate(importance_values):
        ax = axes[col]
        grid: List[List[float]] = []
        for r_kw in rank_values:
            row: List[float] = []
            for r_rev in rank_values:
                score = (
                    keyword_weight * importance / (rrf_k + r_kw)
                    + keyword_weight * importance / (rrf_k + r_rev)
                )
                row.append(score)
            grid.append(row)

        im = ax.imshow(
            grid,
            origin="lower",
            aspect="auto",
            interpolation="nearest",
            extent=[1, rank_max, 1, rank_max],
        )
        ax.contour(
            rank_values,
            rank_values,
            grid,
            levels=[threshold],
            colors=["white"],
            linewidths=[1.2],
        )
        ax.set_title(f"importance={importance:.2f}")
        ax.set_xlabel("r_rev")
        if col == 0:
            ax.set_ylabel("r_kw")
        cbar = fig.colorbar(im, ax=ax, fraction=0.048, pad=0.04)
        cbar.set_label("S_kw+rev")

    fig.suptitle("KW + Reverse score field with threshold contour", y=1.02)
    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, bbox_inches="tight")
    plt.close(fig)


def write_csv(rows: List[Dict[str, Any]], output_path: Path) -> None:
    if not rows:
        output_path.write_text("", encoding="utf-8")
        return
    fieldnames = list(rows[0].keys())
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def parse_int_list(text: str) -> List[int]:
    parts = [part.strip() for part in text.split(",") if part.strip()]
    return [int(part) for part in parts]


def parse_float_list(text: str) -> List[float]:
    parts = [part.strip() for part in text.split(",") if part.strip()]
    return [float(part) for part in parts]


def cmd_score(args: argparse.Namespace) -> None:
    memories, links, semantic_similarity, query_embeddings = load_dataset(Path(args.input))
    config = ScoringConfig(
        semantic_threshold=args.semantic_threshold,
        score_mode=ScoreMode(args.score_mode),
        keyword_weight=args.keyword_weight,
        semantic_weight=args.semantic_weight,
        edge_weight=args.edge_weight,
        rrf_k=args.rrf_k,
        min_score_threshold=args.min_score_threshold,
        semantic_sqrt_norm=not args.disable_semantic_sqrt_norm,
    )

    result = score_memories(
        memories=memories,
        links=links,
        query=args.query,
        config=config,
        semantic_similarity=semantic_similarity,
        query_embeddings=query_embeddings,
    )
    rows = result["results"][: args.top_k]

    proof = {
        "single_hit_rank_bound": analytical_rank_bound(
            threshold=args.min_score_threshold,
            keyword_weight=args.keyword_weight,
            importance=args.boundary_importance,
            rrf_k=args.rrf_k,
            hit_count=1,
        ),
        "double_hit_rank_bound": analytical_rank_bound(
            threshold=args.min_score_threshold,
            keyword_weight=args.keyword_weight,
            importance=args.boundary_importance,
            rrf_k=args.rrf_k,
            hit_count=2,
        ),
    }

    output = {
        "query": result["query"],
        "keywords": result["keywords"],
        "lexical_fragments": result.get("lexical_fragments", []),
        "effective_weights": result["effective_weights"],
        "thresholds": result["thresholds"],
        "raw_candidate_count": result["raw_count"],
        "filtered_candidate_count": result["filtered_count"],
        "top_k": args.top_k,
        "proof": proof,
        "results": rows,
    }

    text = json.dumps(output, ensure_ascii=False, indent=2)
    if args.output:
        path = Path(args.output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(text)


def cmd_simulate(args: argparse.Namespace) -> None:
    keyword_counts = parse_int_list(args.keyword_counts)
    rows = run_fairness_simulation(
        keyword_counts=keyword_counts,
        trials=args.trials,
        seed=args.seed,
        rrf_k=args.rrf_k,
        importance=args.importance,
        semantic_weight=args.semantic_weight,
        semantic_threshold=args.semantic_threshold,
        rank_max=args.rank_max,
        decision_threshold=args.decision_threshold,
    )

    payload = {
        "params": {
            "keyword_counts": keyword_counts,
            "trials": args.trials,
            "seed": args.seed,
            "rrf_k": args.rrf_k,
            "importance": args.importance,
            "semantic_weight": args.semantic_weight,
            "semantic_threshold": args.semantic_threshold,
            "rank_max": args.rank_max,
            "decision_threshold": args.decision_threshold,
        },
        "rows": rows,
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output_json:
        output_json = Path(args.output_json)
        output_json.parent.mkdir(parents=True, exist_ok=True)
        output_json.write_text(text, encoding="utf-8")
    if args.output_csv:
        write_csv(rows, Path(args.output_csv))
    if args.output_plot:
        plot_fairness(rows, Path(args.output_plot), args.decision_threshold)
    print(text)


def cmd_plot_kw_rev(args: argparse.Namespace) -> None:
    importance_values = parse_float_list(args.importance_values)
    plot_kw_rev_heatmaps(
        output_path=Path(args.output_plot),
        importance_values=importance_values,
        rank_max=args.rank_max,
        threshold=args.threshold,
        keyword_weight=args.keyword_weight,
        rrf_k=args.rrf_k,
    )
    print(
        json.dumps(
            {
                "output_plot": args.output_plot,
                "importance_values": importance_values,
                "rank_max": args.rank_max,
                "threshold": args.threshold,
                "keyword_weight": args.keyword_weight,
                "rrf_k": args.rrf_k,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


def cmd_demo_isolated(args: argparse.Namespace) -> None:
    memories, links, semantic_similarity, query_embeddings = load_dataset(Path(args.input))
    isolated_id = ensure_isolated_demo_memory(memories)

    config = ScoringConfig(
        semantic_threshold=args.semantic_threshold,
        score_mode=ScoreMode(args.score_mode),
        keyword_weight=args.keyword_weight,
        semantic_weight=args.semantic_weight,
        edge_weight=args.edge_weight,
        rrf_k=args.rrf_k,
        min_score_threshold=args.min_score_threshold,
        semantic_sqrt_norm=not args.disable_semantic_sqrt_norm,
    )

    noisy_query = f"{args.raw_query}\n{args.solution[:1000]}".strip()
    compact_query = build_candidate_search_query(args.raw_query, args.solution)
    direct_query = args.direct_query.strip()

    cases = {
        "noisy_query": noisy_query,
        "compact_query": compact_query,
        "direct_query": direct_query,
    }
    rows: Dict[str, Any] = {}
    for name, query in cases.items():
        result = score_memories(
            memories=memories,
            links=links,
            query=query,
            config=config,
            semantic_similarity=semantic_similarity,
            query_embeddings=query_embeddings,
        )
        isolated_row = next(
            (item for item in result["results"] if int(item["memory_id"]) == isolated_id),
            None,
        )
        rows[name] = {
            "query_len": len(query),
            "keyword_count": len(result.get("keywords", [])),
            "lexical_fragment_count": len(result.get("lexical_fragments", [])),
            "lexical_fragments_preview": result.get("lexical_fragments", [])[:16],
            "raw_candidate_count": result["raw_count"],
            "filtered_candidate_count": result["filtered_count"],
            "isolated_hit": isolated_row is not None,
            "isolated_result": isolated_row,
        }

    output = {
        "isolated_memory": {
            "id": isolated_id,
            "title": DEFAULT_ISOLATED_TITLE,
            "importance": next(m.importance for m in memories if m.id == isolated_id),
        },
        "config": {
            "semantic_threshold": config.semantic_threshold,
            "score_mode": config.score_mode.value,
            "keyword_weight": config.keyword_weight,
            "semantic_weight": config.semantic_weight,
            "edge_weight": config.edge_weight,
            "rrf_k": config.rrf_k,
            "min_score_threshold": config.min_score_threshold,
            "semantic_sqrt_norm": config.semantic_sqrt_norm,
        },
        "cases": rows,
    }
    text = json.dumps(output, ensure_ascii=False, indent=2)
    if args.output:
        path = Path(args.output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(text)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Reproduce memory scoring and run rigorous simulations."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_score = sub.add_parser(
        "score",
        help="Reproduce scoring on a dataset and output ranked results.",
    )
    p_score.add_argument("--input", required=True, help="Path to input dataset json.")
    p_score.add_argument("--query", required=True, help="Query string.")
    p_score.add_argument("--output", help="Output json file path.")
    p_score.add_argument("--top-k", type=int, default=15, help="Top-k output rows.")
    p_score.add_argument("--semantic-threshold", type=float, default=0.4)
    p_score.add_argument(
        "--score-mode",
        type=str,
        default="BALANCED",
        choices=[mode.value for mode in ScoreMode],
    )
    p_score.add_argument("--keyword-weight", type=float, default=10.0)
    p_score.add_argument("--semantic-weight", type=float, default=0.5)
    p_score.add_argument("--edge-weight", type=float, default=0.4)
    p_score.add_argument("--rrf-k", type=float, default=DEFAULT_RRF_K)
    p_score.add_argument(
        "--min-score-threshold",
        type=float,
        default=DEFAULT_MIN_SCORE_THRESHOLD,
    )
    p_score.add_argument(
        "--boundary-importance",
        type=float,
        default=0.5,
        help="Importance used for analytical rank-bound proof in output.",
    )
    p_score.add_argument(
        "--disable-semantic-sqrt-norm",
        action="store_true",
        help="Disable semantic 1/sqrt(K) normalization (for A/B comparison).",
    )
    p_score.set_defaults(func=cmd_score)

    p_sim = sub.add_parser(
        "simulate",
        help="Monte Carlo simulation for keyword-count fairness.",
    )
    p_sim.add_argument(
        "--keyword-counts",
        type=str,
        default="1,2,4,8,16,32",
        help="Comma-separated K values.",
    )
    p_sim.add_argument("--trials", type=int, default=5000)
    p_sim.add_argument("--seed", type=int, default=42)
    p_sim.add_argument("--rrf-k", type=float, default=DEFAULT_RRF_K)
    p_sim.add_argument("--importance", type=float, default=0.5)
    p_sim.add_argument("--semantic-weight", type=float, default=0.5)
    p_sim.add_argument("--semantic-threshold", type=float, default=0.4)
    p_sim.add_argument("--rank-max", type=int, default=400)
    p_sim.add_argument(
        "--decision-threshold",
        type=float,
        default=1.0,
        help="Decision threshold used to compute pass rates.",
    )
    p_sim.add_argument("--output-json", help="Write simulation json output.")
    p_sim.add_argument("--output-csv", help="Write simulation rows as csv.")
    p_sim.add_argument("--output-plot", help="Write fairness plot png.")
    p_sim.set_defaults(func=cmd_simulate)

    p_plot = sub.add_parser(
        "plot-kw-rev",
        help="Plot multi-dimensional kw+reverse score heatmaps.",
    )
    p_plot.add_argument(
        "--importance-values",
        type=str,
        default="0.3,0.5,0.8",
        help="Comma-separated importance slices.",
    )
    p_plot.add_argument("--rank-max", type=int, default=400)
    p_plot.add_argument("--threshold", type=float, default=DEFAULT_MIN_SCORE_THRESHOLD)
    p_plot.add_argument("--keyword-weight", type=float, default=10.0)
    p_plot.add_argument("--rrf-k", type=float, default=DEFAULT_RRF_K)
    p_plot.add_argument("--output-plot", required=True)
    p_plot.set_defaults(func=cmd_plot_kw_rev)

    p_demo = sub.add_parser(
        "demo-isolated",
        help="Inject an isolated memory node and compare noisy/compact/direct query effects.",
    )
    p_demo.add_argument("--input", required=True, help="Path to input dataset json.")
    p_demo.add_argument(
        "--raw-query",
        default=(
            "问题：我要的是那个工具包里面的\n"
            "解决方案：明白，你要测 super_admin 工具包里的等待能力。"
        ),
    )
    p_demo.add_argument(
        "--solution",
        default=(
            "我先帮你做一个简单的 wait for 测试。"
            "随后你确认要的是 super_admin 工具包里的能力。"
        ),
    )
    p_demo.add_argument(
        "--direct-query",
        default="激活web包 发专栏 工具包",
    )
    p_demo.add_argument("--output", help="Write demo json output.")
    p_demo.add_argument("--semantic-threshold", type=float, default=0.6)
    p_demo.add_argument(
        "--score-mode",
        type=str,
        default="BALANCED",
        choices=[mode.value for mode in ScoreMode],
    )
    p_demo.add_argument("--keyword-weight", type=float, default=10.0)
    p_demo.add_argument("--semantic-weight", type=float, default=0.0)
    p_demo.add_argument("--edge-weight", type=float, default=0.4)
    p_demo.add_argument("--rrf-k", type=float, default=DEFAULT_RRF_K)
    p_demo.add_argument(
        "--min-score-threshold",
        type=float,
        default=DEFAULT_MIN_SCORE_THRESHOLD,
    )
    p_demo.add_argument(
        "--disable-semantic-sqrt-norm",
        action="store_true",
        help="Disable semantic 1/sqrt(K) normalization (for A/B comparison).",
    )
    p_demo.set_defaults(func=cmd_demo_isolated)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
