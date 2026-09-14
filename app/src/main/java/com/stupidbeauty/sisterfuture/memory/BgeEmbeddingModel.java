package com.stupidbeauty.sisterfuture.memory;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Offline document embeddings. Owned by one background worker, never shared with TTS. */
public final class BgeEmbeddingModel implements MemoryEmbeddingIndexer.Model {
    public static final String MODEL_ID = "bge-small-zh-v1.5-tflite-1113ab251b20-cls-l2-v1";
    public static final int DIMENSIONS = 512;
    private final Interpreter interpreter;
    private final BgeTokenizer tokenizer;
    private final ByteBuffer hidden = ByteBuffer.allocateDirect(BgeTokenizer.LENGTH * DIMENSIONS * 4)
            .order(ByteOrder.nativeOrder());
    private final ByteBuffer pooled = ByteBuffer.allocateDirect(DIMENSIONS * 4).order(ByteOrder.nativeOrder());

    public BgeEmbeddingModel(AssetManager assets) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(assets.open("memory/tokenizer.json"), StandardCharsets.UTF_8)) {
            tokenizer = new BgeTokenizer(reader);
        }
        try (AssetFileDescriptor fd = assets.openFd("memory/bge-small-zh-v1.5.tflite");
             FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            ByteBuffer model = stream.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(2));
        }
    }

    public float[] embed(String content) {
        int[][] tokens = tokenizer.encode(content == null ? "" : content);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("args_0", new int[][]{tokens[0]});
        inputs.put("args_1", new int[][]{tokens[1]});
        hidden.clear();
        pooled.clear();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("last_hidden_state", hidden);
        outputs.put("pooler_output", pooled);
        interpreter.runSignature(inputs, outputs, "serving_default");
        // BGE uses the first token of last_hidden_state, NOT pooler_output or mean pooling.
        float[] vector = new float[DIMENSIONS];
        hidden.rewind();
        hidden.asFloatBuffer().get(vector);
        normalize(vector);
        return vector;
    }

    static void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite embedding");
            norm += (double) value * value;
        }
        if (norm == 0) throw new IllegalStateException("Empty embedding");
        norm = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }

    @Override public void close() { interpreter.close(); }
}
