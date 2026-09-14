package com.stupidbeauty.sisterfuture.memory;

import android.content.res.AssetManager;
import android.util.Log;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact cosine search for the current small memory store. No additional index or downloads. */
public final class SemanticMemorySearch {
    public static final String QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章：";
    public static final int DEFAULT_LIMIT = 5;
    // A tunable candidate filter, not a calibrated probability of relevance.
    public static final double DEFAULT_MIN_SIMILARITY = 0.5;
    private final MemoryEmbeddingIndexer.ModelFactory factory;
    private final Consumer<Throwable> onError;

    public SemanticMemorySearch(AssetManager assets) {
        this(() -> new BgeEmbeddingModel(assets), error ->
                Log.e("SemanticMemorySearch", "Query embedding failed; using keyword fallback", error));
    }

    SemanticMemorySearch(MemoryEmbeddingIndexer.ModelFactory factory) { this(factory, error -> {}); }

    private SemanticMemorySearch(MemoryEmbeddingIndexer.ModelFactory factory, Consumer<Throwable> onError) {
        this.factory = factory;
        this.onError = onError;
    }

    public Result search(List<MemoryEntity> memories, String query, int limit, double minSimilarity) {
        if (query == null || query.trim().isEmpty()) throw new IllegalArgumentException("query 不能为空");
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit 必须为 1 到 20");
        if (!Double.isFinite(minSimilarity) || minSimilarity < -1 || minSimilarity > 1)
            throw new IllegalArgumentException("min_similarity 必须在 -1 到 1 之间");
        query = query.trim();
        int ready = 0;
        for (MemoryEntity memory : memories) if (usable(memory)) ready++;
        float[] queryVector = null;
        String fallback = ready == 0 ? "no_ready_vectors" : null;
        if (ready > 0) {
            try (MemoryEmbeddingIndexer.Model model = factory.create()) {
                // Only queries get the retrieval instruction; persisted document vectors stay unchanged.
                queryVector = model.embed(QUERY_INSTRUCTION + query);
                if (!valid(queryVector)) throw new IllegalStateException("Invalid query embedding");
            } catch (Exception | LinkageError failure) {
                queryVector = null;
                fallback = "query_embedding_failed";
                onError.accept(failure);
            }
        }
        List<Match> matches = new ArrayList<>();
        String keyword = query.toLowerCase(Locale.ROOT);
        for (MemoryEntity memory : memories) {
            boolean exact = contains(memory.getKey(), keyword) || contains(memory.getContent(), keyword);
            if (memory.getTags() != null) for (String tag : memory.getTags()) exact |= contains(tag, keyword);
            Double score = queryVector != null && usable(memory) ? cosine(queryVector, memory.getEmbedding()) : null;
            boolean semantic = score != null && score >= minSimilarity;
            if (exact || semantic) matches.add(new Match(memory, exact, semantic, score));
        }
        // Keep exact key/content/tag hits, then order by similarity; deterministic ties favor newer memories.
        matches.sort(Comparator.comparing((Match match) -> match.keyword).reversed()
                .thenComparing((Match match) -> match.similarity == null ? -2.0 : match.similarity, Comparator.reverseOrder())
                .thenComparing((Match match) -> match.memory.getTimestamp(), Comparator.reverseOrder())
                .thenComparing((Match match) -> match.memory.getId(), Comparator.reverseOrder()));
        int totalMatches = matches.size();
        if (matches.size() > limit) matches = new ArrayList<>(matches.subList(0, limit));
        return new Result(query, matches, totalMatches, memories.size(), ready, fallback, minSimilarity);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static boolean usable(MemoryEntity memory) {
        return MemoryEmbeddingIndexer.isCurrent(memory) && valid(memory.getEmbedding());
    }

    private static boolean valid(float[] vector) {
        if (vector == null || vector.length != BgeEmbeddingModel.DIMENSIONS) return false;
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) return false;
            norm += (double) value * value;
        }
        return norm > 0;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            aa += (double) a[i] * a[i];
            bb += (double) b[i] * b[i];
        }
        return Math.max(-1, Math.min(1, dot / Math.sqrt(aa * bb)));
    }

    public static final class Match {
        public final MemoryEntity memory;
        public final boolean keyword;
        public final boolean semantic;
        public final Double similarity;
        Match(MemoryEntity memory, boolean keyword, boolean semantic, Double similarity) {
            this.memory = memory; this.keyword = keyword; this.semantic = semantic; this.similarity = similarity;
        }
    }

    public static final class Result {
        public final String query;
        public final List<Match> matches;
        public final int totalMatches, totalMemories, readyVectors;
        public final String fallbackReason;
        public final double minSimilarity;
        Result(String query, List<Match> matches, int totalMatches, int totalMemories, int readyVectors,
               String fallbackReason, double minSimilarity) {
            this.query = query; this.matches = matches; this.totalMatches = totalMatches;
            this.totalMemories = totalMemories; this.readyVectors = readyVectors;
            this.fallbackReason = fallbackReason; this.minSimilarity = minSimilarity;
        }
        public JSONObject toJson() throws Exception {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("query", query);
            result.put("found_count", matches.size());
            result.put("total_match_count", totalMatches);
            result.put("search_mode", fallbackReason == null ? "hybrid" : "keyword_fallback");
            if (fallbackReason != null) result.put("fallback_reason", fallbackReason);
            result.put("total_memories", totalMemories);
            result.put("ready_vectors", readyVectors);
            result.put("pending_or_invalid_vectors", totalMemories - readyVectors);
            result.put("min_similarity", minSimilarity);
            if (fallbackReason == null) result.put("embedding_model", BgeEmbeddingModel.MODEL_ID);
            result.put("note", "相似度表示语义相关程度，不代表事实正确或赞同；请结合原文判断。关键词命中优先。");
            JSONArray jsonMatches = new JSONArray();
            for (Match match : matches) {
                JSONObject item = new JSONObject();
                item.put("key", match.memory.getKey());
                item.put("content", match.memory.getContent());
                item.put("tags", match.memory.getTags());
                item.put("timestamp", match.memory.getTimestamp());
                item.put("match_type", match.keyword ? (match.semantic ? "keyword_and_semantic" : "keyword") : "semantic");
                item.put("similarity", match.similarity == null ? JSONObject.NULL : match.similarity);
                jsonMatches.put(item);
            }
            result.put("matches", jsonMatches);
            return result;
        }
    }
}
