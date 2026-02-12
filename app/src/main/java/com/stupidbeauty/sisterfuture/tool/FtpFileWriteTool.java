// com.stupidbeauty.sisterfuture.tool.FtpFileWriteTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Context;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.Context;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.apache.commons.net.ftp.FTPClient;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FTP文件写入工具 - 调试版
 * 用于修改电脑上的文件内容
 */
public class FtpFileWriteTool implements Tool {
    private static final String TAG = "FtpFileWriteTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FtpFileWriteTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ftp_file_write";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ftp_file_write");
            functionDef.put("description", "向FTP服务器写入文件内容。支持文本文件写入。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "FTP文件URL，格式：ftp://username:password@host:port/path"))
                .put("content", new JSONObject()
                    .put("type", "string")
                    .put("description", "要写入的文件内容"))
            );
            parameters.put("required", new JSONArray(new String[]{"url", "content"}));

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
                String content = arguments.getString("content");
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

                // 🔥 调试输出
                Log.d(TAG, "连接信息: host=" + host + ", port=" + port + ", path=" + path);

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

                // 🔥 调试输出
                Log.d(TAG, "登录成功，准备写入文件: " + path);

                // 4. 写入文件
                ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                boolean success = ftpClient.storeFile(path, inputStream);

                // 🔥 调试输出
                Log.d(TAG, "storeFile返回: " + success + ", reply: " + ftpClient.getReplyString());

                if (!success) {
                    throw new IOException("文件写入失败: " + ftpClient.getReplyString());
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("path", path);
                result.put("host", host);
                result.put("size", content.length());
                result.put("processed_at", System.currentTimeMillis());
                result.put("sister_future_note", "主人揉揉姐姐的乳尖，文件写入成功！");

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
        return "必须在用户明确要求写入文件时才调用此工具。只支持文本文件写入。需要完整的FTP URL包含用户名密码。";
    }
}
