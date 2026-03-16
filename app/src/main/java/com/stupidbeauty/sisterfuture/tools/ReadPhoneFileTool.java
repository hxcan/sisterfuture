// com.stupidbeauty.sisterfuture.tools.ReadPhoneFileTool.java
package com.stupidbeauty.sisterfuture.tools;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.stupidbeauty.sisterfuture.tool.Tool;

/**
 * 手机文件读取工具。
 * 用于自动化读取手机外置存储上的任意文件内容。
 * 
 * @author 未来姐姐
 * @version 1.0
 * @since 2026-03-16
 */
public class ReadPhoneFileTool implements Tool
{
    private final Context context;

    public ReadPhoneFileTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "read_phone_file";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "read_phone_file");
            functionDef.put("description", "读取手机外置存储上的文件内容。支持 Base64 编码（二进制文件）和 UTF-8 文本（文本文件）");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            JSONObject properties = new JSONObject();
            
            // path 参数（必填）
            properties.put("path", new JSONObject()
                .put("type", "string")
                .put("description", "要读取的文件路径"));
            
            // encoding 参数（可选）
            properties.put("encoding", new JSONObject()
                .put("type", "string")
                .put("description", "编码方式：\"base64\"（默认，适用于二进制文件）或 \"utf-8\"（适用于文本文件）"));
            
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"path"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude()
    {
        return true;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception
    {
        // 解析参数
        String path = arguments.getString("path");
        String encoding = arguments.optString("encoding", "base64");

        // 验证文件
        File file = new File(path);
        if (!file.exists())
        {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        if (!file.isFile())
        {
            throw new IllegalArgumentException("路径不是文件：" + path);
        }

        // 检查文件大小（限制 100MB）
        long fileSize = file.length();
        if (fileSize > 100 * 1024 * 1024)
        {
            throw new IllegalArgumentException("文件过大（最大支持 100MB）: " + formatFileSize(fileSize));
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

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求读取手机文件时才调用此工具。需要提供文件路径。对于二进制文件（如图片、PDF）使用 base64 编码，对于文本文件可以使用 utf-8 编码。";
    }

    /**
     * 格式化文件大小。
     */
    private String formatFileSize(long bytes)
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