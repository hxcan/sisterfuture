# Semantic memory search — phase 2

`searchMemory` now embeds the query offline and searches the document vectors
persisted by phase 1. The tool name and original `query` parameter are unchanged.
No new model, runtime dependency, database migration, or network request is added.

## Ranking and fallback

- Prefix only queries with BGE's Chinese retrieval instruction; document embeddings
  and their model ID remain unchanged. Reference:
  https://huggingface.co/BAAI/bge-small-zh-v1.5#model-list
- Only use finite, nonzero, 512-dimensional vectors whose model and source content
  match the current memory. Outdated/invalid vectors cannot participate in ranking.
- Search the current memory snapshot by exact cosine similarity. This phase is
  designed for a small local memory store, not a large ANN/HNSW index. It currently
  loads all memory rows and sorts matching candidates (linear scan plus sorting).
- Preserve case-insensitive substring hits in key, content or tags, including
  memories whose vectors are not ready. Keyword hits sort first, then similarity
  descending, then timestamp/id descending for deterministic ties.
- Optional `limit`: 1–20, default 5. Optional `min_similarity`: -1–1, default 0.5.
  The threshold only filters semantic candidates, not keyword hits. It is a tunable
  heuristic, not a probability or a threshold calibrated on the user's data.
- No ready vectors: skip model loading and explicitly fall back to keywords.
  Query model failure: retain keyword results and report fallback. A healthy
  vector query with no above-threshold match returns an empty list, not an error.
- Tool dispatch is asynchronous on one search worker; concurrent searches are
  serialized. Each query owns/closes its interpreter. Backfill may run separately;
  this is not a shared interpreter cache. Phone memory/latency during backfill still
  need observation.

## Observable tool results

```json
{
  "query": "我平时爱喝什么饮品",
  "search_mode": "hybrid",
  "ready_vectors": 3,
  "pending_or_invalid_vectors": 0,
  "found_count": 1,
  "matches": [{"content": "我喜欢喝咖啡", "match_type": "semantic", "similarity": 0.58}]
}
```

This is an illustrative subset, not a guaranteed response. Original match fields
(`key`, `content`, `tags`, `timestamp`) remain. Added fields include:

- `search_mode`: `hybrid` when a valid query vector was used; `keyword_fallback`
  otherwise. `hybrid` does not imply all memories have vectors; check coverage.
- `fallback_reason`: `no_ready_vectors` or `query_embedding_failed`.
- `total_memories`, `ready_vectors`, `pending_or_invalid_vectors` describe coverage.
- `total_match_count` counts candidates before limiting; `found_count` is returned count.
- `match_type`: `keyword`, `semantic`, or `keyword_and_semantic`.
- `similarity`: cosine when available, null otherwise; vectors themselves are never
  sent to the conversation. Relatedness does not establish truth or agreement.

## Tests and acceptance

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
python scripts/validate_memory_models.py
./gradlew connectedDebugAndroidTest  # needs a connected ARM phone
```

JVM tests isolate ranking/fallback with deterministic vectors and check asynchronous
tool dispatch, existing query-only calls, parameters and observable JSON output.
The desktop real-model test checks drink/login queries against coffee/password
documents using the retrieval prefix, and reruns the existing speech smoke test.
The device test searches actual persisted embeddings after reopening ObjectBox.

Manual phone acceptance: ask the assistant to write “我每天早上喜欢喝咖啡”, wait for
backfill, then explicitly ask it to **call searchMemory** with “我平时爱喝什么饮品”.
Inspect the actual tool output: it should report `hybrid`, have nonzero
`ready_vectors`, and return the coffee memory as a semantic candidate. Do not use
the assistant's unaided answer (which might come from current conversation context)
as evidence that semantic retrieval worked. Also try exact keys/tags and verify
existing speech still works. Unrelated memories may still be returned; adjust
the threshold using real examples rather than treating similarity as confidence.
