package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 创建日历事件工具
 * 用于向安卓系统日历写入提醒/日程安排
 */
public class CreateCalendarEventTool implements Tool {
    private static final String TAG = "CreateCalendarEventTool";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private final Context context;

    public CreateCalendarEventTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "createCalendarEvent";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "createCalendarEvent");
            functionDef.put("description", "向安卓系统日历写入事件（提醒/日程安排）。需要 WRITE_CALENDAR 权限。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("title", new JSONObject()
                    .put("type", "string")
                    .put("description", "事件标题"))
                .put("description", new JSONObject()
                    .put("type", "string")
                    .put("description", "事件描述（可选）"))
                .put("startTime", new JSONObject()
                    .put("type", "string")
                    .put("description", "开始时间，ISO 8601 格式，如：2026-05-28T15:30:00+08:00"))
                .put("endTime", new JSONObject()
                    .put("type", "string")
                    .put("description", "结束时间，ISO 8601 格式，如：2026-05-28T16:30:00+08:00"))
                .put("reminderMinutes", new JSONObject()
                    .put("type", "integer")
                    .put("description", "提前提醒时间（分钟），默认 30 分钟"))
                .put("location", new JSONObject()
                    .put("type", "string")
                    .put("description", "事件地点（可选）"))
            );
            parameters.put("required", new JSONArray(new String[]{"title", "startTime", "endTime"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
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
     * 检查日历权限是否已授予
     */
    private boolean hasCalendarPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求日历权限
     */
    private void requestCalendarPermission() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            
            // 显示权限说明对话框
            new AlertDialog.Builder(context)
                .setTitle("需要日历权限")
                .setMessage("未来姐姐需要日历权限来创建日程提醒。是否授权？")
                .setPositiveButton("授权", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(activity,
                            new String[]{
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            },
                            PERMISSION_REQUEST_CODE);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 用户取消，不执行操作
                    }
                })
                .show();
        } else {
            Log.e(TAG, "Context is not an Activity, cannot request permission");
        }
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 检查权限
        if (!hasCalendarPermission()) {
            // 权限未授予，尝试请求
            requestCalendarPermission();
            
            // 返回提示信息，让用户手动授权
            JSONObject result = new JSONObject();
            result.put("status", "permission_required");
            result.put("message", "需要日历权限。请在弹出的权限对话框中授权，或手动在系统设置中开启日历权限。");
            result.put("permission_required", "READ_CALENDAR and WRITE_CALENDAR");
            return result;
        }

        // 解析参数
        String title = arguments.optString("title", null);
        String description = arguments.optString("description", "");
        String startTimeStr = arguments.optString("startTime", null);
        String endTimeStr = arguments.optString("endTime", null);
        int reminderMinutes = arguments.optInt("reminderMinutes", 30);
        String location = arguments.optString("location", "");

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("事件标题不能为空");
        }
        if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
        if (endTimeStr == null || endTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("结束时间不能为空");
        }

        // 解析时间
        long startTimeMillis = parseIso8601(startTimeStr);
        long endTimeMillis = parseIso8601(endTimeStr);

        // 获取日历账户 ID（使用第一个可用的本地日历账户）
        long calendarId = findOrCreateCalendarId();

        // 创建日历事件
        Uri eventUri = createCalendarEvent(calendarId, title, description, startTimeMillis, endTimeMillis, location, reminderMinutes);

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "成功创建日历事件：" + title);
        result.put("eventId", eventUri.getLastPathSegment());
        result.put("title", title);
        result.put("startTime", startTimeStr);
        result.put("endTime", endTimeStr);
        return result;
    }

    /**
     * 解析 ISO 8601 时间字符串为毫秒时间戳
     */
    private long parseIso8601(String isoString) throws Exception {
        // 简单实现，支持基本格式：2026-05-28T15:30:00+08:00
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        return sdf.parse(isoString).getTime();
    }

    /**
     * 查找或创建日历账户 ID
     */
    private long findOrCreateCalendarId() throws Exception {
        // 查询可用的日历账户
        Uri uri = CalendarContract.Calendars.CONTENT_URI;
        String[] projection = new String[] {
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
        };

        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long calendarId = cursor.getLong(0);
                    Log.d(TAG, "Found calendar ID: " + calendarId);
                    return calendarId;
                }
            } finally {
                cursor.close();
            }
        }

        throw new Exception("未找到可用的日历账户，请确保设备上有日历应用并已授权");
    }

    /**
     * 创建日历事件
     */
    private Uri createCalendarEvent(long calendarId, String title, String description, 
                                    long startTimeMillis, long endTimeMillis, 
                                    String location, int reminderMinutes) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, startTimeMillis);
        values.put(CalendarContract.Events.DTEND, endTimeMillis);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
        if (location != null && !location.isEmpty()) {
            values.put(CalendarContract.Events.EVENT_LOCATION, location);
        }

        Uri eventUri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
        Log.d(TAG, "Created event: " + eventUri);

        // 添加提醒
        addReminder(eventUri, reminderMinutes);

        return eventUri;
    }

    /**
     * 添加事件提醒
     */
    private void addReminder(Uri eventUri, int minutesBefore) {
        String eventId = eventUri.getLastPathSegment();
        ContentValues reminderValues = new ContentValues();
        reminderValues.put(CalendarContract.Reminders.EVENT_ID, Long.parseLong(eventId));
        reminderValues.put(CalendarContract.Reminders.MINUTES, minutesBefore);
        reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

        context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
        Log.d(TAG, "Added reminder: " + minutesBefore + " minutes before");
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户需要向安卓系统日历写入事件（如预约，会议、治疗等）时调用此工具。需要提供事件标题、开始时间、结束时间。可选提供描述、地点和提前提醒时间。注意：需要 WRITE_CALENDAR 权限。工具会自动请求权限，用户授权后即可正常使用。";
    }
}