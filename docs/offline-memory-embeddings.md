# Offline memory embeddings — phase 1

This phase generates and persists embeddings. **`search_memory` still uses its
existing keyword matching; this is not yet semantic retrieval.**

## Behavior

- The APK bundles a ~26.5 MB Chinese BGE-small TFLite model and its tokenizer.
  No service, API key, first-run download, or Google Play service is needed.
- Original memories are written immediately. A single background worker generates
  512-dimensional, L2-normalized CLS embeddings from `content` only (not key/tags).
- Opening the memory manager and writing a memory request a coalesced backfill.
  Old memories are read in pages of 32; already-current embeddings are skipped.
  Inference is sequential, uses two CPU threads, and releases the interpreter
  after each drain. There is no vector index yet.
- The database stores `embedding` (float array), `embeddingModel`, and
  `embeddingContent` (the exact source text). Existing entity/property IDs are
  preserved. Changing model/tokenizer/pooling requires a new model ID and backfill.
- Updates re-read the row inside a transaction. Deleted rows are not recreated,
  and changed content never receives an embedding of the previous content.
- If model initialization/inference fails, the worker logs the error and stops
  that pass. Original memories and keyword search remain available. A later write
  or app restart retries; there is no automatic tight retry loop.
- `list_all_memories` reports `embedding_status: ready` with model/dimensions, or
  `pending_or_failed`. It intentionally does not send vector arrays to the LLM.

## Runtime change

The community model requires `EMBEDDING_LOOKUP` v4 and newer quantized operations.
TensorFlow Lite 2.16.1 rejects it on load. Use LiteRT 1.2.0, retaining the existing
`org.tensorflow.lite.Interpreter` API. Exclude transitive old TFLite runtime/API
artifacts to avoid duplicate classes. The existing TFLite support 0.4.4 and Flex
2.16.1 dependency remain for TTS. Check both embedding and speech on actual phones
before merging/releasing this shared-runtime change.

The model is a third-party quantized conversion, not an official BAAI TFLite
release. Provenance, fixed revision, SHA-256 and licenses are under
`app/src/main/assets/memory/NOTICE.md`.

## Verification

On Linux:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
python scripts/validate_memory_models.py
```

The Python script lists its isolated desktop dependencies. The LiteRT 1.2.0 Linux
wheel may require clearing its executable-stack flag on newer distributions
(`patchelf --clear-execstack` on its `_pywrap_tensorflow_interpreter_wrapper.so`).
This only modifies the local test wheel, not the Android runtime or model.

JVM tests compare Java token IDs to the pinned Hugging Face tokenizer, verify
normalization and staleness checks, and use real ObjectBox storage with a fake
embedder to test multi-page backfill, reopen/persistence, deletion/update races,
closing during inference, and failure/retry.

Desktop model smoke test passed with LiteRT 1.2.0: coffee paraphrase cosine ~0.925,
unrelated password sentence ~0.191; FastSpeech2 → MBMelGAN produced finite mel and
audio outputs. This is a smoke test, not a retrieval-quality benchmark or Android
latency/compatibility measurement.

On a connected ARM phone (not yet run in this development environment):

```sh
./gradlew connectedDebugAndroidTest
```

`OfflineMemoryTest` exercises the actual Java embedding adapter, persistent
backfill, and existing Java speech pipeline. Then manually verify:

1. Install over an existing app with memories (do not clear its data).
2. Start the app, wait for backfill, ask to list memories. Check `ready` and 512
   dimensions; restart and check again.
3. Write a new Chinese memory, then list it after background generation.
4. Turn off networking and repeat; confirm speech generation still works.

Long inputs are truncated to 510 WordPiece tokens plus CLS/SEP; no chunking yet.
Cosine similarity measures relatedness, not agreement/negation. Next phase can
embed queries with the appropriate BGE retrieval instruction and implement
similarity ranking/fallback, using only vectors with matching model IDs.
