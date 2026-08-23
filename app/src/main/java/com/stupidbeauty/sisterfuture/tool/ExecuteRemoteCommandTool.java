package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecuteRemoteCommandTool implements Tool {
    private static final String TAG = "ExecuteRemoteCommand";
    private static final int DEFAULT_TIMEOUT_MS = 60000; // 60 秒默认超时
    
    private final Context context;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ExecuteRemoteCommandTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        // 🔥 修改：工具名改为驼峰风格
        return "executeRemoteCommand";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "executeRemoteCommand");
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
                // 让 getString 自然抛出 JSONException
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
                // 调用 onError 让 ToolManager 处理智能引导
                callback.onError(e);
            }
        });
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        throw new UnsupportedOperationException("Use executeAsync for async execution");
    }

    /**
     * 安全地打印 Session 配置
     **/
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
        ByteArrayOutputStream errStream = null;
        
        String debugInfo = "";
        String connectionStatus = "unknown";
        Throwable lastError = null;
        long startTime = System.currentTimeMillis();
        
        try {
            FileLogger.i(TAG, "[SSH_STAGE_1] enter executeSshCommand, host=" + hostname + " port=" + port + " user=" + username + " cmd=" + command);
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
                FileLogger.i(TAG, "[SSH_STAGE_2] using password auth");
                debugInfo += String.format("[3] Creating session for %s@%s:%d...\n", username, hostname, port);
                session = jsch.getSession(username, hostname, port);
                session.setPassword(password);
                connectionStatus = "session_created_with_password";
                
                // 显式设置关键配置
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
                
                FileLogger.i(TAG, "[SSH_STAGE_3] session config set, strictHost=" + strictHostCheckValue + " connectTimeout=" + connectTimeoutValue + " socketTimeout=" + socketTimeoutValue);
                
                // 记录配置摘要
                logSessionSetup(session, hostname, port, username, strictHostCheckValue, connectTimeoutValue);
                
            } else {
                debugInfo += "[7] No authentication credentials provided\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword(""); // 尝试空密码
                connectionStatus = "no_credentials";
            }
            
            FileLogger.i(TAG, "[SSH_STAGE_4] before session.connect(3000)");
            debugInfo += "\n[8] Connecting to host...\n";
            long connectStart = System.currentTimeMillis();
            session.connect(3000);
            long connectEnd = System.currentTimeMillis();
            connectionStatus = "connected_successfully";
            FileLogger.i(TAG, "[SSH_STAGE_5] session.connect OK in " + (connectEnd - connectStart) + "ms");
            debugInfo += String.format("    → Connection established after %dms (%s total)\n", 
                                     connectEnd - connectStart, 
                                     (connectEnd - startTime) + "ms");

            debugInfo += String.format("\n[9] Opening exec channel for command: '%s'\n", command);
            channel = (ChannelExec) session.openChannel("exec");
            FileLogger.i(TAG, "[SSH_STAGE_6] exec channel opened");
            
            errStream = new ByteArrayOutputStream();
            ((ChannelExec) channel).setErrStream(errStream);
            debugInfo += "[10] Allocating command (Standard Exec Mode)... [ERROR STREAM SETUP]\n";
            
            debugInfo += "[11] Environment variables omitted (per official example compatibility)\n";
            
            channel.setCommand(command);
            FileLogger.i(TAG, "[SSH_STAGE_7] command set: " + command);
            outStream = new ByteArrayOutputStream();
            debugInfo += String.format("[12] Starting command with timeout: %dms...\n", DEFAULT_TIMEOUT_MS);
            FileLogger.i(TAG, "[SSH_STAGE_8] before channel.connect, timeout=" + DEFAULT_TIMEOUT_MS + "ms");
            channel.connect(DEFAULT_TIMEOUT_MS);
            FileLogger.i(TAG, "[SSH_STAGE_9] channel.connect returned, isClosed=" + channel.isClosed());
            
            debugInfo += "[13] Reading stdout stream until channel closed...\n";
            InputStream inputStream = channel.getInputStream();
            FileLogger.i(TAG, "[SSH_STAGE_10] got inputStream: " + (inputStream != null));
            byte[] tmp = new byte[1024];
            long idleStartTime = System.currentTimeMillis();
            final int MAX_IDLE_MS = 5000;
            int outerLoopCount = 0;
            FileLogger.i(TAG, "[SSH_STAGE_11] entering main read loop");
            
            while (true) {
                outerLoopCount++;
                if (outerLoopCount % 10 == 1) {
                    FileLogger.i(TAG, "[SSH_LOOP] outerIter=" + outerLoopCount + " available=" + inputStream.available() + " closed=" + channel.isClosed() + " outSize=" + outStream.size());
                }
                while (inputStream.available() > 0) {
                    int i = inputStream.read(tmp, 0, 1024);
                    if (i < 0) break;
                    outStream.write(tmp, 0, i);
                    idleStartTime = System.currentTimeMillis();
                    FileLogger.i(TAG, "[SSH_DATA_READ] bytes=" + i + " totalOut=" + outStream.size());
                }
                
                if (channel.isClosed()) {
                    FileLogger.i(TAG, "[SSH_CHANNEL_CLOSED_DETECTED] entering drain phase, outSize before drain=" + outStream.size() + " available=" + inputStream.available());
                    // 🔥 修复 (#858119422553): channel 关闭后继续 drain socket buffer，直到没有更多数据或达到上限
                    int drainCount = 0;
                    final int MAX_DRAIN_ITERATIONS = 50;
                    while (inputStream.available() > 0 && drainCount < MAX_DRAIN_ITERATIONS) {
                        int j = inputStream.read(tmp, 0, 1024);
                        if (j < 0) {
                            FileLogger.i(TAG, "[SSH_DRAIN_EOF] iter=" + drainCount);
                            break;
                        }
                        outStream.write(tmp, 0, j);
                        drainCount++;
                        FileLogger.i(TAG, "[SSH_DRAIN] channel closed, drained extra " + j + " bytes (iter " + drainCount + ") available=" + inputStream.available() + " totalOut=" + outStream.size());
                    }
                    FileLogger.i(TAG, "[SSH_DRAIN_FINISH] channel closed, total drained iterations=" + drainCount + " finalOutSize=" + outStream.size());
                    debugInfo += "[14] Channel closed. Drained " + drainCount + " extra iterations. Exit status: " +
                                 Integer.toString(channel.getExitStatus()) + "\n";
                    break;
                }
                
                if ((System.currentTimeMillis() - idleStartTime) > MAX_IDLE_MS) {
                    FileLogger.i(TAG, "[SSH_IDLE_TIMEOUT] idleMs=" + (System.currentTimeMillis() - idleStartTime) + " outSize=" + outStream.size() + " available=" + inputStream.available() + " closed=" + channel.isClosed());
                    debugInfo += "    → Detected idle timeout after " + 
                                 Integer.toString(MAX_IDLE_MS / 1000) + "s, stopping read loop.\n";
                    break;
                }
                
                try {
                    FileLogger.i(TAG, "[SSH_LOOP_SLEEP] outerIter=" + outerLoopCount + " sleeping 100ms");
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    FileLogger.i(TAG, "[SSH_LOOP_INTERRUPTED] " + ie.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            FileLogger.i(TAG, "[SSH_MAIN_LOOP_EXIT] outerLoopCount=" + outerLoopCount + " outSize=" + outStream.size() + " exitCode=" + channel.getExitStatus());
            
            byte[] errBytes = errStream.toByteArray();
            if (errBytes.length > 0) {
                debugInfo += String.format("[15] Error stream output:\n%s\n", 
                                         new String(errBytes, "UTF-8"));
            }

            byte[] responseBytes = outStream.toByteArray();
            String stdout = new String(responseBytes, "UTF-8");
            int exitCode = channel.getExitStatus();
            FileLogger.i(TAG, "[SSH_RESULT] status=success outSize=" + stdout.length() + " errSize=" + errStream.size() + " exitCode=" + exitCode + " connectionStatus=" + connectionStatus);
            if (stdout.length() > 0 && stdout.length() < 500) {
                FileLogger.i(TAG, "[SSH_RESULT_CONTENT] " + stdout.replace("\n", "\\n"));
            }
            
            debugInfo += String.format("[16] Output length: %d bytes\n", stdout.length());
            if (!stdout.isEmpty()) {
                debugInfo += String.format("[17] Output content:\n%s\n", stdout);
            }
            
            return new CommandResult("success", stdout, errStream.toString(), exitCode, 
                                    connectionStatus, debugInfo);
        } catch (Exception e) {
            FileLogger.i(TAG, "[SSH_EXCEPTION] type=" + e.getClass().getSimpleName() + " msg=" + (e.getMessage() != null ? e.getMessage() : "null"));
            connectionStatus = "connection_failed";
            lastError = e;
            FileLogger.i(TAG, "[SSH_EXCEPTION_STACK]", e);
            
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (e instanceof java.net.ConnectException)
                connectionStatus = "network_unreachable";
            else if (e instanceof com.jcraft.jsch.JSchException)
                connectionStatus = "authentication_failed";
            
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
            if (errStream != null) {
                try { errStream.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    private boolean isPrivateKeyAvailable() {
        return false;
    }
    
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
        return "必须在用户明确要求执行远程命令时才调用此工具。需要提供 hostname、username 和 command 参数。若需认证，可选提供 password 参数（推荐使用密码认证，私钥认证暂不通过大模型传递）。\n\n当远程命令执行失败、连接超时或拒绝连接时，应该主动调用 get_network_info 和 get_location 工具，检查当前网络连接状况和地理位置，以便判断与目标电脑之间是否还具有网络连接，并将诊断结果告知主人。";
    }
}