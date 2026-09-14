package com.stupidbeauty.sisterfuture.memory;

import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.stupidbeauty.sisterfuture.bean.MyObjectBox;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Uses real ObjectBox storage; a deterministic fake isolates indexer scheduling from inference. */
public class MemoryEmbeddingIndexerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File directory;
    private BoxStore store;
    private Box<MemoryEntity> box;
    private MemoryEmbeddingIndexer indexer;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    @Before public void open() throws Exception {
        directory = temp.newFolder("memory");
        reopen();
    }
    private void reopen() {
        store = MyObjectBox.builder().directory(directory).build();
        box = store.boxFor(MemoryEntity.class);
    }
    @After public void close() {
        if(indexer!=null) indexer.close();
        if(store!=null && !store.isClosed()) store.close();
    }
    private long save(String content) {
        MemoryEntity memory = new MemoryEntity();
        memory.setContent(content); memory.setKey("key"); memory.setTimestamp(123);
        return box.put(memory);
    }
    private MemoryEmbeddingIndexer.Model model(AtomicInteger calls) {
        return new MemoryEmbeddingIndexer.Model() {
            public float[] embed(String content) {
                calls.incrementAndGet(); float[] v=new float[512]; v[0]=1; return v;
            }
            public void close() {}
        };
    }
    @Test public void backfillsMultiplePagesAndPersistsAcrossReopen() throws Exception {
        for(int i=0;i<70;i++) save("旧记忆"+i);
        AtomicInteger calls = new AtomicInteger();
        indexer = new MemoryEmbeddingIndexer(store,()->model(calls),failure::set);
        indexer.request();
        await(()->box.getAll().stream().allMatch(MemoryEmbeddingIndexer::isCurrent));
        assertEquals(70,calls.get());
        indexer.close(); store.close(); reopen();
        assertEquals(70,box.count());
        for(MemoryEntity m:box.getAll()) {
            assertTrue(MemoryEmbeddingIndexer.isCurrent(m));
            assertEquals(123,m.getTimestamp());
            assertEquals(1,m.getEmbedding()[0],0);
        }
        long id=save("新记忆");
        indexer=new MemoryEmbeddingIndexer(store,()->model(calls),failure::set);
        indexer.request(); await(()->MemoryEmbeddingIndexer.isCurrent(box.get(id)));
        assertEquals(71,calls.get()); assertNull(failure.get());
    }
    @Test public void deletedMemoryIsNotResurrected() throws Exception { race(0); }
    @Test public void changedMemoryDoesNotReceiveStaleVector() throws Exception { race(1); }
    @Test public void closeDuringInferenceDoesNotWriteToClosedStore() throws Exception { race(2); }
    private void race(int action) throws Exception {
        long id=save("旧内容");
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1), finished=new CountDownLatch(1);
        indexer=new MemoryEmbeddingIndexer(store,()->new MemoryEmbeddingIndexer.Model() {
            public float[] embed(String content) {
                entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch(InterruptedException e) { throw new IllegalStateException(e); }
                return new float[512];
            }
            public void close() { finished.countDown(); }
        },failure::set);
        indexer.request(); assertTrue(entered.await(5,TimeUnit.SECONDS));
        if(action==2) { indexer.close(); store.close(); }
        else if(action==1) { MemoryEntity m=box.get(id); m.setContent("新内容"); box.put(m); }
        else box.remove(id);
        release.countDown(); assertTrue(finished.await(5,TimeUnit.SECONDS));
        if(action==2) { reopen(); assertNull(box.get(id).getEmbedding()); }
        else if(action==1) { assertEquals("新内容",box.get(id).getContent()); assertNull(box.get(id).getEmbedding()); }
        else assertNull(box.get(id));
        assertNull(failure.get());
    }
    @Test public void failedInitializationRetainsOriginalAndCanRetry() throws Exception {
        long id=save("不能丢失的原文");
        indexer=new MemoryEmbeddingIndexer(store,()->{throw new Exception("model unavailable");},failure::set);
        indexer.request(); await(()->failure.get()!=null);
        assertEquals("不能丢失的原文",box.get(id).getContent()); assertNull(box.get(id).getEmbedding());
        indexer.close();
        indexer=new MemoryEmbeddingIndexer(store,()->model(new AtomicInteger()),failure::set);
        indexer.request(); await(()->MemoryEmbeddingIndexer.isCurrent(box.get(id)));
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(10);
        assertTrue("Background work timed out",condition.getAsBoolean());
    }
}
