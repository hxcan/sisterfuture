package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private CommandResult executeSshCommand(String hostname, int port, String username, 
                                           String password, String command) {
        Session session = null;
        ChannelExec channel = null;
        ByteArrayOutputStream outStream = null;
        
        String debugInfo = "";
        String connectionStatus = "unknown";
        Throwable lastError = null;

        try {
            debugInfo += "Init JSch...\n";
            JSch jsch = new JSch();
            connectionStatus = "jsch_initialized";
            
            // 优先级：私钥 > 密码 > 无认证
            if (isPrivateKeyAvailable()) {
                debugInfo += "Attempting key-based auth...\n";
                loadPrivateKey(jsch);
                connectionStatus = "key_auth_attempted";
            } else if (password != null && !password.isEmpty()) {
                debugInfo += "Creating session for " + username + "@" + hostname + ":" + port + "\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword(password);
                connectionStatus = "session_created_with_password";
                
                // 调试：打印 SSH 配置
                debugInfo += "Session configs:\n";
                debugInfo += "  StrictHostKeyChecking: " + session.getConfig("StrictHostKeyChecking") + "\n";
                debugInfo += "  ConnectTimeout: " + session.getConfig("ConnectTimeout") + "\n";
                debugInfo += "  SocketTimeout: " + session.getConfig("SocketTimeout") + "\n";
            } else {
                debugInfo += "No authentication credentials provided\n";
                session = jsch.getSession(username, hostname, port);
                session.setPassword(""); // 尝试空密码（通常失败）
                connectionStatus = "no_credentials";
            }
            
            debugInfo += "Connecting to host...\n";
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(3000); // 3 秒连接超时
            connectionStatus = "connected_successfully";
            debugInfo += "Connection established after 3s\n";

            debugInfo += "Opening exec channel for command: " + command + "\n";
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            outStream = new ByteArrayOutputStream();
            channel.setInputStream(null);
            channel.setOutputStream(outStream);

            debugInfo += "Executing command with timeout: " + DEFAULT_TIMEOUT_MS + "ms...\n";
            channel.connect(DEFAULT_TIMEOUT_MS);
            debugInfo += "Command execution completed\n";

            byte[] responseBytes = outStream.toByteArray();
            String stdout = new String(responseBytes, "UTF-8");
            int exitCode = channel.getExitStatus();
            
            debugInfo += "Exit code: " + exitCode + "\n";
            debugInfo += "Output length: " + stdout.length() + " bytes";
            
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
            
            debugInfo += "ERROR: " + errorMsg + "\n";
            debugInfo += "Full stack trace available in logs.\n";
            
            return new CommandResult("failed", "", errorMsg, -1, connectionStatus, debugInfo);
        } finally {
            if (channel != null && channel.isConnected()) {
                debugInfo += "Disconnecting channel...\n";
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                debugInfo += "Disconnecting session...\n";
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
        // TODO: 从 Victoria Fresh VFS 检查是否有私钥文件
        // 目前返回 false，强制使用密码认证
        return false;
    }
    
    /**
     * 加载私钥到 JSch（当前为占位符实现）
     */
    private void loadPrivateKey(JSch jsch) {
        try {
            // TODO: 从 VFS 读取私钥并解析
            // 示例伪代码：
            // String keyContent = readFromVFS("/keys.s/android_ssh_key");
            // KeyPair kp = KeyPair.load(jsch, keyContent);
            // jsch.addIdentity(kp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load private key", e);
            // 加载失败不影响其他认证方式
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