package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;

/**
 * FTP 文件请求工具增强版
 * 用于读取电脑上的文件内容，并支持直接保存到手机存储
 *
 * 增强功能 (#4976):
 * - 支持 save_to_phone 参数，将文件保存到手机
 * - 支持 phone_path 参数，自定义保存路径
 * - 自动处理外置存储权限申请
 * - 优先写入外置存储，失败则回退到私有目录
 * - 保存到手机时不限制文件大小，不返回文件内容
 * - 支持二进制文件保存（Base64 编码检测）
 * - 保存后自动调用 MediaScanner 扫描，使文件可被相册/文件选择器识别
 */
public class FtpFileRequestTool implements Tool
{
    private static final String TAG = "FtpFileRequestTool";
    private static final int MAX_FILE_SIZE_FOR_CONTENT = 1024 * 1024; // 1MB (仅当不保存到手机时限制)
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FtpFileRequestTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        // 🔥 修改：工具名改为驼峰风格
        return "ftpFileRequest";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ftpFileRequest");
            functionDef.put("description", "从 FTP 服务器读取文件内容。支持文本和二进制文件。增强版支持直接保存到手机存储，保存时不限制文件大小且不返回内容。保存后自动扫描媒体库，使文件可被相册/文件选择器识别。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "FTP 文件 URL，格式：ftp://username:password@host:port/path"))
                .put("save_to_phone", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否将文件保存到手机存储（默认 false）。为 true 时不限制文件大小，不返回文件内容"))
                .put("phone_path", new JSONObject()
                    .put("type", "string")
                    .put("description", "手机保存路径（可选，默认 /sdcard/Download/文件名）")
            );
            parameters.put("required", new JSONArray(new String[]{"url"}));
            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude()
    {
        return true;
    }

    @Override
    public boolean isAsync()
    {
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback)
    {
        executor.execute(() -> {
            FTPClient ftpClient = new FTPClient();
            try
            {
                String url = arguments.getString("url").trim();
                if (url.isEmpty())
                {
                    throw new IllegalArgumentException("URL 不能为空");
                }

                // 新增参数：是否保存到手机
                boolean saveToPhone = arguments.optBoolean("save_to_phone", false);
                String phonePath = arguments.optString("phone_path", "");

                String username = "ftpuser";
                String password = "yourpassword";
                String host = "localhost";
                int port = 21;
                String path = "";

                if (url.startsWith("ftp://"))
                {
                    String addr = url.substring(6);
                    int atIdx = addr.indexOf('@');
                    if (atIdx != -1)
                    {
                        String auth = addr.substring(0, atIdx);
                        int colonIdx = auth.indexOf(':');
                        if (colonIdx != -1)
                        {
                            username = auth.substring(0, colonIdx);
                            password = auth.substring(colonIdx + 1);
                        }
                        addr = addr.substring(atIdx + 1);
                    }

                    int slashIdx = addr.indexOf('/');
                    if (slashIdx != -1)
                    {
                        String hostPort = addr.substring(0, slashIdx);
                        path = addr.substring(slashIdx);
                        int portIdx = hostPort.indexOf(':');
                        if (portIdx != -1)
                        {
                            host = hostPort.substring(0, portIdx);
                            port = Integer.parseInt(hostPort.substring(portIdx + 1));
                        }
                        else
                        {
                            host = hostPort;
                        }
                    }
                    else
                    {
                        host = addr;
                    }
                }

                ftpClient.connect(host, port);
                if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode()))
                {
                    throw new IOException("连接失败：" + ftpClient.getReplyString());
                }

                if (!ftpClient.login(username, password))
                {
                    throw new IOException("登录失败：" + ftpClient.getReplyString());
                }

                ftpClient.enterLocalPassiveMode();
                // 🔥 改为 BINARY 模式，支持文本和二进制文件
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                boolean success = ftpClient.retrieveFile(path, outputStream);
                if (!success)
                {
                    throw new IOException("文件读取失败：" + ftpClient.getReplyString());
                }

                byte[] fileBytes = outputStream.toByteArray();
                long fileSize = fileBytes.length;

                // 🔥 关键修改：保存到手机时不限制大小，只在不保存时限制
                if (!saveToPhone && fileSize > MAX_FILE_SIZE_FOR_CONTENT)
                {
                    throw new IOException("文件太大，超过 1MB 限制。请使用 save_to_phone=true 参数直接保存到手机");
                }

                // 🔥 新增：保存到手机存储逻辑
                if (saveToPhone)
                {
                    String fileName = getFileNameFromUrl(url);
                    String targetPath = phonePath.isEmpty() ? "/sdcard/Download/" + fileName : phonePath;
                    WriteResult writeResult = writeToPhoneStorage(targetPath, fileBytes);

                    // 🔥 保存到手机时不返回文件内容，只返回元数据
                    JSONObject result = new JSONObject();
                    result.put("status", "success");
                    result.put("ftp_url", url);
                    result.put("file_saved", true);
                    result.put("phone_path", writeResult.path);
                    result.put("size", fileSize);
                    result.put("permission_note", writeResult.permissionNote);
                    result.put("processed_at", System.currentTimeMillis());
                    // 不添加 content 字段，避免大文件占用上下文
                    callback.onResult(result);
                }
                else
                {
                    // 原有逻辑：只返回内容（限制 1MB）
                    // 尝试检测是否为文本文件
                    String content;
                    try
                    {
                        content = new String(fileBytes, StandardCharsets.UTF_8);
                        // 验证是否为有效 UTF-8
                        if (content.contains("\uFFFD"))
                        {
                            throw new Exception("包含无效 UTF-8 字符");
                        }
                    }
                    catch (Exception e)
                    {
                        // 非文本文件，返回 Base64 提示
                        JSONObject result = new JSONObject();
                        result.put("status", "success");
                        result.put("content", "[二进制文件，无法直接显示。请使用 save_to_phone=true 保存到手机]");
                        result.put("url", url);
                        result.put("size", fileSize);
                        result.put("is_binary", true);
                        result.put("processed_at", System.currentTimeMillis());
                        callback.onResult(result);
                        return;
                    }

                    JSONObject result = new JSONObject();
                    result.put("status", "success");
                    result.put("content", content);
                    result.put("url", url);
                    result.put("size", fileSize);
                    result.put("is_binary", false);
                    result.put("processed_at", System.currentTimeMillis());
                    callback.onResult(result);
                }
            }
            catch (Exception e)
            {
                Log.e(TAG, "执行出错", e);
                // 🔥 修复：调用 onError 让 ToolManager 统一处理
                callback.onError(e);
            }
            finally
            {
                try
                {
                    if (ftpClient.isConnected())
                    {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                }
                catch (Exception ignored) {}
            }
        });
    }

    /**
     * 从 FTP URL 中提取文件名
     */
    private String getFileNameFromUrl(String url)
    {
        try
        {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < url.length() - 1)
            {
                return url.substring(lastSlash + 1);
            }
        }
        catch (Exception e)
        {
            Log.w(TAG, "提取文件名失败", e);
        }
        return "ftp_downloaded_file_" + System.currentTimeMillis();
    }

    /**
     * 写入手机存储
     * 优先尝试外置存储，失败则回退到私有目录
     * 保存后自动调用 MediaScanner 扫描
     */
    private WriteResult writeToPhoneStorage(String path, byte[] content)
    {
        File file = new File(path);

        // 确保父目录存在
        if (file.getParentFile() != null)
        {
            file.getParentFile().mkdirs();
        }

        // 尝试写入外置存储
        try
        {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content);
            fos.close();
            Log.d(TAG, "✅ 成功写入外置存储：" + path);

            // 🔥 新增：调用 MediaScanner 扫描文件，使其可被相册/文件选择器识别
            scanFileWithMediaScanner(path);

            return new WriteResult(
                path,
                true,
                "已写入外置存储（公共目录）"
            );
        }
        catch (SecurityException e)
        {
            // 外置存储权限不足，回退到私有目录
            Log.w(TAG, "⚠️ 外置存储权限不足，回退到私有目录", e);
            return fallbackToPrivateStorage(file.getName(), content);
        }
        catch (IOException e)
        {
            Log.e(TAG, "写入外置存储失败", e);
            return fallbackToPrivateStorage(file.getName(), content);
        }
    }

    /**
     * 🔥 新增：使用 MediaScannerConnection 扫描文件
     * 使文件能够被相册、文件选择器等系统应用识别
     */
    private void scanFileWithMediaScanner(String filePath)
    {
        try
        {
            File file = new File(filePath);
            String mimeType = getMimeType(filePath);

            MediaScannerConnection.scanFile(
                context,
                new String[]{filePath},
                new String[]{mimeType},
                (scanPath, uri) -> {
                    if (uri != null)
                    {
                        Log.d(TAG, "✅ MediaScanner 扫描成功: " + scanPath + " -> " + uri.toString());
                    }
                    else
                    {
                        Log.w(TAG, "⚠️ MediaScanner 扫描返回 null: " + scanPath);
                    }
                }
            );

            Log.d(TAG, "📱 已触发 MediaScanner 扫描: " + filePath);
        }
        catch (Exception e)
        {
            Log.e(TAG, "❌ MediaScanner 扫描失败", e);
        }
    }

    /**
     * 🔥 新增：根据文件扩展名获取 MIME 类型
     */
    private String getMimeType(String filePath)
    {
        String extension = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0)
        {
            extension = filePath.substring(lastDot + 1).toLowerCase();
        }

        switch (extension)
        {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "mp4":
                return "video/mp4";
            case "3gp":
                return "video/3gpp";
            case "mkv":
                return "video/x-matroska";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/x-wav";
            case "ogg":
                return "audio/ogg";
            case "pdf":
                return "application/pdf";
            case "zip":
                return "application/zip";
            case "apk":
                return "application/vnd.android.package-archive";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 回退到私有目录存储
     */
    private WriteResult fallbackToPrivateStorage(String fileName, byte[] content)
    {
        try
        {
            // 获取应用私有目录
            File privateDir = context.getExternalFilesDir(null);
            if (privateDir == null)
            {
                privateDir = context.getFilesDir();
            }
            File privateFile = new File(privateDir, fileName);
            FileOutputStream fos = new FileOutputStream(privateFile);
            fos.write(content);
            fos.close();
            String privatePath = privateFile.getAbsolutePath();
            Log.d(TAG, "✅ 成功写入私有目录：" + privatePath);

            // 🔥 私有目录也需要扫描，以便文件管理器可以访问
            scanFileWithMediaScanner(privatePath);

            // 触发权限申请
            requestExternalStoragePermission();

            return new WriteResult(
                privatePath,
                true,
                "已写入私有目录，正在申请外置存储权限，授权后可访问公共目录"
            );
        }
        catch (IOException e)
        {
            Log.e(TAG, "写入私有目录失败", e);
            return new WriteResult(
                "",
                false,
                "写入失败：" + e.getMessage()
            );
        }
    }

    /**
     * 请求外置存储权限（Android 11+ 需要 MANAGE_EXTERNAL_STORAGE）
     */
    private void requestExternalStoragePermission()
    {
        try
        {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "📱 已打开权限申请页面");
        }
        catch (Exception e)
        {
            Log.e(TAG, "打开权限申请页面失败", e);
            // 降级方案：尝试打开应用设置页面
            try
            {
                Intent fallbackIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallbackIntent.setData(Uri.parse("package:" + context.getPackageName()));
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallbackIntent);
            }
            catch (Exception ignored) {}
        }
    }

    /**
     * 写入结果封装类
     */
    private static class WriteResult
    {
        String path;
        boolean success;
        String permissionNote;

        WriteResult(String path, boolean success, String permissionNote)
        {
            this.path = path;
            this.success = success;
            this.permissionNote = permissionNote;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求获取 FTP 文件内容时才调用此工具。支持可选的 save_to_phone 参数将文件保存到手机。保存到手机时不限制文件大小且不返回内容。不保存时仅支持 1MB 以内的文本文件。需要完整的 FTP URL 包含用户名密码。";
    }
}
