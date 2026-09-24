package com.stupidbeauty.sisterfuture;

import android.content.Context;
import android.content.SharedPreferences;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Legacy single-conversation storage, with the original serial asynchronous writer. */
public final class FileConversationStore implements ConversationStore {
    private static final String TAG = "FileConversationStore";
    private final File file;
    private final SharedPreferences preferences;
    private final Executor writer;

    public FileConversationStore(Context context) {
        this(new File(context.getFilesDir(), "conversation_context.json"),
                context.getSharedPreferences("context_manager", Context.MODE_PRIVATE),
                Executors.newSingleThreadExecutor());
    }

    FileConversationStore(File file, SharedPreferences preferences, Executor writer) {
        this.file = file;
        this.preferences = preferences;
        this.writer = writer;
    }

    private static List<JSONObject> decode(JSONArray array) throws Exception {
        List<JSONObject> history = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) history.add(array.getJSONObject(i));
        return history;
    }

    @Override public List<JSONObject> loadHistory() {
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) content.append(line);
                if (content.length() > 0) {
                    JSONObject root = new JSONObject(content.toString());
                    if (root.has("history")) return decode(root.getJSONArray("history"));
                }
            } catch (Exception e) {
                FileLogger.w(TAG, "历史文件不可用，回退到旧偏好设置");
            }
        }
        try {
            String legacy = preferences.getString("history", null);
            if (legacy != null && !legacy.isEmpty()) return decode(new JSONArray(legacy));
        } catch (Exception e) {
            FileLogger.w(TAG, "旧历史不可用，使用空历史");
        }
        return new ArrayList<>();
    }

    @Override public void saveHistory(List<JSONObject> history) {
        // Preserve the existing shallow snapshot and serial write semantics.
        List<JSONObject> copy = new ArrayList<>(history);
        writer.execute(() -> {
            try {
                JSONObject root = new JSONObject().put("history", new JSONArray(copy));
                try (FileWriter output = new FileWriter(file)) {
                    output.write(root.toString());
                    output.flush();
                }
            } catch (Exception e) {
                FileLogger.e(TAG, "保存历史失败", e);
            }
        });
    }

    @Override public int loadMaxRounds(int defaultValue) {
        return preferences.getInt("current_max_rounds", defaultValue);
    }

    @Override public void saveMaxRounds(int value) {
        preferences.edit().putInt("current_max_rounds", value).apply();
    }
}
