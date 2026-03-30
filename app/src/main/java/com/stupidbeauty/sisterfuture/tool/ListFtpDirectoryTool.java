package com.stupidbeauty.sisterfuture.tool;

import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Context;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONObject;
import org.json.JSONArray;
import androidx.annotation.NonNull;
import android.util.Log;
import org.apache.commons.net.ftp.FTPFile;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.apache.commons.net.io.CopyStreamAdapter;


/**
 * 列出 FTP 目录内容工具
 * 用于浏览服务器上的文件系统结构
 * 
 * @author 未来姐姐
 * @version 1.5 (DEBUG)
 * @since 2026-03-16
 */
public class ListFtpDirectoryTool implements Tool {
    private static final String TAG = "ListFtpDirectoryTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ListFtpDirectoryTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "list_ftp_directory";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_ftp_directory");
            functionDef.put("description", "列出 FTP 服务器上的目录内容，支持浏览文件系统结构");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "FTP 目录 URL，格式：ftp://username:password@host:port/path"))
            );
            parameters.put("required", new JSONArray(new String[]{"url"}));

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
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            FTPClient ftpClient = new FTPClient();
            long startTime = System.currentTimeMillis();
            
            // 🔧【新增】创建数据流监听器，捕获所有传输的数据
            CopyStreamAdapter streamListener = new CopyStreamAdapter() {
                @Override
                public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                    FileLogger.d(TAG, "📦 [STREAM] 单次传输：" + bytesTransferred + " 字节，累计：" + totalBytesTransferred + " 字节");
                }
            };
            
            try {
                String url = arguments.getString("url").trim();
                FileLogger.d(TAG, "📡 [FTP] 开始执行，URL: " + url);
                
                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL 不能为空");
                }

                // 🔍 解析 URL
                String username = "ftpuser";
                String password = "yourpassword";
                String host = "localhost";
                int port = 21;
                String path = "";

                if (url.startsWith("ftp://")) {
                    String addr = url.substring(6);
                    int atIdx = addr.indexOf('@');
                    if (atIdx != -1) {
                        String auth = addr.substring(0, atIdx);
                        int colonIdx = auth.indexOf(':');
                        if (colonIdx != -1) {
                            username = auth.substring(0, colonIdx);
                            password = auth.substring(colonIdx + 1);
                        } else {
                            // 🔍 修复：处理 anonymous@host 格式（没有冒号）
                            username = auth;
                            password = "";
                        }
                        addr = addr.substring(atIdx + 1);
                    }
                    int slashIdx = addr.indexOf('/');
                    if (slashIdx != -1) {
                        String hostPort = addr.substring(0, slashIdx);
                        path = addr.substring(slashIdx);
                        int portIdx = hostPort.indexOf(':');
                        if (portIdx != -1) {
                            host = hostPort.substring(0, portIdx);
                            port = Integer.parseInt(hostPort.substring(portIdx + 1));
                        } else {
                            host = hostPort;
                        }
                    } else {
                        host = addr;
                    }
                }
                
                FileLogger.d(TAG, "🔑 [FTP] 解析结果 - Host: " + host + ", Port: " + port + ", Path: " + path + ", Username: " + username + ", Password: " + (password.isEmpty() ? "(empty)" : "***"));

                // 🔌 连接服务器
                FileLogger.d(TAG, "🔌 [FTP] 正在连接到 " + host + ":" + port);
                ftpClient.connect(host, port);
                FileLogger.d(TAG, "📶 [FTP] 连接成功，响应码：" + ftpClient.getReplyCode());
                FileLogger.d(TAG, "📝 [FTP] 服务器响应：" + ftpClient.getReplyString().trim());
                
                if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                    throw new IOException("连接失败：" + ftpClient.getReplyString());
                }

                // 🔐 登录
                FileLogger.d(TAG, "🔐 [FTP] 正在登录，用户名：" + username);
                boolean loginResult = ftpClient.login(username, password);
                FileLogger.d(TAG, "📝 [FTP] 登录结果：" + loginResult + ", 响应码：" + ftpClient.getReplyCode());
                FileLogger.d(TAG, "📝 [FTP] 服务器响应：" + ftpClient.getReplyString().trim());
                
                if (!loginResult) {
                    throw new IOException("登录失败：" + ftpClient.getReplyString());
                }

                // 🔄 进入被动模式
                FileLogger.d(TAG, "🔄 [FTP] 进入被动模式");
                ftpClient.enterLocalPassiveMode();
                
                // 📄 设置文件类型
                FileLogger.d(TAG, "📄 [FTP] 设置文件类型为 ASCII");
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE);

                // 🔧【新增】附加数据流监听器
                FileLogger.d(TAG, "🔧 [DEBUG] 附加数据流监听器到 FTP 客户端");
                ftpClient.setCopyStreamListener(streamListener);

                // 📋 列出文件（使用 listFiles）
                FileLogger.d(TAG, "📋 [FTP] 正在列出目录：" + path);
                long listStartTime = System.currentTimeMillis();
                FTPFile[] files = ftpClient.listFiles(path);
                long listEndTime = System.currentTimeMillis();
                FileLogger.d(TAG, "✅ [FTP] 目录列表完成，耗时：" + (listEndTime - listStartTime) + "ms, 文件数：" + files.length);
                
                // 🔍 输出原始文件列表
                for (int i = 0; i < files.length; i++) {
                    FTPFile file = files[i];
                    FileLogger.d(TAG, "  📁 [" + i + "] 名称：" + file.getName() + ", 类型：" + (file.isDirectory() ? "目录" : "文件") + ", 大小：" + file.getSize() + " 字节");
                }
                
                JSONArray fileList = new JSONArray();

                for (FTPFile file : files) {
                    JSONObject fileInfo = new JSONObject();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("type", file.isDirectory() ? "directory" : "file");
                    fileInfo.put("size", file.getSize());
                    if (file.getTimestamp() != null) {
                        fileInfo.put("timestamp", file.getTimestamp().getTimeInMillis());
                    }
                    fileInfo.put("permissions", file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION) ? "r" : "-");
                    fileList.put(fileInfo);
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("files", fileList);
                result.put("path", path);
                result.put("host", host);
                result.put("processed_at", System.currentTimeMillis());
                
                FileLogger.d(TAG, "✅ [FTP] 执行完成，总耗时：" + (System.currentTimeMillis() - startTime) + "ms, 返回文件数：" + fileList.length());

                callback.onResult(result);
            } catch (Exception e) {
                FileLogger.e(TAG, "❌ [FTP] 执行出错：" + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        FileLogger.d(TAG, "👋 [FTP] 断开连接");
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求列出目录内容时才调用此工具。返回包含文件名、类型、大小和时间戳的列表。可用于文件系统导航。";
    }
}