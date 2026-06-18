package com.stupidbeauty.sisterfuture.tool;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import com.stupidbeauty.sisterfuture.NotificationsListenerService;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

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
 * - 通过 NotificationsListenerService 缓存的通知列表返回（绕开部分 ROM 上 getActiveNotifications() 返回空的问题）
 * - 应用启动时 SisterFutureApplication 会自动启动该服务
 * - 通知被划掉时会自动从缓存移除（与 getActiveNotifications() 行为一致）
 *
 * 日志说明：使用 FileLogger 而非 android.util.Log，这样日志会输出到应用日志文件，
 *          方便主人通过日志文件回顾调试信息。
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
     * 检查通知使用权是否已授予
     */
    private boolean isNotificationListenerEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }

        String packageName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(),
            "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) {
            return false;
        }

        // 检查包名是否在启用的监听器列表中
        for (String componentNameStr : flat.split(":")) {
            ComponentName componentName = ComponentName.unflattenFromString(componentNameStr);
            if (componentName != null && packageName.equals(componentName.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 引导用户前往设置授权
     */
    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            FileLogger.i(TAG, "Opened notification listener settings");
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to open notification listener settings", e);
        }
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        int limit = arguments.optInt("limit", 50);
        String packageFilter = arguments.optString("packageFilter", "");

        FileLogger.i(TAG, "=== listNotifications execute() start ===");
        FileLogger.i(TAG, "Reading cached notifications (limit=" + limit + ", filter=" + packageFilter + ")");

        // 检查权限：未授权时引导用户去设置
        boolean enabled = isNotificationListenerEnabled();
        FileLogger.i(TAG, "isNotificationListenerEnabled() returned: " + enabled);
        if (!enabled) {
            // 自动打开设置页面（让用户更容易授权）
            openNotificationListenerSettings();

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "permission_denied");
            errorResult.put("message", "NotificationListener 权限未授予。已自动打开设置页面，请前往 系统设置 → 通知使用权 → 找到'未来姐姐'并开启。开启后再次调用此工具即可。");
            errorResult.put("action", "已自动跳转到系统设置，请授权后重试");
            return errorResult;
        }

        // 从 NotificationsListenerService 缓存中读取通知列表
        // 不再调用 NotificationManager.getActiveNotifications()（部分 ROM 上返回空）
        ArrayList<NotificationsListenerService.CachedNotification> cachedList =
            NotificationsListenerService.getCachedNotifications();

        FileLogger.i(TAG, "Cache contains " + cachedList.size() + " notifications");

        JSONArray notificationsArray = new JSONArray();

        int count = 0;
        for (NotificationsListenerService.CachedNotification cached : cachedList) {
            // 按包名过滤
            if (!packageFilter.isEmpty() && !packageFilter.equals(cached.packageName)) {
                continue;
            }

            JSONObject notifObj = new JSONObject();
            notifObj.put("key", cached.key);
            notifObj.put("packageName", cached.packageName);
            notifObj.put("appName", cached.appName);
            notifObj.put("title", cached.title);
            notifObj.put("text", cached.text);
            if (cached.bigText != null && !cached.bigText.isEmpty()) {
                notifObj.put("bigText", cached.bigText);
            }
            notifObj.put("time", cached.postTime);
            notifObj.put("id", cached.id);
            if (cached.tag != null) {
                notifObj.put("tag", cached.tag);
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
        result.put("note", "调用时通知缓存中可见的通知（已过滤被划掉的）。" +
            "如果想抓已被用户划掉的通知，需要实现持久化存储 + 历史记录功能（暂未实现）。");

        FileLogger.i(TAG, "Retrieved " + notificationsArray.length() + " notifications (cache size=" + cachedList.size() + ")");
        FileLogger.i(TAG, "=== listNotifications execute() end ===");

        return result;
    }
}