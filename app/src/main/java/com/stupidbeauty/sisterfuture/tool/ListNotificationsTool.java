package com.stupidbeauty.sisterfuture.tool;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 读取手机通知栏通知列表的工具
 *
 * 用法：调用 listNotifications 工具，返回当前通知栏里所有可见通知的列表。
 * 每条通知包含：标题、内容、包名、应用名、时间戳等基本信息。
 *
 * 需要的权限：
 * - NotificationListenerService 授权（用户在系统设置中开启）
 *   设置路径：系统设置 → 通知使用权 → "未来姐姐"
 *
 * 实现说明：
 * - 直接调用 NotificationManager.getActiveNotifications()（Android 6+ 官方 API）
 * - 系统要求先注册一个 NotificationListenerService 子类（NotificationsListenerService）
 *   用户授权后系统才能允许此 API 返回数据
 */
public class ListNotificationsTool implements Tool {
    private static final String TAG = "ListNotificationsTool";
    private final Context context;

    public ListNotificationsTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "listNotifications";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "listNotifications");
            functionDef.put("description", "读取手机通知栏当前所有可见通知的列表。" +
                "返回每条通知的标题、内容、包名、应用名、时间戳等。" +
                "需要在系统设置中授予'通知使用权'给本应用。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "返回结果数量限制，默认 50 条"))
                .put("packageFilter", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选，按包名过滤（精确匹配）"))
            );

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

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        int limit = arguments.optInt("limit", 50);
        String packageFilter = arguments.optString("packageFilter", "");

        Log.i(TAG, "Reading active notifications (limit=" + limit + ", filter=" + packageFilter + ")");

        // 通过 NotificationManager 直接获取当前通知列表（Android 6+ 官方 API）
        // 前提：必须有一个 NotificationListenerService 子类（NotificationsListenerService）
        //       且用户已授予"通知使用权"
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();

        // 检查权限：未授权时返回 null
        if (activeNotifications == null) {
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "permission_denied");
            errorResult.put("message", "NotificationListener 权限未授予。请前往 系统设置 → 通知使用权 → 找到'未来姐姐'并开启。");
            return errorResult;
        }

        JSONArray notificationsArray = new JSONArray();

        int count = 0;
        for (StatusBarNotification sbn : activeNotifications) {
            // 按包名过滤
            if (!packageFilter.isEmpty() && !packageFilter.equals(sbn.getPackageName())) {
                continue;
            }

            JSONObject notifObj = new JSONObject();

            // 包名
            notifObj.put("packageName", sbn.getPackageName());

            // 应用名（通过 PackageManager 查）
            try {
                String appName = context.getPackageManager()
                    .getApplicationLabel(
                        context.getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))
                    .toString();
                notifObj.put("appName", appName);
            } catch (Exception e) {
                notifObj.put("appName", sbn.getPackageName());
            }

            // 通知 key（唯一标识）
            notifObj.put("key", sbn.getKey());

            // 时间戳（通知发出的时间，毫秒）
            notifObj.put("time", sbn.getPostTime());

            // 通知 ID
            notifObj.put("id", sbn.getId());

            // tag（如果有）
            if (sbn.getTag() != null) {
                notifObj.put("tag", sbn.getTag());
            }

            // 解析 Notification 对象的 title 和 text
            Notification notification = sbn.getNotification();
            if (notification != null) {
                parseNotificationFields(notification, notifObj);
            }

            notificationsArray.put(notifObj);
            count++;

            if (limit > 0 && count >= limit) {
                break;
            }
        }

        // 构建返回结果
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "成功获取通知列表");
        result.put("count", notificationsArray.length());
        result.put("notifications", notificationsArray);
        result.put("note", "调用时通知栏中可见的通知。如果想抓已被用户划掉的通知，需要实现 NotificationListenerService 持续监听并保存。");

        Log.i(TAG, "Retrieved " + notificationsArray.length() + " notifications");

        return result;
    }

    /**
     * 解析 Notification 对象，提取 title / text / bigText
     */
    private void parseNotificationFields(Notification notification, JSONObject target) throws Exception {
        // Notification.extras（Bundle）从 API 19 开始可用
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            target.put("title", "");
            target.put("text", "");
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) {
            target.put("title", "");
            target.put("text", "");
            return;
        }

        // 标准字段
        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");

        target.put("title", title);
        target.put("text", text);

        // API 21+（Android 5.0+）：big text（展开后的长文本）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String bigText = extras.getString(Notification.EXTRA_BIG_TEXT, "");
            if (!bigText.isEmpty()) {
                target.put("bigText", bigText);
            }
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户询问手机通知、短信验证码、社保通知、银行通知等内容时，调用 listNotifications 工具" +
            "获取通知栏当前可见的通知。可以按 packageFilter 过滤特定应用（如 com.tencent.mm 微信）。" +
            "返回的通知列表包含标题、内容、包名、应用名、时间戳等。";
    }
}