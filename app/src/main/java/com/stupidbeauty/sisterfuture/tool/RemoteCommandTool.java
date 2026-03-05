package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteCommandTool implements Tool {
    private static final String TAG = "RemoteCommandTool";
    private static final int DEFAULT_TIMEOUT_MS = 60000; // 60 秒默认超时
    
    private final Context context;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RemoteCommandTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "execute_remote_command";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "execute_remote_command");
            functionDef.put("description", "执行远程 SSH 命令，支持密码或私钥认证");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            JSONObject properties = new JSONObject();
            properties.put("hostname", new JSONObject().put("type", "string").put("description", "目标主机 IP 或域名"));
            properties.put("port", new JSONObject().put("type", "integer").put("description", "SSH 端口"));
            properties.put("username", new JSONObject().put("type", "string").put("description", "登录用户名"));
            properties.put("password", new JSONObject().put("type", "string").put("description", "登录密码（可选）"));
            properties.put("command", new JSONObject().put("type", "string").put("description", "要执行的 Shell 命令"));
            
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{
                "hostname", "username", "command"
            }));

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
    public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        executor.execute(() -> {
            try {
                String hostname = arguments.getString("hostname");
                int port = arguments.optInt("port", 22);
                String username = arguments.getString("username");
                String command = arguments.getString("command");
                
                // 判断使用密码还是私钥认证
                String password = null;
                if (arguments.has("password") && !arguments.isNull("password")) {
                    password = arguments.getString("password");
                }

                CommandResult result = executeSshCommand(hostname, port, username, password, command);
                callback.onResult(result.toJson());
            } catch (Exception e) {
                Log.e(TAG, "Execution failed", e);
                try {
                    JSONObject errorResult = new JSONObject()
                        .put("status", "failed")
                        .put("stdout", "")
                        .put("stderr", e.getMessage())
                        .put("exitCode", -1)
                        .put("connectionStatus", "failed_to_initiate")
                        .put("debugInfo", "Unexpected exception: " + e.getClass().getName());
                    callback.onResult(errorResult);
                } catch (Exception ex) {
                    callback.onError(ex);
                }
            }
        });
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        throw new UnsupportedOperationException("Use executeAsync for async execution");
    }

    /**
     * 安全地打印 Session 配置（不使用内部 API）
     */
    private void logSessionSetup(Session session, String hostname, int port, String username, 
                                String hostKeyCheckPolicy, long connectTimeoutMs) {
        Log.d(TAG, "[SESSION_SETUP] Target: " + username + "@" + hostname + ":" + port);
        Log.d(TAG, "[SESSION_SETUP] HostKeyChecking policy: " + hostKeyCheckPolicy);
        Log.d(TAG, "[SESSION_SETUP] ConnectTimeout: " + connectTimeoutMs + "ms");
        Log.d(TAG, "[SESSION_SETUP] StrictHostKeyChecking: " + 
              (hostKeyCheckPolicy != null ? hostKeyCheckPolicy : "not explicitly set"));
    }

    private CommandResult executeSshCommand(String hostname, int port, String username, 
                                           String password, String command) {
        Session session = null;
        ChannelExec channel = null;
        ByteArrayOutputStream outStream = null;
        
        String debugInfo = "";
        String connectionStatus = "unknown";
        Throwable lastError = null;
        long startTime = System.currentTimeMillis();

        try {
            debugInfo += String.format("[1] Init JSch at %dms\n", startTime);
            long t1 = System.currentTimeMillis();
            JSch jsch = new JSch();
            debugInfo += String.format("    → JSch initialized in %dms\n", t1 - startTime);
            connectionStatus = "jsch_initialized";
            
            // 优先级：私钥 > 密码 > 无认证
            if (isPrivateKeyAvailable()) {
                debugInfo += "[2] Attempting key-based auth...\n";
                loadPrivateKey(jsch);
                connectionStatus = "key_auth_attempted";
            } else if (password != null && !password.isEmpty()) {
                debugInfo += String.format("[3] Creating session for %s@%s:%d...\n", username, hostname, port);
                session = jsch.getSession(username, hostname, port);
                session.setPassword(password);
                connectionStatus = "session_created_with_password";
                
                // 🔧 显式设置关键配置
                String strictHostCheckValue = "no";
                int connectTimeoutValue = 3000;
                int socketTimeoutValue = 30000;
                
                debugInfo += String.format("[4] Setting StrictHostKeyChecking=%s...\n", strictHostCheckValue);
                long t2 = System.currentTimeMillis();
                session.setConfig("StrictHostKeyChecking", strictHostCheckValue);
                debugInfo += String.format("    → Applied at %dms\n", t2 - startTime);
                
                debugInfo += String.format("[5] Setting ConnectTimeout=%dms...\n", connectTimeoutValue);
                session.setConfig("ConnectTimeout", String.valueOf(connectTimeoutValue));
                debugInfo += "[6] Setting SocketTimeout=30000ms...\n";
                session.setConfig("SocketTimeout", String.valueOf(socketTimeoutValue));
                
                // 📋 记录配置摘要
                logSessionSetup(session, hostname, port, username, strictHostCheckValue, connectTimeoutValue);
                
            } else {
                debugInfo += "[7] No authentication credentials provided\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword(""); // 尝试空密码（通常失败）
                connectionStatus = "no_credentials";
            }
            
            debugInfo += "\n[8] Connecting to host...\n";
            long connectStart = System.currentTimeMillis();
            session.connect(3000); // 3 秒连接超时
            long connectEnd = System.currentTimeMillis();
            connectionStatus = "connected_successfully";
            debugInfo += String.format("    → Connection established after %dms (%s total)\n", 
                                     connectEnd - connectStart, 
                                     (connectEnd - startTime) + "ms");

            debugInfo += String.format("\n[9] Opening exec channel for command: '%s'\n", command);
            // ✅ **核心修改：恢复为 ChannelExec（更稳定）**
            channel = (ChannelExec) session.openChannel("exec");
            
            // ✅ **不再分配 PTY**：避免 negotiation 问题（之前的 failed to send channel request 来源）
            debugInfo += "[10] Allocating command (No PTY)... (Standard Exec Mode)\n";
            
            // 🔧 设置环境变量（可选）
            debugInfo += "[11] Setting environment variables...\n";
            channel.setEnv("TERM", "xterm");
            channel.setEnv("LANG", "zh_CN.UTF-8");
            debugInfo += "    ENV: TERM=xterm, LANG=zh_CN.UTF-8\n";
            
            channel.setCommand(command);

            outStream = new ByteArrayOutputStream();

            debugInfo += String.format("[12] Starting command with timeout: %dms...\n", DEFAULT_TIMEOUT_MS);
            channel.connect(DEFAULT_TIMEOUT_MS);
            
            // ✅ **核心修复：持续读取流直到关闭（参考官方示例）**
            debugInfo += "[13] Reading output stream until channel closed...\n";
            InputStream inputStream = channel.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = 0;
            long idleStartTime = System.currentTimeMillis();
            final int MAX_IDLE_MS = 5000; // 最多等待 5 秒空闲时间
            
            while (true) {
                if (inputStream.available() > 0) {
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        outStream.write(buffer, 0, bytesRead);
                        idleStartTime = System.currentTimeMillis(); // 重置空闲计时器
                    }
                } else if (channel.isClosed()) {
                    // 如果通道已关闭且没有更多数据，读取完毕
                    break;
                } else {
                    // 检查是否超过最大空闲时间（防止死锁）
                    if ((System.currentTimeMillis() - idleStartTime) > MAX_IDLE_MS) {
                        debugInfo += "    → Detected idle timeout, stopping read loop.\n";
                        break;
                    }
                    Thread.sleep(100); // 短暂休眠
                }
            }
            
            debugInfo += "[14] Reading completed.\n";

            // ✅ 只有在完全读取后才获取退出码
            int exitCode = channel.getExitStatus();
            debugInfo += String.format("[15] Exit code: %d\n", exitCode);
            
            byte[] responseBytes = outStream.toByteArray();
            String stdout = new String(responseBytes, "UTF-8");
            debugInfo += String.format("[16] Output length: %d bytes\n", stdout.length());
            if (!stdout.isEmpty()) {
                debugInfo += String.format("[17] Output content:\n%s\n", stdout);
            }
            
            return new CommandResult("success", stdout, "", exitCode, connectionStatus, debugInfo);

        } catch (Exception e) {
            connectionStatus = "connection_failed";
            lastError = e;
            
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (e instanceof java.net.ConnectException) {
                connectionStatus = "network_unreachable";
            } else if (e instanceof com.jcraft.jsch.JSchException) {
                connectionStatus = "authentication_failed";
            }
            
            debugInfo += String.format("ERROR at %dms: %s\n", System.currentTimeMillis() - startTime, errorMsg);
            debugInfo += "Full stack trace available in logs.\n";
            
            return new CommandResult("failed", "", errorMsg, -1, connectionStatus, debugInfo);
        } finally {
            if (channel != null && channel.isConnected()) {
                debugInfo += "\n[FINALLY] Disconnecting channel...\n";
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                debugInfo += "[FINALLY] Disconnecting session...\n";
                session.disconnect();
            }
            if (outStream != null) {
                try { outStream.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * 检查是否存在可用的私钥
     */
    private boolean isPrivateKeyAvailable() {
        return false;
    }
    
    /**
     * 加载私钥到 JSch（当前为占位符实现）
     */
    private void loadPrivateKey(JSch jsch) {
        try {
            // TODO: 从 VFS 读取私钥并解析
        } catch (Exception e) {
            Log.e(TAG, "Failed to load private key", e);
        }
    }

    // 内部静态类用于封装结果
    private static class CommandResult {
        final String status;
        final String stdout;
        final String stderr;
        final int exitCode;
        final String connectionStatus;
        final String debugInfo;

        CommandResult(String status, String stdout, String stderr, int exitCode) {
            this(status, stdout, stderr, exitCode, "unknown", "");
        }
        
        CommandResult(String status, String stdout, String stderr, int exitCode, 
                     String connectionStatus, String debugInfo) {
            this.status = status;
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.connectionStatus = connectionStatus;
            this.debugInfo = debugInfo;
        }

        JSONObject toJson() throws Exception {
            JSONObject result = new JSONObject();
            result.put("status", status);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("exitCode", exitCode);
            result.put("connectionStatus", connectionStatus);
            result.put("debugInfo", debugInfo);
            return result;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求执行远程命令时才调用此工具。需要提供 hostname、username 和 command 参数。若需认证，可选提供 password 参数（推荐使用密码认证，私钥认证暂不通过大模型传递）。";
    }
}