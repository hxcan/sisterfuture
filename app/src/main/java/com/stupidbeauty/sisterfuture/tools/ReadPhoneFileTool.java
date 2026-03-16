package com.stupidbeauty.sisterfuture.tools;

import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 手机文件读取工具。
 * 用于自动化读取手机外置存储上的任意文件内容。
 * 
 * @author 未来姐姐
 * @version 1.0
 * @since 2026-03-16
 */
public class ReadPhoneFileTool
{
    /**
     * 执行文件读取。
     * 
     * @param params 工具参数，包含：
     *               - path: 要读取的文件路径（必填）
     *               - encoding: 编码方式（可选，默认 "base64"）
     *                 - "base64": Base64 编码，适用于二进制文件
     *                 - "utf-8": UTF-8 文本，适用于文本文件
     * @return ToolResult 包含文件内容或错误信息
     */
    public static ToolResult execute(ToolParams params)
    {
        try
        {
            // 获取参数
            String path = params.getString("path");
            String encoding = params.getString("encoding", "base64");
            
            // 验证文件
            File file = new File(path);
            if (!file.exists())
            {
                return ToolResult.error("文件不存在：" + path);
            }
            if (!file.isFile())
            {
                return ToolResult.error("路径不是文件：" + path);
            }
            
            // 检查读取权限
            if (!file.canRead())
            {
                return ToolResult.error("无权限读取文件：" + path);
            }
            
            // 检查文件大小（限制 100MB）
            long fileSize = file.length();
            if (fileSize > 100 * 1024 * 1024)
            {
                return ToolResult.error("文件过大（最大支持 100MB）：" + formatFileSize(fileSize));
            }
            
            // 检测文件类型
            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
            
            // 读取文件内容
            String content;
            String actualEncoding;
            
            if ("utf-8".equalsIgnoreCase(encoding))
            {
                // 文本模式
                content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                actualEncoding = "utf-8";
            }
            else
            {
                // Base64 模式（默认）
                byte[] fileBytes = Files.readAllBytes(Paths.get(path));
                content = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
                actualEncoding = "base64";
            }
            
            // 构建返回结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("content", content);
            result.put("encoding", actualEncoding);
            result.put("size", fileSize);
            result.put("path", path);
            if (mimeType != null)
            {
                result.put("mime_type", mimeType);
            }
            result.put("file_name", file.getName());
            
            return ToolResult.success(result);
        }
        catch (Exception e)
        {
            return ToolResult.error("读取失败：" + e.getMessage());
        }
    }
    
    /**
     * 格式化文件大小。
     * 
     * @param bytes 字节数
     * @return 格式化后的大小字符串
     */
    private static String formatFileSize(long bytes)
    {
        if (bytes < 1024)
        {
            return bytes + " B";
        }
        else if (bytes < 1024 * 1024)
        {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        else if (bytes < 1024 * 1024 * 1024)
        {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        else
        {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}