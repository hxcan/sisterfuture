package com.stupidbeauty.sisterfuture.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃处理器
 * 捕获未处理的异常，将崩溃信息输出到外置存储
 * @author 未来姐姐
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    
    private static final String TAG = "CrashHandler";
    private static final String CRASH_LOG_DIR = "/sdcard/Download/sisterfuture_crashes/";
    
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    
    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    
    /**
     * 初始化崩溃处理器
     */
    public static void init(Context context) {
        CrashHandler handler = new CrashHandler(context);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Log.i(TAG, "✅ 全局崩溃检测器已初始化");
        
        // 确保日志目录存在
        File logDir = new File(CRASH_LOG_DIR);
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            Log.i(TAG, "创建日志目录：" + (created ? "成功" : "失败"));
        }
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        Log.e(TAG, "❗ 捕获到未处理异常: " + throwable.getMessage());
        
        // 生成崩溃报告
        String crashReport = generateCrashReport(thread, throwable);
        
        // 写入文件
        saveCrashLog(crashReport);
        
        // 交给默认处理器处理（可选：显示系统崩溃对话框）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }
    
    /**
     * 生成崩溃报告
     */
    private String generateCrashReport(Thread thread, Throwable throwable) {
        StringBuilder report = new StringBuilder();
        
        // 时间戳
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        String timestamp = sdf.format(new Date());
        report.append("========== 崩溃报告 ==========\n");
        report.append("时间：").append(timestamp).append("\n\n");
        
        // 线程信息
        report.append("【线程信息】\n");
        report.append("线程名：").append(thread.getName()).append("\n");
        report.append("线程 ID: ").append(thread.getId()).append("\n\n");
        
        // 异常信息
        report.append("【异常信息】\n");
        report.append("类型：").append(throwable.getClass().getName()).append("\n");
        report.append("消息：").append(throwable.getMessage() != null ? throwable.getMessage() : "null").append("\n");
        report.append("堆栈跟踪:\n");
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        report.append(sw.toString());
        pw.close();
        
        // 设备信息
        report.append("\n【设备信息】\n");
        report.append("品牌：").append(Build.BRAND).append("\n");
        report.append("型号：").append(Build.MODEL).append("\n");
        report.append("Android 版本：").append(Build.VERSION.RELEASE).append("\n");
        report.append("SDK 版本：").append(Build.VERSION.SDK_INT).append("\n");
        report.append("制造商：").append(Build.MANUFACTURER).append("\n");
        
        // 应用信息
        report.append("\n【应用信息】\n");
        report.append("包名：").append(context.getPackageName()).append("\n");
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            report.append("版本名：").append(packageInfo.versionName).append("\n");
            report.append("版本码：").append(packageInfo.versionCode).append("\n");
        } catch (Exception e) {
            report.append("获取版本信息失败：").append(e.getMessage()).append("\n");
        }
        
        report.append("\n==============================\n");
        
        return report.toString();
    }
    
    /**
     * 保存崩溃日志到外置存储
     */
    private void saveCrashLog(String crashReport) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
        String timestamp = sdf.format(new Date());
        String fileName = "crash_" + timestamp + ".log";
        File logFile = new File(CRASH_LOG_DIR + fileName);
        
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(crashReport);
            Log.i(TAG, "✅ 崩溃日志已保存：" + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "❌ 保存崩溃日志失败：" + e.getMessage());
        }
    }
}
