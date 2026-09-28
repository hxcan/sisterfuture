package com.stupidbeauty.sisterfuture.tool;

import org.junit.Test;
import static org.junit.Assert.*;

public class ToolBatchCompletionTest {
    @Test public void fastCallbackCannotFinishUnregisteredBatch() {
        ToolBatchCompletion batch = new ToolBatchCompletion();
        assertFalse(batch.claim(1, 1));
        batch.finishRegistration();
        assertFalse(batch.claim(1, 2));
        assertTrue(batch.claim(2, 2));
        assertFalse(batch.claim(2, 2));
    }

    @Test public void concurrentCallbacksDeliverOnce() throws Exception {
        ToolBatchCompletion batch = new ToolBatchCompletion();
        batch.finishRegistration();
        java.util.concurrent.atomic.AtomicInteger deliveries = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread thread = new Thread(() -> { if (batch.claim(2, 2)) deliveries.incrementAndGet(); });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) thread.join();
        assertEquals(1, deliveries.get());
    }
}
