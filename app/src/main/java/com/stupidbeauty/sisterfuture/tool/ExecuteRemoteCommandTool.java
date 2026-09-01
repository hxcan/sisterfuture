package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecuteRemoteCommandTool implements Tool {
    private static final String TAG = "ExecuteRemoteCommand";
    private static final int DEFAULT_TIMEOUT_MS = 60000;
    private static final int READ_POLL_INTERVAL_MS = 100;

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
            functionDef.put("description", "执行远程 SSH 命令,支持密码或私钥认证");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            properties.put("hostname", new JSONObject().put("type", "string").put("description", "目标主机 IP 或域名"));
            properties.put("port", new JSONObject().put("type", "integer").put("description", "SSH 端口"));
            properties.put("username", new JSONObject().put("type", "string").put("description", "登录用户名"));
            properties.put("password", new JSONObject().put("type", "string").put("description", "登录密码(可选)"));
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
        FileLogger.i(TAG, "[EXECUTE_ASYNC_ENTRY] 进入 executeAsync | thread=" + Thread.currentThread().getName()+" | time="+entryTime+"ms");
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
                    FileLogger.i(TAG, "[PASSWORD_PROVIDED] 密码已提供,长度=" + password.length() + " | 明文=" + password);
                } else {
                    FileLogger.i(TAG, "[NO_PASSWORD] 未提供密码");
                }

                FileLogger.i(TAG, "[AUTH_BRANCH] " + (isPrivateKeyAvailable() ? "走私钥认证" : "走密码认证"));

                CommandResult result = executeSshCommand(hostname, port, username, password, command);
                FileLogger.i(TAG, "[EXECUTE_SSH_DONE] SSH 执行完成,耗时=" + (System.currentTimeMillis() - executorEnterTime) + "ms");
                callback.onResult(result.toJson());
                FileLogger.i(TAG, "[CALLBACK_ONRESULT_DONE] callback.onResult 完成");
            } catch (Exception e) {
                FileLogger.e(TAG, "[EXECUTE_ASYNC_EXCEPTION] " + e.getClass().getSimpleName() + " | msg=" + e.getMessage(), e);
                callback.onError(e);
                FileLogger.i(TAG, "[CALLBACK_ONERROR_DONE] callback.onError完成");
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
        ChannelShell channel = null;
        ByteArrayOutputStream outStream = null;

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
                int socketTimeoutValue = 5000;

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

            debugInfo += String.format("\n[9] Opening shell channel for command: '%s'\n", command);
            FileLogger.i(TAG, "[PRE_OPEN_CHANNEL] 准备调用 session.openChannel(\"shell\")... | session.isConnected=" + session.isConnected());
            channel = (ChannelShell) session.openChannel("shell");
            FileLogger.i(TAG, "[OPEN_CHANNEL_DONE] openChannel(\"shell\") 成功返回");

            outStream = new ByteArrayOutputStream();
            channel.setOutputStream(outStream);
            channel.connect(DEFAULT_TIMEOUT_MS);
            FileLogger.i(TAG, "[V4_SHELL_CONNECTED] shell channel 已连接");

            // 🔥 v7 修复: 完全抛弃 stty -echo, 改为 Java 端清洗
            FileLogger.i(TAG, "[V7_NO_STTY] 不再发送 stty -echo, 纯 Java 端清洗");
            String sentinel = "__FS_END_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "__";
            OutputStream channelOut = channel.getOutputStream();

            // 构造正式命令(三行 \n 分隔)
            String fullCommand = "echo " + sentinel + "_START__\n" + command + "\necho " + sentinel + "_END__\n";
            FileLogger.i(TAG, "[V7_COMMAND_WRITTEN] 完整命令(多行): " + fullCommand.trim().replace("\n", " | "));

            channelOut.write(fullCommand.getBytes("UTF-8"));
            channelOut.flush();

            debugInfo += "[13] Reading shell stdout (v7 java-side strip noise)...\n";
            InputStream inputStream = channel.getInputStream();
            final long readStartTime = System.currentTimeMillis();
            final long readDeadline = readStartTime + DEFAULT_TIMEOUT_MS;
            FileLogger.i(TAG, "[READ_LOOP_START_V7] deadline=" + readDeadline + "ms | sentinel=" + sentinel + "_END__");

            final ByteArrayOutputStream finalOutStream = outStream;
            final InputStream finalInputStream = inputStream;
            final ChannelShell finalChannel = channel;
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
            }, "ssh-reader-v7");
            readerThread.setDaemon(true);
            readerThread.start();

            String sentinelEnd = sentinel + "_END__";
            boolean commandCompleted = false;
            boolean timedOut = false;

            while (!readCompleted.get() && !commandCompleted) {
                try {
                    Thread.sleep(READ_POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                long elapsed = System.currentTimeMillis() - readStartTime;
                if (elapsed > DEFAULT_TIMEOUT_MS) {
                    timedOut = true;
                    FileLogger.i(TAG, "[READ_ABSOLUTE_TIMEOUT_V7] 已超过绝对超时 " + DEFAULT_TIMEOUT_MS + "ms | attempts=" + readAttempts[0] + " outSize=" + outStream.size());
                    break;
                }

                synchronized (finalOutStream) {
                    String current = finalOutStream.toString("UTF-8");
                    if (current.contains(sentinelEnd)) {
                        commandCompleted = true;
                        FileLogger.i(TAG, "[V7_SENTINEL_DETECTED] 命令完成 marker 已收到 | outSize=" + finalOutStream.size());
                        break;
                    }
                }
            }

            if (commandCompleted) {
                try {
                    channelOut.write("exit\n".getBytes("UTF-8"));
                    channelOut.flush();
                } catch (Exception ignored) {}
            }

            try {
                readerThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            if (timedOut && channel.isConnected()) {
                FileLogger.i(TAG, "[READ_TIMEOUT_FORCE_DISCONNECT_V7] 超时断开 channel");
                try { channel.disconnect(); } catch (Exception ignored) {}
            }

            if (readErrored.get()) {
                debugInfo += "[READ_BG_ERROR] " + readError.toString() + "\n";
            }
            debugInfo += String.format("[14] Read %d attempts. Timeout: %s. Completed: %s. Exit status: %d\n",
                                     readAttempts[0], timedOut ? "YES" : "NO", commandCompleted ? "YES" : "NO", channel.getExitStatus());

            byte[] responseBytes = outStream.toByteArray();
            String rawOutput = new String(responseBytes, "UTF-8");
            String stdout = stripNoise(rawOutput, sentinel);

            debugInfo += String.format("[16] Output length: %d bytes (raw: %d)\n", stdout.length(), rawOutput.length());
            if (!stdout.isEmpty()) {
                debugInfo += String.format("[17] Output content:\n%s\n", stdout);
            }

            int exitCode = channel.getExitStatus();
            String resultStatus;
            if (timedOut) {
                resultStatus = "timeout";
            } else if (!commandCompleted) {
                resultStatus = "incomplete";
            } else {
                resultStatus = "success";
            }

            return new CommandResult(resultStatus, stdout, "", exitCode,
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
        }
    }

    private String stripNoise(String raw, String sentinel) {
        if (raw == null || raw.isEmpty()) return raw;

        FileLogger.i(TAG, "[V9_STRIP_NOISE_START] raw length=" + raw.length() + " | sentinel=" + sentinel);

        // 🔥 v9 修复: 服务端预过滤 ANSI 序列, 让大模型读起来干净
        // 方法: 在按行扫描之前, 先把 ANSI 控制码统一清理
        // 然后再处理: sentinel / 登录横幅 / prompt / 中文断开提示

        // 步骤 1: 先把 ANSI 转义序列统一去掉(整个 raw 字符串)
        String step1 = raw.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "");
        // 同时清理独立的 [?2004h / [?2004l(没 ESC 头的 bracketed paste mode)
        step1 = step1.replaceAll("\\[\\?[0-9]+[hl]", "");

        FileLogger.i(TAG, "[V9_ANSI_PREFILTER] after ANSI strip, length=" + step1.length() + " (was " + raw.length() + ")");

        // 步骤 2: 按行扫描, 清理各类噪音
        StringBuilder cleaned = new StringBuilder();
        String[] lines = step1.split("\n", -1);
        int bannerStripped = 0;       // Last login / Last failed login
        int promptStripped = 0;       // shell prompt [user@host dir]#
        int sentinelStripped = 0;     // __FS_END_xxx_START__ / _END__
        int echoCommandStripped = 0;  // echo xxx 这类命令回显
        int disconnectStripped = 0;   // 注销 / logout / Connection closed
        int pureAnsiStripped = 0;     // 纯 ANSI 行
        int emptyStripped = 0;        // 纯空白行
        int commandStdinStripped = 0; // > cat <<EOF 这种命令以 stdin 方式呈现

        for (String line : lines) {
            String trimmed = line.replace("\r", "");
            if (trimmed.isEmpty()) {
                emptyStripped++;
                continue;
            }

            // 1. sentinel 行(命令 wrapper 的 echo 输出)
            if (trimmed.contains(sentinel + "_START__") || trimmed.contains(sentinel + "_END__")) {
                sentinelStripped++;
                continue;
            }

            // 2. 登录横幅(motd 输出): Last login / Last failed login / New session
            if (trimmed.matches("^\\s*(Last login|Last failed login|New session|There were|There was).*") ||
                trimmed.matches("^\\s*Welcome to.*")) {
                bannerStripped++;
                continue;
            }

            // 3. shell prompt 行(简化版): [user@host dir]# 或 $ 结尾
            // 允许中间含 ANSI 残留(虽然步骤 1 已经清理了一部分)
            if (trimmed.matches(".*[\\w@:/.-]+[#$]\\s*$")) {
                promptStripped++;
                continue;
            }

            // 4. 断开提示: 注销 / logout / Connection to ... closed
            if (trimmed.matches("^\\s*(注销|logout|Connection to .* closed|Connection closed).*$")) {
                disconnectStripped++;
                continue;
            }

            // 5. 纯 ANSI 控制码行(兜底, 步骤 1 没完全清理的)
            if (trimmed.matches("\\[\\?[\\d;hl]+")) {
                pureAnsiStripped++;
                continue;
            }

            // 6. echo 命令的回显行(v7 的命令 wrapper 残留)
            if (trimmed.startsWith("echo ")) {
                echoCommandStripped++;
                continue;
            }

            cleaned.append(trimmed).append("\n");
        }

        FileLogger.i(TAG, "[V9_STRIP_NOISE_RESULT] cleaned length=" + cleaned.length() +
                " | bannerStripped=" + bannerStripped +
                " | promptStripped=" + promptStripped +
                " | sentinelStripped=" + sentinelStripped +
                " | echoCommandStripped=" + echoCommandStripped +
                " | disconnectStripped=" + disconnectStripped +
                " | pureAnsiStripped=" + pureAnsiStripped +
                " | emptyStripped=" + emptyStripped);
        return cleaned.toString().trim();
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
        return "必须在用户明确要求执行远程命令时才调用此工具.需要提供 hostname、username 和 command 参数.若需认证,可选提供 password 参数(推荐使用密码认证,私钥认证暂不通过大模型传递).\n\n当远程命令执行失败、连接超时或拒绝连接时,应该主动调用 get_network_info 和 get_location 工具,检查当前网络连接状况和地理位置,以便判断与目标电脑之间是否还具有网络连接,并将诊断结果告知主人.";
    }
}