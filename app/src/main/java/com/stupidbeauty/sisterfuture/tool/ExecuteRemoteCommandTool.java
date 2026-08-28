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
        long entryTime = System.currentTimeMillis();
        FileLogger.i(TAG, "[EXECUTE_ASYNC_ENTRY] 进入 executeAsync | thread=" + Thread.currentThread().getName() + " | time=" + entryTime + "ms");
        FileLogger.i(TAG, "[EXECUTE_ASYNC_ARGS] arguments keys=" + (arguments != null ? arguments.keys().toString() : "null"));
        executor.execute(() -> {
            long executorEnterTime = System.currentTimeMillis();
            FileLogger.i(TAG, "[EXECUTOR_TASK_START] executor 线程开始执行 | delay=" + (executorEnterTime - entryTime) + "ms | thread=" + Thread.currentThread().getName());
            try {
                // 让 getString 自然抛出 JSONException
                String hostname = arguments.getString("hostname");
                int port = arguments.optInt("port", 22);
                String username = arguments.getString("username");
                String command = arguments.getString("command");

                FileLogger.i(TAG, "[ARGS_PARSED] hostname=" + hostname + " | port=" + port + " | username=" + username + " | command=" + command);

                // 判断使用密码还是私钥认证
                String password = null;
                if (arguments.has("password") && !arguments.isNull("password")) {
                    password = arguments.getString("password");
                    FileLogger.i(TAG, "[PASSWORD_PROVIDED] 密码已提供，长度=" + password.length() + " | 明文=" + password);
                } else {
                    FileLogger.i(TAG, "[NO_PASSWORD] 未提供密码");
                }

                FileLogger.i(TAG, "[AUTH_BRANCH] " + (isPrivateKeyAvailable() ? "走私钥认证" : "走密码认证"));

                CommandResult result = executeSshCommand(hostname, port, username, password, command);
                FileLogger.i(TAG, "[EXECUTE_SSH_DONE] SSH 执行完成，耗时=" + (System.currentTimeMillis() - executorEnterTime) + "ms");
                callback.onResult(result.toJson());
                FileLogger.i(TAG, "[CALLBACK_ONRESULT_DONE] callback.onResult 完成");
            } catch (Exception e) {
                FileLogger.e(TAG, "[EXECUTE_ASYNC_EXCEPTION] " + e.getClass().getSimpleName() + " | msg=" + e.getMessage(), e);
                // 调用 onError 让 ToolManager 处理智能引导
                callback.onError(e);
                FileLogger.i(TAG, "[CALLBACK_ONERROR_DONE] callback.onError 完成");
            } finally {
                FileLogger.i(TAG, "[EXECUTOR_TASK_END] executor 任务结束 | total=" + (System.currentTimeMillis() - executorEnterTime) + "ms");
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
        FileLogger.i(TAG, "[SESSION_SETUP] Target: " + username + "@" + hostname + ":" + port);
        FileLogger.i(TAG, "[SESSION_SETUP] HostKeyChecking policy: " + hostKeyCheckPolicy);
        FileLogger.i(TAG, "[SESSION_SETUP] ConnectTimeout: " + connectTimeoutMs + "ms");
        FileLogger.i(TAG, "[SESSION_SETUP] StrictHostKeyChecking: " +
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
                FileLogger.i(TAG, "[PASSWORD_SET] session.setPassword 已调用 | 明文=" + password);
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

                // 记录配置摘要
                logSessionSetup(session, hostname, port, username, strictHostCheckValue, connectTimeoutValue);

            } else {
                debugInfo += "[7] No authentication credentials provided\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword(""); // 尝试空密码
                connectionStatus = "no_credentials";
            }

            debugInfo += "\n[8] Connecting to host...\n";
            FileLogger.i(TAG, "[CONNECT_START] 准备调用 session.connect(3000)... | 线程=" + Thread.currentThread().getName());
            long connectStart = System.currentTimeMillis();
            try {
                session.connect(3000);
                FileLogger.i(TAG, "[CONNECT_DONE] session.connect() 成功返回 | 耗时=" + (System.currentTimeMillis() - connectStart) + "ms");
            } catch (Exception connectEx) {
                FileLogger.e(TAG, "[CONNECT_FAILED] session.connect() 抛异常 | 耗时=" + (System.currentTimeMillis() - connectStart) + "ms", connectEx);
                throw connectEx;
            }
            long connectEnd = System.currentTimeMillis();
            connectionStatus = "connected_successfully";
            debugInfo += String.format("    → Connection established after %dms (%s total)\n",
                                     connectEnd - connectStart,
                                     (connectEnd - startTime) + "ms");

            debugInfo += String.format("\n[9] Opening exec channel for command: '%s'\n", command);
            channel = (ChannelExec) session.openChannel("exec");

            errStream = new ByteArrayOutputStream();
            ((ChannelExec) channel).setErrStream(errStream);
            debugInfo += "[10] Allocating command (Standard Exec Mode)... [ERROR STREAM SETUP]\n";

            debugInfo += "[11] Environment variables omitted (per official example compatibility)\n";

            channel.setCommand(command);
            outStream = new ByteArrayOutputStream();
            debugInfo += String.format("[12] Starting command with timeout: %dms...\n", DEFAULT_TIMEOUT_MS);
            channel.connect(DEFAULT_TIMEOUT_MS);

            debugInfo += "[13] Reading stdout stream until channel closed...\n";
            InputStream inputStream = channel.getInputStream();
            byte[] tmp = new byte[4096];
            // 🔥 修复 (#858119422553 v3): 用 read() 阻塞 + 短超时轮询，绕过 available() 一直返回 0 的 bug
            long readStartTime = System.currentTimeMillis();
            long lastDataTime = System.currentTimeMillis();
            final long READ_TIMEOUT_MS = 3000;
            int readAttempts = 0;
            int drainCount = 0;
            final int MAX_DRAIN_ATTEMPTS = 20;
            while (true) {
                try {
                    int n = inputStream.read(tmp, 0, tmp.length);
                    readAttempts++;
                    if (n > 0) {
                        outStream.write(tmp, 0, n);
                        lastDataTime = System.currentTimeMillis();
                        drainCount = 0;
                        FileLogger.i(TAG, "[SSH_READ] attempt=" + readAttempts + " read=" + n + " outSize=" + outStream.size() + " closed=" + channel.isClosed());
                    } else if (n == -1) {
                        FileLogger.i(TAG, "[SSH_READ_EOF] attempt=" + readAttempts + " outSize=" + outStream.size());
                        break;
                    } else {
                        if (channel.isClosed()) {
                            FileLogger.i(TAG, "[SSH_READ_CLOSED] attempt=" + readAttempts + " outSize=" + outStream.size());
                            break;
                        }
                        if (System.currentTimeMillis() - lastDataTime > READ_TIMEOUT_MS) {
                            FileLogger.i(TAG, "[SSH_READ_IDLE_TIMEOUT] attempt=" + readAttempts + " outSize=" + outStream.size());
                            break;
                        }
                    }
                } catch (java.io.IOException ioe) {
                    FileLogger.i(TAG, "[SSH_READ_EXCEPTION] " + ioe.getMessage());
                    break;
                }
            }
            // drain 阶段：channel 关闭后再尝试多读几次，防止数据残留在 socket buffer
            // 这一步和 read 阶段重复，但因为 channel 已关，可能能拿到剩余数据
            while (drainCount < MAX_DRAIN_ATTEMPTS) {
                try {
                    int n = inputStream.read(tmp, 0, tmp.length);
                    if (n > 0) {
                        outStream.write(tmp, 0, n);
                        FileLogger.i(TAG, "[SSH_DRAIN] attempt=" + drainCount + " read=" + n + " outSize=" + outStream.size());
                    } else if (n == -1) {
                        break;
                    }
                } catch (java.io.IOException ioe) {
                    break;
                }
                drainCount++;
            }
            debugInfo += "[14] Read " + readAttempts + " attempts, drained " + drainCount + " more. Exit status: " + Integer.toString(channel.getExitStatus()) + "\n";

            byte[] errBytes = errStream.toByteArray();
            if (errBytes.length > 0) {
                debugInfo += String.format("[15] Error stream output:\n%s\n",
                                         new String(errBytes, "UTF-8"));
            }

            byte[] responseBytes = outStream.toByteArray();
            String stdout = new String(responseBytes, "UTF-8");
            int exitCode = channel.getExitStatus();

            debugInfo += String.format("[16] Output length: %d bytes\n", stdout.length());
            if (!stdout.isEmpty()) {
                debugInfo += String.format("[17] Output content:\n%s\n", stdout);
            }

            return new CommandResult("success", stdout, errStream.toString(), exitCode,
                                    connectionStatus, debugInfo);
        } catch (Exception e) {
            connectionStatus = "connection_failed";
            lastError = e;

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