package com.stupidbeauty.sisterfuture.memory;

import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import org.junit.Test;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SemanticMemorySearchTest {
    private float[] vector(float x, float y) {
        float[] result = new float[512]; result[0] = x; result[1] = y; return result;
    }
    private MemoryEntity memory(long id, String text, float x, float y) {
        MemoryEntity m = new MemoryEntity(); m.setId(id); m.setKey("m" + id); m.setContent(text);
        m.setEmbedding(vector(x,y)); m.setEmbeddingModel(BgeEmbeddingModel.MODEL_ID);
        m.setEmbeddingContent(text); m.setTimestamp(id); return m;
    }
    private SemanticMemorySearch searcher() { return searcher(new AtomicReference<>(), new AtomicBoolean()); }
    private SemanticMemorySearch searcher(AtomicReference<String> input, AtomicBoolean closed) {
        return new SemanticMemorySearch(() -> new MemoryEmbeddingIndexer.Model() {
            public float[] embed(String text) { input.set(text); return vector(1,0); }
            public void close() { closed.set(true); }
        });
    }

    @Test public void findsSemanticMatchWithoutSharedKeywordsAndUsesQueryInstruction() throws Exception {
        AtomicReference<String> input = new AtomicReference<>(); AtomicBoolean closed = new AtomicBoolean();
        MemoryEntity coffee=memory(1,"我每天早上喝拿铁",0.9f,0.1f), server=memory(2,"服务器登录信息",0,1);
        SemanticMemorySearch.Result result=searcher(input,closed).search(Arrays.asList(server,coffee),"饮品偏好",5,0.5);
        assertEquals(1,result.matches.size()); assertEquals(1,result.matches.get(0).memory.getId());
        assertFalse(result.matches.get(0).keyword); assertTrue(result.matches.get(0).semantic);
        assertEquals(SemanticMemorySearch.QUERY_INSTRUCTION+"饮品偏好",input.get()); assertTrue(closed.get());
        JSONObject json=result.toJson();
        assertEquals("hybrid",json.getString("search_mode")); assertEquals(2,json.getInt("ready_vectors"));
        assertEquals("semantic",json.getJSONArray("matches").getJSONObject(0).getString("match_type"));
        assertFalse(json.getJSONArray("matches").getJSONObject(0).has("embedding"));
    }
    @Test public void exactKeywordIsPreservedEvenBelowThreshold() {
        MemoryEntity key=memory(1,"连接参数",0,1); key.setKey("SSH_HOST");
        MemoryEntity similar=memory(2,"远程机器",1,0);
        SemanticMemorySearch.Result r=searcher().search(Arrays.asList(similar,key),"ssh_host",2,0.8);
        assertEquals(1,r.matches.get(0).memory.getId()); assertTrue(r.matches.get(0).keyword);
        assertFalse(r.matches.get(0).semantic); assertEquals(0,r.matches.get(0).similarity,0);
    }
    @Test public void legacyKeywordTagsWorkWithoutLoadingModel() throws Exception {
        MemoryEntity m=new MemoryEntity(); m.setTags(Arrays.asList("Music",null));
        SemanticMemorySearch s=new SemanticMemorySearch(()->{throw new AssertionError("must not load model");});
        SemanticMemorySearch.Result r=s.search(Collections.singletonList(m),"music",5,0.5);
        assertEquals(1,r.matches.size()); assertEquals("no_ready_vectors",r.fallbackReason);
        assertTrue(r.toJson().getJSONArray("matches").getJSONObject(0).isNull("similarity"));
    }
    @Test public void rejectsStaleWrongModelAndCorruptVectors() {
        MemoryEntity stale=memory(1,"old",1,0); stale.setContent("new");
        MemoryEntity wrong=memory(2,"wrong",1,0); wrong.setEmbeddingModel("other");
        MemoryEntity nan=memory(3,"nan",Float.NaN,0), zero=memory(4,"zero",0,0);
        MemoryEntity dimension=memory(5,"dimension",1,0); dimension.setEmbedding(new float[]{1});
        MemoryEntity valid=memory(6,"valid",1,0);
        SemanticMemorySearch.Result r=searcher().search(Arrays.asList(stale,wrong,nan,zero,dimension,valid),"query",5,0.5);
        assertEquals(1,r.readyVectors); assertEquals(1,r.matches.size()); assertEquals(6,r.matches.get(0).memory.getId());
    }
    @Test public void modelFailureFallsBackExplicitly() throws Exception {
        SemanticMemorySearch s=new SemanticMemorySearch(()->{throw new IllegalStateException("load failed");});
        SemanticMemorySearch.Result r=s.search(Collections.singletonList(memory(1,"咖啡",1,0)),"咖啡",5,0.5);
        assertEquals(1,r.matches.size()); assertEquals("query_embedding_failed",r.fallbackReason);
        assertEquals("keyword_fallback",r.toJson().getString("search_mode")); assertNull(r.matches.get(0).similarity);
    }
    @Test public void invalidQueryVectorClosesModelAndFallsBack() {
        AtomicBoolean closed=new AtomicBoolean();
        SemanticMemorySearch s=new SemanticMemorySearch(()->new MemoryEmbeddingIndexer.Model() {
            public float[] embed(String text) { return new float[512]; }
            public void close() { closed.set(true); }
        });
        assertEquals("query_embedding_failed",s.search(Collections.singletonList(memory(1,"text",1,0)),"query",5,0.5).fallbackReason);
        assertTrue(closed.get());
    }
    @Test public void ranksLimitsAndCountsBeforeTruncation() {
        SemanticMemorySearch.Result r=searcher().search(Arrays.asList(memory(1,"first",0.6f,0.8f),
                memory(2,"best",1,0),memory(3,"second",0.8f,0.6f)),"query",2,0.5);
        assertEquals(3,r.totalMatches); assertEquals(2,r.matches.size());
        assertEquals(2,r.matches.get(0).memory.getId()); assertEquals(3,r.matches.get(1).memory.getId());
    }
    @Test public void highThresholdCanReturnNoMatches() {
        assertTrue(searcher().search(Collections.singletonList(memory(1,"other",0,1)),"query",5,0.5).matches.isEmpty());
    }
    @Test public void mixedBackfillDoesNotLosePendingKeywordMatch() {
        MemoryEntity pending=new MemoryEntity(); pending.setContent("coffee");
        SemanticMemorySearch.Result r=searcher().search(Arrays.asList(pending,memory(2,"拿铁",1,0)),"coffee",5,0.5);
        assertEquals(2,r.matches.size()); assertEquals(1,r.readyVectors); assertNull(r.fallbackReason);
    }
    @Test public void cosineHandlesNonUnitVectors() { assertEquals(1,SemanticMemorySearch.cosine(vector(3,0),vector(9,0)),0.000001); }
    @Test public void emptyStoreDoesNotLoadModel() {
        SemanticMemorySearch s=new SemanticMemorySearch(()->{throw new AssertionError("must not load");});
        assertTrue(s.search(Collections.emptyList(),"query",5,0.5).matches.isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void emptyQueryRejected() { searcher().search(Collections.emptyList(),"  ",5,0.5); }
    @Test(expected=IllegalArgumentException.class) public void excessiveLimitRejected() { searcher().search(Collections.emptyList(),"q",21,0.5); }
    @Test(expected=IllegalArgumentException.class) public void nanThresholdRejected() { searcher().search(Collections.emptyList(),"q",5,Double.NaN); }
}
