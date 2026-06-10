package com.stupidbeauty.sisterfuture.tool;

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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;

/**
 * FTP 文件写入工具增强版
 * 用于向 FTP 服务器写入文件内容
 */
public class FtpFileWriteTool implements Tool {
    private static final String TAG = "FtpFileWriteTool";
    private static final long MAX_FILE_SIZE = 2048L * 1024 * 1024; // 2 GiB (注意 L 后缀防止溢出)
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FtpFileWriteTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ftpFileWrite";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ftpFileWrite");
            functionDef.put("description", "向 FTP 服务器写入文件内容。支持文本文件写入和从手机本机读取文件上传（支持 APK、图片、视频等二进制文件）。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "FTP 文件 URL，格式：ftp://username:password@host:port/path"))
                .put("content", new JSONObject()
                    .put("type", "string")
                    .put("description", "要写入的文件内容（当 read_from_phone=false 时使用）"))
                .put("read_from_phone", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否从手机读取文件内容（默认 false）。为 true 时忽略 content 参数，从 phone_path 读取文件"))
                .put("phone_path", new JSONObject()
                    .put("type", "string")
                    .put("description", "手机上的文件路径（当 read_from_phone=true 时使用）。支持文本和二进制文件，最大 2 GiB"))
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
                String url = arguments.getString("url").trim();
                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL 不能为空");
                }

                boolean readFromPhone = arguments.optBoolean("read_from_phone", false);
                String phonePath = arguments.optString("phone_path", "");

                connectAndLogin(ftpClient, url);

                if (readFromPhone) {
                    if (phonePath.isEmpty()) {
                        throw new IllegalArgumentException("read_from_phone=true 时必须提供 phone_path");
                    }
                    uploadStreamFromPhone(ftpClient, url, phonePath);
                } else {
                    String content = arguments.getString("content");
                    byte[] fileContent = content.getBytes(StandardCharsets.UTF_8);
                    uploadBytesToFTP(ftpClient, url, fileContent);
                }

            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                callback.onError(e);
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

    /**
     * 从手机文件流式上传到 FTP (最小化修改：直接使用 FileInputStream)
     */
    private void uploadStreamFromPhone(FTPClient ftpClient, String url, String phonePath) throws IOException {
        File file = new File(phonePath);
        
        if (!file.exists()) {
            throw new IOException("手机文件不存在：" + phonePath);
        }
        if (!file.canRead()) {
            throw new IOException("无法读取手机文件，请检查权限：" + phonePath);
        }

        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE) {
            throw new IOException("文件太大，超过 2 GiB 限制：" + phonePath);
        }

        String path = extractPath(url);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        
        // ✅ 关键：直接使用 FileInputStream，不经过 ByteArrayOutputStream
        try (FileInputStream fis = new FileInputStream(file)) {
            boolean success = ftpClient.storeFile(path, fis);
            if (!success) {
                throw new IOException("文件写入失败：" + ftpClient.getReplyString());
            }
        }
        
        Log.i(TAG, "✅ 流式上传成功: " + phonePath);
    }

    /**
     * 将字节数组上传到 FTP (适用于小内容)
     */
    private void uploadBytesToFTP(FTPClient ftpClient, String url, byte[] content) throws IOException {
        String path = extractPath(url);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            boolean success = ftpClient.storeFile(path, inputStream);
            if (!success) {
                throw new IOException("内容写入失败：" + ftpClient.getReplyString());
            }
        }
    }

    /**
     * 解析 URL 并连接登录
     */
    private void connectAndLogin(FTPClient ftpClient, String url) throws IOException {
        String username = "ftpuser";
        String password = "yourpassword";
        String host = "localhost";
        int port = 21;

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

        ftpClient.connect(host, port);
        if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
            throw new IOException("连接失败：" + ftpClient.getReplyString());
        }

        if (!ftpClient.login(username, password)) {
            throw new IOException("登录失败：" + ftpClient.getReplyString());
        }
    }

    /**
     * 从 URL 中提取远程路径
     */
    private String extractPath(String url) {
        if (url.startsWith("ftp://")) {
            String addr = url.substring(6);
            int atIdx = addr.indexOf('@');
            if (atIdx != -1) {
                addr = addr.substring(atIdx + 1);
            }
            int slashIdx = addr.indexOf('/');
            if (slashIdx != -1) {
                return addr.substring(slashIdx);
            }
        }
        return "";
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求写入文件时才调用此工具。只支持文本文件写入。需要完整的 FTP URL 包含用户名密码。";
    }
}