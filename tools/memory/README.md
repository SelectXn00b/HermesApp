# Memory Scoring Reproduction (Python)

This folder reproduces the current memory candidate scoring logic in Python and provides simulation/plotting utilities for rigorous verification.

## Files

- `memory_scoring_sim.py`
  - Reproduces scoring:
    - keyword score
    - reverse containment score
    - semantic score
    - graph propagation score
    - min-score filtering
    - top-k truncation
  - Provides:
    - Monte Carlo fairness simulation for keyword-count bias
    - multi-dimensional kw+reverse score heatmaps

## Environment

- Python 3.9+
- For plotting subcommands: `matplotlib`

## Input JSON schema (`score` command)

```json
{
  "memories": [
    {
      "id": 1,
      "title": "Chang'an University",
      "content": "Located in Xi'an",
      "importance": 0.5
    }
  ],
  "links": [
    {
      "source_id": 1,
      "target_id": 2,
      "weight": 0.7
    }
  ],
  "semantic_similarity": {
    "chang'an": {
      "1": 0.86,
      "2": 0.54
    }
  },
  "query_embeddings": {
    "chang'an": [0.1, 0.2, 0.3]
  }
}
```

Notes:
- `semantic_similarity` is the strictest way to control semantic term values for reproducible proof.
- `query_embeddings` + memory embeddings can be used if you want cosine-based semantic computation.

## Usage

### 1) Reproduce scoring and output Top-K

```bash
python tools/memory/memory_scoring_sim.py score \
  --input tools/memory/sample_dataset.json \
  --query "chang'an university in xian" \
  --top-k 15 \
  --semantic-threshold 0.4 \
  --score-mode BALANCED \
  --keyword-weight 10 \
  --semantic-weight 0.5 \
  --edge-weight 0.4 \
  --min-score-threshold 0.025 \
  --output tools/memory/out/score_result.json
```

### 2) Monte Carlo simulation for keyword-count fairness

```bash
python tools/memory/memory_scoring_sim.py simulate \
  --keyword-counts "1,2,4,8,16,32" \
  --trials 5000 \
  --importance 0.5 \
  --semantic-weight 0.5 \
  --semantic-threshold 0.4 \
  --rank-max 400 \
  --decision-threshold 1.0 \
  --output-json tools/memory/out/fairness.json \
  --output-csv tools/memory/out/fairness.csv \
  --output-plot tools/memory/out/fairness.png
```

### 3) Multi-dimensional kw+reverse score heatmaps

```bash
python tools/memory/memory_scoring_sim.py plot-kw-rev \
  --importance-values "0.3,0.5,0.8" \
  --rank-max 400 \
  --threshold 0.025 \
  --keyword-weight 10 \
  --rrf-k 60 \
  --output-plot tools/memory/out/kw_rev_heatmaps.png
```

## Formula alignment

The script follows the same formula structure currently used in app logic:

- Total score:
  - `S = S_kw + S_rev + S_sem + S_graph`
- Keyword / reverse term:
  - `delta = 1/(k+r) * importance * keywordWeight`
- Semantic term:
  - `delta = (1/(k+r) * sqrt(importance) + similarity * semanticWeight) * 1/sqrt(K)`
  - where `K` is keyword count in the query
- Graph propagation:
  - `delta = sourceScore * linkWeight * edgeWeight + 0.03 * edgeWeight`
- Final filter:
  - `score >= minScoreThreshold`

Notes:
- The script enables semantic `1/sqrt(K)` normalization by default to match current Kotlin logic.
- Use `score --disable-semantic-sqrt-norm` for A/B comparison against old behavior.
