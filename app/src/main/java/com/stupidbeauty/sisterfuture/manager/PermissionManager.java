// PermissionManager.java
package com.stupidbeauty.sisterfuture.manager;

import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * 权限管理器
 * 
 * 负责处理 Android 运行时权限的检查和请求
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    
    // 权限常量定义（修复拼写错误）
    public static final String PERMISSION_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String PERMISSION_RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO;
    public static final String PERMISSION_FINE_LOCATION = android.Manifest.permission.ACCESS_FINE_LOCATION; // 修复拼写错误
    public static final String PERMISSION_INSTALL_PACKAGE = android.Manifest.permission.REQUEST_INSTALL_PACKAGES;
    
    // 权限请求常量
    public static final int PERMISSIONS_REQUEST = 1;
    public static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    
    private final Activity activity;
    private final PermissionCallback callback;
    
    /**
     * 权限回调接口
     */
    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionDenied(String permission);
        void onNotificationPermissionDenied();
    }
    
    public PermissionManager(Activity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }
    
    /**
     * 检查是否有所需的所有权限
     * @return true if all permissions granted
     */
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
     */
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
     */
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
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FileLogger.d(TAG, "POST_NOTIFICATIONS permission granted");
            } else {
                FileLogger.w(TAG, "POST_NOTIFICATIONS permission denied");
                if (callback != null) {
                    callback.onNotificationPermissionDenied();
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
     * 请求通知权限
     */
    public void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                FileLogger.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            }
        }
    }
    
    /**
     * 检查是否应该显示权限解释
     */
    private boolean shouldShowRequestPermissionRationale(String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}