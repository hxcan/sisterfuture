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
            
            JSONObject properties = new JSONObject()
                .put("hostname", new JSONObject()
                    .put("type", "string")
                    .put("description", "目标主机 IP 或域名"))
                .put("port", new JSONObject()
                    .put("type", "integer")
                    .put("description", "SSH 端口 (默认 22)", "default", 22))
                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))
                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码（如果提供则使用密码认证）"))
                .put("command", new JSONObject()
                    .put("type", "string")
                    .put("description", "要执行的 Shell 命令"));
            
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
                        .put("exitCode", -1);
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

        try {
            JSch jsch = new JSch();
            
            // 优先级：私钥 > 密码 > 无认证
            if (isPrivateKeyAvailable()) {
                loadPrivateKey(jsch);
            } else if (password != null && !password.isEmpty()) {
                session = jsch.getSession(username, hostname, port);
                session.setPassword(password);
            } else {
                session = jsch.getSession(username, hostname, port);
                session.setPassword(""); // 尝试空密码（通常失败）
            }
            
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(3000); // 3 秒连接超时

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            outStream = new ByteArrayOutputStream();
            channel.setInputStream(null);
            channel.setOutputStream(outStream);

            channel.connect(DEFAULT_TIMEOUT_MS);

            byte[] responseBytes = outStream.toByteArray();
            String stdout = new String(responseBytes, "UTF-8");

            return new CommandResult("success", stdout, "", channel.getExitStatus());

        } catch (Exception e) {
            return new CommandResult("failed", "", e.getMessage(), -1);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
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

        CommandResult(String status, String stdout, String stderr, int exitCode) {
            this.status = status;
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }

        JSONObject toJson() throws Exception {
            JSONObject result = new JSONObject();
            result.put("status", status);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("exitCode", exitCode);
            return result;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求执行远程命令时才调用此工具。需要提供 hostname、username 和 command 参数。若需认证，可选提供 password 参数（推荐使用密码认证，私钥认证暂不通过大模型传递）。";
    }
}