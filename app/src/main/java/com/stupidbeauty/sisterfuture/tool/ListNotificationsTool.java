package com.stupidbeauty.sisterfuture.tool;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
 * Android 版本差异：
 * - API 23+（Android 6.0+）：通过 getActiveNotifications() 直接获取
 * - API 30+（Android 11+）：增加 EXTRA_BIG_TEXT 等字段支持
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

        // 调用 NotificationListenerService 获取当前通知列表
        StatusBarNotification[] activeNotifications = getActiveNotifications(context);

        JSONArray notificationsArray = new JSONArray();

        if (activeNotifications == null) {
            // 用户未授权 NotificationListener 权限
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", "permission_denied");
            errorResult.put("message", "NotificationListener 权限未授予。请前往 系统设置 → 通知使用权 → 找到'未来姐姐'并开启。");
            return errorResult;
        }

        int count = 0;
        for (StatusBarNotification sbn : activeNotifications) {
            // 按包名过滤
            if (!packageFilter.isEmpty() && !packageFilter.equals(sbn.getPackageName())) {
                continue;
            }

            JSONObject notifObj = new JSONObject();

            // 包名
            notifObj.put("packageName", sbn.getPackageName());

            // 应用名（key 是 sbn.getPackageName()，要查 PackageManager）
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
     * 调用 NotificationListenerService 的 getActiveNotifications()
     *
     * 实现原理：
     * - 通过反射调用 NotificationListenerService 子类的 getActiveNotifications() 方法
     * - 要求：
     *   1. 必须在 AndroidManifest.xml 中注册一个 NotificationListenerService
     *   2. 用户必须在系统设置中授予本应用"通知使用权"
     *
     * @param context 应用上下文
     * @return 当前活跃通知列表；如果未授权返回 null
     */
    private StatusBarNotification[] getActiveNotifications(Context context) {
        try {
            // 方法 1：反射查找 NotificationListenerService 子类
            Class<?> listenerServiceClass = findNotificationListenerService();
            if (listenerServiceClass == null) {
                Log.w(TAG, "No NotificationListenerService subclass found. " +
                    "Please register one in AndroidManifest.xml with BIND_NOTIFICATION_LISTENER_SERVICE permission.");
                return null;
            }

            // 创建实例（仅用于反射调用；不会被系统真正使用）
            Object instance = listenerServiceClass.getDeclaredConstructor().newInstance();
            Method method = listenerServiceClass.getMethod("getActiveNotifications");
            Object result = method.invoke(instance);
            return (StatusBarNotification[]) result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to call getActiveNotifications via reflection", e);
            return null;
        }
    }

    /**
     * 查找 NotificationListenerService 子类
     *
     * 通过扫描 dex 中的所有类，找到继承自 android.service.notification.NotificationListenerService 的类。
     * 简化实现：通过反射加载常见类名。
     */
    private Class<?> findNotificationListenerService() {
        // 尝试加载已知的 Service 类名（按命名约定）
        String[] candidates = new String[] {
            "com.stupidbeauty.sisterfuture.NotificationsListenerService",
            "com.stupidbeauty.sisterfuture.service.NotificationsListenerService",
            "com.stupidbeauty.sisterfuture.NotificationListenerServiceImpl"
        };

        for (String className : candidates) {
            try {
                Class<?> clazz = Class.forName(className);
                if (android.service.notification.NotificationListenerService.class.isAssignableFrom(clazz)) {
                    Log.i(TAG, "Found NotificationListenerService: " + className);
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                // 类不存在，继续尝试下一个
            }
        }
        return null;
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