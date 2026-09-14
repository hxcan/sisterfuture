package com.stupidbeauty.sisterfuture.memory;

import android.content.res.AssetManager;
import android.util.Log;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity_;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Coalesced, bounded-page backfill; no model/network work on the caller's thread. */
public final class MemoryEmbeddingIndexer implements AutoCloseable {
    interface Model extends AutoCloseable {
        float[] embed(String content);
        @Override void close();
    }
    interface ModelFactory { Model create() throws Exception; }
    private final BoxStore store;
    private final Box<MemoryEntity> box;
    private final ModelFactory factory;
    private final Consumer<Throwable> onError;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running;
    private boolean requested;
    private boolean closed;

    public MemoryEmbeddingIndexer(BoxStore store, AssetManager assets) {
        this(store, () -> new BgeEmbeddingModel(assets), error ->
                Log.e("MemoryEmbeddingIndexer", "Offline embedding failed; original memories retained", error));
    }

    MemoryEmbeddingIndexer(BoxStore store, ModelFactory factory, Consumer<Throwable> onError) {
        this.store = store;
        this.box = store.boxFor(MemoryEntity.class);
        this.factory = factory;
        this.onError = onError;
    }

    public synchronized void request() {
        if (closed) return;
        requested = true;
        if (!running) {
            running = true;
            executor.execute(this::drain);
        }
    }

    public static boolean isCurrent(MemoryEntity memory) {
        return memory != null && memory.getEmbedding() != null && memory.getEmbedding().length == BgeEmbeddingModel.DIMENSIONS &&
                BgeEmbeddingModel.MODEL_ID.equals(memory.getEmbeddingModel()) &&
                Objects.equals(memory.getContent(), memory.getEmbeddingContent());
    }

    private void drain() {
        Model model = null;
        try {
            while (true) {
                synchronized (this) {
                    if (closed || !requested) return;
                    requested = false;
                }
                long afterId = 0;
                while (true) {
                    List<MemoryEntity> page;
                    synchronized (this) {
                        if (closed) return;
                        try (Query<MemoryEntity> query = box.query().greater(MemoryEntity_.id, afterId)
                                .order(MemoryEntity_.id).build()) {
                            page = query.find(0, 32);
                        }
                    }
                    if (page.isEmpty()) break;
                    for (MemoryEntity memory : page) {
                        afterId = memory.getId();
                        synchronized (this) { if (closed) return; }
                        if (isCurrent(memory)) continue;
                        if (model == null) model = factory.create();
                        float[] vector = model.embed(memory.getContent());
                        synchronized (this) {
                            if (closed) return;
                            // Re-read in the write transaction: never resurrect deleted/changed memories.
                            store.runInTx(() -> {
                                MemoryEntity current = box.get(memory.getId());
                                if (current == null || !Objects.equals(current.getContent(), memory.getContent())) return;
                                current.setEmbedding(vector);
                                current.setEmbeddingModel(BgeEmbeddingModel.MODEL_ID);
                                current.setEmbeddingContent(current.getContent());
                                box.put(current);
                            });
                        }
                    }
                }
            }
        } catch (Exception | LinkageError error) {
            // Original memories remain usable; a later write/restart requests another pass.
            onError.accept(error);
        } finally {
            try {
                if (model != null) model.close();
            } finally {
                synchronized (this) {
                    running = false;
                    if (requested && !closed) request();
                }
            }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        executor.shutdown();
    }
}
