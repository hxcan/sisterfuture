package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.stupidbeauty.sisterfuture.utils.FileLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 读取手机短信内容列表的工具
 *
 * 用法：调用 getSmsList 工具，返回手机短信箱中的短信列表。
 * 每条短信包含：发件人号码、短信内容、时间戳、已读状态等。
 *
 * 需要的权限：
 * - READ_SMS（短信读取权限）
 *
 * 实现说明：
 * - 通过 Android SMS ContentProvider 查询系统短信库
 * - 短信存储在 content://sms/ URI
 * - 检测到权限未授予时，自动发起动态权限申请
 *
 * 日志说明：使用 FileLogger 而非 android.util.Log，这样日志会输出到应用日志文件，
 *          方便主人通过日志文件回顾调试信息。
 */
public class GetSmsListTool implements Tool {
    private static final String TAG = "GetSmsListTool";

    /** SMS ContentProvider URI */
    private static final Uri SMS_URI = Uri.parse("content://sms");

    /** 默认返回数量限制 */
    private static final int DEFAULT_LIMIT = 50;

    /** 最大返回数量限制（防止一次返回过多导致 OOM） */
    private static final int MAX_LIMIT = 200;

    /** READ_SMS 动态权限申请的请求码 */
    private static final int REQUEST_CODE_READ_SMS = 1001;

    private final Context context;

    public GetSmsListTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "getSmsList";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "getSmsList");
            functionDef.put("description", "读取手机短信箱中的短信列表。" +
                "返回每条短信的：发件人号码、短信内容、时间戳、已读状态等基本信息。" +
                "需要在系统设置中授予'读取短信'权限给本应用。" +
                "支持按发件人号码、关键词、时间范围过滤，以及分页查询。");

            JSONObject properties = new JSONObject()
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "返回结果数量限制，默认 50 条，最大 200 条"))
                .put("offset", new JSONObject()
                    .put("type", "integer")
                    .put("description", "分页偏移量，默认 0"))
                .put("address", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选，按发件人号码过滤（精确匹配或号码段前缀匹配）"))
                .put("keyword", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选，按短信内容关键词过滤（包含匹配）"))
                .put("date_from", new JSONObject()
                    .put("type", "integer")
                    .put("description", "可选，起始时间戳（毫秒），只返回该时间之后的短信"))
                .put("date_to", new JSONObject()
                    .put("type", "integer")
                    .put("description", "可选，结束时间戳（毫秒），只返回该时间之前的短信"));

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", properties);

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    /**
     * 检查 READ_SMS 权限是否已授予
     */
    private boolean hasReadSmsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 发起 READ_SMS 动态权限申请
     * 弹出系统权限申请对话框，不等待结果
     */
    private void requestReadSmsPermission() {
        FileLogger.i(TAG, "发起 READ_SMS 动态权限申请");

        // 尝试找到当前 Activity
        Activity activity = null;
        if (context instanceof Activity) {
            activity = (Activity) context;
        } else if (context instanceof android.content.ContextWrapper) {
            // 尝试从 ContextWrapper 找到 Activity
            android.content.Context baseContext = ((android.content.ContextWrapper) context).getBaseContext();
            if (baseContext instanceof Activity) {
                activity = (Activity) baseContext;
            }
        }

        if (activity == null) {
            FileLogger.w(TAG, "无法获取 Activity，权限申请可能无法弹出对话框");
            return;
        }

        try {
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_SMS},
                REQUEST_CODE_READ_SMS);
            FileLogger.i(TAG, "READ_SMS 权限申请已发起，请求码: " + REQUEST_CODE_READ_SMS);
        } catch (Exception e) {
            FileLogger.e(TAG, "发起 READ_SMS 权限申请失败", e);
        }
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        int limit = arguments.optInt("limit", DEFAULT_LIMIT);
        int offset = arguments.optInt("offset", 0);
        String address = arguments.optString("address", "").trim();
        String keyword = arguments.optString("keyword", "").trim();
        long dateFrom = arguments.optLong("date_from", 0L);
        long dateTo = arguments.optLong("date_to", 0L);

        // 限制最大返回数量
        if (limit <= 0 || limit > MAX_LIMIT) {
            limit = DEFAULT_LIMIT;
        }

        FileLogger.i(TAG, "=== getSmsList execute() start ===");
        FileLogger.i(TAG, "Parameters: limit=" + limit + ", offset=" + offset
            + ", address=" + address + ", keyword=" + keyword
            + ", dateFrom=" + dateFrom + ", dateTo=" + dateTo);

        // 检查权限
        if (!hasReadSmsPermission()) {
            FileLogger.w(TAG, "READ_SMS permission not granted, requesting dynamically");

            // 自动发起动态权限申请
            requestReadSmsPermission();

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "permission_denied");
            errorResult.put("message", "READ_SMS 权限未授予。已自动弹出权限申请对话框，请在弹窗中允许'读取短信'权限后再次调用此工具。");
            errorResult.put("action", "请在权限申请弹窗中授予短信读取权限");
            errorResult.put("permissionRequested", true);
            return errorResult;
        }

        // 构建查询条件
        StringBuilder selection = new StringBuilder();
        java.util.List<String> selectionArgs = new java.util.ArrayList<>();

        // address 过滤（精确匹配）
        if (!address.isEmpty()) {
            selection.append("address = ?");
            selectionArgs.add(address);
        }

        // 时间范围过滤
        if (dateFrom > 0) {
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("date >= ?");
            selectionArgs.add(String.valueOf(dateFrom));
        }
        if (dateTo > 0) {
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("date <= ?");
            selectionArgs.add(String.valueOf(dateTo));
        }

        // 排序：按时间倒序（最新的在前）
        String sortOrder = "date DESC";

        FileLogger.i(TAG, "Query: selection=" + selection.toString()
            + ", selectionArgs=" + selectionArgs.toString()
            + ", sortOrder=" + sortOrder);

        JSONArray smsArray = new JSONArray();
        int totalScanned = 0;
        int returned = 0;
        int skipped = 0;

        try {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(
                SMS_URI,
                new String[]{"_id", "address", "body", "date", "read", "type", "thread_id"},
                selection.length() > 0 ? selection.toString() : null,
                selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                sortOrder
            );

            if (cursor == null) {
                FileLogger.e(TAG, "Cursor is null - ContentResolver query failed");
                JSONObject errorResult = new JSONObject();
                errorResult.put("status", "error");
                errorResult.put("error", "query_failed");
                errorResult.put("message", "查询短信库失败。可能原因：1) 短信数据库被加密 2) 设备不支持查询 3) 系统权限异常");
                return errorResult;
            }

            try {
                int countIndex = cursor.getColumnIndex("_id");
                int addressIndex = cursor.getColumnIndex("address");
                int bodyIndex = cursor.getColumnIndex("body");
                int dateIndex = cursor.getColumnIndex("date");
                int readIndex = cursor.getColumnIndex("read");
                int typeIndex = cursor.getColumnIndex("type");
                int threadIdIndex = cursor.getColumnIndex("thread_id");

                FileLogger.i(TAG, "Cursor columns: _id=" + countIndex
                    + ", address=" + addressIndex
                    + ", body=" + bodyIndex
                    + ", date=" + dateIndex
                    + ", read=" + readIndex
                    + ", type=" + typeIndex
                    + ", thread_id=" + threadIdIndex);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                while (cursor.moveToNext()) {
                    totalScanned++;

                    // 处理 offset 跳过
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }

                    String smsAddress = cursor.getString(addressIndex);
                    String body = cursor.getString(bodyIndex);
                    long date = cursor.getLong(dateIndex);
                    int read = cursor.getInt(readIndex);
                    int type = cursor.getInt(typeIndex);
                    long threadId = cursor.getLong(threadIdIndex);

                    // 关键词过滤（包含匹配）
                    if (!keyword.isEmpty()) {
                        if (body == null || !body.contains(keyword)) {
                            continue;
                        }
                    }

                    JSONObject smsObj = new JSONObject();
                    smsObj.put("id", cursor.getLong(countIndex));
                    smsObj.put("address", smsAddress != null ? smsAddress : "");
                    smsObj.put("body", body != null ? body : "");
                    smsObj.put("date", date);
                    smsObj.put("dateFormatted", dateFormat.format(new Date(date)));
                    smsObj.put("read", read == 1);
                    // type: 1=inbox(收件箱), 2=sent(已发送), 3=draft, 4=outbox, 5=failed, 6=queued
                    String typeStr;
                    switch (type) {
                        case 1: typeStr = "inbox"; break;
                        case 2: typeStr = "sent"; break;
                        case 3: typeStr = "draft"; break;
                        case 4: typeStr = "outbox"; break;
                        case 5: typeStr = "failed"; break;
                        case 6: typeStr = "queued"; break;
                        default: typeStr = "unknown"; break;
                    }
                    smsObj.put("type", typeStr);
                    smsObj.put("threadId", threadId);

                    smsArray.put(smsObj);
                    returned++;

                    if (returned >= limit) {
                        break;
                    }
                }
            } finally {
                cursor.close();
            }
        } catch (SecurityException e) {
            FileLogger.e(TAG, "SecurityException - permission denied", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "permission_denied");
            errorResult.put("message", "READ_SMS 权限被拒绝：" + e.getMessage());
            return errorResult;
        } catch (Exception e) {
            FileLogger.e(TAG, "Exception while querying SMS", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "query_exception");
            errorResult.put("message", "查询短信时发生异常：" + e.getMessage());
            return errorResult;
        }

        // 构建返回结果
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "成功获取短信列表");
        result.put("count", smsArray.length());
        result.put("totalScanned", totalScanned);
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("messages", smsArray);
        result.put("note", "按时间倒序排列。type 字段：inbox(收件箱), sent(已发送), draft(草稿), outbox(发件箱), failed(失败), queued(队列)。");

        FileLogger.i(TAG, "Retrieved " + smsArray.length() + " SMS (scanned=" + totalScanned + ", offset=" + offset + ")");
        FileLogger.i(TAG, "=== getSmsList execute() end ===");

        return result;
    }
}
