package com.stupidbeauty.sisterfuture.tool;

import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
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

    /**
     * 使用主动模式捕获 FTP 服务器发送的原始数据流
     */
    private String captureFtpDataWithActiveMode(FTPClient ftpClient, String path) {
        StringBuilder result = new StringBuilder();
        result.append("=== 主动模式数据流捕获开始 ===\n");
        
        ServerSocket serverSocket = null;
        Socket dataSocket = null;
        
        try {
            // 创建 ServerSocket 用于接收数据连接
            serverSocket = new ServerSocket(0); // 自动分配端口
            int port = serverSocket.getLocalPort();
            FileLogger.d(TAG, "🔧 [DEBUG] 创建 ServerSocket 监听端口: " + port);
            
            // 将端口转换为 FTP 需要的格式 (高位字节 + 低位字节)
            String ip = "127.0.0.1"; // 本地回环地址
            String[] ipParts = ip.split("\\.");
            int portHigh = port / 256;
            int portLow = port % 256;
            String portCommand = ipParts[0] + "," + ipParts[1] + "," + ipParts[2] + "," + ipParts[3] + "," + portHigh + "," + portLow;
            
            // 发送 PORT 命令
            FileLogger.d(TAG, "🔧 [DEBUG] 发送 PORT 命令: " + portCommand);
            boolean portSuccess = ftpClient.sendCommand("PORT", portCommand);
            result.append("PORT 命令响应: ").append(ftpClient.getReplyString()).append("\n");
            FileLogger.d(TAG, "📝 [DEBUG] PORT 响应: " + ftpClient.getReplyString());
            
            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                result.append("PORT 命令失败!\n");
                FileLogger.e(TAG, "❌ [DEBUG] PORT 命令失败");
                return result.toString();
            }
            
            // 发送 NLST 命令
            FileLogger.d(TAG, "🔧 [DEBUG] 发送 NLST 命令: " + path);
            boolean listSuccess = ftpClient.sendCommand("NLST", path);
            result.append("NLST 命令响应码: ").append(listSuccess).append("\n");
            FileLogger.d(TAG, "📝 [DEBUG] NLST 响应码: " + listSuccess);
            
            // 立即接受数据连接（设置超时）
            serverSocket.setSoTimeout(10000); // 10秒超时
            FileLogger.d(TAG, "🔧 [DEBUG] 等待数据连接...");
            
            try {
                dataSocket = serverSocket.accept();
                FileLogger.d(TAG, "✅ [DEBUG] 数据连接已建立: " + dataSocket.getInetAddress());
                result.append("数据连接已建立 from: ").append(dataSocket.getInetAddress()).append("\n");
                
                // 读取原始数据流
                FileLogger.d(TAG, "🔧 [DEBUG] 开始读取数据流...");
                result.append("\n--- 原始数据内容 ---\n");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                String line;
                int lineCount = 0;
                int totalBytes = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    totalBytes += line.getBytes().length + 1; // +1 for newline
                    
                    // 记录原始行
                    result.append("行[").append(lineCount).append("](长度=").append(line.length()).append("): ").append(line).append("\n");
                    
                    // 同时记录到日志
                    FileLogger.d(TAG, "📄 [RAW] 行[" + lineCount + "]: " + line);
                }
                
                result.append("--- 原始数据结束 ---\n");
                result.append("共 ").append(lineCount).append(" 行，总计约 ").append(totalBytes).append(" 字节\n");
                
                FileLogger.d(TAG, "🔧 [DEBUG] 数据读取完成: " + lineCount + " 行，约 " + totalBytes + " 字节");
                
                reader.close();
                
            } catch (Exception e) {
                result.append("读取数据时出错: ").append(e.getMessage()).append("\n");
                FileLogger.e(TAG, "❌ [DEBUG] 读取数据出错: " + e.getMessage(), e);
            }
            
            // 获取最终响应
            int replyCode = ftpClient.getReplyCode();
            result.append("\n最终响应码: ").append(replyCode).append("\n");
            result.append("最终响应: ").append(ftpClient.getReplyString()).append("\n");
            FileLogger.d(TAG, "📝 [DEBUG] 最终响应: " + replyCode + " - " + ftpClient.getReplyString());
            
        } catch (Exception e) {
            result.append("\n发生错误: ").append(e.getClass().getSimpleName()).append(" - ").append(e.getMessage()).append("\n");
            FileLogger.e(TAG, "❌ [DEBUG] 主动模式数据捕获出错: " + e.getMessage(), e);
        } finally {
            // 清理资源
            try {
                if (dataSocket != null && !dataSocket.isClosed()) {
                    dataSocket.close();
                    FileLogger.d(TAG, "🔧 [DEBUG] 关闭数据 socket");
                }
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    FileLogger.d(TAG, "🔧 [DEBUG] 关闭 server socket");
                }
            } catch (Exception e) {
                FileLogger.e(TAG, "❌ [DEBUG] 清理资源出错: " + e.getMessage());
            }
        }
        
        result.append("=== 主动模式数据流捕获结束 ===\n");
        return result.toString();
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            FTPClient ftpClient = new FTPClient();
            long startTime = System.currentTimeMillis();
            StringBuilder allDebugInfo = new StringBuilder();
            
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
                
                FileLogger.d(TAG, "🔑 [FTP] 解析结果 - Host: " + host + ", Port: " + port + ", Path: " + path);

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

                // 设置文件类型
                FileLogger.d(TAG, "📄 [FTP] 设置文件类型为 ASCII");
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE);

                // 🔧【DEBUG】使用主动模式捕获原始数据流
                FileLogger.d(TAG, "🔧 [DEBUG] 使用主动模式捕获 FTP 数据流...");
                String activeModeData = captureFtpDataWithActiveMode(ftpClient, path);
                allDebugInfo.append(activeModeData);
                FileLogger.d(TAG, "📋 [DEBUG] 主动模式捕获结果:\n" + activeModeData);
                
                // 🔧【DEBUG】同时也用被动模式对比
                FileLogger.d(TAG, "🔧 [DEBUG] 切换到被动模式测试 listFiles()...");
                ftpClient.enterLocalPassiveMode();
                
                FileLogger.d(TAG, "📋 [FTP] 正在列出目录（被动模式）：" + path);
                long listStartTime = System.currentTimeMillis();
                FTPFile[] files = ftpClient.listFiles(path);
                long listEndTime = System.currentTimeMillis();
                FileLogger.d(TAG, "✅ [FTP] 目录列表完成，耗时：" + (listEndTime - listStartTime) + "ms, 文件数：" + (files != null ? files.length : 0));
                
                // 输出文件列表
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        FTPFile file = files[i];
                        FileLogger.d(TAG, "  📁 [" + i + "] 名称：" + file.getName());
                    }
                } else {
                    FileLogger.d(TAG, "⚠️ [FTP] listFiles() 返回 null");
                }
                
                JSONArray fileList = new JSONArray();

                if (files != null) {
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
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("files", fileList);
                result.put("path", path);
                result.put("host", host);
                result.put("processed_at", System.currentTimeMillis());
                result.put("debug_active_mode_raw_data", activeModeData);
                
                FileLogger.d(TAG, "✅ [FTP] 执行完成，总耗时：" + (System.currentTimeMillis() - startTime) + "ms");

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