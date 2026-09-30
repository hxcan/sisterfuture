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

    private void replace(SQLiteDatabase db, List<String> messages) {
        db.delete("messages", "session_id=?", new String[]{sessionId});
        for (int i = 0; i < messages.size(); i++) {
            ContentValues row = new ContentValues();
            row.put("session_id", sessionId);
            row.put("position", i);
            row.put("message_json", messages.get(i));
            db.insertOrThrow("messages", null, row);
        }
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
            super(context, path, null, 2, db -> {
                throw new IllegalStateException("会话数据库损坏；已停止访问，未自动删除数据库");
            });
        }
        @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE sessions (session_id TEXT PRIMARY KEY NOT NULL, max_rounds INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE messages (session_id TEXT NOT NULL, position INTEGER NOT NULL, message_json TEXT NOT NULL, "
                    + "PRIMARY KEY(session_id,position), FOREIGN KEY(session_id) REFERENCES sessions(session_id))");
            createRequestContext(db);
        }
        private static void createRequestContext(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE request_context (session_id TEXT PRIMARY KEY NOT NULL, state_json TEXT NOT NULL, "
                + "FOREIGN KEY(session_id) REFERENCES sessions(session_id))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) createRequestContext(db);
            else throw new IllegalStateException("Unsupported conversation database upgrade");
        }
    }
}
