package com.stupidbeauty.sisterfuture.tool;

import android.util.Base64;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import okhttp3.Credentials;
import okhttp3.Request;
import org.json.JSONObject;

/** Resolves and applies either Redmine API-key auth or HTTP Basic auth. */
final class RedmineAuth {
    private static final String API_KEY_HEADER = "X-Redmine-API-Key";

    private final String redmineUrl;
    private final String username;
    private final String password;
    private final String apiKey;

    private RedmineAuth(String redmineUrl, String username, String password, String apiKey) {
        this.redmineUrl = stripTrailingSlashes(redmineUrl);
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    static RedmineAuth resolve(JSONObject arguments, String toolNote) {
        JSONObject saved = parseNote(toolNote);
        String redmineUrl = first(arguments, saved, "redmine_url", "redmineUrl");
        String directUsername = firstInObject(arguments, "username");
        String directPassword = firstInObject(arguments, "password");
        String directApiKey = firstInObject(arguments, "api_key", "apiKey", "redmine_api_key");
        String savedUsername = firstInObject(saved, "username");
        String savedPassword = firstInObject(saved, "password");
        String savedApiKey = firstInObject(saved, "api_key", "apiKey", "redmine_api_key");

        String username = directUsername;
        String password = directPassword;
        String apiKey = directApiKey;
        if (apiKey.isEmpty() && (!username.isEmpty() || !password.isEmpty())) {
            if (username.isEmpty()) username = savedUsername;
            if (password.isEmpty()) password = savedPassword;
        }
        if (apiKey.isEmpty() && (username.isEmpty() || password.isEmpty())) {
            apiKey = savedApiKey;
            if (apiKey.isEmpty()) {
                username = savedUsername;
                password = savedPassword;
            }
        }

        if (redmineUrl.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: redmine_url");
        }
        if (apiKey.isEmpty() && (username.isEmpty() || password.isEmpty())) {
            throw new IllegalArgumentException(
                "缺少 Redmine 认证参数：请提供 api_key，或同时提供 username 和 password；也可以将其配置在工具备注中");
        }
        return new RedmineAuth(redmineUrl, username, password, apiKey);
    }

    String getRedmineUrl() {
        return redmineUrl;
    }

    Request.Builder apply(Request.Builder builder) {
        if (!apiKey.isEmpty()) {
            return builder.header(API_KEY_HEADER, apiKey);
        }
        return builder.header("Authorization", Credentials.basic(username, password));
    }

    void apply(HttpURLConnection connection) {
        if (!apiKey.isEmpty()) {
            connection.setRequestProperty(API_KEY_HEADER, apiKey);
        } else {
            String basic = Base64.encodeToString((username + ":" + password)
                .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", "Basic " + basic);
        }
    }

    private static String first(JSONObject arguments, JSONObject saved, String... keys) {
        String direct = firstInObject(arguments, keys);
        return direct.isEmpty() ? firstInObject(saved, keys) : direct;
    }

    private static String firstInObject(JSONObject source, String... keys) {
        if (source == null) return "";
        for (String key : keys) {
            if (!source.has(key) || source.isNull(key)) continue;
            String value = source.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private static JSONObject parseNote(String note) {
        JSONObject result = new JSONObject();
        if (note == null || note.trim().isEmpty()) return result;
        String trimmed = note.trim();
        try {
            JSONObject json = new JSONObject(trimmed);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                result.put(key, json.opt(key));
            }
            return result;
        } catch (Exception ignored) {
            // Also accept the key=value form used by several credential-bearing tools.
        }

        for (String line : trimmed.split("\\r?\\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) separator = line.indexOf(':');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                try {
                    result.put(key, value);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private static String stripTrailingSlashes(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
