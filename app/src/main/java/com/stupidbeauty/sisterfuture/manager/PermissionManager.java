// PermissionManager.java
package com.stupidbeauty.sisterfuture.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * 权限管理器
 *
 * 负责处理 Android 运行时权限的检查和请求
 **/
public class PermissionManager {

    private static final String TAG = "PermissionManager";

    // 权限常量定义（修复拼写错误）
    public static final String PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String PERMISSION_RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO;
    public static final String PERMISSION_FINE_LOCATION = android.Manifest.permission.ACCESS_FINE_LOCATION; // 修复拼写错误
    public static final String PERMISSION_INSTALL_PACKAGE = android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
    public static final String PERMISSION_POST_NOTIFICATIONS = android.Manifest.permission.POST_NOTIFICATIONS;

    // 权限请求常量
    public static final int PERMISSIONS_REQUEST = 1;
    public static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    public static final int NOTIFICATION_ACCESS_REQUEST = 1002;

    private final Activity activity;
    private final PermissionCallback callback;

    /**
     * 权限回调接口
     **/
    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionDenied(String permission);
        void onNotificationPermissionDenied();
        void onNotificationPermissionPermanentlyDenied();
    }

    public PermissionManager(Activity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * 检查是否有所需的所有权限
     * @return true if all permissions granted
     **/
    public boolean hasPermission() {
        boolean result = false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            String[] permissions = {
                PERMISSION_STORAGE,
                PERMISSION_RECORD_AUDIO,
                PERMISSION_FINE_LOCATION
            };

            result = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    result = false;
                    break;
                }
            }
        } else {
            result = true;
        }

        FileLogger.d(TAG, "hasPermission: " + result);
        return result;
    }

    /**
     * 请求所需的所有权限
     **/
    public void requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_STORAGE) ||
                shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO) ||
                shouldShowRequestPermissionRationale(PERMISSION_FINE_LOCATION) ||
                shouldShowRequestPermissionRationale(PERMISSION_INSTALL_PACKAGE)) {

                FileLogger.d(TAG, "Should show permission rationale");
            }

            String[] permissions = {
                PERMISSION_STORAGE,
                PERMISSION_RECORD_AUDIO,
                PERMISSION_FINE_LOCATION
            };

            ActivityCompat.requestPermissions(activity, permissions, PERMISSIONS_REQUEST);
            FileLogger.d(TAG, "requestPermission called");
        }
    }

    /**
     * 检查并请求权限
     **/
    public void checkPermission() {
        if (hasPermission()) {
            FileLogger.d(TAG, "Already has all permissions");
            if (callback != null) {
                callback.onAllPermissionsGranted();
            }
        } else {
            FileLogger.d(TAG, "Requesting permissions");
            requestPermission();
        }
    }

    /**
     * 处理权限请求结果
     * @param requestCode 请求码
     * @param permissions 权限数组
     * @param grantResults 授权结果数组
     **/
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FileLogger.d(TAG, "POST_NOTIFICATIONS permission granted");
            } else {
                FileLogger.w(TAG, "POST_NOTIFICATIONS permission denied");
                if (callback != null) {
                    if (!shouldShowRequestPermissionRationale(PERMISSION_POST_NOTIFICATIONS)) {
                        // 用户选了"不再询问"，需要引导到设置
                        FileLogger.w(TAG, "POST_NOTIFICATIONS permanently denied, need to go to settings");
                        callback.onNotificationPermissionPermanentlyDenied();
                    } else {
                        callback.onNotificationPermissionDenied();
                    }
                }
            }
        } else if (requestCode == PERMISSIONS_REQUEST) {
            boolean allGranted = true;
            String deniedPermission = null;

            if (grantResults.length > 0) {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        deniedPermission = permissions[i];
                        break;
                    }
                }
            }

            if (allGranted) {
                FileLogger.d(TAG, "All permissions granted");
                if (callback != null) {
                    callback.onAllPermissionsGranted();
                }
            } else {
                FileLogger.w(TAG, "Permission denied: " + deniedPermission);
                if (callback != null && deniedPermission != null) {
                    callback.onPermissionDenied(deniedPermission);
                }
            }
        }
    }

    /**
     * 检查 POST_NOTIFICATIONS 权限（发送通知）是否已授权
     * @return true if permission granted
     **/
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int result = ContextCompat.checkSelfPermission(activity, PERMISSION_POST_NOTIFICATIONS);
            return result == PackageManager.PERMISSION_GRANTED;
        }
        // Android 13 以下不需要此权限
        return true;
    }

    /**
     * 请求通知权限
     **/
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, PERMISSION_POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                FileLogger.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(activity,
                        new String[]{PERMISSION_POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            } else {
                FileLogger.d(TAG, "POST_NOTIFICATIONS already granted");
            }
        }
    }

    /**
     * 完整的通知权限检查流程：
     * 1. 检查权限
     * 2. 已授权则直接返回
     * 3. 未授权则先弹窗提示
     * 4. 用户同意后动态申请
     * 5. 申请被永久拒绝时引导到系统设置
     **/
    public void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 13 以下不需要
            FileLogger.d(TAG, "Android < 13, no need to check notification permission");
            return;
        }

        // 1. 检查权限
        if (hasNotificationPermission()) {
            FileLogger.d(TAG, "✅ POST_NOTIFICATIONS already granted");
            return;
        }

        // 2. 未授权：先弹窗提示
        FileLogger.d(TAG, "POST_NOTIFICATIONS not granted, showing prompt dialog");
        new AlertDialog.Builder(activity)
            .setTitle("需要发送通知权限")
            .setMessage("为了让您能收到姐姐的重要通知（如 AI 回复完成、紧急提醒等），\n" +
                       "需要您授予\"发送通知\"的权限。\n\n" +
                       "点击\"申请权限\"按钮，系统会弹出权限申请对话框。\n" +
                       "如果您之前选择了\"不再询问\"，则需要跳转到系统设置手动开启。")
            .setPositiveButton("申请权限", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 3. 动态申请
                    requestNotificationPermission();
                }
            })
            .setNegativeButton("暂不开启", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (callback != null) {
                        callback.onNotificationPermissionDenied();
                    }
                }
            })
            .setCancelable(false)
            .show();
    }

    /**
     * 引导用户跳转到应用详情页（用于"不再询问"的情况）
     **/
    public void openAppSettings() {
        FileLogger.d(TAG, "Opening app settings");
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }

    /**
     * 显示"权限被永久拒绝"的对话框，引导用户去设置
     **/
    public void showPermissionPermanentlyDeniedDialog() {
        new AlertDialog.Builder(activity)
            .setTitle("权限被拒绝")
            .setMessage("您之前选择了\"不再询问\"，无法再通过弹窗申请权限。\n\n" +
                       "请点击\"去设置\"按钮，跳转到应用详情页面，\n" +
                       "然后在\"通知\"或\"权限\"中手动开启。")
            .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openAppSettings();
                }
            })
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show();
    }

    /**
     * 检查是否应该显示权限解释
     **/
    private boolean shouldShowRequestPermissionRationale(String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}