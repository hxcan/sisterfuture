package com.stupidbeauty.sisterfuture;

import java.util.List;
import org.json.JSONObject;

/** Persistence for one conversation; does not own live in-memory history. */
public interface ConversationStore {
    List<JSONObject> loadHistory();
    void saveHistory(List<JSONObject> history);
    int loadMaxRounds(int defaultValue);
    void saveMaxRounds(int value);
    default JSONObject loadCompression() { return null; }
    /** Must commit successfully or throw; never silently report a saved summary. */
    default void saveCompression(JSONObject state) { throw new UnsupportedOperationException("Compression persistence unavailable"); }
}
