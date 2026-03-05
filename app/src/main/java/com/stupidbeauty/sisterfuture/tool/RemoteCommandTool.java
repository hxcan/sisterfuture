package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.victoriafresh.api.VFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RemoteCommandTool implements Tool {
    private static final String TAG = "RemoteCommandTool";
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60秒默认超时
    
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
            functionDef.put("description", "执行远程 SSH 命令，需传入主机、用户名、私钥和命令");

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
                .put("privateKeyBytes", new JSONObject()
                    .put("type", "array")
                    .put("description", "已解密的私钥字节数组（需先解密）"))
                .put("command", new JSONObject()
                    .put("type", "string")
                    .put("description", "要执行的 Shell 命令"));
            
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{
                "hostname", "username", "privateKeyBytes", "command"
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
                byte[] privateKeyBytes = arguments.getJSONArray("privateKeyBytes").toString().getBytes(); // 注意：此处需优化
                String command = arguments.getString("command");

                CommandResult result = executeSshCommand(hostname, port, username, privateKeyBytes, command);
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
                                           byte[] privateKeyBytes, String command) {
        Session session = null;
        ChannelExec channel = null;
        ByteArrayOutputStream outStream = null;

        try {
            JSch jsch = new JSch();
            // TODO: 完整实现私钥加载（含解密逻辑）
            // jsch.addIdentity(new ByteArrayInputStream(privateKeyBytes));
            
            session = jsch.getSession(username, hostname, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(3000); // 3秒连接超时

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
        return "必须在用户明确要求执行远程命令时才调用此工具。需要提供 hostname、username、privateKeyBytes 和 command 参数。";
    }
}