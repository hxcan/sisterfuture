package com.stupidbeauty.sisterfuture.tools;

import android.content.Context;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.victoriafresh.api.VFile;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RemoteCommandExecutor {
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60秒默认超时
    
    /**
     * 执行远程 SSH 命令（异步）
     * @param context Android 上下文
     * @param hostname 目标主机 IP/域名
     * @param port SSH 端口 (默认22)
     * @param username 登录用户名
     * @param privateKeyBytes 加密私钥内容（需先解密，此处为占位符）
     * @param command 要执行的 Shell 命令
     * @return Future<CommandResult> 结果对象
     */
    public static Future<CommandResult> executeAsync(Context context, 
                                                     String hostname, 
                                                     int port, 
                                                     String username, 
                                                     byte[] privateKeyBytes, 
                                                     String command) {
        return executor.submit(() -> {
            try {
                // TODO: 实现私钥解密逻辑（使用AES-GCM等对称加密）
                // 当前暂直接传入原始数据供调试
                return executeSshCommand(hostname, port, username, privateKeyBytes, command);
            } catch (Exception e) {
                return CommandResult.failed("加载私钥失败", null, e.getMessage(), -1);
            }
        });
    }
    
    /**
     * 从 Victoria Fresh VFS 加载加密私钥
     * @param context Android 上下文
     * @return 加密后的私钥字节数组
     * @throws Exception 读取失败时抛出
     */
    public static byte[] loadPrivateKeyFromVFS(Context context) throws Exception {
        String qrcFileName = "android_ssh_key";
        String fullQrcFileName = ":/keys.s/" + qrcFileName;
        VFile keyFile = new VFile(context, fullQrcFileName);
        
        byte[] encryptedKey = keyFile.getFileContent();
        // TODO: 此处应添加解密逻辑（使用AES-GCM），但需在后续步骤完善
        return encryptedKey;
    }
    
    /**
     * 执行SSH命令的核心逻辑
     * @param hostname 目标主机IP
     * @param port SSH 端口
     * @param username 用户名
     * @param privateKeyBytes 已解密的私钥字节数组
     * @param command Shell 命令
     * @return CommandResult 执行结果
     */
    private static CommandResult executeSshCommand(String hostname, int port, String username, 
                                                   byte[] privateKeyBytes, String command) {
        Session session = null;
        ChannelExec channel = null;
        InputStream in = null;
        ByteArrayOutputStream outStream = null;
        
        try {
            JSch jsch = new JSch();
            
            // TODO: 完整实现私钥加载（含解密后加载到JSch）
            // 示例伪代码：
            // KeyPair kp = KeyPair.load(jsch, privateKeyBytes);
            // jsch.addIdentity(kp);
            
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
            
            return CommandResult.success(stdout, "", channel.getExitStatus());
            
        } catch (Exception e) {
            return CommandResult.failed("SSH执行失败", null, e.getMessage(), -1);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
            if (outStream != null) {
                try { outStream.close(); } catch (Exception ignored) {}
            }
        }
    }
}