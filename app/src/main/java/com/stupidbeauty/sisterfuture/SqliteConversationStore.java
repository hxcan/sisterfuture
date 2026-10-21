package com.stupidbeauty.sisterfuture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** One session's persistent history. UI and in-memory history remain in ContextManager. */
public final class SqliteConversationStore implements ConversationStore {
    // SQLite substr/length count Unicode code points, not Java UTF-16 code units.
    // Even four-byte characters keep each returned chunk comfortably below CursorWindow limits.
    static final int MESSAGE_CHUNK_CHARACTERS = 16 * 1024;
    // Shared across Activity recreations: reads wait for already queued writes.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Context context;
    private final String databasePath;
    private final String sessionId;
    private static final com.stupidbeauty.sisterfuture.utils.PerformanceStats SNAPSHOT = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();
    private static final com.stupidbeauty.sisterfuture.utils.PerformanceStats WRITE_QUEUE = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();
    private static final com.stupidbeauty.sisterfuture.utils.PerformanceStats WRITE = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();

    public SqliteConversationStore(Context context) {
        this(context, SessionManager.DEFAULT_SESSION_ID);
    }

    public SqliteConversationStore(Context context, String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty())
            throw new IllegalArgumentException("sessionId must not be empty");
        // Resolve paths before taking application context (also supports isolated test contexts).
        databasePath = new File(context.getFilesDir(), "conversations.db").getAbsolutePath();
        this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        this.sessionId = sessionId;
        run(() -> {
            try (Helper helper = new Helper(this.context, databasePath)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                db.beginTransaction();
                try {
                    boolean exists;
                    try (Cursor cursor = db.rawQuery("SELECT 1 FROM sessions WHERE session_id=?", new String[]{sessionId})) {
                        exists = cursor.moveToFirst();
                    }
                    if (!exists) {
                        List<JSONObject> legacy = new ArrayList<>();
                        int maxRounds = 5;
                        if (SessionManager.DEFAULT_SESSION_ID.equals(sessionId)) {
                            legacy = LegacyConversationReader.load(context);
                            maxRounds = context.getSharedPreferences("context_manager", Context.MODE_PRIVATE)
                                    .getInt("current_max_rounds", 5);
                        }
                        ContentValues session = new ContentValues();
                        session.put("session_id", sessionId);
                        session.put("max_rounds", maxRounds);
                        // Presence of this row is the durable import marker, even with zero messages.
                        db.insertOrThrow("sessions", null, session);
                        replace(db, snapshot(legacy));
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            }
            return null;
        });
    }

    static List<String> snapshot(List<JSONObject> history) {
        List<String> result = new ArrayList<>(history.size());
        for (JSONObject message : history) {
            if (message == null) throw new IllegalArgumentException("Null history message");
            result.add(message.toString());
        }
        return result;
    }

    public List<String> listSessionIds() {
        return run(() -> {
            List<String> ids = new ArrayList<>();
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT session_id FROM sessions ORDER BY rowid", null)) {
                while (cursor.moveToNext()) ids.add(cursor.getString(0));
            }
            return ids;
        });
    }

    /**
     * One page of list-ready session rows, newest activity first.
     *
     * Replaces the previous per-session {@code loadFirstUserText()} fan-out: the preview and the
     * activity timestamp both come from indexed columns, so a page costs one query instead of
     * one blocking database round trip per session. Callers page with {@code offset}/{@code limit}.
     */
    public List<SessionSummary> listSessionSummaries(int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        return run(() -> {
            List<SessionSummary> page = new ArrayList<>();
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery(
                     "SELECT session_id, preview_text, last_activity_at FROM sessions "
                         + "ORDER BY last_activity_at DESC, rowid DESC LIMIT ? OFFSET ?",
                     new String[]{Integer.toString(limit), Integer.toString(offset)})) {
                SQLiteDatabase db = helper.getReadableDatabase();
                while (cursor.moveToNext()) {
                    page.add(new SessionSummary(cursor.getString(0),
                        nullToEmpty(cursor.getString(1)), cursor.getLong(2)));
                }
            }
            return page;
        });
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private void replace(SQLiteDatabase db, List<String> messages) {
        db.delete("messages", "session_id=?", new String[]{sessionId});
        for (int i = 0; i < messages.size(); i++) {
            ContentValues row = new ContentValues();
            row.put("session_id", sessionId);
            row.put("position", i);
            row.put("message_json", messages.get(i));
            db.insertOrThrow("messages", null, row);
        }
        refreshSessionSummary(db, sessionId);
    }

    /**
     * Recompute the cached preview and activity timestamp for one session.
     *
     * Both columns are derived from the last few messages only, so the cost does not grow with
     * history length. Called on every replace(), keeping the listing query free of JSON parsing.
     */
    private static void refreshSessionSummary(SQLiteDatabase db, String sessionId) {
        long lastActivity = 0L;
        String preview = "";
        int scanned = 0;
        // The first user message defines the title; activity comes from the newest message.
        // Walk newest-first and stop as soon as both are known.
        try (Cursor cursor = db.rawQuery(
                "SELECT position, message_json FROM messages WHERE session_id=? ORDER BY position DESC",
                new String[]{sessionId})) {
            while (cursor.moveToNext()) {
                if (scanned++ >= MESSAGE_SUMMARY_SCAN_LIMIT) break;
                String json = cursor.getString(1);
                if (json == null) continue;
                long recorded = readRecordedTime(json);
                if (lastActivity == 0L && recorded > 0L) lastActivity = recorded;
                if (preview.isEmpty()) {
                    String userText = extractUserText(json);
                    if (!userText.isEmpty()) preview = collapsePreview(userText);
                }
                if (lastActivity > 0L && !preview.isEmpty()) break;
            }
        }
        ContentValues row = new ContentValues();
        row.put("preview_text", preview);
        row.put("last_activity_at", lastActivity);
        db.update("sessions", row, "session_id=?", new String[]{sessionId});
    }

    /** Messages newer than this many rows are not summarised; the cost stays bounded per write. */
    private static final int MESSAGE_SUMMARY_SCAN_LIMIT = 32;
    private static final int PREVIEW_MAX_CHARACTERS = 32;

    /** Honour an explicit timestamp when the message carries one, without failing on malformed JSON. */
    private static long readRecordedTime(String json) {
        try {
            JSONObject message = new JSONObject(json);
            if (message.has("created_at")) {
                Object value = message.opt("created_at");
                if (value instanceof Number) return ((Number) value).longValue();
                if (value instanceof String && !((String) value).trim().isEmpty())
                    return Long.parseLong(((String) value).trim());
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static String extractUserText(String json) {
        try {
            JSONObject message = new JSONObject(json);
            if (!"user".equals(message.optString("role"))) return "";
            Object content = message.opt("content");
            if (content instanceof String) return (String) content;
            if (content instanceof org.json.JSONArray) {
                org.json.JSONArray parts = (org.json.JSONArray) content;
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null && "text".equals(part.optString("type"))) {
                        builder.append(part.optString("text"));
                    }
                }
                return builder.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String collapsePreview(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        int codePoints = collapsed.codePointCount(0, collapsed.length());
        if (codePoints <= PREVIEW_MAX_CHARACTERS) return collapsed;
        int end = collapsed.offsetByCodePoints(0, PREVIEW_MAX_CHARACTERS);
        return collapsed.substring(0, end) + "…";
    }

    @Override public List<JSONObject> loadHistory() {
        return run(() -> {
            List<JSONObject> history = new ArrayList<>();
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery(
                         "SELECT position, length(message_json) FROM messages WHERE session_id=? ORDER BY position", new String[]{sessionId})) {
                SQLiteDatabase db = helper.getReadableDatabase();
                while (cursor.moveToNext()) history.add(new JSONObject(
                    readMessage(db, cursor.getLong(0), cursor.getLong(1))));
            }
            return history;
        });
    }

    /** Read existing rows without ever materializing the whole JSON in a CursorWindow. */
    private String readMessage(SQLiteDatabase db, long position, long characters) {
        if (characters < 0) throw new IllegalStateException("Invalid message length");
        StringBuilder json = new StringBuilder();
        try (SQLiteStatement query = db.compileStatement(
                "SELECT substr(message_json, ?, ?) FROM messages WHERE session_id=? AND position=?")) {
            query.bindString(3, sessionId);
            query.bindLong(4, position);
            for (long offset = 0; offset < characters; offset += MESSAGE_CHUNK_CHARACTERS) {
                int count = (int) Math.min(MESSAGE_CHUNK_CHARACTERS, characters - offset);
                query.bindLong(1, offset + 1); // SQLite is one-based.
                query.bindLong(2, count);
                String chunk = query.simpleQueryForString();
                if (chunk == null || chunk.codePointCount(0, chunk.length()) != count)
                    throw new IllegalStateException("Incomplete message read; original data retained");
                json.append(chunk);
            }
        }
        return json.toString();
    }

    /** Read-only preview: do not instantiate/normalize every session's ContextManager. */
    public String loadFirstUserText() {
        return run(() -> {
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery(
                     "SELECT position, length(message_json) FROM messages WHERE session_id=? ORDER BY position", new String[]{sessionId})) {
                while (cursor.moveToNext()) {
                    JSONObject message = new JSONObject(readMessage(helper.getReadableDatabase(),
                        cursor.getLong(0), cursor.getLong(1)));
                    if (!"user".equals(message.optString("role"))) continue;
                    Object content = message.opt("content");
                    String text = "";
                    if (content instanceof String) text = (String) content;
                    else if (content instanceof org.json.JSONArray) {
                        org.json.JSONArray parts = (org.json.JSONArray) content;
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part != null && "text".equals(part.optString("type"))) {
                                text = part.optString("text"); break;
                            }
                        }
                    }
                    text = text.replaceAll("\\s+", " ").trim();
                    return text.length() > 32 ? text.substring(0, 32) + "…" : text;
                }
                return "";
            }
        });
    }

    @Override public void saveHistory(List<JSONObject> history) {
        long started = System.nanoTime();
        List<String> messages = snapshot(history);
        SNAPSHOT.record(System.nanoTime() - started);
        SNAPSHOT.report("history_snapshot", false);
        final long queued = System.nanoTime();
        IO.execute(() -> {
            long writing = System.nanoTime();
            WRITE_QUEUE.record(writing - queued);
            try (Helper helper = new Helper(context, databasePath)) {
                SQLiteDatabase db = helper.getWritableDatabase();
                db.beginTransaction();
                try { replace(db, messages); db.setTransactionSuccessful(); }
                finally { db.endTransaction(); }
            } catch (Exception e) {
                // Do not log SQL bind values or message contents.
                FileLogger.e("SqliteConversationStore", "历史保存失败，数据库保留上次已提交状态");
            } finally {
                WRITE.record(System.nanoTime() - writing);
                WRITE_QUEUE.report("history_write_queue", false);
                WRITE.report("history_sqlite_write", false);
            }
        });
    }

    @Override public int loadMaxRounds(int defaultValue) {
        return run(() -> {
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery(
                         "SELECT max_rounds FROM sessions WHERE session_id=?", new String[]{sessionId})) {
                return cursor.moveToFirst() ? cursor.getInt(0) : defaultValue;
            }
        });
    }

    @Override public JSONObject loadCompression() {
        return run(() -> {
            try (Helper helper = new Helper(context, databasePath);
                 Cursor cursor = helper.getReadableDatabase().rawQuery(
                     "SELECT state_json FROM request_context WHERE session_id=?", new String[]{sessionId})) {
                return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : null;
            }
        });
    }

    @Override public void saveCompression(JSONObject state) {
        final String json = state.toString();
        run(() -> {
            try (Helper helper = new Helper(context, databasePath)) {
                ContentValues row = new ContentValues();
                row.put("session_id", sessionId); row.put("state_json", json);
                if (helper.getWritableDatabase().insertWithOnConflict(
                    "request_context", null, row, SQLiteDatabase.CONFLICT_REPLACE) == -1)
                    throw new IllegalStateException("保存摘要失败，保留原请求上下文");
            }
            return null;
        });
    }

    @Override public void saveMaxRounds(int value) {
        IO.execute(() -> {
            try (Helper helper = new Helper(context, databasePath)) {
                ContentValues row = new ContentValues();
                row.put("max_rounds", value);
                helper.getWritableDatabase().update("sessions", row, "session_id=?", new String[]{sessionId});
            } catch (Exception e) { FileLogger.e("SqliteConversationStore", "保存会话轮数失败"); }
        });
    }

    private static <T> T run(Callable<T> work) {
        try { return IO.submit(work).get(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("会话存储操作被中断", e);
        } catch (ExecutionException e) {
            // Fail closed: never substitute empty/legacy history for a broken database.
            throw new IllegalStateException("无法读取或迁移会话存储；原数据未自动删除", e.getCause());
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context, String path) {
            // Android's default corruption handler can delete database files. Preserve evidence/data instead.
            super(context, path, null, SCHEMA_VERSION, db -> {
                throw new IllegalStateException("会话数据库损坏；已停止访问，未自动删除数据库");
            });
        }
        @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE sessions (session_id TEXT PRIMARY KEY NOT NULL, max_rounds INTEGER NOT NULL, "
                + "preview_text TEXT NOT NULL DEFAULT '', last_activity_at INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE messages (session_id TEXT NOT NULL, position INTEGER NOT NULL, message_json TEXT NOT NULL, "
                    + "PRIMARY KEY(session_id,position), FOREIGN KEY(session_id) REFERENCES sessions(session_id))");
            db.execSQL("CREATE INDEX sessions_activity_index ON sessions(last_activity_at DESC, rowid DESC)");
            createRequestContext(db);
        }
        private static void createRequestContext(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE request_context (session_id TEXT PRIMARY KEY NOT NULL, state_json TEXT NOT NULL, "
                + "FOREIGN KEY(session_id) REFERENCES sessions(session_id))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) createRequestContext(db);
            else if (oldVersion == 2 && newVersion == 3) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN preview_text TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE sessions ADD COLUMN last_activity_at INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE INDEX sessions_activity_index ON sessions(last_activity_at DESC, rowid DESC)");
                backfillSessionSummaries(db);
            }
            else if (oldVersion == 1 && newVersion == 3) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN preview_text TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE sessions ADD COLUMN last_activity_at INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE INDEX sessions_activity_index ON sessions(last_activity_at DESC, rowid DESC)");
                createRequestContext(db);
                backfillSessionSummaries(db);
            }
            else throw new IllegalStateException("Unsupported conversation database upgrade");
        }
        /**
         * Populate the cached summary columns for rows written before they existed.
         * A failure here leaves empty summaries: the list still renders, titles degrade to
         * the id prefix, and the next write to a session refreshes it properly.
         */
        private static void backfillSessionSummaries(SQLiteDatabase db) {
            List<String> ids = new ArrayList<>();
            try (Cursor cursor = db.rawQuery("SELECT session_id FROM sessions", null)) {
                while (cursor.moveToNext()) ids.add(cursor.getString(0));
            }
            for (String id : ids) {
                try { refreshSessionSummary(db, id); }
                catch (Exception ignored) { }
            }
        }
    }
}