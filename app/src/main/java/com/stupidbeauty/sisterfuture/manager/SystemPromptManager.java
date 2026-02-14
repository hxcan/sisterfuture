package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;

public class SystemPromptManager {
    private static final String PREF_NAME = "system_prompt_store";
    private static final String KEY_PROMPT = "current_prompt";    
    private static SystemPromptManager instance;
    private SharedPreferences prefs;    
    private SystemPromptManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized SystemPromptManager getInstance(Context context) {
        if (instance == null) {
            instance = new SystemPromptManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public String getCurrentPrompt() {
        return prefs.getString(KEY_PROMPT, "");
    }
    
    public void updatePrompt(String newPrompt) {
        prefs.edit().putString(KEY_PROMPT, newPrompt).apply();
    }
}
