package com.stupidbeauty.sisterfuture.ssh;

import android.content.Context;
// import com.stupidbeauty.sisterfuture.VFile;
import com.stupidbeauty.victoriafresh.VFile;
// import com.stupidbeauty.hxlauncher.rpc.CloudRequestorZzaqwb;
// import org.apache.commons.collections4.MultiMap;
// import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.io.FileUtils;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelExec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SshCommandExecutor {

    private static final String HOST = "192.168.45.211";
    private static final String USERNAME = "hcai";
    private static final String WORKING_DIR = "/home/hcai/SoftwareDevelop/sisterfuture/SisterFuture/";
    private static final String COMMAND = "./gradlew assembleDebug";
    private static final String PRIVATE_KEY_RESOURCE_PATH = ":/keys.s/android_ssh_key";
    private static final String PRIVATE_KEY_FILE_PATH = "android_ssh_key";

    public static boolean executeCommand(Context context) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            // 从虚拟文件系统加载私钥
            VFile privateKeyFile = new VFile(context, PRIVATE_KEY_RESOURCE_PATH);
            byte[] privateKeyBytes = privateKeyFile.getFileContent();

            // 将私钥写入应用内部存储
            InputStream privateKeyInputStream = new ByteArrayInputStream(privateKeyBytes);
            String internalFilePath = context.getFilesDir().getAbsolutePath() + "/" + PRIVATE_KEY_FILE_PATH;
            java.io.File keyFile = new java.io.File(internalFilePath);
            if (!keyFile.exists()) {
                keyFile.createNewFile();
                try (OutputStream fos = new java.io.FileOutputStream(keyFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = privateKeyInputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }

            // 设置私钥路径
            jsch.addIdentity(internalFilePath);

            // 创建会话
            session = jsch.getSession(USERNAME, HOST, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000);

            // 打开通道
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(COMMAND);
            // channel.setWorkingDirectory(WORKING_DIR);

            // 设置输入输出流
            channel.setInputStream(null);
            channel.setOutputStream(System.out);
            channel.setErrStream(System.err);

            // 执行命令
            channel.connect(10000);

            // 等待命令完成
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitStatus = channel.getExitStatus();
            return exitStatus == 0;

        } catch (JSchException | IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
