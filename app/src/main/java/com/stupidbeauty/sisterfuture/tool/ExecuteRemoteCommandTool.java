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
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecuteRemoteCommandTool implements Tool {
    private static final String TAG = "ExecuteRemoteCommand";
    private static final int DEFAULT_TIMEOUT_MS = 60000; // 60 秒默认超时
    private static final int READ_POLL_INTERVAL_MS = 100; // 读循环轮询间隔

    private final Context context;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ExecuteRemoteCommandTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
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
                String hostname = arguments.getString("hostname");
                int port = arguments.optInt("port", 22);
                String username = arguments.getString("username");
                String command = arguments.getString("command");

                FileLogger.i(TAG, "[ARGS_PARSED] hostname=" + hostname + " | port=" + port + " | username=" + username + " | command=" + command);

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
        long startTime = System.currentTimeMillis();

        try {
            debugInfo += String.format("[1] Init JSch at %dms\n", startTime);
            JSch jsch = new JSch();
            connectionStatus = "jsch_initialized";

            if (isPrivateKeyAvailable()) {
                debugInfo += "[2] Attempting key-based auth...\n";
                loadPrivateKey(jsch);
                connectionStatus = "key_auth_attempted";
            } else if (password != null && !password.isEmpty()) {
                debugInfo += String.format("[3] Creating session for %s@%s:%d...\n", username, hostname, port);
                session = jsch.getSession(username, hostname, port);
                session.setPassword(password);
                connectionStatus = "session_created_with_password";

                String strictHostCheckValue = "no";
                int connectTimeoutValue = 3000;
                int socketTimeoutValue = 5000; // 🔥 v2: 改 5s，让 JSch 异常时也能快速失败

                debugInfo += String.format("[4] Setting StrictHostKeyChecking=%s...\n", strictHostCheckValue);
                session.setConfig("StrictHostKeyChecking", strictHostCheckValue);

                debugInfo += String.format("[5] Setting ConnectTimeout=%dms...\n", connectTimeoutValue);
                session.setConfig("ConnectTimeout", String.valueOf(connectTimeoutValue));
                debugInfo += String.format("[6] Setting SocketTimeout=%dms...\n", socketTimeoutValue);
                session.setConfig("SocketTimeout", String.valueOf(socketTimeoutValue));

                logSessionSetup(session, hostname, port, username, strictHostCheckValue, connectTimeoutValue);

            } else {
                debugInfo += "[7] No authentication credentials provided\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword("");
                connectionStatus = "no_credentials";
            }

            debugInfo += "\n[8] Connecting to host...\n";
            FileLogger.i(TAG, "[CONNECT_START] 准备调用 session.connect(3000)... | 线程=" + Thread.currentThread().getName());
            session.connect(3000);
            FileLogger.i(TAG, "[CONNECT_DONE] session.connect() 成功返回");
            connectionStatus = "connected_successfully";
            debugInfo += "    → Connection established successfully\n";

            debugInfo += String.format("\n[9] Opening exec channel for command: '%s'\n", command);
            FileLogger.i(TAG, "[PRE_OPEN_CHANNEL] 准备调用 session.openChannel(\"exec\")... | session.isConnected=" + session.isConnected());
            channel = (ChannelExec) session.openChannel("exec");
            FileLogger.i(TAG, "[OPEN_CHANNEL_DONE] openChannel(\"exec\") 成功返回");

            // 🔥 v3 修复: 给 exec channel 分配 PTY，让命令完成后 channel 主动发送 EOF
            ((ChannelExec) channel).setPty(true);
            FileLogger.i(TAG, "[V3_PTY_ENABLED] exec channel PTY 已分配");

            errStream = new ByteArrayOutputStream();
            ((ChannelExec) channel).setErrStream(errStream);
            channel.setCommand(command);
            outStream = new ByteArrayOutputStream();
            channel.connect(DEFAULT_TIMEOUT_MS);

            // 🔥 v2: 双线程非阻塞读 - 后台线程读，主线程轮询超时
            debugInfo += "[13] Reading stdout stream (v2 dual-thread non-blocking)...\n";
            InputStream inputStream = channel.getInputStream();
            final long readStartTime = System.currentTimeMillis();
            final long readDeadline = readStartTime + DEFAULT_TIMEOUT_MS;
            FileLogger.i(TAG, "[READ_LOOP_START_V2] deadline=" + readDeadline + "ms (timeout=" + DEFAULT_TIMEOUT_MS + "ms) | poll_interval=" + READ_POLL_INTERVAL_MS + "ms");

            // 🔥 v2 关键修复：后台线程读取，主线程监控超时
            final ByteArrayOutputStream finalOutStream = outStream;
            final InputStream finalInputStream = inputStream;
            final ChannelExec finalChannel = channel;
            final AtomicBoolean readCompleted = new AtomicBoolean(false);
            final AtomicBoolean readErrored = new AtomicBoolean(false);
            final StringBuilder readError = new StringBuilder();
            int[] readAttempts = {0};

            Thread readerThread = new Thread(() -> {
                try {
                    byte[] tmp = new byte[4096];
                    int n;
                    while ((n = finalInputStream.read(tmp, 0, tmp.length)) != -1) {
                        synchronized (finalOutStream) {
                            finalOutStream.write(tmp, 0, n);
                        }
                        readAttempts[0]++;
                        if (readAttempts[0] % 50 == 0) {
                            FileLogger.i(TAG, "[READ_BG_PROGRESS] attempts=" + readAttempts[0] + " outSize=" + finalOutStream.size());
                        }
                    }
                    FileLogger.i(TAG, "[READ_BG_EOF] attempts=" + readAttempts[0] + " outSize=" + finalOutStream.size());
                } catch (java.io.IOException ioe) {
                    readErrored.set(true);
                    readError.append(ioe.getMessage());
                    FileLogger.i(TAG, "[READ_BG_EXCEPTION] " + ioe.getMessage());
                } finally {
                    readCompleted.set(true);
                }
            }, "ssh-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // 主线程：定期轮询，检查超时或 channel 关闭
            boolean timedOut = false;
            while (!readCompleted.get()) {
                try {
                    Thread.sleep(READ_POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                long elapsed = System.currentTimeMillis() - readStartTime;
                if (elapsed > DEFAULT_TIMEOUT_MS) {
                    timedOut = true;
                    FileLogger.i(TAG, "[READ_ABSOLUTE_TIMEOUT_V2] 已超过绝对超时 " + DEFAULT_TIMEOUT_MS + "ms，强制退出 | attempts=" + readAttempts[0] + " outSize=" + outStream.size());
                    break;
                }

                if (channel.isClosed() && outStream.size() > 0) {
                    FileLogger.i(TAG, "[READ_CHANNEL_CLOSED_WITH_DATA] 等待剩余数据 | attempts=" + readAttempts[0] + " outSize=" + outStream.size());
                    // 给 reader 线程一点时间读残余数据
                    try { Thread.sleep(200); } catch (InterruptedException ie) { break; }
                    break;
                }
            }

            // 等待 reader 线程结束（最多再等 1 秒）
            try {
                readerThread.join(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 如果是超时，主动断开 channel
            if (timedOut) {
                FileLogger.i(TAG, "[READ_TIMEOUT_FORCE_DISCONNECT] 超时强制断开 channel | outSize=" + outStream.size());
                try {
                    if (channel.isConnected()) {
                        channel.disconnect();
                    }
                } catch (Exception ignored) {}
            }

            if (readErrored.get()) {
                debugInfo += "[READ_BG_ERROR] " + readError.toString() + "\n";
            }
            debugInfo += String.format("[14] Read %d attempts. Timeout: %s. Exit status: %d\n",
                                     readAttempts[0], timedOut ? "YES" : "NO", channel.getExitStatus());

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

            // 🔥 v2: 如果超时，返回 status="timeout" 而不是 success
            String resultStatus = timedOut ? "timeout" : "success";
            return new CommandResult(resultStatus, stdout, errStream.toString(), exitCode,
                                    connectionStatus, debugInfo);
        } catch (Exception e) {
            connectionStatus = "connection_failed";

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
