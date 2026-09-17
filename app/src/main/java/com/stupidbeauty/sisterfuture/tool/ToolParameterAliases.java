package com.stupidbeauty.sisterfuture.tool;

import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

/** Opt-in, top-level snake_case aliases for declared lowerCamelCase parameters. */
public final class ToolParameterAliases {
    private ToolParameterAliases() {}

    /**
     * Returns a copy. The canonical key wins even when explicitly null/empty.
     * Unknown keys and nested payloads are untouched; legacy keys are removed
     * so downstream parsers cannot accidentally prefer a stale alias.
     */
    public static JSONObject normalize(JSONObject arguments, String... canonicalNames) throws JSONException {
        JSONObject normalized = new JSONObject();
        Iterator<String> keys = arguments.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            normalized.put(key, arguments.get(key));
        }
        for (String canonical : canonicalNames) {
            if (!canonical.matches("[a-z][a-zA-Z0-9]*"))
                throw new IllegalArgumentException("Expected lowerCamelCase parameter name: " + canonical);
            StringBuilder legacy = new StringBuilder();
            for (char c : canonical.toCharArray()) {
                if (c >= 'A' && c <= 'Z') legacy.append('_').append(Character.toLowerCase(c));
                else legacy.append(c);
            }
            String alias = legacy.toString();
            if (alias.equals(canonical)) continue;
            if (!normalized.has(canonical) && normalized.has(alias))
                normalized.put(canonical, normalized.get(alias));
            normalized.remove(alias);
        }
        return normalized;
    }
}
