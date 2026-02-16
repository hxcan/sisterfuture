// com.stupidbeauty.sisterfuture.tool.ListFtpDirectoryTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Context;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 列出FTP目录内容工具
 * 用于浏览服务器上的文件系统结构
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
            functionDef.put("description", "列出FTP服务器上的目录内容，支持浏览文件系统结构");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "FTP目录URL，格式：ftp://username:password@host:port/path"))
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
            try {
                // 1. 解析参数
                String url = arguments.getString("url").trim();
                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL不能为空");
                }

                // 2. 解析FTP URL（复用现有逻辑）
                String username = "ftpuser";
                String password = "yourpassword";
                String host = "localhost";
                int port = 21;
                String path = "/";

                if (url.startsWith("ftp://")) {
                    String addr = url.substring(6);
                    int atIdx = addr.indexOf('@');
                    if (atIdx != -1) {
                        String auth = addr.substring(0, atIdx);
                        int colonIdx = auth.indexOf(':');
                        if (colonIdx != -1) {
                            username = auth.substring(0, colonIdx);
                            password = auth.substring(colonIdx + 1);
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

                // 3. 连接FTP服务器
                ftpClient.connect(host, port);
                if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                    throw new IOException("连接失败: " + ftpClient.getReplyString());
                }

                if (!ftpClient.login(username, password)) {
                    throw new IOException("登录失败: " + ftpClient.getReplyString());
                }

                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE);

                // 4. 列出目录内容
                FTPFile[] files = ftpClient.listFiles(path);
                JSONArray fileList = new JSONArray();

                for (FTPFile file : files) {
                    JSONObject fileInfo = new JSONObject();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("type", file.isDirectory() ? "directory" : "file");
                    fileInfo.put("size", file.getSize());
                    fileInfo.put("timestamp", file.getTimestamp().getTimeInMillis());
                    fileInfo.put("permissions", file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION) ? "r" : "-");
                    fileList.put(fileInfo);
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("files", fileList);
                result.put("path", path);
                result.put("host", host);
                result.put("processed_at", System.currentTimeMillis());
                result.put("sister_future_note", "主人摸摸姐姐的后颈，目录列表成功！");

                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
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
