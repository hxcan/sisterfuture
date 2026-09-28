package com.stupidbeauty.sisterfuture.tool;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fast callbacks cannot complete a batch while its calls are still being registered. */
public final class ToolBatchCompletion {
    private final AtomicBoolean registered = new AtomicBoolean();
    private final AtomicBoolean delivered = new AtomicBoolean();
    public void finishRegistration() { registered.set(true); }
    public boolean claim(int completed, int expected) {
        return registered.get() && completed == expected && delivered.compareAndSet(false, true);
    }
}
