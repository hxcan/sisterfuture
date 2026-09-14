package com.stupidbeauty.sisterfuture.memory;

import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmbeddingStateTest {
    @Test public void legacyAndChangedContentNeedBackfill() {
        MemoryEntity memory = new MemoryEntity();
        memory.setContent("喜欢咖啡");
        assertFalse(MemoryEmbeddingIndexer.isCurrent(memory));
        memory.setEmbedding(new float[512]);
        memory.setEmbeddingModel(BgeEmbeddingModel.MODEL_ID);
        memory.setEmbeddingContent(memory.getContent());
        assertTrue(MemoryEmbeddingIndexer.isCurrent(memory));
        memory.setContent("喜欢茶");
        assertFalse(MemoryEmbeddingIndexer.isCurrent(memory));
    }
    @Test public void modelOrDimensionsChangeNeedsBackfill() {
        MemoryEntity memory = new MemoryEntity();
        memory.setEmbedding(new float[512]); memory.setEmbeddingModel("older-model");
        assertFalse(MemoryEmbeddingIndexer.isCurrent(memory));
        memory.setEmbeddingModel(BgeEmbeddingModel.MODEL_ID); memory.setEmbedding(new float[3]);
        assertFalse(MemoryEmbeddingIndexer.isCurrent(memory));
    }
    @Test public void normalization() {
        float[] v = {3,4}; BgeEmbeddingModel.normalize(v);
        assertArrayEquals(new float[]{0.6f,0.8f},v,0.00001f);
    }
    @Test(expected=IllegalStateException.class) public void rejectsZero() { BgeEmbeddingModel.normalize(new float[512]); }
    @Test(expected=IllegalStateException.class) public void rejectsNan() { BgeEmbeddingModel.normalize(new float[]{Float.NaN}); }
}
