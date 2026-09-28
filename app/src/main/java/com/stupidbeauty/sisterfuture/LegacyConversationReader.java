package com.stupidbeauty.sisterfuture;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Strict migration reader: corrupt existing data must not be marked as imported empty. */
final class LegacyConversationReader {
    static List<JSONObject> load(Context context) throws Exception {
        File file = new File(context.getFilesDir(), "conversation_context.json");
        if (file.exists()) {
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
            return decode(new JSONObject(text.toString()).getJSONArray("history"));
        }
        String legacy = context.getSharedPreferences("context_manager", Context.MODE_PRIVATE).getString("history", null);
        return legacy == null || legacy.isEmpty() ? new ArrayList<>() : decode(new JSONArray(legacy));
    }

    private static List<JSONObject> decode(JSONArray array) throws Exception {
        List<JSONObject> messages = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) messages.add(array.getJSONObject(i));
        return messages;
    }
}
