package com.stupidbeauty.sisterfuture;

import java.util.List;
import org.json.JSONObject;

/** Persistence for one conversation; does not own live in-memory history. */
public interface ConversationStore {
    List<JSONObject> loadHistory();
    void saveHistory(List<JSONObject> history);
    int loadMaxRounds(int defaultValue);
    void saveMaxRounds(int value);
}
